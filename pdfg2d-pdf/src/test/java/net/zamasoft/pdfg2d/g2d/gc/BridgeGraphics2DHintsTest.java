package net.zamasoft.pdfg2d.g2d.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.RenderingHints;
import java.lang.reflect.Proxy;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;

/**
 * {@link BridgeGraphics2D#getRenderingHints()} gives a copy (2026-11-26): Batik saves the hints with it, adds a node's
 * own and puts the saved ones back after the node. With the live map the node's hints stayed on its later siblings
 * (codex: a crispEdges turned antialiasing off for the rest of the SVG).
 */
class BridgeGraphics2DHintsTest {
	private static BridgeGraphics2D graphics() {
		final GC gc = (GC) Proxy.newProxyInstance(GC.class.getClassLoader(), new Class<?>[] { GC.class },
				(p, m, a) -> {
					if (m.getName().equals("begin")) {
						return (GC.State) () -> {
						};
					}
					final Class<?> r = m.getReturnType();
					return r == boolean.class ? false : r.isPrimitive() && r != void.class ? 0 : null;
				});
		return new BridgeGraphics2D(gc);
	}

	@Test
	void savedHintsAreRestored() {
		final BridgeGraphics2D g = graphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// Batik's AbstractGraphicsNode.paint for a node with shape-rendering: crispEdges
		final RenderingHints saved = g.getRenderingHints();
		g.addRenderingHints(Map.of(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF));
		assertEquals(RenderingHints.VALUE_ANTIALIAS_OFF, g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
		g.setRenderingHints(saved);
		assertEquals(RenderingHints.VALUE_ANTIALIAS_ON, g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
	}

	@Test
	void aChildHasItsOwnHints() {
		final BridgeGraphics2D g = graphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final BridgeGraphics2D child = (BridgeGraphics2D) g.create();
		try {
			child.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			assertEquals(RenderingHints.VALUE_ANTIALIAS_ON, g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
		} finally {
			child.dispose();
		}
	}
}
