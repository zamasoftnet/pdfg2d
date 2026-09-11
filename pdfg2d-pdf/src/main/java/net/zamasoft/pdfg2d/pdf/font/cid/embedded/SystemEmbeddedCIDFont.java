package net.zamasoft.pdfg2d.pdf.font.cid.embedded;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.PDFEmbeddedFont;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDFont;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDUtils;
import net.zamasoft.pdfg2d.pdf.font.util.PDFFontUtils;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.pdfg2d.util.ShortList;

class SystemEmbeddedCIDFont extends CIDFont implements PDFEmbeddedFont, ShapedFont {
	private static final long serialVersionUID = 0L;

	protected final ShortList advances = new ShortList(Short.MIN_VALUE);

	protected IntList gids = new IntList(-1);

	protected IntList unicodes = new IntList();

	protected final List<Shape> shapes = new ArrayList<Shape>();

	public SystemEmbeddedCIDFont(SystemEmbeddedCIDFontSource source, String name, ObjectRef fontRef) {
		super(source, name, fontRef);

		int[] cida = new int[] { source.getAwtFont().getMissingGlyphCode() };
		GlyphVector gv = source.getAwtFont().createGlyphVector(source.getFontRenderContext(), cida);
		this.advances.set(0, (short) gv.getGlyphMetrics(0).getAdvance());
		this.shapes.add(gv.getGlyphOutline(0));
	}

	private final char[] chara = new char[1];

	public int toGID(int c) {
		int gid;
		SystemEmbeddedCIDFontSource metaFont = (SystemEmbeddedCIDFontSource) this.source;
		if (metaFont.getAwtFont().canDisplay((char) c)) {
			gid = this.gids.get(c);
			if (gid == -1) {
				this.chara[0] = (char) c;
				gid = this.shapes.size();
				this.gids.set(c, gid);
				this.unicodes.set(gid, c);
				GlyphVector gv = metaFont.getAwtFont().createGlyphVector(metaFont.getFontRenderContext(), this.chara);
				short advance = (short) gv.getGlyphMetrics(0).getAdvance();
				Shape shape = gv.getGlyphOutline(0);
				this.shapes.add(shape);

				this.advances.set(gid, advance);
			}
		} else {
			gid = 0;
		}
		return gid;
	}

	public short getAdvance(int gid) {
		return this.advances.get(gid);
	}

	public short getWidth(int gid) {
		return this.getAdvance(gid);
	}

	public void drawTo(GC gc, Text text) throws IOException, GraphicsException {
		if (gc instanceof PDFGC) {
			PDFFontUtils.drawCIDTo(((PDFGC) gc).getPDFGraphicsOutput(), text, false);
		} else {
			SystemEmbeddedCIDFontSource source = (SystemEmbeddedCIDFontSource) this.getFontSource();
			PDFFontUtils.drawAwtFont(gc, source, source.getAwtFont(), text);
		}
	}

	public void writeTo(PDFFragmentOutput out, XRef xref) throws IOException {
		SystemEmbeddedCIDFontSource metaFont = (SystemEmbeddedCIDFontSource) this.source;
		int[] unicodea = this.unicodes.toArray();
		this.unicodes = null;
		CIDUtils.writeEmbeddedFont(out, xref, metaFont, this, this.fontRef, this.advances.toArray(), null, null, unicodea);
	}

	public BBox getBBox() {
		SystemEmbeddedCIDFontSource metaFont = (SystemEmbeddedCIDFontSource) this.source;
		return metaFont.getBBox();
	}

	public int getGlyphCount() {
		return this.shapes.size();
	}

	public int getCharCount() {
		return this.shapes.size();
	}

	public String getOrdering() {
		return CIDUtils.ORDERING;
	}

	public String getRegistry() {
		return CIDUtils.REGISTRY;
	}

	public Shape getShape(int gid) {
		return (Shape) this.shapes.get(gid);
	}

	public byte[] getCharString(int i) {
		return null;
	}

	public Shape getShapeByGID(int gid) {
		return this.getShape(gid);
	}

	public int getSupplement() {
		return CIDUtils.SUPPLEMENT;
	}

	public String getPSName() {
		SystemEmbeddedCIDFontSource metaFont = (SystemEmbeddedCIDFontSource) this.source;
		Font awtFont = metaFont.getAwtFont();
		return awtFont.getPSName();
	}
}
