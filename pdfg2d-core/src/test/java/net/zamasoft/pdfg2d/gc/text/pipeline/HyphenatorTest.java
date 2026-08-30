package net.zamasoft.pdfg2d.gc.text.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link Hyphenator} against the bundled American English patterns.
 *
 * <p>
 * The words here are deliberately ones no pattern names directly. An earlier
 * built-in set of 280 hand-written patterns passed a test that only used
 * {@code hyphenation}, {@code algorithm}, {@code typography} and
 * {@code computer} - the four words its last eleven patterns had been written
 * for - while producing single-letter fragments such as
 * {@code ma-na-g-e-m-ent} for everything else.
 * </p>
 */
class HyphenatorTest {

	private static String spell(final String word) {
		final var breaks = Hyphenator.english().hyphenate(word);
		final var sb = new StringBuilder();
		var p = 0;
		for (final var b : breaks) {
			sb.append(word, p, b).append('-');
			p = b;
		}
		return sb.append(word.substring(p)).toString();
	}

	@Test
	void loadsTheCompletePublishedPatternFile() {
		final var hyphenator = Hyphenator.english();
		assertEquals(4938, hyphenator.getPatternCount(), "patterns in hyph-en-us.tex");
		assertEquals(14, hyphenator.getExceptionCount(), "words in its \\hyphenation list");
	}

	@Test
	void hyphenatesCommonWords() {
		assertEquals("hy-phen-ation", spell("hyphenation"));
		assertEquals("al-go-rithm", spell("algorithm"));
		assertEquals("ty-pog-ra-phy", spell("typography"));
		assertEquals("doc-u-ment", spell("document"));
		assertEquals("man-age-ment", spell("management"));
		assertEquals("pro-fes-sional", spell("professional"));
		assertEquals("uni-ver-sity", spell("university"));
		assertEquals("in-ter-na-tional", spell("international"));
	}

	/**
	 * The words the previous 280-pattern set got wrong, with the breaks the
	 * published patterns give. A single-letter syllable inside a word is correct
	 * English ({@code doc-u-ment}, {@code sep-a-rate}); what was wrong before was
	 * strings of them, from blanket vowel/consonant rules with nothing to inhibit
	 * a break in a longer context.
	 */
	@Test
	void hyphenatesTheWordsTheHandWrittenSubsetGotWrong() {
		assertEquals("com-puter", spell("computer")); // was co-m-pu-ter
		assertEquals("Japan-ese", spell("Japanese")); // was Ja-p-a-n-ese
		assertEquals("man-age-ment", spell("management")); // was ma-na-g-e-m-ent
		assertEquals("de-vel-op-ment", spell("development")); // was dev-elo-p-m-ent
		assertEquals("op-por-tu-nity", spell("opportunity")); // was op-po-r-tu-n-ity
		assertEquals("rep-re-sen-ta-tive", spell("representative")); // was repr-es-en-ta-tive
		assertEquals("in-for-ma-tion", spell("information")); // was in-fo-r-mation
		assertEquals("tech-nol-ogy", spell("technology")); // was te-ch-no-logy
		assertEquals("dic-tio-nary", spell("dictionary")); // was di-cti-o-nary
		assertEquals("es-pe-cially", spell("especially")); // was esp-ec-i-ally
		assertEquals("sep-a-rate", spell("separate")); // was sepa-rate
		assertEquals("stream-ing", spell("streaming")); // was str-ea-ming
	}

	@Test
	void honorsTheLeftAndRightMinimums() {
		// Every break keeps at least 2 letters before it and 3 after it.
		final String[] words = { "hyphenation", "management", "university", "typography", "internationalization" };
		for (final var word : words) {
			for (final var b : Hyphenator.english().hyphenate(word)) {
				assertTrue(b >= 2, word + ": break at " + b);
				assertTrue(word.length() - b >= 3, word + ": break at " + b);
			}
		}
	}

	@Test
	void appliesTheExplicitExceptionList() {
		// hyph-en-us.tex lists these; "present" and "project" are listed with no
		// hyphen at all, which forbids breaking them.
		assertArrayEquals(new int[0], Hyphenator.english().hyphenate("present"));
		assertArrayEquals(new int[0], Hyphenator.english().hyphenate("project"));
		assertEquals("as-so-ciate", spell("associate"));
		assertEquals("phil-an-thropic", spell("philanthropic"));
	}

	@Test
	void isCaseInsensitive() {
		assertArrayEquals(Hyphenator.english().hyphenate("japanese"), Hyphenator.english().hyphenate("Japanese"));
		assertEquals("Japan-ese", spell("Japanese"));
	}

	@Test
	void leavesShortWordsAlone() {
		assertArrayEquals(new int[0], Hyphenator.english().hyphenate("the"));
		assertArrayEquals(new int[0], Hyphenator.english().hyphenate("page"));
	}

	@Test
	void acceptsAnExplicitPatternList() {
		// The constructor still takes .tex patterns directly.
		final var hyphenator = new Hyphenator("hy3ph", "he2n").setMins(2, 3);
		assertArrayEquals(new int[] { 2 }, hyphenator.hyphenate("hyphen"));
	}
}
