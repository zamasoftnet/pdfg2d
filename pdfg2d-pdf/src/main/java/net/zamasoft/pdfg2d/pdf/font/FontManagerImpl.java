package net.zamasoft.pdfg2d.pdf.font;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.zamasoft.pdfg2d.font.DefaultFontStore;
import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontSourceManager;
import net.zamasoft.pdfg2d.font.FontStore;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;
import net.zamasoft.pdfg2d.pdf.font.cid.missing.MissingCIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.missing.SpaceCIDFontSource;

/**
 * Implementation of FontManager for PDF.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class FontManagerImpl implements FontManager, Closeable {
	private static final long serialVersionUID = 1L;

	protected final FontSourceManager globaldb;

	protected PDFFontSourceManager localdb = null;

	protected final FontStore fontStore;

	protected final Map<FontStyle, FontListMetrics> fontListMetricsCache = new HashMap<FontStyle, FontListMetrics>();

	/**
	 * Constructs a FontManagerImpl with the given font source manager and font store.
	 *
	 * @param fontdb    the global font source manager used for font lookup
	 * @param fontStore the font store used to cache loaded fonts
	 */
	public FontManagerImpl(FontSourceManager fontdb, FontStore fontStore) {
		assert fontdb != null;
		this.globaldb = fontdb;
		this.fontStore = fontStore;
	}

	/**
	 * Constructs a FontManagerImpl with the given font source manager and a default font store.
	 *
	 * @param fontdb the global font source manager used for font lookup
	 */
	public FontManagerImpl(FontSourceManager fontdb) {
		this(fontdb, new DefaultFontStore());
	}

	/**
	 * Closes this font manager and releases any resources held by the local font source manager.
	 */
	public void close() {
		if (this.localdb != null) {
			this.localdb.close();
		}
	}

	/**
	 * Registers a font face into the local font source manager.
	 * The local font source manager is created on first use and takes priority
	 * over the global one during font lookup.
	 *
	 * @param face the font face to register
	 * @throws IOException if an error occurs while loading the font face
	 */
	public void addFontFace(FontFace face) throws IOException {
		if (this.localdb == null) {
			this.localdb = new PDFFontSourceManager(true);
		}
		this.localdb.addFontFace(face);
	}

	/**
	 * Returns the {@link FontListMetrics} for the given font style, building and caching it if necessary.
	 * The list is assembled from locally registered fonts, globally registered fonts, a space font,
	 * and a missing-glyph fallback font, in that priority order.
	 *
	 * @param fontStyle the font style to look up
	 * @return the font list metrics for the given style
	 */
	public FontListMetrics getFontListMetrics(FontStyle fontStyle) {
		FontListMetrics flm = (FontListMetrics) this.fontListMetricsCache.get(fontStyle);
		if (flm != null) {
			return flm;
		}
		int count = 2;
		FontSource[] fonts1;
		if (this.localdb != null) {
			fonts1 = this.localdb.lookup(fontStyle);
			count += fonts1.length;
		} else {
			fonts1 = null;
		}
		FontSource[] fonts2 = this.globaldb.lookup(fontStyle);
		count += fonts2.length;
		FontMetrics[] fms = new FontMetrics[count];
		int j = 0;

		if (fonts1 != null) {
			for (int i = 0; i < fonts1.length; ++i) {
				fms[j++] = new FontMetricsImpl(this.fontStore, fonts1[i], fontStyle);
			}
		}
		for (int i = 0; i < fonts2.length; ++i) {
			fms[j++] = new FontMetricsImpl(this.fontStore, fonts2[i], fontStyle);
		}

		if (fontStyle.getDirection() == Direction.TB
				&& fontStyle.getTextOrientation() != FontStyle.TextOrientation.SIDEWAYS) {
			fms[fms.length - 2] = new FontMetricsImpl(this.fontStore, SpaceCIDFontSource.INSTANCES_TB, fontStyle);
			fms[fms.length - 1] = new FontMetricsImpl(this.fontStore, MissingCIDFontSource.INSTANCES_TB, fontStyle);
		} else {
			fms[fms.length - 2] = new FontMetricsImpl(this.fontStore, SpaceCIDFontSource.INSTANCES_LTR, fontStyle);
			fms[fms.length - 1] = new FontMetricsImpl(this.fontStore, MissingCIDFontSource.INSTANCES_LTR, fontStyle);
		}
		flm = new FontListMetrics(fms);
		this.fontListMetricsCache.put(fontStyle, flm);
		return flm;
	}

	/**
	 * Creates and returns a new {@link TextShaper} that handles Unicode character-to-glyph
	 * mapping, ligature formation, surrogate pair processing, and font fallback.
	 *
	 * @return a new text shaper instance
	 */
	public TextShaper getTextShaper() {
		return new CharacterHandler();
	}

	/**
	 * A {@link TextShaper} implementation that converts a stream of Unicode characters into
	 * a sequence of glyphs. It handles font selection, font fallback, ligature formation,
	 * surrogate pairs, zero-width characters (ZWS/ZWJ), and NBSP normalization.
	 */
	protected class CharacterHandler implements TextShaper {
		private GlyphHandler glyphHandler;

		private final char[] ch = new char[12];

		private int charOffset;

		/**
		 * The source offset of the first character of the pending glyph. The
		 * pending glyph is emitted lazily (when the next character cannot
		 * extend it), at which point {@link #charOffset} has already advanced
		 * to the following character — emitting with {@code charOffset} would
		 * shift every glyph's source mapping by one character.
		 */
		private int glyphOffset;

		private byte len;

		private int gid;

		private FontListMetrics fontListMetrics = null;

		private int fontBound = 0;

		private boolean zw = false; // ZWS(u200B), ZWJ(u200D) and subsequent chars keep the same font

		private FontStyle fontStyle;

		private FontMetricsImpl fontMetrics = null;

		private int pgid = -1;

		private boolean outOfRun = true;

		/**
		 * Constructs a new CharacterHandler.
		 */
		public CharacterHandler() {
			// ignore
		}

		/**
		 * Sets the glyph handler that receives the shaped glyph output.
		 *
		 * @param glyphHandler the glyph handler to receive output
		 */
		public void setGlyphHandler(GlyphHandler glyphHandler) {
			this.glyphHandler = glyphHandler;
		}

		/**
		 * Signals a font style change. Any pending glyph run is flushed before
		 * switching to the new font style.
		 *
		 * @param fontStyle the new font style to apply
		 */
		public void fontStyle(FontStyle fontStyle) {
			this.glyphBreak();
			this.fontListMetrics = null;
			this.fontStyle = fontStyle;
		}

		private void glyphBreak() {
			if (!this.outOfRun) {
				this.glyph();
				this.endRun();
				this.zw = false;
			}
		}

		/**
		 * Processes a sequence of characters, performing font selection, ligature formation,
		 * surrogate pair handling, and NBSP normalization before forwarding glyphs to the
		 * glyph handler.
		 *
		 * @param charOffset the character offset of the first character in the source text
		 * @param ch         the character array containing the text to process
		 * @param off        the offset within {@code ch} at which to start reading
		 * @param len        the number of characters to process
		 */
		public void characters(int charOffset, char[] ch, int off, int len) {
			if (this.fontListMetrics == null) {
				this.fontListMetrics = FontManagerImpl.this.getFontListMetrics(this.fontStyle);
				this.initFont();
			}

			for (int k = 0; k < len; ++k) {
				this.charOffset = charOffset + k;
				char c = ch[k + off];

				// Convert NBSP (\u00A0) to space
				int cc = c == '\u00A0' ? '\u0020' : c;

				// Handle surrogate pairs
				char ls = 0;
				if (Character.isHighSurrogate((char) cc)) {
					if (k + 1 < len && Character.isLowSurrogate(ch[k + off + 1])) {
						++k;
						cc = Character.toCodePoint((char) cc, ls = ch[k + off]);
					}
				}

				// Create text run range
				if (this.fontMetrics.canDisplay(cc)) {
					if (cc >= 0x200B && cc <= 0x200D) {
						this.zw = true;
					}
				} else {
					this.glyphBreak();
					this.initFont();
				}
				if (!this.zw) {
					// Switch to a higher-priority font if available
					for (int j = 0; j < this.fontBound; ++j) {
						FontMetricsImpl metrics = (FontMetricsImpl) this.fontListMetrics.getFontMetrics(j);
						if (metrics.canDisplay(cc)) {
							this.glyphBreak();
							this.fontMetrics = (FontMetricsImpl) this.fontListMetrics.getFontMetrics(j);
							this.fontBound = j;
							break;
						}
					}
				}

				// Process as normal character
				Font font = this.fontMetrics.getFont();
				int gid = font.toGID(cc, this.fontStyle.getFeatures());

				if (this.outOfRun) {
					this.outOfRun = false;
					this.gid = -1;
					this.pgid = -1;
					this.len = 0;
					this.glyphHandler.startTextRun(this.charOffset, this.fontStyle, this.fontMetrics);
				}

				// Check for ligature
				int lgid = this.len >= this.ch.length ? -1 : this.fontMetrics.getLigature(this.gid, cc);
				if (lgid != -1) {
					// Can form ligature
					this.gid = this.pgid;
					gid = lgid;
					this.ch[this.len] = c;
					++this.len;
					if (ls != 0) {
						this.ch[this.len] = ls;
						++this.len;
					}
				} else {
					// Cannot form ligature
					if (this.gid != -1) {
						this.glyph();
					}
					this.glyphOffset = this.charOffset;
					this.ch[0] = c;
					this.len = 1;
					if (ls != 0) {
						this.ch[1] = ls;
						this.len = 2;
					}
					this.zw = (cc >= 0x200B && cc <= 0x200D);
				}

				this.pgid = this.gid;
				this.gid = gid;
			}
		}

		/**
		 * Processes a text control event. Any pending glyph run is flushed before
		 * forwarding the control to the glyph handler.
		 *
		 * @param control the text control event to forward
		 */
		public void control(TextControl control) {
			this.glyphBreak();
			this.glyphHandler.control(control);
		}

		private void glyph() {
			this.glyphHandler.glyph(this.glyphOffset, this.ch, 0, this.len, this.gid);
		}

		private void endRun() {
			assert this.outOfRun == false;
			this.glyphHandler.endTextRun();
			this.outOfRun = true;
		}

		private void initFont() {
			this.fontBound = this.fontListMetrics.getLength();
			this.fontMetrics = (FontMetricsImpl) this.fontListMetrics.getFontMetrics(this.fontBound - 1);
			this.zw = false;
		}

		/**
		 * Flushes any pending glyph run and forwards the flush signal to the glyph handler.
		 */
		public void flush() {
			this.glyphBreak();
			this.glyphHandler.flush();
		}

		/**
		 * Flushes any pending glyph run and closes the glyph handler, releasing all resources.
		 */
		public void close() {
			this.glyphBreak();
			this.glyphHandler.close();
		}
	}
}
