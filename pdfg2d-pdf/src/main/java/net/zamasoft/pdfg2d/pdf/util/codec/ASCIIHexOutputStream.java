package net.zamasoft.pdfg2d.pdf.util.codec;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.zamasoft.pdfg2d.pdf.PDFOutput;

/**
 * Output stream filter that encodes each byte as two uppercase hexadecimal
 * ASCII characters, as required by the PDF {@code ASCIIHexDecode} filter.
 * <p>
 * A line break ({@link net.zamasoft.pdfg2d.pdf.PDFOutput#EOL}) is inserted
 * every 40 encoded bytes to keep lines at a reasonable length.  The
 * end-of-data marker ({@code >}) is <em>not</em> appended automatically;
 * callers must do so when the stream is complete.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class ASCIIHexOutputStream extends FilterOutputStream {
	/** Number of hex byte-pairs written on the current output line. */
	private int pos = 0;

	/**
	 * Constructs an ASCIIHexOutputStream that wraps the given output stream.
	 *
	 * @param out the underlying output stream to write encoded bytes to
	 */
	public ASCIIHexOutputStream(final OutputStream out) {
		super(out);
	}

	private static final char[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E',
			'F' };

	/**
	 * Encodes a single byte as two uppercase hexadecimal characters and writes
	 * them to the underlying stream.  Inserts a line break after every 40 pairs.
	 *
	 * @param b the byte value (only the low 8 bits are used)
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public void write(final int b) throws IOException {
		this.out.write(HEX[((b >> 4) & 0x0F)]);
		this.out.write(HEX[(b & 0x0F)]);
		if (++this.pos > 40) {
			this.out.write(PDFOutput.EOL);
			this.pos = 0;
		}
	}
}
