package net.zamasoft.pdfg2d.pdf.font.util;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.g2d.util.G2DUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GC.TextMode;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.PDFGraphicsOutput;

/**
 * Font-related utilities.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PDFFontUtils {
	private static final Logger LOG = Logger.getLogger(PDFFontUtils.class.getName());
	private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

	private static final boolean JDK1_5;

	static {
		JDK1_5 = "1.5".equals(System.getProperty("java.specification.version"));
	}

	private PDFFontUtils() {
		// unused
	}

	/**
	 * Returns a fallback AWT font.
	 * 
	 * @param source the font source
	 * @return the AWT font
	 */
	public static Font toAwtFont(FontSource source) {
		Map<TextAttribute, String> atts = new HashMap<TextAttribute, String>();
		String fontName = source.getFontName();
		String awtName = null;
		if (!G2DUtils.isAvailable(fontName)) {
			String[] aliases = source.getAliases();
			for (int i = 0; i < aliases.length; ++i) {
				String alias = aliases[i];
				if (G2DUtils.isAvailable(alias)) {
					awtName = G2DUtils.toAwtFontName(alias);
					break;
				}
			}
			if (awtName == null) {
				awtName = fontName;
			}
		} else {
			awtName = G2DUtils.toAwtFontName(fontName);
		}
		atts.put(TextAttribute.FAMILY, awtName);
		return new Font(atts);
	}

	/**
	 * Draws CID text to a PDF graphics context.
	 * 
	 * @param out          the PDF graphics output
	 * @param text         the text to draw
	 * @param verticalFont true if using a vertical font
	 * @throws IOException if an I/O error occurs
	 */
	public static void drawCIDTo(PDFGraphicsOutput out, Text text, boolean verticalFont) throws IOException {
		int[] gids = text.getGlyphIds();
		int glyphCount = text.getGlyphCount();
		final net.zamasoft.pdfg2d.gc.text.GlyphAdvances xadvances = text.xAdvances();
		FontMetrics fm = text.getFontMetrics();
		out.startArray();
		int len = 0;
		int off = 0;
		double size = fm.getFontSize();
		double prevPlacement = 0;
		for (int i = 0; i < glyphCount; ++i) {
			double xadvance = xadvances == null ? 0 : xadvances.get(i);
			if (i > 0) {
				xadvance -= fm.getKerning(gids[i - 1], gids[i]);
				// palt等のfeature由来の詰めもkerningと同型のTJ調整で再現する
				// (ベースのペン移動は/W幅=素のグリフ幅から来るため)。行内の
				// 最終グリフの調整は後続が無く不要——次のrunは配置座標が
				// レイアウト(metrics=調整込み)から決まる
				xadvance += fm.getAdvanceAdjustment(gids[i - 1]);
			}
			// 字面の視覚シフト(palt xPlacement——増分⑤): ペンは進めないため、
			// グリフの前でシフトし、次の境界で戻す(前後の対のTJ調整)。
			// 最終グリフの戻しは不要(同上)
			final double placement = fm.getPlacementAdjustment(gids[i]);
			xadvance += placement - prevPlacement;
			prevPlacement = placement;
			if (xadvance != 0) {
				if (verticalFont) {
					xadvance = -xadvance;
				}
				if (len > 0) {
					out.writeBytes16(gids, off, len);
					off += len;
					len = 0;
				}
				double kerning = -xadvance * FontSource.DEFAULT_UNITS_PER_EM / size;
				out.writeReal(kerning);
			}
			++len;
		}
		if (len > 0) {
			out.writeBytes16(gids, off, len);
		}
		out.endArray();
		out.writeOperator("TJ");
	}

	/**
	 * Draws text using an AWT font.
	 * 
	 * @param gc         the graphics context
	 * @param fontSource the font source
	 * @param awtFont    the AWT font
	 * @param text       the text to draw
	 * @throws GraphicsException if a graphics error occurs
	 */
	public static void drawAwtFont(GC gc, FontSource fontSource, Font awtFont, Text text) throws GraphicsException {
		Direction direction = text.getFontStyle().getDirection();
		double fontSize = text.getFontStyle().getSize();
		Map<TextAttribute, Object> atts = new HashMap<TextAttribute, Object>();
		G2DUtils.setFontAttributes(atts, text.getFontStyle());
		awtFont = awtFont.deriveFont(atts);
		int glyphCount = text.getGlyphCount();
		int[] glyphIds = text.getGlyphIds();
		final byte[] clens = text.getClusterLengths();
		char[] chars = text.getChars();
		double letterSpacing = text.getLetterSpacing();
		final net.zamasoft.pdfg2d.gc.text.GlyphAdvances xadvances = text.xAdvances();
		FontMetrics fm = text.getFontMetrics();
		TextMode textMode = gc.getTextMode();

		if (direction == Direction.TB) {
			// 横書きフォントを縦組みの行へ横倒しにする
			gc.transform(FontUtils.createSidewaysTransform(fontSource, fontSize));
		}
		if (xadvances != null && xadvances.get(0) != 0) {
			gc.transform(AffineTransform.getTranslateInstance(xadvances.get(0), 0));
		}
		// Horizontal writing
		for (int i = 0, k = 0; i < glyphCount; ++i) {
			int gid = glyphIds[i];
			byte gclen = clens[i];
			try {
				GlyphVector gv;
				if (JDK1_5) {
					// Workaround for JDK 1.5 bug #6266084
					gv = awtFont.layoutGlyphVector(FRC, chars, k, gclen, Font.LAYOUT_LEFT_TO_RIGHT);
				} else {
					gv = awtFont.layoutGlyphVector(FRC, chars, k, k + gclen, Font.LAYOUT_LEFT_TO_RIGHT);
				}
				Shape s = gv.getOutline();
				switch (textMode) {
					case FILL:
						gc.fill(s);
						break;
					case STROKE:
						gc.draw(s);
						break;
					case FILL_STROKE:
						gc.fill(s);
						gc.draw(s);
						break;
					default:
						throw new IllegalStateException();
				}
			} catch (Exception e) {
				LOG.log(Level.WARNING,
						new String(chars) + "/k=" + k + "/gclen=" + gclen + "/clen=" + text.getCharCount(),
						e);
			}
			double dx = fm.getAdvance(gid) + letterSpacing;
			if (i + 1 < glyphCount) {
				dx -= fm.getKerning(gid, glyphIds[i + 1]);
				if (xadvances != null) {
					dx += xadvances.get(i + 1);
				}
			}
			gc.transform(AffineTransform.getTranslateInstance(dx, 0));
			k += gclen;
		}
	}
}
