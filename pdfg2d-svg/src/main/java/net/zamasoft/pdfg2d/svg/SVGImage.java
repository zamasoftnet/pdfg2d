package net.zamasoft.pdfg2d.svg;

import java.awt.Graphics2D;

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
public record SVGImage(GraphicsNode node, double width, double height) implements Image {

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
			this.node.paint(g2d);
			g2d.dispose();
		}
	}

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
