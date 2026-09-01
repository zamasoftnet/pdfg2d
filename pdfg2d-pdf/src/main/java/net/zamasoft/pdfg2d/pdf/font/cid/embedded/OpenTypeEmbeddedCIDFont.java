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

	private final OpenTypeEmbeddedCIDFontSubset subset;

	/** Direction-specific CID-to-Unicode map; gaps belong to the other wrapper. */
	private IntList gidToCid = new IntList(-1);

	/**
	 * Constructs a new embedded CID font instance.
	 *
	 * @param source  the font source that supplies the underlying OpenType data
	 * @param name    the internal PDF resource name for this font
	 * @param fontRef the indirect object reference for the font dictionary
	 */
	protected OpenTypeEmbeddedCIDFont(final OpenTypeEmbeddedCIDFontSource source, final String name,
			final ObjectRef fontRef, final OpenTypeEmbeddedCIDFontSubset subset) {
		super(source);
		this.fontRef = fontRef;
		this.name = name;
		this.subset = subset;
		this.subset.initialize(this.getHAdvance(0), this.getVAdvance(0), this.isVertical());
	}

	public String getName() {
		return this.name;
	}

	public int toGID(int c) {
		OpenTypeEmbeddedCIDFontSource source = (OpenTypeEmbeddedCIDFontSource) this.getFontSource();
		int fgid = source.getCmapFormat().mapCharCode(c);
		return this.addGID(c, fgid);
	}

	@Override
	public int toGID(int c, net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		OpenTypeEmbeddedCIDFontSource source = (OpenTypeEmbeddedCIDFontSource) this.getFontSource();
		int fgid = source.getCmapFormat().mapCharCode(c);
		// cmap後・subset登録前にfeature置換(jp78等)。addGIDが縦書き置換と
		// subset GIDの採番・width登録を行うため、置換後のfont GIDを渡せば
		// advanceも置換後グリフのものが自然に載る
		fgid = this.substituteFeatures(fgid, features);
		return this.addGID(c, fgid);
	}

	int addGID(final int c, int fgid) {
		if (fgid == 0) {
			return 0;
		}
		final int directVertical = this.substituteVertical(fgid);
		fgid = this.substituteVertical(c, fgid);
		final boolean emDashFallback = c == 0x2014 && directVertical != fgid;
		final int semanticVariant = emDashFallback ? 0x2014 : 0;
		final int gid = this.subset.register(fgid, this.verticalShapeFlags(c), semanticVariant,
				this.getHAdvance(fgid), this.getVAdvance(fgid), this.isVertical());
		if (this.gidToCid.get(gid) < 0) {
			this.gidToCid.set(gid, c);
		}
		return gid;
	}

	public int getLigature(int gid, int cid) {
		if (gid == -1) {
			return -1;
		}
		UvsCmapFormat ucf = this.source.getUvsCmapFormat();
		if (ucf != null && ucf.isVarSelector(cid)) {
			int c = this.gidToCid.get(gid);
			int fgid = ucf.mapCharCode(c, cid);
			if (fgid == 0) {
				return -1;
			}
			return this.addGID(c, fgid);
		}

		// GSUB standard ligatures: translate the subset glyph and the incoming
		// character into font glyph ids, look up the ligature, and register its
		// glyph in the subset. The ligature is keyed to the joining character
		// for a best-effort ToUnicode mapping.
		int firstFgid = this.subset.sourceGid(gid);
		if (firstFgid < 0) {
			return -1;
		}
		int secondFgid = this.source.getCmapFormat().mapCharCode(cid);
		if (secondFgid == 0) {
			return -1;
		}
		secondFgid = this.substituteVertical(cid, secondFgid);
		int ligFgid = this.gsubLigature(firstFgid, secondFgid);
		if (ligFgid <= 0) {
			return -1;
		}
		return this.addGID(cid, ligFgid);
	}

	@Override
	public boolean isColorGlyph(int gid) {
		int fgid = this.subset.sourceGid(gid);
		return fgid >= 0 && this.hasColorLayers(fgid);
	}

	@Override
	public void drawColorGlyph(GC gc, int gid, java.awt.geom.AffineTransform at) {
		int fgid = this.subset.sourceGid(gid);
		if (fgid >= 0) {
			this.drawColorLayers(gc, fgid, at);
		}
	}

	@Override
	public short getAdvanceAdjustment(int gid, net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		// Translate the subset glyph id back to a font glyph id for GPOS.
		int fgid = this.subset.sourceGid(gid);
		return fgid >= 0 ? super.getAdvanceAdjustment(fgid, features) : 0;
	}

	@Override
	public short getPlacementAdjustment(int gid, net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		// Translate the subset glyph id back to a font glyph id for GPOS.
		int fgid = this.subset.sourceGid(gid);
		return fgid >= 0 ? super.getPlacementAdjustment(fgid, features) : 0;
	}

	@Override
	public short getKerning(int sgid, int gid) {
		// Translate the subset glyph ids back to font glyph ids for GPOS.
		int f1 = this.subset.sourceGid(sgid);
		int f2 = this.subset.sourceGid(gid);
		if (f1 >= 0 && f2 >= 0) {
			short kern = this.gposKerning(f1, f2);
			if (kern != 0) {
				return kern;
			}
		}
		// サブセットGIDのままsuper(フォントGIDキーのGPOS対表)を引く
		// 旧フォールバックは禁止。サブセットの採番(使用順の小さな番号)が
		// フォントGIDの実在ペアと偶然一致し、フォントが定義していない
		// 字間調整が無関係な文字対に乗っていた(2026-08-27、Minion Proの
		// 「56」「78」等でPDF実出力から確認)
		return this.verticalDashKerning(sgid, gid);
	}

	protected int toChar(int gid) {
		return this.gidToCid.get(gid);
	}

	public Shape getShapeByGID(int gid) {
		int fgid = this.subset.sourceGid(gid);
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
		shape = this.adjustShape(shape, this.subset.shapeFlags(gid), this.subset.height(gid));
		return shape;
	}

	public short getAdvance(int gid) {
		if (this.isVertical()) {
			return this.subset.height(gid);
		}
		return this.subset.width(gid);
	}

	public short getWidth(int gid) {
		return this.subset.width(gid);
	}

	public void drawTo(GC gc, Text text) throws IOException, GraphicsException {
		if (gc instanceof PDFGC) {
			final var direction = ((OpenTypeEmbeddedCIDFontSource) this.getFontSource()).getDirection();
			PDFFontUtils.drawCIDTo(((PDFGC) gc).getPDFGraphicsOutput(), text,
					direction == net.zamasoft.pdfg2d.gc.font.FontStyle.Direction.TB);
		} else {
			FontUtils.drawText(gc, this, text);
		}
	}

	public void writeTo(PDFFragmentOutput out, XRef xref) throws IOException {
		OpenTypeEmbeddedCIDFontSource source = (OpenTypeEmbeddedCIDFontSource) this.getFontSource();
		this.subset.prepare(xref, this.getPSName());
		final boolean vertical = source.getDirection() == net.zamasoft.pdfg2d.gc.font.FontStyle.Direction.TB;
		CIDUtils.writeEmbeddedFontType0(out, xref, this.fontRef, this.subset.descendantRef(),
				this.subset.subsetName(), vertical, this.gidToCid.toArray());
		if (!this.subset.isWritten()) {
			CIDUtils.writeEmbeddedFontProgram(out, xref, source, this, this.subset.descendantRef(),
					this.subset.subsetName(), this.subset.widths(), this.subset.heights(), this.subset.signature());
			this.subset.markWritten();
		}
		this.gidToCid = null;
	}

	public BBox getBBox() {
		OpenTypeEmbeddedCIDFontSource source = (OpenTypeEmbeddedCIDFontSource) this.getFontSource();
		return source.getBBox();
	}

	public int getGlyphCount() {
		return this.subset.glyphCount();
	}

	public int getCharCount() {
		return this.subset.glyphCount();
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
		return metaFont.getPostScriptName();
	}
}
