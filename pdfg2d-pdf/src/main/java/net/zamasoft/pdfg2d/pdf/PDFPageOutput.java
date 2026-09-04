package net.zamasoft.pdfg2d.pdf;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.OutputStream;

import net.zamasoft.pdfg2d.pdf.annot.Annot;

/**
 * Output for PDF page content.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class PDFPageOutput extends PDFGraphicsOutput {
	/**
	 * Creates a new PDFPageOutput.
	 * 
	 * @param pdfWriter the creating PDFWriter
	 * @param out       the output stream
	 * @param width     page width
	 * @param height    page height
	 * @throws IOException in case of I/O error
	 */
	protected PDFPageOutput(final PDFWriter pdfWriter, final OutputStream out, final double width, final double height)
			throws IOException {
		super(pdfWriter, out, width, height);
	}

	/**
	 * Adds an annotation.
	 * 
	 * @param annot the annotation
	 * @throws IOException in case of I/O error
	 */
	public abstract void addAnnotation(Annot annot) throws IOException;

	/**
	 * Adds a document fragment.
	 * 
	 * @param id       unique name in the document
	 * @param location location
	 * @throws IOException in case of I/O error
	 */
	public abstract void addFragment(String id, Point2D location) throws IOException;

	/**
	 * Starts a bookmark hierarchy.
	 * <p>
	 * The number of endBookmark calls does not need to match startBookmark.
	 * Unclosed hierarchies are automatically closed when document construction is
	 * complete.
	 * </p>
	 * 
	 * @param title    the title
	 * @param location the location
	 * @throws IOException in case of I/O error
	 */
	public abstract void startBookmark(String title, Point2D location) throws IOException;

	/**
	 * Ends a bookmark hierarchy.
	 * 
	 * @throws IOException in case of I/O error
	 */
	public abstract void endBookmark() throws IOException;

	/**
	 * Sets the MediaBox, which defines the full page boundary.
	 *
	 * @param mediaBox the media box rectangle
	 */
	public abstract void setMediaBox(Rectangle2D mediaBox);

	/**
	 * Sets the CropBox, which defines the visible area of the page.
	 *
	 * @param cropBox the crop box rectangle, or {@code null} to use MediaBox
	 */
	public abstract void setCropBox(Rectangle2D cropBox);

	/**
	 * Sets the BleedBox, which defines the region to which page content should be
	 * clipped when output in a production environment (PDF 1.4+).
	 *
	 * @param bleedBox the bleed box rectangle, or {@code null}
	 */
	public abstract void setBleedBox(Rectangle2D bleedBox);

	/**
	 * Sets the TrimBox, which defines the intended finished dimensions of the page
	 * after trimming (PDF 1.4+).
	 *
	 * @param trimBox the trim box rectangle, or {@code null}
	 */
	public abstract void setTrimBox(Rectangle2D trimBox);

	/**
	 * Sets the ArtBox, which defines the extent of the page's meaningful content
	 * (PDF 1.4+).
	 *
	 * @param artBox the art box rectangle, or {@code null}
	 */
	public abstract void setArtBox(Rectangle2D artBox);

	/**
	 * Opens a logical structure element in a tagged PDF; content drawn until
	 * the matching {@link #endStructElement()} is attached to it. No-op for
	 * untagged documents. May be nested.
	 *
	 * @param role the structure type (e.g. {@code "P"}, {@code "H1"},
	 *             {@code "Table"}, {@code "Figure"})
	 */
	public void beginStructElement(final String role) {
	}

	/**
	 * Opens a table-header ({@code TH}) structure element with a cell scope.
	 * No-op for untagged documents.
	 *
	 * @param role  the structure type (typically {@code "TH"})
	 * @param scope the header scope: {@code "Row"}, {@code "Column"} or
	 *              {@code "Both"}
	 */
	public void beginStructElement(final String role, final String scope) {
		this.beginStructElement(role);
	}

	/** Closes the innermost structure element opened by {@link #beginStructElement}. */
	public void endStructElement() {
	}

	/**
	 * Declares a structure element in logical (declaration) order without
	 * opening it for content; paint content into it later with
	 * {@link #beginStructContent(StructureRef)}. Use this when paint order
	 * (e.g. z-index) differs from document order. No-op (returns
	 * {@code null}) for untagged documents.
	 *
	 * @param parent the declared parent, or {@code null} for the root
	 * @param role   the structure type
	 * @param scope  the table-header scope, or {@code null}
	 * @return the element handle, or {@code null} when untagged
	 * @since 1.3
	 */
	public StructureRef declareStructElement(final StructureRef parent, final String role, final String scope) {
		return null;
	}

	/**
	 * Routes subsequently painted content (and annotation associations) to a
	 * declared structure element until {@link #endStructContent()}. No-op
	 * for untagged documents or a {@code null} target.
	 *
	 * @since 1.3
	 */
	public void beginStructContent(final StructureRef target) {
		this.beginStructContent(target, null);
	}

	/**
	 * Routes subsequently painted content to a declared structure element and
	 * supplies its logical-order key. MCIDs and the parent tree remain in paint
	 * order; the hint affects only the element's emitted {@code /K} array.
	 * The hint applies only when {@code target} is an element declared by this
	 * writer. For a {@code null} target or a reference owned by another writer,
	 * neither the current routing element nor its active ordering hint changes;
	 * the call still balances a matching {@link #endStructContent()}.
	 *
	 * @param target a structure element declared by this writer, or {@code null}
	 * @param order  the logical-order hint, or {@code null} for paint order
	 * @since 1.3
	 */
	public void beginStructContent(final StructureRef target, final StructureOrder order) {
	}

	/**
	 * Ends the routing started by {@link #beginStructContent(StructureRef)}.
	 *
	 * @since 1.3
	 */
	public void endStructContent() {
	}

	/**
	 * Adds an interactive AcroForm field (text input, checkbox, choice or
	 * button) to this page. No-op if the implementation does not support forms.
	 *
	 * @param field the form field
	 * @throws java.io.IOException if an I/O error occurs
	 */
	public void addFormField(final net.zamasoft.pdfg2d.pdf.form.FormField field) throws java.io.IOException {
	}

	/**
	 * Adds a group of mutually-exclusive radio buttons as a single AcroForm
	 * field with multiple widget kids. No-op if the implementation does not
	 * support forms.
	 *
	 * @param group the radio group
	 * @throws java.io.IOException if an I/O error occurs
	 */
	public void addRadioGroup(final net.zamasoft.pdfg2d.pdf.form.RadioGroup group) throws java.io.IOException {
	}
}
