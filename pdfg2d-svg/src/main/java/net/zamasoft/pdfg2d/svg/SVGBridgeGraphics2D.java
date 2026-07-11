package net.zamasoft.pdfg2d.svg;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.PatternPaint;

import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.g2d.util.G2DUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.Pattern;

/**
 * Graphics2D bridge implementation for SVG rendering.
 * 
 * <p>
 * This class extends {@link BridgeGraphics2D} to provide specialized handling
 * for Batik's {@link PatternPaint}, converting it to this library's
 * {@link Pattern}
 * for proper rendering in PDF output.
 * 
 * @since 1.0
 */
public class SVGBridgeGraphics2D extends BridgeGraphics2D {

	/**
	 * Creates a new SVG bridge graphics context.
	 *
	 * @param gc the underlying graphics context
	 * @throws GraphicsException if initialization fails
	 */
	public SVGBridgeGraphics2D(final GC gc) throws GraphicsException {
		super(gc, SVGGraphicsConfiguration.SHARED_INSTANCE);
	}

	/**
	 * Sets the paint used for drawing operations.
	 * 
	 * <p>
	 * This method provides special handling for Batik's {@link PatternPaint},
	 * converting it to this library's {@link Pattern} type. For other paint types,
	 * delegates to the standard AWT paint conversion.
	 *
	 * @param paint the paint to use, or null to do nothing
	 */
	@Override
	public void setPaint(final java.awt.Paint paint) {
		if (paint == null) {
			return;
		}

		this.paint = paint;
		if (paint instanceof java.awt.Color) {
			this.foreground = (java.awt.Color) paint;
		}

		Paint spaint;
		if (paint instanceof final PatternPaint patternPaint) {
			spaint = convertPatternPaint(patternPaint);
		} else {
			// Batik's own gradient classes first, then the standard AWT paints
			spaint = BatikPaintUtils.fromBatikPaint(paint);
			if (spaint == null) {
				spaint = G2DUtils.fromAwtPaint(paint);
			}
		}

		if (spaint != null) {
			this.gc.setStrokePaint(spaint);
			this.gc.setFillPaint(spaint);
		}
	}

	/**
	 * Converts a Batik PatternPaint to this library's Pattern type.
	 *
	 * @param patternPaint the Batik pattern paint to convert
	 * @return the converted pattern
	 */
	private Pattern convertPatternPaint(final PatternPaint patternPaint) {
		final GraphicsNode node = patternPaint.getGraphicsNode();
		final Rectangle2D rect = patternPaint.getPatternRect();

		// Adjust node transform to account for pattern origin
		final AffineTransform nat = node.getTransform();
		nat.translate(-rect.getX(), -rect.getY());
		node.setTransform(nat);

		// Create image from graphics node
		final Image image = new SVGImage(node, rect.getWidth(), rect.getHeight());

		// Create pattern transform including origin offset
		final var at = new AffineTransform(patternPaint.getPatternTransform());
		at.translate(rect.getX(), rect.getY());

		return new Pattern(image, at);
	}
}
