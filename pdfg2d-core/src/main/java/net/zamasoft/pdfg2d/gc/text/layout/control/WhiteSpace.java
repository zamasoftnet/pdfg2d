package net.zamasoft.pdfg2d.gc.text.layout.control;

import net.zamasoft.pdfg2d.gc.font.FontListMetrics;

/**
 * Represents a white space control element ({@code ' '}) in the text layout.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class WhiteSpace extends Control {
	private final FontListMetrics flm;
	private final int charOffset;
	private double advance = 0;

	/**
	 * Constructs a new WhiteSpace whose advance is initialised to the space
	 * advance of the first font in the given font list metrics.
	 *
	 * @param flm        the font list metrics used to determine advance, ascent
	 *                   and descent
	 * @param charOffset the character offset of this element in the source string
	 */
	public WhiteSpace(final FontListMetrics flm, final int charOffset) {
		this.flm = flm;
		this.advance = this.flm.getFontMetrics(0).getSpaceAdvance();
		this.charOffset = charOffset;
	}

	@Override
	public int getCharOffset() {
		return this.charOffset;
	}

	@Override
	public char getControlChar() {
		return '\u0020';
	}

	@Override
	public double getAdvance() {
		return this.advance;
	}

	/**
	 * Sets the word spacing.
	 * 
	 * @param wordSpacing the word spacing to set
	 */
	public void setWordSpacing(final double wordSpacing) {
		this.advance = wordSpacing + this.flm.getFontMetrics(0).getSpaceAdvance();
	}

	/**
	 * Collapses the white space (sets advance to 0).
	 */
	public void collapse() {
		this.advance = 0;
	}

	@Override
	public double getAscent() {
		return this.flm.getMaxAscent();
	}

	@Override
	public double getDescent() {
		return this.flm.getMaxDescent();
	}

	@Override
	public String toString() {
		return "[SPACE]";
	}
}
