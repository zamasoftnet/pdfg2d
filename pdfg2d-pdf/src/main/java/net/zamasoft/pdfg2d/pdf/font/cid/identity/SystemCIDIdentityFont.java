package net.zamasoft.pdfg2d.pdf.font.cid.identity;

import java.io.IOException;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDFont;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDUtils;
import net.zamasoft.pdfg2d.pdf.font.util.PDFFontUtils;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.pdfg2d.util.ShortList;

class SystemCIDIdentityFont extends CIDFont {
	private static final long serialVersionUID = 0L;

	protected final ShortList advances = new ShortList(Short.MIN_VALUE);

	protected final IntList unicodes = new IntList();

	protected SystemCIDIdentityFont(SystemCIDIdentityFontSource source, String name, ObjectRef fontRef) {
		super(source, name, fontRef);
	}

	public int toGID(int c) {
		SystemCIDIdentityFontSource source = (SystemCIDIdentityFontSource) this.source;
		int gid = source.toGID(c);
		return gid;
	}

	public short getAdvance(int cid) {
		SystemCIDIdentityFontSource source = (SystemCIDIdentityFontSource) this.source;
		return source.getWidth(cid);
	}

	public short getWidth(int gid) {
		return this.getAdvance(gid);
	}

	public void drawTo(GC gc, Text text) throws IOException, GraphicsException {
		if (gc instanceof PDFGC) {
			PDFFontUtils.drawCIDTo(((PDFGC) gc).getPDFGraphicsOutput(), text, false);
		} else {
			SystemCIDIdentityFontSource source = (SystemCIDIdentityFontSource) this.getFontSource();
			PDFFontUtils.drawAwtFont(gc, source, source.getAwtFont(), text);
		}

		int glyphCount = text.getGlyphCount();
		int[] glyphIds = text.getGlyphIds();
		char[] chars = text.getChars();
		for (int i = 0; i < glyphCount; ++i) {
			int gid = glyphIds[i];
			short nadvance = this.getAdvance(gid);
			this.advances.set(gid, nadvance);
			this.unicodes.set(gid, chars[i]);
		}
	}

	public void writeTo(PDFFragmentOutput out, XRef xref) throws IOException {
		SystemCIDIdentityFontSource source = (SystemCIDIdentityFontSource) this.source;
		CIDUtils.writeIdentityFont(out, xref, source, this.fontRef, this.advances.toArray(), null, null,
				this.unicodes.toArray());
	}
}
