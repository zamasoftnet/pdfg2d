package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * OpenType {@code post} (PostScript Information) table.
 * <p>
 * Contains data required for using the font on PostScript printers, including
 * PostScript glyph names and the italic angle.  Format 2.0 fonts store a
 * per-glyph name index that references either the built-in Mac glyph name
 * table or font-specific names stored inline.
 * </p>
 *
 * @param version        the table version (e.g., {@code 0x00020000} for version 2.0)
 * @param glyphNameIndex per-glyph index into the combined Mac/font name arrays; {@code null} for non-2.0 fonts
 * @param psGlyphName    font-specific PostScript glyph names referenced by indices above 257;
 *                       {@code null} if all names come from the standard Mac table
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record PostTable(int version, int[] glyphNameIndex, String[] psGlyphName) implements Table {

	/**
	 * Mac glyph names for standard Mac encoding.
	 */
	private static final String[] MAC_GLYPH_NAME = {
			".notdef", "null", "CR", "space", "exclam", "quotedbl", "numbersign", "dollar", "percent", "ampersand",
			"quotesingle", "parenleft", "parenright", "asterisk", "plus", "comma", "hyphen", "period", "slash", "zero",
			"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "colon", "semicolon", "less",
			"equal",
			"greater", "question", "at", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P",
			"Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "bracketleft", "backslash", "bracketright", "asciicircum",
			"underscore", "grave", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q",
			"r", "s", "t", "u", "v", "w", "x", "y", "z", "braceleft", "bar", "braceright", "asciitilde", "Adieresis",
			"Aring", "Ccedilla", "Eacute", "Ntilde", "Odieresis", "Udieresis", "aacute", "agrave", "acircumflex",
			"adieresis", "atilde", "aring", "ccedilla", "eacute", "egrave", "ecircumflex", "edieresis", "iacute",
			"igrave",
			"icircumflex", "idieresis", "ntilde", "oacute", "ograve", "ocircumflex", "odieresis", "otilde", "uacute",
			"ugrave", "ucircumflex", "udieresis", "dagger", "degree", "cent", "sterling", "section", "bullet",
			"paragraph",
			"germandbls", "registered", "copyright", "trademark", "acute", "dieresis", "notequal", "AE", "Oslash",
			"infinity", "plusminus", "lessequal", "greaterequal", "yen", "mu", "partialdiff", "summation", "product",
			"pi",
			"integral'", "ordfeminine", "ordmasculine", "Omega", "ae", "oslash", "questiondown", "exclamdown",
			"logicalnot",
			"radical", "florin", "approxequal", "increment", "guillemotleft", "guillemotright", "ellipsis", "nbspace",
			"Agrave", "Atilde", "Otilde", "OE", "oe", "endash", "emdash", "quotedblleft", "quotedblright", "quoteleft",
			"quoteright", "divide", "lozenge", "ydieresis", "Ydieresis", "fraction", "currency", "guilsinglleft",
			"guilsinglright", "fi", "fl", "daggerdbl", "middot", "quotesinglbase", "quotedblbase", "perthousand",
			"Acircumflex", "Ecircumflex", "Aacute", "Edieresis", "Egrave", "Iacute", "Icircumflex", "Idieresis",
			"Igrave",
			"Oacute", "Ocircumflex", "", "Ograve", "Uacute", "Ucircumflex", "Ugrave", "dotlessi", "circumflex", "tilde",
			"overscore", "breve", "dotaccent", "ring", "cedilla", "hungarumlaut", "ogonek", "caron", "Lslash", "lslash",
			"Scaron", "scaron", "Zcaron", "zcaron", "brokenbar", "Eth", "eth", "Yacute", "yacute", "Thorn", "thorn",
			"minus", "multiply", "onesuperior", "twosuperior", "threesuperior", "onehalf", "onequarter",
			"threequarters",
			"franc", "Gbreve", "gbreve", "Idot", "Scedilla", "scedilla", "Cacute", "cacute", "Ccaron", "ccaron", ""
	};

	protected PostTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private PostTable(PostTable other) {
		this(other.version, other.glyphNameIndex, other.psGlyphName);
	}

	private static PostTable readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());
			final int version = raf.readInt();
			raf.readInt(); // italicAngle
			raf.readShort(); // underlinePosition
			raf.readShort(); // underlineThickness
			raf.readInt(); // isFixedPitch
			raf.readInt(); // minMemType42
			raf.readInt(); // maxMemType42
			raf.readInt(); // minMemType1
			raf.readInt(); // maxMemType1

			int[] glyphNameIndex = null;
			String[] psGlyphName = null;

			if (version == 0x00020000) {
				final int numGlyphs = raf.readUnsignedShort();
				glyphNameIndex = new int[numGlyphs];
				for (int i = 0; i < numGlyphs; i++) {
					glyphNameIndex[i] = raf.readUnsignedShort();
				}
				int high = 0;
				for (int i = 0; i < numGlyphs; i++) {
					if (high < glyphNameIndex[i]) {
						high = glyphNameIndex[i];
					}
				}
				if (high > 257) {
					final int h = high - 257;
					psGlyphName = new String[h];
					for (int i = 0; i < h; i++) {
						final int len = raf.readUnsignedByte();
						final byte[] buf = new byte[len];
						raf.readFully(buf);
						psGlyphName[i] = new String(buf, StandardCharsets.US_ASCII);
					}
				}
			}
			return new PostTable(version, glyphNameIndex, psGlyphName);
		}
	}

	/**
	 * Returns the PostScript name for the glyph at the given index.
	 * Returns {@code null} for fonts whose version is not 2.0.
	 *
	 * @param i the glyph index
	 * @return the PostScript glyph name, or {@code null}
	 */
	public String getGlyphName(final int i) {
		if (this.version == 0x00020000) {
			return (this.glyphNameIndex[i] > 257)
					? this.psGlyphName[this.glyphNameIndex[i] - 258]
					: MAC_GLYPH_NAME[this.glyphNameIndex[i]];
		} else {
			return null;
		}
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return POST;
	}
}
