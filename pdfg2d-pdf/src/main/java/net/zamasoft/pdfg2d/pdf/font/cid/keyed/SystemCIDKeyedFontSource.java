package net.zamasoft.pdfg2d.pdf.font.cid.keyed;

import java.awt.Font;

import net.zamasoft.pdfg2d.pdf.font.cid.CIDTable;
import net.zamasoft.pdfg2d.pdf.font.cid.CMap;
import net.zamasoft.pdfg2d.pdf.font.cid.WArray;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.SystemCIDIdentityFontSource;
import net.zamasoft.pdfg2d.util.ArrayShortMapIterator;
import net.zamasoft.pdfg2d.util.IntMapIterator;
import net.zamasoft.pdfg2d.util.ShortList;

/**
 * A CID-keyed font source backed by a system (AWT) font.  Glyph widths are
 * obtained from the AWT font's glyph metrics.  The typeface appearance varies
 * depending on the fonts installed on the platform.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class SystemCIDKeyedFontSource extends CIDKeyedFontSource {
	private static final long serialVersionUID = 0L;

	protected final Font awtFont;

	/**
	 * Constructs a system-font-backed CID-keyed font source.
	 *
	 * @param hcmap   the horizontal CMap
	 * @param vcmap   the vertical CMap, or {@code null} for horizontal-only fonts
	 * @param awtFont the AWT font to use for metrics and rendering
	 */
	public SystemCIDKeyedFontSource(CMap hcmap, CMap vcmap, Font awtFont) {
		super(hcmap, vcmap);
		this.awtFont = awtFont = awtFont.deriveFont(1000f);
		SystemCIDIdentityFontSource fs = new SystemCIDIdentityFontSource(awtFont);
		this.fontName = fs.getFontName();
		this.aliases = fs.getAliases();
		this.bbox = fs.getBBox();
		this.ascent = fs.getAscent();
		this.descent = fs.getDescent();
		this.capHeight = fs.getCapHeight();
		this.xHeight = fs.getXHeight();
		this.panose = fs.getPanose();
	}

	/**
	 * Returns the AWT font used for glyph metric computation and fallback rendering.
	 *
	 * @return the underlying AWT {@link Font}
	 */
	public Font getAwtFont() {
		return this.awtFont;
	}

	/**
	 * Returns the glyph-width array, building it lazily from the AWT font's glyph
	 * metrics the first time it is called.
	 *
	 * @return the {@link WArray} containing per-CID advance widths
	 */
	public WArray getWArray() {
		if (this.warray == null) {
			SystemCIDIdentityFontSource fs = new SystemCIDIdentityFontSource(this.awtFont);
			this.setWArray(systemWArray(fs, this.hcmap));
		}
		return this.warray;
	}

	private static WArray systemWArray(SystemCIDIdentityFontSource fs, CMap cmap) {
		ShortList cidToAdvance = new ShortList(Short.MIN_VALUE);
		CIDTable ct = cmap.getCIDTable();
		IntMapIterator i = ct.getIterator();
		while (i.next()) {
			int cid = i.value();
			int gid = fs.toGID(i.key());
			short advance = (short) fs.getWidth(gid);
			cidToAdvance.set(cid, advance);
		}
		short[] widths = cidToAdvance.toArray();
		WArray warray = WArray.buildFromWidths(new ArrayShortMapIterator(widths));
		return warray;
	}
}
