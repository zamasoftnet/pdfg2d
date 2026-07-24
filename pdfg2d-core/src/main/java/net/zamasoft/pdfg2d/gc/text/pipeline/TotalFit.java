package net.zamasoft.pdfg2d.gc.text.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * The optimal (Knuth-Plass, total-fit) line breaking strategy over a
 * {@link BreakNode} stream: dynamic programming over feasible breakpoints that
 * minimizes the total demerits of the whole paragraph, instead of committing
 * each line greedily.
 *
 * <p>
 * The node semantics are identical to {@link LineBreaker#greedy}: a
 * {@link BreakNode.Penalty} with cost {@code <= FORCE} is a mandatory break, a
 * penalty with cost {@code < INFINITY} is a feasible break whose width counts
 * only when actually broken there, a {@link BreakNode.Glue} is a feasible
 * break when it follows a box, and glue at the head of a line is discarded.
 * </p>
 *
 * <p>
 * When no feasible breakpoint remains (every candidate is overfull beyond the
 * shrinkability or worse than the tolerance), the breaker degrades
 * deterministically: it keeps the best active candidate anyway (fit-anyway
 * policy) so that a result is always produced — mirroring the greedy
 * strategy's behavior of overflowing rather than failing.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class TotalFit {

	private TotalFit() {
	}

	/** How a chosen line ended. */
	public enum BreakKind {
		/** An optional breakpoint (glue or a non-forced penalty) was chosen. */
		NORMAL,
		/** A mandatory break (forced penalty / explicit line feed). */
		FORCED,
		/** The end of the node stream. */
		PARAGRAPH_END
	}

	/** How the last line of the paragraph is measured. */
	public enum LastLinePolicy {
		/**
		 * The last line is set ragged: underfull last lines carry no badness
		 * (CSS default; {@code text-align-last} other than justify).
		 */
		RAGGED,
		/** The last line is justified like any other line. */
		JUSTIFY
	}

	/**
	 * A chosen line.
	 *
	 * @param begin           the first node index (inclusive; leading discarded
	 *                        glue included, as in {@link LineBreaker.Line})
	 * @param end             the end node index (exclusive; includes the break
	 *                        node, matching {@link LineBreaker.Line})
	 * @param breakIndex      the node index broken at, or the stream size for
	 *                        {@link BreakKind#PARAGRAPH_END}
	 * @param kind            how the line ended
	 * @param adjustmentRatio the glue adjustment ratio r chosen for this line
	 *                        (0 = natural, positive = stretched, negative =
	 *                        shrunk); the consumer must apply the same ratio
	 *                        when positioning glyphs
	 * @param fitnessClass    the TeX fitness class (0=tight, 1=decent,
	 *                        2=loose, 3=very loose)
	 */
	public record BrokenLine(int begin, int end, int breakIndex, BreakKind kind, double adjustmentRatio,
			int fitnessClass) {
	}

	/**
	 * Demerit and tolerance settings. All values follow TeX's conventions:
	 * badness is {@code 100|r|^3} capped at {@link #BADNESS_INFINITE}, and a
	 * line is feasible when its badness does not exceed {@code tolerance}.
	 *
	 * @param tolerance            the maximum badness a line may have
	 * @param linePenalty          added to every line's badness before squaring
	 * @param adjDemerits          added when adjacent lines' fitness classes
	 *                             differ by more than one
	 * @param doubleHyphenDemerits added when two consecutive lines end at
	 *                             flagged penalties
	 * @param finalHyphenDemerits  added when the second-to-last line ends at a
	 *                             flagged penalty
	 * @param lastLine             how the last line is measured
	 */
	public record Parameters(double tolerance, double linePenalty, double adjDemerits, double doubleHyphenDemerits,
			double finalHyphenDemerits, LastLinePolicy lastLine) {

		/**
		 * TeX plain-format defaults (second pass), with a ragged last line.
		 * Intended for unit tests and as a starting point; CSS consumers
		 * should pass explicit values derived from their own model.
		 */
		public static Parameters texDefaults() {
			return new Parameters(200, 10, 10000, 10000, 5000, LastLinePolicy.RAGGED);
		}

		/** The same defaults with the given last-line policy. */
		public Parameters withLastLine(final LastLinePolicy policy) {
			return new Parameters(this.tolerance, this.linePenalty, this.adjDemerits, this.doubleHyphenDemerits,
					this.finalHyphenDemerits, policy);
		}
	}

	/** The badness value treated as infinite (TeX's {@code inf_bad}). */
	public static final double BADNESS_INFINITE = 10000;

	/**
	 * Breaks the node stream into lines of a single fixed width.
	 *
	 * @see #totalFit(List, LineMeasure, Parameters)
	 */
	public static List<BrokenLine> totalFit(final List<BreakNode> nodes, final double width,
			final Parameters params) {
		return totalFit(nodes, LineMeasure.fixed(width), params);
	}

	/**
	 * Breaks the node stream into lines, minimizing total demerits over the
	 * whole paragraph.
	 *
	 * @param nodes   the box/glue/penalty stream
	 * @param measure the per-line available widths
	 * @param params  tolerance and demerit settings
	 * @return the lines in order (empty when {@code nodes} is empty)
	 */
	public static List<BrokenLine> totalFit(final List<BreakNode> nodes, final LineMeasure measure,
			final Parameters params) {
		if (nodes.isEmpty()) {
			return List.of();
		}
		return new Solver(nodes, measure, params).solve();
	}

	/** An active candidate: a breakpoint a line may legally start after. */
	private static final class Active {
		/** Node index broken at (-1 = paragraph start). */
		final int breakIndex;
		/** First node index of the following line (leading glue skipped). */
		final int lineStart;
		/** Number of lines before this breakpoint. */
		final int line;
		/** Fitness class of the line ending here (1 at paragraph start). */
		final int fitness;
		/** Whether the line ending here broke at a flagged penalty. */
		final boolean flagged;
		final double totalDemerits;
		final Active previous;
		/** The chosen line ending at this breakpoint (null at start). */
		final BrokenLine lineRecord;

		Active(int breakIndex, int lineStart, int line, int fitness, boolean flagged, double totalDemerits,
				Active previous, BrokenLine lineRecord) {
			this.breakIndex = breakIndex;
			this.lineStart = lineStart;
			this.line = line;
			this.fitness = fitness;
			this.flagged = flagged;
			this.totalDemerits = totalDemerits;
			this.previous = previous;
			this.lineRecord = lineRecord;
		}
	}

	private static final class Solver {
		private final List<BreakNode> nodes;
		private final LineMeasure measure;
		private final Parameters params;
		/**
		 * The line index from which the width is constant
		 * ({@link LineMeasure#easyLine()}): line numbers at or beyond it are one
		 * equivalence class for candidate dominance — the remaining widths, and
		 * therefore the future demerits, are identical for both candidates, so
		 * keeping only the lower-demerits prefix preserves the optimum. Without
		 * this the active list holds one candidate per distinct line count
		 * (unbounded divergence on breakpoint-dense paragraphs).
		 */
		private final int easyLine;
		/** Cumulative natural width/stretch/shrink before each node index. */
		private final double[] sumWidth, sumStretch, sumShrink;

		Solver(final List<BreakNode> nodes, final LineMeasure measure, final Parameters params) {
			this.nodes = nodes;
			this.measure = measure;
			this.params = params;
			this.easyLine = measure.easyLine();
			final int n = nodes.size();
			this.sumWidth = new double[n + 1];
			this.sumStretch = new double[n + 1];
			this.sumShrink = new double[n + 1];
			for (int i = 0; i < n; ++i) {
				final BreakNode node = nodes.get(i);
				double w = 0, y = 0, z = 0;
				if (node instanceof BreakNode.Box box) {
					w = box.width();
				} else if (node instanceof BreakNode.Glue glue) {
					w = glue.width();
					y = glue.stretch();
					z = glue.shrink();
				}
				// Penalty widths count only when broken at; excluded here.
				this.sumWidth[i + 1] = this.sumWidth[i] + w;
				this.sumStretch[i + 1] = this.sumStretch[i] + y;
				this.sumShrink[i + 1] = this.sumShrink[i] + z;
			}
		}

		List<BrokenLine> solve() {
			final int n = this.nodes.size();
			final List<Active> actives = new ArrayList<>();
			actives.add(new Active(-1, skipDiscardables(0), 0, 1, false, 0, null, null));

			boolean previousWasBox = false;
			for (int i = 0; i < n; ++i) {
				final BreakNode node = this.nodes.get(i);
				if (node instanceof BreakNode.Penalty penalty) {
					if (penalty.cost() <= BreakNode.Penalty.FORCE) {
						this.breakAt(actives, i, true);
					} else if (penalty.cost() < BreakNode.Penalty.INFINITY) {
						this.breakAt(actives, i, false);
					}
					previousWasBox = false;
					continue;
				}
				if (node instanceof BreakNode.Glue) {
					// A glue is a break opportunity only after a box (leading
					// glue on a line is discarded, as in greedy()'s x > 0).
					if (previousWasBox) {
						this.breakAt(actives, i, false);
					}
					previousWasBox = false;
					continue;
				}
				previousWasBox = true;
			}
			// End of stream: a mandatory break for every remaining candidate.
			this.breakAt(actives, n, true);

			// The single surviving active is the paragraph end; unwind.
			assert actives.size() == 1;
			final List<BrokenLine> lines = new ArrayList<>();
			for (Active a = actives.get(0); a != null && a.lineRecord != null; a = a.previous) {
				lines.add(a.lineRecord);
			}
			java.util.Collections.reverse(lines);
			return lines;
		}

		/**
		 * Evaluates a break at node index {@code b} ({@code == nodes.size()}
		 * for the end of the stream) against every active candidate. For a
		 * mandatory break the active list is replaced by the single best
		 * continuation; for an optional break the feasible continuations are
		 * added (dominated same-position candidates pruned).
		 */
		private void breakAt(final List<Active> actives, final int b, final boolean mandatory) {
			final int n = this.nodes.size();
			final BreakNode.Penalty penaltyAt = b < n && this.nodes.get(b) instanceof BreakNode.Penalty p ? p : null;
			final boolean paragraphEnd = b == n;

			Active best = null;
			double bestDemerits = Double.POSITIVE_INFINITY;
			// Non-mandatory pruning state: a candidate is kept only when its
			// total demerits are strictly below every earlier candidate of the
			// same (fitness, line-class) at this breakpoint — equivalent to the
			// old post-hoc dominance scan ("dominated when an earlier candidate
			// has <= total"), but checked before allocating the BrokenLine and
			// Active. On breakpoint-dense paragraphs (CJK justification) the old
			// order allocated hundreds of records per breakpoint only to discard
			// nearly all of them.
			final List<Active> addedAtB = mandatory ? null : new ArrayList<>();
			final double[] minByClass;
			final java.util.Map<Long, Double> minByClassWide;
			if (mandatory || this.easyLine > 255) {
				minByClass = null;
				minByClassWide = mandatory ? null : new java.util.HashMap<>();
			} else {
				minByClass = new double[(this.easyLine + 1) * 4];
				java.util.Arrays.fill(minByClass, Double.POSITIVE_INFINITY);
				minByClassWide = null;
			}
			// Knuth-Plass deactivation: a candidate whose line is already
			// overfull beyond its shrinkability can never become feasible at
			// any later breakpoint (natural width only grows), so it is
			// removed from the active list. Without this the active list
			// grows with every breakpoint and the solver degenerates to
			// quadratic-or-worse — observed as multi-minute hangs on long
			// CJK paragraphs where every character boundary is a breakpoint.
			final List<Active> deactivated = mandatory ? null : new ArrayList<>();
			for (final Active active : actives) {
				if (active.lineStart > b || (active.lineStart == b && !paragraphEnd && penaltyAt == null)) {
					// The candidate line would be empty (break at its own head
					// glue); not a real line.
					continue;
				}
				final double target = this.measure.width(active.line);
				double natural = this.sumWidth[b] - this.sumWidth[active.lineStart];
				if (penaltyAt != null) {
					natural += penaltyAt.width();
				}
				final double stretch = this.sumStretch[b] - this.sumStretch[active.lineStart];
				final double shrink = this.sumShrink[b] - this.sumShrink[active.lineStart];

				final boolean raggedLast = paragraphEnd && this.params.lastLine() == LastLinePolicy.RAGGED;
				double r;
				if (natural < target) {
					r = raggedLast ? 0 : stretch > 0 ? (target - natural) / stretch : Double.POSITIVE_INFINITY;
				} else if (natural > target) {
					r = shrink > 0 ? (target - natural) / shrink : Double.NEGATIVE_INFINITY;
				} else {
					r = 0;
				}

				final double badness = badness(r);
				final boolean fits = r >= -1 && badness <= this.params.tolerance();
				if (!mandatory && !fits) {
					// Overfull beyond shrink — and hopeless forever, because
					// the natural width only grows at later breakpoints. The
					// penalty width at b counts only when actually breaking
					// here, so the deactivation test conservatively uses the
					// width without it.
					final double naturalBase = this.sumWidth[b] - this.sumWidth[active.lineStart];
					if (naturalBase > target + shrink) {
						deactivated.add(active);
					}
					continue;
				}

				final int fitness = fitnessClass(r);
				final boolean flagged = penaltyAt != null && penaltyAt.flagged();
				double demerits = square(this.params.linePenalty() + Math.min(badness, BADNESS_INFINITE));
				if (penaltyAt != null && penaltyAt.cost() > 0) {
					demerits += square(penaltyAt.cost());
				} else if (penaltyAt != null && penaltyAt.cost() > BreakNode.Penalty.FORCE) {
					demerits -= square(penaltyAt.cost());
				}
				if (flagged && active.flagged) {
					demerits += this.params.doubleHyphenDemerits();
				}
				if (paragraphEnd && active.flagged) {
					demerits += this.params.finalHyphenDemerits();
				}
				if (Math.abs(fitness - active.fitness) > 1) {
					demerits += this.params.adjDemerits();
				}
				final double total = active.totalDemerits + demerits;

				if (!mandatory) {
					final int lineClass = Math.min(active.line + 1, this.easyLine);
					if (minByClass != null) {
						final int key = lineClass * 4 + fitness;
						if (total >= minByClass[key]) {
							continue;
						}
						minByClass[key] = total;
					} else {
						final Long key = ((long) lineClass << 2) | fitness;
						final Double min = minByClassWide.get(key);
						if (min != null && total >= min) {
							continue;
						}
						minByClassWide.put(key, total);
					}
				} else if (total >= bestDemerits) {
					continue;
				}

				final BreakKind kind = paragraphEnd ? BreakKind.PARAGRAPH_END
						: mandatory ? BreakKind.FORCED : BreakKind.NORMAL;
				final double appliedRatio = raggedLast && natural < target ? 0 : clampApplied(r);
				final BrokenLine lineRecord = new BrokenLine(active.lineStart == 0 ? 0 : active.breakIndex + 1,
						paragraphEnd ? n : b + 1, b, kind, appliedRatio, fitness);
				final Active continuation = new Active(b, skipDiscardables(b + 1), active.line + 1, fitness, flagged,
						total, active, lineRecord);
				if (mandatory) {
					bestDemerits = total;
					best = continuation;
				} else {
					addedAtB.add(continuation);
				}
			}

			if (mandatory) {
				if (best == null) {
					// No candidate fits (all overfull/unstretchable): fit-anyway
					// — keep the least-demerits candidate line regardless.
					best = this.fitAnyway(actives, b, penaltyAt, paragraphEnd);
				}
				actives.clear();
				actives.add(best);
				return;
			}
			if (!deactivated.isEmpty()) {
				if (deactivated.size() == actives.size() && addedAtB.isEmpty()) {
					// Every candidate just became hopeless with no feasible
					// break at b: force a break here from the least-bad one
					// (fit-anyway), so the solver always makes progress.
					final Active forced = this.fitAnyway(deactivated, b, penaltyAt, false);
					actives.clear();
					actives.add(forced);
					return;
				}
				// Identity-based single pass; List.removeAll is
				// O(|actives| * |deactivated|) which compounds with the hot
				// per-breakpoint loop on breakpoint-dense paragraphs.
				final java.util.Set<Active> dead = java.util.Collections
						.newSetFromMap(new java.util.IdentityHashMap<>());
				dead.addAll(deactivated);
				actives.removeIf(dead::contains);
			}
			actives.addAll(addedAtB);
		}

		/** Builds the least-bad continuation when nothing fits (fit-anyway). */
		private Active fitAnyway(final List<Active> actives, final int b, final BreakNode.Penalty penaltyAt,
				final boolean paragraphEnd) {
			final int n = this.nodes.size();
			Active best = null;
			double bestScore = Double.POSITIVE_INFINITY;
			for (final Active active : actives) {
				if (active.lineStart > b) {
					continue;
				}
				final double target = this.measure.width(active.line);
				double natural = this.sumWidth[b] - this.sumWidth[active.lineStart];
				if (penaltyAt != null) {
					natural += penaltyAt.width();
				}
				// Prefer the candidate whose line deviates least, then the
				// lower accumulated demerits. Deterministic.
				final double score = Math.abs(natural - target) + active.totalDemerits * 1e-9;
				if (score < bestScore) {
					bestScore = score;
					final BreakKind kind = paragraphEnd ? BreakKind.PARAGRAPH_END : BreakKind.FORCED;
					final BrokenLine lineRecord = new BrokenLine(active.lineStart == 0 ? 0 : active.breakIndex + 1,
							paragraphEnd ? n : b + 1, b, kind, 0, 1);
					best = new Active(b, skipDiscardables(b + 1), active.line + 1, 1, false,
							active.totalDemerits + square(BADNESS_INFINITE), active, lineRecord);
				}
			}
			assert best != null : "actives must not be empty";
			return best;
		}

		/** Skips discardable (glue) nodes at the head of a line. */
		private int skipDiscardables(final int start) {
			int i = start;
			final int n = this.nodes.size();
			while (i < n && this.nodes.get(i) instanceof BreakNode.Glue) {
				++i;
			}
			return i;
		}
	}

	/** TeX badness: approximately {@code 100|r|^3}, infinite when r < -1. */
	static double badness(final double r) {
		if (r < -1 || Double.isInfinite(r)) {
			return BADNESS_INFINITE;
		}
		return Math.min(BADNESS_INFINITE, 100 * Math.abs(r) * Math.abs(r) * Math.abs(r));
	}

	/** TeX fitness classes: 0=tight, 1=decent, 2=loose, 3=very loose. */
	static int fitnessClass(final double r) {
		if (r < -0.5) {
			return 0;
		}
		if (r <= 0.5) {
			return 1;
		}
		if (r <= 1) {
			return 2;
		}
		return 3;
	}

	/** The ratio the consumer applies: overfull lines shrink at most fully. */
	private static double clampApplied(final double r) {
		if (Double.isInfinite(r)) {
			return r > 0 ? 0 : -1;
		}
		return Math.max(r, -1);
	}

	private static double square(final double v) {
		return v * v;
	}
}
