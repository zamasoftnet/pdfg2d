package net.zamasoft.pdfg2d.gc.text.pipeline;

import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * A maximal run of characters that share a bidi embedding level and a font
 * style — the unit handed to the shaper. Script and font resolution are
 * carried implicitly by the style; the bidi level drives per-line reordering.
 *
 * @param begin     the first character index (inclusive, logical order)
 * @param end       the end character index (exclusive)
 * @param bidiLevel the UAX #9 embedding level (even = LTR, odd = RTL)
 * @param style     the font style over {@code [begin, end)}
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record Item(int begin, int end, byte bidiLevel, FontStyle style) {
}
