package net.zamasoft.pdfg2d.pdf.params;

/**
 * Encryption parameters for PDF V1 (RC4 40-bit, PDF 1.1+).
 * <p>
 * This is the original PDF standard security handler using a fixed 40-bit RC4
 * key. It corresponds to encryption algorithm version 1 and revision 2 in the
 * PDF specification. Use {@link V2EncryptionParams} or
 * {@link V4EncryptionParams} for stronger encryption.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class V1EncryptionParams extends EncryptionParams {
	private final R2Permissions permissions = new R2Permissions();

	/**
	 * Returns the encryption type {@link EncryptionParams.Type#V1}.
	 *
	 * @return {@link EncryptionParams.Type#V1}
	 */
	public Type getType() {
		return Type.V1;
	}

	/**
	 * Returns the revision 2 permissions associated with this encryption
	 * configuration.
	 *
	 * @return the {@link R2Permissions} object
	 */
	public R2Permissions getPermissions() {
		return this.permissions;
	}
}
