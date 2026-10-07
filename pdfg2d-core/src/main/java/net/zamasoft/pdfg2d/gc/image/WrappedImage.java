package net.zamasoft.pdfg2d.gc.image;

/**
 * Represents a wrapped image.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class WrappedImage implements Image {
	protected final Image image;

	/**
	 * Creates a new WrappedImage.
	 * 
	 * @param image the image to wrap
	 */
	public WrappedImage(final Image image) {
		this.image = image;
	}

	/**
	 * Returns the wrapped image.
	 * 
	 * @return the image
	 */
	public Image getImage() {
		return this.image;
	}

	/**
	 * Delegates the intrinsic dimension type to the original image (2026-08-27).
	 * Without delegation, it defaults to SIZE, so background drawing treats a viewBox-only SVG
	 * wrapped in a px-to-pt {@code TransformedImage} as having an intrinsic size.
	 */
	@Override
	public Intrinsic getIntrinsic() {
		return this.image.getIntrinsic();
	}
}
