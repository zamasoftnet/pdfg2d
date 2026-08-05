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
 * WOFF2をsfnt(TTF/OTF)へ戻します。
 *
 * <p>
 * WOFF2はWOFFと違い、(1)全体を<b>Brotli</b>で圧縮し、(2)字形表
 * ({@code glyf}/{@code loca})を<b>専用の形へ組み替えて</b>縮めています。
 * 実在サイトのWOFF2 1265件を調べたところ<b>1224件がこの組み替えを使って
 * いた</b>ので、伸長だけでは実用になりません。ここでは組み替えを元に戻す
 * ところまでを行います。
 * </p>
 *
 * <p>
 * <b>間違えても例外は出ません。</b> 字形の点の座標を復元する処理なので、
 * 誤ると「読めるが形が違う」出力になります。検証は
 * {@code Woff2DecoderTest} が、<b>同じフォントのWOFF2版とTTF/WOFF版</b>を
 * 突き合わせて行っています(実在サイトの資源に192組ありました)。
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/WOFF2/">WOFF File Format 2.0</a>
 */
final class Woff2Decoder {

	/** WOFF2が番号で参照する表の名前です(仕様 §5.2 表4)。順序に意味があります。 */
	private static final String[] KNOWN_TAGS = { "cmap", "head", "hhea", "hmtx", "maxp", "name", "OS/2", "post",
			"cvt ", "fpgm", "glyf", "loca", "prep", "CFF ", "VORG", "EBDT", "EBLC", "gasp", "hdmx", "kern", "LTSH",
			"PCLT", "VDMX", "vhea", "vmtx", "BASE", "GDEF", "GPOS", "GSUB", "EBSC", "JSTF", "MATH", "CBDT", "CBLC",
			"COLR", "CPAL", "SVG ", "sbix", "acnt", "avar", "bdat", "bloc", "bsln", "cvar", "fdsc", "feat", "fmtx",
			"fvar", "gvar", "hsty", "just", "lcar", "mort", "morx", "opbd", "prop", "trak", "Zapf", "Silf", "Glat",
			"Gloc", "Feat", "Sill" };

	private Woff2Decoder() {
		// utility
	}

	/** 表1つぶんの目録です。 */
	private static final class Entry {
		String tag;
		int transform;
		int origLength;
		int transformLength;
		byte[] data;
	}

