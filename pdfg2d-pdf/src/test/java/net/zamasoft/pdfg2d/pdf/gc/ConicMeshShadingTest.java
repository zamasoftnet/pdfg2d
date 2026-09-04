package net.zamasoft.pdfg2d.pdf.gc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.ConicGradient;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** Byte-level tests for conic gradients encoded as Type 4 mesh shadings. */
public class ConicMeshShadingTest {
	private static final double WIDTH = 320;
	private static final double HEIGHT = 240;
	private static final Color RED = RGBColor.create(1, 0, 0);
	private static final Color GREEN = RGBColor.create(0, 1, 0);
	private static final Color BLUE = RGBColor.create(0, 0, 1);

	@FunctionalInterface
	private interface Drawing {
		void draw(PDFGC gc) throws Exception;
	}

	private record PdfStream(int objectNumber, String dictionary, byte[] encodedData) {
	}

	private record Vertex(int flag, double x, double y, double[] components) {
	}

	private record Mesh(PdfStream stream, double[] decode, List<Vertex> vertices) {
	}

	private static byte[] render(final PDFParams params, final Drawing drawing) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder, params);
		try (final var gc = new PDFGC(pdf.nextPage(WIDTH, HEIGHT))) {
			drawing.draw(gc);
		}
		pdf.close();
		builder.close();
		return bytes.toByteArray();
	}

	private static PDFParams params() {
		return PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);
	}

	private static ConicGradient conic(final double cx, final double cy, final double angle,
			final double[] fractions, final Color[] colors, final AffineTransform transform) {
		return new ConicGradient(cx, cy, angle, fractions, colors, transform);
	}

	private static void fillPage(final PDFGC gc, final ConicGradient gradient) {
		gc.setFillPaint(gradient);
		gc.fill(new Rectangle2D.Double(0, 0, WIDTH, HEIGHT));
	}

	/** Reads stream bodies by their direct /Length without depending on PDFBox. */
	private static List<PdfStream> streams(final byte[] pdf) {
		final String raw = new String(pdf, StandardCharsets.ISO_8859_1);
		final var result = new ArrayList<PdfStream>();
		final var lengthPattern = Pattern.compile("/Length\\s+(\\d+)");
		final var objectPattern = Pattern.compile("(\\d+)\\s+\\d+\\s+obj\\s*<<");
		int search = 0;
		while (true) {
			final int marker = raw.indexOf("stream", search);
			if (marker < 0) {
				return result;
			}
			final int afterWord = marker + "stream".length();
			if (afterWord >= raw.length()
					|| (raw.charAt(afterWord) != '\r' && raw.charAt(afterWord) != '\n')) {
				search = afterWord;
				continue;
			}
			final int dictionaryStart = raw.lastIndexOf("<<", marker);
			final int dictionaryEnd = raw.lastIndexOf(">>", marker);
			assertTrue(dictionaryStart >= 0 && dictionaryEnd >= dictionaryStart);
			final String dictionary = raw.substring(dictionaryStart, dictionaryEnd + 2);
			final var lengthMatch = lengthPattern.matcher(dictionary);
			assertTrue(lengthMatch.find(), dictionary);
			final var objectMatch = objectPattern.matcher(raw);
			final int previousObjectEnd = raw.lastIndexOf("endobj", dictionaryStart);
			objectMatch.region(previousObjectEnd < 0 ? 0 : previousObjectEnd + "endobj".length(), marker);
			assertTrue(objectMatch.find(), "stream must belong to an indirect object");

			int dataStart = afterWord;
			if (raw.charAt(dataStart) == '\r') {
				++dataStart;
			}
			if (raw.charAt(dataStart) == '\n') {
				++dataStart;
			}
			final int dataEnd = dataStart + Integer.parseInt(lengthMatch.group(1));
			assertTrue(dataEnd <= pdf.length);
			result.add(new PdfStream(Integer.parseInt(objectMatch.group(1)), dictionary,
					Arrays.copyOfRange(pdf, dataStart, dataEnd)));
			final int endStream = raw.indexOf("endstream", dataEnd);
			search = endStream >= 0 ? endStream + "endstream".length() : dataEnd;
		}
	}

	private static byte[] asciiHexDecode(final byte[] encoded) {
		final var out = new ByteArrayOutputStream();
		int high = -1;
		for (final byte value : encoded) {
			final int c = value & 0xff;
			if (c == '>') {
				break;
			}
			final int digit = Character.digit((char) c, 16);
			if (digit < 0) {
				continue;
			}
			if (high < 0) {
				high = digit;
			} else {
				out.write((high << 4) | digit);
				high = -1;
			}
		}
		if (high >= 0) {
			out.write(high << 4);
		}
		return out.toByteArray();
	}

	private static List<Mesh> meshes(final byte[] pdf) {
		final var result = new ArrayList<Mesh>();
		for (final var stream : streams(pdf)) {
			if (!Pattern.compile("/ShadingType\\s+4(?=\\s|/|>>)").matcher(stream.dictionary).find()) {
				continue;
			}
			final double[] decode = arrayEntry(stream.dictionary, "Decode");
			final int components;
			if (stream.dictionary.contains("/ColorSpace /DeviceGray")) {
				components = 1;
			} else if (stream.dictionary.contains("/ColorSpace /DeviceCMYK")) {
				components = 4;
			} else {
				assertTrue(stream.dictionary.contains("/ColorSpace /DeviceRGB"), stream.dictionary);
				components = 3;
			}
			assertEquals(4 + 2 * components, decode.length, stream.dictionary);
			assertEquals(32, integerEntry(stream.dictionary, "BitsPerCoordinate"));
			assertEquals(16, integerEntry(stream.dictionary, "BitsPerComponent"));
			assertEquals(8, integerEntry(stream.dictionary, "BitsPerFlag"));

			final byte[] data = stream.dictionary.contains("/ASCIIHexDecode")
					? asciiHexDecode(stream.encodedData)
					: stream.encodedData;
			final int vertexBytes = 1 + 4 + 4 + 2 * components;
			assertEquals(0, data.length % vertexBytes, "packed vertex size");
			final var vertices = new ArrayList<Vertex>();
			for (var offset = 0; offset < data.length; offset += vertexBytes) {
				int p = offset;
				final int flag = data[p++] & 0xff;
				final long rawX = uint32(data, p);
				p += 4;
				final long rawY = uint32(data, p);
				p += 4;
				final double x = decoded(rawX, decode[0], decode[1], 0xffff_ffffL);
				final double y = decoded(rawY, decode[2], decode[3], 0xffff_ffffL);
				final double[] color = new double[components];
				for (var i = 0; i < components; ++i) {
					color[i] = decoded(uint16(data, p), decode[4 + 2 * i], decode[5 + 2 * i], 0xffffL);
					p += 2;
				}
				vertices.add(new Vertex(flag, x, y, color));
			}
			assertEquals(0, vertices.size() % 6, "two explicit triangles per angular sector");
			assertTrue(vertices.stream().allMatch(v -> v.flag == 0), "all mesh flags must be zero");
			result.add(new Mesh(stream, decode, vertices));
		}
		return result;
	}

	private static long uint32(final byte[] data, final int offset) {
		return ((data[offset] & 0xffL) << 24) | ((data[offset + 1] & 0xffL) << 16)
				| ((data[offset + 2] & 0xffL) << 8) | (data[offset + 3] & 0xffL);
	}

	private static int uint16(final byte[] data, final int offset) {
		return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
	}

	private static double decoded(final long value, final double minimum, final double maximum, final long maximumCode) {
		return minimum + value / (double) maximumCode * (maximum - minimum);
	}

	private static int integerEntry(final String dictionary, final String name) {
		final var match = Pattern.compile("/" + Pattern.quote(name) + "\\s+(\\d+)").matcher(dictionary);
		assertTrue(match.find(), dictionary);
		return Integer.parseInt(match.group(1));
	}

	private static double[] arrayEntry(final String dictionary, final String name) {
		final var match = Pattern.compile("/" + Pattern.quote(name) + "\\s*\\[([^]]*)\\]")
				.matcher(dictionary);
		assertTrue(match.find(), dictionary);
		final var numbers = Pattern.compile("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").matcher(match.group(1));
		final var values = new ArrayList<Double>();
		while (numbers.find()) {
			values.add(Double.parseDouble(numbers.group()));
		}
		return values.stream().mapToDouble(Double::doubleValue).toArray();
	}

	private static String patternDictionary(final byte[] pdf, final int shadingObject) {
		final String raw = new String(pdf, StandardCharsets.ISO_8859_1);
		final var match = Pattern.compile("/Shading\\s+" + shadingObject + "\\s+\\d+\\s+R").matcher(raw);
		assertTrue(match.find(), "pattern must reference shading " + shadingObject);
		final int start = raw.lastIndexOf("<<", match.start());
		final int end = raw.indexOf(">>", match.end());
		assertTrue(start >= 0 && end > start);
		final String dictionary = raw.substring(start, end + 2);
		assertTrue(dictionary.contains("/Type /Pattern"), dictionary);
		assertTrue(Pattern.compile("/PatternType\\s+2").matcher(dictionary).find(), dictionary);
		return dictionary;
	}

	@Test
	public void testDictionaryLayoutAndColorDependsOnlyOnAngle() throws Exception {
		final byte[] pdf = render(params(), gc -> fillPage(gc,
				conic(120, 90, 0, new double[] { 0, 1 }, new Color[] { RED, BLUE }, new AffineTransform())));
		final var mesh = meshes(pdf).getFirst();
		assertEquals(10, mesh.decode.length);
		assertTrue(mesh.vertices.size() >= 6);
		for (var i = 0; i < mesh.vertices.size(); i += 6) {
			final var v = mesh.vertices;
			assertArrayEquals(v.get(i).components, v.get(i + 1).components, 1e-9,
					"inner and outer vertices at the sector start");
			assertArrayEquals(v.get(i + 2).components, v.get(i + 4).components, 1e-9);
			assertArrayEquals(v.get(i + 2).components, v.get(i + 5).components, 1e-9,
					"inner and outer vertices at the sector end");
		}
		assertArrayEquals(new double[] { 1, 0, 0 }, mesh.vertices.get(0).components, 1e-5);
		final double innerDistance = Math.hypot(mesh.vertices.get(0).x - 120, mesh.vertices.get(0).y - 90);
		final double outerDistance = Math.hypot(mesh.vertices.get(1).x - 120, mesh.vertices.get(1).y - 90);
		assertEquals(0.01, innerDistance, 1e-2, "the first ring is gradient-local and tiny");
		assertTrue(outerDistance > 100, "the outer ring must cover the page");
		assertTrue(mesh.vertices.stream().anyMatch(v -> Math.abs(v.x - 120) < 1e-2 && v.y > 90
				&& near(v.components, 0.5, 0, 0.5)),
				"the half-turn vertices must contain straight-RGBA red/blue interpolation");
		patternDictionary(pdf, mesh.stream.objectNumber);
	}

	@Test
	public void testHardStopDuplicatesCoordinatesWithDifferentColors() throws Exception {
		final byte[] pdf = render(params(), gc -> fillPage(gc, conic(160, 120, 0,
				new double[] { 0, 0.5, 0.5, 1 }, new Color[] { RED, RED, BLUE, BLUE },
				new AffineTransform())));
		final var mesh = meshes(pdf).getFirst();
		final double radius = (mesh.decode[1] - mesh.decode[0]) / 2;
		Vertex redAtBoundary = null;
		Vertex blueAtBoundary = null;
		for (final var vertex : mesh.vertices) {
			if (Math.abs(vertex.x - 160) < 1e-2 && vertex.y > 120 + radius * 0.99) {
				if (near(vertex.components, 1, 0, 0)) {
					redAtBoundary = vertex;
				}
				if (near(vertex.components, 0, 0, 1)) {
					blueAtBoundary = vertex;
				}
			}
		}
		assertTrue(redAtBoundary != null && blueAtBoundary != null,
				"the same 180-degree coordinate must be emitted on both sides of the hard stop");
		assertEquals(redAtBoundary.x, blueAtBoundary.x, 0);
		assertEquals(redAtBoundary.y, blueAtBoundary.y, 0);
	}

	@Test
	public void testTransparentStopsUseLuminosityMaskOnlyWhenAllowed() throws Exception {
		final var gradient = conic(160, 120, 0, new double[] { 0, 1 },
				new Color[] { RGBAColor.create(1, 0, 0, 0.2f), BLUE }, new AffineTransform());
		final byte[] transparent = render(params().withVersion(PDFParams.Version.V_1_7), gc -> {
			assertTrue(gc.supports(GC.Capability.CONIC_GRADIENT));
			fillPage(gc, gradient);
		});
		final var transparentMeshes = meshes(transparent);
		assertEquals(2, transparentMeshes.size(), "color and alpha meshes");
		assertTrue(transparentMeshes.stream()
				.anyMatch(m -> m.stream.dictionary.contains("/ColorSpace /DeviceGray")));
		final String raw = new String(transparent, StandardCharsets.ISO_8859_1);
		assertTrue(Pattern.compile("/SMask\\s*<<[\\s\\S]*?/S\\s*/Luminosity").matcher(raw).find(), raw);

		final byte[] pdfa1 = render(params().withVersion(PDFParams.Version.V_PDFA1B), gc -> {
			assertTrue(gc.supports(GC.Capability.CONIC_GRADIENT));
			fillPage(gc, gradient);
		});
		assertEquals(1, meshes(pdfa1).size(), "PDF/A-1 drops alpha but keeps the color mesh");
		final String pdfaRaw = new String(pdfa1, StandardCharsets.ISO_8859_1);
		assertFalse(Pattern.compile("/S\\s*/Luminosity").matcher(pdfaRaw).find(), pdfaRaw);
		assertFalse(Pattern.compile("/SMask\\s*<<").matcher(pdfaRaw).find(), pdfaRaw);
	}

	@Test
	public void testPatternMatrixAndVerticesStayGradientLocal() throws Exception {
		final var current = new AffineTransform();
		current.translate(35, 20);
		current.rotate(Math.toRadians(17));
		final var gradientTransform = new AffineTransform();
		gradientTransform.translate(8, -6);
		gradientTransform.rotate(Math.toRadians(31));
		gradientTransform.scale(1.4, 0.7);
		final double cx = 27, cy = 43, start = Math.toRadians(23);
		final byte[] pdf = render(params(), gc -> {
			gc.transform(current);
			fillPage(gc, conic(cx, cy, start, new double[] { 0, 1 }, new Color[] { RED, BLUE },
					gradientTransform));
		});
		final var mesh = meshes(pdf).getFirst();
		final double[] matrix = arrayEntry(patternDictionary(pdf, mesh.stream.objectNumber), "Matrix");
		final var expected = new AffineTransform(current);
		expected.concatenate(gradientTransform);
		final double[] localCorners = { 0, 0, WIDTH, 0, WIDTH, HEIGHT, 0, HEIGHT };
		expected.createInverse().transform(localCorners, 0, localCorners, 0, 4);
		double expectedRadius = 0;
		for (var i = 0; i < localCorners.length; i += 2) {
			expectedRadius = Math.max(expectedRadius,
					Math.hypot(localCorners[i] - cx, localCorners[i + 1] - cy));
		}
		expectedRadius *= 1.05;
		assertEquals(cx - expectedRadius, mesh.decode[0], 1e-2);
		assertEquals(cx + expectedRadius, mesh.decode[1], 1e-2);

		expected.preConcatenate(new AffineTransform(1, 0, 0, -1, 0, HEIGHT));
		final double[] expectedMatrix = new double[6];
		expected.getMatrix(expectedMatrix);
		assertArrayEquals(expectedMatrix, matrix, 1e-2);

		final var first = mesh.vertices.getFirst();
		assertEquals(cx + Math.sin(start) * 0.01, first.x, 1e-2);
		assertEquals(cy - Math.cos(start) * 0.01, first.y, 1e-2);
		assertTrue(Math.abs(first.x - current.getTranslateX()) > 1,
				"the current transform must not be baked into mesh coordinates");
	}

	@Test
	public void testEqualPaintsShareMeshAndDifferentStopsDoNot() throws Exception {
		final byte[] pdf = render(params(), gc -> {
			fillPage(gc, conic(160, 120, 0, new double[] { 0, 1 }, new Color[] { RED, BLUE },
					new AffineTransform()));
			fillPage(gc, conic(160, 120, 0, new double[] { 0, 1 }, new Color[] { RED, BLUE },
					new AffineTransform()));
			fillPage(gc, conic(160, 120, 0, new double[] { 0, 0.6, 1 }, new Color[] { RED, GREEN, BLUE },
					new AffineTransform()));
		});
		assertEquals(2, meshes(pdf).size(), "equal array contents share one shading; changed stops create another");
	}

	@Test
	public void testDecodeLengthAndColorSpaceFollowEffectiveColorMode() throws Exception {
		final var cases = List.of(
				new Object[] { PDFParams.ColorMode.PRESERVE, "/DeviceRGB", 10 },
				new Object[] { PDFParams.ColorMode.GRAY, "/DeviceGray", 6 },
				new Object[] { PDFParams.ColorMode.CMYK, "/DeviceCMYK", 12 });
		for (final var entry : cases) {
			final var colorMode = (PDFParams.ColorMode) entry[0];
			final String colorSpace = (String) entry[1];
			final int decodeLength = (Integer) entry[2];
			final byte[] pdf = render(params().withColorMode(colorMode), gc -> fillPage(gc,
					conic(160, 120, 0, new double[] { 0, 1 }, new Color[] { RED, BLUE },
							new AffineTransform())));
			final var mesh = meshes(pdf).getFirst();
			assertTrue(mesh.stream.dictionary.contains("/ColorSpace " + colorSpace), mesh.stream.dictionary);
			assertEquals(decodeLength, mesh.decode.length);
		}
	}

	@Test
	public void testCapabilityStartsAtPdf13() throws Exception {
		render(params().withVersion(PDFParams.Version.V_1_2),
				gc -> assertFalse(gc.supports(GC.Capability.CONIC_GRADIENT)));
		render(params().withVersion(PDFParams.Version.V_1_3),
				gc -> assertTrue(gc.supports(GC.Capability.CONIC_GRADIENT)));
	}

	private static boolean near(final double[] color, final double... expected) {
		if (color.length != expected.length) {
			return false;
		}
		for (var i = 0; i < color.length; ++i) {
			if (Math.abs(color[i] - expected[i]) > 1e-4) {
				return false;
			}
		}
		return true;
	}
}
