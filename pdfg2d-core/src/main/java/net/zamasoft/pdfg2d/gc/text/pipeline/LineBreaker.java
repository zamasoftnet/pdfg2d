package net.zamasoft.pdfg2d.gc.text.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses line breaks over a {@link BreakNode} stream. The default strategy is
 * greedy (first-fit): fill each line until the next box would overflow, then
 * break at the most recent feasible breakpoint. The same node model also
 * admits an optimal (Knuth-Plass) strategy; only this class changes.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class LineBreaker {

	private LineBreaker() {
	}

	/**
	 * A chosen line: the node range plus whether it ended at a forced break.
	 *
	 * @param begin  the first node index (inclusive)
	 * @param end    the end node index (exclusive)
	 * @param forced whether the line ended at a mandatory break
	 */
	public record Line(int begin, int end, boolean forced) {
	}

	/**
	 * Breaks the node stream into lines under the given line width.
	 *
	 * @param nodes the box/glue/penalty stream
	 * @param width the available line width
	 * @return the lines in order
	 */
	public static List<Line> greedy(final List<BreakNode> nodes, final double width) {
		final var lines = new ArrayList<Line>();
		final var n = nodes.size();
		var lineStart = 0;
		var x = 0.0;
		var lastBreak = -1; // node index of the last feasible break (a glue/penalty)
		var widthAtBreak = 0.0;

		var i = 0;
		while (i < n) {
			final var node = nodes.get(i);
			if (node instanceof BreakNode.Penalty penalty) {
				if (penalty.cost() <= BreakNode.Penalty.FORCE) {
					// Mandatory break: end the line here.
					lines.add(new Line(lineStart, i + 1, true));
					lineStart = i + 1;
					x = 0;
					lastBreak = -1;
					++i;
					continue;
				}
				if (penalty.cost() < BreakNode.Penalty.INFINITY) {
					// Feasible break; the penalty width (e.g. a hyphen) counts
					// only if we actually break here.
					if (x + penalty.width() <= width || lastBreak < 0) {
						lastBreak = i;
						widthAtBreak = x;
					}
				}
				++i;
				continue;
			}
			if (node instanceof BreakNode.Glue glue) {
				// A glue is a break opportunity; leading glue on a line is dropped.
				if (x > 0) {
					lastBreak = i;
					widthAtBreak = x;
					x += glue.width();
				}
				++i;
				continue;
			}
			// Box
			if (x + node.width() > width && lastBreak >= 0 && widthAtBreak > 0) {
				// Overflow: break at the last opportunity.
				lines.add(new Line(lineStart, lastBreak + 1, false));
				lineStart = lastBreak + 1;
				// Skip a glue used as the breakpoint (its space is consumed).
				i = lineStart;
				x = 0;
				lastBreak = -1;
				continue;
			}
			x += node.width();
			++i;
		}
		if (lineStart < n) {
			lines.add(new Line(lineStart, n, true));
		}
		return lines;
	}
}
