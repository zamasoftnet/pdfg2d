package net.zamasoft.pdfg2d.font.cff;

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
		glyph = this.cff.getGlyph(ix, upm);
		this.glyphs.set(ix, new SoftReference<>(glyph));
		return glyph;
	}
}
