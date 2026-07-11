package net.zamasoft.pdfg2d.gc.font.util;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import net.zamasoft.pdfg2d.font.DrawableFont;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ImageFont;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GC.TextMode;
import net.zamasoft.pdfg2d.gc.GraphicsException;

import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.paint.Paint;

import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * Utility class for text drawing and font related operations.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class FontUtils {
	private FontUtils() {
		// unused
	}

	/**
	 * Normalizes the font name by converting to uppercase and removing hyphens and
	 * spaces.
	 * 
	 * @param fontName the font name
	 * @return the normalized font name
	 */
	public static String normalizeName(final String fontName) {
		final var buff = new StringBuilder();
		for (int i = 0; i < fontName.length(); ++i) {
			final var ch = fontName.charAt(i);
			if (Character.isWhitespace(ch) || ch == '-' || ch == '_') {
				continue;
			}
			buff.append(Character.toUpperCase(ch));
		}
		return buff.toString();
	}

	/**
	 * Returns whether two font styles are equal.
	 *
	 * @param a the first font style
	 * @param b the second font style
	 * @return {@code true} if the two font styles are equal
	 */
	public static boolean equals(final FontStyle a, final FontStyle b) {
		return a.getFamily().equals(b.getFamily()) && a.getSize() == b.getSize() && a.getStyle() == b.getStyle()
				&& a.getWeight() == b.getWeight() && a.getDirection() == b.getDirection()
				&& a.getPolicy().equals(b.getPolicy());
	}

	/**
	 * Returns a hash code for the specified font style.
	 *
	 * @param fontStyle the font style
	 * @return the hash code
	 */
	public static int hashCode(final FontStyle fontStyle) {
		int h = fontStyle.getFamily().hashCode();
		final var a = Double.doubleToLongBits(fontStyle.getSize());
		h = 31 * h + (int) (a ^ (a >>> 32));
		h = 31 * h + fontStyle.getStyle().ordinal();
		h = 31 * h + fontStyle.getWeight().w;
		h = 31 * h + fontStyle.getDirection().ordinal();
		h = 31 * h + fontStyle.getPolicy().hashCode();
		return h;
	}

	/**
	 * Adds text to the path. Emoji characters are excluded.
	 * 
	 * @param path      the path to add content to
	 * @param font      the font
	 * @param text      the text to draw
	 * @param transform the transform to apply
	 */
	public static void addTextPath(final GeneralPath path, final ShapedFont font, final Text text,
			final AffineTransform transform) {
		final var fontStyle = text.getFontStyle();
		final var direction = fontStyle.getDirection();
		final var fontSize = fontStyle.getSize();
		final var glyphCount = text.getGlyphCount();
		final var glyphIds = text.getGlyphIds();
		final var letterSpacing = text.getLetterSpacing();
		final var xadvances = text.getXAdvances(false);
		final var fm = text.getFontMetrics();

		final var s = fontSize / FontSource.DEFAULT_UNITS_PER_EM;
		final var at = AffineTransform.getScaleInstance(s, s);

		final var verticalFont = direction == Direction.TB && font.getFontSource().getDirection() == direction;
		AffineTransform oblique = null;
		final var style = fontStyle.getStyle();
		if (style != Style.NORMAL && !font.getFontSource().isItalic()) {
			// Simulate italic manually
			if (verticalFont) {
				oblique = AffineTransform.getShearInstance(0, 0.25);
			} else {
				oblique = AffineTransform.getShearInstance(-0.25, 0);
			}
		}

		if (verticalFont) {
			// Vertical writing mode
			// Vertical font
			at.preConcatenate(AffineTransform.getTranslateInstance(-fontSize / 2.0, fontSize * 0.88));
			int pgid = 0;
			for (int i = 0; i < glyphCount; ++i) {
				final var gid = glyphIds[i];
				if (i > 0) {
					double dy = fm.getAdvance(pgid) + letterSpacing;
					dy -= fm.getKerning(pgid, gid);
					if (xadvances != null) {
						dy += xadvances[i];
					}
					at.preConcatenate(AffineTransform.getTranslateInstance(0, dy));
				}
				pgid = gid;
				var shape = ((ShapedFont) font).getShapeByGID(gid);
				if (shape != null) {
					final var at2 = new AffineTransform(transform);
					final var width = (fontSize - fm.getWidth(gid)) / 2.0;
					if (width != 0) {
						at2.translate(width, 0);
					}
					at2.concatenate(at);
					if (oblique != null) {
						shape = oblique.createTransformedShape(shape);
					}
					path.append(shape.getPathIterator(at2), false);
				}
			}
		} else {
			// Horizontal writing
			int pgid = 0;
			for (int i = 0; i < glyphCount; ++i) {
				final int gid = glyphIds[i];
				if (i > 0) {
					double dx = fm.getAdvance(pgid) + letterSpacing;
					if (i > 0) {
						dx -= fm.getKerning(pgid, gid);
					}
					if (xadvances != null) {
						dx += xadvances[i];
					}
					at.preConcatenate(AffineTransform.getTranslateInstance(dx, 0));
				}
				final var at2 = new AffineTransform(transform);
				var shape = ((ShapedFont) font).getShapeByGID(gid);
				if (shape != null) {
					at2.concatenate(at);
					if (oblique != null) {
						shape = oblique.createTransformedShape(shape);
					}
					path.append(shape.getPathIterator(at2), false);
				}
				pgid = gid;
			}
		}
	}

	/**
	 * Draws text outline directly.
	 * 
	 * @param gc   the graphics context
	 * @param font the font
	 * @param text the text
	 * @throws GraphicsException if a graphics error occurs
	 */
	public static void drawText(final GC gc, final DrawableFont font, final Text text) throws GraphicsException {
		try (final var gcState = gc.begin()) {
			final var fontStyle = text.getFontStyle();
			final var direction = fontStyle.getDirection();
			final var fontSize = fontStyle.getSize();
			final var glyphCount = text.getGlyphCount();
			final var glyphIds = text.getGlyphIds();
			final var letterSpacing = text.getLetterSpacing();
			final var xadvances = text.getXAdvances(false);
			final var fm = text.getFontMetrics();
			AffineTransform at;
			{
				final var s = fontSize / FontSource.DEFAULT_UNITS_PER_EM;
				at = AffineTransform.getScaleInstance(s, s);
			}

			var textMode = gc.getTextMode();
			double enlargement;
			final var weight = fontStyle.getWeight();
			double xlineWidth = 0;
			Paint xstrokePaint = null;
			if (textMode == TextMode.FILL && weight.w >= 500 && font.getFontSource().getWeight().w < 500) {
				// Simulate BOLD manually
				switch (weight) {
					case W_500 -> enlargement = fontSize / 28.0;
					case W_600 -> enlargement = fontSize / 24.0;
					case W_700 -> enlargement = fontSize / 20.0;
					case W_800 -> enlargement = fontSize / 16.0;
					case W_900 -> enlargement = fontSize / 12.0;
					default -> throw new IllegalStateException();
				}
				if (enlargement > 0) {
					textMode = TextMode.FILL_STROKE;
					xlineWidth = gc.getLineWidth();
					gc.setLineWidth(enlargement);
					gc.setStrokePaint(gc.getFillPaint());
					xstrokePaint = gc.getStrokePaint();
				}
			} else {
				enlargement = 0;
			}

			final var verticalFont = direction == Direction.TB && font.getFontSource().getDirection() == direction;
			AffineTransform oblique = null;
			final var style = fontStyle.getStyle();
			if (style != Style.NORMAL && !font.getFontSource().isItalic()) {
				// Simulate italic manually
				if (verticalFont) {
					oblique = AffineTransform.getShearInstance(0, 0.25);
				} else {
					oblique = AffineTransform.getShearInstance(-0.25, 0);
				}
			}

			GeneralPath path = null;
			if (verticalFont) {
				// Vertical writing mode
				// Vertical font
				gc.transform(AffineTransform.getTranslateInstance(-fontSize / 2.0, fontSize * 0.88));
				int pgid = 0;
				for (int i = 0; i < glyphCount; ++i) {
					var at2 = at;
					final var gid = glyphIds[i];
					if (i > 0) {
						double dy = fm.getAdvance(pgid) + letterSpacing;
						dy -= fm.getKerning(pgid, gid);
						if (xadvances != null) {
							dy += xadvances[i];
						}
						at.preConcatenate(AffineTransform.getTranslateInstance(0, dy));
					}
					pgid = gid;
					if (font instanceof ShapedFont) {
						var shape = ((ShapedFont) font).getShapeByGID(gid);
						if (shape != null) {
							final var width = (fontSize - fm.getWidth(gid)) / 2.0;
							if (width != 0) {
								at2 = AffineTransform.getTranslateInstance(width, 0);
								at2.concatenate(at);
							}
							if (oblique != null) {
								shape = oblique.createTransformedShape(shape);
							}
							if (path == null) {
								path = new GeneralPath();
							}
							path.append(shape.getPathIterator(at2), false);
						}
					} else {
						((ImageFont) font).drawGlyphForGid(gc, gid, at2);
					}
				}
			} else {
				if (direction == Direction.TB) {
					// Rotated horizontal
					gc.transform(AffineTransform.getRotateInstance(Math.PI / 2.0));
					final var bbox = font.getFontSource().getBBox();
					final var dy = ((bbox.lly() + bbox.ury()) * fontSize / FontSource.DEFAULT_UNITS_PER_EM) / 2.0;
					gc.transform(AffineTransform.getTranslateInstance(0, dy));
				}
				// Horizontal writing
				int pgid = 0;
				for (int i = 0; i < glyphCount; ++i) {
					final int gid = glyphIds[i];
					if (i > 0) {
						double dx = fm.getAdvance(pgid) + letterSpacing;
						if (i > 0) {
							dx -= fm.getKerning(pgid, gid);
						}
						if (xadvances != null) {
							dx += xadvances[i];
						}
						at.preConcatenate(AffineTransform.getTranslateInstance(dx, 0));
					}
					if (font instanceof ShapedFont) {
						var shape = ((ShapedFont) font).getShapeByGID(gid);
						if (shape != null) {
							if (oblique != null) {
								shape = oblique.createTransformedShape(shape);
							}
							if (path == null) {
								path = new GeneralPath();
							}
							path.append(shape.getPathIterator(at), false);
						}
					} else {
						((ImageFont) font).drawGlyphForGid(gc, gid, at);
					}
					pgid = gid;
				}
			}
			if (path != null) {
				drawPath(gc, path, textMode);
			}

			if (enlargement > 0) {
				gc.setLineWidth(xlineWidth);
				gc.setStrokePaint(xstrokePaint);
			}
		}
	}

	private static void drawPath(final GC gc, final GeneralPath path, final TextMode textMode) {
		switch (textMode) {
			case FILL -> gc.fill(path);
			case STROKE -> gc.draw(path);
			case FILL_STROKE -> gc.fillDraw(path);
			default -> throw new IllegalStateException();
		}
	}
}