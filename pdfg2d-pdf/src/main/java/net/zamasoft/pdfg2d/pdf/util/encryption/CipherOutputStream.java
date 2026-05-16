package net.zamasoft.pdfg2d.pdf.util.encryption;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.Cipher;

/**
 * An {@link OutputStream} that encrypts data using a {@link Cipher} before
 * writing to an underlying stream.
 * <p>
 * Data is fed through {@link Cipher#update} as it is written. When
 * {@link #close()} is called the cipher is finalised with
 * {@link Cipher#doFinal()}, flushing any remaining buffered ciphertext (e.g.
 * PKCS padding for AES-CBC) to the underlying stream before closing it.
 * </p>
 * <p>
 * This class is used internally by {@link AESEncryptor#getOutputStream} to
 * stream-encrypt PDF object data for the {@code AESV2} crypt filter.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CipherOutputStream extends FilterOutputStream {
	private final Cipher cipher;
	private byte[] single = new byte[1];

	/**
	 * Constructs a {@code CipherOutputStream} that wraps {@code out} and encrypts
	 * all written data using {@code cipher}.
	 * <p>
	 * The caller is responsible for initialising {@code cipher} (including writing
	 * any IV) before passing it to this constructor.
	 * </p>
	 *
	 * @param out    the underlying output stream that receives the ciphertext
	 * @param cipher an already-initialised {@link Cipher} in encryption mode
	 */
	public CipherOutputStream(OutputStream out, Cipher cipher) {
		super(out);
		this.cipher = cipher;
	}

	/**
	 * Encrypts a single byte and writes the resulting ciphertext to the underlying
	 * stream.
	 *
	 * @param x the plaintext byte (only the low 8 bits are used)
	 * @throws IOException if an I/O error occurs writing to the underlying stream
	 */
	public void write(int x) throws IOException {
		this.single[0] = (byte) x;
		this.write(this.single, 0, 1);
	}

	/**
	 * Encrypts all bytes in {@code bytes} and writes the ciphertext to the
	 * underlying stream.
	 *
	 * @param bytes the plaintext byte array
	 * @throws IOException if an I/O error occurs writing to the underlying stream
	 */
	public void write(byte[] bytes) throws IOException {
		this.write(bytes, 0, bytes.length);
	}

	/**
	 * Encrypts {@code len} bytes starting at {@code off} in {@code bytes} and
	 * writes any available ciphertext output to the underlying stream.
	 * <p>
	 * Because the underlying cipher may buffer data internally (e.g. to complete
	 * an AES block), this method may not write any bytes to {@code out} on every
	 * call.
	 * </p>
	 *
	 * @param bytes the plaintext byte array
	 * @param off   start offset in {@code bytes}
	 * @param len   number of bytes to encrypt
	 * @throws IOException if an I/O error occurs writing to the underlying stream
	 */
	public void write(byte[] bytes, int off, int len) throws IOException {
		byte[] block = this.cipher.update(bytes, off, len);
		if (block != null) {
			this.out.write(block);
		}
	}

	/**
	 * Finalises the cipher (flushing any remaining buffered output, such as PKCS
	 * padding), writes the final ciphertext block to the underlying stream, and
	 * then closes the underlying stream.
	 *
	 * @throws IOException if an I/O error occurs, or if the cipher finalisation
	 *                     fails (e.g. due to a padding error)
	 */
	public void close() throws IOException {
		Exception ex = null;
		try {
			byte[] block = this.cipher.doFinal();
			if (block != null) {
				this.out.write(block);
			}
		} catch (Exception e) {
			ex = e;
		} finally {
			this.out.close();
		}
		if (ex != null) {
			IOException ioe = new IOException();
			ioe.initCause(ex);
			throw ioe;
		}
	}
}
