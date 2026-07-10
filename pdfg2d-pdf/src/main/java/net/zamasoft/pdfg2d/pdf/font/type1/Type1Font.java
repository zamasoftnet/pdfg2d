package net.zamasoft.pdfg2d.pdf.font.type1;

import java.io.IOException;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.PDFGraphicsOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.util.PDFFontUtils;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

/**
 * Standard Type1 font.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class Type1Font implements PDFFont {
	private static final long serialVersionUID = 0L;

	private final AbstractType1FontSource source;

	private final String name, encoding;

	private final ObjectRef fontRef;

	/**
	 * Constructs a Type1Font with the given source, PDF resource name, encoding, and object reference.
	 *
	 * @param source   the Type 1 font source providing glyph metrics and the AWT font
	 * @param name     the PDF resource name used to reference this font in content streams
	 * @param encoding the PDF encoding name (e.g. {@code "WinAnsiEncoding"}), or {@code null}
	 *                 to use the font's built-in encoding
	 * @param fontRef  the indirect object reference for the font dictionary in the PDF file
	 */
	Type1Font(AbstractType1FontSource source, String name, String encoding, ObjectRef fontRef) {
		this.source = source;
		this.name = name;
		this.encoding = encoding;
		this.fontRef = fontRef;
	}

	/**
	 * Returns the font source backing this Type 1 font.
	 *
	 * @return the font source
	 */
	public FontSource getFontSource() {
		return this.source;
	}

	/**
	 * Converts a Unicode code point to a glyph ID using this font's encoding.
	 *
	 * @param c the Unicode code point
	 * @return the corresponding glyph ID
	 */
	public int toGID(int c) {
		int gid = this.source.toGID(c);
		return gid;
	}

	/**
	 * Returns the kerning adjustment between two glyphs.
	 *
	 * @param scid the glyph ID of the first (source) glyph
	 * @param cid  the glyph ID of the second glyph
	 * @return the kerning value in design units
	 */
	public short getKerning(int scid, int cid) {
		return this.source.getKerning(scid, cid);
	}

	/**
	 * Returns the ligature glyph ID formed by the given glyph followed by the given character.
	 *
	 * @param gid the glyph ID of the first glyph
	 * @param cid the character code of the following character
	 * @return the ligature glyph ID, or {@code -1} if no ligature exists
	 */
	public int getLigature(int gid, int cid) {
		return this.source.getLigature(gid, cid);
	}

	/**
	 * Returns the horizontal advance width for the glyph with the given glyph ID.
	 *
	 * @param gid the glyph ID
	 * @return the advance width in design units
	 */
	public short getAdvance(int gid) {
		return this.source.getAdvance(gid);
	}

	/**
	 * Returns the width of the glyph with the given glyph ID.
	 * For Type 1 fonts the width equals the advance width.
	 *
	 * @param gid the glyph ID
	 * @return the glyph width in design units
	 */
	public short getWidth(int gid) {
		return this.getAdvance(gid);
	}

	/**
	 * Returns the PDF resource name of this font.
	 *
	 * @return the font resource name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Draws the given text using this font into the provided graphics context.
	 * When the graphics context is a {@link PDFGC}, glyphs are written as a PDF {@code TJ}
	 * operator with inline kerning adjustments. Otherwise the AWT font is used for rendering.
	 *
	 * @param gc   the graphics context to draw into
	 * @param text the text run to draw, including glyph IDs and advance widths
	 * @throws IOException       if an I/O error occurs while writing to the PDF output
	 * @throws GraphicsException if a graphics-level error occurs during rendering
	 */
	public void drawTo(GC gc, Text text) throws IOException, GraphicsException {
		if (gc instanceof PDFGC) {
			// PDF
			PDFGraphicsOutput out = ((PDFGC) gc).getPDFGraphicsOutput();
			int glyphCount = text.getGlyphCount();
			int[] glyphIds = text.getGlyphIds();
			double[] xadvances = text.getXAdvances(false);
			double size = text.getFontMetrics().getFontSize();
			out.startArray();
			int pgid = 0;
			StringBuilder buff = new StringBuilder();
			for (int j = 0; j < glyphCount; ++j) {
				int gid = glyphIds[j];
				short kerning = this.source.getKerning(gid, pgid);
				if (xadvances != null) {
					if (j == 0) {
						double xadvance = xadvances[j];
						if (xadvance != 0) {
							out.writeReal(-xadvance * FontSource.DEFAULT_UNITS_PER_EM / size);
						}
					} else {
						kerning += xadvances[j] * FontSource.DEFAULT_UNITS_PER_EM / size;
					}
				}
				if (kerning != 0) {
					out.writeString(buff.toString());
					buff.delete(0, buff.length());
					out.writeInt(-kerning);
				}
				buff.append((char) gid);
				pgid = gid;
			}
			out.writeString(buff.toString());
			out.endArray();
			out.writeOperator("TJ");
		} else {
			PDFFontUtils.drawAwtFont(gc, this.source, this.source.getAwtFont(), text);
		}
	}

	/**
	 * Writes the PDF font dictionary object for this Type 1 font to the given output stream.
	 * The dictionary includes the {@code Type}, {@code Subtype}, {@code Name},
	 * {@code BaseFont}, and optionally {@code Encoding} entries.
	 *
	 * @param out  the PDF fragment output stream to write to
	 * @param xref the cross-reference table (unused for Type 1 fonts but required by the interface)
	 * @throws IOException if an I/O error occurs while writing
	 */
	public void writeTo(PDFFragmentOutput out, XRef xref) throws IOException {
		out.startObject(this.fontRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("Font");
		out.lineBreak();
		out.writeName("Subtype");
		out.writeName("Type1");
		out.lineBreak();
		if (this.encoding != null) {
			out.writeName("Encoding");
			out.writeName(this.encoding);
			out.lineBreak();
		}
		out.writeName("Name");
		out.writeName(this.name);
		out.lineBreak();
		out.writeName("BaseFont");
		out.writeName(this.source.getFontName());
		out.lineBreak();
		out.endHash();
		out.endObject();
	}

	public String toString() {
		return super.toString() + ":[PDFName=" + this.getName() + " source=" + this.getFontSource() + "]";
	}
}