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

	/** Optional hyphenator; when set, words get soft (flagged) breakpoints. */
	private Hyphenator hyphenator = null;

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

	/**
	 * Sets a hyphenator to insert soft breakpoints within words.
	 *
	 * @param hyphenator the hyphenator, or {@code null} to disable
	 * @return this layout, for chaining
	 */
	public ParagraphLayout setHyphenator(final Hyphenator hyphenator) {
		this.hyphenator = hyphenator;
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
				// Buffer letters of the current word so it can be hyphenated as
				// a unit once its extent is known.
				final var word = new StringBuilder();
				final var wordGlyphs = new java.util.ArrayList<Integer>();
				for (var g = 0; g <= run.length; ++g) {
					final var ch = (g < run.length) ? paragraph.text()[run.clusters[g]] : ' ';
					final var isLetter = g < run.length && Character.isLetter(ch) && !isIdeograph(ch);
					if (isLetter) {
						word.append(ch);
						wordGlyphs.add(g);
						continue;
					}
					// Word boundary: emit the buffered word (with hyphenation).
					if (!word.isEmpty()) {
						this.emitWord(run, word.toString(), wordGlyphs, item, nodes, plain);
						word.setLength(0);
						wordGlyphs.clear();
					}
					if (g == run.length) {
						break;
					}
					final var w = run.xAdvances[g];
					if (ch == ' ' || ch == '\t') {
						final var glue = new BreakNode.Glue(w, w * 0.5, w * 0.3);
						nodes.add(new LeveledNode(glue, item.bidiLevel()));
						plain.add(glue);
					} else {
						if (isIdeograph(ch) && !plain.isEmpty()
								&& plain.get(plain.size() - 1) instanceof BreakNode.Box) {
							final var brk = new BreakNode.Penalty(0, 0, false, null);
							nodes.add(new LeveledNode(brk, item.bidiLevel()));
							plain.add(brk);
						}
						final var box = new BreakNode.Box(w, run, g, g + 1);
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

	/**
	 * Emits a word's boxes, inserting flagged hyphenation penalties at the
	 * soft break offsets when a hyphenator is configured.
	 */
	private void emitWord(final GlyphRun run, final String word, final List<Integer> wordGlyphs, final Item item,
			final List<LeveledNode> nodes, final List<BreakNode> plain) {
		int[] breaks = (this.hyphenator != null && word.length() >= 5)
				? this.hyphenator.hyphenate(word)
				: NO_BREAKS;
		var bi = 0;
		for (var wg = 0; wg < wordGlyphs.size(); ++wg) {
			// A hyphen may be inserted before the letter at offset wg.
			if (bi < breaks.length && breaks[bi] == wg && wg > 0) {
				final BreakNode penalty = this.hyphenPenalty(run, item);
				nodes.add(new LeveledNode(penalty, item.bidiLevel()));
				plain.add(penalty);
				++bi;
			}
			final var g = wordGlyphs.get(wg);
			final var box = new BreakNode.Box(run.xAdvances[g], run, g, g + 1);
			nodes.add(new LeveledNode(box, item.bidiLevel()));
			plain.add(box);
		}
	}

	private static final int[] NO_BREAKS = new int[0];

	/** A flagged penalty whose insertion is a hyphen shaped in the run's font. */
	private BreakNode.Penalty hyphenPenalty(final GlyphRun run, final Item item) {
		final var hyphen = this.hyphenGlyph(run, item);
		final var width = (hyphen != null) ? hyphen.advance() : 0;
		// A moderate cost: prefer whole-word wrapping, allow hyphenation.
		return new BreakNode.Penalty(width, 50, true, hyphen);
	}

	/** Shapes a hyphen in the given run's font, cached per font metrics. */
	private GlyphRun hyphenGlyph(final GlyphRun run, final Item item) {
		final var runs = this.shaper.shape(new char[] { '-' }, new Item(0, 1, item.bidiLevel(), item.style()));
		return runs.isEmpty() ? null : runs.get(0);
	}

	/** Positions one line: reorder by bidi, place left to right, justify. */
	private PositionedLine finishLine(final List<LeveledNode> nodes, final LineBreaker.Line line,
			final double width, final boolean lastLine) {
		// If this line ends at a flagged (hyphenation) penalty, its insertion
		// glyph must be drawn at the line end.
		GlyphRun hyphen = null;
		if (line.end() - 1 >= line.begin() && nodes.get(line.end() - 1).node() instanceof BreakNode.Penalty p
				&& p.flagged()) {
			hyphen = p.insertion();
		}

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
		// Draw the hyphen at the line end (visual right for LTR text).
		if (hyphen != null && hyphen.length > 0) {
			runs.add(new PositionedRun(hyphen, 0, hyphen.length, x, 0));
			ascent = Math.max(ascent, hyphen.fontMetrics.getAscent());
			descent = Math.max(descent, hyphen.fontMetrics.getDescent());
			x += hyphen.advance();
		}
		return new PositionedLine(runs, x, ascent, descent);
	}

	private static boolean isIdeograph(final char c) {
		return (c >= 0x4E00 && c <= 0x9FFF) // CJK Unified Ideographs
				|| (c >= 0x3040 && c <= 0x30FF) // Hiragana + Katakana
				|| (c >= 0xFF00 && c <= 0xFFEF); // Halfwidth/Fullwidth forms
	}
}
