package net.zamasoft.pdfg2d.pdf.font.cid.keyed;

import java.awt.Font;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.font.AbstractFontSource;
import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.Panose;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.CMap;
import net.zamasoft.pdfg2d.pdf.font.cid.WArray;
import net.zamasoft.pdfg2d.pdf.font.util.PDFFontUtils;

/**
 * Font source for a CID-keyed font (PDF Type 0 / CIDFontType2).  The glyph
 * outlines are not embedded; instead the font is referenced by name and the
 * appearance may vary depending on whether the system has the corresponding
 * font installed.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CIDKeyedFontSource extends AbstractFontSource implements CIDFontSource {
	private static final Logger LOG = Logger.getLogger(CIDKeyedFontSource.class.getName());

	private static final long serialVersionUID = 1L;

	protected final CMap hcmap, vcmap;

	protected String fontName;

	protected BBox bbox;

	protected short ascent, descent, capHeight, xHeight, stemH, stemV, spaceAdvance;

	protected WArray warray;

	protected Panose panose;

	transient protected Font awtFont = null;

	/**
	 * Constructs a CID-keyed font source with horizontal and optional vertical CMaps.
	 *
	 * @param hcmap the horizontal CMap (must not be {@code null})
	 * @param vcmap the vertical CMap, or {@code null} for horizontal-only fonts
	 */
	public CIDKeyedFontSource(CMap hcmap, CMap vcmap) {
		if (hcmap == null) {
			throw new NullPointerException();
		}
		this.hcmap = hcmap;
		this.vcmap = vcmap;
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("new font: " + this.getFontName());
		}
	}

	/**
	 * Returns the writing direction supported by this font source.
	 * Returns {@link Direction#TB} when a vertical CMap is present, otherwise
	 * {@link Direction#LTR}.
	 *
	 * @return the primary writing direction
	 */
	public Direction getDirection() {
		return this.vcmap == null ? Direction.LTR : Direction.TB;
	}

	/**
	 * Returns the PostScript/PDF name of this font.
	 *
	 * @return the font name
	 */
	public String getFontName() {
		return this.fontName;
	}

	/**
	 * Returns the font bounding box in 1/1000 em units.
	 *
	 * @return the font bounding box
	 */
	public BBox getBBox() {
		return this.bbox;
	}

	/**
	 * Returns the typographic ascent in 1/1000 em units.
	 *
	 * @return the ascent value
	 */
	public short getAscent() {
		return this.ascent;
	}

	/**
	 * Returns the typographic descent (positive value) in 1/1000 em units.
	 *
	 * @return the descent value
	 */
	public short getDescent() {
		return this.descent;
	}

	/**
	 * Returns the cap-height (height of uppercase 'H') in 1/1000 em units.
	 *
	 * @return the cap-height value
	 */
	public short getCapHeight() {
		return this.capHeight;
	}

	/**
	 * Returns the x-height (height of lowercase 'x') in 1/1000 em units.
	 *
	 * @return the x-height value
	 */
	public short getXHeight() {
		return this.xHeight;
	}

	/**
	 * Returns the dominant horizontal stem width in 1/1000 em units.
	 *
	 * @return the stem-H value
	 */
	public short getStemH() {
		return this.stemH;
	}

	/**
	 * Returns the dominant vertical stem width in 1/1000 em units.
	 *
	 * @return the stem-V value
	 */
	public short getStemV() {
		return this.stemV;
	}

	/**
	 * Returns the glyph-width array for this font.
	 *
	 * @return the {@link WArray} containing per-CID glyph widths
	 */
	public WArray getWArray() {
		return this.warray;
	}

	/**
	 * Returns the advance width of the space character in 1/1000 em units.
	 *
	 * @return the space advance width
	 */
	public short getSpaceAdvance() {
		return this.spaceAdvance;
	}

	/**
	 * Overrides the font name used in PDF output.
	 *
	 * @param fontName the new font name
	 */
	public void setFontName(String fontName) {
		this.fontName = fontName;
	}

	/**
	 * Sets the Panose classification data for this font.
	 *
	 * @param panose the Panose data, or {@code null} to clear it
	 */
	public void setPanose(Panose panose) {
		this.panose = panose;
	}

	/**
	 * Sets the font bounding box.
	 *
	 * @param bbox the font bounding box in 1/1000 em units
	 */
	public void setBBox(BBox bbox) {
		this.bbox = bbox;
	}

	/**
	 * Sets the typographic ascent.
	 *
	 * @param ascent the ascent value in 1/1000 em units
	 */
	public void setAscent(short ascent) {
		this.ascent = ascent;
	}

	/**
	 * Sets the typographic descent (positive value).
	 *
	 * @param descent the descent value in 1/1000 em units
	 */
	public void setDescent(short descent) {
		this.descent = descent;
	}

	/**
	 * Sets the cap-height value.
	 *
	 * @param capHeight the cap-height in 1/1000 em units
	 */
	public void setCapHeight(short capHeight) {
		this.capHeight = capHeight;
	}

	/**
	 * Sets the x-height value.
	 *
	 * @param xHeight the x-height in 1/1000 em units
	 */
	public void setXHeight(short xHeight) {
		this.xHeight = xHeight;
	}

	/**
	 * Sets the glyph-width array and derives the space advance from it.
	 *
	 * @param warray the new width array (must not be {@code null})
	 */
	public void setWArray(WArray warray) {
		if (warray == null) {
			throw new NullPointerException();
		}
		this.warray = warray;
		this.spaceAdvance = warray.getWidth(this.hcmap.getCIDTable().toCID(' '));
	}

	/**
	 * Sets the dominant horizontal stem width.
	 *
	 * @param stemH the stem-H value in 1/1000 em units
	 */
	public void setStemH(short stemH) {
		this.stemH = stemH;
	}

	/**
	 * Sets the dominant vertical stem width.
	 *
	 * @param stemV the stem-V value in 1/1000 em units
	 */
	public void setStemV(short stemV) {
		this.stemV = stemV;
	}

	protected synchronized Font getAwtFont() {
		if (this.awtFont == null) {
			this.awtFont = PDFFontUtils.toAwtFont(this);
		}
		return this.awtFont;
	}

	/**
	 * Returns {@link Type#CID_KEYED}, indicating that this font uses a keyed CMap.
	 *
	 * @return {@link Type#CID_KEYED}
	 */
	public Type getType() {
		return Type.CID_KEYED;
	}

	/**
	 * Returns {@code true} if this font can display the given character code.
	 *
	 * @param c the Unicode character code point
	 * @return {@code true} if the character is present in the horizontal CMap
	 */
	public boolean canDisplay(int c) {
		return this.hcmap.getCIDTable().containsChar(c);
	}

	/**
	 * Returns the Panose classification data for this font, or {@code null} if none
	 * is available.
	 *
	 * @return the Panose data, or {@code null}
	 */
	public Panose getPanose() {
		return this.panose;
	}

	/**
	 * Creates a new {@link PDFFont} instance for this CID-keyed font.
	 *
	 * @param name    the internal PDF resource name for the font
	 * @param fontRef the indirect object reference allocated for this font
	 * @return a new font instance bound to the given reference
	 */
	public PDFFont createFont(String name, ObjectRef fontRef) {
		switch (this.getDirection()) {
			case LTR:
			case RTL:// TODO RTL
				// Horizontal writing
				return new CIDKeyedFont(this, name, fontRef, this.hcmap);
			case TB:
				// Vertical writing
				return new CIDKeyedFont(this, name, fontRef, this.vcmap);
			default:
				throw new IllegalArgumentException();
		}
	}

	/**
	 * Creates a new font instance without binding it to a PDF object reference.
	 * Equivalent to {@code createFont(null, null)}.
	 *
	 * @return a new font instance
	 */
	public net.zamasoft.pdfg2d.font.Font createFont() {
		return this.createFont(null, null);
	}
}
