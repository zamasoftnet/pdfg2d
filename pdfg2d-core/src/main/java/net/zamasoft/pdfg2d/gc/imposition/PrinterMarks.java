package net.zamasoft.pdfg2d.gc.imposition;

import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.PageLayoutGlyphHandler;

/**
 * Draws printer's marks (トンボ) for imposed sheets: Japanese-style double
 * corner marks (コーナートンボ), center marks (センタートンボ), spine marks
 * and the marginal note line.
 * <p>
 * All coordinates are relative to the top-left corner of the <em>trim
 * area</em> including its margins — i.e. the same origin the page content
 * uses <em>before</em> translating by {@link Trims#left()}/{@link Trims#top()}.
 * The page area of size {@code pageWidth} × {@code pageHeight} sits at
 * {@code (trims.left, trims.top)}. This matches the historical Foliojet
 * imposition geometry, from which this code was ported.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public final class PrinterMarks {

	/** Line width of the marks, in points. */
	public static final double LINE_WIDTH = 0.3;

	private PrinterMarks() {
		// static use only
	}

	private static void prepareStroke(final GC gc) {
		gc.setStrokePaint(GrayColor.BLACK);
		gc.setLineWidth(LINE_WIDTH);
		gc.setLinePattern(GC.STROKE_SOLID);
	}

	/**
	 * Draws the four Japanese double corner marks (コーナートンボ) around the
	 * page area. The inner line of each pair marks the finished size and the
	 * outer line the bleed (cutting margin).
	 *
	 * @param gc         the graphics context
	 * @param pageWidth  the trimmed page width
	 * @param pageHeight the trimmed page height
	 * @param trims      the trim margins hosting the marks
	 * @throws GraphicsException if drawing fails
	 */
	public static void drawCrop(final GC gc, final double pageWidth, final double pageHeight, final Trims trims)
			throws GraphicsException {
		prepareStroke(gc);
		final var cuttingMargin = trims.cuttingMargin();

		final var louter = trims.left() - cuttingMargin;
		final var touter = trims.top() - cuttingMargin;
		final var router = pageWidth + trims.left() + cuttingMargin;
		final var rinner = pageWidth + trims.left();
		final var right = pageWidth + trims.left() + trims.right();
		final var bouter = pageHeight + trims.top() + cuttingMargin;
		final var binner = pageHeight + trims.top();
		final var bottom = pageHeight + trims.top() + trims.bottom();

		// Top left
		gc.draw(new Line2D.Double(0, touter, louter, touter));
		gc.draw(new Line2D.Double(0, trims.top(), louter, trims.top()));
		gc.draw(new Line2D.Double(louter, 0, louter, touter));
		gc.draw(new Line2D.Double(trims.left(), 0, trims.left(), touter));

		// Top right
		gc.draw(new Line2D.Double(router, touter, right, touter));
		gc.draw(new Line2D.Double(router, trims.top(), right, trims.top()));
		gc.draw(new Line2D.Double(router, 0, router, touter));
		gc.draw(new Line2D.Double(rinner, 0, rinner, touter));

		// Bottom left
		gc.draw(new Line2D.Double(0, bouter, louter, bouter));
		gc.draw(new Line2D.Double(0, binner, louter, binner));
		gc.draw(new Line2D.Double(louter, bottom, louter, bouter));
		gc.draw(new Line2D.Double(trims.left(), bottom, trims.left(), bouter));

		// Bottom right
		gc.draw(new Line2D.Double(router, bouter, right, bouter));
		gc.draw(new Line2D.Double(router, binner, right, binner));
		gc.draw(new Line2D.Double(router, bottom, router, bouter));
		gc.draw(new Line2D.Double(rinner, bottom, rinner, bouter));
	}

	/**
	 * Draws the four center marks (センタートンボ) at the middles of the page
	 * edges.
	 *
	 * @param gc         the graphics context
	 * @param pageWidth  the trimmed page width
	 * @param pageHeight the trimmed page height
	 * @param trims      the trim margins hosting the marks
	 * @throws GraphicsException if drawing fails
	 */
	public static void drawCross(final GC gc, final double pageWidth, final double pageHeight, final Trims trims)
			throws GraphicsException {
		prepareStroke(gc);
		final var cuttingMargin = trims.cuttingMargin();

		{
			final var middle = pageWidth / 2.0 + trims.left();
			final var lmiddle = middle - (trims.left() + trims.right()) / 2.0;
			final var rmiddle = middle + (trims.left() + trims.right()) / 2.0;
			final var tmiddle = trims.top() - cuttingMargin * 2.0;
			final var touter = trims.top() - cuttingMargin;
			final var bmiddle = pageHeight + trims.top() + cuttingMargin * 2.0;
			final var bouter = pageHeight + trims.top() + cuttingMargin;
			final var bottom = pageHeight + trims.top() + trims.bottom();

			// Top
			gc.draw(new Line2D.Double(lmiddle, tmiddle, rmiddle, tmiddle));
			gc.draw(new Line2D.Double(middle, 0, middle, touter));

			// Bottom
			gc.draw(new Line2D.Double(lmiddle, bmiddle, rmiddle, bmiddle));
			gc.draw(new Line2D.Double(middle, bottom, middle, bouter));
		}

		{
			final var lmiddle = trims.left() - cuttingMargin * 2.0;
			final var middle = pageHeight / 2.0 + trims.top();
			final var tmiddle = middle - (trims.top() + trims.bottom()) / 2.0;
			final var bmiddle = middle + (trims.top() + trims.bottom()) / 2.0;
			final var louter = trims.left() - cuttingMargin;
			final var rmiddle = pageWidth + trims.left() + cuttingMargin * 2.0;
			final var router = pageWidth + trims.left() + cuttingMargin;
			final var right = pageWidth + trims.left() + trims.right();

			// Left
			gc.draw(new Line2D.Double(lmiddle, tmiddle, lmiddle, bmiddle));
			gc.draw(new Line2D.Double(0, middle, louter, middle));

			// Right
			gc.draw(new Line2D.Double(rmiddle, tmiddle, rmiddle, bmiddle));
			gc.draw(new Line2D.Double(right, middle, router, middle));
		}
	}

	/**
	 * Draws the spine marks (背トンボ) for a cover spread: vertical lines
	 * marking both edges of the spine at the top and bottom trim margins.
	 *
	 * @param gc         the graphics context
	 * @param pageWidth  the trimmed spread width (including the spine)
	 * @param pageHeight the trimmed page height
	 * @param trims      the trim margins hosting the marks
	 * @param spineWidth the spine width; nothing is drawn when {@code <= 0}
	 * @throws GraphicsException if drawing fails
	 */
	public static void drawSpine(final GC gc, final double pageWidth, final double pageHeight, final Trims trims,
			final double spineWidth) throws GraphicsException {
		if (spineWidth <= 0) {
			return;
		}
		prepareStroke(gc);
		final var middle = pageWidth / 2.0 + trims.left();
		final var bouter = pageHeight + trims.top() + trims.cuttingMargin();
		final var touter = trims.top() - trims.cuttingMargin();
		final var bottom = pageHeight + trims.top() + trims.bottom();
		final var lbc = middle - spineWidth / 2.0;
		final var rbc = middle + spineWidth / 2.0;

		gc.draw(new Line2D.Double(lbc, 0, lbc, touter));
		gc.draw(new Line2D.Double(rbc, 0, rbc, touter));
		gc.draw(new Line2D.Double(lbc, bottom, lbc, bouter));
		gc.draw(new Line2D.Double(rbc, bottom, rbc, bouter));
	}

	/**
	 * Draws compact corner cut marks around a trimmed cell of a multi-up
	 * grid: two short lines per corner, kept {@code gap} away from the cell
	 * so they never touch bleeding content.
	 *
	 * @param gc      the graphics context
	 * @param trimmed the trimmed cell rectangle (finished size)
	 * @param gap     the distance between the cell edge and the mark start
	 *                (normally the cutting margin)
	 * @param length  the mark line length
	 * @throws GraphicsException if drawing fails
	 */
	public static void drawCellCorners(final GC gc, final Rectangle2D trimmed, final double gap, final double length)
			throws GraphicsException {
		prepareStroke(gc);
		final var x0 = trimmed.getMinX();
		final var y0 = trimmed.getMinY();
		final var x1 = trimmed.getMaxX();
		final var y1 = trimmed.getMaxY();
		for (final var corner : new double[][] { { x0, y0, -1, -1 }, { x1, y0, 1, -1 }, { x0, y1, -1, 1 },
				{ x1, y1, 1, 1 } }) {
			final var cx = corner[0];
			final var cy = corner[1];
			final var dx = corner[2];
			final var dy = corner[3];
			// Horizontal tick aligned with the cell's horizontal edge
			gc.draw(new Line2D.Double(cx + dx * gap, cy, cx + dx * (gap + length), cy));
			// Vertical tick aligned with the cell's vertical edge
			gc.draw(new Line2D.Double(cx, cy + dy * gap, cx, cy + dy * (gap + length)));
		}
	}

	/**
	 * Draws a single line of text with the given font policy, wrapping at
	 * {@code width}. Used for the marginal note (page number, job name) on
	 * imposed sheets.
	 *
	 * @param gc         the graphics context
	 * @param fontPolicy the font policy used to resolve fonts
	 * @param fontSize   the font size in points
	 * @param text       the text to draw
	 * @param x          the left edge of the text
	 * @param y          the top edge of the text
	 * @param width      the maximum line width
	 * @throws GraphicsException if drawing fails
	 */
	public static void drawText(final GC gc, final FontPolicyList fontPolicy, final double fontSize, final String text,
			final double x, final double y, final double width) throws GraphicsException {
		gc.begin();
		gc.transform(AffineTransform.getTranslateInstance(x, y));

		final var lineHandler = new PageLayoutGlyphHandler(gc);
		lineHandler.setLineAdvance(width);

		final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules(null), lineHandler);
		tlf.setFontFamilies(FontFamilyList.SERIF);
		tlf.setFontPolicy(fontPolicy);
		tlf.setFontSize(fontSize);
		tlf.characters(text);
		tlf.flush();

		lineHandler.close();
		gc.end();
	}

	/**
	 * Draws the marginal note at its conventional position: inside the top
	 * trim margin, left-aligned with the page area, occupying at most half
	 * of the sheet width.
	 *
	 * @param gc         the graphics context
	 * @param fontPolicy the font policy used to resolve fonts
	 * @param text       the note text
	 * @param pageWidth  the trimmed page width
	 * @param trims      the trim margins
	 * @throws GraphicsException if drawing fails
	 */
	public static void drawNote(final GC gc, final FontPolicyList fontPolicy, final String text,
			final double pageWidth, final Trims trims) throws GraphicsException {
		final var paperWidth = pageWidth + trims.left() + trims.right();
		final var fontSize = trims.top() / 6.0;
		final var y = trims.top() - trims.cuttingMargin() - fontSize;
		final var width = paperWidth / 2.0 - trims.left();
		drawText(gc, fontPolicy, fontSize, text, trims.left(), y, width);
	}
}
