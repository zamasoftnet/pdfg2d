package net.zamasoft.pdfg2d.gc.text.pipeline;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/**
 * Draws {@link PositionedLine}s through the existing {@link GC#drawText} path,
 * so the new pipeline reuses the tested glyph-emission and font-embedding code.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class PipelineTextDrawer {

	private PipelineTextDrawer() {
	}

	/**
	 * Draws one positioned line with its baseline at {@code (x, y)}.
	 *
	 * @param gc   the graphics context
	 * @param line the positioned line
	 * @param x    the line origin x
	 * @param y    the baseline y
	 * @throws GraphicsException if drawing fails
	 */
	public static void draw(final GC gc, final PositionedLine line, final double x, final double y)
			throws GraphicsException {
		for (final var pr : line.runs()) {
			final var run = pr.run();
			final var text = new TextImpl(run.clusters[pr.glyphBegin()], run.getFontStyle(), run.fontMetrics);
			for (var g = pr.glyphBegin(); g < pr.glyphEnd(); ++g) {
				final var cluster = run.clusters[g];
				// Stage 1 shaping is 1:1; a cluster maps to a single char (or a
				// surrogate pair, handled by the char count below).
				final var clen = clusterLength(run, g);
				text.appendGlyph(run.text, cluster, clen, run.gids[g]);
			}
			gc.drawText(text, x + pr.x(), y);
		}
	}

	/** Number of source chars a glyph consumes (1, or 2 for a surrogate pair). */
	private static byte clusterLength(final GlyphRun run, final int g) {
		final var cluster = run.clusters[g];
		if (cluster + 1 < run.text.length && Character.isHighSurrogate(run.text[cluster])
				&& Character.isLowSurrogate(run.text[cluster + 1])) {
			return 2;
		}
		return 1;
	}
}
