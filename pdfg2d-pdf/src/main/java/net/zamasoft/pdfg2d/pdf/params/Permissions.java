package net.zamasoft.pdfg2d.pdf.params;

/**
 * Abstract base class representing the access permissions embedded in a PDF
 * encryption dictionary.
 * <p>
 * Permissions are encoded as a 32-bit integer bitmask whose structure varies
 * between PDF revisions. Concrete subclasses ({@link R2Permissions},
 * {@link R3Permissions}, {@link R4Permissions}) define the revision-specific
 * bit constants and accessor methods.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class Permissions {
	/**
	 * Encryption revision identifier that determines which permission bits are
	 * recognised by a conforming PDF reader.
	 */
	public enum Type {
		/** Revision 2 — RC4 40-bit, PDF 1.1+. */
		R2(2),
		/** Revision 3 — RC4 variable-length, PDF 1.4+. */
		R3(3),
		/** Revision 4 — AES cipher support, PDF 1.5+. */
		R4(4);

		/** Numeric revision number written to the PDF encryption dictionary. */
		public final int r;

		Type(final int r) {
			this.r = r;
		}
	}

	/**
	 * Permission flags bitmask.
	 * Bits 0-1 are always 0; all other bits default to 1 (all permissions granted).
	 */
	protected int flags = 0xFFFFFFFC;

	/**
	 * Returns the encryption revision type implemented by this permissions object.
	 *
	 * @return the revision type
	 */
	public abstract Type getType();

	/**
	 * Returns the raw 32-bit permissions bitmask to be written to the PDF
	 * encryption dictionary entry {@code P}.
	 *
	 * @return the permission flags
	 */
	public abstract int getFlags();
}
