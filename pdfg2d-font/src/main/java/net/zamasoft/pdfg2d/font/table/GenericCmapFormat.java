/*

 Copyright 2001  The Apache Software Foundation 

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 */
package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 文字コード→GID写像の圧縮範囲表現です(2026-08-01に全文字HashMap展開から
 * 置換)。
 *
 * <p>
 * 従来はどのformatも{@code HashMap<Integer, Integer>}へ1文字=1エントリで
 * 展開していたため、CJKフォント1つで数MB、290フォントの起動で約350MBを
 * 保持していた(実測)。cmap format 12/13と同型の範囲(先頭コード・末尾
 * コード・GID基点・GID固定フラグ)の並びに圧縮すると数KB/フォントになり、
 * 参照({@link #mapCharCode})もboxingなしの二分探索になる。
 * </p>
 *
 * @param starts   各範囲の先頭文字コード(昇順)
 * @param ends     各範囲の末尾文字コード(両端含む)
 * @param gids     各範囲のGID基点
 * @param constant 真なら範囲内の全コードが同じGID(format 13の多対一)、
 *                 偽ならコードとともにGIDも+1ずつ進む
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 */
public record GenericCmapFormat(int[] starts, int[] ends, int[] gids, boolean[] constant)
		implements CmapFormat, Serializable {
	private static final Logger LOG = Logger.getLogger(GenericCmapFormat.class.getName());
	private static final long serialVersionUID = 1L;

	protected static final long LEAD_OFFSET = 0xD800 - (0x10000 >> 10);
	protected static final long SURROGATE_OFFSET = 0x10000 - (0xD800 << 10) - 0xDC00;

	/**
	 * This will read the required data from the stream.
	 *
	 * @param format    the CMAP this encoding belongs to.
	 * @param numGlyphs number of glyphs.
	 * @param data      The stream to read the data from.
	 * @return a new GenericCmapFormat
	 * @throws IOException If there is an error reading the data.
	 */
	public static GenericCmapFormat read(int format, int numGlyphs, RandomAccessFile data) throws IOException {
		if (format < 8) {
			data.readUnsignedShort();
			data.readUnsignedShort();
		} else if (format < 14) {
			// read an other UnsignedShort to read a Fixed32
			data.readUnsignedShort();
			data.readInt();
			data.readInt();
		}
		CmapData cmapData = switch (format) {
			case 0 -> processSubtype0(data);
			case 2 -> processSubtype2(data, numGlyphs);
			case 4 -> processSubtype4(data, numGlyphs);
			case 6 -> processSubtype6(data, numGlyphs);
			case 8 -> processSubtype8(data, numGlyphs);
			case 10 -> processSubtype10(data, numGlyphs);
			case 12 -> processSubtype12(data, numGlyphs);
			case 13 -> processSubtype13(data, numGlyphs);
			default -> throw new IOException("Unknown cmap format:" + format);
		};
		return compress(cmapData.characterCodeToGlyphId);
	}

	/**
	 * パース中に組み立てた写像を圧縮範囲へ変換します(パース用のMapと
	 * 逆引き配列はここで捨てる——保持するのは範囲配列だけ)。
	 */
	private static GenericCmapFormat compress(final Map<Integer, Integer> characterCodeToGlyphId) {
		final long[] pairs = new long[characterCodeToGlyphId.size()];
		int n = 0;
		for (final Entry<Integer, Integer> e : characterCodeToGlyphId.entrySet()) {
			pairs[n++] = ((long) e.getKey() << 16) | (e.getValue() & 0xFFFFL);
		}
		Arrays.sort(pairs);
		int rangeCount = 0;
		final int[] starts = new int[n], ends = new int[n], gids = new int[n];
		final boolean[] constant = new boolean[n];
		for (int i = 0; i < n; ++i) {
			final int code = (int) (pairs[i] >>> 16);
			final int gid = (int) (pairs[i] & 0xFFFFL);
			if (rangeCount > 0 && code == ends[rangeCount - 1] + 1) {
				final int k = rangeCount - 1;
				final int length = ends[k] - starts[k] + 1;
				if (length == 1 && gid == gids[k]) {
					// 2点目で固定GIDモードが確定
					constant[k] = true;
					ends[k] = code;
					continue;
				}
				if (constant[k] ? gid == gids[k] : gid == gids[k] + length) {
					ends[k] = code;
					continue;
				}
			}
			starts[rangeCount] = code;
			ends[rangeCount] = code;
			gids[rangeCount] = gid;
			constant[rangeCount] = false;
			++rangeCount;
		}
		return new GenericCmapFormat(Arrays.copyOf(starts, rangeCount), Arrays.copyOf(ends, rangeCount),
				Arrays.copyOf(gids, rangeCount), Arrays.copyOf(constant, rangeCount));
	}

	private record CmapData(int[] glyphIdToCharacterCode, Map<Integer, Integer> characterCodeToGlyphId) {
	}

	private static int[] readUnsignedByteArray(RandomAccessFile raf, int length) throws IOException {
		int[] array = new int[length];
		for (int i = 0; i < length; i++) {
			array[i] = raf.read();
		}
		return array;
	}

	private static int[] readUnsignedShortArray(RandomAccessFile raf, int length) throws IOException {
		int[] array = new int[length];
		for (int i = 0; i < length; i++) {
			array[i] = raf.readUnsignedShort();
		}
		return array;
	}

	/**
	 * Reads a format 8 subtable.
	 * 
	 * @param raf       the data stream of the to be parsed ttf font
	 * @param numGlyphs number of glyphs to be read
	 * @return CmapData
	 * @throws IOException If there is an error parsing the true type font.
	 */
	private static CmapData processSubtype8(RandomAccessFile raf, int numGlyphs) throws IOException {
		// --- is32 is a 65536 BITS array ( = 8192 BYTES)
		int[] is32 = readUnsignedByteArray(raf, 8192);
		long nbGroups = raf.readInt();
		// --- nbGroups shouldn't be greater than 65536
		if (nbGroups > 65536) {
			throw new IOException("CMap ( Subtype8 ) is invalid");
		}
		int[] glyphIdToCharacterCode = newGlyphIdToCharacterCode(numGlyphs);
		Map<Integer, Integer> characterCodeToGlyphId = new HashMap<Integer, Integer>(numGlyphs);
		// -- Read all sub header
		for (long i = 0; i < nbGroups; ++i) {
			long firstCode = raf.readInt();
			long endCode = raf.readInt();
			long startGlyph = raf.readInt();
			// -- process simple validation
			if (firstCode > endCode || 0 > firstCode) {
				throw new IOException("Range invalid");
			}
			for (long j = firstCode; j <= endCode; ++j) {
				// -- Convert the Character code in decimal
				if (j > Integer.MAX_VALUE) {
					throw new IOException("[Sub Format 8] Invalid Character code");
				}
				int currentCharCode;
				if ((is32[(int) j / 8] & (1 << ((int) j % 8))) == 0) {
					currentCharCode = (int) j;
				} else {
					// the character code uses a 32bits format
					// convert it in decimal : see http://www.unicode.org/faq//utf_bom.html#utf16-4
					long lead = LEAD_OFFSET + (j >> 10);
					long trail = 0xDC00 + (j & 0x3FF);
					long codepoint = (lead << 10) + trail + SURROGATE_OFFSET;
					if (codepoint > Integer.MAX_VALUE) {
						throw new IOException("[Sub Format 8] Invalid Character code");
					}
					currentCharCode = (int) codepoint;
				}
				long glyphIndex = startGlyph + (j - firstCode);
				if (glyphIndex > numGlyphs || glyphIndex > Integer.MAX_VALUE) {
					throw new IOException("CMap contains an invalid glyph index");
				}
				glyphIdToCharacterCode[(int) glyphIndex] = currentCharCode;
				characterCodeToGlyphId.put(currentCharCode, (int) glyphIndex);
			}
		}
		return new CmapData(glyphIdToCharacterCode, characterCodeToGlyphId);
	}

	/**
	 * Reads a format 10 subtable.
	 * 
	 * @param data      the data stream of the to be parsed ttf font
	 * @param numGlyphs number of glyphs to be read
	 * @return CmapData
	 * @throws IOException If there is an error parsing the true type font.
	 */
	private static CmapData processSubtype10(RandomAccessFile data, int numGlyphs) throws IOException {
		long startCode = data.readInt();
		long numChars = data.readInt();
		if (numChars > Integer.MAX_VALUE) {
			throw new IOException("Invalid number of Characters");
		}
		if (startCode < 0 || startCode > 0x0010FFFF || (startCode + numChars) > 0x0010FFFF
				|| ((startCode + numChars) >= 0x0000D800 && (startCode + numChars) <= 0x0000DFFF)) {
			throw new IOException("Invalid Characters codes");
		}
		// Format 10 is a trivial dense mapping: glyphIdArray[i] maps the
		// character (startCode + i). It is rare in practice (used by some Apple
		// fonts for a contiguous supplementary-plane range).
		int[] glyphIdToCharacterCode = newGlyphIdToCharacterCode(numGlyphs);
		Map<Integer, Integer> characterCodeToGlyphId = new HashMap<Integer, Integer>((int) numChars);
		for (int i = 0; i < numChars; ++i) {
			int glyphIndex = data.readUnsignedShort();
			if (glyphIndex >= numGlyphs) {
				LOG.log(Level.WARNING, "Format 10 cmap contains an invalid glyph index");
				break;
			}
			int charCode = (int) (startCode + i);
			glyphIdToCharacterCode[glyphIndex] = charCode;
			characterCodeToGlyphId.put(charCode, glyphIndex);
		}
		return new CmapData(glyphIdToCharacterCode, characterCodeToGlyphId);
	}

	/**
	 * Reads a format 12 subtable.
	 * 
	 * @param data      the data stream of the to be parsed ttf font
	 * @param numGlyphs number of glyphs to be read
	 * @return CmapData
	 * @throws IOException If there is an error parsing the true type font.
	 */
	private static CmapData processSubtype12(RandomAccessFile data, int numGlyphs) throws IOException {
		long nbGroups = data.readInt();
		int[] glyphIdToCharacterCode = newGlyphIdToCharacterCode(numGlyphs);
		Map<Integer, Integer> characterCodeToGlyphId = new HashMap<Integer, Integer>(numGlyphs);
		for (long i = 0; i < nbGroups; ++i) {
			long firstCode = data.readInt();
			long endCode = data.readInt();
			long startGlyph = data.readInt();
			if (firstCode < 0 || firstCode > 0x0010FFFF || firstCode >= 0x0000D800 && firstCode <= 0x0000DFFF) {
				throw new IOException("Invalid characters codes");
			}
			if (endCode > 0 && endCode < firstCode || endCode > 0x0010FFFF
					|| endCode >= 0x0000D800 && endCode <= 0x0000DFFF) {
				throw new IOException("Invalid characters codes");
			}
			for (long j = 0; j <= endCode - firstCode; ++j) {
				long glyphIndex = startGlyph + j;
				if (glyphIndex >= numGlyphs) {
					LOG.log(Level.WARNING, "Format 12 cmap contains an invalid glyph index");
					break;
				}
				if (firstCode + j > 0x10FFFF) {
					LOG.log(Level.WARNING, "Format 12 cmap contains character beyond UCS-4");
				}
				glyphIdToCharacterCode[(int) glyphIndex] = (int) (firstCode + j);
				characterCodeToGlyphId.put((int) (firstCode + j), (int) glyphIndex);
			}
		}
		return new CmapData(glyphIdToCharacterCode, characterCodeToGlyphId);
	}

	/**
	 * Reads a format 13 subtable.
	 * 
	 * @param data      the data stream of the to be parsed ttf font
	 * @param numGlyphs number of glyphs to be read
	 * @return CmapData
	 * @throws IOException If there is an error parsing the true type font.
	 */
	private static CmapData processSubtype13(RandomAccessFile data, int numGlyphs) throws IOException {
		long nbGroups = data.readInt();
		int[] glyphIdToCharacterCode = newGlyphIdToCharacterCode(numGlyphs);
		Map<Integer, Integer> characterCodeToGlyphId = new HashMap<Integer, Integer>(numGlyphs);
		for (long i = 0; i < nbGroups; ++i) {
			long firstCode = data.readInt();
			long endCode = data.readInt();
			long glyphId = data.readInt();
			if (glyphId > numGlyphs) {
				LOG.log(Level.WARNING, "Format 13 cmap contains an invalid glyph index");
				break;
			}
			if (firstCode < 0 || firstCode > 0x0010FFFF || (firstCode >= 0x0000D800 && firstCode <= 0x0000DFFF)) {
				throw new IOException("Invalid Characters codes");
			}
			if ((endCode > 0 && endCode < firstCode) || endCode > 0x0010FFFF
					|| (endCode >= 0x0000D800 && endCode <= 0x0000DFFF)) {
				throw new IOException("Invalid Characters codes");
			}
			for (long j = 0; j <= endCode - firstCode; ++j) {
				if (firstCode + j > Integer.MAX_VALUE) {
					throw new IOException("Character Code greater than Integer.MAX_VALUE");
				}
				if (firstCode + j > 0x10FFFF) {
					LOG.log(Level.WARNING, "Format 13 cmap contains character beyond UCS-4");
				}
				glyphIdToCharacterCode[(int) glyphId] = (int) (firstCode + j);
				characterCodeToGlyphId.put((int) (firstCode + j), (int) glyphId);
			}
		}
		return new CmapData(glyphIdToCharacterCode, characterCodeToGlyphId);
	}

	/**
	 * Reads a format 6 subtable.
	 * 
	 * @param data      the data stream of the to be parsed ttf font
	 * @param numGlyphs number of glyphs to be read
	 * @return CmapData
	 * @throws IOException If there is an error parsing the true type font.
	 */
	private static CmapData processSubtype6(RandomAccessFile data, int numGlyphs) throws IOException {
		int firstCode = data.readUnsignedShort();
		int entryCount = data.readUnsignedShort();
		// skip emtpy tables
		if (entryCount == 0) {
			return new CmapData(newGlyphIdToCharacterCode(numGlyphs), new HashMap<>());
		}
		Map<Integer, Integer> tmpGlyphToChar = new HashMap<Integer, Integer>(numGlyphs);
		Map<Integer, Integer> characterCodeToGlyphId = new HashMap<Integer, Integer>(numGlyphs);
		int[] glyphIdArray = readUnsignedShortArray(data, entryCount);
		int maxGlyphId = 0;
		for (int i = 0; i < entryCount; i++) {
			maxGlyphId = Math.max(maxGlyphId, glyphIdArray[i]);
			tmpGlyphToChar.put(glyphIdArray[i], firstCode + i);
			characterCodeToGlyphId.put(firstCode + i, glyphIdArray[i]);
		}
		int[] glyphIdToCharacterCode = buildGlyphIdToCharacterCodeLookup(tmpGlyphToChar, maxGlyphId);
		return new CmapData(glyphIdToCharacterCode, characterCodeToGlyphId);
	}

	/**
	 * Reads a format 4 subtable.
	 * 
	 * @param raf       the data stream of the to be parsed ttf font
	 * @param numGlyphs number of glyphs to be read
	 * @return CmapData
	 * @throws IOException If there is an error parsing the true type font.
	 */
	private static CmapData processSubtype4(RandomAccessFile raf, int numGlyphs) throws IOException {
		int segCountX2 = raf.readUnsignedShort();
		int segCount = segCountX2 / 2;
		raf.readUnsignedShort();
		raf.readUnsignedShort();
		raf.readUnsignedShort();
		int[] endCount = readUnsignedShortArray(raf, segCount);
		raf.readUnsignedShort();
		int[] startCount = readUnsignedShortArray(raf, segCount);
		int[] idDelta = readUnsignedShortArray(raf, segCount);
		int[] idRangeOffset = readUnsignedShortArray(raf, segCount);
		Map<Integer, Integer> tmpGlyphToChar = new HashMap<Integer, Integer>(numGlyphs);
		Map<Integer, Integer> characterCodeToGlyphId = new HashMap<Integer, Integer>(numGlyphs);
		int maxGlyphId = 0;
		long currentPosition = raf.getFilePointer();
		for (int i = 0; i < segCount; i++) {
			int start = startCount[i];
			int end = endCount[i];
			int delta = idDelta[i];
			int rangeOffset = idRangeOffset[i];
			if (start != 65535 && end != 65535) {
				for (int j = start; j <= end; j++) {
					if (rangeOffset == 0) {
						int glyphid = (j + delta) & 0xFFFF;
						maxGlyphId = Math.max(glyphid, maxGlyphId);
						tmpGlyphToChar.put(glyphid, j);
						characterCodeToGlyphId.put(j, glyphid);
					} else {
						// idRangeOffset is defined relative to its own slot in
						// the idRangeOffset array. currentPosition is the end
						// of that array, so the slot address is
						// currentPosition - (segCount - i) * 2, and the glyph
						// index lives rangeOffset + (j - start) * 2 bytes
						// beyond the slot — folded into one expression here.
						long glyphOffset = currentPosition + ((rangeOffset / 2) + (j - start) + (i - segCount)) * 2;
						raf.seek(glyphOffset);
						int glyphIndex = raf.readUnsignedShort();
						if (glyphIndex != 0) {
							glyphIndex = (glyphIndex + delta) & 0xFFFF;
							if (!tmpGlyphToChar.containsKey(glyphIndex)) {
								maxGlyphId = Math.max(glyphIndex, maxGlyphId);
								tmpGlyphToChar.put(glyphIndex, j);
								characterCodeToGlyphId.put(j, glyphIndex);
							}
						}
					}
				}
			}
		}
		if (tmpGlyphToChar.isEmpty()) {
			LOG.log(Level.WARNING, "cmap format 4 subtable is empty");
			return new CmapData(new int[0], characterCodeToGlyphId);
		}
		int[] glyphIdToCharacterCode = buildGlyphIdToCharacterCodeLookup(tmpGlyphToChar, maxGlyphId);
		return new CmapData(glyphIdToCharacterCode, characterCodeToGlyphId);
	}

	private static int[] buildGlyphIdToCharacterCodeLookup(Map<Integer, Integer> tmpGlyphToChar, int maxGlyphId) {
		int[] glyphIdToCharacterCode = newGlyphIdToCharacterCode(maxGlyphId + 1);
		for (Entry<Integer, Integer> entry : tmpGlyphToChar.entrySet()) {
			// link the glyphId with the right character code
			glyphIdToCharacterCode[entry.getKey()] = entry.getValue();
		}
		return glyphIdToCharacterCode;
	}

	/**
	 * Read a format 2 subtable.
	 * 
	 * @param data      the data stream of the to be parsed ttf font
	 * @param numGlyphs number of glyphs to be read
	 * @return CmapData
	 * @throws IOException If there is an error parsing the true type font.
	 */
	private static CmapData processSubtype2(RandomAccessFile data, int numGlyphs) throws IOException {
		int[] subHeaderKeys = new int[256];
		// ---- keep the Max Index of the SubHeader array to know its length
		int maxSubHeaderIndex = 0;
		for (int i = 0; i < 256; i++) {
			subHeaderKeys[i] = data.readUnsignedShort();
			maxSubHeaderIndex = Math.max(maxSubHeaderIndex, subHeaderKeys[i] / 8);
		}
		// ---- Read all SubHeaders to avoid useless seek on DataSource
		SubHeader[] subHeaders = new SubHeader[maxSubHeaderIndex + 1];
		for (int i = 0; i <= maxSubHeaderIndex; ++i) {
			int firstCode = data.readUnsignedShort();
			int entryCount = data.readUnsignedShort();
			short idDelta = data.readShort();
			int idRangeOffset = data.readUnsignedShort() - (maxSubHeaderIndex + 1 - i - 1) * 8 - 2;
			subHeaders[i] = new SubHeader(firstCode, entryCount, idDelta, idRangeOffset);
		}
		long startGlyphIndexOffset = data.getFilePointer();
		int[] glyphIdToCharacterCode = newGlyphIdToCharacterCode(numGlyphs);
		Map<Integer, Integer> characterCodeToGlyphId = new HashMap<Integer, Integer>(numGlyphs);
		for (int i = 0; i <= maxSubHeaderIndex; ++i) {
			final SubHeader sh = subHeaders[i];
			final int firstCode = sh.firstCode();
			final int idRangeOffset = sh.idRangeOffset();
			final int idDelta = sh.idDelta();
			final int entryCount = sh.entryCount();
			data.seek(startGlyphIndexOffset + idRangeOffset);
			for (int j = 0; j < entryCount; ++j) {
				// ---- compute the Character Code
				int charCode = i;
				charCode = (charCode << 8) + (firstCode + j);
				// ---- Go to the CharacterCOde position in the Sub Array
				// of the glyphIndexArray
				// glyphIndexArray contains Unsigned Short so add (j * 2) bytes
				// at the index position
				int p = data.readUnsignedShort();
				// ---- compute the glyphIndex
				if (p > 0) {
					p = (p + idDelta) % 65536;
				}

				if (p >= numGlyphs) {
					LOG.log(Level.WARNING,
							"glyphId " + p + " for charcode " + charCode + " ignored, numGlyphs is " + numGlyphs);
					continue;
				}

				glyphIdToCharacterCode[p] = charCode;
				characterCodeToGlyphId.put(charCode, p);
			}
		}
		return new CmapData(glyphIdToCharacterCode, characterCodeToGlyphId);
	}

	/**
	 * Initialize the CMapEntry when it is a subtype 0.
	 * 
	 * @param data the data stream of the to be parsed ttf font
	 * @return CmapData
	 * @throws IOException If there is an error parsing the true type font.
	 */
	private static CmapData processSubtype0(RandomAccessFile data) throws IOException {
		byte[] glyphMapping = new byte[256];
		data.readFully(glyphMapping);
		int[] glyphIdToCharacterCode = newGlyphIdToCharacterCode(256);
		Map<Integer, Integer> characterCodeToGlyphId = new HashMap<Integer, Integer>(glyphMapping.length);
		for (int i = 0; i < glyphMapping.length; i++) {
			int glyphIndex = (glyphMapping[i] + 256) % 256;
			glyphIdToCharacterCode[glyphIndex] = i;
			characterCodeToGlyphId.put(i, glyphIndex);
		}
		return new CmapData(glyphIdToCharacterCode, characterCodeToGlyphId);
	}

	/**
	 * Workaround for the fact that glyphIdToCharacterCode doesn't distinguish
	 * between missing character codes and code 0.
	 */
	private static int[] newGlyphIdToCharacterCode(int size) {
		int[] gidToCode = new int[size];
		Arrays.fill(gidToCode, -1);
		return gidToCode;
	}

	/**
	 * Returns the GlyphId linked with the given character code.
	 *
	 * @param characterCode the given character code to be mapped
	 * @return glyphId the corresponding glyph id for the given character code
	 */
	@Override
	public int mapCharCode(int characterCode) {
		// startsがcharacterCode以下の最後の範囲を二分探索
		int low = 0, high = this.starts.length - 1;
		while (low <= high) {
			final int mid = (low + high) >>> 1;
			if (this.starts[mid] <= characterCode) {
				low = mid + 1;
			} else {
				high = mid - 1;
			}
		}
		final int k = low - 1;
		if (k < 0 || characterCode > this.ends[k]) {
			return 0;
		}
		return this.constant[k] ? this.gids[k] : this.gids[k] + (characterCode - this.starts[k]);
	}

	/**
	 * Returns the character code for the given GID, or null if there is none.
	 * 複数のコードが同じGIDへ写る場合は最小のコードを返します(旧実装は
	 * formatにより挙動が揺れていた——挿入順の最初または最後)。
	 *
	 * @param gid glyph id
	 * @return character code
	 */
	public Integer getCharacterCode(int gid) {
		for (int k = 0; k < this.starts.length; ++k) {
			if (this.constant[k]) {
				if (gid == this.gids[k]) {
					return this.starts[k];
				}
				continue;
			}
			final int offset = gid - this.gids[k];
			if (offset >= 0 && offset <= this.ends[k] - this.starts[k]) {
				return this.starts[k] + offset;
			}
		}
		return null;
	}

	/**
	 * Record used to manage CMap - Format 2 SubHeader.
	 * 
	 * @param firstCode     first character code
	 * @param entryCount    number of entries
	 * @param idDelta       used to compute the GlyphIndex
	 * @param idRangeOffset number of bytes to skip to reach the firstCode
	 */
	private record SubHeader(int firstCode, int entryCount, short idDelta, int idRangeOffset) {
	}
}
