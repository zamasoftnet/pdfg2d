package net.zamasoft.pdfg2d.pdf.params;

/**
 * The default color rendering intent ({@code ri} operator) for the
 * document's content: how out-of-gamut colors are mapped by the output
 * device.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public enum RenderingIntent {

	/** Preserve the overall appearance (photographs). */
	PERCEPTUAL("Perceptual"),

	/** Map white points, clip out-of-gamut colors (proofs, logos). */
	RELATIVE_COLORIMETRIC("RelativeColorimetric"),

	/** Preserve saturation (business graphics). */
	SATURATION("Saturation"),

	/** Absolute colorimetry, no white point mapping. */
	ABSOLUTE_COLORIMETRIC("AbsoluteColorimetric");

	private final String pdfName;

	RenderingIntent(final String pdfName) {
		this.pdfName = pdfName;
	}

	/**
	 * Returns the PDF name of this intent.
	 *
	 * @return the name used with the {@code ri} operator
	 */
	public String pdfName() {
		return this.pdfName;
	}
}
