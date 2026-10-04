package net.zamasoft.pdfg2d.gc.text.pipeline;

/**
 * Reorders resolved bidi embedding levels into visual order (UAX #9 rule L2).
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class Itemizer {

	private Itemizer() {
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
