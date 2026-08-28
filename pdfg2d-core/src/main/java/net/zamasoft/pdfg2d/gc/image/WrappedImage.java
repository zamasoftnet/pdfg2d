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
	 * 固有寸法の種別は元画像へ委譲します(2026-08-27)。委譲しないと
	 * 既定のSIZEになり、px→ptの{@code TransformedImage}で包まれた
	 * viewBoxのみのSVGが背景描画で原寸扱いされる。
	 */
	@Override
	public Intrinsic getIntrinsic() {
		return this.image.getIntrinsic();
	}
}
