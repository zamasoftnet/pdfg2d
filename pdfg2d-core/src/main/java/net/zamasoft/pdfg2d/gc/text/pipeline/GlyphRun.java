package net.zamasoft.pdfg2d.gc.text.pipeline;

import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * The output of shaping one {@link Item}: a run of positioned glyphs from a
 * single font, following the HarfBuzz buffer model.
 * <p>
 * Each glyph {@code i} maps to the source character index {@code clusters[i]}
 * (in logical order); several glyphs may share a cluster (decomposition) and
 * several characters may map to one cluster (ligature). Advances move the pen
 * after drawing; offsets move the glyph without moving the pen (mark
 * placement). Both come in x and y so that vertical writing and GPOS mark
 * attachment are representable — the gap that the legacy {@code Text} model
 * could not express.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class GlyphRun {

	/** The item this run was shaped from. */
	public final Item item;

	/** The paragraph text (logical order); {@code clusters} index into it. */
	public char[] text;

	/** The font metrics used for this run. */
	public final FontMetrics fontMetrics;

	/** Glyph identifiers. */
	public int[] gids;

	/** Source character index (logical order) of each glyph's cluster. */
	public int[] clusters;

	/** Pen advance after each glyph (x and y). */
	public double[] xAdvances;
	public double[] yAdvances;

	/** Glyph placement offset that does not move the pen (x and y). */
	public double[] xOffsets;
	public double[] yOffsets;

	/** Number of glyphs. */
	public int length;

	/**
	 * Creates an empty run of the given capacity.
	 *
	 * @param item        the source item
	 * @param fontMetrics the run's font metrics
	 * @param capacity    the initial glyph capacity
	 */
	public GlyphRun(final Item item, final FontMetrics fontMetrics, final int capacity) {
		this.item = item;
		this.fontMetrics = fontMetrics;
		this.gids = new int[capacity];
		this.clusters = new int[capacity];
		this.xAdvances = new double[capacity];
		this.yAdvances = new double[capacity];
		this.xOffsets = new double[capacity];
		this.yOffsets = new double[capacity];
	}

	/** Returns the font style of this run. */
	public FontStyle getFontStyle() {
		return this.item.style();
	}

	/** Returns whether this run is right-to-left (odd bidi level). */
	public boolean isRTL() {
		return (this.item.bidiLevel() & 1) != 0;
	}

	/**
	 * Appends one glyph.
	 *
	 * @param gid      the glyph id
	 * @param cluster  the source character index
	 * @param xAdvance the x advance
	 * @param yAdvance the y advance
	 */
	public void add(final int gid, final int cluster, final double xAdvance, final double yAdvance) {
		if (this.length == this.gids.length) {
			final var n = this.length * 3 / 2 + 1;
			this.gids = java.util.Arrays.copyOf(this.gids, n);
			this.clusters = java.util.Arrays.copyOf(this.clusters, n);
			this.xAdvances = java.util.Arrays.copyOf(this.xAdvances, n);
			this.yAdvances = java.util.Arrays.copyOf(this.yAdvances, n);
			this.xOffsets = java.util.Arrays.copyOf(this.xOffsets, n);
			this.yOffsets = java.util.Arrays.copyOf(this.yOffsets, n);
		}
		this.gids[this.length] = gid;
		this.clusters[this.length] = cluster;
		this.xAdvances[this.length] = xAdvance;
		this.yAdvances[this.length] = yAdvance;
		++this.length;
	}

	/**
	 * Returns the summed x advance of glyphs in {@code [from, to)}.
	 *
	 * @param from the first glyph index
	 * @param to   the end glyph index (exclusive)
	 * @return the total x advance
	 */
	public double advance(final int from, final int to) {
		var sum = 0.0;
		for (var i = from; i < to; ++i) {
			sum += this.xAdvances[i];
		}
		return sum;
	}

	/** Returns the total x advance of the run. */
	public double advance() {
		return this.advance(0, this.length);
	}
}
