package net.zamasoft.pdfg2d.pdf.params;

/**
 * Encryption parameters for PDF V5 (AES-256, security handler revision 6).
 * <p>
 * This is the encryption defined by ISO 32000-2 (PDF 2.0): a random 256-bit
 * file encryption key protected by SHA-2 based password hashes
 * (Algorithm 2.B) with the {@code AESV3} crypt filter. It is the only
 * standard encryption for PDF 2.0 documents and is also accepted by PDF 1.7
 * viewers with extension level 8.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class V5EncryptionParams extends EncryptionParams {

	private final R6Permissions permissions = new R6Permissions();

	private boolean encryptMetadata = true;

	/**
	 * Returns the encryption type {@link EncryptionParams.Type#V5}.
	 *
	 * @return {@link EncryptionParams.Type#V5}
	 */
	@Override
	public Type getType() {
		return Type.V5;
	}

	/**
	 * Returns the revision 6 permissions associated with this encryption
	 * configuration.
	 *
	 * @return the {@link R6Permissions} object
	 */
	public R6Permissions getPermissions() {
		return this.permissions;
	}

	/**
	 * Returns whether document metadata streams are encrypted.
	 *
	 * @return {@code true} if metadata is encrypted (default)
	 */
	public boolean getEncryptMetadata() {
		return this.encryptMetadata;
	}

	/**
	 * Sets whether document metadata streams are encrypted.
	 *
	 * @param encryptMetadata {@code true} to encrypt metadata (default)
	 */
	public void setEncryptMetadata(final boolean encryptMetadata) {
		this.encryptMetadata = encryptMetadata;
	}
}
