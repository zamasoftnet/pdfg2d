package net.zamasoft.pdfg2d.pdf.impl;

import net.zamasoft.zstream.io.FragmentedOutput.PositionInfo;
import net.zamasoft.pdfg2d.pdf.ObjectRef;

/**
 * Concrete implementation of a PDF object reference that tracks the physical
 * position of the object within the fragmented output.
 * <p>
 * In addition to the object number and generation number inherited from
 * {@link ObjectRef}, this class records the fragment ID and the byte offset
 * within that fragment so that the cross-reference table can be built after all
 * objects have been written.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class ObjectRefImpl extends ObjectRef {
	/** Byte offset within the fragment; {@code -1} until {@link #setPosition} is called. */
	private long position = -1;
	/** Fragment ID that contains this object. */
	private int id;

	/**
	 * Creates a new object reference with the given object number.
	 * 
	 * @param objectNum the object number
	 */
	public ObjectRefImpl(final int objectNum) {
		super(objectNum, 0);
	}

	/**
	 * Sets the position of this object in the PDF output.
	 * 
	 * @param id       the fragment ID
	 * @param position the position within the fragment
	 * @throws IllegalStateException if the position has already been set
	 */
	public void setPosition(final int id, final long position) {
		this.id = id;
		this.position = position;
	}

	/**
	 * Gets the absolute position in the output.
	 * 
	 * @param info the position info from the fragmented output
	 * @return the absolute position
	 */
	public long getPosition(final PositionInfo info) {
		return info.getPosition(this.id) + this.position;
	}

	/** Byte length of the serialized object including the {@code endobj} keyword; {@code -1} until written. */
	private int length = -1;

	/**
	 * Records the byte length of this object in the output stream.
	 * <p>
	 * Called by {@link PDFFragmentOutputImpl#endObject()} after the object has
	 * been fully serialised.  The length is used by the linearization logic to
	 * calculate page extents.
	 * </p>
	 *
	 * @param length byte length of the serialized object
	 */
	public void setLength(final int length) {
		this.length = length;
	}

	/**
	 * Returns the byte length of this serialized object, or {@code -1} if the
	 * object has not been closed yet.
	 *
	 * @return byte length, or {@code -1}
	 */
	public int getLength() {
		return this.length;
	}

	/**
	 * Returns a PDF indirect-object reference string, e.g. {@code "5 0 R"}.
	 *
	 * @return PDF reference string
	 */
	@Override
	public String toString() {
		return this.objectNumber() + " " + this.generationNumber() + " R";
	}

	/** Object number of the containing object stream, or -1 when not packed. */
	private int objStmNumber = -1;

	/** Index within the containing object stream. */
	private int objStmIndex = -1;

	/** Marks this object as packed into an object stream (type-2 xref entry). */
	void setCompressed(final int objStmNumber, final int objStmIndex) {
		this.objStmNumber = objStmNumber;
		this.objStmIndex = objStmIndex;
	}

	/** Returns whether this object lives inside an object stream. */
	boolean isCompressed() {
		return this.objStmNumber >= 0;
	}

	int getObjStmNumber() {
		return this.objStmNumber;
	}

	int getObjStmIndex() {
		return this.objStmIndex;
	}
}
