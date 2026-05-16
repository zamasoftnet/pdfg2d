package net.zamasoft.pdfg2d.font;

import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * Manages font sources and lookups.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface FontSourceManager {
	/**
	 * Returns font sources matching the given font style.
	 * 
	 * @param fontStyle the font style to match, or null to return all fonts
	 * @return an array of matching font sources
	 */
	public FontSource[] lookup(FontStyle fontStyle);
}