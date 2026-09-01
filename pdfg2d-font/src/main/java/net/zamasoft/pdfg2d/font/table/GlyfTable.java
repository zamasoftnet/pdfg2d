package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

import net.zamasoft.pdfg2d.font.truetype.GlyfCompositeDescript;
import net.zamasoft.pdfg2d.font.truetype.GlyfDescript;
import net.zamasoft.pdfg2d.font.truetype.GlyfSimpleDescript;

/**
 * OpenType {@code glyf} (Glyph Data) table.
 * <p>
 * Holds the raw outline data for each TrueType glyph.  Glyph outlines are
 * read on demand using the byte offsets supplied by the companion
 * {@link LocaTable}.  Both simple glyphs ({@link net.zamasoft.pdfg2d.font.truetype.GlyfSimpleDescript
 * GlyfSimpleDescript}) and composite glyphs
 * ({@link net.zamasoft.pdfg2d.font.truetype.GlyfCompositeDescript GlyfCompositeDescript}) are
 * supported.
 * </p>
 *
 * @param de   the directory entry that locates this table in the font file
 * @param loca the {@code loca} table used to map glyph indices to byte offsets
 * @param raf  the random-access file from which glyph data is read
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 */
public record GlyfTable(DirectoryEntry de, LocaTable loca, RandomAccessFile raf) implements Table {

	/**
	 * いま読んでいる途中のグリフ番号です(スレッドごと)。
	 *
	 * <p>
	 * 合成グリフの成分は{@link #getDescription(int)}で読み直すので、成分が
	 * 自分自身を(直接または循環して)指す不正なフォントでは無限再帰して
	 * {@code StackOverflowError}になる。2026-09-01に本番のフォント一覧が
	 * これで落ちた——書体ごとに代表符号位置の字形を引いてscriptを名乗るように
	 * したところ、フォントパックの1書体でこの循環を踏んだ。
	 * </p>
	 *
	 * <p>
	 * <b>深さの上限では切れない。</b>{@code GlyfCompositeDescript.getPointCount()}は
	 * 呼び直して<b>別に組み立てた</b>記述子の上で自分を呼ぶので、深さは
	 * 上限と上限−1の間を往復するだけで、フレームだけが積み上がる。読んでいる
	 * 最中のグリフ番号を覚えて、そこへ戻る成分を落とす必要がある。
	 * </p>
	 *
	 * <p>
	 * {@code record}はインスタンスフィールドを持てないのでスレッドごとに持つ。
	 * 同じ表を複数のスレッドが読んでも、経路はスレッドごとに独立している。
	 * </p>
	 */
	private static final ThreadLocal<java.util.Set<Integer>> READING = ThreadLocal
			.withInitial(java.util.HashSet::new);

	/**
	 * Reads and returns the glyph description for the glyph at the given index.
	 *
	 * @param i the glyph index (GID)
	 * @return the {@link GlyfDescript} for the glyph, or {@code null} if the
	 *         glyph has no outline (e.g., space character)
	 * @throws RuntimeException wrapping an {@link java.io.IOException} if the
	 *                          glyph data cannot be read
	 */
	public GlyfDescript getDescription(final int i) {
		GlyfDescript desc = null;
		final java.util.Set<Integer> reading = READING.get();
		if (!reading.add(i)) {
			// このグリフは読んでいる最中——成分が自分へ戻っている不正なフォント。
			// 字形の無いグリフと同じ扱いにして、読み手に成分を落とさせる
			return null;
		}
		try {
			final int len = this.loca.getOffset((i + 1)) - this.loca.getOffset(i);
			if (len <= 0) {
				return null;
			}
			synchronized (this.raf) {
				this.raf.seek(this.de.offset() + this.loca.getOffset(i));
				final int numberOfContours = this.raf.readShort();
				if (numberOfContours >= 0) {
					desc = GlyfSimpleDescript.read(this, numberOfContours, this.raf);
				} else {
					desc = GlyfCompositeDescript.read(this, this.raf);
				}
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			reading.remove(i);
		}
		return desc;
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return GLYF;
	}
}
