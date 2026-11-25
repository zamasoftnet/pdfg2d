package net.zamasoft.pdfg2d.svg;

import java.awt.Graphics2D;
import java.awt.image.ImagingOpException;
import java.awt.image.RasterFormatException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.batik.gvt.GraphicsNode;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Image implementation that wraps an SVG graphics node.
 * 
 * <p>
 * This record represents an SVG image as a Batik {@link GraphicsNode} with
 * specified dimensions. When drawn, it renders the SVG content using
 * {@link SVGBridgeGraphics2D}.
 * 
 * @param node   the GVT graphics node containing the SVG content
 * @param width  the width in user units
 * @param height the height in user units
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record SVGImage(GraphicsNode node, double width, double height, Intrinsic intrinsic) implements Image {

	/** Constructs an image with intrinsic dimensions (for compatibility with previous behavior). */
	public SVGImage(GraphicsNode node, double width, double height) {
		this(node, width, height, Intrinsic.SIZE);
	}

	@Override
	public Intrinsic getIntrinsic() {
		return this.intrinsic;
	}

	/**
	 * Returns the GVT graphics node.
	 *
	 * @return the root graphics node
	 */
	public GraphicsNode getNode() {
		return this.node;
	}

	/**
	 * Returns the width of this image.
	 *
	 * @return the width in user units
	 */
	@Override
	public double getWidth() {
		return this.width;
	}

	/**
	 * Returns the height of this image.
	 *
	 * @return the height in user units
	 */
	@Override
	public double getHeight() {
		return this.height;
	}

	/**
	 * Draws this image to the graphics context.
	 * 
	 * <p>
	 * The SVG content is rendered by painting the GVT graphics node
	 * through an {@link SVGBridgeGraphics2D} adapter.
	 *
	 * @param gc the graphics context to draw to
	 * @throws GraphicsException if a graphics error occurs
	 */
	@Override
	public void drawTo(final GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {
			final Graphics2D g2d = new SVGBridgeGraphics2D(gc);
			try {
				this.node.paint(g2d);
			} catch (final ImagingOpException | RasterFormatException e) {
				// A raster operation AWT cannot do (Batik's filters) leaves the rest of this image unpainted instead of
				// failing the document (2026-11-26: Inkscape's feConvolveMatrix icon, "Unable to convolve src image"
				// from ConvolveOp). Only AWT's raster failures: a failure of the GC itself (the PDF/A nesting limit)
				// may leave its state inconsistent, so it still fails the document
				LOG.log(Level.WARNING, "SVG painting failed", e);
			} finally {
				// closes the GC states of the Graphics2D children Batik left open (also when painting failed)
				g2d.dispose();
			}
		}
	}

	private static final Logger LOG = Logger.getLogger(SVGImage.class.getName());

	/**
	 * Returns alternative text for this image.
	 *
	 * @return always null (not available for SVG images)
	 */
	@Override
	public String getAltString() {
		return null;
	}
}
