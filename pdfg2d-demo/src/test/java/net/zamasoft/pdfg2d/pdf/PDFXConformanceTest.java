package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.paint.SpotColor;
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
		assertEquals(PDFParams.ColorMode.CMYK, params.effectiveColorMode(),
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
	public void testCmykGradientUsesIccExceptForAllNeutralStops() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "pdfx_cmyk_gradients.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A));
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				gc.setFillPaint(new LinearGradient(50, 50, 250, 50, new double[] { 0, 1 },
						new Color[] { RGBColor.create(1, 0, 0), RGBColor.create(0, 0, 1) },
						new AffineTransform()));
				gc.fill(new Rectangle2D.Double(50, 50, 200, 80));
				gc.setFillPaint(new LinearGradient(50, 180, 250, 180, new double[] { 0, 1 },
						new Color[] { GrayColor.create(.25f), RGBColor.create(.75f, .75f, .75f) },
						new AffineTransform()));
				gc.fill(new Rectangle2D.Double(50, 180, 200, 80));
			}
			pdf.close();
			builder.close();
		}

		var foundIcc = false;
		var foundBlackOnly = false;
		try (final var document = Loader.loadPDF(file)) {
			final var patterns = document.getPage(0).getResources().getCOSObject().getCOSDictionary(COSName.PATTERN);
			for (final var value : patterns.getValues()) {
				final var pattern = (COSDictionary) (value instanceof COSObject object ? object.getObject() : value);
				final var shading = pattern.getCOSDictionary(COSName.SHADING);
				final var function = shading.getCOSDictionary(COSName.getPDFName("Function"));
				final var c0 = function.getCOSArray(COSName.getPDFName("C0")).toFloatArray();
				final var c1 = function.getCOSArray(COSName.getPDFName("C1")).toFloatArray();
				if (c0.length == 4 && c1.length == 4
						&& c0[0] == 0 && c0[1] == 0 && c0[2] == 0
						&& c1[0] == 0 && c1[1] == 0 && c1[2] == 0) {
					foundBlackOnly = Math.abs(c0[3] - .75f) < .001f && Math.abs(c1[3] - .25f) < .001f;
				} else if (c0.length == 4 && c1.length == 4) {
					foundIcc = c0[0] < .05f && c0[1] > .85f && c0[2] > .85f && c0[3] < .05f
							&& c1[0] > .85f && c1[1] > .70f && c1[2] < .05f;
				}
			}
		}
		assertTrue(foundIcc, "mixed gradient stops must use ICC CMYK conversion");
		assertTrue(foundBlackOnly, "an all-neutral gradient must use only K");
	}

	@Test
	public void testPdfX4RgbShadingsAreIccBased() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "pdfx4_rgb_shadings.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX4));
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				gc.setFillPaint(new LinearGradient(50, 50, 250, 50, new double[] { 0, 1 },
						new Color[] { RGBColor.create(1, 0, 0), RGBColor.create(0, 0, 1) },
						new AffineTransform()));
				gc.fill(new Rectangle2D.Double(50, 50, 200, 80));
				gc.setFillPaint(new net.zamasoft.pdfg2d.gc.paint.ConicGradient(150, 250, 0,
						new double[] { 0, 1 }, new Color[] { RGBColor.create(1, 0, 0), RGBColor.create(0, 1, 0) },
						new AffineTransform()));
				gc.fill(new Rectangle2D.Double(50, 180, 200, 140));
			}
			pdf.close();
			builder.close();
		}
		var shadings = 0;
		try (final var document = Loader.loadPDF(file)) {
			final var patterns = document.getPage(0).getResources().getCOSObject().getCOSDictionary(COSName.PATTERN);
			for (final var value : patterns.getValues()) {
				final var pattern = (COSDictionary) (value instanceof COSObject object ? object.getObject() : value);
				final var shading = pattern.getCOSDictionary(COSName.SHADING);
				final var colorSpace = shading.getDictionaryObject(COSName.COLORSPACE);
				assertTrue(colorSpace instanceof COSArray, "X-4 RGB shading must be [/ICCBased ...], was " + colorSpace);
				assertEquals("ICCBased", ((COSName) ((COSArray) colorSpace).get(0)).getName());
				++shadings;
			}
		}
		assertEquals(2, shadings, "axial and type 4 mesh");
	}

	@Test
	public void testConflictingNamedColorDefinitionsAreRejected() throws Exception {
		final var out = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(out);
		final var pdf = new PDFWriterImpl(builder,
				PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A));
		pdf.useSeparation("Conflict Spot", RGBColor.create(1, 0, 0));
		final var separationError = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> pdf.useSeparation("Conflict Spot", RGBColor.create(0, 0, 1)));
		assertTrue(separationError.getMessage().contains("Conflict Spot"));
		assertTrue(separationError.getMessage().contains("and"));

		pdf.useDeviceN(new SpotColor[] {
				SpotColor.create("Ink A", RGBColor.create(1, 0, 0)),
				SpotColor.create("Ink B", RGBColor.create(0, 0, 1)) });
		final var deviceNError = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> pdf.useDeviceN(new SpotColor[] {
						SpotColor.create("Ink A", RGBColor.create(1, 0, 0)),
						SpotColor.create("Ink B", RGBColor.create(0, 1, 0)) }));
		assertTrue(deviceNError.getMessage().contains("Ink A"));
		assertTrue(deviceNError.getMessage().contains("Ink B"));
		assertTrue(deviceNError.getMessage().contains("and"));
		pdf.close();
		builder.close();
	}

	@Test
	public void testDefaultOutputIntentCarriesInfo() throws Exception {
		final var file = generate("pdfx_output_intent_default.pdf",
				PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A));
		try (final var doc = Loader.loadPDF(file)) {
			final var intents = doc.getDocumentCatalog().getCOSObject().getCOSArray(COSName.OUTPUT_INTENTS);
			assertEquals(1, intents.size());
			final var intent = (org.apache.pdfbox.cos.COSDictionary) intents.getObject(0);
			assertEquals("GTS_PDFX", intent.getNameAsString(COSName.S));
			assertEquals("FOGRA39", intent.getString(COSName.OUTPUT_CONDITION_IDENTIFIER));
			assertEquals("ISO Coated v2 300% (ECI)", intent.getString(COSName.OUTPUT_CONDITION));
			assertEquals(OutputIntent.ICC_REGISTRY, intent.getString(COSName.REGISTRY_NAME));
			assertEquals("Offset printing, ISO 12647-2:2004/Amd 1, paper type 1/2 (coated), TAC 300%",
					intent.getString(COSName.INFO));
			final var profile = intent.getCOSStream(COSName.DEST_OUTPUT_PROFILE);
			assertTrue(profile != null, "DestOutputProfile must be embedded");
			assertEquals(4, profile.getInt(COSName.N));
			try (final var in = profile.createInputStream()) {
				assertEquals("c6b4b62f0726243742eced8b9669476a6be89e581f50a7600ed8b6fcbb9cdab8",
						HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
			}
		}
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
	public void testPdfX4DefaultUsesIccBasedRgb() throws Exception {
		// PDFWriterImplはX-4の既定PRESERVEへ同梱sRGBを補い、
		// DeviceRGBではなくICCBasedのベクタ色として保持する。
		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_PDFX4)
				.withCompression(PDFParams.Compression.NONE);
		assertEquals(PDFParams.ColorMode.PRESERVE, params.colorMode());
		final var file = generate("pdfx4_iccbased_rgb.pdf", params);
		try (final var doc = Loader.loadPDF(file)) {
			final String stream;
			try (final var contents = doc.getPage(0).getContents()) {
				stream = new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1);
			}
			assertFalse(stream.contains(" rg"), "DeviceRGB operator must not be emitted under X-4");
			assertTrue(stream.contains(" scn"), "RGB vector color must use an ICCBased scn operator");
			final var colorSpaces = doc.getPage(0).getResources().getCOSObject()
					.getCOSDictionary(COSName.COLORSPACE);
			final var iccBased = colorSpaces.getValues().stream().map(value ->
					value instanceof COSObject object ? object.getObject() : value)
					.filter(value -> value instanceof COSArray array && "ICCBased".equals(array.getName(0)))
					.map(COSArray.class::cast).findFirst().orElseThrow();
			final var profile = (COSStream) iccBased.getObject(1);
			try (final var actual = profile.createInputStream();
					final var expected = PDFWriterImpl.class
							.getResourceAsStream("sRGB_IEC61966-2-1_no_black_scaling.icc")) {
				assertArrayEquals(expected.readAllBytes(), actual.readAllBytes(),
						"The injected vector profile must be the bundled sRGB profile");
			}
		}
	}

	@Test
	public void testAnnotationOutsideBleedIsAllowed() throws Exception {
		// ISO 15930 permits annotations entirely outside the bleed area, so
		// proofing notes can live in the slug/marks area.
		final var file = TestOutputFiles.outputFile(getClass(), "pdfx_annot_slug.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A));
			final var page = pdf.nextPage(595, 842);
			page.setTrimBox(new Rectangle2D.Double(60, 60, 475, 722));
			final var link = new net.zamasoft.pdfg2d.pdf.annot.LinkAnnot();
			link.setShape(new Rectangle2D.Double(5, 5, 40, 20)); // in the slug area
			link.setURI(java.net.URI.create("https://example.com/proof"));
			page.addAnnotation(link);
			page.close();
			pdf.close();
			builder.close();
		}
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/Annots"), "The slug-area annotation must be emitted");
	}

	@Test
	public void testAnnotationInsideTrimIsRejected() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "pdfx_annot_inside.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A));
			final var page = pdf.nextPage(595, 842);
			page.setTrimBox(new Rectangle2D.Double(60, 60, 475, 722));
			final var link = new net.zamasoft.pdfg2d.pdf.annot.LinkAnnot();
			link.setShape(new Rectangle2D.Double(100, 100, 50, 50)); // on the printed page
			link.setURI(java.net.URI.create("https://example.com"));
			page.addAnnotation(link);
			org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, page::close,
					"An annotation inside the finished page area must be rejected under PDF/X");
		}
	}

	@Test
	public void testPdfXRejectsInvalidExplicitOutputIntent() throws Exception {
		final byte[] srgb;
		try (final var in = PDFWriterImpl.class.getResourceAsStream("sRGB_IEC61966-2-1_no_black_scaling.icc")) {
			srgb = in == null ? null : in.readAllBytes();
		}
		final var garbage = new OutputIntent("Broken", null, OutputIntent.ICC_REGISTRY, null,
				"not an icc profile".getBytes(StandardCharsets.US_ASCII), 4);
		final var noProfile = new OutputIntent("JC200103", null, OutputIntent.ICC_REGISTRY, null, null, 4);
		for (final var version : new PDFParams.Version[] { PDFParams.Version.V_PDFX1A, PDFParams.Version.V_PDFX4 }) {
			for (final var intent : new OutputIntent[] { garbage, noProfile }) {
				final var params = PDFParams.createDefault().withVersion(version).withOutputIntent(intent);
				org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
						() -> new PDFWriterImpl(new StreamFragmentedOutput(new ByteArrayOutputStream()), params),
						version + " must reject " + intent.outputConditionIdentifier());
			}
			if (srgb != null) {
				final var rgb = new OutputIntent("sRGB IEC61966-2.1", null, OutputIntent.ICC_REGISTRY, null, srgb, 3);
				final var params = PDFParams.createDefault().withVersion(version).withOutputIntent(rgb);
				org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
						() -> new PDFWriterImpl(new StreamFragmentedOutput(new ByteArrayOutputStream()), params),
						version + " must reject a monitor RGB profile");
			}
		}
		// 通常 PDF は従来どおり受け付ける
		final var plain = PDFParams.createDefault().withVersion(PDFParams.Version.V_1_7).withOutputIntent(noProfile);
		new PDFWriterImpl(new StreamFragmentedOutput(new ByteArrayOutputStream()), plain).close();
	}

	@Test
	public void testConfiguredOutputIntentIsWritten() throws Exception {
		// A characterized printing condition registered at color.org
		// (identifier-only reference with registry name and embedded profile).
		final byte[] profile;
		try (final var in = PDFWriterImpl.class.getResourceAsStream("ISOcoated_v2_300_eci.icc")) {
			profile = in.readAllBytes();
		}
		final var intent = new OutputIntent("JC200103", "Japan Color 2001 Coated",
				OutputIntent.ICC_REGISTRY, "Explicit output intent for test", profile, 4);
		final var file = generate("pdfx_output_intent_custom.pdf",
				PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A).withOutputIntent(intent));

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("(JC200103)"), "Configured identifier must be written");
		assertTrue(raw.contains("/RegistryName (http://www.color.org)"), "Registry name must be written");
		assertTrue(raw.contains("/OutputCondition ("), "Output condition must be written");
		assertTrue(raw.contains("/DestOutputProfile"), "Embedded profile must be referenced");
	}
}
