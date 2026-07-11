package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.stream.Collectors;

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
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

/**
 * End-to-end PDF/A-1b conformance validation using the veraPDF greenfield
 * validator. Each test generates a representative document (vector graphics,
 * embedded-font text, raster image) and asserts that veraPDF reports full
 * compliance, listing every failed rule otherwise.
 */
public class PDFAVeraPDFComplianceTest {

	@BeforeAll
	public static void initVeraPDF() {
		VeraGreenfieldFoundryProvider.initialise();
	}

	/** Runs veraPDF and fails the test with a readable rule list if non-compliant. */
	private static void assertCompliant(final File file, final PDFAFlavour flavour) throws Exception {
		try (final var parser = Foundries.defaultInstance().createParser(new FileInputStream(file), flavour);
				final var validator = Foundries.defaultInstance().createValidator(flavour, false)) {
			final var result = validator.validate(parser);
			if (!result.isCompliant()) {
				final var failures = result.getTestAssertions().stream()
						.filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
						.map(a -> a.getRuleId() + " " + a.getMessage() + " @ " + a.getLocation().getContext())
						.distinct()
						.collect(Collectors.joining("\n"));
				assertTrue(result.isCompliant(), "veraPDF " + flavour + " failures for " + file.getName()
						+ ":\n" + failures);
			}
		}
	}

