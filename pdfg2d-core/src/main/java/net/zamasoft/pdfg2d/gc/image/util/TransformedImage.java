package net.zamasoft.pdfg2d.gc.image.util;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;

/**
 * Represents an image scaled to UA units.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class TransformedImage extends WrappedImage {
	private final AffineTransform at;
	private Double cachedWidth = null;
	private Double cachedHeight = null;

	/**
	 * Creates a new TransformedImage.
	 * 
	 * @param image the image to transform
	 * @param at    the affine transform
	 */
	public TransformedImage(final Image image, final AffineTransform at) {
		super(image);
		assert image != null;
		assert at != null;
		this.at = at;
	}

	/**
	 * Returns the affine transform.
	 * 
	 * @return the affine transform
	 */
	public AffineTransform getTransform() {
		return this.at;
	}

	@Override
	public void drawTo(final GC gc) {
		try (final var gcState = gc.begin()) {
			gc.transform(this.at);
			this.image.drawTo(gc);
		}
	}

	@Override
	public String getAltString() {
		return this.image.getAltString();
	}

	@Override
	public double getWidth() {
		if (cachedWidth != null) {
			return cachedWidth;
		}
		calculateDimensions();
		return cachedWidth;
	}

	@Override
	public double getHeight() {
		if (cachedHeight != null) {
			return cachedHeight;
		}
		calculateDimensions();
		return cachedHeight;
	}

	private void calculateDimensions() {
		final double w = image.getWidth();
		final double h = image.getHeight();

		final var pts = new Point2D[] { new Point2D.Double(0, 0), new Point2D.Double(w, 0), new Point2D.Double(0, h),
				new Point2D.Double(w, h) };

		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;

		for (final var pt : pts) {
			final var dst = at.transform(pt, null);
			minX = Math.min(minX, dst.getX());
			maxX = Math.max(maxX, dst.getX());
			minY = Math.min(minY, dst.getY());
			maxY = Math.max(maxY, dst.getY());
		}

		cachedWidth = Math.abs(maxX - minX);
		cachedHeight = Math.abs(maxY - minY);
	}

	@Override
	public String toString() {
		return super.toString() + "/image=" + this.image + ",at=" + this.at;
	}
}