package net.zamasoft.pdfg2d.pdf.util;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Static utility methods for common PDF operations.
 * <p>
 * Provides unit conversion helpers (mm/cm/inch → points), standard paper-size
 * constants, and PDF name encoding/decoding per ISO 32000-1 section 7.3.5.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class PDFUtils {
	private PDFUtils() {
		// unused
	}

	/** Points per inch. */
	public static final double POINTS_PER_INCH = 72.0;

	/** Points per mm. */
	public static final double POINTS_PER_MM = POINTS_PER_INCH / 25.4;

	/** Points per cm. */
	public static final double POINTS_PER_CM = POINTS_PER_INCH / 2.54;

	/** Standard A4 paper width in millimetres (210 mm). */
	public static final double PAPER_A4_WIDTH_MM = 210.0;

	/** Standard A4 paper height in millimetres (297 mm). */
	public static final double PAPER_A4_HEIGHT_MM = 297.0;

	/** Recommended bleed/cutting margin in millimetres (3 mm). */
	public static final double CUTTING_MARGIN_MM = 3.0;

	/**
	 * Converts a length in millimetres to PDF user units (points).
	 *
	 * @param mm the length in millimetres
	 * @return the equivalent length in points (1 pt = 1/72 in)
	 */
	public static double mmToPt(final double mm) {
		return mm * POINTS_PER_MM;
	}

	private static final byte[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E',
			'F' };

	/**
	 * Encodes a PDF name string per the PDF specification: characters outside the
	 * printable ASCII range and reserved delimiter characters ({@code # ( ) [ ] { }
	 * &lt; &gt; / %}) are percent-encoded as {@code #XX} where {@code XX} is the
	 * uppercase hexadecimal byte value.
	 *
	 * @param s        the name string to encode
	 * @param encoding the character encoding to use when converting the string to bytes
	 * @return the encoded byte array ready for output to a PDF stream
	 * @throws UnsupportedEncodingException if the specified encoding is not supported
	 */
	public static byte[] encodeName(final String s, final String encoding) throws UnsupportedEncodingException {
		boolean encode = false;
		final byte[] b = s.getBytes(encoding);
		for (final byte c : b) {
			if (c >= '!' && c <= '~') {
				switch (c) {
					case '#':
					case '(':
					case ')':
					case '[':
					case ']':
					case '{':
					case '}':
					case '<':
					case '>':
					case '/':
					case '%':
						break;
					default:
						continue;
				}
			}
			encode = true;
			break;
		}
		if (!encode) {
			return b;
		}
		final ByteArrayOutputStream buff = new ByteArrayOutputStream();
		for (final byte c : b) {
			if (c >= '!' && c <= '~') {
				switch (c) {
					case '#':
					case '(':
					case ')':
					case '[':
					case ']':
					case '{':
					case '}':
					case '<':
					case '>':
					case '/':
					case '%': {
						buff.write('#');
						final short h = (short) ((c >> 4) & 0x0F);
						final short l = (short) (c & 0x0F);
						buff.write(HEX[h]);
						buff.write(HEX[l]);
					}
						break;
					default:
						buff.write(c);
						break;
				}
			} else {
				buff.write('#');
				final short h = (short) ((c >> 4) & 0x0F);
				final short l = (short) (c & 0x0F);
				buff.write(HEX[h]);
				buff.write(HEX[l]);
			}
		}
		return buff.toByteArray();
	}

	/**
	 * Decodes a PDF name string that was encoded with {@link #encodeName}.
	 * Each {@code #XX} escape sequence is replaced by the corresponding byte
	 * value, and the resulting byte array is converted back to a string using
	 * the given encoding.
	 *
	 * @param s        the encoded PDF name string
	 * @param encoding the character encoding used to interpret the decoded bytes
	 * @return the decoded name string
	 * @throws UnsupportedEncodingException if the specified encoding is not supported
	 */
	public static String decodeName(final String s, final String encoding) throws UnsupportedEncodingException {
		// #エスケープなしの純ASCIIはそのまま返す(2026-08-01)。フォント表
		// パーサ(CMap/CID表/AFM)がトークン毎にここを通るため、旧実装の
		// 「常にバイト列化→new String(bytes, encoding)」は毎回の
		// charset検索とデコーダ生成が起動時間の主要因になっていた
		// (JFR実測: 大文書1変換のCPUサンプルの約6割がフォント表パース)。
		// 前提: encodingはASCII互換(MS932/UTF-8等。PDF名の実データは
		// ほぼ全て素のASCII)
		boolean plainAscii = true;
		for (int i = 0; i < s.length(); ++i) {
			final char c = s.charAt(i);
			if (c == '#' || c >= 0x80) {
				plainAscii = false;
				break;
			}
		}
		if (plainAscii) {
			return s;
		}
		final char[] ch = s.toCharArray();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int i = 0; i < ch.length; ++i) {
			final char c = ch[i];
			if (c != '#') {
				out.write(c);
			} else {
				final char h = s.charAt(++i);
				final char l = s.charAt(++i);
				out.write(Integer.parseInt("" + h + l, 16));
			}
		}
		return new String(out.toByteArray(), encoding);
	}
}