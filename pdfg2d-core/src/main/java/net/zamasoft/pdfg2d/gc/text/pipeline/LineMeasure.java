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
	 * Returns the line index from which the width no longer varies: for every
	 * {@code i >= easyLine()}, {@code width(i) == width(easyLine())} must hold
	 * (TeX's {@code easy_line}). The solver merges active candidates whose line
	 * numbers are both at or beyond this index — without it, paragraphs whose
	 * breakpoints are dense (CJK justification breaks at every character
	 * boundary) accumulate one active per distinct line count and the solver
	 * degenerates to minutes of work. Lambdas keep the safe default
	 * ({@code Integer.MAX_VALUE} = every line distinct); use
	 * {@link #firstLineThenConstant} or {@link #fixed} to benefit.
	 *
	 * @return the first line index of the constant-width tail
	 */
	default int easyLine() {
		return Integer.MAX_VALUE;
	}

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
		return new LineMeasure() {
			@Override
			public double width(final int lineIndex) {
				return width;
			}

			@Override
			public int easyLine() {
				return 0;
			}
		};
	}

	/**
	 * A measure with a distinct first-line width (text-indent) and a constant
	 * width for every following line.
	 *
	 * @param firstLineWidth the available width of line 0
	 * @param lineWidth      the available width of every later line
	 * @return the measure
	 */
	static LineMeasure firstLineThenConstant(final double firstLineWidth, final double lineWidth) {
		if (!(firstLineWidth > 0) || Double.isInfinite(firstLineWidth) || !(lineWidth > 0)
				|| Double.isInfinite(lineWidth)) {
			throw new IllegalArgumentException(
					"widths must be positive and finite: " + firstLineWidth + ", " + lineWidth);
		}
		return new LineMeasure() {
			@Override
			public double width(final int lineIndex) {
				return lineIndex == 0 ? firstLineWidth : lineWidth;
			}

			@Override
			public int easyLine() {
				return 1;
			}
		};
	}
}
