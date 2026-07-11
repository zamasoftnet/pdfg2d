package net.zamasoft.pdfg2d.pdf.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFOutput;

/**
 * Assembles a Linearized ("Fast Web View") PDF as defined in ISO 32000-1
 * Annex F.
 * <p>
 * Linearization cannot be done in one streaming pass because the linearization
 * dictionary, the primary cross-reference table and the hint stream at the
 * head of the file all contain byte offsets that depend on their own encoded
 * lengths. This class therefore works on a byte snapshot of the
 * already-written document: it reorders the body objects so that everything
 * the first page needs comes first, then iterates the header sections to a
 * fixed point (a few rounds at most) until the self-referential offsets
 * stabilize, and finally replaces the original bytes with the assembled file.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
final class LinearizedPDFAssembler {

	private final PDFWriterImpl writer;

	/** Reference reserved up-front for the linearization dictionary. */
	private final ObjectRef linDictRef;

	private final PDFFragmentOutputImpl linDictFlow;

	/** Root of the page tree; excluded from per-page reachability. */
	private final ObjectRef rootPageRef;

	private final byte[][] fileid;

	LinearizedPDFAssembler(final PDFWriterImpl writer, final ObjectRef linDictRef,
			final PDFFragmentOutputImpl linDictFlow, final ObjectRef rootPageRef, final byte[][] fileid) {
		this.writer = writer;
		this.linDictRef = linDictRef;
		this.linDictFlow = linDictFlow;
		this.rootPageRef = rootPageRef;
		this.fileid = fileid;
	}

	/**
	 * Rewrites the document backing store as a linearized PDF.
	 *
	 * @param infoRef the document information dictionary reference
	 * @throws IOException if the backing store does not support snapshots or an
	 *                     I/O error occurs
	 */
	void assemble(final ObjectRef infoRef) throws IOException {
		this.linDictFlow.flush();
		this.writer.mainFlow.flush();

		if (!(this.writer.builder instanceof net.zamasoft.zstream.io.impl.AbstractTempFileOutput tempBuilder)) {
			throw new IOException("Linearized output requires AbstractTempFileOutput-backed storage.");
		}

		final var posInfo = this.writer.builder.getPositionInfo();
		final var snapshot = tempBuilder.snapshotBytes();
		final var hintRef = this.writer.xref.nextObjectRef();
		final var allObjects = this.writer.xref.getObjects();
		final var linearizedBytes = this.assembleLinearizedPdf(snapshot, posInfo, allObjects, infoRef, hintRef);
		tempBuilder.replaceBytes(linearizedBytes);
	}

	private byte[] assembleLinearizedPdf(
			final byte[] snapshot,
			final net.zamasoft.zstream.io.FragmentedOutput.PositionInfo posInfo,
			final List<ObjectRef> allObjects,
			final ObjectRef infoRef,
			final ObjectRef hintRef) throws IOException {
		// Slice the snapshot into per-object byte ranges, ordered by their
		// original file position.
		final var sourceObjects = new ArrayList<ObjectRef>();
		long firstObjectOffset = Long.MAX_VALUE;
		for (final var ref : allObjects) {
			if (ref == this.linDictRef || ref == hintRef) {
				continue;
			}
			final var impl = (ObjectRefImpl) ref;
			sourceObjects.add(ref);
			firstObjectOffset = Math.min(firstObjectOffset, impl.getPosition(posInfo));
		}
		sourceObjects.sort((left, right) -> Long.compare(
				((ObjectRefImpl) left).getPosition(posInfo),
				((ObjectRefImpl) right).getPosition(posInfo)));

		final var headerBytes = Arrays.copyOf(snapshot, (int) firstObjectOffset);
		final var objectBytes = new HashMap<ObjectRef, byte[]>();
		for (int i = 0; i < sourceObjects.size(); ++i) {
			final var ref = sourceObjects.get(i);
			final int start = (int) ((ObjectRefImpl) ref).getPosition(posInfo);
			final int end;
			if (i + 1 < sourceObjects.size()) {
				end = (int) ((ObjectRefImpl) sourceObjects.get(i + 1)).getPosition(posInfo);
			} else {
				end = snapshot.length;
			}
			objectBytes.put(ref, Arrays.copyOfRange(snapshot, start, end));
		}

		// Compute which objects each page needs; objects used by more than one
		// page become "shared objects" in the hint tables.
		final var reachableByPage = new HashMap<PDFPageOutputImpl, LinkedHashSet<ObjectRef>>();
		final var usageCount = new HashMap<ObjectRef, Integer>();
		for (final var page : this.writer.pageOutputs) {
			final var reachable = new LinkedHashSet<ObjectRef>();
			this.collectLinearizedPageObjects(page.getPageRef(), reachable, new LinkedHashSet<>());
			reachableByPage.put(page, reachable);
			for (final var ref : reachable) {
				usageCount.merge(ref, 1, Integer::sum);
			}
		}

		final var sharedObjects = new ArrayList<ObjectRef>();
		for (final var ref : sourceObjects) {
			if (usageCount.getOrDefault(ref, 0) > 1) {
				sharedObjects.add(ref);
			}
		}
		final var sharedSet = new LinkedHashSet<>(sharedObjects);

		// The body is reordered so the first page's objects (and the shared
		// objects it pulls in) come before everything else.
		final var firstPage = this.writer.pageOutputs.get(0);
		final var firstPageSectionObjects = new ArrayList<ObjectRef>();
		for (final var ref : sourceObjects) {
			if (reachableByPage.get(firstPage).contains(ref)) {
				firstPageSectionObjects.add(ref);
			}
		}
		final var firstPageSectionSet = new LinkedHashSet<>(firstPageSectionObjects);
		final var sharedSectionObjects = new ArrayList<ObjectRef>();
		for (final var ref : sharedObjects) {
			if (!firstPageSectionSet.contains(ref)) {
				sharedSectionObjects.add(ref);
			}
		}

		final var bodyOrder = new ArrayList<ObjectRef>(firstPageSectionObjects);
		for (final var ref : sourceObjects) {
			if (!firstPageSectionSet.contains(ref)) {
				bodyOrder.add(ref);
			}
		}

		final var bodyOffsets = new HashMap<ObjectRef, Integer>();
		int bodyLength = 0;
		for (final var ref : bodyOrder) {
			bodyOffsets.put(ref, bodyLength);
			bodyLength += objectBytes.get(ref).length;
		}

		final var bodyIndex = new HashMap<ObjectRef, Integer>();
		for (int i = 0; i < bodyOrder.size(); ++i) {
			bodyIndex.put(bodyOrder.get(i), i);
		}
		final var sharedHintObjects = new ArrayList<ObjectRef>(firstPageSectionObjects);
		sharedHintObjects.addAll(sharedSectionObjects);
		final var sharedObjectIndex = new HashMap<ObjectRef, Integer>();
		for (int i = 0; i < sharedHintObjects.size(); ++i) {
			sharedObjectIndex.put(sharedHintObjects.get(i), i);
		}

		final var pageEntries = new ArrayList<HintTableBuilder.PageEntry>();
		long firstPageEndInBody = 0;
		for (int pageIndex = 0; pageIndex < this.writer.pageOutputs.size(); ++pageIndex) {
			final var page = this.writer.pageOutputs.get(pageIndex);
			final boolean isFirstPage = page == firstPage;
			final boolean isLastPage = pageIndex == this.writer.pageOutputs.size() - 1;
			final var sectionObjects = new ArrayList<ObjectRef>();
			if (isFirstPage) {
				sectionObjects.addAll(firstPageSectionObjects);
			} else {
				for (final var ref : sourceObjects) {
					if (reachableByPage.get(page).contains(ref) && !sharedSet.contains(ref)) {
						sectionObjects.add(ref);
					}
				}
			}

			final long sectionStart = this.computeLinearizedPageStart(page, bodyOffsets);
			final long sectionEnd = this.computeLinearizedPageEndFromBody(
					isLastPage, sectionObjects, bodyOrder, bodyIndex, bodyOffsets, objectBytes);
			final var entry = new HintTableBuilder.PageEntry();
			entry.objectsPerPage = sectionObjects.size();
			entry.pageLength = sectionEnd - sectionStart;
			entry.contentStreamStart = 0;
			entry.contentStreamLength = entry.pageLength;
			if (!isFirstPage) {
				for (final var ref : sharedHintObjects) {
					if (usageCount.getOrDefault(ref, 0) > 1 && reachableByPage.get(page).contains(ref)) {
						entry.sharedObjectIndices.add(sharedObjectIndex.get(ref));
					}
				}
			}
			pageEntries.add(entry);
			if (isFirstPage) {
				firstPageEndInBody = sectionEnd;
			}
		}

		final int xrefSize = allObjects.size() + 1;

		// The linearization dictionary, the primary xref and the hint stream
		// each contain offsets that depend on the byte lengths of the others.
		// Iterate until the rendered lengths stop changing (they converge
		// quickly because only digit counts vary between rounds).
		byte[] primaryXrefBytes = new byte[0];
		byte[] hintCompressedBytes = new byte[0];
		byte[] hintObjectBytes = renderLinearizedHintObject(hintRef, 0, hintCompressedBytes);
		int hintSharedTableOffset = 0;

		for (int i = 0; i < 8; ++i) {
			final int linDictLength = renderLinearizedDictionaryBytes(
					0, 0, 0, firstPage.getPageRef().objectNumber(), 0, this.writer.pageOutputs.size(), 0).length;
			final int primaryXrefOffset = headerBytes.length + linDictLength;
			final int hintObjectOffset = primaryXrefOffset + primaryXrefBytes.length;
			final int bodyStartOffset = hintObjectOffset + hintObjectBytes.length;
			final int mainXrefOffset = bodyStartOffset + bodyLength;

			final var offsets = new HashMap<ObjectRef, Long>();
			offsets.put(this.linDictRef, (long) headerBytes.length);
			offsets.put(hintRef, (long) hintObjectOffset);
			for (final var ref : bodyOrder) {
				offsets.put(ref, (long) bodyStartOffset + bodyOffsets.get(ref));
			}

			final var hintBuild = this.buildLinearizedHintBytes(
					hintObjectOffset,
					pageEntries,
					firstPageSectionObjects,
					sharedSectionObjects,
					bodyOffsets,
					objectBytes);
			hintCompressedBytes = hintBuild.compressedBytes();
			hintSharedTableOffset = hintBuild.sharedObjectTableOffset();
			final var newHintObjectBytes = renderLinearizedHintObject(hintRef, hintSharedTableOffset, hintCompressedBytes);
			final var newPrimaryXrefBytes = this.renderLinearizedXrefBytes(
					allObjects, offsets, infoRef, mainXrefOffset, false, -1);
			if (newHintObjectBytes.length == hintObjectBytes.length
					&& newPrimaryXrefBytes.length == primaryXrefBytes.length) {
				hintObjectBytes = newHintObjectBytes;
				primaryXrefBytes = newPrimaryXrefBytes;
				break;
			}
			hintObjectBytes = newHintObjectBytes;
			primaryXrefBytes = newPrimaryXrefBytes;
		}

		final int linDictLength = renderLinearizedDictionaryBytes(
				0, 0, 0, firstPage.getPageRef().objectNumber(), 0, this.writer.pageOutputs.size(), 0).length;
		final var offsets = new HashMap<ObjectRef, Long>();
		for (int i = 0; i < 8; ++i) {
			final int primaryXrefOffset = headerBytes.length + linDictLength;
			final int hintObjectOffset = primaryXrefOffset + primaryXrefBytes.length;
			final int bodyStartOffset = hintObjectOffset + hintObjectBytes.length;
			final int mainXrefOffset = bodyStartOffset + bodyLength;

			offsets.clear();
			offsets.put(this.linDictRef, (long) headerBytes.length);
			offsets.put(hintRef, (long) hintObjectOffset);
			for (final var ref : bodyOrder) {
				offsets.put(ref, (long) bodyStartOffset + bodyOffsets.get(ref));
			}

			final var nextHintBuild = this.buildLinearizedHintBytes(
					hintObjectOffset,
					pageEntries,
					firstPageSectionObjects,
					sharedSectionObjects,
					bodyOffsets,
					objectBytes);
			final var nextHintBytes = renderLinearizedHintObject(
					hintRef, nextHintBuild.sharedObjectTableOffset(), nextHintBuild.compressedBytes());
			final var nextPrimaryXref = this.renderLinearizedXrefBytes(
					allObjects, offsets, infoRef, mainXrefOffset, false, -1);
			if (nextHintBytes.length == hintObjectBytes.length && nextPrimaryXref.length == primaryXrefBytes.length) {
				hintCompressedBytes = nextHintBuild.compressedBytes();
				hintSharedTableOffset = nextHintBuild.sharedObjectTableOffset();
				hintObjectBytes = nextHintBytes;
				primaryXrefBytes = nextPrimaryXref;
				break;
			}
			hintCompressedBytes = nextHintBuild.compressedBytes();
			hintSharedTableOffset = nextHintBuild.sharedObjectTableOffset();
			hintObjectBytes = nextHintBytes;
			primaryXrefBytes = nextPrimaryXref;
		}

		final int finalBodyStartOffset = headerBytes.length + linDictLength + primaryXrefBytes.length + hintObjectBytes.length;
		offsets.clear();
		offsets.put(this.linDictRef, (long) headerBytes.length);
		offsets.put(hintRef, (long) (headerBytes.length + linDictLength + primaryXrefBytes.length));
		for (final var ref : bodyOrder) {
			offsets.put(ref, (long) finalBodyStartOffset + bodyOffsets.get(ref));
		}

		final int finalMainXrefOffset = finalBodyStartOffset + bodyLength;
		primaryXrefBytes = this.renderLinearizedXrefBytes(allObjects, offsets, infoRef, finalMainXrefOffset, false, -1);
		final var mainXrefBytes = this.renderLinearizedXrefBytes(allObjects, offsets, infoRef, -1, true, finalMainXrefOffset);
		final int finalFileLength = finalMainXrefOffset + mainXrefBytes.length;
		final int firstPageEnd = finalBodyStartOffset + (int) firstPageEndInBody;
		final int tOffset = (int) linearizedXrefFirstItemOffset(finalMainXrefOffset, xrefSize);
		final var linDictBytes = renderLinearizedDictionaryBytes(
				finalFileLength,
				headerBytes.length + linDictLength + primaryXrefBytes.length,
				hintObjectBytes.length,
				firstPage.getPageRef().objectNumber(),
				firstPageEnd,
				this.writer.pageOutputs.size(),
				tOffset);

		final var out = new ByteArrayOutputStream(finalFileLength);
		out.write(headerBytes);
		out.write(linDictBytes);
		out.write(primaryXrefBytes);
		out.write(hintObjectBytes);
		for (final var ref : bodyOrder) {
			out.write(objectBytes.get(ref));
		}
		out.write(mainXrefBytes);
		return out.toByteArray();
	}

	private long computeLinearizedPageStart(final PDFPageOutputImpl page, final Map<ObjectRef, Integer> bodyOffsets) {
		long start = bodyOffsets.get(page.getPageRef());
		start = Math.min(start, bodyOffsets.get(page.getContentsRef()));
		for (final var annotRef : page.getAnnotRefs()) {
			start = Math.min(start, bodyOffsets.get(annotRef));
		}
		return start;
	}

	private long computeLinearizedPageEndFromBody(
			final boolean isLastPage,
			final List<ObjectRef> sectionObjects,
			final List<ObjectRef> bodyOrder,
			final Map<ObjectRef, Integer> bodyIndex,
			final Map<ObjectRef, Integer> bodyOffsets,
			final Map<ObjectRef, byte[]> objectBytes) {
		long end = 0;
		int lastIndex = -1;
		for (final var ref : sectionObjects) {
			end = Math.max(end, bodyOffsets.get(ref) + objectBytes.get(ref).length);
			lastIndex = Math.max(lastIndex, bodyIndex.get(ref));
		}
		if (!isLastPage && lastIndex >= 0 && lastIndex + 1 < bodyOrder.size()) {
			return bodyOffsets.get(bodyOrder.get(lastIndex + 1));
		}
		return end;
	}

	private LinearizedHintBuild buildLinearizedHintBytes(
			final int firstPageLocation,
			final List<HintTableBuilder.PageEntry> pageEntries,
			final List<ObjectRef> firstPageSectionObjects,
			final List<ObjectRef> sharedSectionObjects,
			final Map<ObjectRef, Integer> bodyOffsets,
			final Map<ObjectRef, byte[]> objectBytes) throws IOException {
		final var hintBuilder = new HintTableBuilder();
		hintBuilder.setFirstPageLocation(firstPageLocation);
		for (final var ref : firstPageSectionObjects) {
			hintBuilder.addFirstPageObject(ref, firstPageLocation + bodyOffsets.get(ref), objectBytes.get(ref).length);
		}
		for (final var ref : sharedSectionObjects) {
			hintBuilder.addSharedObject(ref, firstPageLocation + bodyOffsets.get(ref), objectBytes.get(ref).length);
		}
		for (final var entry : pageEntries) {
			hintBuilder.addPage(entry);
		}
		final var rawHints = new ByteArrayOutputStream();
		hintBuilder.build(rawHints);
		final var compressed = new ByteArrayOutputStream();
		try (final var deflater = new java.util.zip.DeflaterOutputStream(compressed)) {
			deflater.write(rawHints.toByteArray());
		}
		return new LinearizedHintBuild(compressed.toByteArray(), hintBuilder.getSharedObjectTableOffset());
	}

	private byte[] renderLinearizedDictionaryBytes(
			final int fileLength,
			final int hintOffset,
			final int hintLength,
			final int firstPageObjectNumber,
			final int firstPageEnd,
			final int pageCount,
			final int mainXrefOffset) throws IOException {
		final var out = new ByteArrayOutputStream();
		try (final var pdf = new PDFOutput(out, "ISO-8859-1")) {
			pdf.writeInt(this.linDictRef.objectNumber());
			pdf.writeInt(this.linDictRef.generationNumber());
			pdf.writeOperator("obj");
			pdf.lineBreak();
			pdf.startHash();
			pdf.writeName("Linearized");
			pdf.writeReal(1.0);
			pdf.lineBreak();
			pdf.writeName("L");
			pdf.spaceBefore();
			writeFixedNumber(pdf, fileLength, 10);
			pdf.lineBreak();
			pdf.writeName("H");
			pdf.startArray();
			writeFixedNumber(pdf, hintOffset, 10);
			pdf.spaceBefore();
			writeFixedNumber(pdf, hintLength, 10);
			pdf.endArray();
			pdf.lineBreak();
			pdf.writeName("O");
			pdf.spaceBefore();
			writeFixedNumber(pdf, firstPageObjectNumber, 10);
			pdf.lineBreak();
			pdf.writeName("E");
			pdf.spaceBefore();
			writeFixedNumber(pdf, firstPageEnd, 10);
			pdf.lineBreak();
			pdf.writeName("N");
			pdf.spaceBefore();
			writeFixedNumber(pdf, pageCount, 10);
			pdf.lineBreak();
			pdf.writeName("T");
			pdf.spaceBefore();
			writeFixedNumber(pdf, mainXrefOffset, 10);
			pdf.lineBreak();
			pdf.endHash();
			pdf.lineBreak();
			pdf.writeOperator("endobj");
			pdf.lineBreak();
		}
		return out.toByteArray();
	}

	private byte[] renderLinearizedXrefBytes(
			final List<ObjectRef> allObjects,
			final Map<ObjectRef, Long> offsets,
			final ObjectRef infoRef,
			final long prevOffset,
			final boolean withStartxref,
			final long startxrefOffset) throws IOException {
		final var out = new ByteArrayOutputStream();
		try (final var pdf = new PDFOutput(out, "ISO-8859-1")) {
			pdf.writeOperator("xref");
			pdf.lineBreak();
			pdf.writeInt(0);
			pdf.writeInt(allObjects.size() + 1);
			pdf.lineBreak();
			writeLinearizedXrefEntry(pdf, 0, 65535, false);
			for (final var ref : allObjects) {
				writeLinearizedXrefEntry(pdf, offsets.get(ref), ref.generationNumber(), true);
			}
			pdf.writeOperator("trailer");
			pdf.lineBreak();
			pdf.startHash();
			pdf.writeName("Size");
			pdf.writeInt(allObjects.size() + 1);
			pdf.lineBreak();
			if (prevOffset >= 0) {
				pdf.writeName("Prev");
				pdf.writeInt((int) prevOffset);
				pdf.lineBreak();
			}
			pdf.writeName("Root");
			pdf.writeObjectRef(this.writer.xref.getRootRef());
			pdf.lineBreak();
			if (infoRef != null) {
				pdf.writeName("Info");
				pdf.writeObjectRef(infoRef);
				pdf.lineBreak();
			}
			if (this.fileid != null) {
				pdf.writeName("ID");
				pdf.startArray();
				pdf.writeBytes8(this.fileid[0], 0, this.fileid[0].length);
				pdf.writeBytes8(this.fileid[1], 0, this.fileid[1].length);
				pdf.endArray();
				pdf.lineBreak();
			}
			if (this.writer.encryption != null) {
				pdf.writeName("Encrypt");
				pdf.writeObjectRef(this.writer.encryption.getObjectRef());
				pdf.lineBreak();
			}
			pdf.endHash();
			if (withStartxref) {
				pdf.writeOperator("startxref");
				pdf.lineBreak();
				writeFixedNumber(pdf, startxrefOffset, 10);
				pdf.lineBreak();
				pdf.writeLine("%%EOF");
			}
		}
		return out.toByteArray();
	}

	private void collectLinearizedPageObjects(final ObjectRef current, final Set<ObjectRef> collected,
			final Set<ObjectRef> visited) {
		if (current == null || current == this.rootPageRef || !visited.add(current)) {
			return;
		}
		collected.add(current);
		for (final var dependency : this.writer.xref.getDependencies(current)) {
			if (dependency == this.rootPageRef) {
				continue;
			}
			this.collectLinearizedPageObjects(dependency, collected, visited);
		}
	}

	private static void writeLinearizedXrefEntry(final PDFOutput out, final long byteOffset, final int generationNum,
			final boolean inUse) throws IOException {
		writeFixedNumber(out, byteOffset, 10);
		out.write(' ');
		writeFixedNumber(out, generationNum, 5);
		out.write(' ');
		out.write(inUse ? 'n' : 'f');
		out.lineBreak();
	}

	/**
	 * Writes {@code value} zero-padded to a fixed digit width so that
	 * re-rendering with a different value cannot change the byte length.
	 */
	private static void writeFixedNumber(final PDFOutput out, long value, final int width) throws IOException {
		final byte[] digits = new byte[width];
		for (int i = width - 1; i >= 0; --i) {
			digits[i] = (byte) ('0' + (value % 10));
			value /= 10;
		}
		out.write(digits);
	}

	private byte[] renderLinearizedHintObject(
			final ObjectRef hintRef,
			final int sharedObjectTableOffset,
			final byte[] hintBytesCompressed) throws IOException {
		final var out = new ByteArrayOutputStream();
		try (final var pdf = new PDFOutput(out, "ISO-8859-1")) {
			pdf.writeInt(hintRef.objectNumber());
			pdf.writeInt(hintRef.generationNumber());
			pdf.writeOperator("obj");
			pdf.lineBreak();
			pdf.startHash();
			pdf.writeName("Filter");
			pdf.writeName("FlateDecode");
			pdf.writeName("S");
			pdf.writeInt(sharedObjectTableOffset);
			pdf.writeName("Length");
			pdf.writeInt(hintBytesCompressed.length);
			pdf.lineBreak();
			pdf.endHash();
			pdf.writeOperator("stream");
			pdf.lineBreak();
			pdf.write(hintBytesCompressed);
			pdf.lineBreak();
			pdf.writeOperator("endstream");
			pdf.lineBreak();
			pdf.writeOperator("endobj");
			pdf.lineBreak();
		}
		return out.toByteArray();
	}

	/**
	 * Byte offset of the first xref entry, used for the {@code /T} key of the
	 * linearization dictionary.
	 */
	private static long linearizedXrefFirstItemOffset(final long xrefOffset, final int objectCount) {
		return xrefOffset + ("xref\r\n0 " + objectCount + "\r\n").length();
	}

	private record LinearizedHintBuild(byte[] compressedBytes, int sharedObjectTableOffset) {
	}
}
