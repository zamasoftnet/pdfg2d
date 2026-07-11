package net.zamasoft.pdfg2d.pdf.util.encryption;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.params.EncryptionParams;
import net.zamasoft.pdfg2d.pdf.params.Permissions;
import net.zamasoft.pdfg2d.pdf.params.V1EncryptionParams;
import net.zamasoft.pdfg2d.pdf.params.V2EncryptionParams;
import net.zamasoft.pdfg2d.pdf.params.V4EncryptionParams;

/**
 * Handles PDF standard security handler key derivation and encryption
 * dictionary generation.
 * <p>
 * On construction this class writes the encryption dictionary object to the
 * main PDF content stream and computes the document encryption key following
 * the algorithm described in PDF specification section 7.6.3. Supported
 * encryption versions are V1 (RC4 40-bit), V2 (RC4 variable-length), and V4
 * (RC4 or AES-128 via crypt filters).
 * </p>
 * <p>
 * After construction callers use {@link #getEncryptor(ObjectRef)} to obtain an
 * {@link Encryptor} instance suitable for encrypting an individual PDF object,
 * and {@link #getObjectRef()} to obtain the reference to the encryption
 * dictionary so that it can be included in the document trailer.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class Encryption {

	// Padding to adjust password to 32 bytes
	private static final byte[] PADDING = { (byte) 0x28, (byte) 0xBF, (byte) 0x4E, (byte) 0x5E, (byte) 0x4E,
			(byte) 0x75, (byte) 0x8A, (byte) 0x41, (byte) 0x64, (byte) 0x00, (byte) 0x4E, (byte) 0x56, (byte) 0xFF,
			(byte) 0xFA, (byte) 0x01, (byte) 0x08, (byte) 0x2E, (byte) 0x2E, (byte) 0x00, (byte) 0xB6, (byte) 0xD0,
			(byte) 0x68, (byte) 0x3E, (byte) 0x80, (byte) 0x2F, (byte) 0x0C, (byte) 0xA9, (byte) 0xFE, (byte) 0x64,
			(byte) 0x53, (byte) 0x69, (byte) 0x7A };

	/**
	 * Truncates password to 32 bytes.
	 * 
	 * @param password the password
	 * @return 32-byte padded password
	 */
	private static byte[] truncate32(byte[] password) {
		byte[] result = new byte[32];
		if (password.length < 32) {
			System.arraycopy(password, 0, result, 0, password.length);
			System.arraycopy(PADDING, 0, result, password.length, 32 - password.length);
		} else {
			System.arraycopy(password, 0, result, 0, 32);
		}
		return result;
	}

	private final MessageDigest md5;

	private final byte[] key;

	private final ObjectRef ref;

	private final int length;

	private final V4EncryptionParams.CFM cfm;

	private ObjectRef keyRef;

	private Encryptor encryptor;

	/**
	 * Constructs an {@code Encryption} instance, writes the encryption dictionary
	 * to the PDF output, and derives the document encryption key.
	 *
	 * @param mainFlow the main PDF fragment output stream to which the encryption
	 *                 dictionary object is written
	 * @param xref     the cross-reference table used to allocate an object number
	 *                 for the encryption dictionary
	 * @param fileid   the two-element file identifier array from the PDF trailer;
	 *                 {@code fileid[0]} is used during key derivation
	 * @param params   the encryption parameters defining the algorithm version,
	 *                 passwords, and permissions
	 * @throws IOException if an I/O error occurs while writing the encryption
	 *                     dictionary
	 */
	public Encryption(PDFFragmentOutput mainFlow, XRef xref, byte[][] fileid, EncryptionParams params)
			throws IOException {
		try {
			this.md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		// Encryption dictionary
		this.ref = xref.nextObjectRef();

		if (params.getType() == EncryptionParams.Type.V5) {
			// The V5/R6 (AES-256) handler shares nothing with the legacy key
			// derivation below; it is written by its own routine.
			this.cfm = V4EncryptionParams.CFM.AESV3;
			this.length = 32;
			this.key = this.writeV5(mainFlow, (net.zamasoft.pdfg2d.pdf.params.V5EncryptionParams) params);
			return;
		}

		mainFlow.startObject(this.ref);
		mainFlow.startHash();

		mainFlow.writeName("Filter");
		mainFlow.writeName("Standard");
		mainFlow.lineBreak();

		EncryptionParams.Type v = params.getType();
		mainFlow.writeName("V");
		mainFlow.writeInt(v.v);
		mainFlow.lineBreak();

		Permissions permissions;
		int length;
		switch (v) {
			case V1: {
				// V1 encryption permissions
				this.cfm = V4EncryptionParams.CFM.V2;
				V1EncryptionParams v1Params = (V1EncryptionParams) params;
				permissions = v1Params.getPermissions();
				length = 40;
			}
				break;

			case V2: {
				// V2 encryption permissions
				this.cfm = V4EncryptionParams.CFM.V2;
				V2EncryptionParams v2Params = (V2EncryptionParams) params;
				permissions = v2Params.getPermissions();

				length = v2Params.getLength();
				if (length != 40) {
					mainFlow.writeName("Length");
					mainFlow.writeInt(length);
					mainFlow.lineBreak();
				}
			}
				break;

			case V4: {
				// V4 encryption permissions
				V4EncryptionParams v4Params = (V4EncryptionParams) params;
				permissions = v4Params.getPermissions();

				if (!v4Params.getEncryptMetadata()) {
					mainFlow.writeName("EncryptMetadata");
					mainFlow.writeBoolean(false);
					mainFlow.lineBreak();
				}

				String filterName = "StdCF";
				mainFlow.writeName("CF");
				mainFlow.startHash();
				mainFlow.writeName(filterName);
				mainFlow.startHash();

				mainFlow.writeName("Type");
				mainFlow.writeName("CryptFilter");
				mainFlow.lineBreak();

				this.cfm = v4Params.getCFM();
				mainFlow.writeName("CFM");
				mainFlow.writeName(this.cfm.name);
				mainFlow.lineBreak();

				length = v4Params.getLength();
				if (length != 40) {
					mainFlow.writeName("Length");
					mainFlow.writeInt(length);
					mainFlow.lineBreak();
				}

				mainFlow.endHash();
				mainFlow.endHash();

				mainFlow.writeName("StmF");
				mainFlow.writeName(filterName);
				mainFlow.lineBreak();

				mainFlow.writeName("StrF");
				mainFlow.writeName(filterName);
				mainFlow.lineBreak();
			}
				break;

			default:
				throw new IllegalArgumentException();
		}
		this.length = length / 8;

		Permissions.Type r = permissions.getType();
		mainFlow.writeName("R");
		mainFlow.writeInt(r.r);
		mainFlow.lineBreak();

		int pflags = permissions.getFlags();
		mainFlow.writeName("P");
		mainFlow.writeInt(pflags);
		mainFlow.lineBreak();

		// Generate owner key
		byte[] ownerPass = params.getOwnerPassword().getBytes("ISO-8859-1");
		byte[] userPass = params.getUserPassword().getBytes("ISO-8859-1");
		if (ownerPass.length == 0) {
			ownerPass = userPass;
		}

		byte[] ownerKey;
		this.md5.reset();
		this.md5.update(truncate32(ownerPass));
		{
			if (r.r >= Permissions.Type.R3.r) {
				// Revision 3+ requires 50 MD5 hash iterations
				for (int i = 0; i < 50; ++i) {
					byte[] key = this.md5.digest();
					this.md5.update(key);
				}
			}
			byte[] key = this.md5.digest();
			ownerKey = truncate32(userPass);
			ArcfourEncryptor arcfour = new ArcfourEncryptor(key, this.length);
			ownerKey = arcfour.encrypt(ownerKey);
			if (r.r >= Permissions.Type.R3.r) {
				// Revision 3+ requires 19 Arcfour encryption iterations
				byte[] key2 = new byte[this.length];
				for (int i = 1; i <= 19; ++i) {
					for (int j = 0; j < this.length; ++j) {
						key2[j] = (byte) (key[j] ^ i);
					}
					ArcfourEncryptor arcfour2 = new ArcfourEncryptor(key2, this.length);
					ownerKey = arcfour2.encrypt(ownerKey);
				}
			}
		}

		// Generate encryption key
		this.md5.reset();
		this.md5.update(truncate32(userPass));
		this.md5.update(ownerKey);
		{
			byte[] key = new byte[4];
			key[0] = (byte) (pflags & 0xFF);
			key[1] = (byte) ((pflags >>> 8) & 0xFF);
			key[2] = (byte) ((pflags >>> 16) & 0xFF);
			key[3] = (byte) ((pflags >>> 24) & 0xFF);
			this.md5.update(key);
		}
		this.md5.update(fileid[0]);
		if (r.r >= Permissions.Type.R3.r) {
			// Revision 3+ requires 50 MD5 hash iterations
			for (int i = 0; i < 50; ++i) {
				byte[] key = this.md5.digest();
				this.md5.update(key);
			}
		}
		this.key = this.md5.digest();

		// Generate user key
		byte[] userKey;
		switch (r) {
			case R2: {
				// Revision 2 uses Arcfour encryption
				userKey = new byte[PADDING.length];
				System.arraycopy(PADDING, 0, userKey, 0, PADDING.length);
				ArcfourEncryptor arcfour = new ArcfourEncryptor(this.key, this.length);
				userKey = arcfour.encrypt(userKey);
			}
				break;

			case R3:
			case R4: {
				// Revision 3+ gets MD5 hash of key
				this.md5.reset();
				this.md5.update(PADDING);
				this.md5.update(fileid[0]);
				byte[] digest = this.md5.digest();
				ArcfourEncryptor arcfour = new ArcfourEncryptor(this.key, this.length);
				digest = arcfour.encrypt(digest);
				byte[] key2 = new byte[this.length];
				for (int i = 1; i <= 19; ++i) {
					for (int j = 0; j < this.length; ++j) {
						key2[j] = (byte) (key[j] ^ i);
					}
					ArcfourEncryptor arcfour2 = new ArcfourEncryptor(key2, this.length);
					digest = arcfour2.encrypt(digest);
				}
				userKey = new byte[32];
				System.arraycopy(digest, 0, userKey, 0, digest.length);
			}
				break;

			default:
				throw new IllegalArgumentException();
		}

		mainFlow.writeName("O");
		mainFlow.writeBytes8(ownerKey, 0, ownerKey.length);
		mainFlow.lineBreak();

		mainFlow.writeName("U");
		mainFlow.writeBytes8(userKey, 0, userKey.length);
		mainFlow.lineBreak();

		mainFlow.endHash();
		mainFlow.endObject();
	}

	/**
	 * Writes the revision 6 (AES-256) encryption dictionary and returns the
	 * random 256-bit file encryption key. Implements ISO 32000-2
	 * Algorithms 2.A (hash) and 8/9 (U/O and UE/OE), plus the Perms entry.
	 *
	 * @param mainFlow the output stream for the encryption dictionary
	 * @param params   the V5 encryption parameters
	 * @return the file encryption key (32 bytes)
	 * @throws IOException if writing fails
	 */
	private byte[] writeV5(final PDFFragmentOutput mainFlow, final net.zamasoft.pdfg2d.pdf.params.V5EncryptionParams params)
			throws IOException {
		final var random = new java.security.SecureRandom();
		final MessageDigest sha256;
		try {
			sha256 = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		final byte[] userPass = utf8(params.getUserPassword());
		byte[] ownerPass = utf8(params.getOwnerPassword());
		if (ownerPass.length == 0) {
			ownerPass = userPass;
		}

		// Random 256-bit file encryption key and per-password salts.
		final byte[] fileKey = new byte[32];
		random.nextBytes(fileKey);
		final byte[] uValidation = new byte[8];
		final byte[] uKeySalt = new byte[8];
		final byte[] oValidation = new byte[8];
		final byte[] oKeySalt = new byte[8];
		random.nextBytes(uValidation);
		random.nextBytes(uKeySalt);
		random.nextBytes(oValidation);
		random.nextBytes(oKeySalt);

		// U = hash(pw, uValidation, "") || uValidation || uKeySalt
		final byte[] uHash = hash2B(userPass, uValidation, null);
		final byte[] u = concat(uHash, uValidation, uKeySalt);

		// O depends on the full U entry per the spec.
		final byte[] oHash = hash2B(ownerPass, oValidation, u);
		final byte[] o = concat(oHash, oValidation, oKeySalt);

		// UE/OE wrap the file key with a key derived from the key salt.
		final byte[] ueKey = hash2B(userPass, uKeySalt, null);
		final byte[] ue = aesNoPadNoIV(ueKey, fileKey, true);
		final byte[] oeKey = hash2B(ownerPass, oKeySalt, u);
		final byte[] oe = aesNoPadNoIV(oeKey, fileKey, true);

		// Perms: 16 bytes encrypted with the file key (ECB, no padding).
		final int p = params.getPermissions().getFlags();
		final byte[] perms = new byte[16];
		perms[0] = (byte) (p & 0xFF);
		perms[1] = (byte) ((p >>> 8) & 0xFF);
		perms[2] = (byte) ((p >>> 16) & 0xFF);
		perms[3] = (byte) ((p >>> 24) & 0xFF);
		perms[4] = perms[5] = perms[6] = perms[7] = (byte) 0xFF;
		perms[8] = (byte) (params.getEncryptMetadata() ? 'T' : 'F');
		perms[9] = 'a';
		perms[10] = 'd';
		perms[11] = 'b';
		random.nextBytes(new byte[0]);
		perms[12] = (byte) random.nextInt(256);
		perms[13] = (byte) random.nextInt(256);
		perms[14] = (byte) random.nextInt(256);
		perms[15] = (byte) random.nextInt(256);
		final byte[] permsEnc = aesEcbNoPad(fileKey, perms);

		mainFlow.startObject(this.ref);
		mainFlow.startHash();
		mainFlow.writeName("Filter");
		mainFlow.writeName("Standard");
		mainFlow.lineBreak();
		mainFlow.writeName("V");
		mainFlow.writeInt(5);
		mainFlow.lineBreak();
		mainFlow.writeName("R");
		mainFlow.writeInt(6);
		mainFlow.lineBreak();
		mainFlow.writeName("Length");
		mainFlow.writeInt(256);
		mainFlow.lineBreak();
		if (!params.getEncryptMetadata()) {
			mainFlow.writeName("EncryptMetadata");
			mainFlow.writeBoolean(false);
			mainFlow.lineBreak();
		}
		mainFlow.writeName("CF");
		mainFlow.startHash();
		mainFlow.writeName("StdCF");
		mainFlow.startHash();
		mainFlow.writeName("Type");
		mainFlow.writeName("CryptFilter");
		mainFlow.lineBreak();
		mainFlow.writeName("CFM");
		mainFlow.writeName("AESV3");
		mainFlow.lineBreak();
		mainFlow.writeName("Length");
		mainFlow.writeInt(32);
		mainFlow.lineBreak();
		mainFlow.endHash();
		mainFlow.endHash();
		mainFlow.writeName("StmF");
		mainFlow.writeName("StdCF");
		mainFlow.lineBreak();
		mainFlow.writeName("StrF");
		mainFlow.writeName("StdCF");
		mainFlow.lineBreak();
		mainFlow.writeName("P");
		mainFlow.writeInt(p);
		mainFlow.lineBreak();
		mainFlow.writeName("O");
		mainFlow.writeBytes8(o, 0, o.length);
		mainFlow.lineBreak();
		mainFlow.writeName("U");
		mainFlow.writeBytes8(u, 0, u.length);
		mainFlow.lineBreak();
		mainFlow.writeName("OE");
		mainFlow.writeBytes8(oe, 0, oe.length);
		mainFlow.lineBreak();
		mainFlow.writeName("UE");
		mainFlow.writeBytes8(ue, 0, ue.length);
		mainFlow.lineBreak();
		mainFlow.writeName("Perms");
		mainFlow.writeBytes8(permsEnc, 0, permsEnc.length);
		mainFlow.lineBreak();
		mainFlow.endHash();
		mainFlow.endObject();

		return fileKey;
	}

	private static byte[] utf8(final String s) {
		// SASLprep is required by the spec; for the printable ASCII/BMP
		// passwords used here plain UTF-8 (truncated to 127 bytes) matches.
		final byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		return (b.length <= 127) ? b : java.util.Arrays.copyOf(b, 127);
	}

	private static byte[] concat(final byte[]... parts) {
		var len = 0;
		for (final var part : parts) {
			len += part.length;
		}
		final byte[] out = new byte[len];
		var off = 0;
		for (final var part : parts) {
			System.arraycopy(part, 0, out, off, part.length);
			off += part.length;
		}
		return out;
	}

	/**
	 * ISO 32000-2 Algorithm 2.B hardened password hash: SHA-256 seeded, then
	 * repeated AES-128-CBC rounds mixing SHA-256/384/512 until the round
	 * count and the last byte agree the work is done.
	 */
	private static byte[] hash2B(final byte[] password, final byte[] salt, final byte[] userKey) {
		try {
			final var sha256 = MessageDigest.getInstance("SHA-256");
			sha256.update(password);
			sha256.update(salt);
			if (userKey != null) {
				sha256.update(userKey, 0, 48);
			}
			byte[] k = sha256.digest();

			for (var round = 0;; ++round) {
				// K1 = 64 repetitions of (password || K || userKey[0..48])
				final var one = concat(password, k, userKey != null ? java.util.Arrays.copyOf(userKey, 48) : new byte[0]);
				final byte[] k1 = new byte[one.length * 64];
				for (var i = 0; i < 64; ++i) {
					System.arraycopy(one, 0, k1, i * one.length, one.length);
				}
				// E = AES-128-CBC(key = K[0..16], iv = K[16..32], K1), no pad
				final var cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
				cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
						new javax.crypto.spec.SecretKeySpec(k, 0, 16, "AES"),
						new javax.crypto.spec.IvParameterSpec(k, 16, 16));
				final byte[] e = cipher.doFinal(k1);
				// Modulo 3 of the first 16 bytes selects the digest.
				var mod = 0;
				for (var i = 0; i < 16; ++i) {
					mod += e[i] & 0xFF;
				}
				mod %= 3;
				final var md = MessageDigest.getInstance(switch (mod) {
					case 0 -> "SHA-256";
					case 1 -> "SHA-384";
					default -> "SHA-512";
				});
				k = md.digest(e);
				// Stop after at least 64 rounds once E's last byte <= round-32.
				if (round >= 63 && (e[e.length - 1] & 0xFF) <= round - 32) {
					break;
				}
			}
			return java.util.Arrays.copyOf(k, 32);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** AES-256-CBC with a zero IV and no padding (for UE/OE key wrapping). */
	private static byte[] aesNoPadNoIV(final byte[] key, final byte[] data, final boolean encrypt) {
		try {
			final var cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
			cipher.init(encrypt ? javax.crypto.Cipher.ENCRYPT_MODE : javax.crypto.Cipher.DECRYPT_MODE,
					new javax.crypto.spec.SecretKeySpec(key, "AES"),
					new javax.crypto.spec.IvParameterSpec(new byte[16]));
			return cipher.doFinal(data);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** AES-256-ECB with no padding (for the 16-byte Perms block). */
	private static byte[] aesEcbNoPad(final byte[] key, final byte[] data) {
		try {
			final var cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"));
			return cipher.doFinal(data);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns an {@link Encryptor} configured to encrypt the content of the PDF
	 * object identified by {@code keyRef}.
	 * <p>
	 * The per-object key is derived by appending the object and generation numbers
	 * (and for AES, the salt bytes {@code sAlT}) to the document encryption key
	 * and hashing the result with MD5, as specified in PDF section 7.6.2.
	 * The returned encryptor instance is cached and reused when the same
	 * {@code keyRef} is requested consecutively.
	 * </p>
	 *
	 * @param keyRef the object reference whose number and generation are mixed into
	 *               the per-object key derivation
	 * @return an {@link Encryptor} ready to encrypt data for the given object
	 */
	public Encryptor getEncryptor(ObjectRef keyRef) {
		if (this.cfm == V4EncryptionParams.CFM.AESV3) {
			// R6: every object is encrypted directly with the 256-bit file
			// key; there is no per-object key derivation.
			if (this.encryptor == null) {
				this.encryptor = new AESEncryptor(this.key, 32);
			}
			return this.encryptor;
		}
		if (this.keyRef != keyRef) {
			int keyLen = Math.min(this.length + 5, 16);
			switch (this.cfm) {
				case V2: {
					byte[] work = new byte[this.length + 5];
					System.arraycopy(this.key, 0, work, 0, this.length);
					work[this.length] = (byte) (keyRef.objectNumber() & 0xFF);
					work[this.length + 1] = (byte) ((keyRef.objectNumber() >>> 8) & 0xFF);
					work[this.length + 2] = (byte) ((keyRef.objectNumber() >>> 16) & 0xFF);
					work[this.length + 3] = (byte) (keyRef.generationNumber() & 0xFF);
					work[this.length + 4] = (byte) ((keyRef.generationNumber() >>> 8) & 0xFF);
					this.md5.reset();
					this.md5.update(work);
					byte[] arckey = this.md5.digest();
					this.keyRef = keyRef;
					this.encryptor = new ArcfourEncryptor(arckey, keyLen);
					break;
				}

				case AESV2: {
					byte[] work = new byte[this.length + 5 + 4];
					System.arraycopy(this.key, 0, work, 0, this.length);
					work[this.length] = (byte) (keyRef.objectNumber() & 0xFF);
					work[this.length + 1] = (byte) ((keyRef.objectNumber() >>> 8) & 0xFF);
					work[this.length + 2] = (byte) ((keyRef.objectNumber() >>> 16) & 0xFF);
					work[this.length + 3] = (byte) (keyRef.generationNumber() & 0xFF);
					work[this.length + 4] = (byte) ((keyRef.generationNumber() >>> 8) & 0xFF);
					// AES adds 'sAlT'
					// Adobe PDF Spec 1.6 had an omission, corrected in 1.7
					work[this.length + 5] = 0x73;
					work[this.length + 6] = 0x41;
					work[this.length + 7] = 0x6C;
					work[this.length + 8] = 0x54;
					this.md5.reset();
					this.md5.update(work);
					byte[] arckey = this.md5.digest();
					this.keyRef = keyRef;
					this.encryptor = new ArcfourEncryptor(arckey, keyLen);
					this.encryptor = new AESEncryptor(arckey, keyLen);
					break;
				}
				default:
					throw new IllegalStateException();
			}
		}
		return this.encryptor;
	}

	/**
	 * Returns the object reference of the encryption dictionary written during
	 * construction. This reference should be included in the PDF trailer
	 * {@code Encrypt} entry.
	 *
	 * @return the object reference of the encryption dictionary
	 */
	public ObjectRef getObjectRef() {
		return this.ref;
	}
}
