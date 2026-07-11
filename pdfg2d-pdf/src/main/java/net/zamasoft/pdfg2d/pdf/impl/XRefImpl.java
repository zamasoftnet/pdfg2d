package net.zamasoft.pdfg2d.pdf.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.zamasoft.zstream.io.FragmentedOutput.PositionInfo;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.util.encryption.Encryption;

/**
 * Implementation of the PDF cross-reference table (xref).
 * This class tracks object positions and generates the trailer and xref table
 * during the finalization of the PDF document.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class XRefImpl implements XRef {
	/** Cross-reference table. */
	private final List<ObjectRef> xref = new ArrayList<>();

	private final ObjectRef rootRef;

	protected final PDFFragmentOutputImpl mainFlow;

	private Map<String, Object> attributes;
	private final Map<ObjectRef, LinkedHashSet<ObjectRef>> dependencies = new HashMap<>();

	private static final byte[] EOF = { '%', '%', 'E', 'O', 'F' };

	/**
	 * Creates a new cross-reference table and immediately opens the root Catalog
	 * object in {@code mainFlow}.
	 *
	 * @param mainFlow the primary output fragment into which the xref table and
	 *                 trailer will eventually be written
	 * @throws IOException if an I/O error occurs while writing the object header
	 */
	XRefImpl(final PDFFragmentOutputImpl mainFlow) throws IOException {
		this.mainFlow = mainFlow;
		this.rootRef = this.nextObjectRef();
		this.mainFlow.startObject(rootRef);
	}

	/**
	 * Creates and returns the next object reference.
	 * 
	 * @return A new ObjectRefImpl instance.
	 */
	public ObjectRef nextObjectRef() {
		final var ref = new ObjectRefImpl(this.xref.size() + 1);
		this.xref.add(ref);
		return ref;
	}

	/**
	 * Returns a snapshot of all object references registered so far, in creation
	 * order.
	 *
	 * @return mutable copy of the internal object list
	 */
	public List<ObjectRef> getObjects() {
		return new ArrayList<>(this.xref);
	}

	ObjectRef getRootRef() {
		return this.rootRef;
	}

	/**
	 * Finalizes the PDF by writing the xref table and trailer.
	 * 
	 * @param posInfo   Position information of fragments.
	 * @param infoRef   Reference to the Info dictionary.
	 * @param fileid    The document IDs.
	 * @param encrypter Encryption settings, if any.
	 * @return The byte offset of the xref table.
	 * @throws IOException If an I/O error occurs.
	 */
	long close(final PositionInfo posInfo, final ObjectRef infoRef, final byte[][] fileid,
			final Encryption encrypter) throws IOException {
		return this.close(posInfo, infoRef, fileid, encrypter, false);
	}

	/**
	 * Writes the xref table and trailer, optionally omitting the final
	 * {@code startxref} entry for linearized PDF assembly.
	 *
	 * @param posInfo    fragment position snapshot
	 * @param infoRef    reference to the Info dictionary, or {@code null}
	 * @param fileid     two-element array of 16-byte document IDs, or {@code null}
	 * @param encrypter  encryption settings, or {@code null}
	 * @param linearized {@code true} when called from the linearized-PDF path;
	 *                   suppresses the {@code startxref…%%EOF} footer
	 * @return absolute byte offset of the xref table in the output file
	 * @throws IOException if an I/O error occurs
	 */
	long close(final PositionInfo posInfo, final ObjectRef infoRef, final byte[][] fileid,
			final Encryption encrypter, boolean linearized) throws IOException {
		// Calculate the starting position of the xref table
		final long xrefPosition = posInfo.getPosition(this.mainFlow.getId()) + this.mainFlow.getLength();

		// Generate trailer content in a memory buffer first
		final var trailerBytes = new ByteArrayOutputStream();
		try (final var trailerFlow = new PDFOutput(trailerBytes, "ISO-8859-1")) {
			trailerFlow.writeOperator("trailer");
			trailerFlow.startHash();

			trailerFlow.writeName("Size");
			trailerFlow.writeInt(this.xref.size() + 1);
			trailerFlow.lineBreak();

			trailerFlow.writeName("Root");
			trailerFlow.writeObjectRef(this.rootRef);
			trailerFlow.lineBreak();

			if (infoRef != null) {
				trailerFlow.writeName("Info");
				trailerFlow.writeObjectRef(infoRef);
				trailerFlow.lineBreak();
			}

			if (fileid != null) {
				trailerFlow.writeName("ID");
				trailerFlow.startArray();
				trailerFlow.writeBytes8(fileid[0], 0, fileid[0].length);
				trailerFlow.writeBytes8(fileid[1], 0, fileid[1].length);
				trailerFlow.endArray();
				trailerFlow.lineBreak();
			}

			if (encrypter != null) {
				trailerFlow.writeName("Encrypt");
				trailerFlow.writeObjectRef(encrypter.getObjectRef());
				trailerFlow.lineBreak();
			}

			trailerFlow.endHash();

			if (!linearized) {
				trailerFlow.writeOperator("startxref");
				trailerFlow.lineBreak();
				trailerFlow.write(String.valueOf(xrefPosition));

				trailerFlow.lineBreak();
				trailerFlow.write(EOF);
				trailerFlow.lineBreak();
			}
		}
		final var trailer = trailerBytes.toString("ISO-8859-1");

		// Write xref table header
		this.mainFlow.writeOperator("xref");
		this.mainFlow.lineBreak();
		this.mainFlow.writeInt(0);
		this.mainFlow.writeInt(this.xref.size() + 1);

		// First entry is always the free object at generation 65535
		this.writeXrefEntry(this.mainFlow, 0, 65535, false);

		// Write actual object positions
		for (final var ref : this.xref) {
			final var impl = (ObjectRefImpl) ref;
			this.writeXrefEntry(this.mainFlow, impl.getPosition(posInfo), impl.generationNumber(), true);
		}

		// Append trailer content
		this.mainFlow.write(trailer);

		return xrefPosition;
	}

	/**
	 * Writes a cross-reference <em>stream</em> (PDF 1.5+) instead of the
	 * classic table: entries are packed binary rows with widths [1 4 2],
	 * deflated, and the trailer keys live in the stream dictionary. Type-2
	 * entries locate objects packed into object streams.
	 *
	 * @param posInfo fragment position snapshot
	 * @param infoRef reference to the Info dictionary, or {@code null}
	 * @param fileid  two-element array of 16-byte document IDs, or {@code null}
	 * @return absolute byte offset of the cross-reference stream
	 * @throws IOException if an I/O error occurs
	 */
	long closeWithXrefStream(final PositionInfo posInfo, final ObjectRef infoRef, final byte[][] fileid)
			throws IOException {
		final long xrefPosition = posInfo.getPosition(this.mainFlow.getId()) + this.mainFlow.getLength();
		// The stream is itself an indirect object with a type-1 entry
		// pointing at xrefPosition.
		final var xrefRef = this.nextObjectRef();

		final var entries = new ByteArrayOutputStream((this.xref.size() + 1) * 7);
		writeStreamEntry(entries, 0, 0, 65535); // object 0: free list head
		for (final var ref : this.xref) {
			final var impl = (ObjectRefImpl) ref;
			if (ref == xrefRef) {
				writeStreamEntry(entries, 1, xrefPosition, 0);
			} else if (impl.isCompressed()) {
				writeStreamEntry(entries, 2, impl.getObjStmNumber(), impl.getObjStmIndex());
			} else {
				writeStreamEntry(entries, 1, impl.getPosition(posInfo), impl.generationNumber());
			}
		}
		final var deflated = deflate(entries.toByteArray());

		this.mainFlow.breakBefore();
		this.mainFlow.writeInt(xrefRef.objectNumber());
		this.mainFlow.writeInt(xrefRef.generationNumber());
		this.mainFlow.writeOperator("obj");
		this.mainFlow.lineBreak();
		this.mainFlow.startHash();
		this.mainFlow.writeName("Type");
		this.mainFlow.writeName("XRef");
		this.mainFlow.writeName("Size");
		this.mainFlow.writeInt(this.xref.size() + 1);
		this.mainFlow.lineBreak();
		this.mainFlow.writeName("W");
		this.mainFlow.startArray();
		this.mainFlow.writeInt(1);
		this.mainFlow.writeInt(4);
		this.mainFlow.writeInt(2);
		this.mainFlow.endArray();
		this.mainFlow.lineBreak();
		this.mainFlow.writeName("Filter");
		this.mainFlow.writeName("FlateDecode");
		this.mainFlow.writeName("Length");
		this.mainFlow.writeInt(deflated.length);
		this.mainFlow.lineBreak();
		this.mainFlow.writeName("Root");
		this.mainFlow.writeObjectRef(this.rootRef);
		this.mainFlow.lineBreak();
		if (infoRef != null) {
			this.mainFlow.writeName("Info");
			this.mainFlow.writeObjectRef(infoRef);
			this.mainFlow.lineBreak();
		}
		if (fileid != null) {
			this.mainFlow.writeName("ID");
			this.mainFlow.startArray();
			this.mainFlow.writeBytes8(fileid[0], 0, fileid[0].length);
			this.mainFlow.writeBytes8(fileid[1], 0, fileid[1].length);
			this.mainFlow.endArray();
			this.mainFlow.lineBreak();
		}
		this.mainFlow.endHash();
		this.mainFlow.writeOperator("stream");
		this.mainFlow.lineBreak();
		this.mainFlow.write(deflated);
		this.mainFlow.lineBreak();
		this.mainFlow.writeOperator("endstream");
		this.mainFlow.lineBreak();
		this.mainFlow.writeOperator("endobj");
		this.mainFlow.lineBreak();

		this.mainFlow.writeOperator("startxref");
		this.mainFlow.lineBreak();
		this.mainFlow.write(String.valueOf(xrefPosition));
		this.mainFlow.lineBreak();
		this.mainFlow.write(EOF);
		this.mainFlow.lineBreak();
		return xrefPosition;
	}

	/** Writes one packed xref-stream row with widths [1 4 2]. */
	private static void writeStreamEntry(final ByteArrayOutputStream out, final int type, final long field2,
			final int field3) {
		out.write(type);
		out.write((int) (field2 >> 24) & 0xFF);
		out.write((int) (field2 >> 16) & 0xFF);
		out.write((int) (field2 >> 8) & 0xFF);
		out.write((int) field2 & 0xFF);
		out.write((field3 >> 8) & 0xFF);
		out.write(field3 & 0xFF);
	}

	/** Deflates the given bytes in memory. */
	private static byte[] deflate(final byte[] data) throws IOException {
		final var buff = new ByteArrayOutputStream(data.length / 2 + 32);
		try (final var out = new java.util.zip.DeflaterOutputStream(buff)) {
			out.write(data);
		}
		return buff.toByteArray();
	}

	/** Scratch buffer for zero-padding numbers in xref entries (10 bytes max). */
	private final byte[] numberBuffer = new byte[10];

	/**
	 * Writes a single 20-byte cross-reference table entry in the format:
	 * <pre>nnnnnnnnnn ggggg n|f[EOL]</pre>
	 * where {@code n} or {@code f} indicates whether the object is in use or free.
	 *
	 * @param out           target fragment output
	 * @param byteOffset    absolute byte offset of the object in the final file
	 * @param generationNum generation number of the object
	 * @param inUse         {@code true} for an in-use entry ({@code n}),
	 *                      {@code false} for a free entry ({@code f})
	 * @throws IOException if an I/O error occurs
	 */
	void writeXrefEntry(final PDFFragmentOutputImpl out, final long byteOffset, final int generationNum,
			final boolean inUse) throws IOException {
		out.breakBefore();

		// Write 10-digit offset with leading zeros
		this.writeFixedNumber(out, byteOffset, 10);
		out.write(' ');

		// Write 5-digit generation number with leading zeros
		this.writeFixedNumber(out, generationNum, 5);
		out.write(' ');

		out.write(inUse ? 'n' : 'f');
		out.lineBreak();
	}

	/**
	 * Writes a decimal number left-padded with {@code '0'} to exactly {@code width}
	 * digits.
	 *
	 * @param out   target output
	 * @param val   non-negative value to write
	 * @param width total character width (must be ≤ 10)
	 * @throws IOException if an I/O error occurs
	 */
	private void writeFixedNumber(final PDFFragmentOutputImpl out, long val, final int width) throws IOException {
		for (var i = width - 1; i >= 0; --i) {
			this.numberBuffer[i] = (byte) ('0' + (val % 10));
			val /= 10;
		}
		out.write(this.numberBuffer, 0, width);
	}

	public Object getAttribute(final String key) {
		return (this.attributes == null) ? null : this.attributes.get(key);
	}

	public void setAttribute(final String key, final Object value) {
		if (this.attributes == null) {
			this.attributes = new HashMap<>();
		}
		this.attributes.put(key, value);
	}

	public void addDependency(final ObjectRef from, final ObjectRef to) {
		if (from == null || to == null || from == to) {
			return;
		}
		this.dependencies.computeIfAbsent(from, key -> new LinkedHashSet<>()).add(to);
	}

	public Set<ObjectRef> getDependencies(final ObjectRef ref) {
		return this.dependencies.getOrDefault(ref, new LinkedHashSet<>());
	}
}
