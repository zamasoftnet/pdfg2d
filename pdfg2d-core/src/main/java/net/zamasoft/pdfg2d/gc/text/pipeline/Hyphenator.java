package net.zamasoft.pdfg2d.gc.text.pipeline;

import java.util.HashMap;
import java.util.Map;

/**
 * Liang's hyphenation algorithm (as used by TeX): competing patterns assign
 * odd/even priorities between letters; an odd value permits a hyphen. Patterns
 * are given in the classic {@code .tex} form (e.g. {@code "hy3ph"},
 * {@code ".ph4"}), where digits are inter-letter priorities and {@code .}
 * marks a word boundary.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class Hyphenator {

	private final Map<String, byte[]> patterns = new HashMap<>();

	/** Minimum letters to keep before the first and after the last break. */
	private int leftMin = 2;
	private int rightMin = 3;

	/**
	 * Builds a hyphenator from {@code .tex}-format patterns.
	 *
	 * @param patternStrings the patterns (letters interleaved with digits, and
	 *                       {@code .} for word boundaries)
	 */
	public Hyphenator(final String... patternStrings) {
		for (final var pattern : patternStrings) {
			this.addPattern(pattern);
		}
	}

	/**
	 * Sets the minimum letters kept before the first break and after the last.
	 *
	 * @param leftMin  letters before the first break
	 * @param rightMin letters after the last break
	 * @return this hyphenator
	 */
	public Hyphenator setMins(final int leftMin, final int rightMin) {
		this.leftMin = leftMin;
		this.rightMin = rightMin;
		return this;
	}

	private void addPattern(final String pattern) {
		final var letters = new StringBuilder();
		// values[i] is the priority before letters.charAt(i); one extra at end.
		final var values = new byte[pattern.length() + 1];
		var vi = 0;
		for (var i = 0; i < pattern.length(); ++i) {
			final var c = pattern.charAt(i);
			if (c >= '0' && c <= '9') {
				values[vi] = (byte) (c - '0');
			} else {
				letters.append(c);
				++vi;
			}
		}
		final var key = letters.toString();
		this.patterns.put(key, java.util.Arrays.copyOf(values, key.length() + 1));
	}

	/**
	 * Returns the break offsets within {@code word} (indices before which a
	 * hyphen may be inserted), honoring the left/right minimums.
	 *
	 * @param word the word (letters only)
	 * @return ascending break offsets in {@code [leftMin, len-rightMin]}
	 */
	public int[] hyphenate(final String word) {
		final var lower = ("." + word.toLowerCase() + ".");
		final var n = lower.length();
		// Priority between original letters; index j is between word[j-1],word[j].
		final var priorities = new int[word.length() + 1];
		for (var i = 0; i < n; ++i) {
			for (var j = i + 1; j <= n; ++j) {
				final var sub = lower.substring(i, j);
				final var values = this.patterns.get(sub);
				if (values == null) {
					continue;
				}
				for (var k = 0; k < values.length; ++k) {
					// Position in the (dotted) string of this inter-letter slot.
					final var pos = i + k;
					// Map to an offset in the original word: pos counts the
					// leading dot, so the break is before word[pos-1].
					final var off = pos - 1;
					if (off >= 0 && off <= word.length() && values[k] > priorities[Math.min(off, word.length())]) {
						priorities[Math.min(off, word.length())] = values[k];
					}
				}
			}
		}
		final var breaks = new java.util.ArrayList<Integer>();
		for (var off = this.leftMin; off <= word.length() - this.rightMin; ++off) {
			if ((priorities[off] & 1) != 0) {
				breaks.add(off);
			}
		}
		final var out = new int[breaks.size()];
		for (var i = 0; i < out.length; ++i) {
			out[i] = breaks.get(i);
		}
		return out;
	}

	/**
	 * A small built-in English pattern set (a subset of hyphen-en). Enough to
	 * hyphenate common words; production use should load a full pattern file.
	 *
	 * @return an English hyphenator
	 */
	public static Hyphenator english() {
		return new Hyphenator(
				".ach4", ".ad4der", ".af1t", ".al3t", ".am5at", ".an5c", ".ang4", ".ani5m",
				"a1b", "ab3i", "2a1c", "ach4", "a1d", "1al", "al3i", "a1m", "an4c", "an5d",
				"1an", "a1p", "a1r", "ar3d", "a1t", "at5t", "1au", "2a1v", "1aw", "1ax", "1ba",
				"1be", "be5r", "1bi", "1bl", "b2l", "1bo", "1br", "b2r", "1bu", "1ca", "1ce",
				"1ch", "c2h", "1ci", "1cl", "c2l", "1co", "1cr", "c2r", "1cu", "1cy", "1da",
				"1de", "de2s", "1di", "di4s", "1do", "1dr", "d2r", "1du", "1e2", "e1a", "1ea",
				" e5act", "ea4t", "e1b", "e3ch", "ec4t", "e1d", "e1f", "eg4", "e1l", "el5d",
				"e1m", "e1n", "en5t", "e1o", "e1p", "e1r", "er3s", "e1s", "es4c", "e1t", "e1v",
				"e1w", "e1x", "1ex", "1fa", "1fe", "1fi", "1fl", "f2l", "1fo", "1fr", "f2r",
				"1fu", "1ga", "1ge", "1gh", "g2h", "1gi", "1gl", "g2l", "1go", "1gr", "g2r",
				"1gu", "1ha", "1he", "1hi", "1ho", "1hu", "1hy", "1ia", "i1b", "i1c", "1id",
				"i1d", "ig4", "i1f", "i1g", "il4", "1im", "i1m", "1in", "i1n", "1io", "i1o",
				"i1p", "i1r", "i1s", "is4c", "i1t", "i1u", "i1v", "iv3i", "i1z", "1ja", "1je",
				"1jo", "1ju", "1ka", "1ke", "1ki", "1la", "1le", "1li", "1lo", "1lu", "1ly",
				"1ma", "1me", "3men", "1mi", "1mo", "1mu", "1na", "1ne", "1ni", "1no", "1nu",
				"1ny", "1oa", "o1b", "1ob", "o1c", "oc5t", "o1d", "o1e", "o1f", "o1g", "oi3",
				"o1i", "o1l", "1om", "o1m", "1on", "o1n", "o1o", "o1p", "o1r", "or5m", "o1s",
				"o1t", "ot5t", "o1u", "o1v", "1pa", "1pe", "1ph", "p2h", "1pi", "1pl", "p2l",
				"1po", "1pr", "p2r", "1pu", "1qu", "q2u", "1ra", "1re", "re4s", "1ri", "1ro",
				"1ru", "1sa", "1sc", "s2c", "1se", "1sh", "s2h", "1si", "1sl", "s2l", "1so",
				"1sp", "s2p", "1st", "s2t", "1su", "1sw", "1sy", "1ta", "1te", "1th", "t2h",
				"1ti", "1to", "1tr", "t2r", "1tu", "1ty", "1ua", "u1b", "u1c", "u1d", "u1e",
				"u1f", "u1g", "u1i", "u1l", "u1m", "u1n", "un1", "u1p", "u1r", "u1s", "us5t",
				"u1t", "u1v", "1va", "1ve", "1vi", "1vo", "1wa", "1we", "1wh", "1wi", "1wo",
				"1ya", "1ye", "1yo", "1za", "1ze", "1zi", "1zo", "tion5", "4tion", "na4t5i",
				"hy3phen", "phen5a", "com5pu", "put5er", "sys5tem", "ex1am", "am5ple",
				"al5go", "rithm5", "lay4o5ut", "typo5graph");
	}
}
