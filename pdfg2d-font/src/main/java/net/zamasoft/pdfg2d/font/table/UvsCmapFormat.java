package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import net.zamasoft.pdfg2d.util.LongIntLookup;

/**
 * UVS (Unicode Variation Sequence) cmap format.
 *
 * <p>
 * 2026-08-01にboxed Map/Setからプリミティブ索引({@link LongIntLookup}+
 * ソート済みint[])へ圧縮({@link GenericCmapFormat}と同方針)。IVS
 * コレクションを持つCJKフォントは数千〜数万対を持つため、boxed実装は
 * フォントあたり数百KBの保持と参照毎のboxingを生んでいた。
 * </p>
 *
 * @param codeToGlyphId {@code (unicodeValue << 32) | varSelector} →GIDの索引
 * @param selectors     異体字セレクタの昇順配列
 */
public record UvsCmapFormat(LongIntLookup codeToGlyphId, int[] selectors) implements CmapFormat {

	/**
	 * Reads a format 14 subtable.
	 *
	 * @param data      the data stream of the to be parsed ttf font
	 * @param numGlyphs number of glyphs to be read
	 * @return a new UvsCmapFormat
	 * @throws IOException If there is an error parsing the true type font.
	 */
	public static UvsCmapFormat read(final RandomAccessFile data, final int numGlyphs) throws IOException {
		data.readInt(); // length
		final long start = data.getFilePointer() - 6;
		final int numVarSelectorRecords = data.readInt();
		final int[] selectors = new int[numVarSelectorRecords];
		long[] keys = new long[64];
		int[] gids = new int[64];
		int count = 0;
		for (int i = 0; i < numVarSelectorRecords; ++i) {
			final int varSelector = 0xFFFFFF & ((data.readShort() << 8) | (0xFF & data.readByte()));
			selectors[i] = varSelector;
			@SuppressWarnings("unused")
			final long defaultUVSOffset = data.readInt();
			final long nonDefaultUVSOffset = data.readInt();
			final long pos = data.getFilePointer();
			if (nonDefaultUVSOffset != 0) {
				data.seek(start + nonDefaultUVSOffset);
				final int numUVSMappings = data.readInt();
				if (count + numUVSMappings > keys.length) {
					final int newSize = Math.max(keys.length * 2, count + numUVSMappings);
					keys = Arrays.copyOf(keys, newSize);
					gids = Arrays.copyOf(gids, newSize);
				}
				for (int j = 0; j < numUVSMappings; ++j) {
					final long unicodeValue = 0xFFFFFF & ((data.readShort() << 8) | (0xFF & data.readByte()));
					final int glyphId = 0xFFFF & data.readShort();
					keys[count] = (unicodeValue << 32L) | varSelector;
					gids[count] = glyphId;
					++count;
				}
			}
			data.seek(pos);
		}
		Arrays.sort(selectors);
		return new UvsCmapFormat(LongIntLookup.fromUnsorted(keys, gids, count), selectors);
	}

	@Override
	public int mapCharCode(final int c) {
		return 0;
	}

	@Override
	public int mapCharCode(final int c, final int vs) {
		return this.codeToGlyphId.getOrDefault(((long) c << 32L) | (long) vs, 0);
	}

	public boolean isVarSelector(final int c) {
		return Arrays.binarySearch(this.selectors, c) >= 0;
	}
}
