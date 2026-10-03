package net.zamasoft.pdfg2d.g2d.util;

import java.io.IOException;

/**
 * An image was refused before decoding because its pixel count, read from the
 * image header, exceeds the configured limit (or the header gives no usable
 * size while a limit is in force).
 * <p>
 * Callers must not retry such an image through another decoder: the refusal
 * is deterministic, and the point of the limit is that the pixels are never
 * allocated.
 * </p>
 */
public class ImageTooLargeException extends IOException {
	private static final long serialVersionUID = 1L;

	private final long width;
	private final long height;
	private final long limit;

	/**
	 * Creates an exception for an image of the given size.
	 *
	 * @param width  image width in pixels, or -1 if the header gives none
	 * @param height image height in pixels, or -1 if the header gives none
	 * @param limit  the pixel-count limit in force
	 * @param cause  why the size could not be read, or {@code null}
	 */
	public ImageTooLargeException(final long width, final long height, final long limit, final Throwable cause) {
		super(width < 0 || height < 0 ? "too-large: unknown size > " + limit
				: "too-large " + width + "x" + height + " > " + limit, cause);
		this.width = width;
		this.height = height;
		this.limit = limit;
	}

	/** @return the image width in pixels, or -1 if unknown */
	public long getWidth() {
		return this.width;
	}

	/** @return the image height in pixels, or -1 if unknown */
	public long getHeight() {
		return this.height;
	}

	/** @return the pixel-count limit that was exceeded */
	public long getLimit() {
		return this.limit;
	}
}
