package net.zamasoft.pdfg2d.gc.text.layout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.Element;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;

/**
 * Line assembly (glyph accumulation, run splitting at break opportunities, justification,
 * and line metrics measurement) (2026-08-01, 90-point plan increment 13).
 * Separated from {@link PageLayoutGlyphHandler#endLine}, which previously handled line splitting,
 * justification, measurement, column overflow, and drawing buffer registration together.
 *
 * <p>
 * This class finalizes a line using content through the break opportunity
 * ({@link #markBreakOpportunity()}) and carries the open text unit over to the next line.
 * Column placement, page advancement, and drawing remain in {@link PageLayoutGlyphHandler}.
 * Line splitting can be unit-tested without XML or GC.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class LineAssembler {

	/**
	 * A finalized line.
	 *
	 * @param elements line content (in drawing order)
	 * @param ascent   maximum ascent of the line (adjusted if fontSize is specified)
	 * @param descent  maximum descent of the line (likewise)
	 */
	record LineBox(Element[] elements, double ascent, double descent) {
	}

	private TextImpl text = null;

	private final List<Element> textBuffer = new ArrayList<>();

	private double letterSpacing = 0;

	private double advance = 0;

	/** Number of elements in the current text unit (since the break opportunity). */
	private int textUnitElementCount = 0;

	/** Number of glyphs in the current text unit that belong to the open run. */
	private int textUnitGlyphCount = 0;

	void setLetterSpacing(final double letterSpacing) {
		this.letterSpacing = letterSpacing;
	}

	double getLetterSpacing() {
		return this.letterSpacing;
	}

	/** Advance of the accumulating line (used for tab positioning and break decisions). */
	double advance() {
		return this.advance;
	}

	void startTextRun(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.closeTextRun();
		this.text = new TextImpl(charOffset, fontStyle, fontMetrics);
		this.text.setLetterSpacing(this.letterSpacing);
	}

	void glyph(final char[] ch, final int coff, final byte clen, final int gid) {
		this.advance += this.text.appendGlyph(ch, coff, clen, gid);
		this.advance += this.letterSpacing;
		++this.textUnitGlyphCount;
	}

	void endTextRun() {
		assert this.text.getGlyphCount() > 0;
	}

	/** Finalizes the open run and moves it to the line buffer. */
	void closeTextRun() {
		if (this.text != null) {
			this.text.pack();
			this.textBuffer.add(this.text);
			++this.textUnitElementCount;
			this.textUnitGlyphCount = 0;
			this.text = null;
		}
	}

	/** Adds a control element (tab, line break marker, etc.) to the line buffer. */
	void addControl(final Control control) {
		this.closeTextRun();
		this.textBuffer.add(control);
		++this.textUnitElementCount;
		this.advance += control.getAdvance();
	}

	/**
	 * Marks a break opportunity. Restarts counting subsequent content as a unit
	 * that may be carried over to the next line.
	 */
	void markBreakOpportunity() {
		this.textUnitElementCount = 0;
		this.textUnitGlyphCount = 0;
	}

	/**
	 * Finalizes the line.
	 *
	 * @param last       {@code true} for a forced break (newline or end): closes the line with all
	 *                   content, including the open unit. {@code false} for an overflow break:
	 *                   closes the line at the last break opportunity and carries the current unit
	 *                   (including splitting the open run) over to the next line
	 * @param justify    whether to justify the line end (applies only to overflow breaks)
	 * @param maxAdvance maximum line advance (used to distribute justification)
	 * @param fontSize   font size for fixed line height (0 uses measured descent)
	 * @return finalized line
	 */
	LineBox breakLine(final boolean last, final boolean justify, final double maxAdvance, final double fontSize) {
		final Element[] elements;
		double advance;
		if (last) {
			int elementCount = this.textBuffer.size();
			if (this.text != null) {
				++elementCount;
			}
			elements = new Element[elementCount];
			for (int i = 0; i < this.textBuffer.size(); ++i) {
				elements[i] = this.textBuffer.get(i);
			}
			if (this.text != null) {
				this.text.pack();
				elements[elementCount - 1] = this.text;
				this.text = null;
			}
			advance = this.advance;
			this.textBuffer.clear();
		} else {
			advance = 0;
			int count = this.textBuffer.size() - this.textUnitElementCount;
			int elementCount = count;
			if (this.text != null) {
				if (this.text.getGlyphCount() <= this.textUnitGlyphCount) {
					if (this.textUnitElementCount > 0) {
						++elementCount;
						++count;
					}
				} else {
					++elementCount;
				}
			}
			elements = new Element[elementCount];
			final Iterator<Element> i = this.textBuffer.iterator();
			for (int j = 0; j < count; ++j) {
				final Element e = i.next();
				elements[j] = e;
				advance += e.getAdvance();
				i.remove();
			}
			if (this.text != null && this.text.getGlyphCount() > this.textUnitGlyphCount) {
				final int pos = this.text.getGlyphCount() - this.textUnitGlyphCount;
				final Element e = this.text.split(pos);
				elements[elementCount - 1] = e;
				advance += e.getAdvance();
			}

			// Justify by distributing the leftover width as extra letter
			// spacing across all glyphs of the line. Known limitation: there
			// is no hyphenation, so an overlong unbreakable word wraps early
			// and the previous line may be stretched noticeably.
			if (justify) {
				int glyphCount = 0;
				for (final Element e : elements) {
					if (e instanceof Text text) {
						glyphCount += text.getGlyphCount();
					}
				}
				if (glyphCount >= 2) {
					final double letterSpacing = (maxAdvance - advance) / (double) (glyphCount - 1);
					for (final Element e : elements) {
						if (e instanceof TextImpl t) {
							t.setLetterSpacing(t.getLetterSpacing() + letterSpacing);
						}
					}
				}
			}
		}
		this.advance -= advance;

		// Calculate ascent/descent
		double maxAscent = 0;
		double maxDescent = 0;
		for (final Element e : elements) {
			if (e instanceof Text text) {
				maxAscent = Math.max(maxAscent, text.getAscent());
				maxDescent = Math.max(maxDescent, text.getDescent());
			} else if (e instanceof Control control) {
				maxAscent = Math.max(maxAscent, control.getAscent());
				maxDescent = Math.max(maxDescent, control.getDescent());
			}
		}
		if (fontSize != 0) {
			maxDescent = fontSize - maxAscent;
		}
		return new LineBox(elements, maxAscent, maxDescent);
	}
}
