package net.zamasoft.pdfg2d.g2d.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.DrawableFont;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.ImageFont;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFont;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/** Raster checks through the real Graphics2D context and outline path API. */
class VerticalOriginDrawingTest {
	private static final File FONT = new File("../pdfg2d-core/src/test/resources/pgothic-vert-subset.ttf");

	private static TextImpl text(final DrawableFont font, final double size, final String chars) {
		final var style = new FontStyleImpl(FontFamilyList.SERIF, size, FontStyle.Style.NORMAL,
				FontStyle.Weight.W_400, FontStyle.Direction.TB, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		final var text = new TextImpl(0, style, new FontMetricsImpl(source -> font, font.getFontSource(), style));
		for (final char c : chars.toCharArray()) {
			text.appendGlyph(new char[] { c }, 0, (byte) 1, font.toGID(c));
		}
		text.pack();
		return text;
	}

	private static BufferedImage draw(final DrawableFont font, final TextImpl text, final AffineTransform outer,
			final boolean outline) {
		return draw(font, text, outer, outline, new GeneralPath());
	}

	private static BufferedImage draw(final DrawableFont font, final TextImpl text, final AffineTransform outer,
			final boolean outline, final GeneralPath recorded) {
		final var image = new BufferedImage(400, 500, BufferedImage.TYPE_INT_ARGB);
		final var graphics = image.createGraphics();
		try {
			graphics.setColor(Color.BLACK);
			final var gc = new G2DGC(graphics, null) {
				@Override
				public void fill(final Shape shape) {
					recorded.append(shape.getPathIterator(graphics.getTransform()), false);
					super.fill(shape);
				}
			};
			if (outline) {
				final var path = new GeneralPath();
				FontUtils.addTextPath(path, (ShapedFont) font, text, outer);
				gc.fill(path);
			} else {
				gc.transform(outer);
				FontUtils.drawText(gc, font, text);
				assertEquals(outer, graphics.getTransform(), "drawText must restore the outer transform");
			}
		} finally {
			graphics.dispose();
		}
		return image;
	}

	private static void assertSameOutline(final GeneralPath expected, final GeneralPath actual) {
		final var e = expected.getPathIterator(null);
		final var a = actual.getPathIterator(null);
		final double[] ec = new double[6];
		final double[] ac = new double[6];
		while (!e.isDone()) {
			assertTrue(!a.isDone());
			assertEquals(e.currentSegment(ec), a.currentSegment(ac));
			for (int i = 0; i < 6; ++i) {
				assertEquals(ec[i], ac[i], 0.0001, "device-space path coordinates");
			}
			e.next();
			a.next();
		}
		assertTrue(a.isDone());
	}

	private static Rectangle2D bounds(final BufferedImage image) {
		int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
		for (int y = 0; y < image.getHeight(); ++y) {
			for (int x = 0; x < image.getWidth(); ++x) {
				if ((image.getRGB(x, y) >>> 24) != 0) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		assertTrue(maxX >= minX, "expected visible ink");
		return new Rectangle2D.Double(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	private static void assertSamePixels(final BufferedImage expected, final BufferedImage actual) {
		int differences = 0;
		for (int y = 0; y < actual.getHeight(); ++y) {
			for (int x = 0; x < actual.getWidth(); ++x) {
				if (expected.getRGB(x, y) != actual.getRGB(x, y)) {
					++differences;
				}
			}
		}
		if (differences != 0) {
			try {
				final var directory = new File(System.getProperty("pdfg2d.testOutputDir"));
				directory.mkdirs();
				javax.imageio.ImageIO.write(expected, "png", new File(directory, "vertical-expected.png"));
				javax.imageio.ImageIO.write(actual, "png", new File(directory, "vertical-actual.png"));
			} catch (final java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
		assertEquals(0, differences, "expected bounds=" + bounds(expected) + ", actual=" + bounds(actual));
	}

	@Test
	void bracketDoesNotOverlapNextGlyphAndBothDrawingPathsAgree() throws Exception {
		final var font = (ShapedFont) new OpenTypeFontSource(FONT, 0, FontStyle.Direction.TB).createFont();
		for (final double size : new double[] { 12, 36 }) {
			for (final double scale : new double[] { 1, 2 }) {
				final var outer = AffineTransform.getTranslateInstance(100, 50);
				outer.scale(1.5 * scale, scale);
				final var bracket = text(font, size, "「");
				bracket.addXAdvance(0, size * 0.2);
				final var next = text(font, size, "組");
				next.addXAdvance(0, size * (0.2 + 0.57));
				for (final boolean outline : new boolean[] { false, true }) {
					final var b = bounds(draw(font, bracket, outer, outline));
					final var n = bounds(draw(font, next, outer, outline));
					final double pen = 50 + size * 0.2 * scale;
					assertTrue(b.getMinY() >= pen - 1);
					assertTrue(b.getMaxY() <= pen + size * 0.57 * scale + 1);
					assertTrue(b.getMaxY() <= n.getMinY(), "opening bracket must not overlap the ideograph");
					assertEquals(0.216, (b.getMinY() - pen) / (size * scale), 1 / (size * scale));
				}
				final var run = text(font, size, "「A組");
				run.addXAdvance(0, 3);
				run.addXAdvance(1, 2);
				run.setLetterSpacing(1);
				final var expectedPath = new GeneralPath();
				final var actualPath = new GeneralPath();
				final var expectedImage = draw(font, run, outer, true, expectedPath);
				final var actualImage = draw(font, run, outer, false, actualPath);
				assertSameOutline(expectedPath, actualPath);
				// Java2D's transformed and pretransformed non-AA fill paths can
				// quantize boundary pixels differently despite identical geometry.
				final var e = bounds(expectedImage);
				final var a = bounds(actualImage);
				assertEquals(e.getMinX(), a.getMinX(), 1);
				assertEquals(e.getMinY(), a.getMinY(), 1);
				assertEquals(e.getMaxX(), a.getMaxX(), 1);
				assertEquals(e.getMaxY(), a.getMaxY(), 1);
			}
		}
	}

	@Test
	void imageFontRetainsConventionalPlacementAndCannotMutateThePen() throws Exception {
		final var source = new OpenTypeFontSource(FONT, 0, FontStyle.Direction.TB);
		final var realFont = source.createFont();
		final var font = (ImageFont) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ImageFont.class }, (proxy, method, args) -> switch (method.getName()) {
					case "getVerticalOrigin" -> (short) 100;
					case "getAdvance" -> (short) 1000;
					case "drawGlyphForGid" -> {
						final var gc = (GC) args[0];
						final var at = (AffineTransform) args[2];
						gc.fill(at.createTransformedShape(new Rectangle2D.Double(0, -880, 1000, 1000)));
						at.translate(10000, 10000);
						yield null;
					}
					default -> method.invoke(realFont, args);
				});
		for (final double size : new double[] { 12, 36 }) {
			final var run = text(font, size, "AA");
			run.addXAdvance(0, 3);
			final var image = draw(font, run, AffineTransform.getTranslateInstance(100, 50), false);
			assertEquals(new Rectangle2D.Double(100 - size / 2, 53, size, 2 * size), bounds(image));
		}
	}

	private static final class RotatedFont extends OpenTypeFont {
		private static final long serialVersionUID = 1L;
		private RotatedFont(final OpenTypeFontSource source) { super(source); }
		@Override
		protected int toChar(final int gid) { return 0xFF0D; }
		private int flags(final int gid) { return this.verticalShapeFlags(0xFF0D, gid); }
	}

	@Test
	void manualRotationRemainsFlagTwoAndRotatesAroundTheSameCenter() throws Exception {
		final var source = new OpenTypeFontSource(FONT, 0, FontStyle.Direction.TB);
		final var font = new RotatedFont(source);
		final int gid = font.toGID('組');
		final Shape raw = source.getOpenTypeFont().getGlyph(gid).path();
		final var b = raw.getBounds2D();
		final Shape rotated = AffineTransform.getRotateInstance(Math.PI / 2, b.getCenterX(), b.getCenterY())
				.createTransformedShape(raw);
		assertEquals(2, font.flags(gid));
		final var run = text(font, 36, "組組");
		final var outer = AffineTransform.getTranslateInstance(100, 50);
		final var expected = new BufferedImage(400, 500, BufferedImage.TYPE_INT_ARGB);
		final var g = expected.createGraphics();
		try {
			g.setColor(Color.BLACK);
			final var path = new GeneralPath();
			for (int i = 0; i < 2; ++i) {
				final var at = new AffineTransform(outer);
				at.translate(-18, i * 36 + 0.88 * 36);
				at.scale(0.036, 0.036);
				path.append(rotated.getPathIterator(at), false);
			}
			g.fill(path);
		} finally {
			g.dispose();
		}
		assertSamePixels(expected, draw(font, run, outer, false));
		assertSamePixels(expected, draw(font, run, outer, true));
	}
}
