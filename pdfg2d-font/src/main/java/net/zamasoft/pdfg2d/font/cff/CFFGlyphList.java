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

	/** Reference em for normalized glyph coordinates (1000, as on the TrueType side). */
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
	 * Normalizes charstring coordinates to a 1000-unit em.
	 *
	 * <p>
	 * While {@link net.zamasoft.pdfg2d.font.truetype.TrueTypeGlyphList} normalizes by
	 * {@code 1000/unitsPerEm}, the CFF path previously returned raw charstring coordinates.
	 * For fonts where the CFF default em (1000) differs from {@code head}'s {@code unitsPerEm}
	 * (Pretendard = 2048, no FontMatrix), **only glyphs were scaled by 2.048**.
	 * Advances were correct, so characters overlapped and extended beyond the type area
	 * (2026-09-01). Embedded PDFs also regenerate glyphs through this path,
	 * so fixing it here aligns PDF, image, and SVG output.
	 * </p>
	 *
	 * @param glyph          decoded glyph
	 * @param glyphUnitsPerEm one em in the charstring coordinate system
	 * @return glyph normalized to 1000 units (the argument unchanged if the scale is 1)
	 */
	private static Glyph normalize(final Glyph glyph, final double glyphUnitsPerEm) {
		if (glyph == null || glyph.path() == null || glyphUnitsPerEm <= 0
				|| glyphUnitsPerEm == DEFAULT_UNITS_PER_EM) {
			return glyph;
		}
		final double scale = DEFAULT_UNITS_PER_EM / glyphUnitsPerEm;
		final GeneralPath path = new GeneralPath(glyph.path());
		path.transform(AffineTransform.getScaleInstance(scale, scale));
		// The charstring retains its original coordinate system, so it becomes inconsistent after scaling.
		// The consumer (CFFGenerator) can regenerate it from the path.
		return new Glyph(path, null);
	}
}
