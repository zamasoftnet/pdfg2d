package net.zamasoft.pdfg2d.gc.text.layout.control;

import net.zamasoft.pdfg2d.gc.font.FontListMetrics;

/**
 * Represents a line break control element ({@code '\n'}) in the text layout.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class LineBreak extends Control {
	private final FontListMetrics flm;
	private final int charOffset;

	/**
	 * Constructs a new LineBreak.
	 *
	 * @param flm        the font list metrics used to determine ascent and descent
	 * @param charOffset the character offset of this element in the source string
	 */
	public LineBreak(final FontListMetrics flm, final int charOffset) {
		this.flm = flm;
		this.charOffset = charOffset;
	}

	@Override
	public int getCharOffset() {
		return this.charOffset;
	}

	@Override
	public char getControlChar() {
		return '\n';
	}

	@Override
	public double getAdvance() {
		return 0;
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
		return "\\n";
	}

}
