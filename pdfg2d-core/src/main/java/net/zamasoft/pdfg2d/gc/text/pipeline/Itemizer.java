package net.zamasoft.pdfg2d.gc.text.pipeline;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;

/**
 * Splits a {@link Paragraph} into {@link Item}s: maximal runs sharing a bidi
 * embedding level and a font style. Bidi levels are resolved with
 * {@link java.text.Bidi} (UAX #9), so mixed LTR/RTL text is handled without an
 * external dependency.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class Itemizer {

	private Itemizer() {
	}

	/**
	 * Itemizes the paragraph in logical order.
	 *
	 * @param paragraph the paragraph
	 * @return the items in logical order
	 */
	public static List<Item> itemize(final Paragraph paragraph) {
		final var text = paragraph.text();
		final var items = new ArrayList<Item>();
		if (text.length == 0) {
			return items;
		}

		// The paragraph's base direction comes from the first span's style.
		final var baseRtl = paragraph.spans().get(0).style().getDirection() == Direction.RTL;
		final byte[] levels = resolveLevels(text, baseRtl);

		var begin = 0;
		while (begin < text.length) {
			final var level = levels[begin];
			final var style = paragraph.styleAt(begin);
			var end = begin + 1;
			// Extend while both the level and the style span are unchanged.
			while (end < text.length && levels[end] == level && paragraph.styleAt(end) == style) {
				++end;
			}
			items.add(new Item(begin, end, level, style));
			begin = end;
		}
		return items;
	}

	/** Resolves the per-character bidi embedding levels. */
	private static byte[] resolveLevels(final char[] text, final boolean baseRtl) {
		final byte[] levels = new byte[text.length];
		final var bidi = new Bidi(new String(text),
				baseRtl ? Bidi.DIRECTION_RIGHT_TO_LEFT : Bidi.DIRECTION_LEFT_TO_RIGHT);
		if (bidi.isLeftToRight()) {
			return levels; // all zero
		}
		for (var run = 0; run < bidi.getRunCount(); ++run) {
			final var lvl = (byte) bidi.getRunLevel(run);
			for (var i = bidi.getRunStart(run); i < bidi.getRunLimit(run); ++i) {
				levels[i] = lvl;
			}
		}
		return levels;
	}

	/**
	 * Reorders a sequence of same-height items on one visual line from logical
	 * to visual order per UAX #9 rule L2 (reverse each maximal run of level
	 * &gt;= L, for L from the highest level down to the lowest odd level).
	 *
	 * @param levels the embedding level of each element, in logical order
	 * @return the element indices in visual (left-to-right) order
	 */
	public static int[] reorderVisual(final byte[] levels) {
		final var n = levels.length;
		final var order = new int[n];
		for (var i = 0; i < n; ++i) {
			order[i] = i;
		}
		if (n == 0) {
			return order;
		}
		byte highest = 0;
		byte lowestOdd = Byte.MAX_VALUE;
		for (final var l : levels) {
			if (l > highest) {
				highest = l;
			}
			if ((l & 1) != 0 && l < lowestOdd) {
				lowestOdd = l;
			}
		}
		for (var level = highest; level >= lowestOdd; --level) {
			var i = 0;
			while (i < n) {
				if (levels[order[i]] >= level) {
					var j = i;
					while (j < n && levels[order[j]] >= level) {
						++j;
					}
					// Reverse order[i..j)
					for (int a = i, b = j - 1; a < b; ++a, --b) {
						final var t = order[a];
						order[a] = order[b];
						order[b] = t;
					}
					i = j;
				} else {
					++i;
				}
			}
		}
		return order;
	}
}
