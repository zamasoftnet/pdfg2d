package net.zamasoft.pdfg2d.gc.imposition;

/**
 * Placement of a single logical page on a sheet of paper: which paper
 * orientation to use, how large the page area is after alignment scaling,
 * and the transforms the renderer must apply.
 * <p>
 * The computation mirrors the sequence historically used by the Foliojet
 * imposition: (1) optionally rotate the content or swap the paper when the
 * page and paper orientations differ, (2) center the trimmed area on the
 * paper, (3) draw the printer's marks, (4) translate by the trim margins and
 * scale to the aligned page size.
 * </p>
 *
 * @param actualPaperWidth  paper width after any orientation swap
 * @param actualPaperHeight paper height after any orientation swap
 * @param rotateContent     whether the renderer must rotate the content by
 *                          -90° (paper kept as configured)
 * @param actualPageWidth   the page area width after alignment
 * @param actualPageHeight  the page area height after alignment
 * @param hscale            horizontal content scale (1 when not scaling)
 * @param vscale            vertical content scale (1 when not scaling)
 * @param centerX           translation that centers the trimmed area
 * @param centerY           translation that centers the trimmed area
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public record PagePlacement(
		double actualPaperWidth,
		double actualPaperHeight,
		boolean rotateContent,
		double actualPageWidth,
		double actualPageHeight,
		double hscale,
		double vscale,
		double centerX,
		double centerY) {

	/** How the logical page is fitted into the trimmed paper area. */
	public enum Align {
		/** Keep the page size and center it. */
		CENTER,
		/** Stretch the page to fill the trimmed paper area. */
		FIT_TO_PAPER,
		/** Scale the page to the trimmed area, preserving the aspect ratio. */
		PRESERVE_ASPECT_RATIO
	}

	/** How orientation mismatches between page and paper are resolved. */
	public enum AutoRotate {
		/** Never rotate. */
		NONE,
		/** Rotate the drawn content by -90°, keeping the paper as is. */
		CONTENT,
		/** Swap the paper's width and height instead. */
		PAPER
	}

	/**
	 * Computes the placement of a page on a sheet of paper.
	 *
	 * @param paperWidth  configured paper width, in points
	 * @param paperHeight configured paper height, in points
	 * @param pageWidth   logical page width, in points
	 * @param pageHeight  logical page height, in points
	 * @param trims       trim margins around the page area
	 * @param align       page alignment mode
	 * @param autoRotate  orientation mismatch handling
	 * @return the computed placement
	 */
	public static PagePlacement compute(final double paperWidth, final double paperHeight, final double pageWidth,
			final double pageHeight, final Trims trims, final Align align, final AutoRotate autoRotate) {
		final boolean mismatch = (paperWidth > paperHeight) != (pageWidth > pageHeight);
		double actualPaperWidth = paperWidth;
		double actualPaperHeight = paperHeight;
		var rotateContent = false;
		switch (autoRotate) {
			case NONE -> {
				// keep as configured
			}
			case CONTENT -> {
				if (mismatch) {
					// The physical paper stays as configured; all subsequent
					// math happens in the rotated coordinate space.
					actualPaperWidth = paperHeight;
					actualPaperHeight = paperWidth;
					rotateContent = true;
				}
			}
			case PAPER -> {
				if (mismatch) {
					actualPaperWidth = paperHeight;
					actualPaperHeight = paperWidth;
				}
			}
		}

		double actualPageWidth;
		double actualPageHeight;
		switch (align) {
			case CENTER -> {
				actualPageWidth = pageWidth;
				actualPageHeight = pageHeight;
			}
			case FIT_TO_PAPER -> {
				actualPageWidth = actualPaperWidth - trims.left() - trims.right();
				actualPageHeight = actualPaperHeight - trims.top() - trims.bottom();
			}
			case PRESERVE_ASPECT_RATIO -> {
				actualPageWidth = pageWidth;
				actualPageHeight = pageHeight;
				final var maxWidth = actualPaperWidth - trims.left() - trims.right();
				final var maxHeight = actualPaperHeight - trims.top() - trims.bottom();
				if (actualPageWidth != maxWidth) {
					actualPageHeight = actualPageHeight * maxWidth / actualPageWidth;
					actualPageWidth = maxWidth;
				}
				if (actualPageHeight > maxHeight) {
					actualPageWidth = actualPageWidth * maxHeight / actualPageHeight;
					actualPageHeight = maxHeight;
				}
			}
			default -> throw new IllegalStateException();
		}

		final var hscale = (pageWidth != 0) ? actualPageWidth / pageWidth : 0;
		final var vscale = (pageHeight != 0) ? actualPageHeight / pageHeight : 0;

		final var centerX = (actualPaperWidth - actualPageWidth - trims.left() - trims.right()) / 2.0;
		final var centerY = (actualPaperHeight - actualPageHeight - trims.top() - trims.bottom()) / 2.0;

		return new PagePlacement(actualPaperWidth, actualPaperHeight, rotateContent, actualPageWidth,
				actualPageHeight, hscale, vscale, centerX, centerY);
	}
}
