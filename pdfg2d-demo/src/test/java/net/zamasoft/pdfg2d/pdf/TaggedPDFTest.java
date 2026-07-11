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
}
