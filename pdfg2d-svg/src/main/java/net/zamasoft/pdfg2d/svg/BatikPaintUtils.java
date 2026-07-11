package net.zamasoft.pdfg2d.svg;

import org.apache.batik.ext.awt.LinearGradientPaint;
import org.apache.batik.ext.awt.MultipleGradientPaint;
import org.apache.batik.ext.awt.RadialGradientPaint;

import net.zamasoft.pdfg2d.g2d.util.G2DUtils;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;

/**
 * Converts Batik's gradient paint classes to this library's paints. Batik
 * predates {@code java.awt.MultipleGradientPaint} and ships its own parallel
 * hierarchy; keeping the conversion here confines the Batik dependency to
 * the SVG module. (Historically this lived in {@code G2DUtils}, where the
 * Batik types shadowed the identically-named standard classes and silently
 * broke plain {@code java.awt} gradients.)
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
final class BatikPaintUtils {

	private BatikPaintUtils() {
		// static use only
	}

	/**
	 * Converts a Batik gradient paint, or returns {@code null} for paints
	 * that are not Batik gradients (or use unsupported cycle methods).
	 *
	 * @param paint the AWT paint received from Batik
	 * @return the converted paint, or {@code null}
	 */
	static Paint fromBatikPaint(final java.awt.Paint paint) {
		if (paint instanceof final RadialGradientPaint gpaint) {
			final var fs = gpaint.getFractions();
			final var fractions = new double[fs.length];
			for (var i = 0; i < fs.length; ++i) {
				fractions[i] = fs[i];
			}
			final var cs = gpaint.getColors();
			final var colors = new Color[cs.length];
			for (var i = 0; i < cs.length; ++i) {
				colors[i] = G2DUtils.fromAwtColor(cs[i]);
			}
			return new RadialGradient(gpaint.getCenterPoint().getX(), gpaint.getCenterPoint().getY(),
					gpaint.getRadius(), gpaint.getFocusPoint().getX(), gpaint.getFocusPoint().getY(), fractions,
					colors, gpaint.getTransform());
		}
		if (paint instanceof final LinearGradientPaint gpaint) {
			if (gpaint.getCycleMethod() != MultipleGradientPaint.NO_CYCLE) {
				return null;
			}
			final var fs = gpaint.getFractions();
			final var fractions = new double[fs.length];
			for (var i = 0; i < fs.length; ++i) {
				fractions[i] = fs[i];
			}
			final var cs = gpaint.getColors();
			final var colors = new Color[cs.length];
			for (var i = 0; i < cs.length; ++i) {
				colors[i] = G2DUtils.fromAwtColor(cs[i]);
			}
			return new LinearGradient(gpaint.getStartPoint().getX(), gpaint.getStartPoint().getY(),
					gpaint.getEndPoint().getX(), gpaint.getEndPoint().getY(), fractions, colors,
					gpaint.getTransform());
		}
		return null;
	}
}
