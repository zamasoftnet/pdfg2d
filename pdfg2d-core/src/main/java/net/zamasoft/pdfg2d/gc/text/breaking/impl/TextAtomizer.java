package net.zamasoft.pdfg2d.gc.text.breaking.impl;

import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;

/**
 * Decomposes text into unbreakable units for line breaking.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class TextAtomizer implements FilterGlyphHandler {
	protected TextBreakingRules rules;

	private GlyphHandler glyphHandler;

	/**
	 * Previous character.
	 */
	private char prevChar = 0;

	private TextControl beforeControl = null;

	/**
	 * Constructs a new TextAtomizer with the given line-breaking rules.
	 *
	 * @param rules the text breaking rules to apply
	 */
	public TextAtomizer(final TextBreakingRules rules) {
		this.rules = rules;
	}

	/**
	 * Returns the current text breaking rules.
	 *
	 * @return the text breaking rules
	 */
	public TextBreakingRules getTextBreakingRules() {
		return this.rules;
	}

	/**
	 * Sets the text breaking rules to apply.
	 *
	 * @param rules the new text breaking rules
	 */
	public void setTextBreakingRules(final TextBreakingRules rules) {
		this.rules = rules;
	}

	@Override
	public void setGlyphHandler(final GlyphHandler glyphHandler) {
		this.glyphHandler = glyphHandler;
	}

	@Override
	public void startTextRun(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.glyphHandler.startTextRun(charOffset, fontStyle, fontMetrics);
	}

	@Override
	public void endTextRun() {
		this.glyphHandler.endTextRun();
	}

	@Override
	public void glyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
		final char c1 = ch[coff];
		final char c2 = ch[coff + clen - 1];
		this.nextGlyph(c1, c2, clen);
		if (this.beforeControl != null) {
			this.glyphHandler.control(this.beforeControl);
			this.beforeControl = null;
		}
		this.glyphHandler.glyph(charOffset, ch, coff, clen, gid);
	}

	@Override
	public void control(final TextControl control) {
		final var str = control.getString();
		switch (str) {
			case TextControl.BREAK -> {
				// Separates strings except for CONTINUE_BEFORE, CONTINUE_AFTER
				if (this.prevChar != 0 && this.prevChar != '\u2060') {
					this.internalFlush();
				}
				this.prevChar = '\u200B';
			}
			case TextControl.CONTINUE_BEFORE -> {
				// Treat as previous character (attach to following string <span>...)
				if (this.prevChar == '\u200B') {
					this.internalFlush();
					this.prevChar = '\u2060';
				} else {
					if (this.beforeControl != null) {
						this.glyphHandler.control(this.beforeControl);
					}
					this.beforeControl = control;
					return;
				}
			}
			case TextControl.CONTINUE_AFTER -> {
				// Treat as previous character (attach to previous string ...</span>)
				if (this.prevChar == '\u200B') {
					this.internalFlush();
					this.prevChar = '\u2060';
				}
			}
			case TextControl.JOIN -> {
				// Treat as previous character (attach to previous/following)
				if (this.prevChar == '\u200B') {
					this.internalFlush();
				}
				this.prevChar = '\u2060';
			}
			default -> {
				// Use corresponding string
				final var strlen = str.length();
				final var c1 = str.charAt(0);
				final var c2 = str.charAt(strlen - 1);
				this.nextGlyph(c1, c2, strlen);
			}
		}

		if (this.beforeControl != null) {
			this.glyphHandler.control(this.beforeControl);
			this.beforeControl = null;
		}
		this.glyphHandler.control(control);
	}

	private void internalFlush() {
		this.glyphHandler.flush();
	}

	@Override
	public void flush() {
		if (this.beforeControl != null) {
			this.glyphHandler.control(this.beforeControl);
			this.beforeControl = null;
		}
		this.internalFlush();
	}

	@Override
	public void close() {
		if (this.beforeControl != null) {
			this.glyphHandler.control(this.beforeControl);
			this.beforeControl = null;
		}
		this.glyphHandler.close();
	}

	/**
	 * 
	 * @param c1        the first character corresponding to the glyph
	 * @param c2        the last character corresponding to the glyph
	 * @param charCount the number of characters
	 */
	private void nextGlyph(final char c1, final char c2, final int charCount) {
		if (this.prevChar != 0 && this.prevChar != '\u2060'
				&& (this.prevChar == '\u200B' || !this.rules.atomic(this.prevChar, c1))) {
			this.internalFlush();
		}
		this.prevChar = c2;
	}
}
