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
 * 画素数の上限を確かめたあとも、同じリーダで画像を読めることを固定します(2026-10-04)。
 *
 * <p>
 * 寸法を読んだ JDK の PNG リーダは「ヘッダを読んだ」状態を覚えていて、呼び出し側が入力を先頭へ
 * 戻すと、次の {@code getImageTypes} が署名をチャンクとして読んで失敗した。上限を設けた本番で
 * 透明度の無い PNG(RGB・グレー・パレット)が「読めない画像」として消えていた。
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
		// 本番の読み込み(foliojet の RasterImageLoader)と同じく、先頭へ戻せるよう flush しない入力
		try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(png(type))) {
			@Override
			public void flushBefore(final long pos) {
				// 戻せるように捨てない
			}
		}) {
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			assertTrue(readers.hasNext());
			final ImageReader reader = readers.next();
			try {
				reader.setInput(in);
				G2DUtils.checkPixelLimit(reader, 25_000_000L);
				// 呼び出し側の手順: 先頭へ戻して型を見る(ここが署名をチャンクとして読んで失敗していた)
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
