package net.zamasoft.pdfg2d.gc.text.breaking.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Words of scripts written with spaces between them do not break between their letters, also above U+00FF
 * (2026-11-25). Before, a word in Polish, Czech, Vietnamese, Greek, Russian, Armenian, Hebrew, Arabic or Hindi broke
 * anywhere; han, kana, hangul, the full-width forms and Thai still break between characters.
 */
public class JapaneseBreakingRulesWordTest {
	private static final JapaneseBreakingRules RULES = new JapaneseBreakingRules();

	/** Every two neighbours of each word are kept together. */
	private static void assertWhole(final String... words) {
		for (final String w : words) {
			for (int i = 0; i + 1 < w.length(); ++i) {
				final char a = w.charAt(i), b = w.charAt(i + 1);
				assertTrue(RULES.atomic(a, b),
						() -> String.format("%s: may break between U+%04X and U+%04X", w, (int) a, (int) b));
			}
		}
	}

	/** Every two neighbours of each string may break. */
	private static void assertBreakable(final String... strings) {
		for (final String s : strings) {
			for (int i = 0; i + 1 < s.length(); ++i) {
				final char a = s.charAt(i), b = s.charAt(i + 1);
				assertFalse(RULES.atomic(a, b),
						() -> String.format("%s: no break between U+%04X and U+%04X", s, (int) a, (int) b));
			}
		}
	}

	@Test
	public void wordsBeyondLatin1StayWhole() {
		assertWhole("żółwiątko", "Přeštěpán", "Nguyễn", "trường", "Œuvre", "Dvořák", "σιδηροδρομικών",
				"железнодорожных", "Երևան", "ירושלים", "القاهرة", "भारत", "ঢাকা", "თბილისი", "ሰላም");
	}

	@Test
	public void combiningMarksStayWithTheirLetters() {
		// e + U+0301, an Arabic letter + fatha, a Devanagari consonant + vowel sign
		assertWhole("ét́", "قَا", "का");
	}

	@Test
	public void ideographsKanaHangulFullWidthAndThaiStillBreak() {
		assertBreakable("漢字", "かな", "カナ", "서울", "ＡＢＣ", "１２", "กรุงเทพ");
	}

	@Test
	public void westernWordsBreakFromIdeographs() {
		// a Cyrillic or Greek letter next to a kanji is a boundary, as a Latin letter is
		assertBreakable("ж漢", "漢ж", "α線", "a漢");
	}

	@Test
	public void latin1AndAsciiAreUnchanged() {
		assertWhole("naïve", "façade", "Straße", "abc123");
	}
}
