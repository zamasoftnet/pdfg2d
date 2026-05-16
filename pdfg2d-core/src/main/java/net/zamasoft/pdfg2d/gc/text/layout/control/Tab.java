package net.zamasoft.pdfg2d.gc.text.layout.control;

import net.zamasoft.pdfg2d.gc.font.FontListMetrics;

/**
 * Represents a tab control element ({@code '\t'}) in the text layout.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class Tab extends Control {
	private final FontListMetrics flm;
	private final int charOffset;

	/** The computed advance width for this tab stop. */
	public double advance = 0;

	/**
	 * Constructs a new Tab.
	 *
	 * @param flm        the font list metrics used to determine ascent and descent
	 * @param charOffset the character offset of this element in the source string
	 */
	public Tab(final FontListMetrics flm, final int charOffset) {
		this.flm = flm;
		this.charOffset = charOffset;
	}

	@Override
	public int getCharOffset() {
		return this.charOffset;
	}

	@Override
	public char getControlChar() {
		return '\t';
	}

	@Override
	public double getAdvance() {
		return this.advance;
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
		return "\\t";
	}
}