	/**
	 * WOFF2を読んで、組み直したsfntを一時ファイルへ書き出します。
	 *
	 * @param raf  先頭に位置づけられている必要はありません(内部でseekします)
	 * @param file 元のファイル(エラーメッセージ用)
	 * @return 組み直したsfntの一時ファイル
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
			// glyf/locaは3が「組み替えなし」、それ以外の表は0が「組み替えなし」
			final boolean transformed = ("glyf".equals(e.tag) || "loca".equals(e.tag)) ? e.transform != 3
					: e.transform != 0;
			e.transformLength = transformed ? (int) r.base128() : e.origLength;
			entries.add(e);
		}

		// 圧縮された本体を伸長して、目録の順に切り分ける。
		// **申告された圧縮長どおりに切ること。** 余分な1バイト(末尾の
		// 4バイト境界の詰め物)まで渡すと、Brotliがそれを続きの符号として
		// 読んで「距離が負」で失敗する(2026-08-05、barlowcondensedで判明)。
		// ファイルが短いときだけ実長で頭打ちにする。
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

	/** Brotliで伸長します。 */
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
	 * 組み替えられた{@code glyf}/{@code loca}を元に戻します(仕様 §5.1)。
	 * 組み替えが使われていなければ何もしません。
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
			return; // 組み替えなし
		}
		if (loca == null) {
			throw new IOException("WOFF2 has transformed glyf without loca: " + file);
		}

		final Reader r = new Reader(glyf.data, 0);
		r.u16(); // reserved
		final int optionFlags = r.u16();
		final int numGlyphs = r.u16();
		r.u16(); // indexFormat(元の形式。こちらは常に長い形式で書き直す)
		final int nContourSize = (int) r.u32();
		final int nPointsSize = (int) r.u32();
		final int flagSize = (int) r.u32();
		final int glyphSize = (int) r.u32();
		final int compositeSize = (int) r.u32();
		final int bboxSize = (int) r.u32();
		final int instructionSize = (int) r.u32();
		if ((optionFlags & 1) != 0) {
			r.u32(); // overlapSimpleBitmapSize(読み飛ばす。字形の形には影響しない)
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
		// **ビット表の長さは4バイト単位に切り上げる**(仕様 §5.1
		// `4 * ((numGlyphs + 31) / 32)`)。単純に ceil(numGlyphs/8) にすると
		// 字形数によっては1〜3バイトずれ、**外枠の値が1バイトずつずれた
		// まま読まれる**。例外は出ず、合成字形の部品番号が桁違いの値になって
		// 初めて気づく(CashSans-MediumItalic、字形数567でちょうど1バイト)。
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
				// 空の字形。データを持たない
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

		// **どの列も使い切っているはず。** 復元を誤っても例外は出ないので、
		// 「読み終えた位置が宣言された大きさと一致するか」を必ず確かめる。
		// ここが合わないまま進むと、字形が静かに崩れた出力になる
		checkConsumed("nContour", nContour, nContourSize, file);
		checkConsumed("nPoints", nPoints, nPointsSize, file);
		checkConsumed("flag", flagsR, flagSize, file);
		checkConsumed("glyph", glyphR, glyphSize, file);
		checkConsumed("composite", compositeR, compositeSize, file);
		checkConsumed("instruction", instructionR, instructionSize, file);

		glyf.data = glyfOut.toByteArray();
		glyf.origLength = glyf.data.length;
		glyf.transform = 3;

		// **locaは常に長い形式で書く。** 点を短縮せずに書いているぶん字形表は
		// 元より大きくなるので、短い形式(2バイト×1/2)の上限128KiBを
		// 超えることがある。超えると後ろの字形の位置がずれて**空の字形として
		// 読まれる**(実際にfa-brands-400で字形301が消えた)。元のWOFF2が
		// どちらでも、こちらは長い形式に統一し、headもそれに合わせる。
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

	/** その列を宣言どおり使い切ったかを確かめます。 */
	private static void checkConsumed(final String name, final Reader r, final int declared, final File file)
			throws IOException {
		if (r.consumed() != declared) {
			throw new IOException("WOFF2 " + name + " stream: read " + r.consumed() + " of " + declared + " bytes: "
					+ file);
		}
	}

	/** 単純字形を書き出します。 */
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
		// **点は素直な形で書く。** 短縮形(X_SHORT_VECTOR・SAME)を使わず、
		// 曲線上かどうかのビットだけ立てて座標は16ビットの差分で書く。
		// 仕様上そのまま正しく、復元の誤りを持ち込む余地が小さい
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

	/** 合成字形を書き出します。部品の並びは組み替えられていないのでそのまま写します。 */
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
	 * 点の座標の三つ組符号(仕様 §5.2)を1点ぶん解きます。
	 *
	 * <p>
	 * 旗の下位7ビットが、xとyそれぞれの<b>桁数と符号</b>、および続く
	 * バイト数を決めます。ここは仕様の表をそのまま写した部分で、
	 * <b>誤ると例外が出ないまま字形が崩れる</b>ので手を入れないこと。
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

	/** 組み直した表からsfntを書き出します。目録は表の名前順(仕様の要求)。 */
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

	/** バイト列を前から読む道具です。 */
	private static final class Reader {
		private final byte[] b;
		private int p;

		private final int start;

		Reader(final byte[] b, final int p) {
			this.b = b;
			this.p = p;
			this.start = p;
		}

		/** 生成時の位置から何バイト読んだか。 */
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

		/** UIntBase128(仕様 §4.1)。7ビットずつ、最上位ビットが継続の印。 */
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

		/** 255UInt16(仕様 §4.2)。253/254/255が桁上げの印。 */
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
