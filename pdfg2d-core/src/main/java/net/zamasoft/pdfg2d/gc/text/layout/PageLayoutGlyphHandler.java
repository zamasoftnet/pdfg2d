package net.zamasoft.pdfg2d.gc.text.layout;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.text.Element;

import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.layout.control.Tab;

/**
 * A {@link GlyphHandler} that arranges glyphs into lines and pages, supporting
 * multi-column layout, text alignment (start, end, center, justify), vertical
 * justification, float positioning, and horizontal/vertical writing modes.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PageLayoutGlyphHandler implements GlyphHandler {
	/**
	 * Tab width.
	 */
	private static final double TAB_WIDTH = 24.0;

	/** The graphics context to draw to. */
	private GC gc;

	/** Text direction. */
	private Direction direction = Direction.LTR;

	/** Line height. */
	private double lineHeight = 1.2;

	/** Max line advance (width). */
	private double lineAdvance = Double.MAX_VALUE;

	/** Max page advance (height). */
	private double pageAdvance = Double.MAX_VALUE;

	/** Actual max line advance of the drawing area. */
	private double maxLineAdvance = 0;

	/** Actual last line advance of the drawing area. */
	private double lastLineAdvance = 0;

	/** Actual max page advance of the drawing area. */
	private double maxPageAdvance = 0;

	public enum FloatPosition {
		NONE, TOP_RIGHT
	}

	private FloatPosition floatPosition = FloatPosition.NONE;

	public enum Alignment {
		START, END, CENTER, JUSTIFY;
	}

	private Alignment align = Alignment.START;

	private double floatWidth, floatHeight;

	private int columnCount = 1;

	private double columnGap = 0;

	private double pageOffset = 0, lineOffset = 0;

	private int column = 0;

	private record LineBufferItem(Element[] elements, boolean last) {
	}

	private List<LineBufferItem> buffer = new ArrayList<>();

	private double lineFactor = 0;

	/** 行の組み立て(蓄積・分割・justify・計測)はLineAssemblerへ分離
	 * (2026-08-01、増分13)。このクラスは段配置と描画だけを持つ。 */
	private final LineAssembler assembler = new LineAssembler();

	// A "text unit" is the run of elements/glyphs accumulated since the last
	// break opportunity (i.e. since the caller last invoked flush()). When a
	// line overflows, endLine(false) keeps the current unit intact and moves
	// it wholesale to the next line; only these counters tell it where the
	// unit begins.

	private boolean justifyPage = false;

	private double fontSize = 0;

	/**
	 * Constructs a new PageLayoutGlyphHandler drawing to the given graphics context.
	 *
	 * @param gc the graphics context to draw to, or {@code null} to suppress drawing
	 */
	public PageLayoutGlyphHandler(final GC gc) {
		this.gc = gc;
	}

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
	 * Returns the writing direction.
	 *
	 * @return the writing direction
	 */
	public Direction getDirection() {
		return this.direction;
	}

	/**
	 * Sets the writing direction.
	 *
	 * @param direction the writing direction to set
	 */
	public void setDirection(final Direction direction) {
		this.direction = direction;
	}

	/**
	 * Returns the text alignment.
	 *
	 * @return the alignment
	 */
	public Alignment getAlign() {
		return this.align;
	}

	/**
	 * Sets the text alignment.
	 *
	 * @param align the alignment to set
	 */
	public void setAlign(final Alignment align) {
		this.align = align;
	}

	/**
	 * Returns the line height multiplier.
	 *
	 * @return the line height
	 */
	public double getLineHeight() {
		return this.lineHeight;
	}

	/**
	 * Sets the line height multiplier.
	 *
	 * @param lineHeight the line height to set
	 */
	public void setLineHeight(final double lineHeight) {
		this.lineHeight = lineHeight;
	}

	/**
	 * Returns the letter spacing.
	 *
	 * @return the letter spacing
	 */
	public double getLetterSpacing() {
		return this.assembler.getLetterSpacing();
	}

	/**
	 * Sets the letter spacing.
	 *
	 * @param letterSpacing the letter spacing to set
	 */
	public void setLetterSpacing(final double letterSpacing) {
		this.assembler.setLetterSpacing(letterSpacing);
	}

	/**
	 * Returns the maximum page advance (height of the layout area).
	 *
	 * @return the page advance limit
	 */
	public double getPageAdvance() {
		return this.pageAdvance;
	}

	/**
	 * Sets the maximum page advance (height of the layout area).
	 *
	 * @param pageAdvance the page advance limit to set
	 */
	public void setPageAdvance(final double pageAdvance) {
		this.pageAdvance = pageAdvance;
	}

	/**
	 * Returns the number of columns.
	 *
	 * @return the column count
	 */
	public int getColumnCount() {
		return this.columnCount;
	}

	/**
	 * Sets the number of columns.
	 *
	 * @param columnCount the column count to set
	 */
	public void setColumnCount(final int columnCount) {
		this.columnCount = columnCount;
	}

	/**
	 * Returns the gap between columns.
	 *
	 * @return the column gap
	 */
	public double getColumnGap() {
		return this.columnGap;
	}

	/**
	 * Sets the gap between columns.
	 *
	 * @param columnGap the column gap to set
	 */
	public void setColumnGap(final double columnGap) {
		this.columnGap = columnGap;
	}

	/**
	 * Sets the maximum line advance (width of the layout area).
	 *
	 * @param lineAdvance the line advance limit to set
	 */
	public void setLineAdvance(final double lineAdvance) {
		this.lineAdvance = lineAdvance;
	}

	/**
	 * Returns the actual maximum line advance reached during layout.
	 *
	 * @return the maximum line advance
	 */
	public double getMaxLineAdvance() {
		return this.maxLineAdvance;
	}

	/**
	 * Returns the actual line advance of the last line drawn.
	 *
	 * @return the last line advance
	 */
	public double getLastLineAdvance() {
		return this.lastLineAdvance;
	}

	/**
	 * Returns the actual maximum page advance reached during layout.
	 *
	 * @return the maximum page advance
	 */
	public double getMaxPageAdvance() {
		return this.maxPageAdvance;
	}

	private double getMaxAdvance() {
		double maxAdvance = (this.lineAdvance - (this.columnGap * (this.columnCount - 1))) / this.columnCount;
		if (this.floatPosition == FloatPosition.TOP_RIGHT) {
			if (this.pageOffset < this.floatHeight) {
				maxAdvance -= this.floatWidth;
			}
		}
		return maxAdvance;
	}

	/**
	 * Returns whether vertical justification (justifying the page) is enabled.
	 *
	 * @return {@code true} if page justification is enabled
	 */
	public boolean isJustifyPage() {
		return this.justifyPage;
	}

	/**
	 * Sets whether vertical justification (justifying the page) is enabled.
	 *
	 * @param justifyPage {@code true} to enable page justification
	 */
	public void setJustifyPage(final boolean justifyPage) {
		this.justifyPage = justifyPage;
	}

	/**
	 * Returns the font size used for line height calculations when set explicitly.
	 *
	 * @return the font size, or {@code 0} if not set
	 */
	public double getFontSize() {
		return this.fontSize;
	}

	/**
	 * Sets the font size used to override descent calculations for fixed
	 * line heights.
	 *
	 * @param fontSize the font size to set, or {@code 0} to disable the override
	 */
	public void setFontSize(final double fontSize) {
		this.fontSize = fontSize;
	}

	/**
	 * Configures a float area that reduces the available line width for the
	 * lines covered by the float.
	 *
	 * @param position the position of the float (e.g. {@link FloatPosition#TOP_RIGHT})
	 * @param width    the width of the float area
	 * @param height   the height of the float area
	 */
	public void setFloat(final FloatPosition position, final double width, final double height) {
		this.floatPosition = position;
		this.floatWidth = width;
		this.floatHeight = height;
	}

	/**
	 * Closes the current line and queues it for drawing.
	 *
	 * @param last {@code true} for a forced break (explicit newline or end of
	 *             text): the whole pending content, including the open text
	 *             unit, ends the line. {@code false} for an overflow break:
	 *             only content up to the last break opportunity ends the
	 *             line, and the current text unit (identified by
	 *             {@link #textUnitElementCount}/{@link #textUnitGlyphCount},
	 *             possibly splitting the open {@link #text} run) is carried
	 *             over as the beginning of the next line.
	 */
	private void endLine(final boolean last) {
		// 行の組み立て(run分割・justify・計測)はLineAssemblerが担い、
		// ここは段配置とページ送りだけを行う(2026-08-01、増分13)
		final LineAssembler.LineBox line = this.assembler.breakLine(last,
				!last && this.align == Alignment.JUSTIFY, this.getMaxAdvance(), this.fontSize);
		final double maxAscent = line.ascent();
		final double maxDescent = line.descent();

		// Calculate page direction advance
		final double lineMargin = (maxAscent + maxDescent) * (this.lineHeight - 1) / 2.0;
		double pageAdvance1 = maxAscent + lineMargin;
		final double pageAdvance2 = maxDescent + lineMargin + this.lineFactor;

		if (compare(this.pageOffset + pageAdvance1 + pageAdvance2, this.pageAdvance) > 0) {
			// Draw
			this.endColumn();

			// Move column
			++this.column;
			if (this.column >= this.columnCount) {
				this.overflow();
			} else {
				this.pageOffset = 0;
				this.lineOffset += (this.getMaxAdvance() + this.columnGap);
			}
			this.buffer = new ArrayList<>();
			pageAdvance1 = maxAscent;
		}

		// Record
		this.buffer.add(new LineBufferItem(line.elements(), last));

		// Line feed
		this.pageOffset += pageAdvance1 + pageAdvance2;
	}

	/**
	 * Compares two double values with a small tolerance to avoid floating-point
	 * rounding errors.
	 *
	 * @param a the first value
	 * @param b the second value
	 * @return {@code 0} if the values are within tolerance, {@code -1} if {@code a < b},
	 *         or {@code 1} if {@code a > b}
	 */
	public static int compare(final double a, final double b) {
		final double diff = a - b;
		if (diff < .1 && diff > -.1) {
			return 0;
		}
		return a < b ? -1 : 1;
	}

	private void drawLine(final Element[] elements, final boolean last) {
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
		if (this.fontSize != 0) {
			maxDescent = this.fontSize - maxAscent;
		}

		// Calculate page direction advance
		final double lineMargin = (maxAscent + maxDescent) * (this.lineHeight - 1) / 2.0;
		final double pageAdvance1 = maxAscent + lineMargin;
		final double pageAdvance2 = maxDescent + lineMargin + this.lineFactor;
		this.pageOffset += pageAdvance1;

		// Calculate current position. Known limitation: RTL lines are placed
		// exactly like LTR ones (no bidi reordering, no right-aligned line
		// origin); RTL text must be supplied in visual order.
		double lineAxis = this.lineOffset;
		double pageAxis = switch (this.direction) {
			case LTR, RTL -> this.pageOffset;
			case TB -> -this.pageOffset;
		};
		if (this.align == Alignment.END || this.align == Alignment.CENTER) {
			double advance = 0;
			for (final Element e : elements) {
				advance += e.getAdvance();
			}
			if (this.align == Alignment.END) {
				lineAxis += this.getMaxAdvance() - advance;
			} else {
				lineAxis += (this.getMaxAdvance() - advance) / 2.0;
			}
		}

		// Draw
		for (final Element e : elements) {
			if (this.gc != null && e instanceof Text text) {
				switch (this.direction) {
					case LTR, RTL -> this.gc.drawText(text, lineAxis, pageAxis);
					case TB -> this.gc.drawText(text, pageAxis, lineAxis);
				}
			}
			lineAxis += e.getAdvance();
		}
		// Line feed
		this.pageOffset += pageAdvance2;
		this.maxLineAdvance = Math.max(this.maxLineAdvance, this.lastLineAdvance = lineAxis);
		this.maxPageAdvance = Math.max(this.maxPageAdvance, this.pageOffset);
	}

	protected void overflow() throws GraphicsException {
		this.pageOffset = this.lineOffset = 0;
		this.column = 0;
	}

	@Override
	public void startTextRun(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.assembler.startTextRun(charOffset, fontStyle, fontMetrics);
	}

	@Override
	public void glyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
		this.assembler.glyph(ch, coff, clen, gid);
	}

	@Override
	public void endTextRun() {
		this.assembler.endTextRun();
	}

	@Override
	public void control(final TextControl control) {
		final Control c = (Control) control;
		switch (c.getControlChar()) {
			case '\n' -> {
				this.endLine(true);
				this.assembler.markBreakOpportunity();
			}

			case '\t' -> {
				// Tab character
				final Tab tab = (Tab) c;
				tab.advance = (TAB_WIDTH - (this.assembler.advance() % TAB_WIDTH));
				if (this.assembler.advance() + tab.advance > this.getMaxAdvance()) {
					this.endLine(false);
					tab.advance = TAB_WIDTH;
				}
			}
		}
		this.assembler.addControl(c);
	}

	/**
	 * Marks a break opportunity. The upstream layout driver calls this after
	 * each breakable text unit (typically at word boundaries or between CJK
	 * characters as decided by the breaking rules); if the accumulated line
	 * overflows the measure at this point, everything up to the previous
	 * break opportunity is emitted as a finished line and the current unit
	 * carries over to the next line.
	 */
	@Override
	public void flush() {
		if (this.assembler.advance() > this.getMaxAdvance()) {
			this.endLine(false);
		}
		this.assembler.markBreakOpportunity();
	}

	private void endColumn() {
		if (this.justifyPage && this.columnCount > 1) {
			// Vertical justification
			this.lineFactor = (this.pageAdvance - this.pageOffset) / (this.buffer.size() / 2 - 1);
		}

		final List<LineBufferItem> list = this.buffer;
		this.buffer = null;
		this.pageOffset = 0;
		for (final LineBufferItem item : list) {
			this.drawLine(item.elements(), item.last());
		}
		this.lineFactor = 0;
	}

	@Override
	public void close() {
		this.endLine(true);
		this.endColumn();
	}
}
