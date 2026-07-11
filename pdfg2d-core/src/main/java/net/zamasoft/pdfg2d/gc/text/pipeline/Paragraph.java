package net.zamasoft.pdfg2d.gc.text.pipeline;

import java.util.List;

import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * A paragraph of text in logical order: the characters, the style spans over
 * them, and any inline objects. A paragraph is the unit of layout — bounded in
 * memory yet large enough to contain every paragraph-scoped algorithm (bidi
 * reordering, optimal line breaking, contextual shaping).
 *
 * @param text  the paragraph characters in logical order
 * @param spans the style spans covering {@code text}; must tile it with no gaps
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record Paragraph(char[] text, List<StyleSpan> spans, List<RubySpan> rubySpans) {

	/**
	 * Creates a paragraph with no ruby annotations.
	 *
	 * @param text  the paragraph characters
	 * @param spans the style spans
	 */
	public Paragraph(final char[] text, final List<StyleSpan> spans) {
		this(text, spans, List.of());
	}

	/**
	 * A run of characters with a single font style.
	 *
	 * @param begin the first character index (inclusive)
	 * @param end   the end character index (exclusive)
	 * @param style the font style over {@code [begin, end)}
	 */
	public record StyleSpan(int begin, int end, FontStyle style) {
	}

	/**
	 * A ruby annotation: small text set over (or under) a base character range.
	 * The base is identified by its character range in {@link #text()}; the
	 * annotation text is independent and set in its own style.
	 *
	 * @param baseBegin  the first base character index (inclusive)
	 * @param baseEnd    the end base character index (exclusive)
	 * @param ruby       the annotation characters
	 * @param rubyStyle  the annotation font style
	 * @param over       whether the annotation sits above ({@code true}) the base
	 */
	public record RubySpan(int baseBegin, int baseEnd, char[] ruby, FontStyle rubyStyle, boolean over) {

		/** Returns the ruby span covering the given base index, or null. */
		static RubySpan covering(final List<RubySpan> spans, final int baseIndex) {
			for (final var span : spans) {
				if (baseIndex >= span.baseBegin() && baseIndex < span.baseEnd()) {
					return span;
				}
			}
			return null;
		}
	}

	/**
	 * Returns the style covering the given character index.
	 *
	 * @param charIndex the character index
	 * @return the covering style
	 */
	public FontStyle styleAt(final int charIndex) {
		for (final var span : this.spans) {
			if (charIndex >= span.begin() && charIndex < span.end()) {
				return span.style();
			}
		}
		throw new IndexOutOfBoundsException("No style span covers index " + charIndex);
	}
}
