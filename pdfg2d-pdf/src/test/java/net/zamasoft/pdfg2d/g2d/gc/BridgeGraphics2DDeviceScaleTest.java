package net.zamasoft.pdfg2d.g2d.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.geom.AffineTransform;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;

/**
 * {@link BridgeGraphics2D#setDeviceScale(double)} (2026-11-26): Batik rasterizes a filter at the resolution
 * {@link BridgeGraphics2D#getTransform()} reports, so that transform carries the device scale, and
 * {@link BridgeGraphics2D#setTransform(AffineTransform)} takes it off again: the GC sees the same drawing as before.
 */
class BridgeGraphics2DDeviceScaleTest {
	/** A GC that records the transforms it is given since its last reset. */
	private static GC recordingGC(final List<AffineTransform> transforms) {
		return (GC) Proxy.newProxyInstance(GC.class.getClassLoader(), new Class<?>[] { GC.class }, (p, m, a) -> {
			switch (m.getName()) {
			case "begin":
				return (GC.State) () -> {
				};
			case "resetState":
				transforms.clear();
				return null;
			case "transform":
				transforms.add(new AffineTransform((AffineTransform) a[0]));
				return null;
			default:
				final Class<?> r = m.getReturnType();
				return r == boolean.class ? false : r.isPrimitive() && r != void.class ? 0 : null;
			}
		});
	}

	private static AffineTransform product(final List<AffineTransform> transforms) {
		final AffineTransform at = new AffineTransform();
		for (final AffineTransform t : transforms) {
			at.concatenate(t);
		}
		return at;
	}

	@Test
	void transformCarriesTheDeviceScale() {
		final List<AffineTransform> gc = new ArrayList<>();
		final BridgeGraphics2D g = new BridgeGraphics2D(recordingGC(gc));
		g.setDeviceScale(4);
		g.translate(10, 0);
		final AffineTransform device = g.getTransform();
		assertEquals(40, device.getTranslateX(), 1e-9);
		assertEquals(4, device.getScaleX(), 1e-9);
		assertEquals(AffineTransform.getTranslateInstance(10, 0), product(gc));
	}

	/**
	 * The raster bounds are reported as the clip but are no clip of the drawing: Batik's save and restore of the clip
	 * (getClip, then setClip with what it got, nested) clips nothing in the GC.
	 */
	@Test
	void rasterBoundsAreNoClip() {
		final List<java.awt.Shape> clips = new ArrayList<>();
		final GC gc = (GC) Proxy.newProxyInstance(GC.class.getClassLoader(), new Class<?>[] { GC.class },
				(p, m, a) -> {
					if (m.getName().equals("begin")) {
						return (GC.State) () -> {
						};
					}
					if (m.getName().equals("clip")) {
						clips.add((java.awt.Shape) a[0]);
					}
					final Class<?> r = m.getReturnType();
					return r == boolean.class ? false : r.isPrimitive() && r != void.class ? 0 : null;
				});
		final BridgeGraphics2D g = new BridgeGraphics2D(gc);
		g.setRasterBounds(new java.awt.Rectangle(0, 0, 10, 10));
		final java.awt.Shape outer = g.getClip();
		assertEquals(new java.awt.Rectangle(0, 0, 10, 10), outer.getBounds());
		g.translate(5, 5);
		final java.awt.Shape inner = g.getClip();
		assertEquals(new java.awt.Rectangle(-5, -5, 10, 10), inner.getBounds());
		g.setClip(inner);
		g.translate(-5, -5);
		g.setClip(outer);
		assertEquals(List.of(), clips);
		// a clip of the drawing still clips, and is reported within the raster bounds
		g.clip(new java.awt.Rectangle(5, 0, 10, 10));
		assertEquals(1, clips.size());
		assertEquals(new java.awt.Rectangle(5, 0, 5, 10), g.getClip().getBounds());
	}

	/** Batik's round trip: what getTransform gave back to setTransform draws as before, in the GC's units. */
	@Test
	void setTransformTakesTheScaleOff() {
		final List<AffineTransform> gc = new ArrayList<>();
		final BridgeGraphics2D g = new BridgeGraphics2D(recordingGC(gc));
		g.setDeviceScale(4);
		g.translate(10, 0);
		final AffineTransform saved = g.getTransform();
		g.scale(2, 2);
		g.setTransform(saved);
		assertEquals(saved, g.getTransform());
		assertEquals(AffineTransform.getTranslateInstance(10, 0), product(gc));
		// the identity of the device is a quarter of a unit
		g.setTransform(new AffineTransform());
		assertEquals(AffineTransform.getScaleInstance(0.25, 0.25), product(gc));
	}
}
