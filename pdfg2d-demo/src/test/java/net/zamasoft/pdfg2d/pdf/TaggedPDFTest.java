package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import net.zamasoft.pdfg2d.demo.DemoUtils;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.PDFMetaInfo;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.TaggedParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

/**
 * Tagged PDF tests: logical structure tree generation, PDF/A level A
 * conformance (veraPDF-validated) and PDF/UA-1 conformance.
 */
public class TaggedPDFTest {

	@BeforeAll
	public static void initVeraPDF() {
		VeraGreenfieldFoundryProvider.initialise();
	}

	private static void assertCompliant(final File file, final PDFAFlavour flavour) throws Exception {
		try (final var parser = Foundries.defaultInstance().createParser(new FileInputStream(file), flavour);
				final var validator = Foundries.defaultInstance().createValidator(flavour, false)) {
			final var result = validator.validate(parser);
			if (!result.isCompliant()) {
				final var failures = result.getTestAssertions().stream()
						.filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
						.map(a -> a.getRuleId() + " " + a.getMessage())
						.distinct()
						.collect(Collectors.joining("\n"));
				assertTrue(result.isCompliant(),
						"veraPDF " + flavour + " failures for " + file.getName() + ":\n" + failures);
			}
		}
	}

	private static PDFFontSourceManager embeddedFonts() throws Exception {
		final var fsm = new PDFFontSourceManager();
		final var face = new FontFace();
		face.src = new FileSource(DemoUtils.getResourceFile("ipaexm.ttf"));
		face.fontFamily = FontFamilyList.create("IPAex明朝");
		fsm.addFontFace(face);
		return fsm;
	}

