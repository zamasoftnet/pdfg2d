package net.zamasoft.pdfg2d.font.otf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.GlyphBounds;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;

/** Bounds stay in outline coordinates, independent of layout metrics. */
class OpenTypeGlyphBoundsTest {
	private static final File PGOTHIC = new File("src/test/resources/pgothic-vert-subset.ttf");
	private static final File IPAEX = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");

	private static final class CountingFont extends OpenTypeFontImpl {
		private static final long serialVersionUID = 1L;
		private final AtomicInteger shapeCalls = new AtomicInteger();

		private CountingFont(final OpenTypeFontSource source) {
			super(source);
		}

		@Override
		public Shape getShapeByGID(final int gid) {
			this.shapeCalls.incrementAndGet();
			return super.getShapeByGID(gid);
		}
	}

	private static void assertBounds(final GlyphBounds expected, final GlyphBounds actual, final double tolerance) {
		assertNotNull(actual);
		assertEquals(expected.minX(), actual.minX(), tolerance);
		assertEquals(expected.minY(), actual.minY(), tolerance);
		assertEquals(expected.maxX(), actual.maxX(), tolerance);
		assertEquals(expected.maxY(), actual.maxY(), tolerance);
	}

	@Test
	void bracketBoundsUseTheSelectedHorizontalOrVerticalGlyph() throws Exception {
		for (final var direction : new Direction[] { Direction.LTR, Direction.TB }) {
			final var font = new CountingFont(new OpenTypeFontSource(PGOTHIC, 0, direction));
			final int gid = font.toGID('「');
			assertEquals(direction == Direction.TB ? 28 : 15, gid);
			// The design's fontTools measurement describes the vertical glyph.
			final var expected = direction == Direction.TB ? new GlyphBounds(222, -234, 948, 38)
					: new GlyphBounds(216, -828, 488, -102);
			final var bounds = font.getGlyphBounds(gid);
			assertBounds(expected, bounds, 0.5);
			assertSame(bounds, font.getGlyphBounds(gid));
			assertEquals(1, font.shapeCalls.get());
		}
	}

	@Test
	void nonThousandUpmPreservesFractionalCoordinates() throws Exception {
		final var source = new OpenTypeFontSource(IPAEX, 0, Direction.TB);
		assertEquals(2048, source.getUnitsPerEm());
		final var font = new CountingFont(source);
		final int gid = font.toGID('「');
		assertEquals(7497, gid);
		// Source glyf bounds scaled by 1000 / 2048, with y inverted.
		final var expected = new GlyphBounds(226.5625, -209.9609375, 946.2890625, 37.109375);
		final var bounds = font.getGlyphBounds(gid);
		assertBounds(expected, bounds, 0.00001);
		assertNotEquals(Math.rint(bounds.minY()), bounds.minY());
		assertSame(bounds, font.getGlyphBounds(gid));
		assertEquals(1, font.shapeCalls.get());
	}

	@Test
	void blankSpaceIsCachedAsNull() throws Exception {
		for (final var direction : new Direction[] { Direction.LTR, Direction.TB }) {
			final var source = new OpenTypeFontSource(IPAEX, 0, direction);
			final var font = new CountingFont(source);
			final int gid = font.toGID(' ');
			assertNotEquals(0, gid, "test a real space, not .notdef");
			// TrueType may omit the Glyph object entirely for a contourless space.
			final var glyph = source.getOpenTypeFont().getGlyph(gid);
			assertTrue(glyph == null || glyph.isBlank());
			assertNull(font.getGlyphBounds(gid));
			assertNull(font.getGlyphBounds(gid));
			assertEquals(1, font.shapeCalls.get());
		}
	}

	@Test
	void concurrentQueriesComputeEachGlyphOnlyOnce() throws Exception {
		final var font = new CountingFont(new OpenTypeFontSource(IPAEX, 0, Direction.LTR));
		final int bracket = font.toGID('「');
		final int space = font.toGID(' ');
		final var start = new CountDownLatch(1);
		try (final var executor = Executors.newFixedThreadPool(8)) {
			final var results = new ArrayList<Future<GlyphBounds>>();
			for (int i = 0; i < 32; ++i) {
				results.add(executor.submit(() -> {
					assertTrue(start.await(10, TimeUnit.SECONDS));
					final var bounds = font.getGlyphBounds(bracket);
					assertNull(font.getGlyphBounds(space));
					return bounds;
				}));
			}
			start.countDown();
			final var expected = results.getFirst().get(10, TimeUnit.SECONDS);
			assertNotNull(expected);
			for (final var result : results) {
				assertSame(expected, result.get(10, TimeUnit.SECONDS));
			}
			assertEquals(2, font.shapeCalls.get());
		}
	}

	private static ShapedFont defaultFont(final Shape shape, final AtomicInteger calls) {
		return (ShapedFont) Proxy.newProxyInstance(ShapedFont.class.getClassLoader(),
				new Class<?>[] { ShapedFont.class }, (proxy, method, args) -> {
					if (method.getName().equals("getShapeByGID")) {
						calls.incrementAndGet();
						return shape;
					}
					return InvocationHandler.invokeDefault(proxy, method, args);
				});
	}

	@Test
	void defaultMethodDoesNotCacheOrReduceDoublePrecision() {
		final var shape = new Rectangle2D.Double(0.123456789, -2.987654321, 3.123456789, 4.987654321);
		final var calls = new AtomicInteger();
		final var font = defaultFont(shape, calls);
		final var first = font.getGlyphBounds(1);
		assertBounds(new GlyphBounds(shape.getMinX(), shape.getMinY(), shape.getMaxX(), shape.getMaxY()), first, 0);
		shape.setRect(1, 2, 3, 4);
		assertEquals(new GlyphBounds(1, 2, 4, 6), font.getGlyphBounds(1));
		assertEquals(0.123456789, first.minX(), 0, "returned bounds cannot be changed through the path");
		assertEquals(2, calls.get());
	}

	@Test
	void defaultMethodRejectsNullEmptyAndMoveOnlyPaths() {
		final var moves = new Path2D.Double();
		moves.moveTo(10, 20);
		moves.closePath();
		moves.moveTo(30, 40);
		moves.closePath();
		assertTrue(!moves.getBounds2D().isEmpty(), "move-only paths can have nonempty bounds");
		for (final var shape : new Shape[] { null, new Path2D.Double(), moves,
				new Rectangle2D.Double(0, 0, 0, 10) }) {
			final var calls = new AtomicInteger();
			final var font = defaultFont(shape, calls);
			assertNull(font.getGlyphBounds(1));
			assertNull(font.getGlyphBounds(1));
			assertEquals(2, calls.get(), "the default method must not cache even blank glyphs");
		}
	}
}
