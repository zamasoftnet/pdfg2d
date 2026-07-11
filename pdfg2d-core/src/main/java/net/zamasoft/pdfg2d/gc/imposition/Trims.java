package net.zamasoft.pdfg2d.gc.imposition;

/**
 * Trim margins around a finished page on an imposed sheet: the space between
 * the paper edge and the trimmed (finished) page area, which hosts crop
 * marks and the printer's note line.
 * <p>
 * The {@code cuttingMargin} (ドブ, bleed allowance) is the innermost part of
 * each trim margin: printed content extends into it so that cutting
 * inaccuracies do not leave white slivers. Crop marks are drawn outside the
 * cutting margin so they never touch bleeding content.
 * </p>
 *
 * @param top           trim margin above the page, in points
 * @param right         trim margin to the right of the page, in points
 * @param bottom        trim margin below the page, in points
 * @param left          trim margin to the left of the page, in points
 * @param cuttingMargin bleed allowance inside each trim margin, in points
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public record Trims(double top, double right, double bottom, double left, double cuttingMargin) {

	/** No trims and no bleed: content occupies the whole paper. */
	public static final Trims NONE = new Trims(0, 0, 0, 0, 0);

	public Trims {
		if (top < 0 || right < 0 || bottom < 0 || left < 0 || cuttingMargin < 0) {
			throw new IllegalArgumentException("Trim values must not be negative.");
		}
	}

	/**
	 * Creates uniform trims with the given margin on all sides.
	 *
	 * @param margin        the trim margin for all four sides, in points
	 * @param cuttingMargin the bleed allowance, in points
	 * @return the trims
	 */
	public static Trims uniform(final double margin, final double cuttingMargin) {
		return new Trims(margin, margin, margin, margin, cuttingMargin);
	}
}
