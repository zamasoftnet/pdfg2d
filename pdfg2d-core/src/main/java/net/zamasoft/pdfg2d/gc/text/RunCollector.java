package net.zamasoft.pdfg2d.gc.text;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * Collects shaped glyphs into packed {@link TextImpl} runs.
 *
 * <p>
 * This is the canonical form of the "self-contained shaping" idiom: drive a
 * {@link TextShaper} over a plain string and collect the resulting runs
 * without any layout context. Controls are ignored (callers strip control
 * characters beforehand or accept their loss by design).
 * </p>
 *
 * <p>
 * Extracted from three identical anonymous collectors in foliojet
 * (ruby units, footnote labels, {@code leader()} patterns) on 2026-08-01.
 * </p>
 *
 * @since 1.3
 */
public final class RunCollector implements GlyphHandler {
	private final List<TextImpl> runs = new ArrayList<>();

	private TextImpl current = null;

	@Override
	public void startTextRun(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.current = new TextImpl(charOffset, fontStyle, fontMetrics);
	}

	@Override
	public void glyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
		this.current.appendGlyph(ch, coff, clen, gid);
	}

	@Override
	public void endTextRun() {
		if (this.current.getGlyphCount() > 0) {
			this.current.pack();
			this.runs.add(this.current);
		}
		this.current = null;
	}

	@Override
	public void control(final TextControl control) {
		// self-contained shaping has no layout context for controls
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}

	/**
	 * Returns the collected runs.
	 *
	 * @return the packed runs, in shaping order
	 */
	public TextImpl[] runs() {
		return this.runs.toArray(new TextImpl[0]);
	}

	/**
	 * Shapes a plain string into packed runs with no layout context.
	 *
	 * @param fontManager the font manager providing the shaper
	 * @param fontStyle   the font style to shape with
	 * @param text        the text (may be empty)
	 * @param charOffset  the source character offset of the first character
	 *                    ({@code -1} for generated content)
	 * @return the packed runs, in shaping order
	 */
	public static TextImpl[] shape(final FontManager fontManager, final FontStyle fontStyle, final String text,
			final int charOffset) {
		if (text.isEmpty()) {
			return new TextImpl[0];
		}
		final RunCollector collector = new RunCollector();
		final TextShaper shaper = fontManager.getTextShaper();
		shaper.setGlyphHandler(collector);
		shaper.fontStyle(fontStyle);
		final char[] ch = text.toCharArray();
		shaper.characters(charOffset, ch, 0, ch.length);
		shaper.close();
		return collector.runs();
	}
}
