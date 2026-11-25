package net.zamasoft.pdfg2d.g2d.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;

/**
 * The GC states that {@link BridgeGraphics2D#create()} opens are closed on the drawing thread, newest first, also for
 * children never disposed (2026-11-26). {@link java.awt.Graphics#finalize()} disposed them on the finalizer thread
 * while the drawing thread used the same GC: a page of SVG images failed now and then, and the same document gave
 * different PDFs.
 */
class BridgeGraphics2DStateTest {
	/** A GC that records which of its states are closed, in order. */
	private static GC recordingGC(final List<String> log) {
		final int[] next = { 0 };
		return (GC) Proxy.newProxyInstance(GC.class.getClassLoader(), new Class<?>[] { GC.class }, (p, m, a) -> {
			if (m.getName().equals("begin")) {
				final String name = "s" + next[0]++;
				log.add("open " + name);
				return (GC.State) () -> log.add("close " + name);
			}
			final Class<?> r = m.getReturnType();
			return r == boolean.class ? false : r.isPrimitive() && r != void.class ? 0 : null;
		});
	}

	@Test
	void undisposedChildIsClosedBeforeItsParent() {
		final List<String> log = new ArrayList<>();
		final BridgeGraphics2D root = new BridgeGraphics2D(recordingGC(log));
		final var a = root.create();
		a.create(); // never disposed
		a.dispose();
		assertEquals(List.of("open s0", "open s1", "close s1", "close s0"), log);
		root.dispose();
		assertEquals(4, log.size());
	}

	@Test
	void rootClosesEveryChildLeftOpen() {
		final List<String> log = new ArrayList<>();
		final BridgeGraphics2D root = new BridgeGraphics2D(recordingGC(log));
		final var a = root.create();
		final var b = root.create();
		b.dispose();
		final var c = a.create();
		// a and c are never disposed
		root.dispose();
		assertEquals(List.of("open s0", "open s1", "close s1", "open s2", "close s2", "close s0"), log);
		// disposing them later does nothing
		c.dispose();
		a.dispose();
		assertEquals(6, log.size());
	}

	@Test
	@SuppressWarnings({ "deprecation", "removal" })
	void finalizeDoesNotCloseAnything() {
		final List<String> log = new ArrayList<>();
		final BridgeGraphics2D root = new BridgeGraphics2D(recordingGC(log));
		final var a = (BridgeGraphics2D) root.create();
		a.finalize();
		assertEquals(List.of("open s0"), log);
		a.dispose();
		assertEquals(List.of("open s0", "close s0"), log);
	}
}
