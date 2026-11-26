package net.zamasoft.pdfg2d.svg;

import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
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
 * @param viewBox the root's viewBox and preserveAspectRatio, or null (then {@link #viewport} gives null)
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record SVGImage(GraphicsNode node, double width, double height, Intrinsic intrinsic, ViewBox viewBox)
		implements Image {

	/** Constructs an image with intrinsic dimensions (for compatibility with previous behavior). */
	public SVGImage(GraphicsNode node, double width, double height) {
		this(node, width, height, Intrinsic.SIZE);
	}

	/** Constructs an image without a viewBox: it is only scaled to another size. */
	public SVGImage(GraphicsNode node, double width, double height, Intrinsic intrinsic) {
		this(node, width, height, intrinsic, null);
	}

	/**
	 * The root's viewBox and preserveAspectRatio, with which {@link #viewport} lays the image out anew (2026-11-26).
	 *
	 * @param x      the viewBox's x
	 * @param y      the viewBox's y
	 * @param width  the viewBox's width
	 * @param height the viewBox's height
	 * @param align  the alignment, a {@code SVGPreserveAspectRatio.SVG_PRESERVEASPECTRATIO_*} constant
	 * @param meet   true for meet, false for slice
	 */
	public record ViewBox(double x, double y, double width, double height, short align, boolean meet) {
		AffineTransform fit(final double viewportWidth, final double viewportHeight) {
			return org.apache.batik.bridge.ViewBox.getPreserveAspectRatioTransform(
					new float[] { (float) this.x, (float) this.y, (float) this.width, (float) this.height }, this.align,
					this.meet, (float) viewportWidth, (float) viewportHeight);
		}
	}

	/**
	 * The image laid out in a viewport of another size (2026-11-26): its viewBox fits the viewport by its
	 * preserveAspectRatio, as browsers do. A box of another ratio than the image's stretched the drawing: an icon's
	 * circle in a tall box became a tall ellipse (BBC Sport's search icon). Null without a viewBox, or with
	 * preserveAspectRatio="none", which stretch.
	 */
	@Override
	public Image viewport(final double width, final double height) {
		if (this.viewBox == null || this.viewBox.align() == org.w3c.dom.svg.SVGPreserveAspectRatio.SVG_PRESERVEASPECTRATIO_NONE
				|| this.viewBox.align() == org.w3c.dom.svg.SVGPreserveAspectRatio.SVG_PRESERVEASPECTRATIO_UNKNOWN
				|| !(this.viewBox.width() > 0) || !(this.viewBox.height() > 0) || !(width > 0) || !(height > 0)
				|| !(this.width > 0) || !(this.height > 0)) {
			return null;
		}
		try {
			final AffineTransform at = this.viewBox.fit(width, height);
			at.concatenate(this.viewBox.fit(this.width, this.height).createInverse());
			return new Fitted(this, width, height, at);
		} catch (final java.awt.geom.NoninvertibleTransformException e) {
			return null;
		}
	}

	/** An SVG image in a viewport of its own size ({@link SVGImage#viewport}). */
	private record Fitted(SVGImage image, double width, double height, AffineTransform at) implements Image {
		@Override
		public Intrinsic getIntrinsic() {
			return this.image.getIntrinsic();
		}

		@Override
		public double getWidth() {
			return this.width;
		}

		@Override
		public double getHeight() {
			return this.height;
		}

		@Override
		public void drawTo(final GC gc) throws GraphicsException {
			try (final var gcState = gc.begin()) {
				if (!this.image.viewBox().meet()) {
					// slice overflows the viewport, which clips it
					gc.clip(new Rectangle2D.Double(0, 0, this.width, this.height));
				}
				gc.transform(this.at);
				this.image.drawTo(gc);
			}
		}

		@Override
		public String getAltString() {
			return this.image.getAltString();
		}

		@Override
		public Image viewport(final double width, final double height) {
			return this.image.viewport(width, height);
		}
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
			final SVGBridgeGraphics2D g2d = new SVGBridgeGraphics2D(gc);
			final double scale = deviceScale(gc, this.width, this.height);
			g2d.setDeviceScale(scale);
			g2d.setRasterBounds(rasterBounds(this.width, this.height, scale));
			// Antialiased, as a browser draws SVG's shape-rendering and text-rendering auto (2026-11-26). Batik draws a
			// filter's source into a raster with the hints of this Graphics2D, and without them the thin strokes of
			// small text broke up there (a 5pt Mincho's horizontals, under a device pixel at 300 dpi, under an ink
			// filter). Vector drawing does not use them; an element's shape-rendering or text-rendering still sets its own
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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

	/** The larger singular value of a transform: the most it enlarges in any direction. */
	static double maxScale(final AffineTransform at) {
		final double a = at.getScaleX(), b = at.getShearY(), c = at.getShearX(), d = at.getScaleY();
		final double s1 = a * a + b * b + c * c + d * d, s2 = a * d - b * c;
		return Math.sqrt((s1 + Math.sqrt(Math.max(0, s1 * s1 - 4 * s2 * s2))) / 2);
	}

	/** The most device pixels the whole image may take at its device scale (16M, about 4000 x 4000). */
	static final double MAX_DEVICE_PIXELS = 16.0 * 1024 * 1024;

	/**
	 * Device pixels per SVG unit (2026-11-26): the output's raster resolution ({@link GC#getRasterResolution()}, 300
	 * dpi for a PDF) at the size the image is drawn ({@link GC#getTransform()}), so that a filter or a mask Batik
	 * rasterizes is as fine as the CSS filters of the page. It was one pixel per SVG unit (39 dpi for an SVG drawn at
	 * 1.86pt a unit: coarse filtered text). Capped so that the whole image stays within {@link #MAX_DEVICE_PIXELS}.
	 */
	static double deviceScale(final GC gc, final double width, final double height) {
		final AffineTransform at = gc.getTransform();
		double deviceScale = gc.getRasterResolution() / 72 * (at == null ? 1 : maxScale(at));
		final double area = width * height;
		if (area > 0 && Double.isFinite(area)) {
			deviceScale = Math.min(deviceScale, Math.sqrt(MAX_DEVICE_PIXELS / area));
		}
		return deviceScale > 0 && Double.isFinite(deviceScale) ? deviceScale : 1;
	}

	/**
	 * Where Batik may rasterize, in SVG units (2026-11-26): the image's box widened by the same margin on each side, as
	 * far as {@link #MAX_DEVICE_PIXELS} allows at the device scale. Batik rasterizes a filter, a mask or a clip path
	 * only where it meets the clip the Graphics2D reports, so the cap holds for a filter region larger than the image
	 * too (codex: a 2000-unit region of a 100-unit SVG came to 625M pixels). Not a clip of the drawing, but Batik also
	 * leaves out a node whose bounds miss it (AbstractGraphicsNode.paint): the box alone left out vector content wholly
	 * outside an SVG with overflow: visible (codex). The margin reaches past the page at the usual sizes (a 100pt SVG
	 * at 300 dpi: about 440pt), and is 0 where the image's own box already takes the whole cap.
	 */
	static Rectangle2D rasterBounds(final double width, final double height, final double scale) {
		final double area = MAX_DEVICE_PIXELS / (scale * scale);
		// (width + 2m)(height + 2m) = area
		final double b = width + height;
		final double m = (-b + Math.sqrt(b * b - 4 * (width * height - area))) / 4;
		final double margin = m > 0 && Double.isFinite(m) ? m : 0;
		return new Rectangle2D.Double(-margin, -margin, width + 2 * margin, height + 2 * margin);
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
