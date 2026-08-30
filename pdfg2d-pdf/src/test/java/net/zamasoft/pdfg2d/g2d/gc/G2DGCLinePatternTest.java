package net.zamasoft.pdfg2d.g2d.gc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.paint.Paint;

/**
 * 実線の線種を読み出せることの検査(2026-08-30)。
 *
 * <p>
 * {@code BasicStroke.getDashArray()}は実線のときnullを返す。これをそのまま
 * 配列として扱うと、線種を保存して元へ戻す処理(擬似ボールドの
 * {@code FontUtils.drawText}など)がJava2D出力で必ず落ちていた。
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

	/** 明示的に設定する前でも色は{@code null}にならない(PDF出力と同じ契約)。 */
	@Test
	void paintsDefaultToBlack() {
		final G2DGC gc = gc();
		assertNotNull(gc.getStrokePaint(), "既定の線の色");
		assertNotNull(gc.getFillPaint(), "既定の塗りの色");
	}

	/**
	 * 線種と色を保存・復元する経路(擬似ボールド)を最小の形でなぞる。
	 * {@code FontUtils.drawText}が擬似ボールドのために行う保存・復元と同じ順番。
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
