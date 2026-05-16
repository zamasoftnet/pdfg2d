package net.zamasoft.pdfg2d.gc.text;

/**
 * A glyph handler that filters or wraps another glyph handler.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface FilterGlyphHandler extends GlyphHandler {
	/**
	 * Sets the downstream glyph handler that this filter delegates to.
	 *
	 * @param glyphHandler the glyph handler to delegate to
	 */
	public void setGlyphHandler(GlyphHandler glyphHandler);
}
