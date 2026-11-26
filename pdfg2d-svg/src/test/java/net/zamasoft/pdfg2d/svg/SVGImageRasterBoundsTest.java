package net.zamasoft.pdfg2d.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.Test;

/**
 * {@link SVGImage#rasterBounds} (2026-11-26): the image's box widened as far as the pixel cap allows at the device
 * scale, so that Batik neither rasterizes past the cap nor leaves out vector content outside an SVG with overflow:
 * visible.
 */
class SVGImageRasterBoundsTest {
	@Test
	void widenedToThePixelCap() {
		// a 100-unit SVG at 300 dpi and a point a unit: about 440 units on each side
		final double scale = 300 / 72.0;
		final Rectangle2D r = SVGImage.rasterBounds(100, 100, scale);
		assertEquals(SVGImage.MAX_DEVICE_PIXELS, r.getWidth() * r.getHeight() * scale * scale,
				SVGImage.MAX_DEVICE_PIXELS * 1e-9);
		assertEquals(r.getCenterX(), 50, 1e-9);
		assertEquals(r.getCenterY(), 50, 1e-9);
		assertTrue(r.getMinX() < -400 && r.getMinX() > -500, "margin " + -r.getMinX());
	}

	@Test
	void notNarrowerThanTheImage() {
		// the device scale at the cap for the image itself: no margin
		final double scale = Math.sqrt(SVGImage.MAX_DEVICE_PIXELS / (300.0 * 200.0));
		assertEquals(new Rectangle2D.Double(0, 0, 300, 200), SVGImage.rasterBounds(300, 200, scale));
		// a scale past the cap (never given by deviceScale): still the box
		assertEquals(new Rectangle2D.Double(0, 0, 300, 200), SVGImage.rasterBounds(300, 200, scale * 2));
	}
}
