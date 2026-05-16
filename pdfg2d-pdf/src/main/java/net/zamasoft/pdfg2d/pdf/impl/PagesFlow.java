package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;

import net.zamasoft.pdfg2d.pdf.ObjectRef;

/**
 * Manages PDF page structure (Pages tree).
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class PagesFlow {
	private final PDFWriterImpl pdfWriter;

	/** Root page reference. */
	private final ObjectRef rootPageRef;

	/** Child page reference flow. */
	private final PDFFragmentOutputImpl pagesKidsFlow;

	/** Page count flow. */
	private final PDFFragmentOutputImpl pageCountFlow;

	/** Page count counter. */
	private int pageCount = 0;

	/**
	 * Constructs a new PagesFlow and writes the root {@code Pages} dictionary
	 * skeleton into the main output flow.
	 * <p>
	 * The {@code Kids} array and {@code Count} value are deferred into forked
	 * fragments that are filled in as pages are created and during
	 * {@link #close()}.
	 * </p>
	 *
	 * @param pdfWriter   the owning PDF writer
	 * @param rootPageRef the indirect object reference for the root Pages node
	 * @throws IOException if an I/O error occurs while writing the dictionary
	 *                     skeleton
	 */
	public PagesFlow(final PDFWriterImpl pdfWriter, final ObjectRef rootPageRef) throws IOException {
		this.pdfWriter = pdfWriter;
		this.rootPageRef = rootPageRef;

		final PDFFragmentOutputImpl mainFlow = pdfWriter.mainFlow;
		mainFlow.startObject(rootPageRef);

		mainFlow.startHash();

		mainFlow.writeName("Type");
		mainFlow.writeName("Pages");
		mainFlow.lineBreak();

		mainFlow.writeName("Kids");
		mainFlow.startArray();
		this.pagesKidsFlow = mainFlow.forkFragment();
		mainFlow.endArray();
		mainFlow.lineBreak();

		mainFlow.writeName("Count");
		mainFlow.write(' ');
		this.pageCountFlow = mainFlow.forkFragment();
		mainFlow.lineBreak();

		mainFlow.endHash();
		mainFlow.endObject();
	}

	/**
	 * Creates a new page of the given dimensions and appends its reference to the
	 * {@code Kids} array of the root Pages node.
	 *
	 * @param width  page width in user units (points)
	 * @param height page height in user units (points)
	 * @return the {@link PDFPageOutputImpl} for writing content to the new page
	 * @throws IOException if an I/O error occurs while starting the page object
	 */
	public PDFPageOutputImpl createPage(final double width, final double height) throws IOException {
		// Page Object
		++this.pageCount;

		return new PDFPageOutputImpl(this.pdfWriter, this.rootPageRef, this.pagesKidsFlow, width, height);
	}

	/**
	 * Finalizes the Pages tree by writing the accumulated page count into the
	 * deferred {@code Count} fragment and closing the {@code Kids} and count
	 * fragment outputs.
	 *
	 * @throws IOException if an I/O error occurs during finalization
	 */
	public void close() throws IOException {
		this.pageCountFlow.writeInt(this.pageCount);
		this.pageCountFlow.close();
		this.pagesKidsFlow.close();
	}
}
