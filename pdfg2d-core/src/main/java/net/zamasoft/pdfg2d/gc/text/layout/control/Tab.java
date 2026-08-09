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

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Uses the metrics of the first font in the list, consistent with the
	 * advance ({@code getFontMetrics(0)}). Using the list-wide maximum
	 * (which typically includes a CJK fallback with a taller ascent) made
	 * every line containing this control element taller than the specified
	 * line-height by a few percent, while lines without one stayed exact
	 * (2026-08-09).
	 * </p>
	 */
	@Override
	public double getAscent() {
		return this.flm.getFontMetrics(0).getAscent();
	}

	@Override
	public double getDescent() {
		return this.flm.getFontMetrics(0).getDescent();
	}

	@Override
	public String toString() {
		return "\\t";
	}
}
