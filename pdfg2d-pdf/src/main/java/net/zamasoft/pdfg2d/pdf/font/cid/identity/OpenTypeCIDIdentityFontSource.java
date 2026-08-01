package net.zamasoft.pdfg2d.pdf.font.cid.identity;

import java.io.File;
import java.io.IOException;

import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDFontSource;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: SystemExternalCIDFontFace.java,v 1.2 2005/06/10 12:46:30
 *          harumanx Exp $
 */
public class OpenTypeCIDIdentityFontSource extends OpenTypeFontSource implements CIDFontSource {
	private static final long serialVersionUID = 1L;

	public OpenTypeCIDIdentityFontSource(File ttfFont, int index, Direction direction) throws IOException {
		super(ttfFont, index, direction);
	}

	/**
	 * 永続フォント索引からの再構築です(2026-08-01、FontIndex参照)。
	 * ファイルI/Oを行わない。
	 */
	public OpenTypeCIDIdentityFontSource(final File file, final int index, final Direction direction, final short upm,
			final net.zamasoft.pdfg2d.font.BBox bbox, final String fontName, final String[] aliases,
			final boolean italic, final net.zamasoft.pdfg2d.gc.font.FontStyle.Weight weight,
			final net.zamasoft.pdfg2d.gc.font.Panose panose, final short ascent, final short descent,
			final short spaceAdvance, final net.zamasoft.pdfg2d.font.table.GenericCmapFormat cmap,
			final net.zamasoft.pdfg2d.font.table.UvsCmapFormat uvsCmap) {
		super(file, index, direction, upm, bbox, fontName, aliases, italic, weight, panose, ascent, descent,
				spaceAdvance, cmap, uvsCmap);
	}

	public Type getType() {
		return Type.CID_IDENTITY;
	}

	public PDFFont createFont(String name, ObjectRef fontRef) {
		return new OpenTypeCIDIdentityFont(this, name, fontRef);
	}

	public Font createFont() {
		return this.createFont(null, null);
	}
}