	/** Generates a tagged document with text, an image and vector decoration. */
	private File generate(final String name, final PDFParams params) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		final var image = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
		final var ig = image.createGraphics();
		ig.setColor(Color.ORANGE);
		ig.fillRect(0, 0, 60, 40);
		ig.dispose();

		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);
			final var page = pdf.nextPage(595, 842);
			try (final var gc = new PDFGC(page)) {
				final var g2d = new BridgeGraphics2D(gc);
				// Decorative rule: should be marked as an artifact
				g2d.setColor(Color.LIGHT_GRAY);
				g2d.fill(new Rectangle2D.Double(50, 45, 495, 2));

				// Heading grouped explicitly through the structure API
				page.beginStructElement("H1");
				g2d.setColor(Color.BLACK);
				g2d.setFont(new Font("IPAex明朝", Font.PLAIN, 24));
				g2d.drawString("見出しテキスト", 50, 100);
				page.endStructElement();

				// Body paragraph (auto-tagged as P)
				g2d.setFont(new Font("IPAex明朝", Font.PLAIN, 12));
				g2d.drawString("本文の段落テキストです。Tagged PDF structure test.", 50, 140);

				// Image (auto-tagged as Figure)
				g2d.drawImage(image, 50, 180, null);
				g2d.dispose();
			}
			pdf.close();
			builder.close();
		}
		return file;
	}

	@Test
	public void testStructureTreeIsEmitted() throws Exception {
		final var params = PDFParams.createDefault()
				.withFontSourceManager(embeddedFonts())
				.withTagged(new TaggedParams("ja", false));
		final var file = generate("tagged_structure.pdf", params);

		final var raw = new String(java.nio.file.Files.readAllBytes(file.toPath()),
				java.nio.charset.StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/StructTreeRoot"), "Catalog must reference the structure tree");
		assertTrue(raw.contains("/Marked true"), "MarkInfo must declare the file as tagged");
		assertTrue(raw.contains("/S /H1"), "Explicit heading element must be present");
		assertTrue(raw.contains("/S /P"), "Auto-tagged paragraph element must be present");
		assertTrue(raw.contains("/S /Figure"), "Image must be tagged as Figure");
		assertTrue(raw.contains("/Lang (ja)"), "Document language must be declared");
		assertTrue(raw.contains("/StructParents"), "Pages with content need a parent tree key");

		try (final var doc = Loader.loadPDF(file)) {
			assertNotNull(doc.getDocumentCatalog().getStructureTreeRoot(),
					"PDFBox must be able to read the structure tree");
		}
	}

	@Test
	public void testPdfA2aCompliance() throws Exception {
		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_PDFA2A)
				.withFontSourceManager(embeddedFonts())
				.withTagged(new TaggedParams("ja", false));
		assertCompliant(generate("tagged_pdfa2a.pdf", params), PDFAFlavour.PDFA_2_A);
	}

	@Test
	public void testPdfA3aCompliance() throws Exception {
		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_PDFA3A)
				.withFontSourceManager(embeddedFonts())
				.withTagged(new TaggedParams("ja", false));
		assertCompliant(generate("tagged_pdfa3a.pdf", params), PDFAFlavour.PDFA_3_A);
	}

	@Test
	public void testPdfUa1Compliance() throws Exception {
		final var meta = new PDFMetaInfo();
		meta.setTitle("PDF/UA テスト文書");
		final var params = PDFParams.createDefault()
				.withFontSourceManager(embeddedFonts())
				.withMetaInfo(meta)
				.withTagged(TaggedParams.pdfua("ja"));
		assertCompliant(generate("tagged_pdfua1.pdf", params), PDFAFlavour.PDFUA_1);
	}

	/** Generates a tagged document with a header table and a body link. */
	private File generateSemantic(final String name, final PDFParams params) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);
			final var page = pdf.nextPage(595, 842);
			try (final var gc = new PDFGC(page)) {
				final var g2d = new BridgeGraphics2D(gc);
				g2d.setColor(Color.BLACK);
				g2d.setFont(new Font("IPAex明朝", Font.PLAIN, 12));

				// A 2x2 table: header row (column scope) then a data row.
				page.beginStructElement("Table");
				page.beginStructElement("TR");
				page.beginStructElement("TH", "Column");
				g2d.drawString("氏名", 50, 100);
				page.endStructElement();
				page.beginStructElement("TH", "Column");
				g2d.drawString("年齢", 200, 100);
				page.endStructElement();
				page.endStructElement();
				page.beginStructElement("TR");
				page.beginStructElement("TD");
				g2d.drawString("山田", 50, 130);
				page.endStructElement();
				page.beginStructElement("TD");
				g2d.drawString("42", 200, 130);
				page.endStructElement();
				page.endStructElement();
				page.endStructElement();

				// A link inside a paragraph: the annotation must be associated
				// with the Link structure element.
				page.beginStructElement("P");
				page.beginStructElement("Link");
				g2d.setColor(Color.BLUE);
				g2d.drawString("example.com を参照", 50, 180);
				final var link = new net.zamasoft.pdfg2d.pdf.annot.LinkAnnot();
				link.setShape(new Rectangle2D.Double(50, 168, 160, 16));
				link.setURI(java.net.URI.create("https://example.com"));
				link.setContents("example.com へのリンク"); // PDF/UA alternate description
				page.addAnnotation(link);
				page.endStructElement();
				page.endStructElement();

				g2d.dispose();
			}
			pdf.close();
			builder.close();
		}
		return file;
	}

	@Test
	public void testTableAndLinkStructure() throws Exception {
		final var params = PDFParams.createDefault()
				.withFontSourceManager(embeddedFonts())
				.withTagged(new TaggedParams("ja", false));
		final var file = generateSemantic("tagged_semantic.pdf", params);

		final var raw = new String(java.nio.file.Files.readAllBytes(file.toPath()),
				java.nio.charset.StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/S /Table"), "Table element expected");
		assertTrue(raw.contains("/S /TH"), "Header cells expected");
		assertTrue(raw.contains("/S /TD"), "Data cells expected");
		assertTrue(raw.contains("/O /Table") && raw.contains("/Scope /Column"),
				"Header cells must carry a column scope attribute");
		assertTrue(raw.contains("/S /Link"), "Link structure element expected");
		assertTrue(raw.contains("/Type /OBJR"), "Link annotation must be referenced via OBJR");
		assertTrue(raw.contains("/StructParent "), "Link annotation must carry a StructParent key");
	}

	@Test
	public void testSemanticStructurePdfUa1Compliant() throws Exception {
		final var meta = new PDFMetaInfo();
		meta.setTitle("表とリンクのテスト");
		final var params = PDFParams.createDefault()
				.withFontSourceManager(embeddedFonts())
				.withMetaInfo(meta)
				.withTagged(TaggedParams.pdfua("ja"));
		assertCompliant(generateSemantic("tagged_semantic_ua.pdf", params), PDFAFlavour.PDFUA_1);
	}

	@Test
	public void testHeadingSkipIsRejectedUnderPdfUa() throws Exception {
		final var meta = new PDFMetaInfo();
		meta.setTitle("見出しスキップ");
		final var params = PDFParams.createDefault()
				.withFontSourceManager(embeddedFonts())
				.withMetaInfo(meta)
				.withTagged(TaggedParams.pdfua("ja"));
		final var file = TestOutputFiles.outputFile(getClass(), "tagged_heading_skip.pdf");
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
			try (final var out = new FileOutputStream(file)) {
				final var builder = new StreamFragmentedOutput(out);
				final var pdf = new PDFWriterImpl(builder, params);
				final var page = pdf.nextPage(595, 842);
				try (final var gc = new PDFGC(page)) {
					final var g2d = new BridgeGraphics2D(gc);
					g2d.setFont(new Font("IPAex明朝", Font.PLAIN, 12));
					page.beginStructElement("H1");
					g2d.drawString("一", 50, 100);
					page.endStructElement();
					page.beginStructElement("H3"); // skips H2
					g2d.drawString("二", 50, 130);
					page.endStructElement();
				}
				pdf.close();
				builder.close();
			}
		}, "PDF/UA must reject a skipped heading level");
	}
}
