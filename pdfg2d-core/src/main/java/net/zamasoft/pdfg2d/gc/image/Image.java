package net.zamasoft.pdfg2d.gc.image;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * Represents an image.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface Image {
	/**
	 * Intrinsic dimension type (css-images-3 §sizing, 2026-08-27).
	 * Raster images always use SIZE. SVGs without width/height attributes have only
	 * the viewBox aspect ratio (RATIO), or no dimension information (NONE) if viewBox is also absent.
	 * For background-size:auto, background drawing uses this to choose the default sizing rule
	 * (SIZE=original size, RATIO=contain constraint, NONE=fill the positioning area).
	 */
	public enum Intrinsic {
		SIZE, RATIO, NONE;
	}

	/**
	 * Returns the intrinsic dimension type (see {@link Intrinsic}).
	 * {@link #getWidth()}/{@link #getHeight()} return concrete drawing values
	 * (viewBox dimensions or the default 300x150) even for RATIO/NONE.
	 * This method distinguishes whether those values are intrinsic dimensions or substitutes.
	 *
	 * @return intrinsic dimension type
	 */
	public default Intrinsic getIntrinsic() {
		return Intrinsic.SIZE;
	}

	/**
	 * Returns the image width.
	 * 
	 * @return the width
	 */
	public double getWidth();

	/**
	 * Returns the image height.
	 * 
	 * @return the height
	 */
	public double getHeight();

	/**
	 * Draws the image.
	 * 
	 * @param gc the graphics context
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void drawTo(final GC gc) throws GraphicsException;

	/**
	 * Returns the alternative string for the image.
	 * 
	 * @return the alternative string
	 */
	public String getAltString();
}
