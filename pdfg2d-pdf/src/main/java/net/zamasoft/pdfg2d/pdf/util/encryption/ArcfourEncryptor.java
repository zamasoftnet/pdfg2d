package net.zamasoft.pdfg2d.pdf.util.encryption;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * RC4 (Arcfour) stream cipher implementation of the {@link Encryptor} interface
 * for use in PDF standard security handlers (revisions 2–4).
 * <p>
 * The RC4 key-scheduling algorithm (KSA) is executed once during construction
 * using the first {@code len} bytes of the supplied key. The resulting
 * initialised S-box state ({@code orgSalt}) is cloned for every encryption
 * operation so that the same encryptor instance can be used for multiple
 * independent byte sequences without re-keying.
 * </p>
 * <p>
 * RC4 is a stream cipher ({@link #isBlock()} returns {@code false}), so
 * in-place encryption via {@link #fastEncrypt} and streaming encryption via
 * {@link #getOutputStream} are the primary entry points. Block-mode operation
 * ({@link #blockEncrypt}) is not supported.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class ArcfourEncryptor implements Encryptor {
	private final int[] orgSalt = new int[256];

	/**
	 * A streaming wrapper around the Arcfour cipher. Each instance clones the
	 * parent encryptor's S-box at construction time so that multiple independent
	 * streams can be derived from the same key without interference.
	 */
	class ArcfourOutputStream extends FilterOutputStream {
		private final int[] salt;

		private final int[] bc = { 0, 0 };

		/**
		 * Constructs an {@code ArcfourOutputStream} that wraps {@code out} and
		 * encrypts every byte using a fresh clone of the parent encryptor's S-box.
		 *
		 * @param out the underlying output stream that receives the ciphertext
		 */
		public ArcfourOutputStream(OutputStream out) {
			super(out);
			this.salt = (int[]) ArcfourEncryptor.this.orgSalt.clone();
		}

		/**
		 * Encrypts a single byte and writes the ciphertext byte to the underlying
		 * stream.
		 *
		 * @param x the plaintext byte (only the low 8 bits are used)
		 * @throws IOException if the underlying stream throws an {@link IOException}
		 */
		public void write(int x) throws IOException {
			this.out.write(ArcfourEncryptor.this.encrypt(this.salt, this.bc, (byte) x));
		}

		/**
		 * Encrypts all bytes in {@code bytes} and writes the ciphertext to the
		 * underlying stream.
		 *
		 * @param bytes the plaintext bytes
		 * @throws IOException if the underlying stream throws an {@link IOException}
		 */
		public void write(byte[] bytes) throws IOException {
			for (int i = 0; i < bytes.length; ++i) {
				this.write(bytes[i]);
			}
		}

		/**
		 * Encrypts {@code len} bytes starting at {@code off} in {@code bytes} and
		 * writes the ciphertext to the underlying stream.
		 *
		 * @param bytes the plaintext byte array
		 * @param off   start offset in {@code bytes}
		 * @param len   number of bytes to encrypt and write
		 * @throws IOException if the underlying stream throws an {@link IOException}
		 */
		public void write(byte[] bytes, int off, int len) throws IOException {
			for (int i = 0; i < len; ++i) {
				this.write(bytes[i + off]);
			}
		}
	}

	/**
	 * Constructs an {@code ArcfourEncryptor} by running the RC4 key-scheduling
	 * algorithm with the first {@code len} bytes of {@code key}.
	 *
	 * @param key the raw key bytes; only the first {@code len} bytes are used
	 * @param len the number of key bytes to use (1–32)
	 * @throws IllegalArgumentException if {@code len} is not in the range 1–32
	 */
	public ArcfourEncryptor(byte[] key, int len) {
		if (len < 0 || len > 32) {
			throw new IllegalArgumentException("The key length is limited to 1 to 32.");
		}
		for (int i = 0; i < this.orgSalt.length; i++) {
			this.orgSalt[i] = i;
		}

		int keyIndex = 0;
		int saltIndex = 0;
		for (int i = 0; i < this.orgSalt.length; i++) {
			byte x = key[keyIndex];
			saltIndex = ((x < 0 ? 256 + x : x) + this.orgSalt[i] + saltIndex) % 256;
			this.swap(this.orgSalt, i, saltIndex);
			keyIndex = (keyIndex + 1) % len;
		}
	}

	/**
	 * Encrypts {@code len} bytes of {@code data} in place starting at {@code off},
	 * using a fresh clone of the S-box so that the encryptor state is not modified.
	 *
	 * @param data the byte array to encrypt in place
	 * @param off  start offset in {@code data}
	 * @param len  number of bytes to encrypt
	 */
	public final void fastEncrypt(byte[] data, int off, int len) {
		int[] salt = (int[]) this.orgSalt.clone();
		int[] bc = { 0, 0 };
		for (int i = 0; i < len; ++i) {
			data[i + off] = this.encrypt(salt, bc, data[i + off]);
		}
	}

	private final void swap(int[] salt, int firstIndex, int secondIndex) {
		int tmp = salt[firstIndex];
		salt[firstIndex] = salt[secondIndex];
		salt[secondIndex] = tmp;
	}

	private byte encrypt(int[] salt, int[] bc, byte x) {
		bc[0] = (bc[0] + 1) % 256;
		bc[1] = (salt[bc[0]] + bc[1]) % 256;
		this.swap(salt, bc[0], bc[1]);
		int saltIndex = (salt[bc[0]] + salt[bc[1]]) % 256;
		return (byte) (x ^ (byte) salt[saltIndex]);
	}

	/**
	 * Wraps {@code out} with an {@link ArcfourOutputStream} that encrypts every
	 * byte written to it using a fresh clone of this encryptor's S-box.
	 *
	 * @param out the underlying output stream
	 * @return an encrypting {@link OutputStream}
	 */
	public OutputStream getOutputStream(OutputStream out) {
		return new ArcfourOutputStream(out);
	}

	/**
	 * Encrypts the entire {@code data} array in place and returns the same array.
	 *
	 * @param data the plaintext byte array, modified in place
	 * @return the same {@code data} array, now containing ciphertext
	 */
	public byte[] encrypt(byte[] data) {
		this.fastEncrypt(data, 0, data.length);
		return data;
	}

	/**
	 * Returns {@code false} because RC4 is a stream cipher and does not operate in
	 * block mode.
	 *
	 * @return {@code false}
	 */
	public boolean isBlock() {
		return false;
	}

	/**
	 * Not supported for RC4 stream encryption.
	 *
	 * @param data ignored
	 * @param off  ignored
	 * @param len  ignored
	 * @return never returns normally
	 * @throws UnsupportedOperationException always
	 */
	public byte[] blockEncrypt(byte[] data, int off, int len) {
		throw new UnsupportedOperationException();
	}
}