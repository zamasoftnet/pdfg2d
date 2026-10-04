package net.zamasoft.pdfg2d.pdf.gc;

import java.awt.geom.Rectangle2D;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * Paints the content of a Form XObject that a page referenced before its
 * content was known, such as a page number of a later page.
 * <p>
 * The writer calls the painter once, when it closes, after every page has been
 * drawn. The form is not written at all until then, so a document may reserve
 * many of them without holding streams open.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 * @see PDFGC#drawDeferredForm(double, double, DeferredFormPainter)
 */
@FunctionalInterface
public interface DeferredFormPainter {
	/**
	 * Paints the form content.
	 * <p>
	 * The coordinate system has its origin at the top-left corner of the
	 * reserved rectangle, like the page that drew the form. Content may extend
	 * beyond the rectangle; return the painted bounds so that the form's
	 * bounding box does not clip it.
	 * </p>
	 *
	 * @param gc the graphics context of the form
	 * @return the painted bounds in the same coordinates, or {@code null} for the
	 *         reserved rectangle
	 * @throws GraphicsException if painting fails
	 */
	Rectangle2D paint(GC gc) throws GraphicsException;
}
