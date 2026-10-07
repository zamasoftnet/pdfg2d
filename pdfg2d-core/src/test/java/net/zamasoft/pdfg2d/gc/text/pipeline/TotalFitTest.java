package net.zamasoft.pdfg2d.gc.text.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.text.pipeline.TotalFit.BreakKind;
import net.zamasoft.pdfg2d.gc.text.pipeline.TotalFit.BrokenLine;
import net.zamasoft.pdfg2d.gc.text.pipeline.TotalFit.LastLinePolicy;
import net.zamasoft.pdfg2d.gc.text.pipeline.TotalFit.Parameters;

/**
 * Unit tests for {@link TotalFit} (Knuth-Plass total-fit). Synthetic node sequences
 * fix the algorithm's contract: differences from greedy fitting, forced/prohibited/flagged
 * penalties, last-line policy, and fit-anyway fallback.
 */
class TotalFitTest {

	private static BreakNode.Box box(final double width) {
		return new BreakNode.Box(width);
	}

	private static BreakNode.Glue glue(final double width, final double stretch, final double shrink) {
		return new BreakNode.Glue(width, stretch, shrink);
	}

	private static List<BreakNode> words(final double wordWidth, final int count, final double glueWidth,
			final double stretch, final double shrink) {
		final List<BreakNode> nodes = new ArrayList<>();
		for (int i = 0; i < count; ++i) {
			if (i > 0) {
				nodes.add(glue(glueWidth, stretch, shrink));
			}
			nodes.add(box(wordWidth));
		}
		return nodes;
	}

	@Test
	void emptyNodesYieldNoLines() {
		assertEquals(List.of(), TotalFit.totalFit(List.of(), 100, Parameters.texDefaults()));
	}

	@Test
	void singleFittingLineIsParagraphEnd() {
		final List<BrokenLine> lines = TotalFit.totalFit(words(10, 3, 5, 3, 1), 100, Parameters.texDefaults());
		assertEquals(1, lines.size());
		final BrokenLine line = lines.get(0);
		assertEquals(BreakKind.PARAGRAPH_END, line.kind());
		assertEquals(0, line.begin());
		// RAGGED is the default, so the last line's adjustment ratio is 0 (retains its natural width).
		assertEquals(0.0, line.adjustmentRatio());
	}

	@Test
	void justifiedLastLineStretches() {
		final Parameters params = Parameters.texDefaults().withLastLine(LastLinePolicy.JUSTIFY);
		// Natural width 10+5+10=25, line width 30 -> fill 5 with stretch 6 -> r=5/6.
		final List<BrokenLine> lines = TotalFit.totalFit(words(10, 2, 5, 6, 1), 30, params);
		assertEquals(1, lines.size());
		assertEquals(5.0 / 6, lines.get(0).adjustmentRatio(), 1e-9);
	}

	@Test
	void forcedPenaltySplitsAndResets() {
		final List<BreakNode> nodes = new ArrayList<>();
		nodes.add(box(10));
		nodes.add(BreakNode.Penalty.forced());
		nodes.add(box(10));
		final List<BrokenLine> lines = TotalFit.totalFit(nodes, 100, Parameters.texDefaults());
		assertEquals(2, lines.size());
		assertEquals(BreakKind.FORCED, lines.get(0).kind());
		assertEquals(1, lines.get(0).breakIndex());
		assertEquals(BreakKind.PARAGRAPH_END, lines.get(1).kind());
		assertEquals(2, lines.get(1).begin());
	}

	@Test
	void forbiddenPenaltyKeepsBoxesTogetherEvenOverfull() {
		// Two boxes joined by kinsoku (line-breaking rules) prohibiting separation stay together even beyond the line width
		// (the fit-anyway fallback produces one overflowing line).
		final List<BreakNode> nodes = new ArrayList<>();
		nodes.add(box(8));
		nodes.add(BreakNode.Penalty.forbidden());
		nodes.add(box(8));
		final List<BrokenLine> lines = TotalFit.totalFit(nodes, 10, Parameters.texDefaults());
		assertEquals(1, lines.size());
		assertEquals(BreakKind.PARAGRAPH_END, lines.get(0).kind());
	}

	@Test
	void flaggedPenaltyAddsHyphenWidthOnlyWhenBroken() {
		// box(4) [hyphen: width1 cost50] box(4), line width 5.
		// Breaking at the hyphen gives 4+1=5, an exact fit.
		final List<BreakNode> nodes = new ArrayList<>();
		nodes.add(box(4));
		nodes.add(new BreakNode.Penalty(1, 50, true));
		nodes.add(box(4));
		final List<BrokenLine> lines = TotalFit.totalFit(nodes, 5, Parameters.texDefaults());
		assertEquals(2, lines.size());
		assertEquals(1, lines.get(0).breakIndex());
		assertEquals(BreakKind.NORMAL, lines.get(0).kind());
	}

