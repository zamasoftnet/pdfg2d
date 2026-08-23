package net.zamasoft.pdfg2d.font;

import java.io.IOException;

import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * Implementation of {@link FontMetrics}.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class FontMetricsImpl implements FontMetrics {
	private static final long serialVersionUID = 1L;

	protected final FontStore fontStore;

	protected final FontSource source;

	protected final double size, xheight;

	protected final double ascent, descent;

	/** The style's OpenType feature settings (advance adjustments, etc.). */
	protected final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features;

	protected final FontStyle.Direction direction;

	protected final FontStyle.TextOrientation textOrientation;

	protected Font font = null;

	/**
	 * Creates a new FontMetricsImpl.
	 *
	 * @param fontStore  the font store
	 * @param fontSource the font source
	 * @param fontStyle  the font style
	 */
	public FontMetricsImpl(final FontStore fontStore, final FontSource fontSource, final FontStyle fontStyle) {
		this.fontStore = fontStore;
		this.source = fontSource;
		this.features = fontStyle.getFeatures();
		this.direction = fontStyle.getDirection();
		this.textOrientation = fontStyle.getTextOrientation();
		this.size = fontStyle.getSize();
		this.xheight = this.size * this.source.getXHeight() / FontSource.DEFAULT_UNITS_PER_EM;

		final var direction = fontStyle.getDirection();
		double ascent, descent;
		switch (direction) {
			case LTR, RTL -> {
				// Horizontal
				ascent = this.size * this.source.getAscent() / FontSource.DEFAULT_UNITS_PER_EM;
				descent = this.size * this.source.getDescent() / FontSource.DEFAULT_UNITS_PER_EM;
			}
			case TB -> {
				// Vertical
				ascent = descent = this.size / 2.0;
			}
			default -> throw new IllegalStateException();
		}
		final double remainder = (this.size - ascent - descent);
		if (remainder != 0) {
			final double afrac = ascent / (ascent + descent);
			final double dfrac = descent / (ascent + descent);
			ascent = this.size * afrac;
			descent = this.size * dfrac;
		}
		this.ascent = ascent;
		this.descent = descent;
	}

	/**
	 * Returns the font, loading it lazily from the font store if necessary.
	 *
	 * @return the font instance
	 * @throws RuntimeException wrapping any {@link java.io.IOException} that occurs
	 */
	public Font getFont() {
		if (this.font == null) {
			try {
				this.font = this.fontStore.useFont(this.source);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}
		return this.font;
	}

	@Override
	public FontSource getFontSource() {
		return this.source;
	}

	@Override
	public double getAscent() {
		return this.ascent;
	}

	@Override
	public double getDescent() {
		return this.descent;
	}

	@Override
	public double getFontSize() {
		return this.size;
	}

	@Override
	public double getXHeight() {
		return this.xheight;
	}

	@Override
	public double getSpaceAdvance() {
		return this.size * this.source.getSpaceAdvance() / FontSource.DEFAULT_UNITS_PER_EM;
	}

	@Override
	public double getAdvance(final int gid) {
		double advance = this.size * this.getFont().getAdvance(gid) / FontSource.DEFAULT_UNITS_PER_EM;
		if (!this.features.isEmpty()) {
			advance += this.getAdvanceAdjustment(gid);
		}
		return advance;
	}

	@Override
	public double getAdvanceAdjustment(final int gid) {
		if (this.features.isEmpty()) {
			return 0;
		}
		return this.size * this.getFont().getAdvanceAdjustment(gid, this.features)
				/ FontSource.DEFAULT_UNITS_PER_EM;
	}

	@Override
	public double getPlacementAdjustment(final int gid) {
		if (this.features.isEmpty()) {
			return 0;
		}
		return this.size * this.getFont().getPlacementAdjustment(gid, this.features)
				/ FontSource.DEFAULT_UNITS_PER_EM;
	}

	@Override
	public double getWidth(final int gid) {
		return this.size * this.getFont().getWidth(gid) / FontSource.DEFAULT_UNITS_PER_EM;
	}

	/** Packed {@code kern}/{@code liga} tags for the explicit-off checks. */
	private static final int TAG_KERN = 0x6b65726e, TAG_LIGA = 0x6c696761;

	@Override
	public double getKerning(final int gid, final int sgid) {
		// font-feature-settings "kern" 0: 明示offのみ無効化(無指定=-1は
		// 既定どおり有効)。push型と新pipelineの両経路がここへ委譲する
		if (this.features.value(TAG_KERN) == 0) {
			return 0;
		}
		return this.size * this.getFont().getKerning(gid, sgid) / FontSource.DEFAULT_UNITS_PER_EM;
	}

	/**
	 * Returns the ligature glyph ID for the given glyph and following character.
	 * Explicitly disabled by {@code font-feature-settings "liga" 0} (an
	 * unspecified {@code liga} keeps the default behaviour).
	 *
	 * @param gid the current glyph ID
	 * @param cid the following character code
	 * @return the ligature glyph ID, or a negative value if none exists
	 */
	public int getLigature(final int gid, final int cid) {
		if (this.features.value(TAG_LIGA) == 0) {
			return -1;
		}
		return this.getFont().getLigature(gid, cid);
	}

	/**
	 * Returns whether the given character can be displayed by this font.
	 *
	 * @param c the character code
	 * @return {@code true} if the character can be displayed
	 */
	public boolean canDisplay(final int c) {
		if (this.direction == FontStyle.Direction.TB
				&& this.textOrientation == FontStyle.TextOrientation.UPRIGHT
				&& this.source.getDirection() == FontStyle.Direction.TB
				&& this.source instanceof net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource otf) {
			return otf.canDisplayUpright(c);
		}
		return this.getFontSource().canDisplay(c);
	}

	@Override
	public String toString() {
		return super.toString() + ":[fontSource=" + this.source + "]";
	}
}
