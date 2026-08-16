package net.zamasoft.pdfg2d.font.otf;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.pdfg2d.font.table.ColrTable;
import net.zamasoft.pdfg2d.font.table.CpalTable;
import net.zamasoft.pdfg2d.font.table.FeatureTags;
import net.zamasoft.pdfg2d.font.table.GposTable;
import net.zamasoft.pdfg2d.font.table.GsubTable;
import net.zamasoft.pdfg2d.font.table.PairPos;
import net.zamasoft.pdfg2d.font.table.ScriptTags;
import net.zamasoft.pdfg2d.font.table.SinglePos;
import net.zamasoft.pdfg2d.font.table.SingleSubst;
import net.zamasoft.pdfg2d.font.table.Table;
import net.zamasoft.pdfg2d.font.table.XmtxTable;
import net.zamasoft.pdfg2d.font.ColorGlyphFont;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * Abstract base class for an OpenType (TrueType/CFF) font.
 * <p>
 * Handles horizontal and optional vertical writing via GSUB {@code vert}
 * substitution, glyph advance computation, kerning for CJK punctuation, and
 * delegating text drawing to {@link FontUtils}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class OpenTypeFont implements ShapedFont, ColorGlyphFont {
	private static final long serialVersionUID = 2L;

	protected static final int DEFAULT_VERTICAL_ORIGIN = 880;

	protected static final boolean ADJUST_VERTICAL = false;

	protected final OpenTypeFontSource source;

	/** Required vertical substitutions (vrt2/vert), empty in horizontal mode. */
	protected final List<SingleSubst> vSubsts;

	protected final XmtxTable vmtx, hmtx;

	/**
	 * GSUB {@code liga} pairs: key {@code (firstGid << 32) | secondGid} to
	 * ligature glyph. boxed Mapからプリミティブ索引へ(2026-08-01、95点計画
	 * 増分3)——グリフ対毎に引かれる整形ホットパス。
	 */
	private final net.zamasoft.pdfg2d.util.LongIntLookup ligatures;

	/**
	 * Cache of composed GSUB single-substitution plans per feature set
	 * ({@code jp78}, {@code pwid}, ...). This font instance is shared per
	 * source across all styles ({@code DefaultFontStore}), so the cache is
	 * keyed by the immutable {@link net.zamasoft.pdfg2d.gc.font.FontFeatureSet}
	 * — never mutable "current feature" state. Lazily initialised; transient
	 * because plans are cheap to rebuild after deserialisation.
	 */
	private transient volatile java.util.concurrent.ConcurrentHashMap<net.zamasoft.pdfg2d.gc.font.FontFeatureSet, java.util.List<SingleSubst>> featurePlans;

	/**
	 * Cache of composed GPOS single-adjustment plans per feature set
	 * ({@code palt}, {@code vpal}, ...) — same sharing rules as
	 * {@link #featurePlans}.
	 */
	private transient volatile java.util.concurrent.ConcurrentHashMap<net.zamasoft.pdfg2d.gc.font.FontFeatureSet, java.util.List<SinglePos>> positionPlans;

	/** GPOS {@code kern} pair-adjustment subtables, or {@code null}. */
	private final List<PairPos> kernPairs;

	/** COLR color-layer table, or {@code null} for a monochrome font. */
	private final ColrTable colr;

	/** CPAL palette table, or {@code null}. */
	private final CpalTable cpal;

	/**
	 * Creates a new OpenTypeFont.
	 * 
	 * @param source the font source
	 */
	protected OpenTypeFont(final OpenTypeFontSource source) {
		this.source = source;
		final var ttfFont = source.getOpenTypeFont();
		this.hmtx = (XmtxTable) ttfFont.getTable(Table.HMTX);

		// GSUB standard ligatures (liga) and GPOS pair kerning (kern) are read
		// once here so that shaping can apply them per pair.
		this.ligatures = buildLigatures((GsubTable) ttfFont.getTable(Table.GSUB));
		final var gpos = (GposTable) ttfFont.getTable(Table.GPOS);
		final var kern = (gpos != null) ? gpos.collectKernPairPos() : List.<PairPos>of();
		this.kernPairs = kern.isEmpty() ? null : kern;

		// Color glyphs (COLR/CPAL) — present only in color fonts.
		this.colr = (ColrTable) ttfFont.getTable(Table.COLR);
		this.cpal = (this.colr != null) ? (CpalTable) ttfFont.getTable(Table.CPAL) : null;

		if (this.source.getDirection() == Direction.TB) {
			// Vertical writing mode. vrt2 (all-vertical alternates) supersedes
			// vert when the font has it; either way apply every matching
			// lookup's subtables, in lookup-list order — the former
			// first-lookup/first-subtable shortcut dropped substitutions in
			// fonts that split vert across subtables (unified 2026-07-31 with
			// the generic feature-plan collection; script/language filtering
			// matches liga/kern: none).
			final var gsub = (GsubTable) ttfFont.getTable(Table.GSUB);
			if (gsub != null) {
				var subs = gsub.collectSingleSubstitutions(TAG_VRT2);
				if (subs.isEmpty()) {
					subs = gsub.collectSingleSubstitutions(TAG_VERT);
				}
				if (!subs.isEmpty()) {
					this.vSubsts = subs;
					this.vmtx = (XmtxTable) ttfFont.getTable(Table.VMTX);
					return;
				}
			}
		}
		this.vSubsts = List.of();
		this.vmtx = null;
	}

	/** Packed {@code vrt2}/{@code vert} feature tags. */
	private static final int TAG_VRT2 = 0x76727432, TAG_VERT = 0x76657274;

	/**
	 * Applies the required vertical substitution ({@code vrt2}/{@code vert})
	 * to a font glyph id; identity in horizontal mode.
	 *
	 * @param gid the font glyph id after cmap (and optional feature GSUB)
	 * @return the (possibly substituted) font glyph id
	 */
	protected final int substituteVertical(int gid) {
		for (int i = 0; i < this.vSubsts.size(); ++i) {
			gid = this.vSubsts.get(i).substitute(gid);
		}
		return gid;
	}

	/**
	 * 必須縦字形を適用し、フォントがEM DASH(U+2014)の縦字形を持たない
	 * 場合だけHORIZONTAL BAR(U+2015)の縦字形を代用します。
	 *
	 * <p>Unicode Vertical_Orientationで両方とも縦組み時に回転対象ですが、
	 * 日本語フォントの一部はU+2015だけをvert/vrt2へ収録しています。
	 * PDFのCIDテキストは輪郭へ後処理の回転を掛けられないため、subset登録前の
	 * グリフ選択で補完します。ToUnicodeには元のU+2014を保持します。</p>
	 */
	protected final int substituteVertical(final int codePoint, final int gid) {
		final int vertical = this.substituteVertical(gid);
		if (!this.isVertical() || codePoint != 0x2014 || vertical != gid) {
			return vertical;
		}
		final var source = (OpenTypeFontSource) this.getFontSource();
		final int bar = source.getCmapFormat().mapCharCode(0x2015);
		if (bar == 0) {
			return vertical;
		}
		final int verticalBar = this.substituteVertical(bar);
		return verticalBar != bar ? verticalBar : vertical;
	}

	/**
	 * Returns whether this font is configured for vertical writing.
	 *
	 * @return {@code true} if vertical tables are present
	 */
	protected final boolean isVertical() {
		return this.vmtx != null;
	}

	/**
	 * Adjusts the glyph shape for vertical writing mode, including rotating
	 * certain fullwidth punctuation characters by 90 degrees.
	 *
	 * @param shape the original glyph outline
	 * @param gid   the glyph ID
	 * @return the (possibly modified) glyph outline
	 */
	protected final Shape adjustShape(Shape shape, final int gid) {
		return this.adjustShape(shape, gid, this.verticalShapeFlags(this.toChar(gid)));
	}

	/** Bit flag for the optional vertical-origin translation. */
	protected static final int VERTICAL_SHAPE_ADJUST = 1;

	/** Bit flag for glyphs whose outline is manually rotated in vertical text. */
	protected static final int VERTICAL_SHAPE_ROTATE = 2;

	/**
	 * Returns the outline transformation flags needed for one source character.
	 * Keeping this value with an embedded subset CID makes shared horizontal and
	 * vertical font programs independent of whichever Type0 wrapper is written
	 * first.
	 */
	protected final int verticalShapeFlags(final int cid) {
		if (!this.isVertical()) {
			return 0;
		}
		int flags = ADJUST_VERTICAL ? VERTICAL_SHAPE_ADJUST : 0;
		if (cid == 0xFF0D || cid == 0xFF1C || cid == 0xFF1E || cid == 0x2212 || cid == 0x226A
				|| cid == 0x226B) {
			flags |= VERTICAL_SHAPE_ROTATE;
		}
		return flags;
	}

	/** Applies an explicitly recorded vertical outline transformation. */
	protected final Shape adjustShape(Shape shape, final int sourceGid, final int flags) {
		return this.adjustShape(shape, flags, this.getVAdvance(sourceGid));
	}

	/** Applies recorded flags with the vertical metric held by a shared subset. */
	protected final Shape adjustShape(Shape shape, final int flags, final short verticalAdvance) {
		if (flags == 0) {
			return shape;
		}
		if ((flags & VERTICAL_SHAPE_ADJUST) != 0) {
			final double advance = verticalAdvance;
			final var bound = shape.getBounds2D();
			final double bottom = bound.getY() + bound.getHeight() + DEFAULT_VERTICAL_ORIGIN;
			if (bottom > advance) {
				// Adjust to avoid collision
				final var path = new GeneralPath(shape);
				path.transform(AffineTransform.getTranslateInstance(0, advance - bottom));
				shape = path;
			}
		}
		if ((flags & VERTICAL_SHAPE_ROTATE) != 0) {
			final var path = new GeneralPath(shape);
			final var bound = shape.getBounds2D();
			path.transform(AffineTransform.getRotateInstance(Math.PI / 2.0, bound.getCenterX(), bound.getCenterY()));
			shape = path;
		}
		return shape;
	}

	/**
	 * Returns the horizontal advance width of the glyph, normalised to
	 * {@link FontSource#DEFAULT_UNITS_PER_EM}.
	 *
	 * @param gid the glyph ID
	 * @return the horizontal advance width
	 */
	protected final short getHAdvance(final int gid) {
		final var source = (OpenTypeFontSource) this.getFontSource();
		return (short) (this.hmtx.getAdvanceWidth(gid) * FontSource.DEFAULT_UNITS_PER_EM / source.getUnitsPerEm());
	}

	/**
	 * Returns the vertical advance width of the glyph, normalised to
	 * {@link FontSource#DEFAULT_UNITS_PER_EM}. Falls back to
	 * {@code DEFAULT_UNITS_PER_EM} when no vmtx table is present.
	 *
	 * @param gid the glyph ID
	 * @return the vertical advance width
	 */
	protected final short getVAdvance(final int gid) {
		if (this.vmtx == null) {
			return FontSource.DEFAULT_UNITS_PER_EM;
		}
		final var source = (OpenTypeFontSource) this.getFontSource();
		return (short) (this.vmtx.getAdvanceWidth(gid) * FontSource.DEFAULT_UNITS_PER_EM / source.getUnitsPerEm());
	}

	@Override
	public FontSource getFontSource() {
		return this.source;
	}

	@Override
	public int toGID(final int c) {
		final var source = (OpenTypeFontSource) this.getFontSource();
		int gid = source.getCmapFormat().mapCharCode(c);
		return this.substituteVertical(c, gid);
	}

	@Override
	public int toGID(final int c, final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		final var source = (OpenTypeFontSource) this.getFontSource();
		int gid = source.getCmapFormat().mapCharCode(c);
		// cmap → 有効featureのGSUB単一置換 → 必須の縦書き置換(vert)。
		// エンジン必須のvertはCSSからは無効化させない(印刷エンジンとしての
		// 意図的仕様差——consult-codex-2026-07-31-font-features.txt §3.7)
		gid = this.substituteFeatures(gid, features);
		return this.substituteVertical(c, gid);
	}

	/**
	 * Applies the enabled features' GSUB single substitutions to a font glyph
	 * id (identity when the set is empty or the font has no matching lookups).
	 *
	 * @param gid      the font glyph id after cmap
	 * @param features the feature settings
	 * @return the (possibly substituted) font glyph id
	 */
	protected final int substituteFeatures(int gid, final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		if (gid == 0 || features.isEmpty()) {
			return gid;
		}
		var plans = this.featurePlans;
		if (plans == null) {
			synchronized (this) {
				plans = this.featurePlans;
				if (plans == null) {
					this.featurePlans = plans = new java.util.concurrent.ConcurrentHashMap<>();
				}
			}
		}
		final var plan = plans.computeIfAbsent(features, this::buildFeaturePlan);
		for (int i = 0; i < plan.size(); ++i) {
			gid = plan.get(i).substitute(gid);
		}
		return gid;
	}

	@Override
	public short getAdvanceAdjustment(final int gid, final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		if (gid == 0 || features.isEmpty()) {
			return 0;
		}
		final var plan = this.positionPlan(features);
		if (plan.isEmpty()) {
			return 0;
		}
		// 書字軸の成分だけを合算する。横書きにvpal(x成分ほぼ0)、縦書きに
		// palt(y成分ほぼ0)が指定されても自然に無効果になり、タグと方向の
		// 対応表は不要(consult-codex-2026-07-31-font-features.txt §3.7)
		final boolean vertical = this.isVertical();
		int adjustment = 0;
		for (int i = 0; i < plan.size(); ++i) {
			final var pos = plan.get(i).getPosition(gid);
			if (pos != null) {
				adjustment += vertical ? pos.yAdvance() : pos.xAdvance();
			}
		}
		return this.normalizeUnits(adjustment);
	}

	@Override
	public short getPlacementAdjustment(final int gid, final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		// 字面の視覚シフト(ペンは進めない)。縦書きのyPlacementはGPOSのy-up
		// 規約と描画座標の対応を実フォントで未検証のため未搬送(0)——
		// 横書きxPlacementのみ(増分⑤、consult-codex-2026-07-31-font-features
		// .txt §3.6。既知の限界として記録)
		if (gid == 0 || features.isEmpty() || this.isVertical()) {
			return 0;
		}
		final var plan = this.positionPlan(features);
		int adjustment = 0;
		for (int i = 0; i < plan.size(); ++i) {
			final var pos = plan.get(i).getPosition(gid);
			if (pos != null) {
				adjustment += pos.xPlacement();
			}
		}
		return this.normalizeUnits(adjustment);
	}

	/** hmtx幅(getHAdvance)と同じ基準へ正規化する(2048 UPMのTTF等)。 */
	private short normalizeUnits(final int value) {
		return (short) (value * FontSource.DEFAULT_UNITS_PER_EM
				/ ((OpenTypeFontSource) this.getFontSource()).getUnitsPerEm());
	}

	/** 有効featureのGPOS単一調整plan(キャッシュ、遅延構築)。 */
	private java.util.List<SinglePos> positionPlan(final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		var plans = this.positionPlans;
		if (plans == null) {
			synchronized (this) {
				plans = this.positionPlans;
				if (plans == null) {
					this.positionPlans = plans = new java.util.concurrent.ConcurrentHashMap<>();
				}
			}
		}
		return plans.computeIfAbsent(features, this::buildPositionPlan);
	}

	/**
	 * Composes the GPOS single-adjustment subtables of the enabled features.
	 * Like {@link #buildFeaturePlan}, lookup boundaries are flattened (each
	 * covering subtable contributes) — the fonts in practice carry one lookup
	 * per metrics feature.
	 */
	private java.util.List<SinglePos> buildPositionPlan(final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		final var gpos = (GposTable) this.source.getOpenTypeFont().getTable(Table.GPOS);
		if (gpos == null) {
			return java.util.List.of();
		}
		final var plan = new java.util.ArrayList<SinglePos>();
		for (int i = 0; i < features.size(); ++i) {
			if (features.valueAt(i) > 0) {
				plan.addAll(gpos.collectSinglePositions(features.tagAt(i)));
			}
		}
		return java.util.List.copyOf(plan);
	}

	/** Composes the GSUB single-substitution subtables of the enabled features. */
	private java.util.List<SingleSubst> buildFeaturePlan(final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		final var gsub = (GsubTable) this.source.getOpenTypeFont().getTable(Table.GSUB);
		if (gsub == null) {
			return java.util.List.of();
		}
		final var plan = new java.util.ArrayList<SingleSubst>();
		for (int i = 0; i < features.size(); ++i) {
			if (features.valueAt(i) > 0) {
				// GPOS系タグ(palt等)やliga(type 4)はtype 1 lookupを持たないため
				// ここでは自然に空になる(タグ種別の分岐は不要)
				plan.addAll(gsub.collectSingleSubstitutions(features.tagAt(i)));
			}
		}
		return java.util.List.copyOf(plan);
	}

	@Override
	public Shape getShapeByGID(final int gid) {
		final var source = (OpenTypeFontSource) this.getFontSource();
		final var glyph = source.getOpenTypeFont().getGlyph(gid);
		if (glyph == null) {
			return null;
		}
		Shape shape = glyph.path();
		shape = this.adjustShape(shape, gid);
		return shape;
	}

	@Override
	public short getAdvance(final int gid) {
		if (this.isVertical()) {
			return this.getVAdvance(gid);
		}
		return this.getHAdvance(gid);
	}

	@Override
	public short getWidth(final int gid) {
		return this.getHAdvance(gid);
	}

	@Override
	public void drawTo(final GC gc, final Text text) throws IOException, GraphicsException {
		FontUtils.drawText(gc, this, text);
	}

	/**
	 * Converts a glyph ID to a character code.
	 * 
	 * @param gid the glyph ID
	 * @return the character code
	 */
	protected abstract int toChar(int gid);

	@Override
	public short getKerning(final int sgid, final int gid) {
		// GPOS pair kerning (glyph ids are font glyph ids for the
		// non-embedding font; subclasses that remap glyphs translate before
		// calling gposKerning).
		//
		// Japanese punctuation trimming (CL01/CL02/CL06/07 pairs, formerly
		// hardcoded here) moved to the layout layer so it can be controlled
		// by CSS text-spacing-trim (foliojet4
		// consult-codex-2026-07-31-text-spacing.txt T1a). The layout layer
		// reproduces the same pair table via per-glyph advance adjustments.
		final short gpos = this.gposKerning(sgid, gid);
		return gpos != 0 ? gpos : this.verticalDashKerning(sgid, gid);
	}

	/**
	 * 縦組みで連続するEM DASH/HORIZONTAL BARの字面間の空きを詰めます。
	 * 固定値ではなく、実際の縦字形の輪郭端と縦advanceから求めるため、
	 * フォントごとのサイドベアリング差に追従します。
	 */
	protected final short verticalDashKerning(final int firstGid, final int secondGid) {
		if (!this.isVertical() || !isDash(this.toChar(firstGid)) || !isDash(this.toChar(secondGid))) {
			return 0;
		}
		final Shape first = this.getShapeByGID(firstGid);
		final Shape second = this.getShapeByGID(secondGid);
		if (first == null || second == null) {
			return 0;
		}
		final var source = (OpenTypeFontSource) this.getFontSource();
		final double scale = (double) FontSource.DEFAULT_UNITS_PER_EM / source.getUnitsPerEm();
		final var a = first.getBounds2D();
		final var b = second.getBounds2D();
		// 2字目の原点は1字目の縦advance後。字面が離れる量だけをkerning
		// (呼出側がadvanceから減算する正値)として返す。
		final double gap = this.getVAdvance(firstGid) + b.getMinY() * scale - a.getMaxY() * scale;
		if (!(gap > 0)) {
			return 0;
		}
		return (short) Math.min(Short.MAX_VALUE, Math.round(gap));
	}

	private static boolean isDash(final int codePoint) {
		return codePoint == 0x2014 || codePoint == 0x2015;
	}

	@Override
	public int getLigature(final int gid, final int cid) {
		if (gid < 0) {
			return -1;
		}
		return this.gsubLigature(gid, this.toGID(cid));
	}

	/**
	 * Returns the ligature glyph for a two-glyph GSUB {@code liga} pair, in
	 * font glyph-id space, or -1 if none.
	 *
	 * @param firstFontGid  the first component's font glyph id
	 * @param secondFontGid the second component's font glyph id
	 * @return the ligature's font glyph id, or -1
	 */
	protected final int gsubLigature(final int firstFontGid, final int secondFontGid) {
		if (this.ligatures == null) {
			return -1;
		}
		return this.ligatures.getOrDefault(((long) firstFontGid << 32) | (secondFontGid & 0xFFFFFFFFL), -1);
	}

	/**
	 * Returns the GPOS {@code kern} adjustment for a pair, in font glyph-id
	 * space, normalized to {@link FontSource#DEFAULT_UNITS_PER_EM} and negated
	 * to the "amount subtracted from the advance" convention of
	 * {@link #getKerning}.
	 *
	 * @param firstFontGid  the first glyph's font glyph id
	 * @param secondFontGid the second glyph's font glyph id
	 * @return the kerning to subtract, or 0
	 */
	protected final short gposKerning(final int firstFontGid, final int secondFontGid) {
		if (this.kernPairs == null) {
			return 0;
		}
		int xAdvance = 0;
		for (final var pp : this.kernPairs) {
			xAdvance += pp.getKerning(firstFontGid, secondFontGid);
		}
		if (xAdvance == 0) {
			return 0;
		}
		final var source = (OpenTypeFontSource) this.getFontSource();
		return (short) (-xAdvance * FontSource.DEFAULT_UNITS_PER_EM / source.getUnitsPerEm());
	}

	@Override
	public boolean isColorGlyph(final int gid) {
		return this.hasColorLayers(gid);
	}

	@Override
	public void drawColorGlyph(final GC gc, final int gid, final AffineTransform at) {
		this.drawColorLayers(gc, gid, at);
	}

	/**
	 * Returns whether the given font glyph id has COLR layers.
	 *
	 * @param fontGid the font glyph id
	 * @return {@code true} if it is a color glyph
	 */
	protected final boolean hasColorLayers(final int fontGid) {
		return this.colr != null && this.cpal != null && this.colr.getLayers(fontGid) != null;
	}

	/**
	 * Draws the COLR layers of the given font glyph id: each layer's outline
	 * filled with its CPAL color (palette entry {@code 0xFFFF} uses the current
	 * fill paint). Glyph outlines are taken directly from the underlying font,
	 * so this works regardless of glyph subsetting.
	 *
	 * @param gc      the graphics context
	 * @param fontGid the base color glyph's font glyph id
	 * @param at      the design-units-to-user transform
	 */
	protected final void drawColorLayers(final GC gc, final int fontGid, final AffineTransform at) {
		final var layers = this.colr.getLayers(fontGid);
		if (layers == null) {
			return;
		}
		final var ttf = ((OpenTypeFontSource) this.getFontSource()).getOpenTypeFont();
		final var savedFill = gc.getFillPaint();
		try (final var state = gc.begin()) {
			for (final var layer : layers) {
				final var glyph = ttf.getGlyph(layer.glyphId());
				if (glyph == null || glyph.path() == null) {
					continue;
				}
				if (layer.paletteEntry() != 0xFFFF) {
					final var argb = this.cpal.getColor(0, layer.paletteEntry());
					gc.setFillPaint(RGBAColor.create(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
							(argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f));
				} else {
					gc.setFillPaint(savedFill);
				}
				// The glyph outline is already normalized to the standard em, so
				// the caller's design-units-to-user transform applies directly.
				gc.fill(at.createTransformedShape(glyph.path()));
			}
		}
		gc.setFillPaint(savedFill);
	}

	/**
	 * Builds the two-component ligature map from the {@code liga} feature.
	 * Longer ligatures are formed incrementally by the shaper through the
	 * intermediate ligatures that fonts conventionally also define.
	 */
	private static net.zamasoft.pdfg2d.util.LongIntLookup buildLigatures(final GsubTable gsub) {
		if (gsub == null) {
			return null;
		}
		// 構築中のみboxed Mapを使う(重複キーのlast-wins意味論を保存)。
		// 保持するのはプリミティブ索引だけ
		final var map = new HashMap<Long, Integer>();
		for (final var subst : gsub.collectLigatures()) {
			final var firstGlyphs = subst.coverage().getGlyphIds();
			for (int i = 0; i < firstGlyphs.length && i < subst.getLigatureSetCount(); i++) {
				final int firstGid = firstGlyphs[i];
				for (final var lig : subst.getLigatureSet(i).ligatures()) {
					// Only two-component ligatures (one trailing component).
					if (lig.components().length == 1) {
						final int secondGid = lig.components()[0];
						map.put(((long) firstGid << 32) | (secondGid & 0xFFFFFFFFL), lig.ligGlyph());
					}
				}
			}
		}
		if (map.isEmpty()) {
			return null;
		}
		final long[] keys = new long[map.size()];
		final int[] values = new int[map.size()];
		int n = 0;
		for (final var e : map.entrySet()) {
			keys[n] = e.getKey();
			values[n] = e.getValue();
			++n;
		}
		return net.zamasoft.pdfg2d.util.LongIntLookup.fromUnsorted(keys, values, n);
	}
}