	private static PDFParams pdfa1bParams() {
		return PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFA1B);
	}

	@Test
	public void testVectorGraphicsDocument() throws Exception {
		final var file = TestOutputFiles.outputFile(PDFAVeraPDFComplianceTest.class, "pdfa_vector.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, pdfa1bParams());
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				final var g2d = new BridgeGraphics2D(gc);
				g2d.setColor(Color.BLUE);
				g2d.fill(new Rectangle2D.Double(50, 50, 200, 100));
				g2d.setPaint(new GradientPaint(0, 200, Color.RED, 300, 400, Color.YELLOW));
				g2d.fill(new Rectangle2D.Double(50, 200, 250, 200));
				g2d.dispose();
			}
			pdf.close();
			builder.close();
		}
		assertCompliant(file, PDFAFlavour.PDFA_1_B);
	}

	@Test
	public void testEmbeddedFontTextDocument() throws Exception {
		final var file = TestOutputFiles.outputFile(PDFAVeraPDFComplianceTest.class, "pdfa_text.pdf");
		try (final var fsm = new PDFFontSourceManager()) {
			final var face = new FontFace();
			face.src = new FileSource(DemoUtils.getResourceFile("ipaexm.ttf"));
			face.fontFamily = FontFamilyList.create("IPAex明朝");
			fsm.addFontFace(face);

			final var params = pdfa1bParams().withFontSourceManager(fsm);
			try (final var out = new FileOutputStream(file)) {
				final var builder = new StreamFragmentedOutput(out);
				final var pdf = new PDFWriterImpl(builder, params);
				try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
					final var g2d = new BridgeGraphics2D(gc);
					g2d.setColor(Color.BLACK);
					g2d.setFont(new Font("IPAex明朝", Font.PLAIN, 16));
					g2d.drawString("PDF/A-1b 日本語テキスト Embedded", 60, 100);
					g2d.drawString("Second line 0123456789", 60, 140);
					g2d.dispose();
				}
				pdf.close();
				builder.close();
			}
		}
		assertCompliant(file, PDFAFlavour.PDFA_1_B);
	}

	@Test
	public void testRasterImageDocument() throws Exception {
		final var file = TestOutputFiles.outputFile(PDFAVeraPDFComplianceTest.class, "pdfa_image.pdf");
		final var image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
		final var ig = image.createGraphics();
		ig.setColor(Color.GREEN);
		ig.fillRect(0, 0, 80, 60);
		ig.setColor(Color.RED);
		ig.fillOval(10, 10, 60, 40);
		ig.dispose();

		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, pdfa1bParams());
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				final var g2d = new BridgeGraphics2D(gc);
				g2d.drawImage(image, 100, 100, null);
				g2d.dispose();
			}
			pdf.close();
			builder.close();
		}
		assertCompliant(file, PDFAFlavour.PDFA_1_B);
	}

	@Test
	public void testCmykColorModeDocument() throws Exception {
		// PDF/A-1 requires the output intent's color space to match the
		// device color space used; CMYK mode must switch to a CMYK intent.
		final var file = TestOutputFiles.outputFile(PDFAVeraPDFComplianceTest.class, "pdfa_cmyk.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					pdfa1bParams().withColorMode(PDFParams.ColorMode.CMYK));
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				final var g2d = new BridgeGraphics2D(gc);
				g2d.setColor(Color.RED);
				g2d.fill(new Rectangle2D.Double(50, 50, 200, 100));
				g2d.dispose();
			}
			pdf.close();
			builder.close();
		}
		assertCompliant(file, PDFAFlavour.PDFA_1_B);
	}

	@Test
	public void testFormXObjectDocument() throws Exception {
		final var file = TestOutputFiles.outputFile(PDFAVeraPDFComplianceTest.class, "pdfa_form.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, pdfa1bParams());
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				final var group = pdf.createGroupImage(200, 200);
				try (final var ggc = new PDFGC(group)) {
					ggc.setFillPaint(net.zamasoft.pdfg2d.gc.paint.RGBColor.create(1, 0, 0));
					ggc.fill(new Rectangle2D.Double(0, 0, 100, 100));
				}
				gc.drawImage(group);
			}
			pdf.close();
			builder.close();
		}
		assertCompliant(file, PDFAFlavour.PDFA_1_B);
	}

	@Test
	public void testPdfA2bWithTransparency() throws Exception {
		// PDF/A-2 permits transparency: alpha fills and transparency-group
		// form XObjects must validate.
		final var file = TestOutputFiles.outputFile(PDFAVeraPDFComplianceTest.class, "pdfa2_transparency.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFA2B));
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				gc.setFillPaint(net.zamasoft.pdfg2d.gc.paint.RGBAColor.create(1f, 0f, 0f, 0.5f));
				gc.fill(new Rectangle2D.Double(50, 50, 200, 200));
				final var group = pdf.createGroupImage(200, 200);
				try (final var ggc = new PDFGC(group)) {
					ggc.setFillPaint(net.zamasoft.pdfg2d.gc.paint.RGBColor.create(0, 0, 1));
					ggc.fill(new Rectangle2D.Double(0, 0, 100, 100));
				}
				gc.setFillAlpha(0.5f);
				gc.drawImage(group);
			}
			pdf.close();
			builder.close();
		}
		assertCompliant(file, PDFAFlavour.PDFA_2_B);
	}

	/** Generates a text document with an embedded Japanese font. */
	private File generateTextDocument(final String name, final PDFParams.Version version) throws Exception {
		final var file = TestOutputFiles.outputFile(PDFAVeraPDFComplianceTest.class, name);
		try (final var fsm = new PDFFontSourceManager()) {
			final var face = new FontFace();
			face.src = new FileSource(DemoUtils.getResourceFile("ipaexm.ttf"));
			face.fontFamily = FontFamilyList.create("IPAex明朝");
			fsm.addFontFace(face);
			final var params = PDFParams.createDefault().withVersion(version).withFontSourceManager(fsm);
			try (final var out = new FileOutputStream(file)) {
				final var builder = new StreamFragmentedOutput(out);
				final var pdf = new PDFWriterImpl(builder, params);
				try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
					final var g2d = new BridgeGraphics2D(gc);
					g2d.setColor(Color.BLACK);
					g2d.setFont(new Font("IPAex明朝", Font.PLAIN, 16));
					g2d.drawString("日本語テキスト Unicode mapping テスト", 60, 100);
					g2d.dispose();
				}
				pdf.close();
				builder.close();
			}
		}
		return file;
	}

	@Test
	public void testPdfA2uTextDocument() throws Exception {
		// Level U requires every glyph to be mappable to Unicode (ToUnicode).
		assertCompliant(generateTextDocument("pdfa2u_text.pdf", PDFParams.Version.V_PDFA2U),
				PDFAFlavour.PDFA_2_U);
	}

	@Test
	public void testPdfA3uTextDocument() throws Exception {
		assertCompliant(generateTextDocument("pdfa3u_text.pdf", PDFParams.Version.V_PDFA3U),
				PDFAFlavour.PDFA_3_U);
	}

	@Test
	public void testPdfA4TextDocument() throws Exception {
		// PDF/A-4 is PDF 2.0 based: no Info dictionary, pdfaid:rev in XMP.
		assertCompliant(generateTextDocument("pdfa4_text.pdf", PDFParams.Version.V_PDFA4),
				PDFAFlavour.PDFA_4);
	}

	@Test
	public void testPdfA4fWithAttachment() throws Exception {
		final var file = TestOutputFiles.outputFile(PDFAVeraPDFComplianceTest.class, "pdfa4f_attachment.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFA4F));
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				gc.setFillPaint(net.zamasoft.pdfg2d.gc.paint.RGBColor.create(0.2f, 0.2f, 0.8f));
				gc.fill(new Rectangle2D.Double(50, 50, 200, 100));
			}
			try (final var att = pdf.addAttachment("data.json",
					new Attachment("Machine-readable data", "application/json"))) {
				att.write("{\"a\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			}
			pdf.close();
			builder.close();
		}
		assertCompliant(file, PDFAFlavour.PDFA_4_F);
	}

	@Test
	public void testPdfA3bWithAttachment() throws Exception {
		// PDF/A-3 permits arbitrary attachments with an AFRelationship.
		final var file = TestOutputFiles.outputFile(PDFAVeraPDFComplianceTest.class, "pdfa3_attachment.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFA3B));
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				gc.setFillPaint(net.zamasoft.pdfg2d.gc.paint.RGBColor.create(0, 0.5f, 0));
				gc.fill(new Rectangle2D.Double(50, 50, 200, 100));
			}
			try (final var att = pdf.addAttachment("source.csv",
					new Attachment("Source data", "text/csv"))) {
				att.write("a,b\n1,2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			}
			pdf.close();
			builder.close();
		}
		assertCompliant(file, PDFAFlavour.PDFA_3_B);
	}
}
