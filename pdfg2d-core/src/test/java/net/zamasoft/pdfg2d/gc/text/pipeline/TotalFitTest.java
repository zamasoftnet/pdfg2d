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
 * {@link TotalFit}(Knuth-Plass total-fit)の単体テスト。合成ノード列で
 * アルゴリズム契約(貪欲法との差・強制/禁止/flagged penalty・最終行
 * ポリシー・fit-anyway縮退)を固定する。
 */
class TotalFitTest {

	private static BreakNode.Box box(final double width) {
		return new BreakNode.Box(width, null, 0, 0);
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
		// RAGGED既定なので最終行のadjustment ratioは0(自然幅のまま)。
		assertEquals(0.0, line.adjustmentRatio());
	}

	@Test
	void justifiedLastLineStretches() {
		final Parameters params = Parameters.texDefaults().withLastLine(LastLinePolicy.JUSTIFY);
		// 自然幅 10+5+10=25、行幅30 → 5をstretch 6で埋める → r=5/6
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
		// 禁則(分離禁止)で結ばれた2つのboxは、行幅を超えても分割されない
		// (fit-anyway縮退ではみ出したまま1行になる)。
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
		// box(4) [hyphen: width1 cost50] box(4)、行幅5。
		// ハイフンで割れば 4+1=5 でちょうど収まる。
		final List<BreakNode> nodes = new ArrayList<>();
		nodes.add(box(4));
		nodes.add(new BreakNode.Penalty(1, 50, true, null));
		nodes.add(box(4));
		final List<BrokenLine> lines = TotalFit.totalFit(nodes, 5, Parameters.texDefaults());
		assertEquals(2, lines.size());
		assertEquals(1, lines.get(0).breakIndex());
		assertEquals(BreakKind.NORMAL, lines.get(0).kind());
	}

	@Test
	void totalFitAvoidsGreedyTrap() {
		// 貪欲法は先の行を詰め込みすぎて最後の行が1語だけになる。
		// total-fitは全体のdemerits最小化により2語ずつへ均す。
		// box(5)×4をglue(1, stretch 8, shrink 0.3)で結合、行幅17。
		// 貪欲: [5,1,5,1,5]=17 → 2行目 [5](1語のみ)。
		// K-P: 2-2分割(各行 natural 11、r=0.75、badness 42 ≤ tolerance)。
		final List<BreakNode> nodes = words(5, 4, 1, 8, 0.3);
		final List<LineBreaker.Line> greedy = LineBreaker.greedy(nodes, 17);
		final List<BrokenLine> optimal = TotalFit.totalFit(nodes, 17,
				Parameters.texDefaults().withLastLine(LastLinePolicy.JUSTIFY));
		// 貪欲法は最終行が1語(ノード1個)になる
		final LineBreaker.Line greedyLast = greedy.get(greedy.size() - 1);
		assertEquals(1, greedyLast.end() - greedyLast.begin());
		// total-fitは同じ行数のまま、どの行も2語を保つ(極端な最終行を回避)
		assertEquals(greedy.size(), optimal.size());
		for (final BrokenLine line : optimal) {
			final long boxes = nodes.subList(line.begin(), Math.min(line.end(), nodes.size())).stream()
					.filter(n -> n instanceof BreakNode.Box).count();
			assertTrue(boxes >= 2, "each line should keep at least 2 boxes, got " + boxes);
		}
	}

	@Test
	void varyingLineMeasureIsRespected() {
		// 1行目だけ狭い(text-indent相当)。1行目は2語、2行目に4語。
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
	void fixedMeasureRejectsNonPositiveWidth() {
		assertThrows(IllegalArgumentException.class, () -> LineMeasure.fixed(0));
		assertThrows(IllegalArgumentException.class, () -> LineMeasure.fixed(Double.POSITIVE_INFINITY));
	}
}
