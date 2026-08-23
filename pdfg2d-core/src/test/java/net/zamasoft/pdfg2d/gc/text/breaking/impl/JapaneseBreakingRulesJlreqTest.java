package net.zamasoft.pdfg2d.gc.text.breaking.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * JLREQ Appendix A character-class coverage for Japanese line breaking.
 */
public class JapaneseBreakingRulesJlreqTest {
	private static final JapaneseBreakingRules RULES = new JapaneseBreakingRules();

	private static void assertRequiresFollowing(final String characters) {
		for (int i = 0; i < characters.length(); ++i) {
			final char c = characters.charAt(i);
			assertTrue(RULES.atomic(c, '漢'), () -> String.format("U+%04X may not end a line", (int) c));
		}
	}

	private static void assertProhibitedAtLineStart(final String characters) {
		for (int i = 0; i < characters.length(); ++i) {
			final char c = characters.charAt(i);
			assertTrue(RULES.atomic('漢', c), () -> String.format("U+%04X may not start a line", (int) c));
		}
	}

	@Test
	public void allOpeningBracketsRequireFollowingText() {
		// cl-01. In particular, Pi characters ‘ “ « are not START_PUNCTUATION.
		assertRequiresFollowing("‘“（〔［｛〈《「『【⦅〘〖«〝");
	}

	@Test
	public void allClosingBracketsAreLineStartProhibited() {
		// cl-02. In particular, Pf characters ’ ” » are not END_PUNCTUATION.
		assertProhibitedAtLineStart("’”）〕］｝〉》」』】⦆〙〗»〟");
	}

	@Test
	public void lineStartProhibitedClassesAreCovered() {
		assertProhibitedAtLineStart("‐〜゠–"); // cl-03 hyphens
		assertProhibitedAtLineStart("！？‼⁇⁈⁉"); // cl-04 dividing punctuation
		assertProhibitedAtLineStart("・：；"); // cl-05 middle dots
		assertProhibitedAtLineStart("。．"); // cl-06 full stops
		assertProhibitedAtLineStart("、，"); // cl-07 commas
		assertProhibitedAtLineStart("ヽヾゝゞ々〻"); // cl-09 iteration marks
		assertProhibitedAtLineStart("ー"); // cl-10 prolonged sound mark
		assertProhibitedAtLineStart("ぁぃぅぇぉァィゥェォっゃゅょゎゕゖッャュョヮヵヶ"
				+ "ㇰㇱㇲㇳㇴㇵㇶㇷㇸㇹㇺㇻㇼㇽㇾㇿ"); // cl-11 small kana
	}

	@Test
	public void inseparableClassBindsOnlyItsRequiredPairs() {
		assertTrue(RULES.atomic('—', '—'));
		assertTrue(RULES.atomic('…', '…'));
		assertTrue(RULES.atomic('‥', '‥'));
		assertTrue(RULES.atomic('〳', '〵'));
		assertTrue(RULES.atomic('〴', '〵'));

		// cl-08 leaders and em dashes are allowed at a line head unless they
		// form the inseparable two-character sequence.
		assertFalse(RULES.atomic('漢', '—'));
		assertFalse(RULES.atomic('漢', '…'));
		assertFalse(RULES.atomic('漢', '‥'));
		assertFalse(RULES.atomic('漢', '〳'));
		assertFalse(RULES.atomic('漢', '〴'));
		assertFalse(RULES.atomic('漢', '〵'));
	}
}
