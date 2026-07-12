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
import net.zamasoft.pdfg2d.gc.text.breaking.impl.BitSetCharacterSet;
import net.zamasoft.pdfg2d.gc.text.breaking.impl.CharacterSet;

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

	protected final SingleSubst vSubst;

	protected final XmtxTable vmtx, hmtx;

	/** GSUB {@code liga} pairs: key {@code (firstGid << 32) | secondGid} to ligature glyph. */
	private final Map<Long, Integer> ligatures;

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
			// Vertical writing mode
			final var gsub = (GsubTable) ttfFont.getTable(Table.GSUB);
			final var scriptList = gsub.getScriptList();
			var script = scriptList.findScript(ScriptTags.SCRIPT_TAG_KANA);
			if (script == null) {
				script = scriptList.findScript(ScriptTags.SCRIPT_TAG_HANI);
			}
			if (script == null) {
				script = scriptList.findScript(ScriptTags.SCRIPT_TAG_LATN);
			}
			if (script == null) {
				script = scriptList.findScript(ScriptTags.SCRIPT_TAG_HANG);
			}
			if (script != null) {
				final var langSys = script.getDefaultLangSys();
				final var featureList = gsub.getFeatureList();
				final var feature = featureList.findFeature(langSys, FeatureTags.FEATURE_TAG_VERT);
				if (feature != null) {
					final var lookupList = gsub.getLookupList();
					final var lookup = lookupList.getLookup(feature, 0);
					this.vSubst = (SingleSubst) lookup.getSubtable(0);
					this.vmtx = (XmtxTable) ttfFont.getTable(Table.VMTX);
					return;
				}
			}
		}
		this.vSubst = null;
		this.vmtx = null;
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
		if (!this.isVertical()) {
			return shape;
		}
		if (ADJUST_VERTICAL) {
			final double advance = this.getAdvance(gid);
			final var bound = shape.getBounds2D();
			final double bottom = bound.getY() + bound.getHeight() + DEFAULT_VERTICAL_ORIGIN;
			if (bottom > advance) {
				// Adjust to avoid collision
				final var path = new GeneralPath(shape);
				path.transform(AffineTransform.getTranslateInstance(0, advance - bottom));
				shape = path;
			}
		}
		final int cid = this.toChar(gid);
		// Check for specific characters to rotate: fullwidth hyphen, less-than,
		// greater-than, minus, double less-than, double greater-than
		if (cid == 0xFF0D || cid == 0xFF1C || cid == 0xFF1E || cid == 0x2212 || cid == 0x226A || cid == 0x226B) {
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
		if (this.vSubst != null) {
			gid = this.vSubst.substitute(gid);
		}
		return gid;
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

	// Opening brackets
	private static final CharacterSet CL01 = new BitSetCharacterSet("‘“（〔［｛〈《「『【⦅〖«〝");
	// Closing brackets
	private static final CharacterSet CL02 = new BitSetCharacterSet("’”）〕］｝〉》」』】⦆〙〗»〟");
	// Punctuation
	private static final CharacterSet CL0607 = new BitSetCharacterSet("。．、，");

	@Override
	public short getKerning(final int sgid, final int gid) {
		// GPOS pair kerning first (glyph ids are font glyph ids for the
		// non-embedding font; subclasses that remap glyphs translate before
		// calling gposKerning).
		final short gpos = this.gposKerning(sgid, gid);
		if (gpos != 0) {
			return gpos;
		}

		final int scid = this.toChar(sgid);
		// Kerning for brackets and punctuation
		final short THRESHOLD = 750, KERNING = 500;
		if (CL01.contains((char) scid) && this.getWidth(sgid) > THRESHOLD) {
			final int cid = this.toChar(gid);
			if (CL01.contains((char) cid) && this.getWidth(gid) > THRESHOLD) {
				return KERNING;
			}
		} else if (CL02.contains((char) scid) && this.getWidth(sgid) > THRESHOLD) {
			final int cid = this.toChar(gid);
			if ((CL01.contains((char) cid) || CL02.contains((char) cid) || CL0607.contains((char) cid))
					&& this.getWidth(gid) > THRESHOLD) {
				return KERNING;
			}
		} else if (CL0607.contains((char) scid) && this.getWidth(sgid) > THRESHOLD) {
			final int cid = this.toChar(gid);
			if ((CL01.contains((char) cid) || (CL02.contains((char) cid)) && this.getWidth(gid) > THRESHOLD)) {
				return KERNING;
			}
		}
		return 0;
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
		final Integer lig = this.ligatures
				.get(((long) firstFontGid << 32) | (secondFontGid & 0xFFFFFFFFL));
		return (lig != null) ? lig : -1;
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
	private static Map<Long, Integer> buildLigatures(final GsubTable gsub) {
		if (gsub == null) {
			return null;
		}
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
		return map.isEmpty() ? null : map;
	}
}

