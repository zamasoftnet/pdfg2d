package net.zamasoft.pdfg2d.gc.font;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Represents the metrics information for multiple fonts.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record FontListMetrics(FontMetrics[] metrics, double maxAscent, double maxDescent, double maxXHeight)
		implements Serializable {

	/**
	 * Creates a new FontListMetrics.
	 * 
	 * @param metrics the array of font metrics
	 */
	public FontListMetrics(final FontMetrics[] metrics) {
		this(metrics,
				Arrays.stream(metrics).mapToDouble(FontMetrics::getAscent).max().orElse(0),
				Arrays.stream(metrics).mapToDouble(FontMetrics::getDescent).max().orElse(0),
				Arrays.stream(metrics).mapToDouble(FontMetrics::getXHeight).max().orElse(0));
	}

	/**
	 * Returns the number of font metrics.
	 * 
	 * @return the number of font metrics
	 */
	public int getLength() {
		return this.metrics.length;
	}

	/**
	 * Returns the font metrics at the specified index.
	 * 
	 * @param i the index
	 * @return the font metrics
	 */
	public FontMetrics getFontMetrics(final int i) {
		return this.metrics[i];
	}

	/**
	 * Returns the advance of a space (U+0020) in the font that draws it: the
	 * first font of the list that can display a space (2026-10-04).
	 * <p>
	 * Word spaces used the first font of the list. A generic family that starts
	 * with the emoji font (which has no space, and answers a space advance of
	 * half an em) widened every word space of text in the default fonts to
	 * half an em, although the space was drawn by the text font. A font without
	 * a source is taken as it is, since whether it has a space cannot be known.
	 * </p>
	 *
	 * @return the space advance, or that of the first font when none can display
	 *         a space
	 */
	public double getSpaceAdvance() {
		for (final FontMetrics fm : this.metrics) {
			if (fm.getFontSource() == null || fm.getFontSource().canDisplay(' ')) {
				return fm.getSpaceAdvance();
			}
		}
		return this.metrics[0].getSpaceAdvance();
	}

	/**
	 * Returns the maximum ascent among the fonts.
	 * 
	 * @return the maximum ascent
	 */
	public double getMaxAscent() {
		return this.maxAscent;
	}

	/**
	 * Returns the maximum descent among the fonts.
	 * 
	 * @return the maximum descent
	 */
	public double getMaxDescent() {
		return this.maxDescent;
	}

	/**
	 * Returns the maximum x-height among the fonts.
	 * 
	 * @return the maximum x-height
	 */
	public double getMaxXHeight() {
		return this.maxXHeight;
	}

	@Override
	public String toString() {
		return "FontListMetrics:[" + Arrays.stream(this.metrics)
				.map(String::valueOf)
				.collect(java.util.stream.Collectors.joining(",")) + "]";
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o)
			return true;
		if (!(o instanceof FontListMetrics))
			return false;
		FontListMetrics that = (FontListMetrics) o;
		return Double.compare(that.maxAscent, maxAscent) == 0 &&
				Double.compare(that.maxDescent, maxDescent) == 0 &&
				Double.compare(that.maxXHeight, maxXHeight) == 0 &&
				Arrays.equals(metrics, that.metrics);
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(metrics);
		result = 31 * result + Double.hashCode(maxAscent);
		result = 31 * result + Double.hashCode(maxDescent);
		result = 31 * result + Double.hashCode(maxXHeight);
		return result;
	}
}