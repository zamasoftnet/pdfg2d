package net.zamasoft.pdfg2d.gc.text;

/**
 * Represents an element in the text layout, such as a text run or a control character.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public sealed interface Element permits Text, TextControl {

	/**
	 * Returns the advance width of the element.
	 * 
	 * @return the advance width
	 */
	public double getAdvance();
}
