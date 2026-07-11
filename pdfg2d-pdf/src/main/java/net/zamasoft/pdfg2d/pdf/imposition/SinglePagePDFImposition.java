package net.zamasoft.pdfg2d.pdf.imposition;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.imposition.PagePlacement;
import net.zamasoft.pdfg2d.gc.imposition.PrinterMarks;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * One logical page per sheet with printer's marks — the layout used for
 * prepress submission (トンボ付き入稿データ). In addition to drawing the
 * marks it maintains the PDF page boxes: MediaBox is the paper, TrimBox the
 * finished page and BleedBox the finished page expanded by the cutting
 * margin, so PDF/X box validation and the marks always agree.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public class SinglePagePDFImposition extends PDFImposition {

	protected PDFPageOutput pageOut;

	protected PDFGC gc;

	/** State begun in {@link #nextPage()} and closed in {@link #closePage()}. */
	protected GC.State gcState;

	protected double actualPageWidth, actualPageHeight;

	public SinglePagePDFImposition(final PDFWriter pdfWriter) {
		super(pdfWriter);
	}

	@Override
	public GC nextPage() throws GraphicsException {
		++this.pageNumber;
		final var placement = PagePlacement.compute(this.paperWidth, this.paperHeight, this.pageWidth,
				this.pageHeight, this.trims, this.align, this.autoRotate);
		this.actualPageWidth = placement.actualPageWidth();
		this.actualPageHeight = placement.actualPageHeight();

		try {
			// AUTO_ROTATE_CONTENT keeps the configured paper; everything else
			// uses the (possibly swapped) actual paper size.
			if (this.autoRotate == PagePlacement.AutoRotate.CONTENT) {
				this.pageOut = this.pdfWriter.nextPage(this.paperWidth, this.paperHeight);
			} else {
				this.pageOut = this.pdfWriter.nextPage(placement.actualPaperWidth(), placement.actualPaperHeight());
			}
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		this.gc = new PDFGC(this.pageOut);
		this.gcState = this.gc.begin();

		if (placement.rotateContent()) {
			final var at = AffineTransform.getRotateInstance(-Math.PI / 2.0);
			at.translate(-placement.actualPaperWidth(), 0);
			this.gc.transform(at);
		}

		this.gc.transform(AffineTransform.getTranslateInstance(placement.centerX(), placement.centerY()));

		// Page boxes reflect the imposed geometry. When the content is
		// rotated the boxes are computed in unrotated paper coordinates, so
		// they are only set for the non-rotated cases.
		if (this.pageBoxes && !placement.rotateContent()
				&& this.pdfWriter.getParams().version().v >= PDFParams.Version.V_1_4.v) {
			final var trimX = placement.centerX() + this.trims.left();
			final var trimY = placement.centerY() + this.trims.top();
			this.pageOut.setTrimBox(new Rectangle2D.Double(trimX, trimY,
					this.actualPageWidth, this.actualPageHeight));
			final var m = this.trims.cuttingMargin();
			this.pageOut.setBleedBox(new Rectangle2D.Double(trimX - m, trimY - m,
					this.actualPageWidth + m * 2.0, this.actualPageHeight + m * 2.0));
		}

		// Printer's marks and the note line
		this.drawMarks();
		if (this.note != null) {
			final var text = this.note.format(new Object[] { String.valueOf(this.pageNumber) });
			PrinterMarks.drawNote(this.gc, this.notePolicy, text, this.actualPageWidth, this.trims);
		}

		// Shift into the page area
		if (this.trims.left() != 0 || this.trims.top() != 0) {
			this.gc.transform(AffineTransform.getTranslateInstance(this.trims.left(), this.trims.top()));
		}

		// Clip to the page area plus bleed, and apply the alignment scale
		final var m = this.trims.cuttingMargin();
		final var bgX = -m;
		final var bgY = -m;
		final var bgW = this.pageWidth + m * 2.0;
		final var bgH = this.pageHeight + m * 2.0;
		switch (this.align) {
			case CENTER -> {
				if (this.clip) {
					this.gc.clip(new Rectangle2D.Double(bgX, bgY, bgW, bgH));
				}
			}
			case FIT_TO_PAPER, PRESERVE_ASPECT_RATIO -> {
				final var hscale = placement.hscale();
				final var vscale = placement.vscale();
				if (this.clip) {
					this.gc.clip(new Rectangle2D.Double(bgX, bgY, bgW * hscale, bgH * vscale));
				}
				if (hscale != 0 && vscale != 0) {
					this.gc.transform(AffineTransform.getScaleInstance(hscale, vscale));
				}
			}
			default -> throw new IllegalStateException();
		}

		return this.gc;
	}

	/** Draws the enabled marks around the placed page area. */
	protected void drawMarks() throws GraphicsException {
		if (this.crop) {
			PrinterMarks.drawCrop(this.gc, this.actualPageWidth, this.actualPageHeight, this.trims);
		}
		if (this.cross) {
			PrinterMarks.drawCross(this.gc, this.actualPageWidth, this.actualPageHeight, this.trims);
		}
		PrinterMarks.drawSpine(this.gc, this.actualPageWidth, this.actualPageHeight, this.trims, this.spineWidth);
	}

	@Override
	public void closePage() throws GraphicsException {
		this.gcState.close();
		try {
			this.gc.close();
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
			this.gc = null;
			this.gcState = null;
			this.pageOut = null;
		}
	}
}
