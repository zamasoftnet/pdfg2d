package net.zamasoft.pdfg2d.gc.text;

/**
 * Processes a string to convert it to glyphs.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface TextShaper extends CharacterHandler {
	/**
	 * Sets the glyph handler that receives the shaped glyphs.
	 *
	 * @param glyphHandler the glyph handler to set
	 */
	public void setGlyphHandler(GlyphHandler glyphHandler);
}
