package net.zamasoft.pdfg2d.font.otf;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.io.IOException;

import net.zamasoft.pdfg2d.font.table.FeatureTags;
import net.zamasoft.pdfg2d.font.table.GsubTable;
import net.zamasoft.pdfg2d.font.table.ScriptTags;
import net.zamasoft.pdfg2d.font.table.SingleSubst;
import net.zamasoft.pdfg2d.font.table.Table;
import net.zamasoft.pdfg2d.font.table.XmtxTable;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ShapedFont;
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
public abstract class OpenTypeFont implements ShapedFont {
	private static final long serialVersionUID = 2L;

	protected static final int DEFAULT_VERTICAL_ORIGIN = 880;

	protected static final boolean ADJUST_VERTICAL = false;

	protected final OpenTypeFontSource source;

	protected final SingleSubst vSubst;

	protected final XmtxTable vmtx, hmtx;

	/**
	 * Creates a new OpenTypeFont.
	 * 
	 * @param source the font source
	 */
	protected OpenTypeFont(final OpenTypeFontSource source) {
		this.source = source;
		final var ttfFont = source.getOpenTypeFont();
		this.hmtx = (XmtxTable) ttfFont.getTable(Table.HMTX);

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
		return -1;
	}
}

