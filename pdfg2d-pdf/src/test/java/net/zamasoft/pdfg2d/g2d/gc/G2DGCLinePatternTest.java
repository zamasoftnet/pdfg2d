package net.zamasoft.pdfg2d.g2d.gc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.paint.Paint;

/**
 * Verifies that the line pattern of a solid stroke can be retrieved (2026-08-30).
 *
 * <p>
 * {@code BasicStroke.getDashArray()} returns null for a solid stroke. Treating it directly
 * as an array always caused Java2D output to fail when saving and restoring the line pattern
 * (for example, in {@code FontUtils.drawText} for synthetic bold).
 */
class G2DGCLinePatternTest {
	private static G2DGC gc() {
		final BufferedImage canvas = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = canvas.createGraphics();
		return new G2DGC(g, null);
	}

	@Test
	void solidStrokeReadsBackAsSolid() {
		final G2DGC gc = gc();
		assertArrayEquals(GC.STROKE_SOLID, gc.getLinePattern(), "既定の実線");

		gc.setLinePattern(new double[] { 3, 2 });
		assertArrayEquals(new double[] { 3, 2 }, gc.getLinePattern(), "破線を設定した後");

		gc.setLinePattern(GC.STROKE_SOLID);
		assertArrayEquals(GC.STROKE_SOLID, gc.getLinePattern(), "実線へ戻した後");

		gc.setLinePattern(null);
		assertArrayEquals(GC.STROKE_SOLID, gc.getLinePattern(), "nullで実線に戻した後");
	}

	/** Colors are never {@code null}, even before they are explicitly set (the same contract as PDF output). */
	@Test
	void paintsDefaultToBlack() {
		final G2DGC gc = gc();
		assertNotNull(gc.getStrokePaint(), "既定の線の色");
		assertNotNull(gc.getFillPaint(), "既定の塗りの色");
	}

	/**
	 * Exercises the minimal path for saving and restoring the line pattern and colors (synthetic bold).
	 * Uses the same save/restore order as {@code FontUtils.drawText} for synthetic bold.
	 */
	@Test
	void saveAndRestoreAroundSolidStroke() {
		final G2DGC gc = gc();
		final double[] savedPattern = gc.getLinePattern();
		final double savedWidth = gc.getLineWidth();
		final GC.LineJoin savedJoin = gc.getLineJoin();
		final Paint savedStroke = gc.getStrokePaint();
		final float savedAlpha = gc.getStrokeAlpha();

		gc.setLineWidth(0.5);
		gc.setLineJoin(GC.LineJoin.ROUND);
		gc.setLinePattern(GC.STROKE_SOLID);
		gc.setStrokePaint(gc.getFillPaint());
		gc.setStrokeAlpha(gc.getFillAlpha());

		gc.setLineWidth(savedWidth);
		gc.setLineJoin(savedJoin);
		gc.setLinePattern(savedPattern);
		gc.setStrokePaint(savedStroke);
		gc.setStrokeAlpha(savedAlpha);

		assertArrayEquals(GC.STROKE_SOLID, gc.getLinePattern());
		assertNotNull(gc.getStrokePaint());
	}
}
