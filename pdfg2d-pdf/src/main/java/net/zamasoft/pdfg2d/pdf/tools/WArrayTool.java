package net.zamasoft.pdfg2d.pdf.tools;

import java.io.File;

import net.zamasoft.zstream.resolver.protocol.file.FileSource;
import net.zamasoft.pdfg2d.font.OpenTypeFont;
import net.zamasoft.pdfg2d.font.table.Os2Table;
import net.zamasoft.pdfg2d.font.table.Table;
import net.zamasoft.pdfg2d.font.table.XmtxTable;
import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDTable;
import net.zamasoft.pdfg2d.pdf.font.cid.CMap;
import net.zamasoft.pdfg2d.pdf.font.cid.WArray;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.OpenTypeCIDIdentityFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.SystemCIDIdentityFontSource;
import net.zamasoft.pdfg2d.util.ArrayShortMapIterator;
import net.zamasoft.pdfg2d.util.IntMapIterator;
import net.zamasoft.pdfg2d.util.ShortList;

public class WArrayTool {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		// 0- cmap file
		// 1- java encoding (ie UTF-16BE)
		// 2- font
		// 3- ttc index

		if (args.length < 3) {
			System.out.println("WArrayTool cmap encoding font [ttc-index]");
			return;
		}
		String pCmap = args[0];
		String pEncoding = args[1];
		String pFont = args[2];
		int index = 0;
		if (args.length >= 4) {
			index = Integer.parseInt(args[3]);
		}
		boolean ttf = (pFont.toLowerCase().endsWith(".ttf") || pFont.toLowerCase().endsWith(".ttc"));

		File cmapFile = new File(pCmap);
		CMap cmap = new CMap(new FileSource(cmapFile), pEncoding);

		FontSource fs;

		if (ttf) {
			File ttFile = new File(pFont);
			fs = new OpenTypeCIDIdentityFontSource(ttFile, index, Direction.LTR);
		} else {
			java.awt.Font font = java.awt.Font.decode(pFont);
			fs = new SystemCIDIdentityFontSource(font);
		}

		BBox bbox = fs.getBBox();
		System.out.println("FontName: " + fs.getFontName());
		System.out.println("BBox: " + bbox.llx() + ' ' + bbox.lly() + ' ' + bbox.urx() + ' ' + bbox.ury());
		System.out.println("Ascent: " + fs.getAscent());
		System.out.println("Descent: " + fs.getDescent());
		System.out.println("CapHeight: " + fs.getCapHeight());
		System.out.println("XHeight: " + fs.getXHeight());

		WArray warray;
		if (ttf) {
			warray = otWArray((OpenTypeCIDIdentityFontSource) fs, cmap);
		} else {
			warray = systemWArray((SystemCIDIdentityFontSource) fs, cmap);
		}

		System.out.println("WArray:");
		System.out.println(warray.getWidths().length);
		System.out.println(warray);
	}

	private static WArray otWArray(OpenTypeCIDIdentityFontSource fs, CMap cmap) {
		OpenTypeFont ttfFont = fs.getOpenTypeFont();
		Os2Table os2 = (Os2Table) ttfFont.getTable(Table.OS_2);
		XmtxTable hmtx = (XmtxTable) ttfFont.getTable(Table.HMTX);
		System.out.println("FamilyClass: " + Integer.toHexString(os2.getFamilyClass()));
		System.out.println("PANOSE-1: " + os2.getPanose());
		short upm = fs.getUnitsPerEm();

		ShortList cidToAdvance = new ShortList(Short.MIN_VALUE);
		CIDTable ct = cmap.getCIDTable();
		IntMapIterator i = ct.getIterator();
		while (i.next()) {
			int gid = fs.getCmapFormat().mapCharCode(i.key());
			if (gid != 0) {
				int cid = i.value();
				short advance = (short) (hmtx.getAdvanceWidth(gid) * FontSource.DEFAULT_UNITS_PER_EM / upm);
				cidToAdvance.set(cid, advance);
			}
		}
		short[] widths = cidToAdvance.toArray();
		WArray warray = WArray.buildFromWidths(new ArrayShortMapIterator(widths));
		return warray;
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
