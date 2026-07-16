package net.zamasoft.pdfg2d.gc.text.layout.control;

import net.zamasoft.pdfg2d.gc.text.TextImpl;

/**
 * Represents a soft hyphen ({@code U+00AD}) in the text layout: a conditional
 * hyphenation opportunity inside a word. It occupies no space; when a line is
 * actually broken at this point, the layout engine materializes the held
 * hyphen glyph ({@link #getText()}) at the end of the line instead.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class SoftHyphen extends Control {
	/** The soft hyphen character ({@code U+00AD}). */
	public static final char CHAR = '\u00AD';

	private final int charOffset;

	private final TextImpl text;

	/**
	 * Constructs a soft hyphen.
	 *
	 * @param charOffset the character offset of this element in the source string
	 * @param text       the hyphen glyph to draw when a line break occurs here
	 */
	public SoftHyphen(final int charOffset, final TextImpl text) {
		this.charOffset = charOffset;
		this.text = text;
	}

	/**
	 * Returns the hyphen glyph to draw when a line is broken at this point.
	 *
	 * @return the hyphen glyph text
	 */
	public TextImpl getText() {
		return this.text;
	}

	@Override
	public int getCharOffset() {
		return this.charOffset;
	}

	@Override
	public char getControlChar() {
		return '\u00AD';
	}

	@Override
	public double getAdvance() {
		return 0;
	}

	@Override
	public double getAscent() {
		return this.text.getAscent();
	}

	@Override
	public double getDescent() {
		return this.text.getDescent();
	}

	@Override
	public String toString() {
		return "[SHY]";
	}
}
