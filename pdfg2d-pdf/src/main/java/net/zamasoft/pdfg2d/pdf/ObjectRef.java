package net.zamasoft.pdfg2d.pdf;

/**
 * Immutable identifier for a PDF indirect object, consisting of an
 * <em>object number</em> and a <em>generation number</em>.
 * <p>
 * In PDF syntax an indirect reference is written as {@code N G R} (e.g.
 * {@code 5 0 R}).  Two {@code ObjectRef} instances are considered equal when
 * both numbers match, regardless of subclass.
 * </p>
 * <p>
 * Subclasses may add mutable state (e.g. a file position for cross-reference
 * patching) but equality and hashing are always based solely on the two
 * immutable number components.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class ObjectRef {
	private final int objectNumber;
	private final int generationNumber;

	/**
	 * Creates a new PDF object reference.
	 * 
	 * @param objectNumber     the object number for referencing
	 * @param generationNumber the generation number used during modifications
	 */
	protected ObjectRef(final int objectNumber, final int generationNumber) {
		this.objectNumber = objectNumber;
		this.generationNumber = generationNumber;
	}

	/**
	 * Returns the object number.
	 * 
	 * @return the object number
	 */
	public final int objectNumber() {
		return this.objectNumber;
	}

	/**
	 * Returns the generation number.
	 * 
	 * @return the generation number
	 */
	public final int generationNumber() {
		return this.generationNumber;
	}

	@Override
	public final boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ObjectRef ref)) {
			return false;
		}
		return this.objectNumber == ref.objectNumber && this.generationNumber == ref.generationNumber;
	}

	@Override
	public final int hashCode() {
		return 31 * this.objectNumber + this.generationNumber;
	}
}
