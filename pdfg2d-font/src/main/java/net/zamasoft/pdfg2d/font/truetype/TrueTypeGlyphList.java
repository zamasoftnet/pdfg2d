package net.zamasoft.pdfg2d.font.truetype;

import java.awt.geom.GeneralPath;
import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import net.zamasoft.pdfg2d.font.Glyph;
import net.zamasoft.pdfg2d.font.GlyphList;
import net.zamasoft.pdfg2d.font.table.GlyfTable;
import net.zamasoft.pdfg2d.font.table.HeadTable;
import net.zamasoft.pdfg2d.font.table.MaxpTable;

/**
 * {@link GlyphList} implementation for TrueType outline fonts.
 * Glyph outlines are decoded from the {@code glyf} table on demand and cached
 * with {@link java.lang.ref.SoftReference soft references} so they can be
 * reclaimed under memory pressure.  Both simple and composite glyphs are
 * supported; composite glyphs are resolved recursively through
 * {@link GlyfCompositeDescript}.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class TrueTypeGlyphList implements GlyphList {

	private final HeadTable head;
	private final GlyfTable glyf;
	private final AtomicReferenceArray<SoftReference<Glyph>> glyphs;

	/**
	 * Creates a new glyph list for the given TrueType font tables.
	 *
	 * @param glyf the {@code glyf} table that holds raw glyph outlines
	 * @param head the {@code head} table providing the units-per-em value
	 * @param maxp the {@code maxp} table providing the total glyph count
	 */
	public TrueTypeGlyphList(final GlyfTable glyf, final HeadTable head, final MaxpTable maxp) {
		this.head = head;
		this.glyf = glyf;
		this.glyphs = new AtomicReferenceArray<>(maxp.getNumGlyphs());
	}

	/**
	 * Returns the glyph at the given index, decoding it from the {@code glyf}
	 * table if it has not been cached yet.
	 *
	 * @param ix the glyph index (GID)
	 * @return the decoded {@link Glyph}, or {@code null} if the index is out of
	 *         range or the glyph description cannot be found
	 */
	@Override
	public Glyph getGlyph(final int ix) {
		if (ix >= this.glyphs.length()) {
			return null;
		}
		final SoftReference<Glyph> ref = this.glyphs.get(ix);
		Glyph glyph = (ref != null) ? ref.get() : null;
		if (glyph != null) {
			return glyph;
		}

		final GlyfDescript gd = this.glyf.getDescription(ix);
		if (gd == null) {
			return null;
		}
		final short upm = this.head.getUnitsPerEm();
		final GeneralPath path = new GeneralPath();
		final float scale = 1000f / upm;
		final int pointCount = gd.getPointCount();
		final int contourCount = gd.getContourCount();

		// 輪郭ごとに、点列を巡回しながら2次ベジェへ変換する
		// (2026-08-15修正: 旧実装は「先頭2点を末尾へ継ぎ足す」方式で輪郭を
		// 閉じようとしていたが、残り1点になった反復で未初期化の隣接点を読み、
		// **始点へ戻る最後の曲線を落として直線で閉じていた**。〇や( の
		// 滑らかな側面が弦に化ける実書籍の欠陥として発覚。TrueTypeの
		// 標準的な巡回アルゴリズムへ置き換える)
		int start = 0;
		for (int c = 0; c < contourCount; ++c) {
			final int end = gd.getEndPtOfContours(c);
			if (end < start || end >= pointCount) {
				break;
			}
			final int n = end - start + 1;
			if (n <= 0) {
				start = end + 1;
				continue;
			}

			// 開始点を決める。先頭が曲線上ならそれ、そうでなければ末尾、
			// 双方とも制御点なら両者の中点(TrueTypeの規約)
			final float firstX = gd.getXCoordinate(start) * scale;
			final float firstY = -(gd.getYCoordinate(start) * scale);
			final boolean firstOn = (gd.getFlags(start) & GlyfDescript.onCurve) != 0;
			final float lastX = gd.getXCoordinate(end) * scale;
			final float lastY = -(gd.getYCoordinate(end) * scale);
			final boolean lastOn = (gd.getFlags(end) & GlyfDescript.onCurve) != 0;

			final float startX, startY;
			final int firstIndex;
			if (firstOn) {
				startX = firstX;
				startY = firstY;
				firstIndex = 1;
			} else if (lastOn) {
				startX = lastX;
				startY = lastY;
				firstIndex = 0;
			} else {
				startX = midValue(lastX, firstX);
				startY = midValue(lastY, firstY);
				firstIndex = 0;
			}
			path.moveTo(startX, startY);

			// 制御点を溜めながら、開始点の次から輪郭を1周する
			boolean hasControl = false;
			float controlX = 0, controlY = 0;
			final int steps = firstOn ? n - 1 : n;
			for (int k = 0; k < steps; ++k) {
				final int i = start + (firstIndex + k) % n;
				final float px = gd.getXCoordinate(i) * scale;
				final float py = -(gd.getYCoordinate(i) * scale);
				final boolean on = (gd.getFlags(i) & GlyfDescript.onCurve) != 0;
				if (on) {
					if (hasControl) {
						path.quadTo(controlX, controlY, px, py);
						hasControl = false;
					} else {
						path.lineTo(px, py);
					}
				} else {
					if (hasControl) {
						// 連続する制御点の間には曲線上の点が省略されている
						path.quadTo(controlX, controlY, midValue(controlX, px), midValue(controlY, py));
					}
					controlX = px;
					controlY = py;
					hasControl = true;
				}
			}
			// 開始点へ戻って閉じる。制御点が残っていれば最後の曲線を必ず描く
			if (hasControl) {
				path.quadTo(controlX, controlY, startX, startY);
			}
			path.closePath();

			start = end + 1;
		}

		glyph = new Glyph(path, null);
		this.glyphs.set(ix, new SoftReference<>(glyph));
		return glyph;
	}

	private static float midValue(final float a, final float b) {
		return a + (b - a) / 2.0f;
	}
}
