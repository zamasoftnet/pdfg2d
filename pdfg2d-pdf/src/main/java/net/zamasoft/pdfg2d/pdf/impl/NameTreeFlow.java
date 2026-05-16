package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;
import java.util.SortedMap;
import java.util.TreeMap;

import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Base class for managing PDF name trees.
 * Name trees are used to map string keys to PDF objects (e.g., Destinations,
 * embedded files).
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
abstract class NameTreeFlow {
	public final PDFFragmentOutputImpl out;

	private final PDFWriterImpl pdfWriter;

	private final NameDictionaryFlow nameDict;

	private final String key;

	private SortedMap<String, Object> nameToEntry = null;

	/**
	 * Constructs a new NameTreeFlow.
	 * <p>
	 * A forked fragment is allocated from the writer's main flow to buffer the
	 * name-tree objects until {@link #close()} serializes them.
	 * </p>
	 *
	 * @param pdfWriter the owning PDF writer
	 * @param key       the dictionary key used in the PDF Names dictionary (e.g.
	 *                  {@code "Dests"} or {@code "EmbeddedFiles"})
	 * @throws IOException if an I/O error occurs while forking the fragment
	 */
	public NameTreeFlow(final PDFWriterImpl pdfWriter, final String key) throws IOException {
		this.pdfWriter = pdfWriter;
		this.key = key;

		final var mainFlow = pdfWriter.mainFlow;
		this.out = mainFlow.forkFragment();
		this.nameDict = pdfWriter.nameDict;
	}

	/**
	 * Adds a name-to-value mapping that will be serialized into the PDF name tree
	 * when {@link #close()} is called.
	 * <p>
	 * Entries are stored in a {@link java.util.TreeMap} so that the resulting
	 * {@code Names} array is lexicographically sorted, which is required by the
	 * PDF specification.
	 * </p>
	 *
	 * @param name  the string key used to look up the entry
	 * @param entry the associated value; its type depends on the concrete subclass
	 */
	public void addEntry(final String name, final Object entry) {
		if (this.nameToEntry == null) {
			this.nameToEntry = new TreeMap<>();
		}
		this.nameToEntry.put(name, entry);
	}

	/**
	 * Serializes all accumulated entries into the PDF output and closes the
	 * underlying fragment.
	 * <p>
	 * For PDF 1.2 and earlier the name tree must have an intermediate
	 * {@code Kids} level even when there is only a single leaf node; this method
	 * handles that automatically based on the document version.
	 * </p>
	 *
	 * @throws IOException if an I/O error occurs during serialization
	 */
	public void close() throws IOException {
		if (this.nameToEntry != null) {
			final var xref = this.pdfWriter.xref;
			final var rootRef = xref.nextObjectRef();
			this.nameDict.addEntry(this.key, rootRef);

			this.out.startObject(rootRef);
			this.out.startHash();

			// PDF 1.2 or earlier does not support top-level Names array in the root node.
			// It requires a Kids array even if there is only one page-level node.
			final var version = this.pdfWriter.params.version();
			if (version.v <= PDFParams.Version.V_1_2.v) {
				this.out.writeName("Kids");
				this.out.startArray();
				final var kidRef = xref.nextObjectRef();
				this.out.writeObjectRef(kidRef);
				this.out.endArray();
				this.out.lineBreak();

				this.out.endHash();
				this.out.endObject();

				this.out.startObject(kidRef);
				this.out.startHash();

				this.out.writeName("Limits");
				this.out.startArray();
				this.out.writeText(this.nameToEntry.firstKey());
				this.out.writeText(this.nameToEntry.lastKey());
				this.out.endArray();
				this.out.lineBreak();
			}

			this.out.writeName("Names");
			this.out.startArray();
			for (final var entry : this.nameToEntry.entrySet()) {
				this.out.writeText(entry.getKey());
				this.writeEntry(entry.getValue());
			}
			this.out.endArray();
			this.out.lineBreak();

			this.out.endHash();
			this.out.endObject();
		}
		this.out.close();
	}

	/**
	 * Writes a single name-tree value to the PDF output.
	 * <p>
	 * Subclasses implement this method to serialize the concrete value type
	 * (e.g. an object reference for destinations, or a file specification
	 * dictionary for embedded files).
	 * </p>
	 *
	 * @param entry the value to write; must be the type expected by the subclass
	 * @throws IOException if an I/O error occurs
	 */
	protected abstract void writeEntry(Object entry) throws IOException;
}
