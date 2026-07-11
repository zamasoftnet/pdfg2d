package net.zamasoft.pdfg2d.pdf.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * A {@link DeflaterOutputStream} with a larger output buffer than the JDK
 * default of 512 bytes, which reduces the number of small writes to the
 * underlying stream on content-heavy documents.
 * <p>
 * Because the enlarged buffer requires supplying our own {@link Deflater},
 * the JDK no longer releases it automatically; this class ends the deflater
 * on {@link #close()} to free its native memory deterministically.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public final class FinishingDeflaterOutputStream extends DeflaterOutputStream {

	private static final int BUFFER_SIZE = 8192;

	public FinishingDeflaterOutputStream(final OutputStream out) {
		super(out, new Deflater(), BUFFER_SIZE);
	}

	/**
	 * Creates a deflating stream with an explicit compression level.
	 *
	 * @param out   the underlying stream
	 * @param level the deflate level ({@code -1} for the zlib default, or
	 *              {@code 0}-{@code 9})
	 */
	public FinishingDeflaterOutputStream(final OutputStream out, final int level) {
		super(out, new Deflater(level), BUFFER_SIZE);
	}

	@Override
	public void close() throws IOException {
		try {
			super.close();
		} finally {
			this.def.end();
		}
	}
}
