package net.zamasoft.pdfg2d.gc.font;

import java.io.IOException;
import java.io.Serializable;

import net.zamasoft.pdfg2d.gc.text.TextShaper;

/**
 * Decomposes text into multiple glyphs.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface FontManager extends Serializable {
	/**
	 * Adds a font face.
	 * 
	 * @param face the font face to add
	 * @throws IOException if an I/O error occurs
	 */
	public void addFontFace(FontFace face) throws IOException;

	/**
	 * Forgets the font faces added so far for font selection, when the next document of an output begins
	 * (2026-10-08). A document's {@code @font-face} fonts belong to that document; text already drawn keeps its
	 * fonts.
	 */
	public default void clearFontFaces() {
		// No font faces to forget
	}

	/**
	 * Returns the font list metrics for the specified font style.
	 * 
	 * @param fontStyle the font style
	 * @return the font list metrics
	 */
	public FontListMetrics getFontListMetrics(FontStyle fontStyle);

	/**
	 * Returns a glypher for breaking text into glyphs.
	 * 
	 * @return the glypher
	 */
	public TextShaper getTextShaper();
}