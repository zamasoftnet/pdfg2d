package net.zamasoft.pdfg2d.pdf.font.cid;

import java.io.IOException;
import java.io.OutputStream;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.gc.font.Panose;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.PDFEmbeddedFont;
import net.zamasoft.pdfg2d.pdf.font.cid.ToUnicode.Unicode;
import net.zamasoft.pdfg2d.pdf.font.type2.CFFGenerator;
import net.zamasoft.pdfg2d.util.ArrayShortMapIterator;

/**
 * Utility class for writing CID font structures into PDF output streams.
 * Provides helpers for writing font flags, glyph-width arrays, embedded
 * CFF font data, and ToUnicode CMaps.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class CIDUtils {
	public static final String ENCODING_H = "Identity-H";

	public static final String ENCODING_V = "Identity-V";

	public static final String REGISTRY = "Adobe";

	public static final String ORDERING = "Identity";

	public static final int SUPPLEMENT = 0;

	public static final int CID_FIXED_WIDTH = 1;

	public static final int CID_SERIF = 1 << 1;

	public static final int CID_SYMBOLIC = 1 << 2;

	public static final int CID_SCRIPT = 1 << 3;

	public static final int CID_ITALIC = 1 << 6;

	public static final int CID_ALL_CAP = 1 << 16;

	public static final int CID_SMALL_CAP = 1 << 17;

	public static final int CID_FORCE_BOLD = 1 << 18;

	public static final int DEFAULT_VERTICAL_ORIGIN = 880;

	public static final int DEFAULT_H = 500;

	private CIDUtils() {
		// ignore
	}

	/**
	 * Writes the PDF font Flags entry and the optional Panose Style entry for the
	 * given CID font source.
	 *
	 * @param out    the PDF output stream
	 * @param source the CID font source whose metrics and Panose data are used
	 * @throws IOException if an I/O error occurs
	 */
	public static void writeFlagsAndPanose(PDFOutput out, CIDFontSource source) throws IOException {
		int flags = CID_SYMBOLIC;
		Panose panose = source.getPanose();
		if (panose != null) {
			if (panose.proportion() >= 8) {
				flags |= CID_FIXED_WIDTH;
			}
			if (panose.serifStyle() <= 3) {
				flags |= CID_SERIF;
			}
			if (panose.familyType() == 3) {
				flags |= CID_SCRIPT;
			}
			if (panose.letterForm() >= 9) {
				flags |= CID_ITALIC;
			}
			if (panose.familyType() == 4 && panose.xHeight() == 4) {
				flags |= CID_ALL_CAP;
			}
			if (panose.familyType() == 4 && panose.xHeight() == 5) {
				flags |= CID_SMALL_CAP;
			}
			if (panose.weight() >= 8) {
				flags |= CID_FORCE_BOLD;
			}
		} else {
			if (source.isItalic()) {
				flags |= CID_ITALIC;
			}
			if (source.getWeight().w >= 500) {
				flags |= CID_FORCE_BOLD;
			}
		}

		out.writeName("Flags");
		out.writeInt(flags);
		out.lineBreak();

		if (panose != null) {
			byte[] bytes = new byte[12];
			bytes[0] = panose.familyClassId();
			bytes[1] = panose.familySubclass();
			bytes[2] = panose.familyType();
			bytes[3] = panose.serifStyle();
			bytes[4] = panose.weight();
			bytes[5] = panose.proportion();
			bytes[6] = panose.contrast();
			bytes[7] = panose.strokeVariation();
			bytes[8] = panose.armStyle();
			bytes[9] = panose.letterForm();
			bytes[10] = panose.midline();
			bytes[11] = panose.xHeight();
			out.writeName("Style");
			out.startHash();
			out.writeName("Panose");
			out.writeBytes8(bytes, 0, bytes.length);
			out.endHash();
			out.lineBreak();
		}
	}

	/**
	 * Writes the DW (default width) and W (width) arrays for a horizontal CID font.
	 *
	 * @param out    the PDF output stream
	 * @param warray the width array containing per-glyph widths
	 * @throws IOException if an I/O error occurs
	 */
	public static void writeWArray(PDFOutput out, WArray warray) throws IOException {
		out.writeName("DW");
		out.writeInt(warray.getDefaultWidth());
		out.lineBreak();
		final var widths = warray.getWidths();
		out.writeName("W");
		out.startArray();
		if (widths.length > 0) {
			out.lineBreak();
			for (final var w : widths) {
				final var shorts = w.widths();
				if (shorts.length == 1) {
					out.writeInt(w.firstCode());
					out.writeInt(w.lastCode());
					out.writeInt(shorts[0]);
				} else {
					if (shorts.length <= (w.lastCode() - w.firstCode())) {
						out.writeInt(w.firstCode());
						out.startArray();
						for (int j = 0; j < shorts.length - 1; ++j) {
							out.writeInt(shorts[j]);
						}
						out.endArray();
						out.writeInt(w.firstCode() + (shorts.length - 1));
						out.writeInt(w.lastCode());
						out.writeInt(shorts[shorts.length - 1]);
					} else {
						out.writeInt(w.firstCode());
						out.startArray();
						for (int j = 0; j < shorts.length; ++j) {
							out.writeInt(shorts[j]);
						}
						out.endArray();
					}
				}
				out.lineBreak();
			}
		}
		out.endArray();
	}

	/**
	 * Writes the DW2 (default vertical metrics) and W2 (vertical widths) arrays
	 * for a vertical CID font.
	 *
	 * @param out    the PDF output stream
	 * @param warray the width array containing per-glyph vertical widths
	 * @throws IOException if an I/O error occurs
	 */
	public static void writeWArray2(PDFOutput out, WArray warray) throws IOException {
		out.writeName("DW2");
		out.startArray();
		out.writeInt(DEFAULT_VERTICAL_ORIGIN);
		out.writeInt(-warray.getDefaultWidth());
		out.endArray();
		out.lineBreak();
		final var widths = warray.getWidths();
		out.writeName("W2");
		out.startArray();
		if (widths.length > 0) {
			out.lineBreak();
			for (final var w : widths) {
				final var shorts = w.widths();
				if (shorts.length == 1) {
					out.writeInt(w.firstCode());
					out.writeInt(w.lastCode());
					out.writeInt(-shorts[0]);
					out.writeInt(DEFAULT_H);
					out.writeInt(DEFAULT_VERTICAL_ORIGIN);
				} else {
					if (shorts.length <= (w.lastCode() - w.firstCode())) {
						out.writeInt(w.firstCode());
						out.startArray();
						for (int j = 0; j < shorts.length - 1; ++j) {
							out.writeInt(-shorts[j]);
							out.writeInt(DEFAULT_H);
							out.writeInt(DEFAULT_VERTICAL_ORIGIN);
						}
						out.endArray();
						out.writeInt(w.firstCode() + (shorts.length - 1));
						out.writeInt(w.lastCode());
						out.writeInt(-shorts[shorts.length - 1]);
						out.writeInt(DEFAULT_H);
						out.writeInt(DEFAULT_VERTICAL_ORIGIN);
					} else {
						out.writeInt(w.firstCode());
						out.startArray();
						for (int j = 0; j < shorts.length; ++j) {
							out.writeInt(-shorts[j]);
							out.writeInt(DEFAULT_H);
							out.writeInt(DEFAULT_VERTICAL_ORIGIN);
						}
						out.endArray();
					}
				}
				out.lineBreak();
			}
		}
		out.endArray();
	}

	/**
	 * Writes a non-embedded (identity) CID font, using Adobe-Identity encoding,
	 * into the PDF output.
	 *
	 * @param out          the PDF fragment output stream
	 * @param xref         the cross-reference table for allocating object references
	 * @param source       the CID font source providing metrics and metadata
	 * @param fontRef      the object reference for the top-level font dictionary
	 * @param w            horizontal glyph widths indexed by GID
	 * @param w2           vertical glyph widths indexed by GID, or {@code null} for
	 *                     horizontal-only fonts
	 * @param unicodeArray mapping from GID to Unicode code point
	 * @throws IOException if an I/O error occurs
	 */
	public static void writeIdentityFont(PDFFragmentOutput out, XRef xref, CIDFontSource source, ObjectRef fontRef,
			short[] w, short[] w2, int[] unicodeArray) throws IOException {
		// Main font
		String fontName = source.getFontName();
		out.startObject(fontRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("Font");
		out.lineBreak();
		out.writeName("Subtype");
		out.writeName("Type0");
		out.lineBreak();
		out.writeName("BaseFont");
		out.writeName(fontName);
		out.lineBreak();
		out.writeName("DescendantFonts");
		out.startArray();
		ObjectRef xfontRef = xref.nextObjectRef();
		out.writeObjectRef(xfontRef);
		out.endArray();
		out.lineBreak();
		out.writeName("Encoding");
		out.writeName((w2 != null) ? ENCODING_V : ENCODING_H);

		// ToUnicode
		ObjectRef toUnicodeRef = xref.nextObjectRef();
		out.lineBreak();
		out.writeName("ToUnicode");
		out.writeObjectRef(toUnicodeRef);
		out.endHash();
		out.endObject();

		out.startObject(toUnicodeRef);
		PDFOutput pout = new PDFOutput(out.startStream(PDFFragmentOutput.Mode.ASCII), "ISO-8859-1");
		pout.setPrecision(out.getPrecision());
		CIDUtils.writeIdentityToUnicode(pout, unicodeArray);
		out.endObject();

		// Descendant font
		out.startObject(xfontRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("Font");
		out.lineBreak();
		out.writeName("Subtype");
		out.writeName("CIDFontType2");
		out.lineBreak();
		out.writeName("BaseFont");
		out.writeName(fontName);
		out.lineBreak();
		out.writeName("FontDescriptor");
		ObjectRef fontDescRef = xref.nextObjectRef();
		out.writeObjectRef(fontDescRef);
		out.lineBreak();
		out.writeName("CIDSystemInfo");
		out.startHash();
		out.writeName("Registry");
		out.writeString(REGISTRY);
		out.writeName("Ordering");
		out.writeString(ORDERING);
		out.writeName("Supplement");
		out.writeInt(SUPPLEMENT);
		out.lineBreak();
		out.writeName("CIDToGIDMap");
		out.writeName("Identity");
		out.lineBreak();
		out.endHash();

		// WArray
		{
			WArray warray = WArray.buildFromWidths(new ArrayShortMapIterator(w));
			CIDUtils.writeWArray(out, warray);
		}
		if (w2 != null && w2.length > 0) {
			WArray warray = WArray.buildFromWidths(new ArrayShortMapIterator(w2));
			CIDUtils.writeWArray2(out, warray);
		}

		out.endHash();
		out.endObject();

		// Font descriptor
		out.startObject(fontDescRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("FontDescriptor");
		out.lineBreak();
		out.writeName("FontName");
		out.writeName(fontName);
		out.lineBreak();
		writeFlagsAndPanose(out, source);
		out.writeName("FontBBox");
		BBox bbox = source.getBBox();
		out.startArray();
		out.writeInt(bbox.llx());
		out.writeInt(bbox.lly());
		out.writeInt(bbox.urx());
		out.writeInt(bbox.ury());
		out.endArray();
		out.lineBreak();
		out.writeName("StemV");
		out.writeInt(source.getStemV());
		out.lineBreak();
		out.writeName("ItalicAngle");
		out.writeInt(0);
		out.lineBreak();
		out.writeName("CapHeight");
		out.writeInt(source.getCapHeight());
		out.lineBreak();
		out.writeName("XHeight");
		out.writeInt(source.getXHeight());
		out.lineBreak();
		out.writeName("Ascent");
		out.writeInt(source.getAscent());
		out.lineBreak();
		out.writeName("Descent");
		out.writeInt(-source.getDescent());
		out.lineBreak();
		out.endHash();
		out.endObject();
	}

	private static void writeIdentityToUnicode(PDFOutput pout, int[] unicodeArray) throws IOException {
		boolean sparse = false;
		for (final int unicode : unicodeArray) {
			if (unicode < 0) {
				sparse = true;
				break;
			}
		}
		ToUnicode toUnicode = sparse ? ToUnicode.buildFromSparseChars(unicodeArray)
				: ToUnicode.buildFromChars(unicodeArray);

		pout.writeName("CIDInit");
		pout.writeName("ProcSet");
		pout.writeOperator("findresource");
		pout.writeOperator("begin");
		pout.lineBreak();

		pout.writeInt(12);
		pout.writeOperator("dict");
		pout.writeOperator("begin");
		pout.lineBreak();

		pout.writeOperator("begincmap");
		pout.lineBreak();

		pout.writeName("CIDSystemInfo");
		pout.lineBreak();

		pout.startHash();

		pout.writeName("Registry");
		pout.writeString("Adobe");
		pout.lineBreak();

		pout.writeName("Ordering");
		pout.writeString("UCS");
		pout.lineBreak();

		pout.writeName("Supplement");
		pout.writeInt(0);
		pout.lineBreak();

		pout.endHash();
		pout.writeOperator("def");
		pout.lineBreak();

		pout.writeName("CMapName");
		pout.writeName("Adobe-Identity-UCS");
		pout.writeOperator("def");
		pout.lineBreak();

		pout.writeName("CMapType");
		pout.writeInt(2);
		pout.writeOperator("def");
		pout.lineBreak();

		pout.writeInt(1);
		pout.writeOperator("begincodespacerange");
		pout.lineBreak();

		pout.writeBytes16(0);
		pout.writeBytes16(0xFFFF);
		pout.lineBreak();

		pout.writeOperator("endcodespacerange");
		pout.lineBreak();

		Unicode[] unicodes = toUnicode.getUnicodes();
		pout.writeInt(unicodes.length);
		pout.writeOperator("beginbfrange");
		pout.lineBreak();

		for (int i = 0; i < unicodes.length; ++i) {
			Unicode u = unicodes[i];
			int[] chars = u.getUnicodes();
			if (chars.length == 1) {
				pout.writeBytes16(u.getFirstCode());
				pout.writeBytes16(u.getLastCode());
				pout.writeBytes16(chars[0]);
			} else {
				pout.writeBytes16(u.getFirstCode());
				pout.writeBytes16(u.getLastCode());
				pout.startArray();
				for (int j = 0; j < chars.length; ++j) {
					pout.writeBytes16(chars[j]);
				}
				pout.endArray();
			}
			pout.lineBreak();
		}

		pout.writeOperator("endbfrange");
		pout.lineBreak();

		pout.writeOperator("endcmap");
		pout.lineBreak();

		pout.writeOperator("CMapName");
		pout.writeOperator("currentdict");
		pout.writeName("CMap");
		pout.writeOperator("defineresource");
		pout.writeOperator("pop");
		pout.lineBreak();

		pout.writeOperator("end");
		pout.writeOperator("end");
		pout.lineBreak();

		pout.close();
	}

	/** Returns a deterministic name for one embedded physical subset. */
	public static String createEmbeddedSubsetName(final short[] w, final short[] w2, final int[] glyphSignature,
			final String psName) {
		final int h = new FontSubsetCache.Key(w, w2, glyphSignature).contentHash();
		final char a = (char) ('A' + (h & 0xF));
		final char b = (char) ('A' + ((h >> 4) & 0xF));
		final char c = (char) ('A' + ((h >> 8) & 0xF));
		final char d = (char) ('A' + ((h >> 12) & 0xF));
		final char e = (char) ('A' + ((h >> 16) & 0xF));
		final char f = (char) ('A' + ((h >> 20) & 0xF));
		return "" + a + b + c + d + e + f + '+' + sanitizeEmbeddedPostScriptName(psName);
	}

	/** PDF NameとCFF Name INDEXの両方に使えるASCII名に制限する。 */
	private static String sanitizeEmbeddedPostScriptName(final String psName) {
		final var name = new StringBuilder(psName == null ? 0 : psName.length());
		if (psName != null) {
			for (int i = 0; i < psName.length(); ++i) {
				final char c = psName.charAt(i);
				if (c < '!' || c > '~' || c == '#' || c == '(' || c == ')' || c == '<' || c == '>' || c == '['
						|| c == ']' || c == '{' || c == '}' || c == '/' || c == '%') {
					continue;
				}
				name.append(c);
			}
		}
		return name.isEmpty() ? "SubsetFont" : name.toString();
	}

	/** Writes the direction-specific Type0 wrapper and its sparse ToUnicode map. */
	public static void writeEmbeddedFontType0(final PDFFragmentOutput out, final XRef xref,
			final ObjectRef fontRef, final ObjectRef descendantRef, final String subsetName, final boolean vertical,
			final int[] unicodeArray) throws IOException {
		out.startObject(fontRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("Font");
		out.lineBreak();
		out.writeName("Subtype");
		out.writeName("Type0");
		out.lineBreak();
		out.writeName("BaseFont");
		out.writeName(subsetName);
		out.lineBreak();
		out.writeName("DescendantFonts");
		out.startArray();
		out.writeObjectRef(descendantRef);
		out.endArray();
		out.lineBreak();
		out.writeName("Encoding");
		out.writeName(vertical ? ENCODING_V : ENCODING_H);

		// ToUnicode
		ObjectRef toUnicodeRef = xref.nextObjectRef();
		out.lineBreak();
		out.writeName("ToUnicode");
		out.writeObjectRef(toUnicodeRef);
		out.endHash();
		out.endObject();

		out.startObject(toUnicodeRef);
		PDFOutput pout = new PDFOutput(out.startStream(PDFFragmentOutput.Mode.ASCII), "ISO-8859-1");
		pout.setPrecision(out.getPrecision());
		CIDUtils.writeIdentityToUnicode(pout, unicodeArray);
		out.endObject();
	}

	/** Writes an embedded font using one private descendant and program. */
	public static void writeEmbeddedFont(final PDFFragmentOutput out, final XRef xref, final CIDFontSource source,
			final PDFEmbeddedFont font, final ObjectRef fontRef, final short[] w, final short[] w2,
			final int[] unicodeArray) throws IOException {
		final ObjectRef descendantRef = xref.nextObjectRef();
		final String subsetName = createEmbeddedSubsetName(w, w2, unicodeArray, font.getPSName());
		writeEmbeddedFontType0(out, xref, fontRef, descendantRef, subsetName, w2 != null, unicodeArray);
		writeEmbeddedFontProgram(out, xref, source, font, descendantRef, subsetName, w, w2, unicodeArray);
	}

	/** Writes the descendant CIDFont, descriptor, CIDSet, and FontFile3 once. */
	public static void writeEmbeddedFontProgram(final PDFFragmentOutput out, final XRef xref,
			final CIDFontSource source, final PDFEmbeddedFont font, final ObjectRef descendantRef,
			final String subsetName, final short[] w, final short[] w2, final int[] glyphSignature)
			throws IOException {
		final BBox subsetBBox = CFFGenerator.calculateSubsetBBox(font);
		final FontSubsetCache.Key subsetKey = new FontSubsetCache.Key(w, w2, glyphSignature);

		// Descendant font
		out.startObject(descendantRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("Font");
		out.lineBreak();
		out.writeName("Subtype");
		out.writeName("CIDFontType0");
		out.lineBreak();
		out.writeName("BaseFont");
		out.writeName(subsetName);
		out.lineBreak();
		out.writeName("FontDescriptor");
		ObjectRef fontDescRef = xref.nextObjectRef();
		out.writeObjectRef(fontDescRef);
		out.lineBreak();
		out.writeName("CIDSystemInfo");
		out.startHash();
		out.writeName("Registry");
		out.writeString(CIDUtils.REGISTRY);
		out.writeName("Ordering");
		out.writeString(CIDUtils.ORDERING);
		out.writeName("Supplement");
		out.writeInt(CIDUtils.SUPPLEMENT);
		out.endHash();

		// WArray
		{
			WArray warray = WArray.buildFromWidths(new ArrayShortMapIterator(w));
			CIDUtils.writeWArray(out, warray);
		}
		if (w2 != null && w2.length > 0) {
			WArray warray = WArray.buildFromWidths(new ArrayShortMapIterator(w2));
			CIDUtils.writeWArray2(out, warray);
		}

		out.endHash();
		out.endObject();

		// Font descriptor
		out.startObject(fontDescRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("FontDescriptor");
		out.lineBreak();
		out.writeName("FontName");
		out.writeName(subsetName);
		out.lineBreak();
		writeFlagsAndPanose(out, source);
		out.writeName("FontBBox");
		BBox bbox = subsetBBox;
		out.startArray();
		out.writeInt(bbox.llx());
		out.writeInt(bbox.lly());
		out.writeInt(bbox.urx());
		out.writeInt(bbox.ury());
		out.endArray();
		out.lineBreak();
		out.writeName("StemV");
		out.writeInt(source.getStemV());
		out.lineBreak();
		out.writeName("ItalicAngle");
		out.writeInt(0);
		out.lineBreak();
		out.writeName("CapHeight");
		out.writeInt(source.getCapHeight());
		out.lineBreak();
		out.writeName("XHeight");
		out.writeInt(source.getXHeight());
		out.lineBreak();
		out.writeName("Ascent");
		out.writeInt(source.getAscent());
		out.lineBreak();
		out.writeName("Descent");
		out.writeInt(-source.getDescent());
		out.lineBreak();
		ObjectRef cidSetRef = xref.nextObjectRef();
		out.writeName("CIDSet");
		out.writeObjectRef(cidSetRef);
		out.lineBreak();
		ObjectRef fontFile3Ref = xref.nextObjectRef();
		out.writeName("FontFile3");
		out.writeObjectRef(fontFile3Ref);
		out.endHash();
		out.endObject();

		// CIDSet
		// CIDs being used
		out.startObject(cidSetRef);
		out.startHash();
		try (OutputStream sout = out.startStreamFromHash(PDFFragmentOutput.Mode.BINARY)) {
			final int glyphCount = font.getGlyphCount();
			int bytes = (int) Math.ceil(glyphCount / 8.0);
			for (int i = 0; i < bytes; ++i) {
				int start = i * 8;
				int end = start + 8;
				int b = 0;
				for (int j = start; j < end; ++j) {
					if (j < glyphCount) {
						b |= (1 << (end - j - 1));
					}
				}
				sout.write(b);
			}
		}
		out.endObject();

		// Embed CFF font data
		out.startObject(fontFile3Ref);
		out.startHash();
		out.writeName("Subtype");
		out.writeName("CIDFontType0C");
		out.lineBreak();

		try (OutputStream cout = out.startStreamFromHash(PDFFragmentOutput.Mode.BINARY)) {
			// Reuse the generated program when the same source produced the
			// same subset before (batch generation with a shared
			// FontSourceManager); charstring generation dominates embedding
			// cost.
			byte[] program = FontSubsetCache.get(source, subsetKey);
			if (program == null) {
				java.io.ByteArrayOutputStream buff = new java.io.ByteArrayOutputStream(1 << 14);
				CFFGenerator cff = new CFFGenerator();
				cff.setSubsetName(subsetName);
				cff.setEmbedableFont(font);
				cff.setBBox(subsetBBox);
				cff.writeTo(buff);
				program = buff.toByteArray();
				FontSubsetCache.put(source, subsetKey, program);
			}
			cout.write(program);
		}

		out.endObject();
	}
}
