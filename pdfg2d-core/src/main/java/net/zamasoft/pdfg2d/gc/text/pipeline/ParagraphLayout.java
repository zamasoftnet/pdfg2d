package net.zamasoft.pdfg2d.gc.text.pipeline;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.text.pipeline.PositionedLine.PositionedRun;

/**
 * The paragraph layout pipeline facade: itemize (bidi) → shape → build break
 * nodes → break into lines → position (with per-line bidi reordering and
 * optional justification). Produces {@link PositionedLine}s ready to draw.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class ParagraphLayout {

	private final Shaper shaper;

	/** Justify (spread glue to fill the measure) all but the last line. */
	private boolean justify = false;

	/**
	 * Creates a layout engine backed by the given font manager.
	 *
	 * @param fontManager the font manager
	 */
	public ParagraphLayout(final FontManager fontManager) {
		this.shaper = new Shaper(fontManager);
	}

	/**
	 * Enables or disables justification.
	 *
	 * @param justify whether to justify
	 * @return this layout, for chaining
	 */
	public ParagraphLayout setJustify(final boolean justify) {
		this.justify = justify;
		return this;
	}

	/** A break node paired with the bidi level of its material. */
	private record LeveledNode(BreakNode node, byte level) {
	}

	/**
	 * Lays out a paragraph to the given measure.
	 *
	 * @param paragraph the paragraph
	 * @param width     the line width (measure)
	 * @return the positioned lines, top to bottom
	 */
	public List<PositionedLine> layout(final Paragraph paragraph, final double width) {
		final var items = Itemizer.itemize(paragraph);
		final var nodes = new ArrayList<LeveledNode>();
		final var plain = new ArrayList<BreakNode>();

		for (final var item : items) {
			for (final var run : this.shaper.shape(paragraph.text(), item)) {
				for (var g = 0; g < run.length; ++g) {
					final var cluster = run.clusters[g];
					final var ch = paragraph.text()[cluster];
					final var w = run.xAdvances[g];
					if (ch == ' ' || ch == '\t') {
						// Breakable, stretchable space.
						final var glue = new BreakNode.Glue(w, w * 0.5, w * 0.3);
						nodes.add(new LeveledNode(glue, item.bidiLevel()));
						plain.add(glue);
					} else {
						final var box = new BreakNode.Box(w, run, g, g + 1);
						// Allow a break before an ideograph (simple CJK rule).
						if (isIdeograph(ch) && !plain.isEmpty()
								&& plain.get(plain.size() - 1) instanceof BreakNode.Box) {
							final var brk = new BreakNode.Penalty(0, 0, false, null);
							nodes.add(new LeveledNode(brk, item.bidiLevel()));
							plain.add(brk);
						}
						nodes.add(new LeveledNode(box, item.bidiLevel()));
						plain.add(box);
					}
				}
			}
		}

		final var lines = LineBreaker.greedy(plain, width);
		final var result = new ArrayList<PositionedLine>();
		for (var li = 0; li < lines.size(); ++li) {
			final var line = lines.get(li);
			result.add(this.finishLine(nodes, line, width, li == lines.size() - 1));
		}
		return result;
	}

	/** Positions one line: reorder by bidi, place left to right, justify. */
	private PositionedLine finishLine(final List<LeveledNode> nodes, final LineBreaker.Line line,
			final double width, final boolean lastLine) {
		// Collect boxes, trimming glue at both ends.
		var begin = line.begin();
		var end = line.end();
		while (begin < end && !(nodes.get(begin).node() instanceof BreakNode.Box)) {
			++begin;
		}
		while (end > begin && !(nodes.get(end - 1).node() instanceof BreakNode.Box)) {
			--end;
		}

		final var boxes = new ArrayList<BreakNode.Box>();
		final var levels = new ArrayList<Byte>();
		var natural = 0.0;
		var glueCount = 0;
		var glueWidth = 0.0;
		for (var i = begin; i < end; ++i) {
			final var ln = nodes.get(i);
			if (ln.node() instanceof BreakNode.Box box) {
				boxes.add(box);
				levels.add(ln.level());
				natural += box.width();
			} else if (ln.node() instanceof BreakNode.Glue glue) {
				boxes.add(null);
				levels.add(ln.level());
				natural += glue.width();
				glueWidth += glue.width();
				++glueCount;
			}
		}

		final byte[] lvl = new byte[levels.size()];
		for (var i = 0; i < lvl.length; ++i) {
			lvl[i] = levels.get(i);
		}
		final var order = Itemizer.reorderVisual(lvl);

		// Justification: spread the shortfall across glue.
		var extraPerGlue = 0.0;
		if (this.justify && !lastLine && glueCount > 0 && natural < width) {
			extraPerGlue = (width - natural) / glueCount;
		}

		final var runs = new ArrayList<PositionedRun>();
		var x = 0.0;
		var ascent = 0.0;
		var descent = 0.0;
		for (final var idx : order) {
			final var box = boxes.get(idx);
			final var srcIndex = begin + idx;
			final var srcNode = nodes.get(srcIndex).node();
			if (box == null) {
				final var glue = (BreakNode.Glue) srcNode;
				x += glue.width() + extraPerGlue;
				continue;
			}
			runs.add(new PositionedRun(box.run(), box.glyphBegin(), box.glyphEnd(), x, 0));
			ascent = Math.max(ascent, box.run().fontMetrics.getAscent());
			descent = Math.max(descent, box.run().fontMetrics.getDescent());
			x += box.width();
		}
		return new PositionedLine(runs, x, ascent, descent);
	}

	private static boolean isIdeograph(final char c) {
		return (c >= 0x4E00 && c <= 0x9FFF) // CJK Unified Ideographs
				|| (c >= 0x3040 && c <= 0x30FF) // Hiragana + Katakana
				|| (c >= 0xFF00 && c <= 0xFFEF); // Halfwidth/Fullwidth forms
	}
}
