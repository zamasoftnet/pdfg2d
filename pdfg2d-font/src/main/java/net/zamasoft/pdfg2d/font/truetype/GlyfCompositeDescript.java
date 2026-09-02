package net.zamasoft.pdfg2d.font.truetype;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.font.table.GlyfTable;
import net.zamasoft.pdfg2d.font.table.Program;

/**
 * TrueType glyph description for composite glyphs.  A composite glyph is
 * assembled from one or more referenced component glyphs, each optionally
 * transformed by a 2×2 affine matrix and/or translation.  Component data is
 * stored as a list of {@link GlyfCompositeComp} records read from the
 * {@code glyf} table.
 *
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class GlyfCompositeDescript extends GlyfDescript {

	/** 解けなかった成分の点数・輪郭数。 */
	private static final int[] EMPTY_SIZE = { 0, 0 };

	private final List<GlyfCompositeComp> components;

	/**
	 * 全成分を合わせた点と輪郭の数です。
	 *
	 * <p>
	 * 読み取りの途中で数え上げた値をそのまま持つ(2026-09-01)。以前は
	 * {@link #getPointCount()}が最後の成分を<b>解き直して</b>その点数を足していた。
	 * 成分がそれ自体合成グリフだと部分木を丸ごと読み直すので、小片を大量に
	 * 積む書体(点字体・ピクセル書体)で1グリフに何分もかかっていた。
	 * </p>
	 */
	private final int pointCount, contourCount;

	/**
	 * 成分のグリフ番号→解いた記述(2026-09-02)。読み取りのときに1度だけ解き、
	 * 点や輪郭を引くたびに{@code parentTable.getDescription}(ファイルの
	 * seek+パース)へ戻らない。以前は1点ごとに全成分を解き直していたので、
	 * 合成の入れ子が深い書体で1グリフに長い時間がかかっていた。
	 * 解けなかった成分は{@code null}のまま覚える。
	 */
	private final java.util.Map<Integer, GlyfDescript> resolved;

	private GlyfCompositeDescript(final GlyfTable parentTable, final short xMin, final short yMin, final short xMax,
			final short yMax, final short[] instructions, final List<GlyfCompositeComp> components,
			final int pointCount, final int contourCount, final java.util.Map<Integer, GlyfDescript> resolved) {
		super(parentTable, -1, xMin, yMin, xMax, yMax, instructions);
		this.components = components;
		this.pointCount = pointCount;
		this.contourCount = contourCount;
		this.resolved = resolved;
	}

	/** 成分の記述。読み取り時に解いたものを返し、無ければ表へ問い合わせる。 */
	private GlyfDescript descript(final GlyfCompositeComp c) {
		final Integer gid = c.getGlyphIndex();
		if (this.resolved.containsKey(gid)) {
			return this.resolved.get(gid);
		}
		return this.parentTable.getDescription(gid);
	}

	/**
	 * Reads a composite glyph description from the current position in the given
	 * random-access file.
	 *
	 * @param parentTable the {@link GlyfTable} that owns this description
	 * @param raf         the file to read from, positioned at the start of the
	 *                    bounding-box data
	 * @return the parsed {@link GlyfCompositeDescript}
	 * @throws IOException if the data cannot be read or is malformed
	 */
	public static GlyfCompositeDescript read(final GlyfTable parentTable, final RandomAccessFile raf)
			throws IOException {
		final short xMin = (short) (raf.read() << 8 | raf.read());
		final short yMin = (short) (raf.read() << 8 | raf.read());
		final short xMax = (short) (raf.read() << 8 | raf.read());
		final short yMax = (short) (raf.read() << 8 | raf.read());

		// Get all of the composite components
		final List<GlyfCompositeComp> components = new ArrayList<>();
		GlyfCompositeComp comp;
		int firstIndex = 0;
		int firstContour = 0;
		// 同じ成分を何度も指す書体(小片を積むピクセル書体・点字体)は、
		// 同じグリフを何百回も読み直していた。1グリフを読む間だけ覚える
		// (2026-09-01。Handjetは1グリフに2.2秒かかっていた)
		final java.util.Map<Integer, int[]> counts = new java.util.HashMap<>();
		final java.util.Map<Integer, GlyfDescript> resolved = new java.util.HashMap<>();
		do {
			comp = GlyfCompositeComp.read(firstIndex, firstContour, raf);

			final long off = raf.getFilePointer();
			int[] size = counts.get(comp.getGlyphIndex());
			if (size == null) {
				final GlyfDescript desc = parentTable.getDescription(comp.getGlyphIndex());
				// 解けない成分(字形の無いグリフ、または自分へ戻る循環——
				// GlyfTableが読み取り中の番号を覚えて切っている)は0として数える
				size = desc == null ? EMPTY_SIZE : new int[] { desc.getPointCount(), desc.getContourCount() };
				counts.put(comp.getGlyphIndex(), size);
				resolved.put(comp.getGlyphIndex(), desc);
			}
			components.add(comp);
			firstIndex += size[0];
			firstContour += size[1];
			// **読み位置を戻すのは問い合わせを終えてから**(2026-09-01)。
			// 成分がそれ自体合成グリフのとき、getPointCount/getContourCountは
			// 中でgetDescriptionを呼んでrafを動かす。解決の直後だけ戻していたので、
			// 次の成分をずれた位置から読み、flagsとglyphIndexが0xFFFFになっていた——
			// 範囲外のグリフ番号、EOF、MORE_COMPONENTSが立ちっぱなしの無限ループ、
			// そしてStackOverflowError。本番のフォント一覧が500になった原因
			raf.seek(off);
		} while ((comp.getFlags() & GlyfCompositeComp.MORE_COMPONENTS) != 0);

		// Are there hinting instructions to read?
		short[] instructions = null;
		if ((comp.getFlags() & GlyfCompositeComp.WE_HAVE_INSTRUCTIONS) != 0) {
			instructions = Program.readInstructions(raf, (raf.read() << 8 | raf.read()));
		}

		return new GlyfCompositeDescript(parentTable, xMin, yMin, xMax, yMax, instructions, components, firstIndex,
				firstContour, resolved);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getEndPtOfContours(final int i) {
		final GlyfCompositeComp c = getCompositeCompEndPt(i);
		if (c != null) {
			final GlyfDescript gd = this.descript(c);
			return gd.getEndPtOfContours(i - c.getFirstContour()) + c.getFirstIndex();
		}
		return 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getFlags(final int i) {
		final GlyfCompositeComp c = getCompositeComp(i);
		if (c != null) {
			final GlyfDescript gd = this.descript(c);
			return gd.getFlags(i - c.getFirstIndex());
		}
		return 0;
	}

	/**
	 * {@inheritDoc}
	 * The coordinate is transformed by the component's affine matrix and
	 * translation before being returned.
	 */
	@Override
	public short getXCoordinate(final int i) {
		final GlyfCompositeComp c = getCompositeComp(i);
		if (c != null) {
			final GlyfDescript gd = this.descript(c);
			final int n = i - c.getFirstIndex();
			final int x = gd.getXCoordinate(n);
			final int y = gd.getYCoordinate(n);
			short x1 = (short) c.scaleX(x, y);
			x1 += c.getXTranslate();
			return x1;
		}
		return 0;
	}

	/**
	 * {@inheritDoc}
	 * The coordinate is transformed by the component's affine matrix and
	 * translation before being returned.
	 */
	@Override
	public short getYCoordinate(final int i) {
		final GlyfCompositeComp c = getCompositeComp(i);
		if (c != null) {
			final GlyfDescript gd = this.descript(c);
			final int n = i - c.getFirstIndex();
			final int x = gd.getXCoordinate(n);
			final int y = gd.getYCoordinate(n);
			short y1 = (short) c.scaleY(x, y);
			y1 += c.getYTranslate();
			return y1;
		}
		return 0;
	}

	/**
	 * Always returns {@code true} since this is a composite glyph.
	 *
	 * @return {@code true}
	 */
	@Override
	public boolean isComposite() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 * Calculated as the first-index offset of the last component plus that
	 * component's own point count.
	 */
	@Override
	public int getPointCount() {
		return this.pointCount;
	}

	/**
	 * {@inheritDoc}
	 * Calculated as the first-contour offset of the last component plus that
	 * component's own contour count.
	 */
	@Override
	public int getContourCount() {
		return this.contourCount;
	}

	/**
	 * Returns the first-point index of the {@code i}-th component.
	 *
	 * @param i the zero-based component index
	 * @return the first-point index of the specified component
	 */
	public int getComponentIndex(final int i) {
		return this.components.get(i).getFirstIndex();
	}

	/**
	 * Returns the number of component glyphs that make up this composite glyph.
	 *
	 * @return the component count
	 */
	public int getComponentCount() {
		return this.components.size();
	}

	/**
	 * Returns the component that contains the point at the given absolute index,
	 * or {@code null} if no component owns that index.
	 *
	 * @param i the absolute point index
	 * @return the owning {@link GlyfCompositeComp}, or {@code null}
	 */
	protected GlyfCompositeComp getCompositeComp(final int i) {
		for (int n = 0; n < this.components.size(); n++) {
			final GlyfCompositeComp c = this.components.get(n);
			final GlyfDescript gd = this.descript(c);
			// 解けない成分は点を持たないので、どの番号も含まない
			// (字形の無いグリフを指す成分でここが落ちていた——2026-09-01)
			if (gd == null) {
				continue;
			}
			if (c.getFirstIndex() <= i && i < (c.getFirstIndex() + gd.getPointCount())) {
				return c;
			}
		}
		return null;
	}

	protected GlyfCompositeComp getCompositeCompEndPt(final int i) {
		for (int j = 0; j < this.components.size(); j++) {
			final GlyfCompositeComp c = this.components.get(j);
			final GlyfDescript gd = this.descript(c);
			// 同上
			if (gd == null) {
				continue;
			}
			if (c.getFirstContour() <= i && i < (c.getFirstContour() + gd.getContourCount())) {
				return c;
			}
		}
		return null;
	}
}
