package net.zamasoft.pdfg2d.font;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Restores WOFF2 to sfnt (TTF/OTF).
 *
 * <p>
 * Unlike WOFF, WOFF2 (1) compresses the whole payload with <b>Brotli</b> and
 * (2) shrinks glyph tables ({@code glyf}/{@code loca}) by <b>transforming them into a dedicated format</b>.
 * Of 1265 WOFF2 files examined from real websites, <b>1224 used this transform</b>,
 * so decompression alone is not practical. This class also reverses the transform.
 * </p>
 *
 * <p>
 * <b>Mistakes do not throw exceptions.</b> This code restores glyph point coordinates,
 * so errors produce output that is readable but has incorrect shapes.
 * {@code Woff2DecoderTest} verifies it by comparing <b>WOFF2 and TTF/WOFF versions of the same font</b>
 * (192 pairs were found among resources from real websites).
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/WOFF2/">WOFF File Format 2.0</a>
 */
final class Woff2Decoder {

	/** Table names referenced by number in WOFF2 (specification §5.2, Table 4). Order matters. */
	private static final String[] KNOWN_TAGS = { "cmap", "head", "hhea", "hmtx", "maxp", "name", "OS/2", "post",
			"cvt ", "fpgm", "glyf", "loca", "prep", "CFF ", "VORG", "EBDT", "EBLC", "gasp", "hdmx", "kern", "LTSH",
			"PCLT", "VDMX", "vhea", "vmtx", "BASE", "GDEF", "GPOS", "GSUB", "EBSC", "JSTF", "MATH", "CBDT", "CBLC",
			"COLR", "CPAL", "SVG ", "sbix", "acnt", "avar", "bdat", "bloc", "bsln", "cvar", "fdsc", "feat", "fmtx",
			"fvar", "gvar", "hsty", "just", "lcar", "mort", "morx", "opbd", "prop", "trak", "Zapf", "Silf", "Glat",
			"Gloc", "Feat", "Sill" };

	private Woff2Decoder() {
		// utility
	}

	/** Directory entry for one table. */
	private static final class Entry {
		String tag;
		int transform;
		int origLength;
		int transformLength;
		byte[] data;
	}

	/**
	 * Reads WOFF2 and writes the reconstructed sfnt to a temporary file.
	 *
	 * @param raf  need not be positioned at the beginning (seeks internally)
	 * @param file original file (for error messages)
	 * @return temporary file containing the reconstructed sfnt
	 */
	static File extract(final RandomAccessFile raf, final File file) throws IOException {
		raf.seek(0);
		final byte[] all = new byte[(int) raf.length()];
		raf.readFully(all);
		final Reader r = new Reader(all, 0);
		if (r.u32() != 0x774F4632L) { // 'wOF2'
			throw new IOException("Not a WOFF2 file: " + file);
		}
		final long flavor = r.u32();
		if (flavor == 0x74746366L) { // 'ttcf'
			throw new IOException("WOFF2 font collections are not supported: " + file);
		}
		r.u32(); // length
		final int numTables = r.u16();
		r.u16(); // reserved
		r.u32(); // totalSfntSize
		final long totalCompressedSize = r.u32();
		r.u16(); // majorVersion
		r.u16(); // minorVersion
		r.u32(); // metaOffset
		r.u32(); // metaLength
		r.u32(); // metaOrigLength
		r.u32(); // privOffset
		r.u32(); // privLength

		final List<Entry> entries = new ArrayList<>(numTables);
		for (int i = 0; i < numTables; ++i) {
			final Entry e = new Entry();
			final int flags = r.u8();
			final int index = flags & 0x3f;
			e.transform = (flags >> 6) & 0x3;
			if (index == 0x3f) {
				e.tag = new String(r.bytes(4), StandardCharsets.ISO_8859_1);
			} else {
				if (index >= KNOWN_TAGS.length) {
					throw new IOException("Unknown WOFF2 table index " + index + ": " + file);
				}
				e.tag = KNOWN_TAGS[index];
			}
			e.origLength = (int) r.base128();
			// For glyf/loca, 3 means "no transform"; for other tables, 0 means "no transform".
			final boolean transformed = ("glyf".equals(e.tag) || "loca".equals(e.tag)) ? e.transform != 3
					: e.transform != 0;
			e.transformLength = transformed ? (int) r.base128() : e.origLength;
			entries.add(e);
		}

		// Decompress the payload and split it in directory order.
		// **Slice at exactly the declared compressed length.** Passing even one extra byte
		// (trailing padding to a 4-byte boundary) makes Brotli read it as a continuation code
		// and fail with a "negative distance" (2026-08-05, found with barlowcondensed).
		// Cap at the actual length only when the file is shorter.
		final int bodyStart = r.pos();
		final int available = all.length - bodyStart;
		final byte[] plain = brotli(all, bodyStart, Math.min((int) totalCompressedSize, available), file);
		int off = 0;
		for (final Entry e : entries) {
			if (off + e.transformLength > plain.length) {
				throw new IOException("Truncated WOFF2 data at table " + e.tag + ": " + file);
			}
			e.data = new byte[e.transformLength];
			System.arraycopy(plain, off, e.data, 0, e.transformLength);
			off += e.transformLength;
		}

		reconstructGlyf(entries, file);

		for (final Entry e : entries) {
			final boolean transformed = ("glyf".equals(e.tag) || "loca".equals(e.tag)) ? e.transform != 3
					: e.transform != 0;
			if (transformed) {
				throw new IOException("Unsupported WOFF2 transform " + e.transform + " for " + e.tag + ": " + file);
			}
		}

		return writeSfnt(flavor, entries);
	}

