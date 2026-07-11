package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import net.zamasoft.pdfg2d.gc.imposition.Trims;
import net.zamasoft.pdfg2d.gc.paint.CMYKColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.paint.SpotColor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.imposition.GridPDFImposition;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.RenderingIntent;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Commercial print features: spot colors (Separation), ICC-based RGB
 * content, rendering intents, generalized optional-content layers, PDF/VT
 * record parts and cut-and-stack imposition.
 */
public class CommercialPrintTest {

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
				assertTrue(result.isCompliant(), "veraPDF failures:\n" + failures);
			}
		}
	}

	@Test
	public void testSpotColorEmitsSeparation() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "spot_basic.pdf");
		final var pantone = SpotColor.create("PANTONE 185 C", CMYKColor.create(0, 0.91f, 0.76f, 0));
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withCompression(PDFParams.Compression.NONE));
			try (final var gc = new PDFGC(pdf.nextPage(200, 200))) {
				gc.setFillPaint(pantone);
				gc.fill(new Rectangle2D.Double(0, 0, 200, 100));
				gc.setFillPaint(pantone.tint(0.5f)); // same plate, 50% screen
				gc.fill(new Rectangle2D.Double(0, 100, 200, 100));
			}
			pdf.close();
			builder.close();
		}

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/Separation /PANTONE#20185#20C"), "Separation color space expected");
		assertEquals(1, raw.split("/Separation", -1).length - 1,
				"The same colorant must reuse one color space object");
		assertTrue(raw.contains("scn"), "Tint must be set via scn");

		// Viewers render via the tint transform: expect roughly the alternate
		try (final var doc = Loader.loadPDF(file)) {
			final var image = new PDFRenderer(doc).renderImage(0);
			final var full = new java.awt.Color(image.getRGB(100, 40));
			assertTrue(full.getRed() > 180 && full.getGreen() < 100,
					"Full tint must render like the alternate, was " + full);
			final var half = new java.awt.Color(image.getRGB(100, 160));
			assertTrue(half.getGreen() > full.getGreen() + 40,
					"50% tint must be lighter, was " + half + " vs " + full);
		}
	}

	@Test
	public void testSpotColorPdfA2bCompliant() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "spot_pdfa.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFA2B));
			try (final var gc = new PDFGC(pdf.nextPage(200, 200))) {
				gc.setFillPaint(SpotColor.create("DIC 156", CMYKColor.create(0.9f, 0, 0.6f, 0)));
				gc.fill(new Rectangle2D.Double(20, 20, 160, 160));
			}
			pdf.close();
			builder.close();
		}
		assertCompliant(file, PDFAFlavour.PDFA_2_B);
	}

	@Test
	public void testSpotViaGraphics2DAndRegistration() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "spot_awt.pdf");
		try (final var g2d = new net.zamasoft.pdfg2d.PDFGraphics2D(file, 200, 200)) {
			g2d.setPaint(new net.zamasoft.pdfg2d.g2d.util.SpotPaint(
					SpotColor.create("GOLD", CMYKColor.create(0.2f, 0.3f, 0.9f, 0.1f))));
			g2d.fill(new Rectangle2D.Double(10, 10, 100, 100));
			g2d.setPaint(new net.zamasoft.pdfg2d.g2d.util.SpotPaint(SpotColor.REGISTRATION));
			g2d.fill(new Rectangle2D.Double(120, 120, 40, 40));
		}
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/Separation /GOLD"));
		assertTrue(raw.contains("/Separation /All"), "Registration color uses the All colorant");
	}

	@Test
	public void testICCBasedRGBContent() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "icc_rgb.pdf");
		final var params = PDFParams.createDefault()
				.withSRGBProfile()
				.withRenderingIntent(RenderingIntent.PERCEPTUAL)
				.withCompression(PDFParams.Compression.NONE);
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);
			try (final var gc = new PDFGC(pdf.nextPage(200, 200))) {
				gc.setFillPaint(RGBColor.create(1, 0, 0));
				gc.fill(new Rectangle2D.Double(0, 0, 200, 200));
			}
			pdf.close();
			builder.close();
		}
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/ICCBased"), "RGB content must use the ICCBased color space");
		assertTrue(raw.contains("/Perceptual ri"), "The default rendering intent must be emitted");
		try (final var doc = Loader.loadPDF(file)) {
			final String stream;
			try (final var in = doc.getPage(0).getContents()) {
				stream = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
			}
			assertFalse(stream.contains(" rg"), "DeviceRGB operator must not appear");
			assertTrue(stream.contains(" scn"), "ICC colors are set via scn");
		}
	}

	@Test
	public void testPdfX4KeepsRGBWithProfile() throws Exception {
		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_PDFX4)
				.withSRGBProfile();
		assertEquals(PDFParams.ColorMode.PRESERVE, params.effectiveColorMode(),
				"X-4 with an RGB profile keeps the RGB workflow");

		final var x1a = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_PDFX1A)
				.withSRGBProfile();
		assertEquals(PDFParams.ColorMode.CMYK, x1a.effectiveColorMode(),
				"X-1a never allows ICC-managed RGB");
	}

	@Test
	public void testOptionalContentLayers() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "layers.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withCompression(PDFParams.Compression.NONE));
			final var proof = pdf.createOptionalContentGroup("校正メモ", true, false, false, false);
			final var grid = pdf.createOptionalContentGroup("グリッド", true, true, true, true);
			try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
				gc.beginLayer(proof);
				gc.setFillPaint(RGBColor.create(1, 0, 0));
				gc.fill(new Rectangle2D.Double(10, 10, 100, 40));
				gc.endLayer();

				gc.beginLayer(grid);
				gc.setFillPaint(RGBColor.create(0, 0, 1));
				gc.fill(new Rectangle2D.Double(10, 60, 100, 40));
				gc.endLayer();
			}
			pdf.close();
			builder.close();
		}

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/OC /MC0 BDC") || raw.contains("/OC /MC1 BDC"),
				"Content must be wrapped in optional-content marked sequences");
		assertTrue(raw.contains("/Locked ["), "Locked layers must be listed");
		assertTrue(raw.contains("/OFF ["), "Initially hidden layers must be listed");

		try (final var doc = Loader.loadPDF(file)) {
			final var oc = doc.getDocumentCatalog().getOCProperties();
			assertNotNull(oc);
			final var names = List.of(oc.getGroupNames());
			assertTrue(names.contains("校正メモ") && names.contains("グリッド"), String.valueOf(names));
			assertFalse(oc.isGroupEnabled("校正メモ"), "The proof layer starts hidden");
			assertTrue(oc.isGroupEnabled("グリッド"));
		}
	}

	@Test
	public void testVTDocumentParts() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "vt_records.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFVT1));
			for (var record = 0; record < 3; ++record) {
				if (record > 0) {
					pdf.nextDocumentPart(Map.of("RecipientID", "R-" + record));
				}
				for (var p = 0; p < 2; ++p) {
					try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
						gc.setFillPaint(RGBColor.create(0, 0.5f, record / 2f));
						gc.fill(new Rectangle2D.Double(20, 20, 100, 50));
					}
				}
			}
			pdf.close();
			builder.close();
		}

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertEquals(3, raw.split("/Start", -1).length - 1, "One DPart leaf per record");
		assertTrue(raw.contains("/DPM"), "Record metadata must be written");
		assertTrue(raw.contains("(R-1)") && raw.contains("(R-2)"));
		try (final var doc = Loader.loadPDF(file)) {
			assertEquals(6, doc.getNumberOfPages());
		}
	}

	@Test
	public void testCutAndStackOrder() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "cut_and_stack.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withCompression(PDFParams.Compression.NONE));
			final var imp = new GridPDFImposition(pdf, 2, 1, GridPDFImposition.Order.CUT_AND_STACK);
			imp.setPageWidth(100);
			imp.setPageHeight(100);
			imp.setTrims(Trims.NONE);
			imp.fitPaperWidth();
			imp.fitPaperHeight();
			for (var i = 0; i < 6; ++i) {
				final var gc = imp.nextPage();
				gc.setFillPaint(RGBColor.create(i / 5f, 0, 0));
				gc.fill(new Rectangle2D.Double(5, 5, 50, 50));
				imp.closePage();
			}
			imp.finish();
			pdf.close();
			builder.close();
		}

		try (final var doc = Loader.loadPDF(file)) {
			assertEquals(3, doc.getNumberOfPages(), "6 pages on a 2-cell grid = 3 sheets");
			final var pattern = Pattern.compile("/T(\\d+)\\s+Do");
			final var expected = List.of(List.of(0, 3), List.of(1, 4), List.of(2, 5));
			for (var i = 0; i < 3; ++i) {
				final var order = new java.util.ArrayList<Integer>();
				try (final var in = doc.getPage(i).getContents()) {
					final var m = pattern.matcher(new String(in.readAllBytes(), StandardCharsets.ISO_8859_1));
					while (m.find()) {
						order.add(Integer.parseInt(m.group(1)));
					}
				}
				assertEquals(expected.get(i), order, "Sheet " + i + " cut-and-stack order");
			}
		}
	}
}
