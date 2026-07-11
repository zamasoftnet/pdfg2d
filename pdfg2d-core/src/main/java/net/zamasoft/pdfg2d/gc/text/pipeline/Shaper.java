package net.zamasoft.pdfg2d.gc.text.pipeline;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;

/**
 * Shapes an {@link Item} into one or more {@link GlyphRun}s. Character-to-glyph
 * mapping, font fallback and cluster handling are delegated to the existing
 * {@link TextShaper} of the {@link FontManager}, so glyph selection matches the
 * legacy pipeline exactly; the results are collected into the new
 * {@code GlyphRun} model (with x/y advances and offsets) that downstream stages
 * consume.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class Shaper {

	private final FontManager fontManager;

	/**
	 * Creates a shaper backed by the given font manager.
	 *
	 * @param fontManager the font manager providing metrics and glyph mapping
	 */
	public Shaper(final FontManager fontManager) {
		this.fontManager = fontManager;
	}

	/**
	 * Shapes the characters of one item.
	 *
	 * @param text the paragraph text (logical order)
	 * @param item the item to shape
	 * @return the glyph runs, in logical order
	 */
	public List<GlyphRun> shape(final char[] text, final Item item) {
		final var collector = new Collector(item);
		final TextShaper shaper = this.fontManager.getTextShaper();
		shaper.setGlyphHandler(collector);
		shaper.fontStyle(item.style());
		shaper.characters(item.begin(), text, item.begin(), item.end() - item.begin());
		shaper.close();
		for (final var run : collector.runs) {
			run.text = text;
		}
		return collector.runs;
	}

	/** Collects the push-model glyph events into {@link GlyphRun}s. */
	private static final class Collector implements GlyphHandler {
		private final Item item;
		private final List<GlyphRun> runs = new ArrayList<>();
		private GlyphRun current;
		private FontMetrics metrics;

		/**
		 * Running source-character pointer. The legacy shaper reports glyphs
		 * one behind with a misaligned {@code charOffset}, but passes the exact
		 * source chars and their count; advancing this pointer by each glyph's
		 * cluster length yields correct clusters (including ligatures and
		 * surrogate pairs).
		 */
		private int cluster;

		Collector(final Item item) {
			this.item = item;
			this.cluster = item.begin();
		}

		@Override
		public void startTextRun(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
			this.metrics = fontMetrics;
			this.current = new GlyphRun(this.item, fontMetrics, 16);
		}

		@Override
		public void glyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
			// Advance includes pair kerning against the previous glyph, matching
			// the legacy Text model.
			var advance = this.metrics.getAdvance(gid);
			if (this.current.length > 0) {
				advance -= this.metrics.getKerning(this.current.gids[this.current.length - 1], gid);
			}
			this.current.add(gid, this.cluster, advance, 0);
			this.cluster += clen;
		}

		@Override
		public void control(final TextControl control) {
			// Inline controls (tabs, breaks) are handled by the layout stage,
			// not by shaping; ignore here.
		}

		@Override
		public void endTextRun() {
			if (this.current != null && this.current.length > 0) {
				this.runs.add(this.current);
			}
			this.current = null;
		}

		@Override
		public void flush() {
			// no-op: break opportunities are recomputed by the line breaker
		}

		@Override
		public void close() {
			this.endTextRun();
		}
	}
}
