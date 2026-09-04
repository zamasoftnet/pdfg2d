package net.zamasoft.pdfg2d.pdf.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.GroupEffects.DropShadow;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.TaggedParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** Byte-level tests for captured element filters in {@link PDFGC}. */
public class PDFGCFilterTest {
	private static final double PAGE_SIZE = 300;
	private static final double GROUP_SIZE = 72;
	private static final Rectangle2D SHAPE = new Rectangle2D.Double(12, 14, 30, 24);

	@FunctionalInterface
	private interface Drawing {
		GC.GroupEffectsResult draw(PDFGC gc) throws Exception;
	}

	private record Rendered(byte[] bytes, GC.GroupEffectsResult result) {
	}

	private record PdfStream(String dictionary, byte[] data) {
		boolean isImage() {
			return Pattern.compile("/Subtype\\s*/Image\\b").matcher(this.dictionary).find();
		}

		boolean isForm() {
			return Pattern.compile("/Subtype\\s*/Form\\b").matcher(this.dictionary).find();
		}
	}

	private record PdfMatrix(double a, double b, double c, double d, double e, double f) {
	}

	private static Rendered render(final PDFParams params, final Drawing drawing) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder, params);
		final GC.GroupEffectsResult result;
		try (final var gc = new PDFGC(pdf.nextPage(PAGE_SIZE, PAGE_SIZE))) {
			result = drawing.draw(gc);
		}
		pdf.close();
		builder.close();
		return new Rendered(bytes.toByteArray(), result);
	}

	private static PDFParams uncompressed() {
		return PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);
	}

	private static Image capturedRect(final PDFGC gc, final double width, final double height) {
		return capturedShape(gc, width, height, SHAPE);
	}

	private static Image capturedShape(final PDFGC gc, final double width, final double height,
			final Rectangle2D shape) {
		final GroupImageGC group = gc.createFilterGroup(width, height);
		assertTrue(group.supports(GC.Capability.GROUP_FILTER));
		assertTrue(group.supports(GC.Capability.GAUSSIAN_BLUR));
		assertTrue(group.supports(GC.Capability.DROP_SHADOW));
		group.setFillPaint(RGBColor.create(.8f, .2f, .1f));
		group.fill(shape);
		return group.finish();
	}

	@Test
	public void testColorMatrixRasterizesOneTaggedImageWithSoftMask() throws Exception {
		final float[] grayscale = {
				.2126f, .7152f, .0722f, 0, 0,
				.2126f, .7152f, .0722f, 0, 0,
				.2126f, .7152f, .0722f, 0, 0,
				0, 0, 0, 1, 0 };
		final var rendered = render(uncompressed().withTagged(new TaggedParams("ja", false)), gc -> {
			assertTrue(gc.supports(GC.Capability.GROUP_FILTER));
			assertTrue(gc.rasterizesGroupEffects());
			return gc.drawGroupEffects(capturedRect(gc, GROUP_SIZE, GROUP_SIZE),
					new GroupEffects(grayscale, 0, null, 1));
		});

		assertEquals(GC.GroupEffectsResult.RASTERIZED, rendered.result);
		assertRasterImage(rendered.bytes);
		final String raw = new String(rendered.bytes, StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/Figure"), raw);
		assertTrue(raw.contains("filter"), "the generated Figure must have the filter fallback alt text");
		assertFalse(contentStreams(rendered.bytes).contains("/Artifact BMC"),
				"a filtered element is content, not an artifact");
	}

	@Test
	public void testBlurRasterizesOneImageWithSoftMask() throws Exception {
		final var rendered = render(uncompressed(), gc -> gc.drawGroupEffects(
				capturedRect(gc, GROUP_SIZE, GROUP_SIZE), new GroupEffects(null, 2, null, 1)));
		assertEquals(GC.GroupEffectsResult.RASTERIZED, rendered.result);
		assertRasterImage(rendered.bytes);
	}

	@Test
	public void testLargeGroupRasterizesOnlyPaddedContentBoundsAndOffsetsPlacement() throws Exception {
		final PDFParams params = uncompressed();
		final Rectangle2D content = new Rectangle2D.Double(100, 100, 50, 20);
		final var rendered = render(params, gc -> gc.drawGroupEffects(
				capturedShape(gc, 1000, 1000, content), new GroupEffects(null, 2, null, 1)));
		assertEquals(GC.GroupEffectsResult.RASTERIZED, rendered.result);

		final double scale = params.filterRasterDpi() / 72.0;
		final int pad = (int) Math.ceil(3 * 2 * scale) + 1;
		final PdfStream image = imageStreams(rendered.bytes).stream()
				.filter(stream -> stream.dictionary.contains("/SMask"))
				.findFirst().orElseThrow();
		final int imageWidth = dictionaryInt(image.dictionary, "Width");
		final int imageHeight = dictionaryInt(image.dictionary, "Height");
		assertEquals(content.getWidth() * scale + 2 * pad, imageWidth, 3,
				"the raster width must follow the content, not the 1000-unit group");
		assertEquals(content.getHeight() * scale + 2 * pad, imageHeight, 3,
				"the raster height must follow the content, not the 1000-unit group");
		assertTrue(imageWidth < 1000 * scale / 2);
		assertTrue(imageHeight < 1000 * scale / 2);

		final double regionX = (Math.floor(content.getMinX() * scale) - pad) / scale;
		final double regionY = (Math.floor(content.getMinY() * scale) - pad) / scale;
		final double placementScale = 1 / scale;
		final PdfMatrix placement = matrices(contentStreams(rendered.bytes)).stream()
				.filter(matrix -> Math.abs(matrix.a - placementScale) < 1e-6
						&& Math.abs(matrix.d - placementScale) < 1e-6
						&& Math.abs(matrix.b) < 1e-9 && Math.abs(matrix.c) < 1e-9)
				.findFirst().orElseThrow();
		assertEquals(regionX, placement.e, .01);
		assertEquals(PAGE_SIZE * (1 - placementScale) - regionY, placement.f, .01,
				"the PDF-space cm translation must encode the cropped region origin");
	}

	@Test
	public void testDropShadowRasterizesOneImageWithSoftMask() throws Exception {
		final var shadow = new DropShadow(4, 5, 2, RGBAColor.create(0, 0, 0, .6f));
		final var rendered = render(uncompressed(), gc -> gc.drawGroupEffects(
				capturedRect(gc, GROUP_SIZE, GROUP_SIZE), new GroupEffects(null, 0, shadow, 1)));
		assertEquals(GC.GroupEffectsResult.RASTERIZED, rendered.result);
		assertRasterImage(rendered.bytes);
	}

	@Test
	public void testOpacityOnlyStaysVectorAndUsesFormAlpha() throws Exception {
		final var rendered = render(uncompressed(), gc -> gc.drawGroupEffects(
				capturedRect(gc, GROUP_SIZE, GROUP_SIZE), new GroupEffects(null, 0, null, .4)));
		assertEquals(GC.GroupEffectsResult.VECTOR, rendered.result);
		assertEquals(0, imageStreams(rendered.bytes).size());
		assertEquals(1, formStreams(rendered.bytes).size());
		assertEquals(1, countOperator(contentStreams(rendered.bytes), "Do"));
		final String raw = new String(rendered.bytes, StandardCharsets.ISO_8859_1);
		assertTrue(Pattern.compile("/ca\\s+0?\\.4(?=\\s|/|>>)").matcher(raw).find(), raw);
	}

	@Test
	public void testIdentityEffectsStayVectorInAForm() throws Exception {
		final var rendered = render(uncompressed(), gc -> gc.drawGroupEffects(
				capturedRect(gc, GROUP_SIZE, GROUP_SIZE), GroupEffects.NONE));
		assertEquals(GC.GroupEffectsResult.VECTOR, rendered.result);
		assertEquals(0, imageStreams(rendered.bytes).size());
		assertEquals(1, formStreams(rendered.bytes).size());
		assertEquals(1, countOperator(contentStreams(rendered.bytes), "Do"));
	}

	@Test
	public void testPdfA1ReturnsUnsupportedWithoutDrawing() throws Exception {
		final var rendered = render(uncompressed().withVersion(PDFParams.Version.V_PDFA1B), gc -> {
			assertFalse(gc.supports(GC.Capability.GROUP_FILTER));
			assertFalse(gc.supports(GC.Capability.DROP_SHADOW));
			return gc.drawGroupEffects(capturedRect(gc, GROUP_SIZE, GROUP_SIZE),
					new GroupEffects(null, 2, null, 1));
		});
		assertEquals(GC.GroupEffectsResult.UNSUPPORTED, rendered.result);
		assertEquals(0, imageStreams(rendered.bytes).size());
		assertEquals(0, formStreams(rendered.bytes).size());
		assertEquals(0, countOperator(contentStreams(rendered.bytes), "Do"));
	}

	@Test
	public void testPixelBudgetFallsBackToEffectlessForm() throws Exception {
		assertEquals(300, PDFParams.createDefault().filterRasterDpi());
		assertEquals(72, PDFParams.createDefault().withFilterRasterDpi(1).filterRasterDpi());
		assertEquals(600, PDFParams.createDefault().withFilterRasterDpi(1000).filterRasterDpi());
		assertEquals(240, PDFParams.createDefault().withFilterRasterDpi(240)
				.withVersion(PDFParams.Version.V_1_7).filterRasterDpi());

		final var rendered = render(uncompressed().withFilterRasterDpi(600), gc -> gc.drawGroupEffects(
				capturedShape(gc, 500, 500, new Rectangle2D.Double(0, 0, 500, 500)),
				new GroupEffects(null, 1, null, 1)));
		assertEquals(GC.GroupEffectsResult.LIMIT_FALLBACK, rendered.result);
		assertEquals(0, imageStreams(rendered.bytes).size());
		assertEquals(1, formStreams(rendered.bytes).size());
		assertEquals(1, countOperator(contentStreams(rendered.bytes), "Do"));
	}

	@Test
	public void testGraphicsStateIsRestoredAfterRasterization() throws Exception {
		final var red = RGBColor.create(1, 0, 0);
		final var transform = AffineTransform.getTranslateInstance(17, 23);
		final var rendered = render(uncompressed(), gc -> {
			gc.setFillPaint(red);
			gc.setFillAlpha(.35f);
			gc.setBlendMode(BlendMode.MULTIPLY);
			gc.transform(transform);
			final var result = gc.drawGroupEffects(capturedRect(gc, GROUP_SIZE, GROUP_SIZE),
					new GroupEffects(null, 1, null, .8));
			assertSame(red, gc.getFillPaint());
			assertEquals(.35f, gc.getFillAlpha());
			assertEquals(BlendMode.MULTIPLY, gc.getBlendMode());
			assertEquals(transform, gc.getTransform());
			gc.fill(new Rectangle2D.Double(100, 20, 10, 10));
			return result;
		});
		assertEquals(GC.GroupEffectsResult.RASTERIZED, rendered.result);
	}

	private static void assertRasterImage(final byte[] pdf) {
		final List<PdfStream> images = imageStreams(pdf);
		assertEquals(2, images.size(), "the generated image and its SMask are separate XObjects");
		assertEquals(1, images.stream().filter(s -> s.dictionary.contains("/SMask")).count());
		assertEquals(0, formStreams(pdf).size());
		assertEquals(1, countOperator(contentStreams(pdf), "Do"));
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
			final var objectMatch = objectPattern.matcher(raw);
			final int previousObjectEnd = raw.lastIndexOf("endobj", marker);
			objectMatch.region(previousObjectEnd < 0 ? 0 : previousObjectEnd + "endobj".length(), marker);
			assertTrue(objectMatch.find(), "stream must belong to an indirect object");
			final int dictionaryStart = raw.indexOf("<<", objectMatch.start());
			final int dictionaryEnd = raw.lastIndexOf(">>", marker);
			if (dictionaryStart < 0 || dictionaryEnd < dictionaryStart) {
				search = afterWord;
				continue;
			}
			final String dictionary = raw.substring(dictionaryStart, dictionaryEnd + 2);
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
			final int dataEnd = dataStart + Integer.parseInt(match.group(1));
			assertTrue(dataEnd <= pdf.length, "stream length extends beyond the PDF");
			streams.add(new PdfStream(dictionary, Arrays.copyOfRange(pdf, dataStart, dataEnd)));
			final int endStream = raw.indexOf("endstream", dataEnd);
			search = endStream >= 0 ? endStream + "endstream".length() : dataEnd;
		}
	}

	private static List<PdfStream> imageStreams(final byte[] pdf) {
		return streams(pdf).stream().filter(PdfStream::isImage).toList();
	}

	private static List<PdfStream> formStreams(final byte[] pdf) {
		return streams(pdf).stream().filter(PdfStream::isForm).toList();
	}

	private static String contentStreams(final byte[] pdf) {
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

	private static int dictionaryInt(final String dictionary, final String name) {
		final var matcher = Pattern.compile("/" + Pattern.quote(name) + "\\s+(\\d+)").matcher(dictionary);
		assertTrue(matcher.find(), "missing /" + name + " in " + dictionary);
		return Integer.parseInt(matcher.group(1));
	}

	private static List<PdfMatrix> matrices(final String content) {
		final String number = "([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))";
		final var matcher = Pattern.compile(number + "\\s+" + number + "\\s+" + number + "\\s+"
				+ number + "\\s+" + number + "\\s+" + number + "\\s+cm\\b").matcher(content);
		final var matrices = new ArrayList<PdfMatrix>();
		while (matcher.find()) {
			matrices.add(new PdfMatrix(
					Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)),
					Double.parseDouble(matcher.group(3)), Double.parseDouble(matcher.group(4)),
					Double.parseDouble(matcher.group(5)), Double.parseDouble(matcher.group(6))));
		}
		return matrices;
	}
}
