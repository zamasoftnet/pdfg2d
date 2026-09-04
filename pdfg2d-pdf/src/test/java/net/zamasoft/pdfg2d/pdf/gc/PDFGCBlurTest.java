package net.zamasoft.pdfg2d.pdf.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.TaggedParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** Byte-level tests for the shadow-only raster path in {@link PDFGC}. */
public class PDFGCBlurTest {
	private static final double PAGE_SIZE = 300;
	private static final Rectangle2D SHAPE = new Rectangle2D.Double(40, 50, 80, 60);

	@FunctionalInterface
	private interface Drawing {
		boolean draw(PDFGC gc) throws Exception;
	}

	private record Rendered(byte[] bytes, boolean drawn) {
	}

	private record PdfStream(int objectNumber, String dictionary, byte[] data) {
		boolean isImage() {
			return Pattern.compile("/Subtype\\s*/Image\\b").matcher(this.dictionary).find();
		}
	}

	private static Rendered render(final PDFParams params, final Drawing drawing) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder, params);
		final boolean drawn;
		try (final var gc = new PDFGC(pdf.nextPage(PAGE_SIZE, PAGE_SIZE))) {
			drawn = drawing.draw(gc);
		}
		pdf.close();
		builder.close();
		return new Rendered(bytes.toByteArray(), drawn);
	}

	private static PDFParams uncompressed() {
		return PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);
	}

	/** Reads stream bodies using their direct /Length value, without PDFBox. */
	private static List<PdfStream> streams(final byte[] pdf) {
		final String raw = new String(pdf, StandardCharsets.ISO_8859_1);
		final var streams = new ArrayList<PdfStream>();
		final var lengthPattern = Pattern.compile("/Length\\s+(\\d+)");
		final var objectPattern = Pattern.compile("(\\d+)\\s+\\d+\\s+obj\\s*<<");
		int search = 0;
		while (true) {
			final int marker = raw.indexOf("stream", search);
			if (marker < 0) {
				return streams;
			}
			final int afterWord = marker + "stream".length();
			if (afterWord >= raw.length()
					|| (raw.charAt(afterWord) != '\r' && raw.charAt(afterWord) != '\n')) {
				search = afterWord;
				continue;
			}
			final int dictionaryStart = raw.lastIndexOf("<<", marker);
			final int dictionaryEnd = raw.lastIndexOf(">>", marker);
			if (dictionaryStart < 0 || dictionaryEnd < dictionaryStart) {
				search = afterWord;
				continue;
			}
			final String dictionary = raw.substring(dictionaryStart, dictionaryEnd + 2);
			final var objectMatch = objectPattern.matcher(raw);
			final int previousObjectEnd = raw.lastIndexOf("endobj", dictionaryStart);
			objectMatch.region(previousObjectEnd < 0 ? 0 : previousObjectEnd + "endobj".length(), marker);
			assertTrue(objectMatch.find(), "stream must belong to an indirect object");
			final int objectNumber = Integer.parseInt(objectMatch.group(1));
			final var match = lengthPattern.matcher(dictionary);
			if (!match.find()) {
				search = afterWord;
				continue;
			}
			int dataStart = afterWord;
			if (raw.charAt(dataStart) == '\r') {
				++dataStart;
			}
			if (raw.charAt(dataStart) == '\n') {
				++dataStart;
			}
			final int length = Integer.parseInt(match.group(1));
			final int dataEnd = dataStart + length;
			assertTrue(dataEnd <= pdf.length, "stream length extends beyond the PDF");
			streams.add(new PdfStream(objectNumber, dictionary, Arrays.copyOfRange(pdf, dataStart, dataEnd)));
			final int endStream = raw.indexOf("endstream", dataEnd);
			search = endStream >= 0 ? endStream + "endstream".length() : dataEnd;
		}
	}

	private static List<PdfStream> imageStreams(final byte[] pdf) {
		return streams(pdf).stream().filter(PdfStream::isImage).toList();
	}

	/** Returns unfiltered page-content streams, excluding XMP metadata. */
	private static String pageContent(final byte[] pdf) {
		final var content = new StringBuilder();
		for (final PdfStream stream : streams(pdf)) {
			if (!stream.isImage() && !stream.dictionary.contains("/Filter")
					&& !stream.dictionary.contains("/Type /Metadata")) {
				content.append(new String(stream.data, StandardCharsets.ISO_8859_1));
			}
		}
		return content.toString();
	}

	private static int countOperator(final String content, final String operator) {
		return (int) Pattern.compile("(?<!\\S)" + Pattern.quote(operator) + "(?!\\S)")
				.matcher(content).results().count();
	}

	private static byte[] inflate(final byte[] data) throws Exception {
		try (final var in = new InflaterInputStream(new ByteArrayInputStream(data))) {
			return in.readAllBytes();
		}
	}

	@Test
	public void testBlurIsOneArtifactImageWithSoftMask() throws Exception {
		final var params = uncompressed().withTagged(new TaggedParams("ja", false));
		final var rendered = render(params, gc -> {
			assertTrue(gc.supports(GC.Capability.GAUSSIAN_BLUR));
			assertTrue(gc.supports(GC.Capability.CONIC_GRADIENT));
			assertTrue(gc.supports(GC.Capability.GROUP_FILTER));
			assertTrue(gc.supports(GC.Capability.DROP_SHADOW));
			for (final var capability : GC.Capability.values()) {
				if (capability != GC.Capability.GAUSSIAN_BLUR
						&& capability != GC.Capability.CONIC_GRADIENT
						&& capability != GC.Capability.GROUP_FILTER
						&& capability != GC.Capability.DROP_SHADOW) {
					assertFalse(gc.supports(capability), capability.toString());
				}
			}
			gc.setFillPaint(RGBColor.create(0.2f, 0.4f, 0.8f));
			return gc.tryFillBlurred(SHAPE, 3);
		});

		assertTrue(rendered.drawn);
		final var images = imageStreams(rendered.bytes);
		assertEquals(2, images.size(), "the color image and its SMask are separate XObjects");
		assertEquals(1, images.stream().filter(s -> s.dictionary.contains("/SMask")).count());

		final String content = pageContent(rendered.bytes);
		assertEquals(1, countOperator(content, "Do"), content);
		assertEquals(0, countOperator(content, "f"), "the shadow must not be vector-filled: " + content);
		assertEquals(0, countOperator(content, "f*"), content);
		final int artifact = content.indexOf("/Artifact BMC");
		final int draw = content.indexOf(" Do");
		final int end = content.indexOf("EMC", draw);
		assertTrue(artifact >= 0 && draw > artifact && end > draw,
				"the image Do must be enclosed by one artifact: " + content);
		assertEquals(1, countOperator(content, "BMC"), content);
		assertEquals(1, countOperator(content, "EMC"), content);
		assertFalse(new String(rendered.bytes, StandardCharsets.ISO_8859_1).contains("/Figure"),
				"a shadow must not enter the structure tree as a Figure");
	}

	@Test
	public void testFillAlphaIsBakedOnceAndDoUsesUnitAlpha() throws Exception {
		final var rendered = render(uncompressed(), gc -> {
			gc.setFillPaint(RGBAColor.create(0.8f, 0.2f, 0.1f, 0.5f));
			// Materialize ca=.5 first, so the shadow Do must explicitly restore ca=1.
			gc.fill(new Rectangle2D.Double(5, 5, 5, 5));
			return gc.tryFillBlurred(SHAPE, 2);
		});

		assertTrue(rendered.drawn);
		final var mask = imageStreams(rendered.bytes).stream()
				.filter(s -> s.dictionary.contains("/ColorSpace /DeviceGray")
						&& !s.dictionary.contains("/SMask"))
				.findFirst().orElseThrow();
		final byte[] alpha = inflate(mask.data);
		final int maxAlpha = java.util.stream.IntStream.range(0, alpha.length)
				.map(i -> alpha[i] & 0xff).max().orElseThrow();
		assertTrue(maxAlpha >= 126 && maxAlpha <= 129,
				"50% effective fill alpha must be stored in the SMask once: " + maxAlpha);

		final String raw = new String(rendered.bytes, StandardCharsets.ISO_8859_1);
		assertTrue(Pattern.compile("/ca\\s+1(?:\\.0+)?(?=\\s|/)").matcher(raw).find(),
				"the final Do must run with nonstroking alpha 1");
	}

	@Test
	public void testStateIsRestoredBeforeTheFollowingFill() throws Exception {
		final var red = RGBColor.create(1, 0, 0);
		final var rendered = render(uncompressed(), gc -> {
			gc.setFillPaint(red);
			gc.setFillAlpha(0.35f);
			gc.setBlendMode(BlendMode.MULTIPLY);
			assertTrue(gc.tryFillBlurred(SHAPE, 2));
			assertSame(red, gc.getFillPaint());
			assertEquals(0.35f, gc.getFillAlpha());
			assertEquals(BlendMode.MULTIPLY, gc.getBlendMode());
			gc.fill(new Rectangle2D.Double(180, 30, 30, 20));
			return true;
		});

		final String content = pageContent(rendered.bytes);
		final int draw = content.indexOf(" Do");
		assertTrue(draw >= 0, content);
		final String following = content.substring(draw + 3);
		assertTrue(Pattern.compile("1\\s+0\\s+0\\s+rg.*\\bre\\s+f\\b", Pattern.DOTALL)
				.matcher(following).find(), "the following fill must restore the red paint: " + following);
		final String raw = new String(rendered.bytes, StandardCharsets.ISO_8859_1);
		assertTrue(Pattern.compile("/ca\\s+0?\\.35(?=\\s|/)").matcher(raw).find(),
				"the following fill must restore ca=.35");
		assertTrue(raw.contains("/BM /Multiply") || raw.contains("/BM/Multiply"),
				"the shadow Do must preserve the caller's blend mode");
	}

	@Test
	public void testConformanceProfilesGateTheRasterPath() throws Exception {
		for (final var version : List.of(PDFParams.Version.V_PDFA1B, PDFParams.Version.V_PDFX1A)) {
			final var rendered = render(uncompressed().withVersion(version), gc -> {
				assertFalse(gc.supports(GC.Capability.GAUSSIAN_BLUR));
				return gc.tryFillBlurred(SHAPE, 2);
			});
			assertFalse(rendered.drawn, version.toString());
			assertEquals(0, imageStreams(rendered.bytes).size(), version.toString());
			assertEquals(0, countOperator(pageContent(rendered.bytes), "Do"), version.toString());
			assertEquals(0, countOperator(pageContent(rendered.bytes), "f"),
					"false means that no fallback was drawn: " + version);
		}

		final var pdfa2 = render(uncompressed().withVersion(PDFParams.Version.V_PDFA2B), gc -> {
			assertTrue(gc.supports(GC.Capability.GAUSSIAN_BLUR));
			return gc.tryFillBlurred(SHAPE, 2);
		});
		assertTrue(pdfa2.drawn);
		assertEquals(2, imageStreams(pdfa2.bytes).size());
	}

	@Test
	public void testGeneratedRgbUsesSharedIccProfileInPdfX4AndPlainPdf() throws Exception {
		for (final var version : List.of(PDFParams.Version.V_PDFX4, PDFParams.Version.V_1_7)) {
			final var rendered = render(uncompressed().withVersion(version), gc -> {
				assertTrue(gc.supports(GC.Capability.GAUSSIAN_BLUR));
				gc.setFillPaint(RGBColor.create(0.25f, 0.5f, 0.75f));
				final boolean first = gc.tryFillBlurred(SHAPE, 2);
				return first && gc.tryFillBlurred(new Rectangle2D.Double(150, 80, 40, 30), 1);
			});
			assertTrue(rendered.drawn, version.toString());
			final var allStreams = streams(rendered.bytes);
			final var mains = allStreams.stream()
					.filter(PdfStream::isImage)
					.filter(s -> s.dictionary.contains("/SMask"))
					.toList();
			assertEquals(2, mains.size());
			final var profileRefs = mains.stream().map(main -> referenceEntry(main.dictionary, "ColorSpace",
					"\\[\\s*/ICCBased\\s+(\\d+)\\s+\\d+\\s+R\\s*\\]")).distinct().toList();
			assertEquals(1, profileRefs.size(), "generated RGB images must share one ICC stream object");
			final int profileRef = profileRefs.getFirst();
			final var profile = allStreams.stream().filter(s -> s.objectNumber == profileRef)
					.findFirst().orElseThrow();
			assertFalse(profile.isImage(), profile.dictionary);
			assertTrue(Pattern.compile("/N\\s+3(?=\\s|/|>>)").matcher(profile.dictionary).find(),
					profile.dictionary);

			for (final var main : mains) {
				final int maskRef = referenceEntry(main.dictionary, "SMask", "(\\d+)\\s+\\d+\\s+R");
				final var mask = allStreams.stream().filter(s -> s.objectNumber == maskRef)
						.findFirst().orElseThrow();
				assertTrue(mask.isImage(), mask.dictionary);
				assertTrue(mask.dictionary.contains("/ColorSpace /DeviceGray"), mask.dictionary);
			}
		}
	}

	@Test
	public void testRotatedNonUniformTransformUsesLocalRasterPlacement() throws Exception {
		final var current = new AffineTransform();
		current.translate(70, 35);
		current.rotate(Math.toRadians(30));
		current.scale(2, 0.5);
		final double sigma = 2;
		final var rendered = render(uncompressed(), gc -> {
			gc.transform(current);
			gc.setFillPaint(RGBColor.BLACK);
			return gc.tryFillBlurred(SHAPE, sigma);
		});
		assertTrue(rendered.drawn);

		final String content = pageContent(rendered.bytes);
		final List<AffineTransform> matrices = matricesBeforeDo(content);
		assertTrue(matrices.size() >= 2, content);
		final AffineTransform actual = fromPdfCoordinates(matrices.get(0));
		actual.concatenate(fromPdfCoordinates(matrices.get(1)));

		// The singular values are 2 and .5; rotation does not change them.
		final double localScale = (150.0 / 72.0) * 2;
		final int pad = (int) Math.ceil(3 * sigma * localScale);
		final double rx = Math.floor(SHAPE.getMinX() * localScale) - pad;
		final double ry = Math.floor(SHAPE.getMinY() * localScale) - pad;
		final var placement = AffineTransform.getTranslateInstance(rx / localScale, ry / localScale);
		placement.scale(1 / localScale, 1 / localScale);
		final var expected = new AffineTransform(current);
		expected.concatenate(placement);
		assertTransformEquals(expected, actual, 1e-2);
	}

	@Test
	public void testGeneratedBlurIgnoresJpegAndImageSizeSettings() throws Exception {
		final var params = uncompressed()
				.withImageCompression(PDFParams.ImageCompression.JPEG)
				.withImageCompressionLossless(0)
				.withMaxImageWidth(1)
				.withMaxImageHeight(1);
		final var rendered = render(params, gc -> gc.tryFillBlurred(SHAPE, 2));
		assertTrue(rendered.drawn);
		final var images = imageStreams(rendered.bytes);
		assertEquals(2, images.size());
		for (final var image : images) {
			assertTrue(image.dictionary.contains("/FlateDecode"), image.dictionary);
			assertFalse(image.dictionary.contains("/DCTDecode"), image.dictionary);
			assertFalse(image.dictionary.contains("/JPXDecode"), image.dictionary);
		}
		final var main = images.stream().filter(s -> s.dictionary.contains("/SMask")).findFirst().orElseThrow();
		assertTrue(integerEntry(main.dictionary, "Width") > 1, "generated width must bypass maxImageWidth");
		assertTrue(integerEntry(main.dictionary, "Height") > 1, "generated height must bypass maxImageHeight");
	}

	@Test
	public void testDpiClampingAndRejectedInputs() throws Exception {
		assertEquals(150, PDFParams.createDefault().blurRasterDpi());
		assertEquals(72, PDFParams.createDefault().withBlurRasterDpi(1).blurRasterDpi());
		assertEquals(600, PDFParams.createDefault().withBlurRasterDpi(1000).blurRasterDpi());
		assertEquals(240, PDFParams.createDefault().withBlurRasterDpi(240)
				.withVersion(PDFParams.Version.V_1_7).blurRasterDpi());

		final var rejected = render(uncompressed(), gc -> {
			assertFalse(gc.tryFillBlurred(SHAPE, Double.NaN));
			assertFalse(gc.tryFillBlurred(SHAPE, Double.POSITIVE_INFINITY));
			assertFalse(gc.tryFillBlurred(SHAPE, -1));
			assertFalse(gc.tryFillBlurred(new Rectangle2D.Double(0, 0, 0, 10), 2));
			assertFalse(gc.tryFillBlurred(new Rectangle2D.Double(0, 0, 3000, 3000), 2),
					"the 16M-pixel budget must reject the layer before allocation");
			return false;
		});
		assertEquals(0, imageStreams(rejected.bytes).size());

		final var singular = render(uncompressed(), gc -> {
			gc.transform(AffineTransform.getScaleInstance(0, 1));
			return gc.tryFillBlurred(SHAPE, 2);
		});
		assertFalse(singular.drawn);
		assertEquals(0, imageStreams(singular.bytes).size());
	}

	private static int integerEntry(final String dictionary, final String name) {
		final var matcher = Pattern.compile("/" + Pattern.quote(name) + "\\s+(\\d+)").matcher(dictionary);
		assertTrue(matcher.find(), dictionary);
		return Integer.parseInt(matcher.group(1));
	}

	private static int referenceEntry(final String dictionary, final String name, final String valuePattern) {
		final var matcher = Pattern.compile("/" + Pattern.quote(name) + "\\s+" + valuePattern)
				.matcher(dictionary);
		assertTrue(matcher.find(), dictionary);
		return Integer.parseInt(matcher.group(1));
	}

	private static List<AffineTransform> matricesBeforeDo(final String content) {
		final int draw = content.indexOf(" Do");
		assertTrue(draw >= 0, content);
		final String number = "[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)";
		final var matcher = Pattern.compile("(" + number + ")\\s+(" + number + ")\\s+(" + number
				+ ")\\s+(" + number + ")\\s+(" + number + ")\\s+(" + number + ")\\s+cm")
				.matcher(content.substring(0, draw));
		final var matrices = new ArrayList<AffineTransform>();
		while (matcher.find()) {
			matrices.add(new AffineTransform(
					Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)),
					Double.parseDouble(matcher.group(3)), Double.parseDouble(matcher.group(4)),
					Double.parseDouble(matcher.group(5)), Double.parseDouble(matcher.group(6))));
		}
		return matrices;
	}

	/** Reverses PDFGraphicsOutput.writeTransform's top-left/bottom-left conjugation. */
	private static AffineTransform fromPdfCoordinates(final AffineTransform pdfTransform) {
		final var flip = new AffineTransform(1, 0, 0, -1, 0, PAGE_SIZE);
		final var result = new AffineTransform(pdfTransform);
		result.preConcatenate(flip);
		result.concatenate(flip);
		return result;
	}

	private static void assertTransformEquals(final AffineTransform expected, final AffineTransform actual,
			final double tolerance) {
		final double[] e = new double[6];
		final double[] a = new double[6];
		expected.getMatrix(e);
		actual.getMatrix(a);
		for (int i = 0; i < e.length; ++i) {
			assertEquals(e[i], a[i], tolerance, "matrix component " + i);
		}
	}
}
