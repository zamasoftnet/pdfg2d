package net.zamasoft.pdfg2d.pdf.font.cid;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;

/**
 * Abstract base class for CID-keyed PDF fonts. A CID font references glyphs
 * by CID (Character Identifier) and supports large character sets such as
 * East Asian scripts. Subclasses handle specific CID font subtypes (e.g.
 * identity-mapped or keyed CID fonts).
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class CIDFont implements PDFFont {
	private static final long serialVersionUID = 0L;

	protected final FontSource source;

	protected final String name;

	protected final ObjectRef fontRef;

	/**
	 * Constructs a CIDFont with the given font source, PDF resource name, and object reference.
	 *
	 * @param metaFont the font source that provides glyph metrics and encoding information
	 * @param name     the PDF resource name used to reference this font in content streams
	 * @param fontRef  the indirect object reference for the font dictionary in the PDF file
	 */
	protected CIDFont(FontSource metaFont, String name, ObjectRef fontRef) {
		this.source = metaFont;
		this.name = name;
		this.fontRef = fontRef;
	}

	/**
	 * Returns the font source that backs this CID font.
	 *
	 * @return the font source
	 */
	public FontSource getFontSource() {
		return this.source;
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
	 * Returns the kerning adjustment between two glyphs. CID fonts do not support
	 * pair kerning, so this always returns 0.
	 *
	 * @param gid the glyph ID of the first glyph
	 * @param c   the character code of the second character
	 * @return always {@code 0}
	 */
	public short getKerning(int gid, int c) {
		return 0;
	}

	/**
	 * Returns the ligature glyph ID for the given glyph and following character.
	 * CID fonts do not support ligatures, so this always returns {@code -1}.
	 *
	 * @param gid the glyph ID of the first glyph
	 * @param c   the character code of the following character
	 * @return always {@code -1}
	 */
	public int getLigature(int gid, int c) {
		return -1;
	}

	public String toString() {
		return super.toString() + ":[PDFName=" + this.getName() + " source=" + this.getFontSource() + "]";
	}
}
