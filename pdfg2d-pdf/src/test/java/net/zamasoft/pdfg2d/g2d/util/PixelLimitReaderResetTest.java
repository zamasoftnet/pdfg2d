package net.zamasoft.pdfg2d.g2d.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Ensures that the same reader can read an image after checking the pixel count limit (2026-10-04).
 *
 * <p>
 * After reading the dimensions, the JDK PNG reader remembered that it had read the header. When the caller
 * rewound the input, the next {@code getImageTypes} read the signature as a chunk and failed. In production
 * with the limit enabled, opaque PNGs (RGB, grayscale, and palette) disappeared as unreadable images.
 * </p>
 */
public class PixelLimitReaderResetTest {

	private static byte[] png(final int type) throws Exception {
		final BufferedImage image = new BufferedImage(200, 100, type);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	@ParameterizedTest
	@ValueSource(ints = { BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_BYTE_INDEXED,
			BufferedImage.TYPE_INT_ARGB })
	public void readerStillReadsAfterTheCheck(final int type) throws Exception {
		// As in production (foliojet's RasterImageLoader), do not flush input so it can be rewound
		try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(png(type))) {
			@Override
			public void flushBefore(final long pos) {
				// Retain data so the input can be rewound
			}
		}) {
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			assertTrue(readers.hasNext());
			final ImageReader reader = readers.next();
			try {
				reader.setInput(in);
				G2DUtils.checkPixelLimit(reader, 25_000_000L);
				// Caller sequence: rewind and check the type (this read the signature as a chunk and failed)
				in.seek(0);
				assertTrue(reader.getImageTypes(0).hasNext());
				in.seek(0);
				reader.setInput(in);
				final BufferedImage image = reader.read(0);
				assertEquals(200, image.getWidth());
				assertEquals(100, image.getHeight());
			} finally {
				reader.dispose();
			}
		}
	}
}
