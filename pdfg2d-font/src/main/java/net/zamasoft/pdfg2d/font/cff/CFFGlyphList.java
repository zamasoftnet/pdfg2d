package net.zamasoft.pdfg2d.font.cff;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import net.zamasoft.pdfg2d.font.Glyph;
import net.zamasoft.pdfg2d.font.GlyphList;
import net.zamasoft.pdfg2d.font.table.HeadTable;
import net.zamasoft.pdfg2d.font.table.MaxpTable;

/**
 * {@link GlyphList} implementation for CFF (Compact Font Format) fonts.
 * Glyphs are decoded from Type 2 CharStrings on demand and cached with
 * {@link SoftReference soft references} so they can be reclaimed under
 * memory pressure.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CFFGlyphList implements GlyphList {

	/** 字形座標を揃える基準のem(TrueType側と同じ1000)。 */
	private static final double DEFAULT_UNITS_PER_EM = 1000.0;

	private final CFFTable cff;
	private final HeadTable head;
	private final AtomicReferenceArray<SoftReference<Glyph>> glyphs;

	/**
	 * Creates a new glyph list for the given CFF font data.
	 *
	 * @param cff  the CFF table containing the charstring data
	 * @param head the {@code head} table providing the units-per-em value
	 * @param maxp the {@code maxp} table providing the total glyph count
	 */
	public CFFGlyphList(final CFFTable cff, final HeadTable head, final MaxpTable maxp) {
		this.cff = cff;
		this.head = head;
		this.glyphs = new AtomicReferenceArray<>(maxp.getNumGlyphs());
	}

	/**
	 * Returns the glyph at the given index, decoding it from the CFF charstring
	 * data if it has not been cached yet.
	 *
	 * @param ix the glyph index (GID)
	 * @return the decoded {@link Glyph}, or {@code null} if the index is
	 *         out of range
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
		final short upm = this.head.getUnitsPerEm();
		glyph = normalize(this.cff.getGlyph(ix, upm), this.cff.getGlyphUnitsPerEm(upm));
		this.glyphs.set(ix, new SoftReference<>(glyph));
		return glyph;
	}

	/**
	 * charstringの座標を1000単位のemへ揃えます。
	 *
	 * <p>
	 * {@link net.zamasoft.pdfg2d.font.truetype.TrueTypeGlyphList}が
	 * {@code 1000/unitsPerEm}で正規化しているのに対し、CFF側は
	 * charstringの生の座標をそのまま返していました。CFFの既定em(1000)と
	 * {@code head}の{@code unitsPerEm}が食い違うフォント
	 * (Pretendard = 2048、FontMatrixなし)で、**字形だけが2.048倍**になり、
	 * アドバンスは正しいので文字が重なって版面からはみ出します
	 * (2026-09-01)。埋め込みPDFもこのパスから字形を再生成するので、
	 * ここで直すとPDF・画像・SVGのすべてが揃います。
	 * </p>
	 *
	 * @param glyph          復号したglyph
	 * @param glyphUnitsPerEm charstring座標系の1em
	 * @return 1000単位へ揃えたglyph(倍率が1なら引数のまま)
	 */
	private static Glyph normalize(final Glyph glyph, final double glyphUnitsPerEm) {
		if (glyph == null || glyph.path() == null || glyphUnitsPerEm <= 0
				|| glyphUnitsPerEm == DEFAULT_UNITS_PER_EM) {
			return glyph;
		}
		final double scale = DEFAULT_UNITS_PER_EM / glyphUnitsPerEm;
		final GeneralPath path = new GeneralPath(glyph.path());
		path.transform(AffineTransform.getScaleInstance(scale, scale));
		// charstringは元の座標系のままなので、拡縮したら整合しない。
		// 消費側(CFFGenerator)はpathから作り直せる
		return new Glyph(path, null);
	}
}
