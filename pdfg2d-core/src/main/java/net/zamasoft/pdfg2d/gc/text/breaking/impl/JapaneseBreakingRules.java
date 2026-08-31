package net.zamasoft.pdfg2d.gc.text.breaking.impl;

import java.lang.Character.UnicodeBlock;

import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;

/**
 * Japanese hyphenation rules.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class JapaneseBreakingRules implements TextBreakingRules {
	/**
	 * JLREQ Appendix A cl-01.  Unicode's punctuation category is not sufficient:
	 * quotation marks such as U+2018 and U+00AB are {@code Pi}, not {@code Ps}.
	 */
	private static final String JLREQ_OPENING_BRACKETS = "‘“（〔［｛〈《「『【⦅〘〖«〝";

	/**
	 * JLREQ Appendix A cl-02.  U+2019, U+201D and U+00BB are {@code Pf}, not
	 * {@code Pe}, so list the class explicitly for line-start prohibition.
	 */
	private static final String JLREQ_CLOSING_BRACKETS = "’”）〕］｝〉》」』】⦆〙〗»〟";

	/**
	 * The {@code Pf} quotation marks of cl-02. They close a quotation, but they
	 * are also the apostrophe: between letters, {@code Darcy’s} is one word and
	 * must not break after the mark (UAX #14 LB19). The ASCII branch of
	 * {@link #requiresAfter(char)} already does this for {@code U+0027}, because
	 * every ASCII letter and mark there requires a letter after it; these live
	 * above {@code U+00FF} and fell through to "no character required".
	 */
	private static final String JLREQ_CLOSING_QUOTES = "’”»›";

	private static final CharacterSet ASCII = new BitSetCharacterSet(
			"#$%&*+-/0123456789=@ABCDEFGHIJKLMNOPQRSTUVWXYZ\\^_abcdefghijklmnopqrstuvwxyz|~");

	/**
	 * Half-width alphanumeric characters.
	 */
	private static final CharacterSet LATIN_OR_DIGIT = c -> {
		if (c > 0xFF) {
			return false;
		}
		if (c > 0x7F) {
			return true;
		}
		return ASCII.contains(c);
	};

	/**
	 * Prohibited characters at the start of a line (SPEC JIS-X4051 8.1.1 + custom).
	 */
	private static final String GYOTO_KINSOKU = "～〜ヽヾゝゞ ー ァ ィ ゥ ェ ォ ッ ャ ュ ョ ヮ ヵ ヶ ぁ ぃ ぅ ぇ ぉ っ ゃ ゅ ょ ゎ ゕ ゖ ㇰ ㇱ ㇳ ㇲ ㇳ ㇴ ㇵ ㇶ ㇷ ㇸ ㇹ ㇺ ㇻ ㇼ ㇽ ㇾ ㇿ 々 〻\u3000・”"
			// JLREQ 3.1.7: ハイフン類(cl-03)は行頭禁則。カテゴリPdはswitchに
			// 掛からないため明示する(U+2010, U+2013, U+30A0。波ダッシュは既出)
			+ "\u2010\u2013\u30A0";

	/**
	 * Exclude emphatic dots that are mistakenly treated as line-start prohibited.
	 */
	private static final String GYOTO_KINSOKU_EX = "\uFE45\uFE46"
			// JLREQ 3.1.7: 分離禁止文字(cl-08)は行頭禁則ではない。三点リーダ・
			// 二点リーダはPo一律禁止から除外(……の対自体はcanSeparate=falseで不可分)
			+ "\u2026\u2025";

	/**
	 * Line-start prohibition processing. SPEC JIS-X4051 7.3
	 * 
	 * @param c the character to check
	 * @return the character set required before the character
	 */
	protected CharacterSet requiresBefore(final char c) {
		// Line-start prohibition
		// JLREQ cl-08 treats 〳〵 and 〴〵 as two-character units. These
		// characters are Unicode Lm, but applying the generic Lm rule would
		// incorrectly bind 〵 to every preceding character.
		if (c == '〵') {
			return cc -> cc == '〳' || cc == '〴';
		}
		if (c == '〳' || c == '〴') {
			return CharacterSet.NOTHING;
		}
		if (JLREQ_CLOSING_BRACKETS.indexOf(c) != -1) {
			return CharacterSet.ALL;
		}
		if (GYOTO_KINSOKU.indexOf(c) != -1) {
			return CharacterSet.ALL;
		}
		if (GYOTO_KINSOKU_EX.indexOf(c) != -1) {
			return CharacterSet.NOTHING;
		}
		final int type = Character.getType(c);

		return switch (type) {
			case Character.END_PUNCTUATION, Character.OTHER_PUNCTUATION, Character.MODIFIER_LETTER,
					Character.MODIFIER_SYMBOL ->
				// Characters required before closing parenthesis, delimiters, modifier letters,
				// or modifier symbols.
				CharacterSet.ALL;
			default -> CharacterSet.NOTHING;
		};
	}

	/**
	 * Line-end prohibition processing. SPEC JIS-X4051 7.4
	 * 
	 * @param c the character to check
	 * @return the character set required after the character
	 */
	protected CharacterSet requiresAfter(final char c) {
		if (JLREQ_OPENING_BRACKETS.indexOf(c) != -1) {
			return CharacterSet.ALL;
		}
		final int type = Character.getType(c);

		if (c <= 0xFF || LATIN_OR_DIGIT.contains(c)) {
			// Half-width alphanumeric
			switch (type) {
				case Character.START_PUNCTUATION:
					// Some character is required after an opening parenthesis.
					return CharacterSet.ALL;

				case Character.END_PUNCTUATION:
					// No character is required after a closing parenthesis.
					return CharacterSet.NOTHING;

				default:
					if (c == '\u0020' || c == '-' || c == '!' || c == '?') {
						// Can wrap after a delimiter.
						return CharacterSet.NOTHING;
					}
					// Alphanumeric characters require alphanumeric characters after them.
					return LATIN_OR_DIGIT;
			}
		} else {
			// Other characters
			switch (type) {
				case Character.START_PUNCTUATION:
					// Some character is required after an opening parenthesis.
					return CharacterSet.ALL;
			}
			// Dash, etc.
			if (c == '─' || c == '“') {
				return CharacterSet.ALL;
			}
			if (JLREQ_CLOSING_QUOTES.indexOf(c) != -1) {
				// Apostrophe when a letter follows; a closing quote otherwise.
				// A space, a bracket or a kanji after the mark still breaks.
				return LATIN_OR_DIGIT;
			}
			// JLREQ 3.1.10: 分離禁止文字(cl-08)の同字連続は分割禁止
			// (2倍ダーシ——・2倍リーダ……・‥‥)。―(U+2015)は同注記の
			// 処理系拡張。異字の組は分割可(附属書C.2)
			if (c == '—' || c == '…' || c == '‥' || c == '―') {
				return cc -> cc == c;
			}
		}
		return CharacterSet.NOTHING;
	}

	@Override
	public boolean atomic(final char c1, final char c2) {
		return this.requiresAfter(c1).contains(c2) || this.requiresBefore(c2).contains(c1);
	}

	@Override
	public boolean canSeparate(final char c1, final char c2) {
		if (Character.isWhitespace(c1) || Character.isWhitespace(c2)) {
			return c1 != '\u3000';
		}
		if (this.isCJK(c1) || this.isCJK(c2)) {
			return true;
		}
		return false;
	}

	/**
	 * Returns whether the given character belongs to a CJK Unicode block.
	 *
	 * @param c the character to test
	 * @return {@code true} if the character is a CJK character
	 */
	protected boolean isCJK(final char c) {
		final UnicodeBlock b = UnicodeBlock.of(c);
		if (b == null) {
			return true;
		}
		if (b == UnicodeBlock.CJK_COMPATIBILITY || b == UnicodeBlock.CJK_COMPATIBILITY_FORMS
				|| b == UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS || b == UnicodeBlock.CJK_RADICALS_SUPPLEMENT
				|| b == UnicodeBlock.CJK_STROKES || b == UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
				|| b == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || b == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
				|| b == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
				|| b == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
				|| b == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D || b == UnicodeBlock.HIRAGANA
				|| b == UnicodeBlock.KATAKANA || b == UnicodeBlock.KANBUN || b == UnicodeBlock.HANGUL_SYLLABLES
				|| b == UnicodeBlock.HANGUL_JAMO || b == UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
				|| b == UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
			return true;
		}
		return false;
	}
}
