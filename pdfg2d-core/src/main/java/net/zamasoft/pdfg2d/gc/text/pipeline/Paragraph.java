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
public record Paragraph(char[] text, List<StyleSpan> spans) {

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
