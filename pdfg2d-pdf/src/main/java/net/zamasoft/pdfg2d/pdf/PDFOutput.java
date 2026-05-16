package net.zamasoft.pdfg2d.pdf;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import net.zamasoft.pdfg2d.pdf.util.PDFUtils;

/**
 * Output stream for writing PDF data.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PDFOutput extends FilterOutputStream {
	// Flag to determine if a space should be written before the next token.
	private boolean spaceBefore = true;

	// Encoding for PDF names.
	protected final String nameEncoding;

	// End-of-line sequence for PDF.
	public static final byte[] EOL = { 0x0D, 0x0A };

	// Byte representations of common PDF literals.
	private static final byte[] NULL = "null".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] TRUE = "true".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] FALSE = "false".getBytes(StandardCharsets.US_ASCII);

	// Escape sequences for string literals.
	private static final byte[] ESC_N = { '\\', 'n' };
	private static final byte[] ESC_R = { '\\', 'r' };
	private static final byte[] ESC_T = { '\\', 't' };
	private static final byte[] ESC_B = { '\\', 'b' };
	private static final byte[] ESC_F = { '\\', 'f' };
	private static final byte[] ESC_BS = { '\\', '\\' };
	private static final byte[] ESC_LP = { '\\', '(' };
	private static final byte[] ESC_RP = { '\\', ')' };

	// Hexadecimal characters for encoding.
	private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

	// Buffer for efficient byte writing.
	private ByteBuffer bbuff = null;

	// Reusable buffer for number formatting.
	private final byte[] numBuf = new byte[20];

	// Precision for real numbers.
	private int precision = 1;

	// Scaling factor for real numbers.
	private double scale = 10.0;

	// Epsilon for real number comparisons.
	private double epsilon = 0.05;

	/**
	 * Constructs a PDFOutput wrapping the given output stream.
	 *
	 * @param out          the underlying output stream
	 * @param nameEncoding the encoding used for PDF name objects
	 */
	public PDFOutput(final OutputStream out, final String nameEncoding) {
		super(out);
		this.nameEncoding = nameEncoding;
	}

	/**
	 * Sets the precision for real numbers.
	 * 
	 * @param precision the number of decimal places
	 */
	public void setPrecision(final int precision) {
		this.precision = Math.max(0, precision);
		this.scale = Math.pow(10, this.precision);
		this.epsilon = 0.5 / this.scale;
	}

	/**
	 * Returns the precision for real numbers.
	 * 
	 * @return the precision
	 */
	public int getPrecision() {
		return this.precision;
	}

	/**
	 * Represents a link destination in a PDF document.
	 * 
	 * @param pageRef The target page reference.
	 * @param x       The X coordinate.
	 * @param y       The Y coordinate.
	 * @param zoom    The zoom level.
	 */
	public record Destination(ObjectRef pageRef, double x, double y, double zoom) {
	}

	/**
	 * Writes an object reference (e.g., "1 0 R").
	 * 
	 * @param ref the object reference
	 * @throws IOException if an I/O error occurs
	 */
	public void writeObjectRef(final ObjectRef ref) throws IOException {
		this.writeInt(ref.objectNumber());
		this.writeInt(ref.generationNumber());
		this.writeOperator("R");
	}

	/**
	 * Writes a destination array.
	 * 
	 * @param dest the destination
	 * @throws IOException if an I/O error occurs
	 */
	public void writeDestination(final Destination dest) throws IOException {
		this.startArray();
		this.writeObjectRef(dest.pageRef);
		this.writeName("XYZ");
		this.writeReal(dest.x);
		this.writeReal(dest.y);
		this.writeReal(dest.zoom);
		this.endArray();
	}

	/**
	 * Writes a name literal, prefixed with '/'.
	 * 
	 * @param name the name
	 * @throws IOException if an I/O error occurs
	 */
	public void writeName(final String name) throws IOException {
		this.spaceBefore();
		this.write('/');
		final var b = PDFUtils.encodeName(name, this.nameEncoding);
		if (b.length <= 0 || b.length > 127) {
			throw new IllegalArgumentException("Name length must be between 1 and 127 bytes.");
		}
		this.write(b);
	}

	/**
	 * Writes an operator.
	 * 
	 * @param name the operator name
	 * @throws IOException if an I/O error occurs
	 */
	public void writeOperator(final String name) throws IOException {
		this.spaceBefore();
		this.write(name);
	}

	/**
	 * Writes null literal.
	 * 
	 * @throws IOException if an I/O error occurs
	 */
	public void writeNull() throws IOException {
		this.spaceBefore();
		this.write(NULL);
	}

	/**
	 * Writes a boolean literal.
	 * 
	 * @param b the boolean value
	 * @throws IOException if an I/O error occurs
	 */
	public void writeBoolean(final boolean b) throws IOException {
		this.spaceBefore();
		this.write(b ? TRUE : FALSE);
	}

	/**
	 * Writes an integer literal.
	 * 
	 * @param number the integer
	 * @throws IOException if an I/O error occurs
	 */
	public void writeInt(final int number) throws IOException {
		this.spaceBefore();
		this.buffAllocate(12);
		this.buffWriteLong(number);
		this.buffFlush();
	}

	/**
	 * Writes a real number literal.
	 * 
	 * @param number the real number
	 * @throws IOException if an I/O error occurs
	 */
	public void writeReal(final double number) throws IOException {
		this.spaceBefore();
		this.buffAllocate(24);
		this.buffWriteReal(number);
		this.buffFlush();
	}

	// Lookup table for fast division by 10 (0-99 mapping)
	private static final byte[] DIGITS_TENS = { '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '1', '1', '1',
			'1', '1', '1', '1', '1', '1', '2', '2', '2', '2', '2', '2', '2', '2', '2', '2', '3', '3', '3', '3', '3',
			'3', '3', '3', '3', '3', '4', '4', '4', '4', '4', '4', '4', '4', '4', '4', '5', '5', '5', '5', '5', '5',
			'5', '5', '5', '5', '6', '6', '6', '6', '6', '6', '6', '6', '6', '6', '7', '7', '7', '7', '7', '7', '7',
			'7', '7', '7', '8', '8', '8', '8', '8', '8', '8', '8', '8', '8', '9', '9', '9', '9', '9', '9', '9', '9',
			'9', '9' };

	private static final byte[] DIGITS_ONES = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3',
			'4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4',
			'5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5',
			'6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9' };

	private void buffWriteReal(final double number) {
		assert !Double.isInfinite(number) : "Infinite number";
		assert !Double.isNaN(number) : "Undefined number";

		final long scaled = this.toLong(number);
		final long iScale = (long) this.scale;

		long v = scaled;
		if (v < 0) {
			this.bbuff.put((byte) '-');
			v = -v;
		}

		long ipart = v / iScale;

		// Write integer part using optimized writer
		buffWriteLongPositive(ipart);

		long fpart = v % iScale;
		if (fpart == 0) {
			return;
		}

		this.bbuff.put((byte) '.');

		// Fast write of fractional part with padding
		// We know fpart is < iScale (power of 10) and precision is small (usually 1-4)
		int prec = this.precision;
		int pos = 0;
		// Fill temporary buffer backwards
		while (prec > 0) {
			long q = fpart / 10;
			long r = fpart - (q * 10); // faster than % 10
			numBuf[pos++] = (byte) ('0' + r);
			fpart = q;
			prec--;
		}

		// Skip trailing zeros (which are at the beginning of numBuf since we wrote
		// backwards)
		int start = 0;
		while (start < pos && numBuf[start] == '0') {
			start++;
		}

		// Write remaining digits in correct order
		for (int i = pos - 1; i >= start; i--) {
			this.bbuff.put(numBuf[i]);
		}
	}

	private void buffWriteLong(long v) {
		if (v == 0) {
			this.bbuff.put((byte) '0');
			return;
		}
		if (v < 0) {
			this.bbuff.put((byte) '-');
			v = -v;
		}
		buffWriteLongPositive(v);
	}

	/**
	 * Optimized long writer for positive values.
	 * Writes directly to bbuff using lookup tables for 2-digit blocks.
	 */
	private void buffWriteLongPositive(long v) {
		if (v == 0) {
			this.bbuff.put((byte) '0');
			return;
		}

		// Use local buffer for reversing digits
		int p = 0;
		while (v >= 100) {
			// Do two digits at a time
			long q = v / 100;
			int r = (int) (v - (q * 100));
			v = q;
			numBuf[p++] = DIGITS_ONES[r];
			numBuf[p++] = DIGITS_TENS[r];
		}

		if (v >= 10) {
			int r = (int) v;
			numBuf[p++] = DIGITS_ONES[r];
			numBuf[p++] = DIGITS_TENS[r];
		} else {
			numBuf[p++] = (byte) ('0' + v);
		}

		// Write reversed to buffer
		while (p > 0) {
			this.bbuff.put(numBuf[--p]);
		}
	}

	/**
	 * Converts a double to a long with PDF-specific scaling and clamping.
	 * 
	 * @param number the double to convert
	 * @return the scaled long value
	 */
	private long toLong(double number) {
		if (number > 32767) {
			number = 32767;
		} else if (number < -32767) {
			number = -32767;
		} else if (Math.abs(number) < this.epsilon) {
			return 0;
		}
		return (long) (number * this.scale + (number >= 0 ? 0.5 : -0.5));
	}

	/**
	 * Compares real numbers using the precision used for PDF output.
	 * 
	 * @param a first number
	 * @param b second number
	 * @return true if equal
	 */
	public boolean equals(final double a, final double b) {
		if (a == b) {
			return true;
		}
		return this.toLong(a) == this.toLong(b);
	}

	/**
	 * Writes the start of a dictionary (hash).
	 * 
	 * @throws IOException if an I/O error occurs
	 */
	public void startHash() throws IOException {
		this.writeLine("<<");
	}

	/**
	 * Writes the end of a dictionary (hash).
	 * 
	 * @throws IOException if an I/O error occurs
	 */
	public void endHash() throws IOException {
		this.writeLine(">>");
	}

	/**
	 * Writes the start of an array.
	 * 
	 * @throws IOException if an I/O error occurs
	 */
	public void startArray() throws IOException {
		this.spaceBefore();
		this.write('[');
	}

	/**
	 * Writes the end of an array.
	 * 
	 * @throws IOException if an I/O error occurs
	 */
	public void endArray() throws IOException {
		this.spaceBefore();
		this.write(']');
	}

	/**
	 * Writes a string literal, escaping special characters.
	 * 
	 * @param str the string
	 * @throws IOException if an I/O error occurs
	 */
	public void writeString(final String str) throws IOException {
		this.spaceBefore();
		this.write('(');
		var len = 0;
		for (var i = 0; i < str.length(); ++i) {
			final char c = str.charAt(i);
			switch (c) {
				case '\n' -> {
					len += ESC_N.length;
					this.write(ESC_N);
				}
				case '\r' -> {
					len += ESC_R.length;
					this.write(ESC_R);
				}
				case '\t' -> {
					len += ESC_T.length;
					this.write(ESC_T);
				}
				case '\b' -> {
					len += ESC_B.length;
					this.write(ESC_B);
				}
				case '\f' -> {
					len += ESC_F.length;
					this.write(ESC_F);
				}
				case '\\' -> {
					len += ESC_BS.length;
					this.write(ESC_BS);
				}
				case '(' -> {
					len += ESC_LP.length;
					this.write(ESC_LP);
				}
				case ')' -> {
					len += ESC_RP.length;
					this.write(ESC_RP);
				}
				default -> {
					++len;
					this.write(c);
				}
			}
		}
		this.write(')');
		if (len > 65535) {
			throw new IllegalArgumentException("String length exceeds 65535 bytes.");
		}
	}

	/**
	 * Allocates or reuses a ByteBuffer for writing.
	 * 
	 * @param size the minimum capacity required
	 */
	private void buffAllocate(final int size) {
		if (this.bbuff == null || this.bbuff.capacity() < size) {
			this.bbuff = ByteBuffer.allocate(size);
		}
		this.bbuff.clear();
	}

	/**
	 * Flushes the ByteBuffer content to the underlying output stream.
	 * 
	 * @throws IOException if an I/O error occurs
	 */
	private void buffFlush() throws IOException {
		this.bbuff.flip();
		this.write(this.bbuff.array(), this.bbuff.arrayOffset(), this.bbuff.limit());
		this.bbuff.clear();
	}

	/**
	 * Writes a single byte to the ByteBuffer.
	 * 
	 * @param b the byte to write
	 */
	private void buffWrite(final byte b) {
		this.bbuff.put(b);
	}

	/**
	 * Writes a byte as two hexadecimal characters to the ByteBuffer.
	 * 
	 * @param b the byte to convert and write
	 */
	private void buffHex8(final byte b) {
		this.bbuff.put((byte) HEX_CHARS[((b >> 4) & 0x0F)]);
		this.bbuff.put((byte) HEX_CHARS[(b & 0x0F)]);
	}

	/**
	 * Writes a 16-bit integer as four hexadecimal characters to the ByteBuffer.
	 * 
	 * @param c the integer to convert and write
	 */
	private void buffHex16(final int c) {
		this.buffHex8((byte) ((c >> 8) & 0xFF));
		this.buffHex8((byte) (c & 0xFF));
	}

	/**
	 * Writes a byte as two hexadecimal characters directly to the output stream.
	 * 
	 * @param b the byte to convert and write
	 * @throws IOException if an I/O error occurs
	 */
	private void writeHex8(final byte b) throws IOException {
		this.write(HEX_CHARS[((b >> 4) & 0x0F)]);
		this.write(HEX_CHARS[(b & 0x0F)]);
	}

	/**
	 * Writes a UTF-16 hex string literal, prefixed with '<FEFF>'.
	 * 
	 * @param text the text
	 * @throws IOException if an I/O error occurs
	 */
	public void writeUTF16(final String text) throws IOException {
		this.spaceBefore();
		this.buffAllocate(2 + 4 + 4 * text.length());
		this.buffWrite((byte) '<');
		this.buffHex8((byte) 0xFE);
		this.buffHex8((byte) 0xFF);
		for (var i = 0; i < text.length(); ++i) {
			final char c = text.charAt(i);
			this.buffHex16(c);
		}
		this.buffWrite((byte) '>');
		this.buffFlush();
	}

	/**
	 * Writes text as either a string literal or UTF-16 hex string if needed.
	 * 
	 * @param text the text
	 * @throws IOException if an I/O error occurs
	 */
	public void writeText(final String text) throws IOException {
		for (var i = 0; i < text.length(); ++i) {
			final char c = text.charAt(i);
			if (c > 0x7F) {
				this.writeUTF16(text);
				return;
			}
		}
		this.writeString(text);
	}

	/**
	 * Writes a file name as a string or hex string.
	 * 
	 * @param elements path elements
	 * @param encoding path encoding
	 * @throws IOException if an I/O error occurs
	 */
	public void writeFileName(final String[] elements, final String encoding) throws IOException {
		var complex = false;
		outer: for (final var text : elements) {
			for (var i = 0; i < text.length(); ++i) {
				final char c = text.charAt(i);
				if (c > 0x7F || c == '/') {
					complex = true;
					break outer;
				}
			}
		}

		if (complex) {
			this.spaceBefore();
			this.write('<');
			for (var k = 0; k < elements.length; ++k) {
				final byte[] name = elements[k].getBytes(encoding);
				for (final byte d : name) {
					if (d == '/' || d == '\\') {
						this.writeHex8((byte) '\\');
					}
					this.writeHex8(d);
				}
				if (k != elements.length - 1) {
					this.writeHex8((byte) '/');
				}
			}
			this.write('>');
		} else {
			final var sb = new StringBuilder();
			for (var j = 0; j < elements.length; ++j) {
				sb.append(elements[j]);
				if (j < elements.length - 1) {
					sb.append('/');
				}
			}
			this.writeString(sb.toString());
		}
	}

	/**
	 * Writes an 8-bit byte array literal (hex string).
	 * 
	 * @param a   the array
	 * @param off offset
	 * @param len length
	 * @throws IOException if an I/O error occurs
	 */
	public void writeBytes8(final byte[] a, final int off, final int len) throws IOException {
		if (len > 65535) {
			throw new IllegalArgumentException("Byte array length exceeds 65535.");
		}
		this.spaceBefore();
		this.buffAllocate(2 + 2 * len);
		this.buffWrite((byte) '<');
		for (var i = 0; i < len; ++i) {
			this.buffHex8(a[i + off]);
		}
		this.buffWrite((byte) '>');
		this.buffFlush();
	}

	/**
	 * Writes a 16-bit integer array literal (hex string).
	 * 
	 * @param a   the array
	 * @param off offset
	 * @param len length
	 * @throws IOException if an I/O error occurs
	 */
	public void writeBytes16(final int[] a, final int off, final int len) throws IOException {
		if (len * 2 > 65535) {
			throw new IllegalArgumentException("Bytes length exceeds 65535.");
		}
		this.spaceBefore();
		this.buffAllocate(2 + 4 * len);
		this.buffWrite((byte) '<');
		for (var i = 0; i < len; ++i) {
			this.buffHex16(a[i + off]);
		}
		this.buffWrite((byte) '>');
		this.buffFlush();
	}

	/**
	 * Writes a 16-bit integer literal (hex string).
	 * 
	 * @param a the integer
	 * @throws IOException if an I/O error occurs
	 */
	public void writeBytes16(final int a) throws IOException {
		this.spaceBefore();
		this.buffAllocate(2 + 4);
		this.buffWrite((byte) '<');
		this.buffHex16(a);
		this.buffWrite((byte) '>');
		this.buffFlush();
	}

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	/**
	 * Writes a date in PDF format (e.g., "D:20201220170000+09'00'").
	 * 
	 * @param time the time in milliseconds
	 * @param zone the time zone
	 * @throws IOException if an I/O error occurs
	 */
	public void writeDate(final long time, final TimeZone zone) throws IOException {
		final var zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), zone.toZoneId());
		final var sb = new StringBuilder();
		sb.append("D:").append(zdt.format(DATE_FORMAT));

		final var offsetSeconds = zdt.getOffset().getTotalSeconds();
		sb.append(offsetSeconds < 0 ? '-' : '+');

		final var absOffset = Math.abs(offsetSeconds);
		final var hours = absOffset / 3600;
		final var minutes = (absOffset % 3600) / 60;

		if (hours < 10)
			sb.append('0');
		sb.append(hours).append('\'');
		if (minutes < 10)
			sb.append('0');
		sb.append(minutes).append('\'');

		this.writeString(sb.toString());
	}

	/**
	 * Writes a line break.
	 * 
	 * @throws IOException if an I/O error occurs
	 */
	public void lineBreak() throws IOException {
		this.write(EOL);
		this.spaceBefore = true;
	}

	/**
	 * Writes a leading space character if necessary to separate tokens.
	 * The first token on a line does not need a preceding space.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	public void spaceBefore() throws IOException {
		if (this.spaceBefore) {
			this.spaceBefore = false;
		} else {
			this.write(' ');
		}
	}

	/**
	 * Ensures the output is at the beginning of a new line.
	 * If the current position is not at a line boundary, writes a line break.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	public void breakBefore() throws IOException {
		if (!this.spaceBefore) {
			this.lineBreak();
		}
	}

	/**
	 * Writes a line of text (preceded by breakBefore, followed by lineBreak).
	 * 
	 * @param line the line text
	 * @throws IOException if an I/O error occurs
	 */
	public void writeLine(final String line) throws IOException {
		this.breakBefore();
		this.write(line);
		this.lineBreak();
	}

	/**
	 * Writes an ASCII string directly to the underlying output stream.
	 *
	 * @param str the ASCII string to write
	 * @throws IOException if an I/O error occurs
	 */
	public void write(final String str) throws IOException {
		final var len = str.length();
		this.buffAllocate(len);
		for (var i = 0; i < len; ++i) {
			this.bbuff.put((byte) str.charAt(i));
		}
		this.buffFlush();
	}

	@Override
	public void write(final byte[] buff, final int off, final int len) throws IOException {
		this.out.write(buff, off, len);
	}

	@Override
	public void write(final byte[] buff) throws IOException {
		this.out.write(buff);
	}

	@Override
	public void write(final int b) throws IOException {
		this.out.write(b);
	}

	@Override
	public void close() throws IOException {
		this.out.close();
	}

	@Override
	public void flush() throws IOException {
		this.out.flush();
	}
}