package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;

import net.zamasoft.pdfg2d.pdf.ObjectRef;

/**
 * Manages the "Names" dictionary in the PDF Catalog.
 * This class handles mapping of top-level name tree categories (e.g., Dests,
 * EmbeddedFiles)
 * to their respective root nodes.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class NameDictionaryFlow {
	private final XRefImpl xref;

	private final PDFFragmentOutputImpl out, catalogFlow;

	private boolean hasEntry = false;

	/**
	 * Constructs a new NameDictionaryFlow associated with the given PDF writer.
	 * <p>
	 * A forked fragment is allocated from the writer's main flow so that the Names
	 * dictionary object can be inserted at the correct position in the output
	 * once all name-tree categories are known.
	 * </p>
	 *
	 * @param pdfWriter the owning PDF writer
	 * @throws IOException if an I/O error occurs while forking the fragment
	 */
	public NameDictionaryFlow(final PDFWriterImpl pdfWriter) throws IOException {
		this.xref = pdfWriter.xref;
		final var mainFlow = pdfWriter.mainFlow;
		this.out = mainFlow.forkFragment();
		this.catalogFlow = pdfWriter.catalogFlow;
	}

	/**
	 * Registers a name-tree category in the PDF Names dictionary.
	 * <p>
	 * The first call to this method lazily allocates the Names dictionary object
	 * and writes a {@code Names} entry in the document catalog pointing to it.
	 * Subsequent calls add further key/reference pairs to the open dictionary.
	 * </p>
	 *
	 * @param key the name-tree category key (e.g. {@code "Dests"} or
	 *            {@code "EmbeddedFiles"})
	 * @param ref the indirect object reference for the root node of that name tree
	 * @throws IOException if an I/O error occurs while writing to the output
	 */
	public void addEntry(final String key, final ObjectRef ref) throws IOException {
		if (!this.hasEntry) {
			final var nameTreeRef = this.xref.nextObjectRef();
			this.catalogFlow.writeName("Names");
			this.catalogFlow.writeObjectRef(nameTreeRef);
			this.catalogFlow.lineBreak();

			this.out.startObject(nameTreeRef);
			this.out.startHash();
			this.hasEntry = true;
		}

		this.out.writeName(key);
		this.out.writeObjectRef(ref);
		this.out.lineBreak();
	}

	/**
	 * Finalizes the Names dictionary by closing the hash and the underlying
	 * fragment.
	 * <p>
	 * If no entries were added, only the fragment is closed without writing any
	 * dictionary content.
	 * </p>
	 *
	 * @throws IOException if an I/O error occurs during finalization
	 */
	public void close() throws IOException {
		if (this.hasEntry) {
			this.out.endHash();
			this.out.endObject();
		}
		this.out.close();
	}
}
