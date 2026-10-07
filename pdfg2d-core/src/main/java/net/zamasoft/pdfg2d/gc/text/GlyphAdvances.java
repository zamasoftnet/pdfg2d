package net.zamasoft.pdfg2d.gc.text;

/**
 * Read-only view of per-glyph advance adjustments (2026-08-01, 90-point plan
 * increment 12).
 *
 * <p>
 * Previously, renderers and measurement code received raw {@code double[]} arrays,
 * allowing modification from anywhere (a source of bugs that applied Japanese text
 * spacing reduction and justification twice, as learned from the B5c-2 series).
 * Using this type for reads and restricting writes to meaningful operations such as
 * {@link TextImpl#addXAdvance(int, double)} enforces the invariants through types:
 * length equals glyph count, and only the layout layer makes changes.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public interface GlyphAdvances {
	/** Returns the glyph count (the logical length of the adjustment array). */
	int size();

	/**
	 * Returns the advance adjustment for a glyph.
	 *
	 * @param glyphIndex glyph position (zero-based)
	 * @return adjustment (negative for spacing reduction)
	 */
	double get(int glyphIndex);
}
