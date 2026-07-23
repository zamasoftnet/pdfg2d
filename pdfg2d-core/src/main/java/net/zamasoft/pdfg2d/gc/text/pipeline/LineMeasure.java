package net.zamasoft.pdfg2d.gc.text.pipeline;

/**
 * Supplies the available width for each line of a paragraph. Line indexes are
 * zero-based in paragraph order. Implementations must be pure: the breaker may
 * query any index any number of times while exploring candidate breakings.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
@FunctionalInterface
public interface LineMeasure {

	/**
	 * Returns the available width for the given line.
	 *
	 * @param lineIndex the zero-based line index
	 * @return the available width; must be positive and finite
	 */
	double width(int lineIndex);

	/**
	 * A measure with the same width for every line.
	 *
	 * @param width the available width
	 * @return the fixed measure
	 */
	static LineMeasure fixed(final double width) {
		if (!(width > 0) || Double.isInfinite(width)) {
			throw new IllegalArgumentException("width must be positive and finite: " + width);
		}
		return lineIndex -> width;
	}
}
