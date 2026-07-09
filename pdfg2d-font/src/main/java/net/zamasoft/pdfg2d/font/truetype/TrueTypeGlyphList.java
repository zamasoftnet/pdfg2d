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
		int count = 0, endIndex = 0;
		float[] x = new float[5], y = new float[5];
		boolean[] onCurve = new boolean[5];
		int scount = 0;
		final float[] sx = new float[2], sy = new float[2];
		final boolean[] sonCurve = new boolean[2];
		boolean first = true;

		for (int i = 0; i < gd.getPointCount(); i++) {
			if (count < 0) {
				count = 0;
			}
			if (count >= x.length) {
				final float[] nx = new float[count + 5];
				System.arraycopy(x, 0, nx, 0, x.length);
				x = nx;
				final float[] ny = new float[count + 5];
				System.arraycopy(y, 0, ny, 0, y.length);
				y = ny;
				final boolean[] nc = new boolean[count + 5];
				System.arraycopy(onCurve, 0, nc, 0, onCurve.length);
				onCurve = nc;
			}

			x[count] = gd.getXCoordinate(i) * 1000f / upm;
			y[count] = -(gd.getYCoordinate(i) * 1000f / upm);
			onCurve[count] = (gd.getFlags(i) & GlyfDescript.onCurve) != 0;
			if (scount <= 1) {
				sx[scount] = x[count];
				sy[scount] = y[count];
				sonCurve[scount] = onCurve[count];
				++scount;
			}
			++count;

			// End of contour
			final boolean end = gd.getEndPtOfContours(endIndex) == i;
			if (end) {
				if (count + scount > x.length) {
					// resize arrays if needed before appending start points
					final float[] nx = new float[count + scount];
					System.arraycopy(x, 0, nx, 0, count);
					x = nx;
					final float[] ny = new float[count + scount];
					System.arraycopy(y, 0, ny, 0, count);
					y = ny;
					final boolean[] nc = new boolean[count + scount];
					System.arraycopy(onCurve, 0, nc, 0, count);
					onCurve = nc;
				}
				for (int j = 0; j < scount; ++j) {
					x[count + j] = sx[j];
					y[count + j] = sy[j];
					onCurve[count + j] = sonCurve[j];
				}
			}

			// Re-implementing original logic precisely but with bounds checks
			if (end) {
				for (int j = 0; j < scount; ++j) {
					if (count + j >= x.length) {
						// resize
						final float[] nx = new float[count + scount + 5];
						System.arraycopy(x, 0, nx, 0, count);
						x = nx;
						final float[] ny = new float[count + scount + 5];
						System.arraycopy(y, 0, ny, 0, count);
						y = ny;
						final boolean[] nc = new boolean[count + scount + 5];
						System.arraycopy(onCurve, 0, nc, 0, count);
						onCurve = nc;
					}
					x[count + j] = sx[j];
					y[count + j] = sy[j];
					onCurve[count + j] = sonCurve[j];
				}
				scount = 0; // consumed
			}

			do {
				if (!end && count < 2) {
					break;
				}
				// 2 points
				if (onCurve[0] && onCurve[1]) {
					if (first) {
						path.moveTo(x[0], y[0]);
						first = false;
					}
					path.lineTo(x[1], y[1]);
				} else if (!onCurve[0] && !onCurve[1]) {
					if (first) {
						path.moveTo(midValue(x[0], x[1]), midValue(y[0], y[1]));
						first = false;
					} else {
						path.quadTo(x[0], y[0], midValue(x[0], x[1]), midValue(y[0], y[1]));
					}
				} else if (!onCurve[0] && onCurve[1]) {
					if (first) {
						path.moveTo(x[1], y[1]);
						first = false;
					} else {
						path.quadTo(x[0], y[0], x[1], y[1]);
					}
				} else {
					if (!end && count < 3) {
						break;
					}
					// 3 points
					if (first) {
						path.moveTo(x[0], y[0]);
						first = false;
					}
					if (onCurve[0] && !onCurve[1] && onCurve[2]) {
						path.quadTo(x[1], y[1], x[2], y[2]);
					} else if (onCurve[0] && !onCurve[1] && !onCurve[2]) {
						path.quadTo(x[1], y[1], midValue(x[1], x[2]), midValue(y[1], y[2]));
					} else {
						// All onCurve? or other combinations (not possible if valid TTF?)
						// Fallback or ignore for robustness
					}
					count -= 2;
					// 輪郭の末尾でcountは負になり得る(その場合はシフト不要)
					final int shift3 = Math.max(count, 0);
					System.arraycopy(x, 2, x, 0, shift3);
					System.arraycopy(y, 2, y, 0, shift3);
					System.arraycopy(onCurve, 2, onCurve, 0, shift3);
					continue;
				}
				--count;
				final int shift2 = Math.max(count, 0);
				System.arraycopy(x, 1, x, 0, shift2);
				System.arraycopy(y, 1, y, 0, shift2);
				System.arraycopy(onCurve, 1, onCurve, 0, shift2);
			} while (end && count > 0);

			if (end) {
				++endIndex;
				path.closePath();
				first = true;
			}
		}

		glyph = new Glyph(path, null);
		this.glyphs.set(ix, new SoftReference<>(glyph));
		return glyph;
	}

	private static float midValue(final float a, final float b) {
		return a + (b - a) / 2.0f;
	}
}
