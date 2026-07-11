package net.zamasoft.pdfg2d.gc.text.pipeline;

import java.util.List;

/**
 * A finished line: glyph slices placed at absolute x positions relative to the
 * line origin, in visual (left-to-right) order, plus the line's vertical
 * extent.
 *
 * @param runs    the placed glyph slices in visual order
 * @param width   the line's used width
 * @param ascent  the maximum ascent over the line
 * @param descent the maximum descent over the line
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record PositionedLine(List<PositionedRun> runs, List<RubyOverlay> rubies, double width, double ascent,
		double descent) {

	/**
	 * Creates a line with no ruby overlays.
	 *
	 * @param runs    the placed glyph slices
	 * @param width   the used width
	 * @param ascent  the maximum ascent
	 * @param descent the maximum descent
	 */
	public PositionedLine(final List<PositionedRun> runs, final double width, final double ascent,
			final double descent) {
		this(runs, List.of(), width, ascent, descent);
	}

	/**
	 * A glyph slice placed on a line.
	 *
	 * @param run        the source glyph run
	 * @param glyphBegin the first glyph index (inclusive)
	 * @param glyphEnd   the end glyph index (exclusive)
	 * @param x          the x position of the slice origin on the line
	 * @param extraSpace extra advance distributed into inter-glyph space for
	 *                   justification (added after each glyph of the slice)
	 */
	public record PositionedRun(GlyphRun run, int glyphBegin, int glyphEnd, double x, double extraSpace) {
	}

	/**
	 * A ruby annotation placed above (or below) its base on the line.
	 *
	 * @param run  the ruby glyph run
	 * @param x    the x position of the first ruby glyph
	 * @param y    the baseline offset relative to the main baseline (negative =
	 *             above)
	 * @param step the x advance between ruby glyphs (mono distribution)
	 */
	public record RubyOverlay(GlyphRun run, double x, double y, double step) {
	}
}
