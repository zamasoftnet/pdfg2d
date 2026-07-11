package net.zamasoft.pdfg2d.pdf.imposition;

import java.text.MessageFormat;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.imposition.PagePlacement;
import net.zamasoft.pdfg2d.gc.imposition.Trims;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.util.PDFUtils;

/**
 * Base class for placing logical pages onto physical PDF pages (imposition,
 * 面付け), drawing printer's marks and maintaining the PDF page boxes
 * (MediaBox / BleedBox / TrimBox) consistently with the trim configuration.
 * <p>
 * Usage: configure the instance, then repeat {@link #nextPage()} — draw —
 * {@link #closePage()} for every logical page, and call {@link #finish()}
 * once at the end (required for impositions that buffer sheet layouts, such
 * as booklets).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public abstract class PDFImposition {

	protected final PDFWriter pdfWriter;

	protected int pageNumber = 0;

	protected PagePlacement.Align align = PagePlacement.Align.CENTER;

	protected PagePlacement.AutoRotate autoRotate = PagePlacement.AutoRotate.NONE;

	/** Corner marks (コーナートンボ). */
	protected boolean crop = false;

	/** Center marks (センタートンボ). */
	protected boolean cross = false;

	/** Whether to clip content to the page area plus bleed. */
	protected boolean clip = true;

	/** Whether to set MediaBox/BleedBox/TrimBox from the imposition geometry. */
	protected boolean pageBoxes = true;

	protected Trims trims = Trims.uniform(1.0 * PDFUtils.POINTS_PER_CM,
			PDFUtils.CUTTING_MARGIN_MM * PDFUtils.POINTS_PER_MM);

	/** Spine width for cover spreads; {@code 0} disables the spine marks. */
	protected double spineWidth = 0;

	/** Logical page (finished) size, in points. */
	protected double pageWidth = PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM);

	protected double pageHeight = PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM);

	/** Physical paper size, in points. */
	protected double paperWidth = this.pageHeight;

	protected double paperHeight = this.pageHeight;

	/**
	 * Marginal note pattern; {@code {0}} expands to the physical sheet
	 * number.
	 */
	protected MessageFormat note = null;

	/** Font policy for the note text. */
	protected FontPolicyList notePolicy = new FontPolicyList(
			new FontPolicyList.FontPolicy[] { FontPolicyList.FontPolicy.CORE, FontPolicyList.FontPolicy.CID_KEYED });

	protected PDFImposition(final PDFWriter pdfWriter) {
		if (pdfWriter == null) {
			throw new NullPointerException("pdfWriter");
		}
		this.pdfWriter = pdfWriter;
	}

	/**
	 * Starts the next logical page and returns the graphics context to draw
	 * it with. The origin is the top-left corner of the finished page.
	 *
	 * @return the graphics context for the logical page
	 * @throws GraphicsException if an error occurs
	 */
	public abstract GC nextPage() throws GraphicsException;

	/**
	 * Finishes the current logical page.
	 *
	 * @throws GraphicsException if an error occurs
	 */
	public abstract void closePage() throws GraphicsException;

	/**
	 * Flushes any buffered sheets. Must be called once after the last page
	 * and before closing the writer.
	 *
	 * @throws GraphicsException if an error occurs
	 */
	public void finish() throws GraphicsException {
		// nothing buffered by default
	}

	public final PagePlacement.Align getAlign() {
		return this.align;
	}

	public final void setAlign(final PagePlacement.Align align) {
		this.align = align;
	}

	public final PagePlacement.AutoRotate getAutoRotate() {
		return this.autoRotate;
	}

	public final void setAutoRotate(final PagePlacement.AutoRotate autoRotate) {
		this.autoRotate = autoRotate;
	}

	public final Trims getTrims() {
		return this.trims;
	}

	public final void setTrims(final Trims trims) {
		if (trims == null) {
			throw new NullPointerException("trims");
		}
		this.trims = trims;
	}

	public final double getSpineWidth() {
		return this.spineWidth;
	}

	public final void setSpineWidth(final double spineWidth) {
		this.spineWidth = spineWidth;
	}

	public final boolean isCrop() {
		return this.crop;
	}

	/** Enables the corner marks (コーナートンボ). */
	public final void setCrop(final boolean crop) {
		this.crop = crop;
	}

	public final boolean isCross() {
		return this.cross;
	}

	/** Enables the center marks (センタートンボ). */
	public final void setCross(final boolean cross) {
		this.cross = cross;
	}

	public final boolean isClip() {
		return this.clip;
	}

	public final void setClip(final boolean clip) {
		this.clip = clip;
	}

	public final boolean isPageBoxes() {
		return this.pageBoxes;
	}

	/** Enables MediaBox/BleedBox/TrimBox maintenance (default on). */
	public final void setPageBoxes(final boolean pageBoxes) {
		this.pageBoxes = pageBoxes;
	}

	public final double getPageWidth() {
		return this.pageWidth;
	}

	public final void setPageWidth(final double pageWidth) {
		this.pageWidth = pageWidth;
	}

	public final double getPageHeight() {
		return this.pageHeight;
	}

	public final void setPageHeight(final double pageHeight) {
		this.pageHeight = pageHeight;
	}

	public final double getPaperWidth() {
		return this.paperWidth;
	}

	public final void setPaperWidth(final double paperWidth) {
		this.paperWidth = paperWidth;
	}

	public final double getPaperHeight() {
		return this.paperHeight;
	}

	public final void setPaperHeight(final double paperHeight) {
		this.paperHeight = paperHeight;
	}

	/** Sizes the paper to the page plus the horizontal trim margins. */
	public void fitPaperWidth() {
		this.paperWidth = this.pageWidth + this.trims.left() + this.trims.right();
	}

	/** Sizes the paper to the page plus the vertical trim margins. */
	public void fitPaperHeight() {
		this.paperHeight = this.pageHeight + this.trims.top() + this.trims.bottom();
	}

	public final String getNote() {
		return (this.note == null) ? null : this.note.toPattern();
	}

	/**
	 * Sets the marginal note pattern printed in the top trim margin;
	 * {@code {0}} expands to the sheet number. {@code null} disables it.
	 */
	public final void setNote(final String note) {
		this.note = (note == null) ? null : new MessageFormat(note);
	}

	public final FontPolicyList getNotePolicy() {
		return this.notePolicy;
	}

	/**
	 * Sets the font policy used for the note text. For PDF/A or PDF/X output
	 * this must resolve to an embeddable font.
	 */
	public final void setNotePolicy(final FontPolicyList notePolicy) {
		if (notePolicy == null) {
			throw new NullPointerException("notePolicy");
		}
		this.notePolicy = notePolicy;
	}
}
