package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.OutputIntent;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * PDF/X-1a color and output-intent conformance tests: RGB input must be
 * converted to CMYK operators, and the OutputIntent dictionary must carry the
 * printing-condition entries (identifier, registry, info) required by
 * ISO 15930.
 */
public class PDFXConformanceTest {

	private File generate(final String name, final PDFParams params) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				// RGB input color: must not survive as an rg operator in X-1a
				gc.setFillPaint(RGBColor.create(1, 0, 0));
				gc.fill(new Rectangle2D.Double(50, 50, 200, 100));
			}
			pdf.close();
			builder.close();
		}
		return file;
	}

	@Test
	public void testRgbContentIsForcedToCmyk() throws Exception {
		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_PDFX1A)
				.withCompression(PDFParams.Compression.NONE);
		assertEquals(PDFParams.ColorMode.CMYK, params.colorMode(),
				"PDF/X-1a must normalize PRESERVE color mode to CMYK");

		final var file = generate("pdfx_cmyk_forced.pdf", params);
		try (final var doc = Loader.loadPDF(file)) {
			final String stream;
			try (final var contents = doc.getPage(0).getContents()) {
				stream = new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1);
			}
			assertFalse(stream.contains(" rg"), "DeviceRGB fill operator is forbidden in PDF/X-1a");
			assertTrue(stream.contains(" k"), "Fill color must be emitted as DeviceCMYK");
		}
	}

	@Test
	public void testDefaultOutputIntentCarriesInfo() throws Exception {
		final var file = generate("pdfx_output_intent_default.pdf",
				PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A));
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/S /GTS_PDFX"), "OutputIntent subtype must be GTS_PDFX");
		assertTrue(raw.contains("/OutputConditionIdentifier"), "Identifier is required");
		assertTrue(raw.contains("/Info"), "Info is required for a non-registered output condition");
		assertTrue(raw.contains("/DestOutputProfile"), "Profile must be embedded for a custom condition");
	}

	@Test
	public void testDefaultTrimBoxIsEmitted() throws Exception {
		final var file = generate("pdfx_default_trimbox.pdf",
				PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A));
		try (final var doc = Loader.loadPDF(file)) {
			final var page = doc.getPage(0);
			assertTrue(page.getCOSObject().containsKey(org.apache.pdfbox.cos.COSName.TRIM_BOX),
					"PDF/X page must carry a TrimBox by default");
			assertFalse(page.getCOSObject().containsKey(org.apache.pdfbox.cos.COSName.ART_BOX),
					"Default must not emit both TrimBox and ArtBox");
		}
	}

	@Test
	public void testTrimBoxAndArtBoxTogetherAreRejected() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "pdfx_both_boxes.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A));
			final var page = pdf.nextPage(595, 842);
			page.setTrimBox(new Rectangle2D.Double(10, 10, 575, 822));
			page.setArtBox(new Rectangle2D.Double(10, 10, 575, 822));
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, page::close,
					"PDF/X allows only one of TrimBox or ArtBox");
		}
	}

	@Test
	public void testTrimBoxOutsideMediaBoxIsRejected() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "pdfx_box_overflow.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A));
			final var page = pdf.nextPage(595, 842);
			page.setTrimBox(new Rectangle2D.Double(-10, 0, 700, 900));
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, page::close,
					"TrimBox must lie within the MediaBox");
		}
	}

	@Test
	public void testPdfX4AllowsTransparencyAndDeclaresIdentification() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "pdfx4_features.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX4));
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				// Live transparency is one of X-4's key features over X-1a
				gc.setFillPaint(net.zamasoft.pdfg2d.gc.paint.RGBAColor.create(1, 0, 0, 0.5f));
				gc.fill(new Rectangle2D.Double(50, 50, 200, 200));
				final var group = pdf.createGroupImage(200, 200);
				try (final var ggc = new PDFGC(group)) {
					ggc.setFillPaint(RGBColor.create(0, 0, 1));
					ggc.fill(new Rectangle2D.Double(0, 0, 100, 100));
				}
				gc.drawImage(group);
			}
			pdf.close();
			builder.close();
		}

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.startsWith("%PDF-1.6"), "PDF/X-4 is based on PDF 1.6");
		assertTrue(raw.contains("(PDF/X-4)"), "GTS_PDFXVersion must identify PDF/X-4");
		assertTrue(raw.contains("pdfxid:GTS_PDFXVersion"), "X-4 requires XMP identification (pdfxid)");
		assertTrue(raw.contains("/Transparency"), "X-4 permits transparency groups");
		assertTrue(raw.contains("/TrimBox"), "Every X page carries a TrimBox");
		assertTrue(raw.contains("/S /GTS_PDFX"), "OutputIntent subtype must be GTS_PDFX");

		try (final var doc = Loader.loadPDF(file)) {
			assertEquals(1.6f, doc.getVersion(), 0.01f);
			assertTrue(doc.getPage(0).getResources().getCOSObject()
					.containsKey(org.apache.pdfbox.cos.COSName.EXT_G_STATE),
					"X-4 permits alpha via ExtGState");
		}
	}

	@Test
	public void testPdfX4StillForcesCmykContent() throws Exception {
		// Until ICC-based color spaces are supported, DeviceRGB must also be
		// converted to CMYK under X-4.
		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_PDFX4)
				.withCompression(PDFParams.Compression.NONE);
		assertEquals(PDFParams.ColorMode.CMYK, params.colorMode());
		final var file = generate("pdfx4_cmyk.pdf", params);
		try (final var doc = Loader.loadPDF(file)) {
			final String stream;
			try (final var contents = doc.getPage(0).getContents()) {
				stream = new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1);
			}
			assertFalse(stream.contains(" rg"), "DeviceRGB must be converted under X-4 as well");
		}
	}

	@Test
	public void testConfiguredOutputIntentIsWritten() throws Exception {
		// A characterized printing condition registered at color.org
		// (identifier-only reference with registry name and embedded profile).
		final byte[] profile;
		try (final var in = PDFWriterImpl.class.getResourceAsStream("Probev1_ICCv2.icc")) {
			profile = in.readAllBytes();
		}
		final var intent = new OutputIntent("JC200103", "Japan Color 2001 Coated",
				OutputIntent.ICC_REGISTRY, "Japan Color 2001 Coated (probe profile for test)", profile, 4);
		final var file = generate("pdfx_output_intent_custom.pdf",
				PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A).withOutputIntent(intent));

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("(JC200103)"), "Configured identifier must be written");
		assertTrue(raw.contains("/RegistryName (http://www.color.org)"), "Registry name must be written");
		assertTrue(raw.contains("/OutputCondition ("), "Output condition must be written");
		assertTrue(raw.contains("/DestOutputProfile"), "Embedded profile must be referenced");
	}
}
