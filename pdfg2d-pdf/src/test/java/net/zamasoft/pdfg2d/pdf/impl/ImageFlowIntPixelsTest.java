package net.zamasoft.pdfg2d.pdf.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Images with one int per pixel are encoded from whole rows (2026-10-09): the samples must be the bytes the
 * per-pixel {@code ColorModel} calls give (RGB and the soft mask), including premultiplied pixels with small
 * alphas, subimages, and runs of one pixel value.
 */
public class ImageFlowIntPixelsTest {
	private static final int W = 37, H = 23;

	private static BufferedImage randomImage(final int type, final long seed) {
		final BufferedImage image = new BufferedImage(W, H, type);
		final Random random = new Random(seed);
		for (int y = 0; y < H; ++y) {
			for (int x = 0; x < W; ++x) {
				int argb;
				if (x < 8) {
					argb = 0x1A000000; // a run of one value, like the inside of a shadow
				} else {
					final int a = random.nextInt(4) == 0 ? random.nextInt(4) : random.nextInt(256);
					final int bound = type == BufferedImage.TYPE_INT_ARGB_PRE ? a + 1 : 256;
					argb = (a << 24) | (random.nextInt(bound) << 16) | (random.nextInt(bound) << 8)
							| random.nextInt(bound);
				}
				// Store the int as is (setRGB would convert to premultiplied)
				image.getRaster().setDataElements(x, y, new int[] { argb });
			}
		}
		return image;
	}

	private static List<byte[]> imageStreams(final BufferedImage image) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
		try (final var gc = new PDFGC(pdf.nextPage(100, 100))) {
			gc.drawImage(pdf.addGeneratedImage(image));
		}
		pdf.close();
		builder.close();
		final byte[] data = bytes.toByteArray();
		final String raw = new String(data, StandardCharsets.ISO_8859_1);
		final List<byte[]> streams = new ArrayList<byte[]>();
		final Matcher m = Pattern.compile("<<([^>]*?/Subtype\\s*/Image[^>]*?)>>\\s*stream\r?\n").matcher(raw);
		while (m.find()) {
			final Matcher length = Pattern.compile("/Length\\s+(\\d+)").matcher(m.group(1));
			assertTrue(length.find(), m.group(1));
			final int start = m.end();
			final byte[] stream = Arrays.copyOfRange(data, start, start + Integer.parseInt(length.group(1)));
			try (final var in = new InflaterInputStream(new ByteArrayInputStream(stream))) {
				streams.add(in.readAllBytes());
			}
		}
		return streams;
	}

	/** The samples as the per-pixel ColorModel calls give them: RGB, then the alpha of a soft mask. */
	private static byte[][] expected(final BufferedImage image) {
		final ColorModel cm = image.getColorModel();
		final Raster raster = image.getRaster();
		final int w = image.getWidth(), h = image.getHeight();
		final byte[] rgb = new byte[w * h * 3], alpha = new byte[w * h];
		Object pixel = null;
		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				pixel = raster.getDataElements(x, y, pixel);
				final int i = y * w + x;
				rgb[i * 3] = (byte) cm.getRed(pixel);
				rgb[i * 3 + 1] = (byte) cm.getGreen(pixel);
				rgb[i * 3 + 2] = (byte) cm.getBlue(pixel);
				alpha[i] = (byte) cm.getAlpha(pixel);
			}
		}
		return new byte[][] { rgb, alpha };
	}

	private static void check(final BufferedImage image) throws Exception {
		final List<byte[]> streams = imageStreams(image);
		final byte[][] expected = expected(image);
		final boolean hasAlpha = image.getColorModel().hasAlpha();
		assertEquals(hasAlpha ? 2 : 1, streams.size());
		// The color image comes first, its soft mask after it
		assertArrayEquals(expected[0], streams.get(0), "RGB samples");
		if (hasAlpha) {
			assertArrayEquals(expected[1], streams.get(1), "soft mask");
		}
	}

	@Test
	public void testPremultipliedArgb() throws Exception {
		check(randomImage(BufferedImage.TYPE_INT_ARGB_PRE, 1));
	}

	@Test
	public void testArgb() throws Exception {
		check(randomImage(BufferedImage.TYPE_INT_ARGB, 2));
	}

	@Test
	public void testRgb() throws Exception {
		check(randomImage(BufferedImage.TYPE_INT_RGB, 3));
	}

	@Test
	public void testSubimage() throws Exception {
		// A child raster: rows start inside the parent's data
		check(randomImage(BufferedImage.TYPE_INT_ARGB_PRE, 4).getSubimage(5, 3, 20, 15));
	}
}
