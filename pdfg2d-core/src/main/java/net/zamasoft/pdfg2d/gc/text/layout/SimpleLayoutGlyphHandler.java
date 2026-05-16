package net.zamasoft.pdfg2d.gc.text.layout;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.layout.control.Tab;

/**
 * A simple {@link GlyphHandler} that draws glyphs sequentially in a single
 * line, advancing the cursor after each glyph run and supporting basic
 * line-break ({@code '\n'}) and tab ({@code '\t'}) controls.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class SimpleLayoutGlyphHandler implements GlyphHandler {
	/**
	 * Tab width.
	 */
	private static final double TAB_WIDTH = 24.0;

	/** The graphics context to draw to. */
	private GC gc;

	private TextImpl text = null;

	private double letterSpacing = 0;

	private double advance = 0, line = 0, maxLineHeight = 0;

	/**
	 * Returns the graphics context used for drawing.
	 *
	 * @return the graphics context
	 */
	public GC getGC() {
		return this.gc;
	}

	/**
	 * Sets the graphics context used for drawing.
	 *
	 * @param gc the graphics context to set
	 */
	public void setGC(final GC gc) {
		this.gc = gc;
	}

	/**
	 * Returns the current advance (cursor position in the line direction).
	 *
	 * @return the advance
	 */
	public double getAdvance() {
		return this.advance;
	}

	/**
	 * Returns the letter spacing added between glyphs.
	 *
	 * @return the letter spacing
	 */
	public double getLetterSpacing() {
		return this.letterSpacing;
	}

	/**
	 * Sets the letter spacing added between glyphs.
	 *
	 * @param letterSpacing the letter spacing to set
	 */
	public void setLetterSpacing(final double letterSpacing) {
		this.letterSpacing = letterSpacing;
	}

	@Override
	public void startTextRun(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.text = new TextImpl(charOffset, fontStyle, fontMetrics);
		this.text.setLetterSpacing(this.letterSpacing);
	}

	@Override
	public void glyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
		this.text.appendGlyph(ch, coff, clen, gid);
	}

	@Override
	public void endTextRun() {
		assert this.text.getGlyphCount() > 0;
		if (this.gc != null) {
			switch (this.text.getFontStyle().getDirection()) {
				case LTR:
				case RTL:
					// Horizontal writing
					this.gc.drawText(this.text, this.advance, this.line);
					break;
				case TB:
					// Vertical writing
					this.gc.drawText(this.text, -this.line, this.advance);
					break;
				default:
					throw new IllegalArgumentException();
			}
		}
		this.advance += this.text.getAdvance();
		this.maxLineHeight = Math.max(this.maxLineHeight, this.text.getFontStyle().getSize());
	}

	@Override
	public void control(final TextControl control) {
		final Control c = (Control) control;
		this.maxLineHeight = Math.max(this.maxLineHeight, c.getAscent() + c.getDescent());
		switch (c.getControlChar()) {
			case '\n':
				this.line += this.maxLineHeight;
				this.maxLineHeight = 0;
				this.advance = 0;
				return;

			case '\t':
				// Tab character
				final Tab tab = (Tab) c;
				tab.advance = (TAB_WIDTH - (this.advance % TAB_WIDTH));
				break;
		}
		this.advance += control.getAdvance();
	}

	@Override
	public void flush() {
		// ignore
	}

	@Override
	public void close() {
		// ignore
	}
}
