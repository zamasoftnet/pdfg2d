package net.zamasoft.pdfg2d.pdf.params;

/**
 * Encryption parameters for PDF V2 (RC4 variable-length, PDF 1.4+).
 * <p>
 * This security handler uses the RC4 stream cipher with a key length that can
 * be configured between 40 and 128 bits in multiples of 8. It corresponds to
 * encryption algorithm version 2 and revision 3 in the PDF specification.
 * The default key length is 128 bits.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class V2EncryptionParams extends EncryptionParams {
	private final R3Permissions permissions = new R3Permissions();

	private int length = 128;

	/**
	 * Returns the encryption type {@link EncryptionParams.Type#V2}.
	 *
	 * @return {@link EncryptionParams.Type#V2}
	 */
	@Override
	public Type getType() {
		return Type.V2;
	}

	/**
	 * Returns the revision 3 permissions associated with this encryption
	 * configuration.
	 *
	 * @return the {@link R3Permissions} object
	 */
	public R3Permissions getPermissions() {
		return this.permissions;
	}

	/**
	 * Returns the RC4 key length in bits.
	 *
	 * @return key length in bits (default 128)
	 */
	public int getLength() {
		return this.length;
	}

	/**
	 * Sets the RC4 key length in bits.
	 * Must be between 40 and 128 inclusive and a multiple of 8.
	 *
	 * @param length the key length in bits
	 * @throws IllegalArgumentException if {@code length} is out of range or not a
	 *                                  multiple of 8
	 */
	public void setLength(final int length) throws IllegalArgumentException {
		if (length < 40 || length > 128 || (length % 8) != 0) {
			throw new IllegalArgumentException(
					"V2 encryption length must be between 40 and 128 bits, in increments of 8: " + length);
		}
		this.length = length;
	}
}
