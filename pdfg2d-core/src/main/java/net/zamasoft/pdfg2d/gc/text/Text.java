package net.zamasoft.pdfg2d.gc.text;

import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * Represents text that can be drawn.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public non-sealed interface Text extends Element {
	/**
	 * Returns the font style of this text run.
	 *
	 * @return the font style
	 */
	public FontStyle getFontStyle();

	/**
	 * Returns the font metrics of this text run.
	 *
	 * @return the font metrics
	 */
	public FontMetrics getFontMetrics();

	/**
	 * Returns the character offset of the first character in this text run
	 * within the original source string.
	 *
	 * @return the character offset
	 */
	public int getCharOffset();

	/**
	 * Returns the ascent of this text run.
	 *
	 * @return the ascent
	 */
	public double getAscent();

	/**
	 * Returns the descent of this text run.
	 *
	 * @return the descent
	 */
	public double getDescent();

	/**
	 * Returns the character buffer for this text run.
	 *
	 * @return the character array
	 */
	public char[] getChars();

	/**
	 * Returns the number of characters in this text run.
	 *
	 * @return the character count
	 */
	public int getCharCount();

	/**
	 * Returns the glyph ID array for this text run.
	 *
	 * @return the glyph ID array
	 */
	public int[] getGlyphIds();

	/**
	 * Returns the number of characters corresponding to each glyph (cluster lengths).
	 *
	 * @return the cluster length array
	 */
	public byte[] getClusterLengths();

	/**
	 * Returns the number of glyphs in this text run.
	 *
	 * @return the glyph count
	 */
	public int getGlyphCount();

	/**
	 * Returns the additional spacing added between each glyph (letter spacing).
	 *
	 * @return the letter spacing
	 */
	public double getLetterSpacing();

	/**
	 * Sends all glyphs in this text run to the specified glyph handler.
	 *
	 * @param gh the glyph handler to receive the glyphs
	 */
	public void toGlyphs(GlyphHandler gh);

	/**
	 * Returns the per-glyph extra advance adjustments as a read-only view,
	 * or {@code null} if no adjustments have been applied.
	 * 生配列公開({@code getXAdvances(boolean)})の置換(2026-08-01)——
	 * 書き込みは{@link TextImpl}の意味のある操作
	 * ({@code addXAdvance}/{@code resetXAdvances})に限定する。
	 *
	 * @return the per-glyph x-advance view, or {@code null}
	 */
	public GlyphAdvances xAdvances();
}
