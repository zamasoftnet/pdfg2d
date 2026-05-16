package net.zamasoft.pdfg2d.gc.text.layout.control;

import net.zamasoft.pdfg2d.gc.text.TextControl;

/**
 * Abstract class representing a control character in the text layout,
 * such as a line break, tab, or white space.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class Control extends TextControl {
	/**
	 * Returns the character offset of this control within the source string.
	 *
	 * @return the character offset
	 */
	public abstract int getCharOffset();

	/**
	 * Returns the control character represented by this element (e.g. {@code '\n'},
	 * {@code '\t'}, {@code ' '}).
	 *
	 * @return the control character
	 */
	public abstract char getControlChar();

	/**
	 * Returns the ascent associated with this control element.
	 *
	 * @return the ascent
	 */
	public abstract double getAscent();

	/**
	 * Returns the descent associated with this control element.
	 *
	 * @return the descent
	 */
	public abstract double getDescent();

	@Override
	public final String getString() {
		return BREAK;
	}

	@Override
	public String toString() {
		return String.valueOf(this.getControlChar());
	}
}
