package net.zamasoft.pdfg2d.pdf.util.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A lightweight buffered output stream that wraps a caller-supplied byte array.
 * <p>
 * Unlike {@link java.io.BufferedOutputStream}, this class does not allocate its
 * own buffer; instead the caller passes in a pre-allocated array.  This allows
 * a single scratch buffer to be reused across multiple pipeline stages without
 * heap pressure.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class FastBufferedOutputStream extends FilterOutputStream {
	/** Shared backing buffer supplied by the caller. */
	protected final byte[] buf;
	/** Number of valid bytes currently held in {@link #buf}. */
	protected int count = 0;

	/**
	 * Constructs a new buffered output stream.
	 *
	 * @param out the underlying output stream to flush to
	 * @param buf the scratch buffer to use; its length determines the buffer size
	 */
	public FastBufferedOutputStream(OutputStream out, byte[] buf) {
		super(out);
		this.buf = buf;
	}

	/**
	 * Flushes all buffered bytes to the underlying stream and resets
	 * {@link #count} to zero.
	 *
	 * @throws IOException if an I/O error occurs while writing to the underlying
	 *                     stream
	 */
	protected void writeAll() throws IOException {
		this.out.write(this.buf, 0, this.count);
		this.count = 0;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		while (len > 0) {
			int a = this.buf.length - this.count;
			if (a <= len) {
				System.arraycopy(b, off, this.buf, this.count, a);
				this.count += a;
				off += a;
				len -= a;
				this.writeAll();
			} else {
				System.arraycopy(b, off, this.buf, this.count, len);
				this.count += len;
				break;
			}
		}
	}

	@Override
	public void write(byte[] b) throws IOException {
		this.write(b, 0, b.length);
	}

	@Override
	public void write(int b) throws IOException {
		this.buf[this.count++] = (byte) b;
		if (this.count >= this.buf.length) {
			this.writeAll();
		}
	}

	@Override
	public void flush() throws IOException {
		if (this.count > 0) {
			this.writeAll();
		}
		this.out.flush();
	}
}