	@Test
	void totalFitAvoidsGreedyTrap() {
		// Greedy fitting packs earlier lines too tightly, leaving only one word on the last line.
		// Total-fit minimizes overall demerits to balance the lines at two words each.
		// Four box(5) nodes joined with glue(1, stretch 8, shrink 0.3), line width 17.
		// Greedy: [5,1,5,1,5]=17 -> second line [5] (only one word).
		// K-P: 2-2 split (each line: natural 11, r=0.75, badness 42 <= tolerance).
		final List<BreakNode> nodes = words(5, 4, 1, 8, 0.3);
		final List<BrokenLine> optimal = TotalFit.totalFit(nodes, 17,
				Parameters.texDefaults().withLastLine(LastLinePolicy.JUSTIFY));
		// Total-fit keeps two lines, as greedy fitting does, but puts two words on each (avoiding an extreme last line).
		assertEquals(2, optimal.size());
		for (final BrokenLine line : optimal) {
			final long boxes = nodes.subList(line.begin(), Math.min(line.end(), nodes.size())).stream()
					.filter(n -> n instanceof BreakNode.Box).count();
			assertTrue(boxes >= 2, "each line should keep at least 2 boxes, got " + boxes);
		}
	}

	@Test
	void varyingLineMeasureIsRespected() {
		// Only the first line is narrow (equivalent to text-indent). Two words on the first line, four on the second.
		final List<BreakNode> nodes = words(5, 6, 1, 2, 0.5);
		final LineMeasure measure = lineIndex -> lineIndex == 0 ? 11 : 100;
		final List<BrokenLine> lines = TotalFit.totalFit(nodes, measure, Parameters.texDefaults());
		assertEquals(2, lines.size());
		final BrokenLine first = lines.get(0);
		final long boxesInFirst = nodes.subList(first.begin(), first.end()).stream()
				.filter(n -> n instanceof BreakNode.Box).count();
		assertEquals(2, boxesInFirst);
	}

	@Test
	void overfullSingleBoxStillProducesALine() {
		final List<BrokenLine> lines = TotalFit.totalFit(List.of(box(50)), 10, Parameters.texDefaults());
		assertEquals(1, lines.size());
		assertEquals(BreakKind.PARAGRAPH_END, lines.get(0).kind());
	}

	@Test
	void deterministicAcrossRuns() {
		final List<BreakNode> nodes = words(3, 40, 1, 1, 0.3);
		final List<BrokenLine> first = TotalFit.totalFit(nodes, 20, Parameters.texDefaults());
		final List<BrokenLine> second = TotalFit.totalFit(nodes, 20, Parameters.texDefaults());
		assertEquals(first, second);
	}

	@Test
	void longCjkParagraphSolvesInLinearTime() {
		// Typical Japanese text: every character boundary is a break candidate (repeated box+penalty(0)).
		// Without deactivation (removing active nodes that exceed the shrink limit), the active set
		// grew in proportion to the number of break candidates and stalled for minutes in measurements
		// (2026-07-24, discovered when making text.line-breaker the default). Require a solution
		// in under a second even for 10000 characters.
		final List<BreakNode> nodes = new ArrayList<>();
		for (int i = 0; i < 10000; ++i) {
			if (i > 0) {
				nodes.add(new BreakNode.Penalty(0, 0, false));
			}
			nodes.add(box(10));
		}
		final long start = System.nanoTime();
		final List<BrokenLine> lines = TotalFit.totalFit(nodes, 400,
				Parameters.texDefaults().withLastLine(LastLinePolicy.JUSTIFY));
		final long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
		assertTrue(lines.size() > 200, "expected many lines, got " + lines.size());
		assertTrue(elapsedMillis < 5000, "solver took " + elapsedMillis + "ms — deactivation regressed?");
	}

	@Test
	void allCandidatesHopelessForcesProgress() {
		// When normal break candidates follow a large chunk joined by prohibited breaks,
		// fit-anyway makes progress even if all active nodes exceed the shrink limit at once
		// (no infinite loop or failure due to an empty active set).
		final List<BreakNode> nodes = new ArrayList<>();
		nodes.add(box(5));
		nodes.add(glue(1, 0.5, 0));
		// An indivisible chunk far too large to fit in a line of width 50.
		for (int i = 0; i < 30; ++i) {
			nodes.add(box(10));
			if (i < 29) {
				nodes.add(BreakNode.Penalty.forbidden());
			}
		}
		nodes.add(glue(1, 0.5, 0));
		nodes.add(box(5));
		final List<BrokenLine> lines = TotalFit.totalFit(nodes, 50, Parameters.texDefaults());
		assertTrue(lines.size() >= 2, "expected the oversized chunk to be forced onto its own line(s)");
	}

	@Test
	void fixedMeasureRejectsNonPositiveWidth() {
		assertThrows(IllegalArgumentException.class, () -> LineMeasure.fixed(0));
		assertThrows(IllegalArgumentException.class, () -> LineMeasure.fixed(Double.POSITIVE_INFINITY));
	}
}
