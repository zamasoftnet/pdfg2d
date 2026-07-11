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
public record PositionedLine(List<PositionedRun> runs, double width, double ascent, double descent) {

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
}
