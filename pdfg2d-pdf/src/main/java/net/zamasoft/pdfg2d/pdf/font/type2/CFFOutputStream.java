package net.zamasoft.pdfg2d.pdf.font.type2;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Outputs CFF (Compact Font Format) data.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CFFOutputStream extends FilterOutputStream {
	public static final byte[] VERSION = { 0 };

	public static final byte[] NOTICE = { 1 };

	public static final byte[] FULL_NAME = { 2 };

	public static final byte[] FAMILY_NAME = { 3 };

	public static final byte[] WEIGHT = { 4 };

	public static final byte[] FONT_BBOX = { 5 };

	public static final byte[] BLUE_VALUES = { 6 };

	public static final byte[] OTHER_BLUES = { 7 };

	public static final byte[] FAMILY_BLUES = { 8 };

	public static final byte[] FAMILY_OTHER_BLUES = { 9 };

	public static final byte[] STD_HW = { 10 };

	public static final byte[] STD_VW = { 11 };

	public static final byte[] UNIQUE_ID = { 13 };

	public static final byte[] XUID = { 14 };

	public static final byte[] CHARSETS = { 15 };

	public static final byte[] ENCODING = { 16 };

	public static final byte[] CHAR_STRINGS = { 17 };

	public static final byte[] PRIVATE = { 18 };

	public static final byte[] SUBRS = { 19 };

	public static final byte[] DEFAULT_WIDTHX = { 20 };

	public static final byte[] NOMINAL_WIDTHX = { 21 };

	public static final byte[] COPYRIGHT = { 12, 0 };

	public static final byte[] IS_FIXED_PITCH = { 12, 1 };

	public static final byte[] ITALIC_ANGLE = { 12, 2 };

	public static final byte[] UNDERLINE_POSITION = { 12, 3 };

	public static final byte[] UNDERLINE_THICKNESS = { 12, 4 };

	public static final byte[] PAINT_TYPE = { 12, 5 };

	public static final byte[] CHARSTRING_TYPE = { 12, 6 };

	public static final byte[] FONT_MATRIX = { 12, 7 };

	public static final byte[] STROKE_WIDTH = { 12, 8 };

	public static final byte[] BLUE_SCALE = { 12, 9 };

	public static final byte[] BLUE_SHIFT = { 12, 10 };

	public static final byte[] BLUE_FUZZ = { 12, 11 };

	public static final byte[] STEM_SNAP_H = { 12, 12 };

	public static final byte[] STEM_SNAP_V = { 12, 13 };

	public static final byte[] FORCE_BOLD = { 12, 14 };

	public static final byte[] LANGUAGE_GROUP = { 12, 17 };

	public static final byte[] EXPANSION_FACTOR = { 12, 18 };

	public static final byte[] INITIAL_RANDOM_SEED = { 12, 19 };

	public static final byte[] SYNTHETIC_BASE = { 12, 20 };

	public static final byte[] POST_SCRIPT = { 12, 21 };

	public static final byte[] BASE_FONT_NAME = { 12, 22 };

	public static final byte[] BASE_FONT_BLEND = { 12, 23 };

	public static final byte[] ROS = { 12, 30 };

	public static final byte[] CID_FONT_VERSION = { 12, 31 };

	public static final byte[] CID_FONT_REVISION = { 12, 32 };

	public static final byte[] CID_FONT_TYPE = { 12, 33 };

	public static final byte[] CID_COUNT = { 12, 34 };

	public static final byte[] UID_BASE = { 12, 35 };

	public static final byte[] FD_ARRAY = { 12, 36 };

	public static final byte[] FD_SELECT = { 12, 37 };

	public static final byte[] FONT_NAME = { 12, 38 };

	private static final int NSTDSTRINGS = 391;

	private int offset = 0;

	/**
	 * Constructs a new CFF output stream wrapping the given underlying stream.
	 *
	 * @param out the underlying output stream
	 */
	public CFFOutputStream(OutputStream out) {
		super(out);
	}

	/** {@inheritDoc} */
	public void write(byte[] b, int off, int len) throws IOException {
		this.offset += len;
		this.out.write(b, off, len);
	}

	public void write(byte[] b) throws IOException {
		this.offset += b.length;
		this.out.write(b);
	}

	public void write(int b) throws IOException {
		++this.offset;
		this.out.write(b);
	}

	/**
	 * Returns the number of bytes written to this stream so far.
	 *
	 * @return the byte offset
	 */
	public int getOffset() {
		return this.offset;
	}

	/**
	 * Converts a string to its ISO-8859-1 byte representation.
	 *
	 * @param str the string to convert
	 * @return the ISO-8859-1 encoded bytes
	 */
	public static byte[] toBytes(String str) {
		try {
			return str.getBytes("ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Writes a single unsigned 8-bit integer (Card8).
	 *
	 * @param b the byte value to write
	 * @throws IOException if an I/O error occurs
	 */
	public void writeCard8(byte b) throws IOException {
		this.write(b);
	}

	/**
	 * Writes an unsigned 16-bit integer (Card16) in big-endian order.
	 *
	 * @param a the integer value to write (lower 16 bits used)
	 * @throws IOException if an I/O error occurs
	 */
	public void writeCard16(int a) throws IOException {
		this.write(a >> 8);
		this.write(a);
	}

	/**
	 * Writes a CFF OffSize field (1 byte, value must be 1–4).
	 *
	 * @param offSize the offset size in bytes (1–4)
	 * @throws IOException              if an I/O error occurs
	 * @throws IllegalArgumentException if {@code offSize} is not in range 1–4
	 */
	public void writeOffSize(byte offSize) throws IOException {
		if (offSize < 1 || offSize > 4) {
			throw new IllegalArgumentException();
		}
		this.write(offSize);
	}

	/**
	 * Writes an offset value using the specified number of bytes (big-endian).
	 *
	 * @param a    the offset value
	 * @param size the number of bytes to write (1–4)
	 * @throws IOException              if an I/O error occurs
	 * @throws IllegalArgumentException if {@code size} is not in range 1–4
	 */
	public void writeOffset(int a, int size) throws IOException {
		switch (size) {
			case 4:
				this.write(a >> 24);
			case 3:
				this.write(a >> 16);
			case 2:
				this.write(a >> 8);
			case 1:
				this.write(a);
				break;

			default:
				throw new IllegalArgumentException();
		}
	}

	/**
	 * Writes a non-standard String ID (NSSID), offset by the number of standard
	 * CFF strings (391).
	 *
	 * @param sid the zero-based index into the non-standard string INDEX
	 * @throws IOException              if an I/O error occurs
	 * @throws IllegalArgumentException if the resulting SID is out of range
	 */
	public void writeNSSID(int sid) throws IOException {
		sid += NSTDSTRINGS;
		if (sid < 0 || sid > 64999) {
			throw new IllegalArgumentException();
		}
		this.writeInteger(sid);
	}

	/**
	 * Writes a CFF DICT operator (1 or 2 bytes).
	 *
	 * @param o the operator byte sequence
	 * @return the number of bytes written
	 * @throws IOException if an I/O error occurs
	 */
	public int writeOperator(byte[] o) throws IOException {
		this.write(o);
		return o.length;
	}

	/**
	 * Encodes and writes an integer operand using the most compact CFF encoding.
	 *
	 * @param a the integer value to write
	 * @return the number of bytes written (1, 2, 3, or 5)
	 * @throws IOException if an I/O error occurs
	 */
	public int writeInteger(int a) throws IOException {
		if (a >= -107 && a <= 107) {
			this.write(a + 139);
			return 1;
		} else if (a >= 108 && a <= 1131) {
			a -= 108;
			this.write((a >> 8) + 247);
			this.write(a);
			return 2;
		} else if (a >= -1131 && a <= -108) {
			a += 108;
			this.write((-a >> 8) + 251);
			this.write(-a);
			return 2;
		} else if (a >= -32768 && a <= 32767) {
			this.write(28);
			this.write(a >> 8);
			this.write(a);
			return 3;
		} else {
			this.write(29);
			this.write(a >> 24);
			this.write(a >> 16);
			this.write(a >> 8);
			this.write(a);
			return 5;
		}
	}

	/**
	 * Encodes and writes a real-number operand using the CFF nibble encoding.
	 *
	 * @param real the decimal string representation of the real number
	 * @return the number of bytes written
	 * @throws IOException              if an I/O error occurs
	 * @throws IllegalArgumentException if {@code real} contains an unexpected character
	 */
	public int writeReal(String real) throws IOException {
		this.write(0x1e);

		int count = 1;
		byte b = 0;
		boolean low = false;

		int len = real.length();
		for (int i = 0; i < len; ++i) {
			char c = real.charAt(i);
			byte hex;
			switch (c) {
				case '.':
					hex = 0xA;
					break;

				case 'E':
					if (real.charAt(i + 1) == '-') {
						++i;
						hex = 0xC;
					} else {
						hex = 0xB;
					}
					break;

				case '-':
					hex = 0xE;
					break;

				default:
					if (c < '0' || c > '9') {
						throw new IllegalArgumentException();
					}
					hex = (byte) (c - '0');
					break;
			}

			if (low) {
				++count;
				this.write(b | hex);
				low = false;
			} else {
				b = (byte) (hex << 4);
				low = true;
			}
		}
		if (low) {
			this.write(b | 0xF);
		} else {
			this.write(0xFF);
		}
		return count + 1;
	}

	/**
	 * Writes the 4-byte CFF header.
	 *
	 * @param major   the major version (typically 1)
	 * @param minor   the minor version (typically 0)
	 * @param hdrSize the size of the header in bytes (typically 4)
	 * @param offSize the default offset size used in the top-level INDEXes
	 * @throws IOException if an I/O error occurs
	 */
	public void writeHeader(byte major, byte minor, byte hdrSize, byte offSize) throws IOException {
		this.writeCard8(major);
		this.writeCard8(minor);
		this.writeCard8(hdrSize);
		this.writeOffSize(offSize);
	}

	/**
	 * Writes a CFF INDEX structure containing the given objects.
	 *
	 * @param objects the array of byte arrays representing the INDEX entries
	 * @param offSize the number of bytes used for each offset in the INDEX
	 * @throws IOException if an I/O error occurs
	 */
	public void writeIndex(byte[][] objects, byte offSize) throws IOException {
		this.writeCard16((short) (objects.length));
		if (objects.length <= 0) {
			// Empty index only outputs the count
			return;
		}
		this.writeOffSize(offSize);

		// Position of each object (1-origin)
		int offset = 1;
		for (int i = 0; i < objects.length; ++i) {
			byte[] object = objects[i];
			this.writeOffset(offset, offSize);
			offset += object.length;
		}

		// Total data size + 1
		this.writeOffset(offset, offSize);

		// Data body
		for (int i = 0; i < objects.length; ++i) {
			byte[] object = objects[i];
			this.write(object);
		}
	}
}