	/** Decompresses with Brotli. */
	private static byte[] brotli(final byte[] src, final int off, final int len, final File file) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(len * 4, 1024));
		try (InputStream in = new org.brotli.dec.BrotliInputStream(
				new java.io.ByteArrayInputStream(src, off, len))) {
			final byte[] buff = new byte[8192];
			int n;
			while ((n = in.read(buff)) > 0) {
				out.write(buff, 0, n);
			}
		} catch (final IOException e) {
			throw new IOException("Cannot decompress WOFF2 (Brotli): " + file, e);
		}
		return out.toByteArray();
	}

	/**
	 * Reverses transforms on {@code glyf}/{@code loca} (specification §5.1).
	 * Does nothing if no transform is used.
	 */
	private static void reconstructGlyf(final List<Entry> entries, final File file) throws IOException {
		Entry glyf = null, loca = null, head = null;
		for (final Entry e : entries) {
			switch (e.tag) {
			case "glyf" -> glyf = e;
			case "loca" -> loca = e;
			case "head" -> head = e;
			default -> {
			}
			}
		}
		if (glyf == null || glyf.transform == 3) {
			return; // No transform.
		}
		if (loca == null) {
			throw new IOException("WOFF2 has transformed glyf without loca: " + file);
		}

		final Reader r = new Reader(glyf.data, 0);
		r.u16(); // reserved
		final int optionFlags = r.u16();
		final int numGlyphs = r.u16();
		r.u16(); // indexFormat (original format; always rewritten here in long format).
		final int nContourSize = (int) r.u32();
		final int nPointsSize = (int) r.u32();
		final int flagSize = (int) r.u32();
		final int glyphSize = (int) r.u32();
		final int compositeSize = (int) r.u32();
		final int bboxSize = (int) r.u32();
		final int instructionSize = (int) r.u32();
		if ((optionFlags & 1) != 0) {
			r.u32(); // overlapSimpleBitmapSize (skip; does not affect glyph shapes).
		}
		int p = r.pos();
		final Reader nContour = new Reader(glyf.data, p);
		p += nContourSize;
		final Reader nPoints = new Reader(glyf.data, p);
		p += nPointsSize;
		final Reader flagsR = new Reader(glyf.data, p);
		p += flagSize;
		final Reader glyphR = new Reader(glyf.data, p);
		p += glyphSize;
		final Reader compositeR = new Reader(glyf.data, p);
		p += compositeSize;
		final int bboxStart = p;
		// **Round the bitmap length up to a multiple of 4 bytes** (specification §5.1:
		// `4 * ((numGlyphs + 31) / 32)`). Simply using ceil(numGlyphs/8) causes
		// a 1-3 byte offset for some glyph counts, so **bounding box values are read
		// at shifted byte positions**. No exception occurs; the error becomes apparent only when composite glyph
		// component numbers are wildly wrong (CashSans-MediumItalic, exactly 1 byte with 567 glyphs).
		final int bitmapLen = 4 * ((numGlyphs + 31) / 32);
		final Reader bboxR = new Reader(glyf.data, bboxStart + bitmapLen);
		p += bboxSize;
		final Reader instructionR = new Reader(glyf.data, p);

		final ByteArrayOutputStream glyfOut = new ByteArrayOutputStream(glyf.origLength);
		final int[] offsets = new int[numGlyphs + 1];
		for (int gid = 0; gid < numGlyphs; ++gid) {
			offsets[gid] = glyfOut.size();
			final int nContours = (short) nContour.u16();
			final boolean haveBbox = (glyf.data[bboxStart + (gid >> 3)] & (0x80 >> (gid & 7))) != 0;
			if (nContours == 0) {
				// Empty glyph. Has no data.
				continue;
			}
			if (nContours < 0) {
				writeComposite(glyfOut, compositeR, glyphR, instructionR, bboxR, haveBbox, file);
			} else {
				writeSimple(glyfOut, nContours, nPoints, flagsR, glyphR, instructionR, bboxR, haveBbox);
			}
			pad4(glyfOut);
		}
		offsets[numGlyphs] = glyfOut.size();

		// **Every stream should be fully consumed.** Incorrect reconstruction does not throw an exception,
		// so always verify that the final read position matches the declared size.
		// Continuing with a mismatch here silently produces malformed glyphs.
		checkConsumed("nContour", nContour, nContourSize, file);
		checkConsumed("nPoints", nPoints, nPointsSize, file);
		checkConsumed("flag", flagsR, flagSize, file);
		checkConsumed("glyph", glyphR, glyphSize, file);
		checkConsumed("composite", compositeR, compositeSize, file);
		checkConsumed("instruction", instructionR, instructionSize, file);

		glyf.data = glyfOut.toByteArray();
		glyf.origLength = glyf.data.length;
		glyf.transform = 3;

		// **Always write loca in long format.** Writing uncompressed points makes the glyph table
		// larger than the original, which can exceed the short format's 128 KiB limit
		// (2-byte offsets in half units). If exceeded, later glyph offsets shift and the glyphs
		// are **read as empty** (glyph 301 actually disappeared in fa-brands-400). Regardless of the
		// original WOFF2 format, use long format consistently and update head to match.
		final ByteArrayOutputStream locaOut = new ByteArrayOutputStream((numGlyphs + 1) * 4);
		final DataOutputStream locaDos = new DataOutputStream(locaOut);
		for (int i = 0; i <= numGlyphs; ++i) {
			locaDos.writeInt(offsets[i]);
		}
		locaDos.flush();
		loca.data = locaOut.toByteArray();
		loca.origLength = loca.data.length;
		loca.transform = 3;

		if (head != null && head.data.length >= 52) {
			head.data[50] = 0;
			head.data[51] = 1; // indexToLocFormat = long
		}
	}

	/** Checks that the stream has been fully consumed as declared. */
	private static void checkConsumed(final String name, final Reader r, final int declared, final File file)
			throws IOException {
		if (r.consumed() != declared) {
			throw new IOException("WOFF2 " + name + " stream: read " + r.consumed() + " of " + declared + " bytes: "
					+ file);
		}
	}

	/** Writes a simple glyph. */
	private static void writeSimple(final ByteArrayOutputStream out, final int nContours, final Reader nPoints,
			final Reader flagsR, final Reader glyphR, final Reader instructionR, final Reader bboxR,
			final boolean haveBbox) throws IOException {
		final int[] endPts = new int[nContours];
		int total = 0;
		for (int i = 0; i < nContours; ++i) {
			total += nPoints.u255();
			endPts[i] = total - 1;
		}
		final int[] x = new int[total];
		final int[] y = new int[total];
		final boolean[] onCurve = new boolean[total];
		int cx = 0, cy = 0;
		for (int i = 0; i < total; ++i) {
			final int flag = flagsR.u8();
			onCurve[i] = (flag & 0x80) == 0;
			final int[] d = triplet(flag & 0x7f, glyphR);
			cx += d[0];
			cy += d[1];
			x[i] = cx;
			y[i] = cy;
		}
		final int instructionLength = glyphR.u255();
		final byte[] instructions = instructionR.bytes(instructionLength);

		int xMin, yMin, xMax, yMax;
		if (haveBbox) {
			xMin = (short) bboxR.u16();
			yMin = (short) bboxR.u16();
			xMax = (short) bboxR.u16();
			yMax = (short) bboxR.u16();
		} else {
			xMin = yMin = Integer.MAX_VALUE;
			xMax = yMax = Integer.MIN_VALUE;
			for (int i = 0; i < total; ++i) {
				xMin = Math.min(xMin, x[i]);
				yMin = Math.min(yMin, y[i]);
				xMax = Math.max(xMax, x[i]);
				yMax = Math.max(yMax, y[i]);
			}
			if (total == 0) {
				xMin = yMin = xMax = yMax = 0;
			}
		}

		final DataOutputStream d = new DataOutputStream(out);
		d.writeShort(nContours);
		d.writeShort(xMin);
		d.writeShort(yMin);
		d.writeShort(xMax);
		d.writeShort(yMax);
		for (int i = 0; i < nContours; ++i) {
			d.writeShort(endPts[i]);
		}
		d.writeShort(instructionLength);
		d.write(instructions);
		// **Write points in a straightforward form.** Do not use short forms (X_SHORT_VECTOR or SAME);
		// set only the on-curve bit and write coordinates as 16-bit deltas.
		// This is valid under the specification and leaves less room for reconstruction errors.
		for (int i = 0; i < total; ++i) {
			d.writeByte(onCurve[i] ? 0x01 : 0x00);
		}
		int px = 0;
		for (int i = 0; i < total; ++i) {
			d.writeShort(x[i] - px);
			px = x[i];
		}
		int py = 0;
		for (int i = 0; i < total; ++i) {
			d.writeShort(y[i] - py);
			py = y[i];
		}
		d.flush();
	}

	/** Writes a composite glyph. Copies the component sequence unchanged because it was not transformed. */
	private static void writeComposite(final ByteArrayOutputStream out, final Reader compositeR, final Reader glyphR,
			final Reader instructionR, final Reader bboxR, final boolean haveBbox, final File file)
			throws IOException {
		if (!haveBbox) {
			throw new IOException("Composite glyph without bbox in WOFF2: " + file);
		}
		final int xMin = (short) bboxR.u16();
		final int yMin = (short) bboxR.u16();
		final int xMax = (short) bboxR.u16();
		final int yMax = (short) bboxR.u16();

		final int start = compositeR.pos();
		boolean haveInstructions = false;
		boolean more = true;
		while (more) {
			final int flags = compositeR.u16();
			compositeR.u16(); // glyphIndex
			more = (flags & 0x0020) != 0; // MORE_COMPONENTS
			haveInstructions |= (flags & 0x0100) != 0; // WE_HAVE_INSTRUCTIONS
			compositeR.skip((flags & 0x0001) != 0 ? 4 : 2); // ARG_1_AND_2_ARE_WORDS
			if ((flags & 0x0008) != 0) { // WE_HAVE_A_SCALE
				compositeR.skip(2);
			} else if ((flags & 0x0040) != 0) { // X_AND_Y_SCALE
				compositeR.skip(4);
			} else if ((flags & 0x0080) != 0) { // TWO_BY_TWO
				compositeR.skip(8);
			}
		}
		final int end = compositeR.pos();

		final DataOutputStream d = new DataOutputStream(out);
		d.writeShort(-1);
		d.writeShort(xMin);
		d.writeShort(yMin);
		d.writeShort(xMax);
		d.writeShort(yMax);
		d.write(compositeR.slice(start, end - start));
		if (haveInstructions) {
			final int len = glyphR.u255();
			d.writeShort(len);
			d.write(instructionR.bytes(len));
		}
		d.flush();
	}

	/**
	 * Decodes the triplet coordinate encoding (specification §5.2) for one point.
	 *
	 * <p>
	 * The low 7 flag bits determine the <b>magnitude and sign</b> of x and y
	 * and the number of following bytes. This code directly transcribes the specification table.
	 * Do not modify it: <b>mistakes distort glyphs without throwing exceptions</b>.
	 * </p>
	 */
	private static int[] triplet(final int flag, final Reader in) throws IOException {
		final int dx, dy;
		if (flag < 10) {
			final int b0 = in.u8();
			dx = 0;
			dy = sign(flag, ((flag & 14) << 7) + b0);
		} else if (flag < 20) {
			final int b0 = in.u8();
			dx = sign(flag, (((flag - 10) & 14) << 7) + b0);
			dy = 0;
		} else if (flag < 84) {
			final int b0 = in.u8();
			final int b = flag - 20;
			dx = sign(flag, 1 + (b & 0x30) + (b0 >> 4));
			dy = sign(flag >> 1, 1 + ((b & 0x0c) << 2) + (b0 & 0x0f));
		} else if (flag < 120) {
			final int b0 = in.u8();
			final int b1 = in.u8();
			final int b = flag - 84;
			dx = sign(flag, 1 + ((b / 12) << 8) + b0);
			dy = sign(flag >> 1, 1 + (((b % 12) >> 2) << 8) + b1);
		} else if (flag < 124) {
			final int b0 = in.u8();
			final int b1 = in.u8();
			final int b2 = in.u8();
			dx = sign(flag, (b0 << 4) + (b1 >> 4));
			dy = sign(flag >> 1, ((b1 & 0x0f) << 8) + b2);
		} else {
			final int b0 = in.u8();
			final int b1 = in.u8();
			final int b2 = in.u8();
			final int b3 = in.u8();
			dx = sign(flag, (b0 << 8) + b1);
			dy = sign(flag >> 1, (b2 << 8) + b3);
		}
		return new int[] { dx, dy };
	}

	private static int sign(final int flag, final int value) {
		return (flag & 1) != 0 ? value : -value;
	}

	private static void pad4(final ByteArrayOutputStream out) {
		while ((out.size() & 3) != 0) {
			out.write(0);
		}
	}

	/** Writes sfnt from the reconstructed tables. Sorts the directory by table name (required by the specification). */
	private static File writeSfnt(final long flavor, final List<Entry> entries) throws IOException {
		final List<Entry> sorted = new ArrayList<>(entries);
		sorted.sort((a, b) -> a.tag.compareTo(b.tag));
		final int numTables = sorted.size();
		final File temp = File.createTempFile("pdfg2d-woff2-", ".dat");
		try (OutputStream fos = new java.io.BufferedOutputStream(new FileOutputStream(temp));
				DataOutputStream out = new DataOutputStream(fos)) {
			out.writeInt((int) flavor);
			out.writeShort(numTables);
			out.writeShort(0); // searchRange
			out.writeShort(0); // entrySelector
			out.writeShort(0); // rangeShift
			int offset = 12 + numTables * 16;
			for (final Entry e : sorted) {
				out.write(e.tag.getBytes(StandardCharsets.ISO_8859_1));
				out.writeInt(checksum(e.data));
				out.writeInt(offset);
				out.writeInt(e.data.length);
				offset += (e.data.length + 3) & ~3;
			}
			for (final Entry e : sorted) {
				out.write(e.data);
				for (int pad = (4 - (e.data.length & 3)) & 3; pad > 0; --pad) {
					out.writeByte(0);
				}
			}
		}
		temp.deleteOnExit();
		return temp;
	}

	private static int checksum(final byte[] data) {
		int sum = 0;
		for (int i = 0; i < data.length; i += 4) {
			int v = 0;
			for (int j = 0; j < 4; ++j) {
				v <<= 8;
				if (i + j < data.length) {
					v |= data[i + j] & 0xff;
				}
			}
			sum += v;
		}
		return sum;
	}

	/** Utility for reading a byte sequence from the beginning. */
	private static final class Reader {
		private final byte[] b;
		private int p;

		private final int start;

		Reader(final byte[] b, final int p) {
			this.b = b;
			this.p = p;
			this.start = p;
		}

		/** Number of bytes read since the position at construction. */
		int consumed() {
			return this.p - this.start;
		}

		int pos() {
			return this.p;
		}

		void skip(final int n) {
			this.p += n;
		}

		int u8() throws IOException {
			if (this.p >= this.b.length) {
				throw new IOException("Unexpected end of WOFF2 data");
			}
			return this.b[this.p++] & 0xff;
		}

		int u16() throws IOException {
			return (this.u8() << 8) | this.u8();
		}

		long u32() throws IOException {
			return ((long) this.u16() << 16) | this.u16();
		}

		byte[] bytes(final int n) throws IOException {
			if (this.p + n > this.b.length) {
				throw new IOException("Unexpected end of WOFF2 data");
			}
			final byte[] r = new byte[n];
			System.arraycopy(this.b, this.p, r, 0, n);
			this.p += n;
			return r;
		}

		byte[] slice(final int from, final int n) {
			final byte[] r = new byte[n];
			System.arraycopy(this.b, from, r, 0, n);
			return r;
		}

		/** UIntBase128 (specification §4.1). Seven bits at a time; the most significant bit marks continuation. */
		long base128() throws IOException {
			long v = 0;
			for (int i = 0; i < 5; ++i) {
				final int c = this.u8();
				v = (v << 7) | (c & 0x7f);
				if ((c & 0x80) == 0) {
					return v;
				}
			}
			throw new IOException("Malformed UIntBase128 in WOFF2");
		}

		/** 255UInt16 (specification §4.2). 253/254/255 mark extended values. */
		int u255() throws IOException {
			final int c = this.u8();
			if (c == 253) {
				return this.u16();
			}
			if (c == 254) {
				return this.u8() + 253 * 2;
			}
			if (c == 255) {
				return this.u8() + 253;
			}
			return c;
		}
	}
}
