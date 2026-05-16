package net.zamasoft.pdfg2d.pdf.util.encryption;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Strategy interface for PDF encryption algorithms.
 * <p>
 * Two encryption modes are supported:
 * <ul>
 *   <li><strong>Stream encryption</strong> – RC4 and similar ciphers that can
 *       encrypt data in-place byte by byte; {@link #isBlock()} returns
 *       {@code false}.</li>
 *   <li><strong>Block encryption</strong> – AES and similar ciphers that
 *       require a full plaintext block; {@link #isBlock()} returns
 *       {@code true} and callers must use {@link #blockEncrypt}.</li>
 * </ul>
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface Encryptor {
	/**
	 * Returns {@code true} if this encryptor operates in block mode (e.g., AES),
	 * or {@code false} if it can encrypt a stream in-place (e.g., RC4).
	 *
	 * @return {@code true} for block ciphers, {@code false} for stream ciphers
	 */
	boolean isBlock();

	/**
	 * Encrypts a byte range in-place.
	 * <p>
	 * Only valid when {@link #isBlock()} returns {@code false}.  The array
	 * {@code data} is modified in place; the caller writes the same array to the
	 * PDF output after this call returns.
	 * </p>
	 *
	 * @param data byte array containing plaintext to encrypt
	 * @param off  start offset in {@code data}
	 * @param len  number of bytes to encrypt
	 */
	void fastEncrypt(byte[] data, int off, int len);

	/**
	 * Encrypts a byte range and returns a new array containing the ciphertext.
	 * <p>
	 * Used when {@link #isBlock()} returns {@code true}.  The returned array may
	 * be longer than the input due to IV prepending or PKCS#7 padding.
	 * </p>
	 *
	 * @param data byte array containing plaintext
	 * @param off  start offset in {@code data}
	 * @param len  number of bytes to encrypt
	 * @return new byte array containing the ciphertext
	 */
	byte[] blockEncrypt(byte[] data, int off, int len);

	/**
	 * Encrypts an entire byte array and returns a new array containing the
	 * ciphertext.
	 *
	 * @param data byte array containing plaintext
	 * @return new byte array containing the ciphertext
	 */
	byte[] encrypt(byte[] data);

	/**
	 * Wraps the given output stream so that all data written to it is encrypted
	 * before being forwarded to {@code out}.
	 *
	 * @param out the underlying (plaintext-receiving) output stream
	 * @return an encrypting wrapper stream
	 * @throws IOException if an I/O error occurs while initializing the stream
	 */
	OutputStream getOutputStream(OutputStream out) throws IOException;
}
