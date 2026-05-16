package net.zamasoft.pdfg2d.pdf.font.cid.keyed;

import java.io.File;
import java.io.IOException;

import net.zamasoft.pdfg2d.font.OpenTypeFont;
import net.zamasoft.pdfg2d.font.table.Table;
import net.zamasoft.pdfg2d.font.table.XmtxTable;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDTable;
import net.zamasoft.pdfg2d.pdf.font.cid.CMap;
import net.zamasoft.pdfg2d.pdf.font.cid.WArray;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.OpenTypeCIDIdentityFontSource;
import net.zamasoft.pdfg2d.util.ArrayShortMapIterator;
import net.zamasoft.pdfg2d.util.IntMapIterator;
import net.zamasoft.pdfg2d.util.ShortList;

/**
 * A CID-keyed font source backed by an OpenType (TrueType/OTF) font file.
 * Glyph widths are derived from the OpenType {@code hmtx} table and mapped
 * through the CMap.  The font program itself is not embedded; the file is used
 * only to read metrics.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class OpenTypeCIDKeyedFontSource extends CIDKeyedFontSource {
	private static final long serialVersionUID = 1L;

	protected final File otFile;

	protected final int index;

	/**
	 * Constructs an OpenType-backed CID-keyed font source.
	 *
	 * @param hcmap  the horizontal CMap
	 * @param vcmap  the vertical CMap, or {@code null} for horizontal-only fonts
	 * @param otFile the OpenType font file (TTF/OTF/TTC)
	 * @param index  the zero-based font index within a TTC collection
	 * @throws IOException if the font file cannot be read
	 */
	public OpenTypeCIDKeyedFontSource(CMap hcmap, CMap vcmap, File otFile, int index) throws IOException {
		super(hcmap, vcmap);
		this.otFile = otFile;
		this.index = index;
		OpenTypeCIDIdentityFontSource fs = new OpenTypeCIDIdentityFontSource(this.otFile, this.index,
				this.getDirection());
		this.fontName = fs.getFontName();
		this.aliases = fs.getAliases();
		this.bbox = fs.getBBox();
		this.ascent = fs.getAscent();
		this.descent = fs.getDescent();
		this.capHeight = fs.getCapHeight();
		this.xHeight = fs.getXHeight();
		this.stemH = fs.getStemH();
		this.stemV = fs.getStemV();
		this.panose = fs.getPanose();
	}

	/**
	 * Returns the glyph-width array, building it lazily from the OpenType
	 * {@code hmtx} table the first time it is called.
	 *
	 * @return the {@link WArray} containing per-CID advance widths
	 */
	public WArray getWArray() {
		if (this.warray == null) {
			try {
				OpenTypeCIDIdentityFontSource fs = new OpenTypeCIDIdentityFontSource(this.otFile, this.index,
						this.getDirection());
				this.setWArray(otWArray(fs, this.hcmap));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return this.warray;
	}

	private static WArray otWArray(OpenTypeCIDIdentityFontSource fs, CMap cmap) {
		OpenTypeFont otFont = fs.getOpenTypeFont();
		XmtxTable hmtx = (XmtxTable) otFont.getTable(Table.HMTX);
		short upm = fs.getUnitsPerEm();

		ShortList cidToAdvance = new ShortList(Short.MIN_VALUE);
		CIDTable ct = cmap.getCIDTable();
		IntMapIterator i = ct.getIterator();
		while (i.next()) {
			int cid = i.value();
			int gid = fs.getCmapFormat().mapCharCode(i.key());
			short advance = (short) (hmtx.getAdvanceWidth(gid) * FontSource.DEFAULT_UNITS_PER_EM / upm);
			// CIDs may overlap, so use the wider width
			if (advance > cidToAdvance.get(cid)) {
				cidToAdvance.set(cid, advance);
			}
		}
		short[] widths = cidToAdvance.toArray();
		WArray warray = WArray.buildFromWidths(new ArrayShortMapIterator(widths));
		return warray;
	}
}
