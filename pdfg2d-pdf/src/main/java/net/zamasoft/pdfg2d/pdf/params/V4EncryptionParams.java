package net.zamasoft.pdfg2d.pdf.params;

/**
 * Encryption parameters for PDF V4 (crypt filters, PDF 1.5/1.6+).
 * <p>
 * This security handler uses the crypt filter mechanism introduced in PDF 1.5,
 * allowing either RC4 ({@link CFM#V2}) or AES-128 ({@link CFM#AESV2}) to be
 * selected independently for streams and strings. It corresponds to encryption
 * algorithm version 4 and revision 4 in the PDF specification.
 * The default cipher is {@link CFM#V2} (RC4) with a 128-bit key.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class V4EncryptionParams extends EncryptionParams {
	/**
	 * Crypt filter method identifier, written to the {@code CFM} entry of a crypt
	 * filter dictionary.
	 */
	public enum CFM {
		/** RC4 stream cipher (variable-length key). */
		V2("V2"),
		/** AES-128 block cipher in CBC mode. */
		AESV2("AESV2"),
		/** AES-256 block cipher in CBC mode (V5/R6 handler only). */
		AESV3("AESV3");

		/** PDF name string for this crypt filter method. */
		public final String name;

		CFM(final String name) {
			this.name = name;
		}
	}

	private final R4Permissions permissions = new R4Permissions();

	private int length = 128;

	private CFM cfm = CFM.V2;

	private boolean encryptMetadata = true;

	/**
	 * Returns the encryption type {@link EncryptionParams.Type#V4}.
	 *
	 * @return {@link EncryptionParams.Type#V4}
	 */
	@Override
	public Type getType() {
		return Type.V4;
	}

	/**
	 * Returns the revision 4 permissions associated with this encryption
	 * configuration.
	 *
	 * @return the {@link R4Permissions} object
	 */
	public R4Permissions getPermissions() {
		return this.permissions;
	}

	/**
	 * Returns the cipher key length in bits.
	 *
	 * @return key length in bits (default 128)
	 */
	public int getLength() {
		return this.length;
	}

	/**
	 * Sets the cipher key length in bits.
	 * Must be between 40 and 128 inclusive and a multiple of 8.
	 *
	 * @param length the key length in bits
	 * @throws IllegalArgumentException if {@code length} is out of range or not a
	 *                                  multiple of 8
	 */
	public void setLength(final int length) throws IllegalArgumentException {
		if (length < 40 || length > 128 || (length % 8) != 0) {
			throw new IllegalArgumentException(
					"V4 encryption length must be between 40 and 128 bits, in increments of 8: " + length);
		}
		this.length = length;
	}

	/**
	 * Returns the crypt filter method (cipher algorithm) used for both streams and
	 * strings.
	 *
	 * @return the crypt filter method
	 */
	public CFM getCFM() {
		return this.cfm;
	}

	/**
	 * Sets the crypt filter method (cipher algorithm) used for both streams and
	 * strings.
	 *
	 * @param cfm the crypt filter method
	 */
	public void setCFM(final CFM cfm) {
		this.cfm = cfm;
	}

	/**
	 * Returns whether document metadata streams are encrypted.
	 *
	 * @return {@code true} if metadata is encrypted (default), {@code false}
	 *         otherwise
	 */
	public boolean getEncryptMetadata() {
		return this.encryptMetadata;
	}

	/**
	 * Sets whether document metadata streams are encrypted.
	 * When set to {@code false}, the {@code EncryptMetadata} entry is written
	 * as {@code false} in the encryption dictionary.
	 *
	 * @param encryptMetadata {@code true} to encrypt metadata (default)
	 */
	public void setEncryptMetadata(final boolean encryptMetadata) {
		this.encryptMetadata = encryptMetadata;
	}

}
