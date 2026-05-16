package net.zamasoft.pdfg2d.font;

/**
 * A list of glyphs in a font, indexed by glyph ID.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface GlyphList {

	/**
	 * Returns the glyph at the given index.
	 *
	 * @param i the glyph index (GID)
	 * @return the {@link Glyph} at the given index, or {@code null} if not found
	 */
	Glyph getGlyph(int i);
}

