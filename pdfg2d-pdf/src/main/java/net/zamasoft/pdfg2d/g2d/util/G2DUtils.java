package net.zamasoft.pdfg2d.g2d.util;

import java.awt.Canvas;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.MediaTracker;
import java.awt.TexturePaint;
import java.awt.Toolkit;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.zamasoft.pdfg2d.g2d.gc.G2DGC;
import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GC.LineCap;
import net.zamasoft.pdfg2d.gc.GC.LineJoin;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.ConicGradient;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.Pattern;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;
import net.zamasoft.pdfg2d.gc.paint.SpreadMethod;

/**
 * Utilities for converting between AWT and internal graphics objects.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class G2DUtils {
	private static final Logger LOGGER = Logger.getLogger(G2DUtils.class.getName());

	private G2DUtils() {
		// unused
	}

	/**
	 * Converts AWT Paint to internal Paint.
	 * 
	 * @param paint AWT Paint
	 * @return Internal Paint
	 */
	public static Paint fromAwtPaint(java.awt.Paint paint) {
		if (paint instanceof java.awt.Color) {
			return fromAwtColor((java.awt.Color) paint);
		}
		if (paint instanceof SpotPaint spot) {
			return spot.getSpotColor();
		}
		if (paint instanceof GradientPaint) {
			GradientPaint gpaint = (GradientPaint) paint;
			if (gpaint.isCyclic()) {
				return null;
			}
			return new LinearGradient(gpaint.getPoint1().getX(), gpaint.getPoint1().getY(), gpaint.getPoint2().getX(),
					gpaint.getPoint2().getY(), new double[] { 0, 1 },
					new Color[] { fromAwtColor(gpaint.getColor1()), fromAwtColor(gpaint.getColor2()) },
					new AffineTransform());
		}
		// The standard java.awt multi-stop gradients and the Batik ones used by
		// the SVG bridge are distinct class hierarchies; both must be handled.
		if (paint instanceof java.awt.LinearGradientPaint gpaint) {
			if (gpaint.getCycleMethod() != java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE) {
				return null;
			}
			float[] fs = gpaint.getFractions();
			double[] fractions = new double[fs.length];
			for (int i = 0; i < fs.length; ++i) {
				fractions[i] = fs[i];
			}
			java.awt.Color[] cs = gpaint.getColors();
			Color[] colors = new Color[cs.length];
			for (int i = 0; i < cs.length; ++i) {
				colors[i] = fromAwtColor(cs[i]);
			}
			return new LinearGradient(gpaint.getStartPoint().getX(), gpaint.getStartPoint().getY(),
					gpaint.getEndPoint().getX(), gpaint.getEndPoint().getY(), fractions, colors,
					gpaint.getTransform());
		}
		if (paint instanceof java.awt.RadialGradientPaint gpaint) {
			if (gpaint.getCycleMethod() != java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE) {
				return null;
			}
			float[] fs = gpaint.getFractions();
			double[] fractions = new double[fs.length];
			for (int i = 0; i < fs.length; ++i) {
				fractions[i] = fs[i];
			}
			java.awt.Color[] cs = gpaint.getColors();
			Color[] colors = new Color[cs.length];
			for (int i = 0; i < cs.length; ++i) {
				colors[i] = fromAwtColor(cs[i]);
			}
			return new RadialGradient(gpaint.getCenterPoint().getX(), gpaint.getCenterPoint().getY(),
					gpaint.getRadius(), gpaint.getFocusPoint().getX(), gpaint.getFocusPoint().getY(), fractions,
					colors, gpaint.getTransform());
		}
		if (paint instanceof TexturePaint) {
			TexturePaint tpaint = (TexturePaint) paint;
			Rectangle2D r = tpaint.getAnchorRect();
			AffineTransform at = AffineTransform.getTranslateInstance(r.getX(), r.getY());
			BufferedImage image = tpaint.getImage();
			at.scale(r.getWidth() / image.getWidth(), r.getHeight() / image.getHeight());
			return new Pattern(new RasterImageImpl(image), at);
		}
		return null;
	}

	/**
	 * Converts internal LinearGradient to AWT Paint.
	 * 
	 * @param gradient Internal LinearGradient
	 * @return AWT Paint
	 */
	public static java.awt.Paint toAwtPaint(LinearGradient gradient) {
		double[] fs = gradient.fractions();
		float[] fractions = new float[fs.length];
		for (int i = 0; i < fs.length; ++i) {
			fractions[i] = (float) fs[i];
		}
		Color[] cs = gradient.colors();
		java.awt.Color[] colors = new java.awt.Color[cs.length];
		for (int i = 0; i < cs.length; ++i) {
			colors[i] = toAwtColor(cs[i]);
		}
		return new java.awt.LinearGradientPaint(new Point2D.Double(gradient.x1(), gradient.y1()),
				new Point2D.Double(gradient.x2(), gradient.y2()), fractions, colors,
				toCycleMethod(gradient.spread()),
				java.awt.MultipleGradientPaint.ColorSpaceType.SRGB,
				gradient.transform() != null ? gradient.transform() : new AffineTransform());
	}

	/**
	 * {@link SpreadMethod} を Java2D の周期指定へ写す(2026-08-29)。
	 * PAD→NO_CYCLE、REPEAT→REPEAT、REFLECT→REFLECT。
	 */
	public static java.awt.MultipleGradientPaint.CycleMethod toCycleMethod(final SpreadMethod spread) {
		if (spread == null) {
			return java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE;
		}
		return switch (spread) {
			case REPEAT -> java.awt.MultipleGradientPaint.CycleMethod.REPEAT;
			case REFLECT -> java.awt.MultipleGradientPaint.CycleMethod.REFLECT;
			default -> java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE;
		};
	}

	/**
	 * 円錐グラデーションを厳密に描く AWT Paint へ変換する(2026-08-29)。
	 *
	 * @param gradient 円錐グラデーション
	 * @return AWT Paint
	 */
	public static java.awt.Paint toAwtPaint(final ConicGradient gradient) {
		return new ConicGradientPaint(gradient);
	}

	/**
	 * Converts internal RadialGradient to AWT Paint.
	 * 
	 * @param gradient Internal RadialGradient
	 * @return AWT Paint
	 */
	public static java.awt.Paint toAwtPaint(RadialGradient gradient) {
		double[] fs = gradient.fractions();
		float[] fractions = new float[fs.length];
		for (int i = 0; i < fs.length; ++i) {
			fractions[i] = (float) fs[i];
		}
		Color[] cs = gradient.colors();
		java.awt.Color[] colors = new java.awt.Color[cs.length];
		for (int i = 0; i < cs.length; ++i) {
			colors[i] = toAwtColor(cs[i]);
		}
		// Note: the focus point is (fx, fy); the previous Batik-based code
		// passed the coordinates swapped.
		return new java.awt.RadialGradientPaint(new Point2D.Double(gradient.cx(), gradient.cy()),
				(float) gradient.radius(), new Point2D.Double(gradient.fx(), gradient.fy()), fractions, colors,
				toCycleMethod(gradient.spread()),
				java.awt.MultipleGradientPaint.ColorSpaceType.SRGB,
				gradient.transform() != null ? gradient.transform() : new AffineTransform());
	}

	/**
	 * Converts AWT Color to internal Color.
	 * 
	 * @param color AWT Color
	 * @return Internal Color
	 */
	public static Color fromAwtColor(java.awt.Color color) {
		float r = (short) color.getRed() / 255f;
		float g = (short) color.getGreen() / 255f;
		float b = (short) color.getBlue() / 255f;
		if (color.getAlpha() == 255) {
			return RGBColor.create(r, g, b);
		}
		float a = (short) color.getAlpha() / 255f;
		return RGBAColor.create(r, g, b, a);
	}

	/**
	 * Converts internal Color to AWT Color.
	 * 
	 * @param color Internal Color
	 * @return AWT Color
	 */
	public static java.awt.Color toAwtColor(Color color) {
		return new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	/**
	 * Converts internal Pattern to AWT Paint.
	 * 
	 * @param pattern Internal Pattern
	 * @param gc      Graphics Context
	 * @return AWT Paint
	 */
	public static java.awt.Paint toAwtPaint(Pattern pattern, GC gc) {
		Image image = pattern.getImage();
		double width = image.getWidth();
		double height = image.getHeight();
		BufferedImage bimage;
		if (image instanceof RasterImageImpl) {
			bimage = (BufferedImage) ((RasterImageImpl) image).getImage();
		} else {
			// 1pt未満の画像(1px画像のpx→pt変換等)を(int)で切り捨てると0になり
			// BufferedImageが生成できない。切り上げ+最低1pxを保証する
			bimage = new BufferedImage(Math.max(1, (int) Math.ceil(width)), Math.max(1, (int) Math.ceil(height)),
					BufferedImage.TYPE_INT_ARGB);
			Graphics2D bg = (Graphics2D) bimage.getGraphics();
			image.drawTo(new G2DGC(bg, gc.getFontManager()));
		}
		return new TexturePaint(bimage, new Rectangle2D.Double(0, 0, width, height));
	}

	/**
	 * Draws a BufferedImage with specified dimensions.
	 * 
	 * @param g     Graphics2D context
	 * @param image Image to draw
	 * @param x     X coordinate
	 * @param y     Y coordinate
	 * @param w     Width
	 * @param h     Height
	 */
	public static void drawImage(Graphics2D g, BufferedImage image, double x, double y, double w, double h) {
		g.drawImage(image, new AffineTransform(w / image.getWidth(), 0, 0, h / image.getHeight(), x, y), null);
	}

	/**
	 * Converts internal FontFamily to AWT font family name.
	 * 
	 * @param ffe Internal FontFamily
	 * @return AWT font family name
	 */
	public static final String toAwtFamilyName(FontFamily ffe) {
		switch (ffe.getGenericFamily()) {
			case CURSIVE:
				return "SansSerif";// AWT doesn't support logical font family
			// 'cursive'.
			case FANTASY:
				return "SansSerif";// AWT doesn't support logical font family
			// 'fantasy'.
			case MONOSPACE:
				return "Monospaced";
			case SANS_SERIF:
				return "SansSerif";
			case SERIF:
				return "Serif";

			default:
				return ffe.getName();
		}

	}

	/**
	 * Converts FontStyle to an array of AWT Fonts.
	 * 
	 * @param fontStyle FontStyle
	 * @return Array of AWT Fonts
	 */
	public static final Font[] toFonts(FontStyle fontStyle) {
		Map<TextAttribute, Object> atts = new HashMap<TextAttribute, Object>();
		setFontAttributes(atts, fontStyle);
		Font[] fonts = new Font[fontStyle.getFamily().getLength()];
		for (int i = 0; i < fonts.length; ++i) {
			atts.put(TextAttribute.FAMILY, G2DUtils.toAwtFamilyName(fontStyle.getFamily().get(i)));
			fonts[i] = new Font(atts);
		}
		return fonts;
	}

	/**
	 * Sets AWT TextAttributes based on FontStyle.
	 * 
	 * @param atts      Map to store attributes
	 * @param fontStyle From FontStyle
	 */
	public static final void setFontAttributes(Map<TextAttribute, Object> atts, FontStyle fontStyle) {
		atts.put(TextAttribute.SIZE, Float.valueOf((float) fontStyle.getSize()));

		Float weight;
		switch (fontStyle.getWeight()) {
			case W_100:
				weight = TextAttribute.WEIGHT_EXTRA_LIGHT;
				break;
			case W_200:
				weight = TextAttribute.WEIGHT_LIGHT;
				break;
			case W_300:
				weight = TextAttribute.WEIGHT_DEMILIGHT;
				break;
			case W_400:
				weight = TextAttribute.WEIGHT_REGULAR;
				break;
			case W_500:
				weight = TextAttribute.WEIGHT_SEMIBOLD;
				break;
			case W_600:
				weight = TextAttribute.WEIGHT_DEMIBOLD;
				break;
			case W_700:
				weight = TextAttribute.WEIGHT_BOLD;
				break;
			case W_800:
				weight = TextAttribute.WEIGHT_EXTRABOLD;
				break;
			case W_900:
				weight = TextAttribute.WEIGHT_ULTRABOLD;
				break;
			default:
				throw new IllegalStateException();
		}
		atts.put(TextAttribute.WEIGHT, weight);

		Float posture;
		switch (fontStyle.getStyle()) {
			case NORMAL:
				posture = TextAttribute.POSTURE_REGULAR;
				break;
			case ITALIC:
			case OBLIQUE:
				posture = TextAttribute.POSTURE_OBLIQUE;
				break;
			default:
				throw new IllegalStateException();
		}
		atts.put(TextAttribute.POSTURE, posture);
	}

	private static final class AwtFontNames {
		private static final Map<String, String> BY_NORMALIZED_NAME = load();

		private static Map<String, String> load() {
			final var names = new HashMap<String, String>();
			final var fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
			for (final var font : fonts) {
				final var name = font.getFontName();
				LOGGER.fine(name);
				names.put(FontUtils.normalizeName(name), name);
			}
			return Map.copyOf(names);
		}
	}

	/**
	 * Checks if a font is available in the local graphics environment.
	 * 
	 * @param fontName Font name to check
	 * @return True if available
	 */
	public static boolean isAvailable(final String fontName) {
		return AwtFontNames.BY_NORMALIZED_NAME.containsKey(FontUtils.normalizeName(fontName));
	}

	/**
	 * Gets the AWT font name for a normalized font name.
	 * 
	 * @param fontName Normalized font name
	 * @return AWT font name
	 */
	public static String toAwtFontName(final String fontName) {
		return AwtFontNames.BY_NORMALIZED_NAME.get(FontUtils.normalizeName(fontName));
	}

	/**
	 * Loads a BufferedImage from an ImageInputStream.
	 * 
	 * @param reader  ImageReader
	 * @param imageIn ImageInputStream
	 * @return Loaded BufferedImage
	 * @throws IOException If an I/O error occurs
	 */
	public static BufferedImage loadImage(ImageReader reader, ImageInputStream imageIn) throws IOException {
		try {
			BufferedImage buffer = null;
			if ("png".equalsIgnoreCase(reader.getFormatName())) {
				// PNGのgAMAガンマ補正(2026-08-01、旧JAI系自前デコーダ約2,500行の
				// 置換)。ImageIOはgAMAチャンクを復号に反映しないが、ブラウザは
				// 反映する——旧デコーダと同じ「表示指数2.2のガンマLUT」を
				// ImageIOの復号結果へ適用して挙動を保存する(visual golden
				// 0115-z-index/000-ABSOLUTE=gAMA 0.22727のorder.pngで固定)
				try {
					buffer = decodePngWithGamma(reader, imageIn);
				} catch (Throwable e) {
					buffer = null;
					imageIn.seek(0);
				}
			}
			if (buffer == null) {
				try {
					// Use ImageIO

					// Workaround for an ImageIO JPEG decoder defect: streams whose
					// SOI marker (FFD8) is immediately followed by DQT (FFDB) carry
					// no JFIF/EXIF/Adobe APPn segment, and ImageIO then guesses the
					// wrong color transform, producing inverted colors. Detect that
					// shape and bail out so decoding falls through to the AWT
					// Toolkit decoder below, which handles these files correctly.
					if ("JPEG".equalsIgnoreCase(reader.getFormatName())) {
						for (int i = 0; i < 100; ++i) {
							if (imageIn.read() == 0xFF) {
								if (imageIn.read() == 0xD8) {
									if (imageIn.read() == 0xFF) {
										if (imageIn.read() == 0xDB) {
											throw new Exception("Unreadable JPEG");
										}
									}
									break;
								}
							}
						}
						imageIn.seek(0);
					}

					reader.setInput(imageIn);
					buffer = reader.read(0);
				} catch (Throwable e1) {
					LOGGER.log(Level.FINE, "loadImage", e1);

					// Use Toolkit
					try {
						imageIn.seek(0);
						ByteArrayOutputStream out = new ByteArrayOutputStream();
						byte[] buff = new byte[8192];
						for (int len = imageIn.read(buff); len != -1; len = imageIn.read(buff)) {
							out.write(buff, 0, len);
						}
						java.awt.Image image = Toolkit.getDefaultToolkit().createImage(out.toByteArray());
						MediaTracker tracker = new MediaTracker(new Canvas());
						tracker.addImage(image, 0);
						tracker.waitForAll();
						buffer = new BufferedImage(image.getWidth(null), image.getHeight(null),
								BufferedImage.TYPE_INT_ARGB);
						buffer.getGraphics().drawImage(image, 0, 0, null);
					} catch (IOException ioe) {
						throw ioe;
					} catch (Throwable e2) {
						IOException ioe = new IOException(e2.getMessage());
						ioe.initCause(e2);
						throw ioe;
					}
				}
			}
			return buffer;
		} finally {
			reader.dispose();
		}
	}

	/**
	 * ImageIOでPNGを復号し、gAMAチャンクがあれば旧JAI系デコーダと同じ
	 * ガンマLUT(復号指数 = 1 / (fileGamma × 表示指数2.2))を適用します。
	 * sRGBチャンクがある場合はgAMAを無視する(PNG仕様・旧デコーダと同じ)。
	 * 実質恒等(標準のgAMA=1/2.2)の場合はLUTを掛けない。
	 */
	private static BufferedImage decodePngWithGamma(final ImageReader reader, final ImageInputStream imageIn)
			throws IOException {
		reader.setInput(imageIn);
		final BufferedImage image = reader.read(0);
		final double exponent = pngGammaExponent(reader);
		if (exponent == 1.0) {
			return image;
		}
		final int[] lut = new int[256];
		for (int i = 0; i < 256; ++i) {
			// 旧PNGImage.initGammaLutと同じ丸め
			final int v = (int) (Math.pow(i / 255.0, exponent) * 255.0 + 0.5);
			lut[i] = Math.min(v, 255);
		}
		final int w = image.getWidth(), h = image.getHeight();
		final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final int[] row = new int[w];
		for (int y = 0; y < h; ++y) {
			image.getRGB(0, y, w, 1, row, 0, w);
			for (int x = 0; x < w; ++x) {
				final int argb = row[x];
				row[x] = (argb & 0xFF000000) | (lut[(argb >> 16) & 0xFF] << 16) | (lut[(argb >> 8) & 0xFF] << 8)
						| lut[argb & 0xFF];
			}
			out.setRGB(0, y, w, 1, row, 0, w);
		}
		return out;
	}

	/**
	 * PNGのガンマ復号指数を返します(補正不要なら1.0)。
	 */
	private static double pngGammaExponent(final ImageReader reader) {
		try {
			final org.w3c.dom.Node tree = reader.getImageMetadata(0).getAsTree("javax_imageio_png_1.0");
			Double fileGamma = null;
			for (org.w3c.dom.Node node = tree.getFirstChild(); node != null; node = node.getNextSibling()) {
				if ("sRGB".equals(node.getNodeName())) {
					// sRGBチャンクがあればgAMAは無視
					return 1.0;
				}
				if ("gAMA".equals(node.getNodeName())) {
					final org.w3c.dom.Node value = node.getAttributes().getNamedItem("value");
					fileGamma = Double.parseDouble(value.getNodeValue()) / 100000.0;
				}
			}
			if (fileGamma == null || fileGamma <= 0) {
				return 1.0;
			}
			final double exponent = 1.0 / (fileGamma * 2.2);
			// 標準のsRGB相当(gAMA=45455)は恒等——LUT適用を省く
			return Math.abs(exponent - 1.0) < 0.01 ? 1.0 : exponent;
		} catch (final Exception e) {
			return 1.0;
		}
	}

	/**
	 * Decodes internal line cap to LineCap enum.
	 *
	 * @param lineCap Line cap value
	 * @return LineCap enum
	 */
	public static LineCap decodeLineCap(final short lineCap) {
		switch (lineCap) {
			case 0:
				return LineCap.BUTT;
			case 1:
				return LineCap.ROUND;
			case 2:
				return LineCap.SQUARE;
			default:
				throw new IllegalStateException();
		}
	}

	/**
	 * Decodes internal line join to LineJoin enum.
	 *
	 * @param lineJoin Line join value
	 * @return LineJoin enum
	 */
	public static LineJoin decodeLineJoin(final short lineJoin) {
		switch (lineJoin) {
			case 0:
				return LineJoin.MITER;
			case 1:
				return LineJoin.ROUND;
			case 2:
				return LineJoin.BEVEL;
			default:
				throw new IllegalStateException();
		}
	}
}
