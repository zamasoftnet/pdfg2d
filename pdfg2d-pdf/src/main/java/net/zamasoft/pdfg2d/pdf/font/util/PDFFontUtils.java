package net.zamasoft.pdfg2d.pdf.font.util;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
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
import net.zamasoft.pdfg2d.gc.font.FontStyle;
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
				// Reproduce spacing reductions from features such as palt with TJ adjustments like kerning
				// (the base pen movement comes from /W widths, i.e. unadjusted glyph widths). The final glyph
				// in a line needs no adjustment because nothing follows; the next run's position
				// comes from layout (metrics include adjustments).
				xadvance += fm.getAdvanceAdjustment(gids[i - 1]);
			}
			// Visual shift of glyph bounds (ink) (palt xPlacement, increment ⑤): the pen does not advance,
			// so shift before the glyph and restore at the next boundary (paired TJ adjustments before and after).
			// No restoration is needed for the final glyph (as above).
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
		final FontStyle fontStyle = text.getFontStyle();
		Map<TextAttribute, Object> atts = new HashMap<TextAttribute, Object>();
		G2DUtils.setFontAttributes(atts, fontStyle);
		// Bold made as the PDF's text renderer and FontUtils.drawText make it, the outline widened by a round-joined
		// stroke (2026-11-26). Only AWT's weight was asked for before, which emboldens a logical font but not an
		// installed font's regular face: Yu Gothic at 700 under an SVG filter came out regular. AWT is asked for the
		// regular weight when the font is not bold itself, as the PDF draws it: not emboldened twice, nor when
		// synthesis is off. The widened outline is filled (a G2DGC's stroke paint is not the fill paint, codex)
		if (fontSource.getWeight().w < 500) {
			atts.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_REGULAR);
		}
		awtFont = awtFont.deriveFont(atts);
		drawAwtGlyphs(gc, fontSource, awtFont, text, syntheticBoldWidth(fontStyle, fontSource, gc.getTextMode()));
	}

	/** Draws a glyph's outline in the text mode, filled widened by a stroke {@code bold} wide when that is above 0. */
	private static void drawGlyph(final GC gc, Shape s, final TextMode textMode, final double bold)
			throws GraphicsException {
		switch (textMode) {
		case FILL:
			if (bold > 0) {
				final Area area = new Area(s);
				area.add(new Area(new BasicStroke((float) bold, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
						.createStrokedShape(s)));
				s = area;
			}
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
	}

	/**
	 * The width of the stroke that makes a synthetic bold (as PDFTextRenderer and FontUtils.drawText take it), or 0
	 * when the font is bold itself, synthesis is off, or the text is not only filled.
	 */
	static double syntheticBoldWidth(final FontStyle fontStyle, final FontSource fontSource, final TextMode textMode) {
		if (textMode != TextMode.FILL || fontStyle.getWeight().w < 500 || fontSource.getWeight().w >= 500
				|| !fontStyle.getSynthesisWeight()) {
			return 0;
		}
		final double size = fontStyle.getSize();
		return switch (fontStyle.getWeight()) {
		case W_500 -> size / 28.0;
		case W_600 -> size / 24.0;
		case W_700 -> size / 20.0;
		case W_800 -> size / 16.0;
		case W_900 -> size / 12.0;
		default -> 0;
		};
	}

	private static void drawAwtGlyphs(GC gc, FontSource fontSource, Font awtFont, Text text, double bold) {
		final TextMode textMode = gc.getTextMode();
		Direction direction = text.getFontStyle().getDirection();
		double fontSize = text.getFontStyle().getSize();
		int glyphCount = text.getGlyphCount();
		int[] glyphIds = text.getGlyphIds();
		final byte[] clens = text.getClusterLengths();
		char[] chars = text.getChars();
		double letterSpacing = text.getLetterSpacing();
		final net.zamasoft.pdfg2d.gc.text.GlyphAdvances xadvances = text.xAdvances();
		FontMetrics fm = text.getFontMetrics();

		if (direction == Direction.TB && fontSource.getDirection() == Direction.TB) {
			drawAwtUpright(gc, awtFont, text, textMode, bold);
			return;
		}
		if (direction == Direction.TB) {
			// Turn a horizontal writing font sideways for a vertical writing line.
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
				drawGlyph(gc, gv.getOutline(), textMode, bold);
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

	/**
	 * Draws a vertical font's text with the horizontal AWT substitute (2026-11-26): each glyph upright, centred on the
	 * column and stacked down, as {@link FontUtils#drawText} draws a vertical font's own outlines. Before, the whole
	 * line was turned sideways like a horizontal font's, so text under an SVG filter (drawn to a raster) lay on its
	 * side. The substitute has only the horizontal forms, so punctuation takes its Unicode vertical presentation form
	 * where the substitute has one; otherwise a bracket or dash is turned a quarter clockwise and a comma or full
	 * stop moved to the upper right, where a vertical font has them (an exclamation or question mark stays as it is).
	 */
	private static void drawAwtUpright(GC gc, Font awtFont, Text text, TextMode textMode, double bold) {
		final double fontSize = text.getFontStyle().getSize();
		final int glyphCount = text.getGlyphCount();
		final int[] glyphIds = text.getGlyphIds();
		final byte[] clens = text.getClusterLengths();
		final char[] chars = text.getChars();
		final double letterSpacing = text.getLetterSpacing();
		final net.zamasoft.pdfg2d.gc.text.GlyphAdvances xadvances = text.xAdvances();
		final FontMetrics fm = text.getFontMetrics();
		final double ascent = FontSource.DEFAULT_VERTICAL_ORIGIN * fontSize / FontSource.DEFAULT_UNITS_PER_EM;
		// the column's left edge at x 0, the first glyph's top at y 0
		gc.transform(AffineTransform.getTranslateInstance(-fontSize / 2.0, 0));
		double y = xadvances != null ? xadvances.get(0) : 0;
		for (int i = 0, k = 0; i < glyphCount; ++i) {
			final int gid = glyphIds[i];
			final byte gclen = clens[i];
			if (i > 0) {
				y += fm.getAdvance(glyphIds[i - 1]) + letterSpacing - fm.getKerning(glyphIds[i - 1], gid);
				if (xadvances != null) {
					y += xadvances.get(i);
				}
			}
			try {
				char[] gchars = chars;
				int off = k;
				int turn = 0; // 1: a quarter clockwise, 2: to the upper right
				if (gclen == 1) {
					final char c = chars[k];
					final char form = verticalForm(c);
					if (form != 0 && awtFont.canDisplay(form)) {
						gchars = new char[] { form };
						off = 0;
					} else if (c == '、' || c == '。' || c == '，' || c == '．') {
						turn = 2;
					} else if (form != 0 && c != '！' && c != '？' || isTurnedInVertical(c)) {
						turn = 1;
					}
				}
				final GlyphVector gv = awtFont.layoutGlyphVector(FRC, gchars, off, off + gclen,
						Font.LAYOUT_LEFT_TO_RIGHT);
				final double advance = gv.getGlyphPosition(gv.getNumGlyphs()).getX();
				final AffineTransform at;
				if (turn == 1) {
					// the horizontal em box's centre to the column's centre, turned
					at = AffineTransform.getTranslateInstance(fontSize / 2.0, y + fontSize / 2.0);
					at.rotate(Math.PI / 2);
					at.translate(-advance / 2.0, ascent - fontSize / 2.0);
				} else {
					at = AffineTransform.getTranslateInstance((fontSize - advance) / 2.0, y + ascent);
					if (turn == 2) {
						at.preConcatenate(AffineTransform.getTranslateInstance(fontSize / 2.0, -fontSize / 2.0));
					}
				}
				drawGlyph(gc, at.createTransformedShape(gv.getOutline()), textMode, bold);
			} catch (Exception e) {
				LOG.log(Level.WARNING,
						new String(chars) + "/k=" + k + "/gclen=" + gclen + "/clen=" + text.getCharCount(), e);
			}
			k += gclen;
		}
	}

	/**
	 * The Unicode vertical presentation form (Vertical Forms and CJK Compatibility Forms) of a character, or 0.
	 */
	static char verticalForm(final char c) {
		return switch (c) {
		case '，' -> '︐';
		case '、' -> '︑';
		case '。' -> '︒';
		case '：' -> '︓';
		case '；' -> '︔';
		case '！' -> '︕';
		case '？' -> '︖';
		case '〖' -> '︗';
		case '〗' -> '︘';
		case '…' -> '︙';
		case '‥' -> '︰';
		case '—' -> '︱';
		case '–' -> '︲';
		case '＿' -> '︳';
		case '（' -> '︵';
		case '）' -> '︶';
		case '｛' -> '︷';
		case '｝' -> '︸';
		case '〔' -> '︹';
		case '〕' -> '︺';
		case '【' -> '︻';
		case '】' -> '︼';
		case '《' -> '︽';
		case '》' -> '︾';
		case '〈' -> '︿';
		case '〉' -> '﹀';
		case '「' -> '﹁';
		case '」' -> '﹂';
		case '『' -> '﹃';
		case '』' -> '﹄';
		case '［' -> '﹇';
		case '］' -> '﹈';
		default -> 0;
		};
	}

	/** Whether a character without a vertical presentation form is turned a quarter in vertical text. */
	static boolean isTurnedInVertical(final char c) {
		return switch (c) {
		case 'ー', '〜', '～', '‐', '―', '－', '＝', '＜', '＞', '〘',
				'〙', '〚', '〛', '｟', '｠' ->
			true;
		default -> false;
		};
	}
}
