package net.zamasoft.pdfg2d.util;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Adapts a {@link ImageInputStream} to the {@link InputStream} API.
 * <p>
 * {@link javax.imageio.ImageReader} and related APIs return
 * {@link ImageInputStream} objects, but many callers (e.g., compressors,
 * encoders) expect a plain {@link InputStream}.  This class bridges the gap
 * without copying data.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class ImageInputStreamProxy extends InputStream {
	private final ImageInputStream imageInputStream;

	/**
	 * Constructs a proxy that delegates all operations to {@code imageInputStream}.
	 *
	 * @param imageInputStream the underlying image input stream; must not be
	 *                         {@code null}
	 */
	public ImageInputStreamProxy(ImageInputStream imageInputStream) {
		this.imageInputStream = imageInputStream;
	}

	@Override
	public int read() throws IOException {
		return imageInputStream.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return imageInputStream.read(b);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return imageInputStream.read(b, off, len);
	}

	@Override
	public long skip(long n) throws IOException {
		return imageInputStream.skipBytes(n);
	}

	@Override
	public int available() throws IOException {
		// length()が不明(-1)のストリームで負のavailableを返さない
		// (foliojet側フォークにあった改良を2026-08-01の一本化で移植)
		long length = imageInputStream.length();
		if (length < 0) {
			return 0;
		}
		long available = length - imageInputStream.getStreamPosition();
		return available > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) available;
	}

	@Override
	public void close() throws IOException {
		imageInputStream.close();
	}

	@Override
	public synchronized void mark(int readlimit) {
		imageInputStream.mark();
	}

	@Override
	public synchronized void reset() throws IOException {
		imageInputStream.reset();
	}

	@Override
	public boolean markSupported() {
		return true;
	}
}
