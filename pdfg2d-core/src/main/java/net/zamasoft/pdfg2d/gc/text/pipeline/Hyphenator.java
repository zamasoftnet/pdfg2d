package net.zamasoft.pdfg2d.gc.text.pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Liang's hyphenation algorithm (as used by TeX): competing patterns assign
 * odd/even priorities between letters; an odd value permits a hyphen. Patterns
 * are given in the classic {@code .tex} form (e.g. {@code "hy3ph"},
 * {@code ".ph4"}), where digits are inter-letter priorities and {@code .}
 * marks a word boundary.
 *
 * <p>
 * A pattern set only works as a whole: an odd value permits a break only
 * because even values in longer patterns forbid one everywhere the break would
 * be wrong. A subset is therefore not a partial answer but a wrong one, so
 * {@link #english()} loads the complete published pattern file rather than a
 * hand-picked selection.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class Hyphenator {

	private final Map<String, byte[]> patterns = new HashMap<>();

	/**
	 * Words whose break points are listed explicitly. An entry overrides the
	 * patterns entirely, and an empty array means the word is never broken.
	 */
	private final Map<String, int[]> exceptions = new HashMap<>();

	/** Length of the longest pattern key, bounding the match window. */
	private int maxPatternLength = 0;

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
		this.patterns.put(key, Arrays.copyOf(values, key.length() + 1));
		if (key.length() > this.maxPatternLength) {
			this.maxPatternLength = key.length();
		}
	}

	/**
	 * Records a word whose breaks are given explicitly, in the
	 * {@code \hyphenation} form ({@code "as-so-ciate"}). A word listed with no
	 * hyphen at all is never broken.
	 */
	private void addException(final String spelled) {
		final var word = new StringBuilder();
		final var offsets = new ArrayList<Integer>();
		for (var i = 0; i < spelled.length(); ++i) {
			final var c = spelled.charAt(i);
			if (c == '-') {
				offsets.add(word.length());
			} else {
				word.append(c);
			}
		}
		final var breaks = new int[offsets.size()];
		for (var i = 0; i < breaks.length; ++i) {
			breaks[i] = offsets.get(i);
		}
		this.exceptions.put(word.toString().toLowerCase(), breaks);
	}

	private static final int[] NO_BREAKS = new int[0];

	/**
	 * Returns the break offsets within {@code word} (indices before which a
	 * hyphen may be inserted), honoring the left/right minimums.
	 *
	 * @param word the word (letters only)
	 * @return ascending break offsets in {@code [leftMin, len-rightMin]}
	 */
	public int[] hyphenate(final String word) {
		final var lower = word.toLowerCase();
		final var listed = this.exceptions.get(lower);
		if (listed != null) {
			return listed.length == 0 ? NO_BREAKS : listed.clone();
		}
		final var dotted = "." + lower + ".";
		final var n = dotted.length();
		// Priority between original letters; index j is between word[j-1],word[j].
		final var priorities = new int[word.length() + 1];
		for (var i = 0; i < n; ++i) {
			// No pattern is longer than maxPatternLength, so there is nothing to
			// gain from asking the map about a longer substring.
			final var limit = Math.min(n, i + this.maxPatternLength);
			for (var j = i + 1; j <= limit; ++j) {
				final var values = this.patterns.get(dotted.substring(i, j));
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
		final var breaks = new ArrayList<Integer>();
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
	 * Reads a hyphenator from a TeX pattern file: the tokens inside
	 * {@code \patterns{...}} become patterns and those inside
	 * {@code \hyphenation{...}} become explicit exceptions. Everything from
	 * {@code %} to the end of a line is a comment.
	 */
	static Hyphenator readTex(final InputStream in) throws IOException {
		final var hyphenator = new Hyphenator();
		// 0 = outside any group, 1 = inside \patterns, 2 = inside \hyphenation.
		var group = 0;
		try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
			String line;
			while ((line = reader.readLine()) != null) {
				final var comment = line.indexOf('%');
				if (comment >= 0) {
					line = line.substring(0, comment);
				}
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.startsWith("\\patterns{")) {
					group = 1;
					line = line.substring("\\patterns{".length()).trim();
				} else if (line.startsWith("\\hyphenation{")) {
					group = 2;
					line = line.substring("\\hyphenation{".length()).trim();
				}
				if (line.startsWith("}")) {
					group = 0;
					continue;
				}
				if (group == 0 || line.isEmpty()) {
					continue;
				}
				for (final var token : line.split("\\s+")) {
					if (token.isEmpty() || token.charAt(0) == '\\' || "}".equals(token)) {
						continue;
					}
					if (group == 1) {
						hyphenator.addPattern(token);
					} else {
						hyphenator.addException(token);
					}
				}
			}
		}
		return hyphenator;
	}

	/**
	 * The American English pattern file bundled with this class:
	 * {@code hyph-en-us.tex} from the hyph-utf8 package (Copyright (C) 1990,
	 * 2004, 2005 Gerard D.C. Kuiken), which carries Knuth's original
	 * {@code hyphen.tex} patterns plus the corrections from the TUGboat
	 * Hyphenation Exception Log. Its notice permits copying and distribution in
	 * any medium provided the copyright notice and that notice are preserved,
	 * so the file is bundled unmodified with its header comments.
	 */
	private static final String ENGLISH_PATTERNS = "hyph-en-us.tex";

	private static final class EnglishHolder {
		static final Hyphenator INSTANCE = load();

		private static Hyphenator load() {
			try (var in = Hyphenator.class.getResourceAsStream(ENGLISH_PATTERNS)) {
				if (in == null) {
					throw new IllegalStateException("Missing hyphenation patterns: " + ENGLISH_PATTERNS);
				}
				return readTex(in);
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	/**
	 * The American English hyphenator, with the left and right minimums of 2 and
	 * 3 the pattern file is designed for. The instance is built once and shared;
	 * it is immutable in use.
	 *
	 * @return an English hyphenator
	 */
	public static Hyphenator english() {
		return EnglishHolder.INSTANCE;
	}

	/**
	 * Returns the number of loaded patterns.
	 *
	 * @return the pattern count
	 */
	public int getPatternCount() {
		return this.patterns.size();
	}

	/**
	 * Returns the number of words with explicitly listed breaks.
	 *
	 * @return the exception count
	 */
	public int getExceptionCount() {
		return this.exceptions.size();
	}
}
