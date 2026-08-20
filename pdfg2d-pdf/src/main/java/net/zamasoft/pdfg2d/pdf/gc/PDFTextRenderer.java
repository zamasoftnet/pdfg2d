package net.zamasoft.pdfg2d.pdf.gc;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.font.DrawableFont;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ImageFont;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSource;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSource.Type;
import net.zamasoft.pdfg2d.gc.GC.TextMode;

/**
 * Emits the PDF text operators for {@link PDFGC#drawText}: font selection and
 * embedding checks, outline/image font fallbacks, horizontal and vertical
 * writing (including the 90-degree rotated fallback and per-glyph metrics),
 * synthetic italic/bold, letter spacing and text rendering modes.
 * <p>
 * Extracted from {@code PDFGC} to keep the graphics-state machinery and the
 * text pipeline separately readable; it operates on the GC's package-visible
 * state and is not part of the public API.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
final class PDFTextRenderer {

	private static final Logger LOG = Logger.getLogger(PDFTextRenderer.class.getName());

	private PDFTextRenderer() {
		// static use only
	}

	/**
	 * Draws the glyphs of the given text run at the specified position.
	 *
	 * @param gc   the graphics context holding the target output and state
	 * @param text the shaped text run
	 * @param x    the baseline start X
	 * @param y    the baseline start Y
	 * @throws GraphicsException if drawing fails
	 */
	static void drawText(final PDFGC gc, final Text text, final double x, final double y) throws GraphicsException {
		final var font = ((FontMetricsImpl) text.getFontMetrics()).getFont();
		final var fpl = text.getFontStyle().getPolicy();
		boolean outline = false;
		LOOP: for (var i = 0; i < fpl.getLength(); ++i) {
			switch (fpl.get(i)) {
				case EMBEDDED:
				case CID_IDENTITY:
					break LOOP;
				case OUTLINES:
					outline = true;
					break LOOP;
				default:
					break;
			}
		}
		// Color fonts (COLR/CPAL) are drawn as stacked filled outlines, so
		// route a run that contains any color glyph through the outline path.
		boolean colorGlyphs = false;
		if (font instanceof net.zamasoft.pdfg2d.font.ColorGlyphFont cgf) {
			final var glyphIds = text.getGlyphIds();
			for (var i = 0; i < text.getGlyphCount(); ++i) {
				if (cgf.isColorGlyph(glyphIds[i])) {
					colorGlyphs = true;
					break;
				}
			}
		}
		if (outline || colorGlyphs || font instanceof ImageFont) {
			if (font instanceof DrawableFont df) {
				if (font instanceof ShapedFont sf) {
					final var glyphCount = text.getGlyphCount();
					final var glyphIds = text.getGlyphIds();
					boolean hasShape = false;
					for (var i = 0; i < glyphCount; ++i) {
						final var gid = glyphIds[i];
						final var shape = sf.getShapeByGID(gid);
						if (shape != null && !shape.getPathIterator(null).isDone()) {
							hasShape = true;
							break;
						}
					}
					if (!hasShape) {
						// No characters to draw
						return;
					}
				}
				try (final var gcState = gc.begin()) {
					gc.transform(AffineTransform.getTranslateInstance(x, y));
					FontUtils.drawText(gc, df, text);
				}
				return;
			}
		}

		assert text.getCharCount() > 0;
		try {
			gc.applyStates();
			if (gc.textMode != gc.xtextMode) {
				gc.xtextMode = gc.textMode;
				gc.out.writeInt(gc.textMode.code);
				gc.out.writeOperator("Tr");
			}

			FontMetricsImpl fm = (FontMetricsImpl) text.getFontMetrics();
			PDFFontSource source = (PDFFontSource) fm.getFontSource();
			if (gc.requireEmbeddedFonts) {
				Type type = source.getType();
				if (type != Type.EMBEDDED && type != Type.MISSING) {
					throw new IllegalStateException("Only embedded fonts can be used in PDF/A, PDF/X or PDF/UA.");
				}
			}
			FontStyle fontStyle = text.getFontStyle();

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("drawText: fontSource=" + source + " text=" + text);
			}

			boolean localContext = false;
			double size = fontStyle.getSize();
			var drawX = x;
			var drawY = y;

			double enlargement;
			final var weight = fontStyle.getWeight();
			if (gc.textMode == TextMode.FILL && weight.w >= 500 && source.getWeight().w < 500
					&& fontStyle.getSynthesisWeight()) {
				// Simulate bold manually
				enlargement = switch (weight) {
					case W_500 -> size / 28.0;
					case W_600 -> size / 24.0;
					case W_700 -> size / 20.0;
					case W_800 -> size / 16.0;
					case W_900 -> size / 12.0;
					default -> throw new IllegalStateException("Unexpected weight: " + weight);
				};
				if (enlargement > 0 && gc.fillPaint.getPaintType() == Paint.Type.COLOR && gc.fillAlpha == 1) {
					gc.q();
					localContext = true;
					gc.out.writeReal(enlargement);
					gc.out.writeOperator("w");
					gc.out.writeInt(TextMode.FILL_STROKE.code);
					gc.out.writeOperator("Tr");
					if (!gc.fillPaint.equals(gc.strokePaint)) {
						if (gc.xstrokePaint != null && gc.xstrokePaint.getPaintType() != Paint.Type.COLOR) {
							gc.out.writeName("DeviceRGB");
							gc.out.writeOperator("CS");
						}
						gc.out.writeStrokeColor((Color) gc.fillPaint);
					}
				}
			} else {
				enlargement = 0;
			}

			final var direction = fontStyle.getDirection();
			AffineTransform rotate = null;
			double center = 0;
			boolean verticalFont = false;
			switch (direction) {
				case LTR, RTL -> {
					// Horizontal. Known limitation: RTL runs are emitted in
					// logical order without bidi reordering or glyph mirroring;
					// callers must pass text in visual order for RTL scripts.
				}
				case TB -> {
					// Vertical
					if (source.getDirection() == direction) {
						// Vertical typesetting
						verticalFont = true;
					} else {
						// 90-degree rotated horizontal
						if (!localContext) {
							gc.q();
							localContext = true;
						}
						rotate = AffineTransform.getRotateInstance(Math.PI / 2, drawX, drawY);
						gc.out.writeTransform(rotate);
						gc.out.writeOperator("cm");
						final var bbox = source.getBBox();
						center = ((bbox.lly() + bbox.ury()) * size / FontSource.DEFAULT_UNITS_PER_EM) / 2.0;
						drawY += center;
					}
				}
				default -> throw new IllegalStateException("Unexpected direction: " + direction);
			}

			// Begin text
			gc.out.writeOperator("BT");

			// Italic
			final var style = fontStyle.getStyle();
			if (style != Style.NORMAL && !source.isItalic() && fontStyle.getSynthesisStyle()) {
				// Simulate italic manually
				if (verticalFont) {
					// Vertical italic
					gc.out.writeReal(1);
					gc.out.writeReal(-0.25);
					gc.out.writeReal(0);
					gc.out.writeReal(1);
					gc.out.writePosition(drawX, drawY);
					gc.out.writeOperator("Tm");
				} else {
					// Horizontal italic
					gc.out.writeReal(1);
					gc.out.writeReal(0);
					gc.out.writeReal(0.25);
					gc.out.writeReal(1);
					gc.out.writePosition(drawX, drawY);
					gc.out.writeOperator("Tm");
				}
			} else {
				gc.out.writePosition(drawX, drawY);
				gc.out.writeOperator("Td");
			}

			// Font name and size
			String name = ((PDFFont) font).getName();
			gc.out.useResource("Font", name);
			gc.out.writeName(name);
			gc.out.writeReal(size);
			gc.out.writeOperator("Tf");

			// Letter spacing
			double letterSpacing = text.getLetterSpacing();
			// Use negative value for vertical writing (PDF 1.3 spec 8.7.1.1)
			if (verticalFont) {
				letterSpacing = -letterSpacing;
			}
			if (!gc.out.equals(letterSpacing, gc.xletterSpacing)) {
				gc.out.writeReal(letterSpacing);
				gc.out.writeOperator("Tc");
				if (!localContext) {
					gc.xletterSpacing = letterSpacing;
				}
			}

			// Draw
			font.drawTo(gc, text);

			// End text
			gc.out.writeOperator("ET");

			if (enlargement > 0 && gc.fillPaint.getPaintType() == Paint.Type.COLOR && gc.fillAlpha == 1) {
				// End bold simulation
				gc.out.writeInt(TextMode.FILL.code);
				gc.out.writeOperator("Tr");
				if (!gc.fillPaint.equals(gc.strokePaint)) {
					if (gc.xfillPaint != null && gc.xfillPaint.getPaintType() != Paint.Type.COLOR) {
						gc.out.writeName("DeviceRGB");
						gc.out.writeOperator("CS");
					}
					gc.out.writeStrokeColor((Color) gc.strokePaint);
				}
			}

			if (localContext) {
				gc.Q();
			}
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		}
}
