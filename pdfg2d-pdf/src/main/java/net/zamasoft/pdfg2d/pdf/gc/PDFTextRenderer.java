package net.zamasoft.pdfg2d.pdf.gc;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.font.DrawableFont;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.ImageFont;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
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

	/** Text rendering mode 3: neither fill nor stroke (the text stays extractable). */
	private static final int INVISIBLE_MODE = 3;

	/** Text rendering mode 7: add the glyphs to the clip. */
	private static final int CLIP_MODE = 7;

	private PDFTextRenderer() {
		// static use only
	}

	/**
	 * Whether text would paint nothing because what it paints has alpha 0, in a PDF version that cannot express
	 * the alpha: there the text would be painted opaque, so it is written invisible instead (2026-10-09; until then
	 * {@code color: transparent} text came out black in PDF/A-1b, PDF/X-1a and PDF 1.3).
	 */
	private static boolean invisible(final PDFGC gc) {
		if (gc.pdfVersion.allowsTransparency()) {
			return false;
		}
		final boolean fills = gc.textMode != TextMode.STROKE;
		final boolean strokes = gc.textMode != TextMode.FILL;
		return (!fills || gc.fillAlpha <= 0) && (!strokes || gc.strokeAlpha <= 0);
	}

	/**
	 * Whether the run is drawn as glyph outlines (paths) rather than shown as PDF text: fonts under the outline
	 * policy, runs with color glyphs, and image fonts, when the font can draw itself.
	 */
	static boolean drawsAsOutlines(final Text text) {
		final var font = ((FontMetricsImpl) text.getFontMetrics()).getFont();
		if (!(font instanceof DrawableFont)) {
			return false;
		}
		if (font instanceof ImageFont) {
			return true;
		}
		final var fpl = text.getFontStyle().getPolicy();
		LOOP: for (var i = 0; i < fpl.getLength(); ++i) {
			switch (fpl.get(i)) {
				case EMBEDDED:
				case CID_IDENTITY:
					break LOOP;
				case OUTLINES:
					return true;
				default:
					break;
			}
		}
		// Color fonts (COLR/CPAL) are drawn as stacked filled outlines, so
		// route a run that contains any color glyph through the outline path.
		if (font instanceof net.zamasoft.pdfg2d.font.ColorGlyphFont cgf) {
			final var glyphIds = text.getGlyphIds();
			for (var i = 0; i < text.getGlyphCount(); ++i) {
				if (cgf.isColorGlyph(glyphIds[i])) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Shows the runs in one text object in text rendering mode 7, which narrows the clip to their glyphs at
	 * {@code ET}; for {@link PDFGC#clipToText}. One object holds them all because every {@code ET} intersects the
	 * clip with its own glyphs. A text object takes no {@code cm}, so each run's placement goes into its text matrix,
	 * together with the sideways rotation and the synthetic italic that {@link #drawText} writes. Synthetic bold is
	 * not applied: the clip of a text rendering mode is the glyph fill without the stroke.
	 *
	 * <p>
	 * The caller has saved the graphics state, so the clip ends with it. The text state that the graphics context
	 * tracks (rendering mode and character spacing) is written back after the object.
	 * </p>
	 */
	static void clipText(final PDFGC gc, final java.util.List<net.zamasoft.pdfg2d.gc.text.TextClip.Run> runs)
			throws GraphicsException {
		try {
			final var out = gc.out;
			out.writeOperator("BT");
			out.writeInt(CLIP_MODE);
			out.writeOperator("Tr");
			double letterSpacing = gc.xletterSpacing;
			for (final var run : runs) {
				final var text = run.text();
				final var metrics = (FontMetricsImpl) text.getFontMetrics();
				final var font = metrics.getFont();
				final var source = (PDFFontSource) metrics.getFontSource();
				if (gc.requireEmbeddedFonts) {
					final Type type = source.getType();
					if (type != Type.EMBEDDED && type != Type.MISSING) {
						throw new IllegalStateException("Only embedded fonts can be used in PDF/A, PDF/X or PDF/UA.");
					}
				}
				final FontStyle fontStyle = text.getFontStyle();
				final double size = fontStyle.getSize();
				final AffineTransform placement = run.transform();
				boolean verticalFont = false;
				if (fontStyle.getDirection() == FontStyle.Direction.TB) {
					if (source.getDirection() == FontStyle.Direction.TB) {
						verticalFont = true;
					} else {
						// 90-degree rotated horizontal
						placement.concatenate(FontUtils.createSidewaysTransform(source, size));
					}
				}
				// Text space to PDF user space: the y-flip of the page, the placement, and the glyph space's y-up
				final AffineTransform tm = new AffineTransform(1, 0, 0, -1, 0, out.getHeight());
				tm.concatenate(placement);
				tm.concatenate(new AffineTransform(1, 0, 0, -1, 0, 0));
				if (fontStyle.getStyle() != Style.NORMAL && !source.isItalic() && fontStyle.getSynthesisStyle()) {
					tm.concatenate(verticalFont ? new AffineTransform(1, -0.25, 0, 1, 0, 0)
							: new AffineTransform(1, 0, 0.25, 1, 0, 0));
				}
				out.writeRealCoefficient(tm.getScaleX());
				out.writeRealCoefficient(tm.getShearY());
				out.writeRealCoefficient(tm.getShearX());
				out.writeRealCoefficient(tm.getScaleY());
				out.writeRealExact(tm.getTranslateX());
				out.writeRealExact(tm.getTranslateY());
				out.writeOperator("Tm");
				final String name = ((PDFFont) font).getName();
				out.useResource("Font", name);
				out.writeName(name);
				out.writeReal(size);
				out.writeOperator("Tf");
				// Use negative value for vertical writing (PDF 1.3 spec 8.7.1.1)
				final double spacing = verticalFont ? -text.getLetterSpacing() : text.getLetterSpacing();
				if (!out.equals(spacing, letterSpacing)) {
					out.writeReal(spacing);
					out.writeOperator("Tc");
					letterSpacing = spacing;
				}
				font.drawTo(gc, text);
			}
			out.writeOperator("ET");
			out.writeInt(gc.xtextMode.code);
			out.writeOperator("Tr");
			if (!out.equals(letterSpacing, gc.xletterSpacing)) {
				out.writeReal(gc.xletterSpacing);
				out.writeOperator("Tc");
			}
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
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
		final boolean invisible = invisible(gc);
		if (drawsAsOutlines(text)) {
			if (invisible) {
				// Paths have no invisible mode and nothing to extract: leave them out
				return;
			}
			final DrawableFont df = (DrawableFont) font;
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
			double xlineWidth = 0;
			GC.LineJoin xlineJoin = null;
			double[] xlinePattern = null;
			Paint xstrokePaint = null;
			float xstrokeAlpha = 1;
			final var weight = fontStyle.getWeight();
			if (!invisible && gc.textMode == TextMode.FILL && weight.w >= 500 && source.getWeight().w < 500
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
				if (enlargement > 0) {
					// Route the stroke state through the regular applyStates
					// machinery so that gradient/pattern fills (Pattern CS +
					// SCN) and translucent fills (ExtGState CA) are handled
					// exactly like ordinary stroked shapes, instead of
					// bailing out of the bold simulation as before. A round
					// join makes fill+stroke equal a uniform dilation; a
					// solid pattern guards against an inherited dash.
					xlineWidth = gc.getLineWidth();
					xlineJoin = gc.getLineJoin();
					xlinePattern = gc.getLinePattern();
					xstrokePaint = gc.getStrokePaint();
					xstrokeAlpha = gc.getStrokeAlpha();
					gc.setLineWidth(enlargement);
					gc.setLineJoin(GC.LineJoin.ROUND);
					gc.setLinePattern(GC.STROKE_SOLID);
					gc.setStrokePaint(gc.getFillPaint());
					gc.setStrokeAlpha(gc.getFillAlpha());
					gc.applyStates();
				}
			} else {
				enlargement = 0;
			}

			final var direction = fontStyle.getDirection();
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
						final var rotate = AffineTransform.getTranslateInstance(drawX, drawY);
						rotate.concatenate(FontUtils.createSidewaysTransform(source, size));
						rotate.translate(-drawX, -drawY);
						gc.out.writeTransform(rotate);
						gc.out.writeOperator("cm");
					}
				}
				default -> throw new IllegalStateException("Unexpected direction: " + direction);
			}

			// Begin text
			gc.out.writeOperator("BT");
			if (invisible) {
				// Text that should not show but stay extractable (alpha 0 where transparency is not allowed),
				// inside the text object like the bold simulation below
				gc.out.writeInt(INVISIBLE_MODE);
				gc.out.writeOperator("Tr");
			}
			if (enlargement > 0) {
				// The rendering mode is written inside the text object:
				// written outside BT..ET some rasterizers (PDFBox) do not
				// pick it up, and the old q/Q pair that used to mask that
				// is gone.
				gc.out.writeInt(TextMode.FILL_STROKE.code);
				gc.out.writeOperator("Tr");
			}

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

			if (invisible) {
				gc.out.writeInt(gc.xtextMode.code);
				gc.out.writeOperator("Tr");
			}
			if (enlargement > 0) {
				// End bold simulation (inside the text object, see above).
				// The text mode shadow (xtextMode) still says FILL, so
				// restore the operator to match; the stroke state is
				// restored logically and the next applyStates call re-syncs
				// the PDF state by diff.
				gc.out.writeInt(TextMode.FILL.code);
				gc.out.writeOperator("Tr");
			}

			// End text
			gc.out.writeOperator("ET");

			if (enlargement > 0) {
				gc.setLineWidth(xlineWidth);
				gc.setLineJoin(xlineJoin);
				gc.setLinePattern(xlinePattern);
				gc.setStrokePaint(xstrokePaint);
				gc.setStrokeAlpha(xstrokeAlpha);
			}

			if (localContext) {
				gc.Q();
			}
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		}
}
