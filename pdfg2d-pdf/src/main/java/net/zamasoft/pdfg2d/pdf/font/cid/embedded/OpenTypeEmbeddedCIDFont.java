package net.zamasoft.pdfg2d.pdf.font.cid.embedded;

import java.awt.Shape;
import java.io.IOException;

import net.zamasoft.pdfg2d.font.Glyph;
import net.zamasoft.pdfg2d.font.table.UvsCmapFormat;
import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFont;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.PDFEmbeddedFont;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDUtils;
import net.zamasoft.pdfg2d.pdf.font.util.PDFFontUtils;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.pdfg2d.util.ShortList;

/**
 * A PDF CID font instance that is built from an OpenType font file and
 * embedded as a subset in the PDF output.  Each character code is mapped to
 * an internal glyph identifier (GID) that is independent of the original font
 * GID, so that the subset can be re-numbered compactly.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class OpenTypeEmbeddedCIDFont extends OpenTypeFont implements PDFEmbeddedFont {
	private static final long serialVersionUID = 0L;

	protected final ObjectRef fontRef;

	protected final String name;

	protected final ShortList widths = new ShortList(Short.MIN_VALUE), heights = new ShortList(Short.MIN_VALUE);

	protected IntList fgidToGid = new IntList(-1);

	protected IntList gidToFgid = new IntList(-1);

	protected IntList gidToCid = new IntList();

	protected int glyphCount = 1;

	/**
	 * Constructs a new embedded CID font instance.
	 *
	 * @param source  the font source that supplies the underlying OpenType data
	 * @param name    the internal PDF resource name for this font
	 * @param fontRef the indirect object reference for the font dictionary
	 */
	protected OpenTypeEmbeddedCIDFont(OpenTypeEmbeddedCIDFontSource source, String name, ObjectRef fontRef) {
		super(source);
		this.fontRef = fontRef;
		this.name = name;
		this.widths.set(0, (short) this.getHAdvance(0));
		this.heights.set(0, (short) this.getVAdvance(0));
	}

	public String getName() {
		return this.name;
	}

	public int toGID(int c) {
		OpenTypeEmbeddedCIDFontSource source = (OpenTypeEmbeddedCIDFontSource) this.getFontSource();
		int fgid = source.getCmapFormat().mapCharCode(c);
		return this.addGID(c, fgid);
	}

	private int addGID(int c, int fgid) {
		if (fgid == 0) {
			return 0;
		}
		if (this.vSubst != null) {
			fgid = this.vSubst.substitute(fgid);
		}
		int gid = this.fgidToGid.get(fgid);
		if (gid == -1) {
			gid = this.glyphCount++;
			this.fgidToGid.set(fgid, gid);
			this.gidToFgid.set(gid, fgid);
			this.gidToCid.set(gid, c);
			this.widths.set(gid, this.getHAdvance(fgid));
			this.heights.set(gid, this.getVAdvance(fgid));
		}
		return gid;
	}

	public int getLigature(int gid, int cid) {
		if (gid == -1) {
			return -1;
		}
		UvsCmapFormat ucf = this.source.getUvsCmapFormat();
		if (ucf == null || !ucf.isVarSelector(cid)) {
			return -1;
		}
		int c = this.gidToCid.get(gid);
		int fgid = ucf.mapCharCode(c, cid);
		if (fgid == 0) {
			return -1;
		}
		return this.addGID(c, fgid);
	}

	protected int toChar(int gid) {
		return this.gidToCid.get(gid);
	}

	public Shape getShapeByGID(int gid) {
		int fgid = this.gidToFgid.get(gid);
		if (fgid == -1) {
			return null;
		}
		Glyph glyph = this.source.getOpenTypeFont().getGlyph(fgid);
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
			return this.heights.get(gid);
		}
		return this.widths.get(gid);
	}

	public short getWidth(int gid) {
		return this.widths.get(gid);
	}

	public void drawTo(GC gc, Text text) throws IOException, GraphicsException {
		if (gc instanceof PDFGC) {
			PDFFontUtils.drawCIDTo(((PDFGC) gc).getPDFGraphicsOutput(), text, this.vSubst != null);
		} else {
			FontUtils.drawText(gc, this, text);
		}
	}

	public void writeTo(PDFFragmentOutput out, XRef xref) throws IOException {
		OpenTypeEmbeddedCIDFontSource source = (OpenTypeEmbeddedCIDFontSource) this.getFontSource();
		final int[] unicodea = this.gidToCid.toArray();
		final short[] w = this.widths.toArray();
		final short[] w2;
		if (this.vSubst != null) {
			w2 = this.heights.toArray();
		} else {
			w2 = null;
		}

		CIDUtils.writeEmbeddedFont(out, xref, source, this, this.fontRef, w, w2, unicodea);
		this.gidToCid = null;
	}

	public BBox getBBox() {
		OpenTypeEmbeddedCIDFontSource source = (OpenTypeEmbeddedCIDFontSource) this.getFontSource();
		return source.getBBox();
	}

	public int getGlyphCount() {
		return this.glyphCount;
	}

	public int getCharCount() {
		return this.glyphCount;
	}

	public String getOrdering() {
		return CIDUtils.ORDERING;
	}

	public String getRegistry() {
		return CIDUtils.REGISTRY;
	}

	public Shape getShape(int gid) {
		return this.getShapeByGID(gid);
	}

	public byte[] getCharString(int gid) {
		return null;
//		Glyph glyph = this.source.getOpenTypeFont().getGlyph(this.toSourceGID(gid));
//		if (glyph == null) {
//			return null;
//		}
//		return glyph.getCharString();
	}

	public int getSupplement() {
		return CIDUtils.SUPPLEMENT;
	}

	public String getPSName() {
		OpenTypeEmbeddedCIDFontSource metaFont = (OpenTypeEmbeddedCIDFontSource) this.getFontSource();
		return metaFont.getFontName();
	}
}


