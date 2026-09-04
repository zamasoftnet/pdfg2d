package net.zamasoft.pdfg2d.pdf.font.cid.identity;

import java.awt.Shape;
import java.io.IOException;

import net.zamasoft.pdfg2d.font.Glyph;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFont;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDUtils;
import net.zamasoft.pdfg2d.pdf.font.util.PDFFontUtils;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.pdfg2d.util.ShortList;

class OpenTypeCIDIdentityFont extends OpenTypeFont implements PDFFont {
	private static final long serialVersionUID = 0L;

	protected final ObjectRef fontRef;

	protected final String name;

	protected final ShortList widths = new ShortList(Short.MIN_VALUE);

	protected final ShortList heights;

	protected final IntList unicodes = new IntList();

	protected OpenTypeCIDIdentityFont(OpenTypeCIDIdentityFontSource metaFont, String name, ObjectRef fontRef) {
		super(metaFont);
		this.fontRef = fontRef;
		this.name = name;
		if (this.isVertical()) {
			this.heights = new ShortList(Short.MIN_VALUE);
		} else {
			this.heights = null;
		}
	}

	public String getName() {
		return this.name;
	}

	public int toGID(int c) {
		OpenTypeCIDIdentityFontSource source = (OpenTypeCIDIdentityFontSource) this.getFontSource();
		int gid = source.getCmapFormat().mapCharCode(c);
		gid = this.substituteVertical(c, gid);
		// Kerning is queried during layout, before drawTo() records the glyphs used
		// by the page.  Keep the reverse mapping available at shaping time as well.
		if (gid != 0) {
			this.unicodes.set(gid, c);
		}
		return gid;
	}

	/**
	 * Identity fonts cannot allocate a semantic alias because CID equals the
	 * displayed GID. The display glyph is returned unchanged and the existing
	 * ToUnicode behaviour is retained. Such fonts therefore rely on
	 * {@code ActualText} for bidirectional mirroring; embedded subsets are
	 * recommended when robust bidi extraction is required.
	 */
	@Override
	public int toGID(final int displayCodePoint, final int logicalCodePoint,
			final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		final int gid = super.toGID(displayCodePoint, features);
		if (gid != 0) {
			this.unicodes.set(gid, displayCodePoint);
		}
		return gid;
	}

	protected int toChar(int gid) {
		return this.unicodes.get(gid);
	}

	public Shape getShapeByGID(int gid) {
		OpenTypeFontSource source = (OpenTypeFontSource) this.getFontSource();
		Glyph glyph = source.getOpenTypeFont().getGlyph(gid);
		if (glyph == null) {
			return null;
		}
		Shape shape = glyph.path();
		if (shape == null) {
			return null;
		}
		shape = this.adjustShape(shape, gid);
		return shape;
	}

	public short getAdvance(int gid) {
		if (this.isVertical()) {
			return this.getVAdvance(gid);
		}
		return this.getHAdvance(gid);
	}

	public short getWidth(int gid) {
		return this.getHAdvance(gid);
	}

	public void drawTo(GC gc, Text text) throws IOException, GraphicsException {
		if (gc instanceof PDFGC) {
			PDFFontUtils.drawCIDTo(((PDFGC) gc).getPDFGraphicsOutput(), text, this.isVertical());
		} else {
			FontUtils.drawText(gc, this, text);
		}

		int glyphCount = text.getGlyphCount();
		int[] glyphIds = text.getGlyphIds();
		char[] chars = text.getChars();
		for (int i = 0; i < glyphCount; ++i) {
			this.widths.set(glyphIds[i], this.getHAdvance(glyphIds[i]));
			if (this.isVertical()) {
				this.heights.set(glyphIds[i], this.getVAdvance(glyphIds[i]));
			}
			this.unicodes.set(glyphIds[i], chars[i]);
		}
	}

	public void writeTo(PDFFragmentOutput out, XRef xref) throws IOException {
		OpenTypeCIDIdentityFontSource source = (OpenTypeCIDIdentityFontSource) this.getFontSource();
		short[] w = this.widths.toArray();
		short[] w2;
		if (this.isVertical()) {
			w2 = this.heights.toArray();
		} else {
			w2 = null;
		}
		CIDUtils.writeIdentityFont(out, xref, source, this.fontRef, w, w2, this.unicodes.toArray());
	}

	public short getKerning(int scid, int cid) {
		// Identity CID fonts historically do not apply general GPOS kerning here;
		// keep that contract and add only the vertical dash continuity fix.
		return this.verticalDashKerning(scid, cid);
	}

	public int getLigature(int gid, int cid) {
		return -1;
	}

	@Override
	public int getLigature(int gid, int cid, net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		return -1;
	}
}
