package net.zamasoft.pdfg2d.pdf.util.encryption;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-CBC encryption implementation of the {@link Encryptor} interface for use
 * in PDF standard security handlers (revision 4, {@code AESV2} crypt filter).
 * <p>
 * This encryptor operates in block mode ({@link #isBlock()} returns
 * {@code true}). Encryption uses AES in CBC mode with PKCS#5 padding. A
 * randomly generated 16-byte initialisation vector (IV) is prepended to the
 * ciphertext as required by the PDF specification (section 7.6.5).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class AESEncryptor implements Encryptor {
	private final Key key;

	/**
	 * Constructs an {@code AESEncryptor} using the first {@code len} bytes of the
	 * provided key material.
	 *
	 * @param key the raw key bytes; only the first {@code len} bytes are used
	 * @param len the number of key bytes to use (1–32, typically 16 for AES-128)
	 * @throws IllegalArgumentException if {@code len} is not in the range 1–32
	 */
	public AESEncryptor(byte[] key, int len) {
		if (len < 0 || len > 32) {
			throw new IllegalArgumentException("The key length is limited to 1 to 32.");
		}
		this.key = new SecretKeySpec(key, 0, len, "AES");

	}

	/**
	 * Creates and initialises a new AES/CBC/PKCS5Padding {@link Cipher} instance
	 * in encryption mode with a randomly generated IV.
	 *
	 * @return a freshly initialised {@link Cipher}
	 * @throws RuntimeException if the cipher cannot be obtained or initialised
	 */
	private Cipher createCipher() {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, AESEncryptor.this.key);
			return cipher;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Encrypts the specified range of {@code data} and returns a new byte array
	 * containing the 16-byte IV followed by the AES-CBC ciphertext.
	 *
	 * @param data the plaintext byte array
	 * @param off  start offset in {@code data}
	 * @param len  number of bytes to encrypt
	 * @return a new byte array containing {@code IV || ciphertext}
	 * @throws RuntimeException if encryption fails
	 */
	public final byte[] blockEncrypt(byte[] data, int off, int len) {
		try {
			Cipher cipher = this.createCipher();
			byte[] iv = cipher.getIV();
			byte[] code = cipher.doFinal(data, off, len);
			byte[] result = new byte[iv.length + code.length];
			System.arraycopy(iv, 0, result, 0, iv.length);
			System.arraycopy(code, 0, result, iv.length, code.length);
			return result;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Wraps {@code out} with an encrypting stream. The randomly generated 16-byte
	 * IV is written to {@code out} immediately before this method returns.
	 *
	 * @param out the underlying output stream that receives the ciphertext
	 * @return a {@link CipherOutputStream} that encrypts written data
	 * @throws IOException if writing the IV to {@code out} fails
	 */
	public OutputStream getOutputStream(OutputStream out) throws IOException {
		Cipher cipher = this.createCipher();
		byte[] iv = cipher.getIV();
		out.write(iv);
		return new CipherOutputStream(out, cipher);
	}

	/**
	 * Encrypts the entire {@code data} array and returns a new array containing the
	 * IV followed by the ciphertext.
	 *
	 * @param data the plaintext byte array
	 * @return a new byte array containing {@code IV || ciphertext}
	 */
	public byte[] encrypt(byte[] data) {
		return this.blockEncrypt(data, 0, data.length);
	}

	/**
	 * Returns {@code true} because AES operates in block mode and callers must use
	 * {@link #blockEncrypt} or {@link #getOutputStream}.
	 *
	 * @return {@code true}
	 */
	public boolean isBlock() {
		return true;
	}

	/**
	 * Not supported for AES block encryption.
	 *
	 * @param data ignored
	 * @param off  ignored
	 * @param len  ignored
	 * @throws UnsupportedOperationException always
	 */
	public void fastEncrypt(byte[] data, int off, int len) {
		throw new UnsupportedOperationException();
	}
}