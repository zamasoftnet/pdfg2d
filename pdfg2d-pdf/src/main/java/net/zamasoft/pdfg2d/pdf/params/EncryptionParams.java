package net.zamasoft.pdfg2d.pdf.params;

/**
 * Abstract base class for PDF encryption configuration.
 * <p>
 * Concrete subclasses ({@link V1EncryptionParams}, {@link V2EncryptionParams},
 * {@link V4EncryptionParams}) correspond to the encryption revision numbers
 * defined in the PDF specification (section 7.6.3).
 * </p>
 * <p>
 * Both the user password and the owner password default to an empty string if
 * not set.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class EncryptionParams {
	/**
	 * PDF encryption algorithm revision.
	 */
	public enum Type {
		/** RC4 40-bit key (PDF 1.1+). */
		V1(1),
		/** RC4 variable-length key (PDF 1.4+). */
		V2(2),
		/** AES cipher support (PDF 1.5+). */
		V4(4);

		/** Numeric version identifier. */
		public final int v;

		Type(final int v) {
			this.v = v;
		}
	}

	private String userPassword = "";
	private String ownerPassword = "";

	/**
	 * Returns the encryption type (revision) implemented by this configuration.
	 *
	 * @return the encryption type
	 */
	public abstract Type getType();

	/**
	 * Returns the owner password, never {@code null} (defaults to empty string).
	 *
	 * @return the owner password
	 */
	public String getOwnerPassword() {
		return this.ownerPassword;
	}

	/**
	 * Sets the owner password.
	 *
	 * @param ownerPassword the owner password; {@code null} is treated as empty
	 */
	public void setOwnerPassword(String ownerPassword) {
		if (ownerPassword == null) {
			ownerPassword = "";
		}
		this.ownerPassword = ownerPassword;
	}

	/**
	 * Returns the user password, never {@code null} (defaults to empty string).
	 *
	 * @return the user password
	 */
	public String getUserPassword() {
		return this.userPassword;
	}

	/**
	 * Sets the user password.
	 *
	 * @param userPassword the user password; {@code null} is treated as empty
	 */
	public void setUserPassword(String userPassword) {
		if (userPassword == null) {
			userPassword = "";
		}
		this.userPassword = userPassword;
	}
}
