package net.zamasoft.pdfg2d.g2d.gc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.ConicGradient;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.paint.SpreadMethod;

/**
 * Java2D 出力の厳密描画(ぼかし・円錐・繰り返し・層効果・ブレンド)の画素検査(2026-08-29)。
 */
class G2DGCEffectsTest {
	private static final Color RED = RGBColor.create(1, 0, 0);
	private static final Color GREEN = RGBColor.create(0, 1, 0);
	private static final Color BLUE = RGBColor.create(0, 0, 1);
	private static final Color YELLOW = RGBColor.create(1, 1, 0);
	private static final Color BLACK = RGBColor.create(0, 0, 0);

	private static final int ARGB_RED = 0xFFFF0000;
	private static final int ARGB_GREEN = 0xFF00FF00;
	private static final int ARGB_BLUE = 0xFF0000FF;
	private static final int ARGB_YELLOW = 0xFFFFFF00;

	private static BufferedImage canvas(final int w, final int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	private static G2DGC gc(final BufferedImage canvas) {
		final Graphics2D g = canvas.createGraphics();
		return new G2DGC(g, null);
	}

	private static int alpha(final BufferedImage image, final int x, final int y) {
		return image.getRGB(x, y) >>> 24;
	}

	private static void assertRgbNear(final int expected, final int actual, final int tolerance) {
		for (int shift = 0; shift <= 24; shift += 8) {
			final int e = (expected >> shift) & 0xFF, a = (actual >> shift) & 0xFF;
			assertTrue(Math.abs(e - a) <= tolerance,
					String.format("expected %08X but was %08X (channel shift %d)", expected, actual, shift));
		}
	}

	@Test
	void supportsEveryCapabilityAlsoInsideGroupImages() {
		final G2DGC gc = gc(canvas(8, 8));
		for (final GC.Capability c : GC.Capability.values()) {
			assertTrue(gc.supports(c), c.name());
		}
		final GroupImageGC group = gc.createGroupImage(4, 4);
		for (final GC.Capability c : GC.Capability.values()) {
			assertTrue(group.supports(c), "group " + c.name());
		}
	}

	// (a) ぼかし塗り

	@Test
	void fillBlurredSpreadsAlphaSmoothlyAndPreservesCoverage() {
		final BufferedImage canvas = canvas(60, 60);
		final G2DGC gc = gc(canvas);
		gc.setFillPaint(BLACK);
		gc.fillBlurred(new Rectangle2D.Double(20, 20, 20, 20), 3);

		assertTrue(alpha(canvas, 30, 30) >= 250, "the middle stays opaque");
		int previous = alpha(canvas, 30, 30);
		for (final int x : new int[] { 19, 17, 15, 13 }) {
			final int a = alpha(canvas, x, 30);
			assertTrue(a > 0 && a < previous, "alpha must decrease smoothly outside the square at x=" + x + ": " + a);
			previous = a;
		}
		assertEquals(0, alpha(canvas, 10, 30), "beyond 3 sigma nothing is painted");
		assertEquals(0, alpha(canvas, 5, 5));
		assertEquals(alpha(canvas, 19, 30), alpha(canvas, 30, 19), "the blur is isotropic");
		assertEquals(0, canvas.getRGB(19, 30) & 0xFFFFFF, "premultiplied blur keeps the color black");

		double coverage = 0;
		for (int y = 0; y < 60; ++y) {
			for (int x = 0; x < 60; ++x) {
				coverage += alpha(canvas, x, y) / 255.0;
			}
		}
		assertEquals(400, coverage, 8, "total coverage is preserved by the normalized kernel");
	}

	@Test
	void fillBlurredHonorsClipAlphaAndZeroSigma() {
		final BufferedImage clipped = canvas(60, 60);
		final G2DGC gc = gc(clipped);
		gc.setFillPaint(BLACK);
		gc.clip(new Rectangle2D.Double(0, 0, 30, 60));
		gc.fillBlurred(new Rectangle2D.Double(20, 20, 20, 20), 3);
		assertTrue(alpha(clipped, 25, 30) > 0);
		assertEquals(0, alpha(clipped, 35, 30), "the clip applies to the blurred result");

		final BufferedImage translucent = canvas(60, 60);
		final G2DGC gc2 = gc(translucent);
		gc2.setFillPaint(BLACK);
		gc2.setFillAlpha(0.5f);
		gc2.fillBlurred(new Rectangle2D.Double(20, 20, 20, 20), 3);
		assertEquals(128, alpha(translucent, 30, 30), 3, "fill alpha scales the blurred layer");

		final BufferedImage plain = canvas(60, 60), zero = canvas(60, 60);
		final G2DGC gcPlain = gc(plain), gcZero = gc(zero);
		gcPlain.setFillPaint(RED);
		gcZero.setFillPaint(RED);
		gcPlain.fill(new Rectangle2D.Double(20, 20, 20, 20));
		gcZero.fillBlurred(new Rectangle2D.Double(20, 20, 20, 20), 0);
		assertArrayEquals(plain.getRGB(0, 0, 60, 60, null, 0, 60), zero.getRGB(0, 0, 60, 60, null, 0, 60),
				"sigma <= 0 is a plain fill");
	}

	@Test
	void fillBlurredScalesSigmaWithTheTransform() {
		// 2倍拡大でσ=1.5 → デバイスではσ=3 の結果と同じ広がり
		final BufferedImage scaled = canvas(60, 60);
		final G2DGC gc = gc(scaled);
		gc.setFillPaint(BLACK);
		gc.transform(AffineTransform.getScaleInstance(2, 2));
		gc.fillBlurred(new Rectangle2D.Double(10, 10, 10, 10), 1.5);

		final BufferedImage reference = canvas(60, 60);
		final G2DGC ref = gc(reference);
		ref.setFillPaint(BLACK);
		ref.fillBlurred(new Rectangle2D.Double(20, 20, 20, 20), 3);
		assertEquals(alpha(reference, 17, 30), alpha(scaled, 17, 30), 1);
		assertEquals(alpha(reference, 13, 30), alpha(scaled, 13, 30), 1);
	}

	// (b) 円錐グラデーション

	private static ConicGradient quadrants(final double startAngle) {
		return new ConicGradient(50, 50, startAngle, new double[] { 0, 0.25, 0.25, 0.5, 0.5, 0.75, 0.75, 1 },
				new Color[] { RED, RED, GREEN, GREEN, BLUE, BLUE, YELLOW, YELLOW }, new AffineTransform());
	}

	@Test
	void conicGradientPaintsQuadrantsClockwiseFromUp() {
		final BufferedImage canvas = canvas(100, 100);
		final G2DGC gc = gc(canvas);
		gc.setFillPaint(quadrants(0));
		gc.fill(new Rectangle2D.Double(0, 0, 100, 100));
		assertEquals(ARGB_RED, canvas.getRGB(70, 30), "0..90deg (up-right) is red");
		assertEquals(ARGB_GREEN, canvas.getRGB(70, 70), "90..180deg (down-right) is green");
		assertEquals(ARGB_BLUE, canvas.getRGB(30, 70), "180..270deg (down-left) is blue");
		assertEquals(ARGB_YELLOW, canvas.getRGB(30, 30), "270..360deg (up-left) is yellow");
		// 境界のすぐ両側
		assertEquals(ARGB_RED, canvas.getRGB(51, 10));
		assertEquals(ARGB_YELLOW, canvas.getRGB(48, 10));
	}

	@Test
	void conicGradientHonorsStartAngleTransformStrokeAndGroupImages() {
		final BufferedImage rotated = canvas(100, 100);
		final G2DGC gc = gc(rotated);
		gc.setFillPaint(quadrants(Math.PI / 2));
		gc.fill(new Rectangle2D.Double(0, 0, 100, 100));
		assertEquals(ARGB_YELLOW, rotated.getRGB(70, 30), "start angle 90deg rotates the wheel clockwise");
		assertEquals(ARGB_RED, rotated.getRGB(70, 70));

		// GC の変換で中心が動く(中心を(0,0)に置き、平行移動で(50,50)へ)
		final BufferedImage moved = canvas(100, 100);
		final G2DGC gcMoved = gc(moved);
		gcMoved.transform(AffineTransform.getTranslateInstance(50, 50));
		gcMoved.setFillPaint(new ConicGradient(0, 0, 0, new double[] { 0, 0.25, 0.25, 0.5, 0.5, 0.75, 0.75, 1 },
				new Color[] { RED, RED, GREEN, GREEN, BLUE, BLUE, YELLOW, YELLOW }, new AffineTransform()));
		gcMoved.fill(new Rectangle2D.Double(-50, -50, 100, 100));
		assertEquals(ARGB_RED, moved.getRGB(70, 30));
		assertEquals(ARGB_BLUE, moved.getRGB(30, 70));

		// 線
		final BufferedImage stroked = canvas(100, 100);
		final G2DGC gcStroke = gc(stroked);
		gcStroke.setStrokePaint(quadrants(0));
		gcStroke.setLineWidth(4);
		gcStroke.draw(new Line2D.Double(10, 30, 90, 30));
		assertEquals(ARGB_RED, stroked.getRGB(70, 30));
		assertEquals(ARGB_YELLOW, stroked.getRGB(30, 30));
		assertEquals(0, stroked.getRGB(70, 70));

		// グループ画像の中
		final BufferedImage grouped = canvas(100, 100);
		final G2DGC gcGroup = gc(grouped);
		final GroupImageGC group = gcGroup.createGroupImage(100, 100);
		group.setFillPaint(quadrants(0));
		group.fill(new Rectangle2D.Double(0, 0, 100, 100));
		gcGroup.drawImage(group.finish());
		assertEquals(ARGB_GREEN, grouped.getRGB(70, 70));
		assertEquals(ARGB_YELLOW, grouped.getRGB(30, 30));
	}

	@Test
	void conicGradientInterpolatesLinearlyAndPadsOutsideTheStops() {
		final BufferedImage canvas = canvas(100, 100);
		final G2DGC gc = gc(canvas);
		gc.setFillPaint(new ConicGradient(50, 50, 0, new double[] { 0, 1 }, new Color[] { RED, BLUE },
				new AffineTransform()));
		gc.fill(new Rectangle2D.Double(0, 0, 100, 100));
		// 真下(180deg)は中間色
		assertRgbNear(0xFF800080, canvas.getRGB(50, 90), 4);

		// 停止が 0.25..0.75 しか覆わない: 外側は端の色(CSS と同じ)
		final BufferedImage padded = canvas(100, 100);
		final G2DGC gcPad = gc(padded);
		gcPad.setFillPaint(new ConicGradient(50, 50, 0, new double[] { 0.25, 0.75 }, new Color[] { GREEN, BLUE },
				new AffineTransform()));
		gcPad.fill(new Rectangle2D.Double(0, 0, 100, 100));
		assertEquals(ARGB_GREEN, padded.getRGB(70, 30), "before the first stop: first color");
		assertEquals(ARGB_BLUE, padded.getRGB(30, 30), "after the last stop: last color");
		assertRgbNear(0xFF008080, padded.getRGB(50, 90), 4);

		// REPEAT: 0.25 周期で繰り返す
		final BufferedImage repeated = canvas(100, 100);
		final G2DGC gcRep = gc(repeated);
		gcRep.setFillPaint(new ConicGradient(50, 50, 0, new double[] { 0, 0.25 }, new Color[] { RED, BLUE },
				new AffineTransform(), SpreadMethod.REPEAT));
		gcRep.fill(new Rectangle2D.Double(0, 0, 100, 100));
		// 画素中心がちょうど 45/135/225/315deg に乗る画素: 各周期の中点=中間色
		assertRgbNear(0xFF800080, repeated.getRGB(60, 39), 1);
		assertRgbNear(0xFF800080, repeated.getRGB(60, 60), 1);
		assertRgbNear(0xFF800080, repeated.getRGB(39, 60), 1);
		assertRgbNear(0xFF800080, repeated.getRGB(39, 39), 1);
		// 周期の始まりは赤、終わりは青
		assertRgbNear(ARGB_RED, repeated.getRGB(50, 10), 8);
		assertRgbNear(ARGB_BLUE, repeated.getRGB(89, 49), 8);
	}

	// (c) 繰り返し線形グラデーション

	@Test
	void repeatLinearGradientRepeatsWithItsPeriod() {
		final BufferedImage canvas = canvas(40, 10);
		final G2DGC gc = gc(canvas);
		gc.setFillPaint(new LinearGradient(0, 0, 10, 0, new double[] { 0, 1 }, new Color[] { BLACK, RGBColor.create(1, 1, 1) },
				new AffineTransform(), SpreadMethod.REPEAT));
		gc.fill(new Rectangle2D.Double(0, 0, 40, 10));
		assertEquals(canvas.getRGB(2, 5), canvas.getRGB(12, 5));
		assertEquals(canvas.getRGB(2, 5), canvas.getRGB(22, 5));
		assertEquals(canvas.getRGB(7, 5), canvas.getRGB(37, 5));
		assertNotEquals(canvas.getRGB(2, 5), canvas.getRGB(7, 5));
		assertTrue((canvas.getRGB(2, 5) & 0xFF) < (canvas.getRGB(7, 5) & 0xFF), "brightness rises within a period");
		assertTrue((canvas.getRGB(12, 5) & 0xFF) < (canvas.getRGB(17, 5) & 0xFF), "and again in the next period");

		// PAD は端の色で埋める
		final BufferedImage padded = canvas(40, 10);
		final G2DGC gcPad = gc(padded);
		gcPad.setFillPaint(new LinearGradient(0, 0, 10, 0, new double[] { 0, 1 }, new Color[] { BLACK, RGBColor.create(1, 1, 1) },
				new AffineTransform()));
		gcPad.fill(new Rectangle2D.Double(0, 0, 40, 10));
		assertEquals(0xFFFFFFFF, padded.getRGB(30, 5));
	}

	// (d) 層効果

	private static Image redSquare(final G2DGC gc, final double size) {
		final GroupImageGC group = gc.createGroupImage(size, size);
		group.setFillPaint(RED);
		group.fill(new Rectangle2D.Double(0, 0, size, size));
		return group.finish();
	}

	@Test
	void drawImageWithColorMatrixInvertsTheLayer() {
		final BufferedImage canvas = canvas(50, 50);
		final G2DGC gc = gc(canvas);
		gc.transform(AffineTransform.getTranslateInstance(20, 20));
		final Image image = redSquare(gc, 10);
		final float[] invert = { -1, 0, 0, 0, 1, 0, -1, 0, 0, 1, 0, 0, -1, 0, 1, 0, 0, 0, 1, 0 };
		gc.drawImage(image, new GroupEffects(invert, 0, null, 1));
		assertEquals(0xFF00FFFF, canvas.getRGB(25, 25), "red inverted is cyan");
		assertEquals(0, canvas.getRGB(15, 15), "transparent stays transparent (alpha row is identity)");
		assertEquals(0, canvas.getRGB(35, 35));
	}

	@Test
	void drawImageWithDropShadowPutsTheShadowUnderTheLayer() {
		final BufferedImage canvas = canvas(50, 50);
		final G2DGC gc = gc(canvas);
		gc.transform(AffineTransform.getTranslateInstance(20, 20));
		final Image image = redSquare(gc, 10);
		gc.drawImage(image, new GroupEffects(null, 0, new GroupEffects.DropShadow(8, 8, 0, BLACK), 1));
		assertEquals(ARGB_RED, canvas.getRGB(25, 25), "the layer itself");
		assertEquals(ARGB_RED, canvas.getRGB(29, 29), "where both overlap the layer is on top");
		assertEquals(0xFF000000, canvas.getRGB(35, 35), "the shadow appears at the offset");
		assertEquals(0xFF000000, canvas.getRGB(28, 31));
		assertEquals(0, canvas.getRGB(17, 17), "nothing outside layer and shadow");
		assertEquals(0, canvas.getRGB(33, 19));
		assertEquals(0, canvas.getRGB(39, 39));

		// ぼかした半透明の影
		final BufferedImage soft = canvas(60, 60);
		final G2DGC gcSoft = gc(soft);
		gcSoft.transform(AffineTransform.getTranslateInstance(20, 20));
		final Image image2 = redSquare(gcSoft, 10);
		gcSoft.drawImage(image2, new GroupEffects(null, 0,
				new GroupEffects.DropShadow(8, 8, 2, RGBAColor.create(0, 0, 1, 0.5f)), 1));
		final int core = soft.getRGB(33, 33);
		assertTrue(alpha(soft, 33, 33) >= 100 && alpha(soft, 33, 33) <= 130, "shadow alpha ~0.5: " + alpha(soft, 33, 33));
		assertEquals(0x0000FF, core & 0xFFFFFF, "shadow color is the given color");
		assertTrue(alpha(soft, 38, 33) > 0 && alpha(soft, 38, 33) < alpha(soft, 33, 33), "the shadow edge is blurred");
		assertEquals(ARGB_RED, soft.getRGB(25, 25));
	}

	@Test
	void drawImageWithBlurAndOpacity() {
		final BufferedImage blurred = canvas(50, 50);
		final G2DGC gc = gc(blurred);
		gc.transform(AffineTransform.getTranslateInstance(20, 20));
		gc.drawImage(redSquare(gc, 10), new GroupEffects(null, 2, null, 1));
		assertTrue(alpha(blurred, 25, 25) >= 240, "middle of the blurred layer");
		final int edge = alpha(blurred, 19, 25);
		assertTrue(edge > 0 && edge < 255, "blur bleeds outside the layer: " + edge);
		assertEquals(0, alpha(blurred, 12, 25));
		assertEquals(0xFF0000, blurred.getRGB(19, 25) & 0xFFFFFF, "premultiplied blur keeps the color");

		final BufferedImage faded = canvas(50, 50);
		final G2DGC gcFaded = gc(faded);
		gcFaded.transform(AffineTransform.getTranslateInstance(20, 20));
		gcFaded.drawImage(redSquare(gcFaded, 10), new GroupEffects(null, 0, null, 0.5));
		assertEquals(128, alpha(faded, 25, 25), 2);
		assertEquals(0xFF0000, faded.getRGB(25, 25) & 0xFFFFFF);

		// 恒等効果は普通の drawImage と同じ
		final BufferedImage plain = canvas(50, 50), identity = canvas(50, 50);
		final G2DGC gcPlain = gc(plain), gcIdentity = gc(identity);
		gcPlain.transform(AffineTransform.getTranslateInstance(20, 20));
		gcIdentity.transform(AffineTransform.getTranslateInstance(20, 20));
		gcPlain.drawImage(redSquare(gcPlain, 10));
		gcIdentity.drawImage(redSquare(gcIdentity, 10), GroupEffects.NONE);
		assertArrayEquals(plain.getRGB(0, 0, 50, 50, null, 0, 50), identity.getRGB(0, 0, 50, 50, null, 0, 50));
	}

	// (e) ブレンド

	@Test
	void multiplyBlendOfTwoFillsGivesTheProduct() {
		final BufferedImage canvas = canvas(20, 20);
		final G2DGC gc = gc(canvas);
		gc.setFillPaint(RGBColor.create(200 / 255f, 100 / 255f, 50 / 255f));
		gc.fill(new Rectangle2D.Double(0, 0, 20, 20));
		try (final var state = gc.begin()) {
			gc.setBlendMode(BlendMode.MULTIPLY);
			assertEquals(BlendMode.MULTIPLY, gc.getBlendMode());
			gc.setFillPaint(RGBColor.create(100 / 255f, 200 / 255f, 150 / 255f));
			gc.fill(new Rectangle2D.Double(5, 5, 10, 10));
			gc.setStrokePaint(RGBColor.create(100 / 255f, 200 / 255f, 150 / 255f));
			gc.setLineWidth(2);
			gc.draw(new Line2D.Double(0, 18, 20, 18));
		}
		assertEquals(BlendMode.NORMAL, gc.getBlendMode(), "begin/close restores the blend mode");
		assertRgbNear(0xFF4E4E1D, canvas.getRGB(10, 10), 1);
		assertRgbNear(0xFF4E4E1D, canvas.getRGB(2, 18), 1, "strokes blend too");
		assertRgbNear(0xFFC86432, canvas.getRGB(2, 2), 0);

		// 復元後は普通に上書き
		gc.setFillPaint(RGBColor.create(100 / 255f, 200 / 255f, 150 / 255f));
		gc.fill(new Rectangle2D.Double(0, 0, 3, 3));
		assertRgbNear(0xFF64C896, canvas.getRGB(1, 1), 0);
	}

	private static void assertRgbNear(final int expected, final int actual, final int tolerance, final String message) {
		assertRgbNear(expected, actual, tolerance);
	}

	@Test
	void blendModeAppliesToGroupImagesAndTransparentBackdrops() {
		final BufferedImage canvas = canvas(20, 20);
		final G2DGC gc = gc(canvas);
		gc.setFillPaint(RGBColor.create(200 / 255f, 100 / 255f, 50 / 255f));
		gc.fill(new Rectangle2D.Double(0, 0, 10, 20));
		final GroupImageGC group = gc.createGroupImage(20, 20);
		group.setFillPaint(RGBColor.create(100 / 255f, 200 / 255f, 150 / 255f));
		group.fill(new Rectangle2D.Double(0, 0, 20, 20));
		final Image image = group.finish();
		gc.setBlendMode(BlendMode.MULTIPLY);
		gc.drawImage(image);
		assertRgbNear(0xFF4E4E1D, canvas.getRGB(5, 10), 1, "the group multiplies the backdrop");
		assertRgbNear(0xFF64C896, canvas.getRGB(15, 10), 0, "over a transparent backdrop the source shows unchanged");

		// 定数アルファと併用
		final BufferedImage half = canvas(20, 20);
		final G2DGC gcHalf = gc(half);
		gcHalf.setFillPaint(RGBColor.create(1, 1, 1));
		gcHalf.fill(new Rectangle2D.Double(0, 0, 20, 20));
		gcHalf.setBlendMode(BlendMode.MULTIPLY);
		gcHalf.setFillAlpha(0.5f);
		gcHalf.setFillPaint(BLACK);
		gcHalf.fill(new Rectangle2D.Double(0, 0, 20, 20));
		assertRgbNear(0xFF808080, half.getRGB(10, 10), 1);
	}

	@Test
	void blendCompositeImplementsSeparableAndNonSeparableModes() {
		final BlendComposite screen = (BlendComposite) BlendComposite.getInstance(BlendMode.SCREEN, 1);
		assertRgbNear(0xFFDEDEAB, screen.blend(0xFF64C896, 0xFFC86432), 1);
		final BlendComposite difference = (BlendComposite) BlendComposite.getInstance(BlendMode.DIFFERENCE, 1);
		assertRgbNear(0xFF646464, difference.blend(0xFF64C896, 0xFFC86432), 0);
		final BlendComposite luminosity = (BlendComposite) BlendComposite.getInstance(BlendMode.LUMINOSITY, 1);
		// 灰(0.5)の輝度を赤へ: SetLum(red, 0.5) = (1, 0.286, 0.286) after ClipColor
		assertRgbNear(0xFFFF4A4A, luminosity.blend(0xFF808080, 0xFFFF0000), 1);
		final BlendComposite hue = (BlendComposite) BlendComposite.getInstance(BlendMode.HUE, 1);
		// 背景が灰(彩度0)なら色相を移しても灰のまま
		assertRgbNear(0xFF808080, hue.blend(0xFFFF0000, 0xFF808080), 1);
		assertTrue(BlendComposite.getInstance(BlendMode.NORMAL, 1) instanceof java.awt.AlphaComposite);
		// 透明なソースは背景をそのまま
		assertEquals(0xFFC86432, screen.blend(0x00FFFFFF, 0xFFC86432));
	}
}
