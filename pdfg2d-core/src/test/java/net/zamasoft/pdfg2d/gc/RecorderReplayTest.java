package net.zamasoft.pdfg2d.gc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.text.GlyphAdvances;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.Text;

/** Tests deferred replay invariants used by filter capture groups. */
public class RecorderReplayTest {
	@Test
	public void testMutableArgumentsAreSnapshottedWhenRecorded() {
		final var recorder = new RecorderGC(null);
		final var transform = AffineTransform.getTranslateInstance(12, 18);
		final double[] pattern = { 2, 3 };
		final var shape = new Path2D.Double(new Rectangle2D.Double(1, 2, 3, 4));

		recorder.transform(transform);
		recorder.setLinePattern(pattern);
		recorder.fill(shape);

		transform.setToTranslation(120, 180);
		pattern[0] = 200;
		shape.reset();
		shape.append(new Rectangle2D.Double(100, 200, 300, 400), false);

		final var target = new ReplayGC();
		recorder.getPage().drawTo(target);
		assertEquals(12, target.getTransform().getTranslateX());
		assertEquals(18, target.getTransform().getTranslateY());
		assertArrayEquals(new double[] { 2, 3 }, target.getLinePattern());
		assertEquals(new Rectangle2D.Double(1, 2, 3, 4), target.filled.getBounds2D());
	}

	@Test
	public void testNestedRecorderImagesAreMaterializedAsLayers() {
		final var recorder = new RecorderGC(null);
		final GroupImageGC plainGroup = recorder.createGroupImage(20, 30);
		plainGroup.fill(new Rectangle2D.Double(1, 2, 3, 4));
		recorder.drawImage(plainGroup.finish());

		final GroupImageGC effectsGroup = recorder.createGroupImage(40, 50);
		effectsGroup.fill(new Rectangle2D.Double(5, 6, 7, 8));
		recorder.drawImage(effectsGroup.finish(), new GroupEffects(null, 0, null, .5));

		final var target = new CountingGC();
		recorder.getPage().drawTo(target);
		assertEquals(2, target.createdGroups);
		assertEquals(1, target.plainImages);
		assertEquals(1, target.effectsImages);
	}

	@Test
	public void testCaptureRecorderAdvertisesAllCapabilities() {
		final var ordinary = new RecorderGC(null);
		final var capture = new RecorderGC.RecorderGroupImageGC(null, 10, 10, true);
		for (final var capability : GC.Capability.values()) {
			assertFalse(ordinary.supports(capability), capability.toString());
			assertTrue(capture.supports(capability), capability.toString());
		}

		final GroupImageGC nested = capture.createGroupImage(5, 5);
		for (final var capability : GC.Capability.values()) {
			assertTrue(nested.supports(capability), "nested " + capability);
		}
	}

	@Test
	public void testContentBoundsTrackTransformRestoreAndText() {
		final var group = new RecorderGC.RecorderGroupImageGC(null, 1000, 1000, true);
		try (final var state = group.begin()) {
			group.transform(AffineTransform.getTranslateInstance(10, 5));
			group.fill(new Rectangle2D.Double(1, 2, 3, 4));
		}
		group.drawText(new BoundsText(4, 3, 1), 20, 20);

		final var image = (RecorderGC.RecorderImage) group.finish();
		assertEquals(1000, image.getWidth());
		assertEquals(1000, image.getHeight());
		assertEquals(new Rectangle2D.Double(11, 7, 13, 17), image.getContentBounds());
	}

	private static final class ReplayGC extends NoOpGC {
		Shape filled;

		ReplayGC() {
			super(null);
		}

		@Override
		public void fill(final Shape shape) {
			this.filled = shape;
		}
	}

	private static final class CountingGC extends NoOpGC {
		int createdGroups;
		int plainImages;
		int effectsImages;

		CountingGC() {
			super(null);
		}

		@Override
		public GroupImageGC createGroupImage(final double width, final double height) {
			++this.createdGroups;
			return new NoOpGroupImageGC(this.getFontManager(), width, height);
		}

		@Override
		public void drawImage(final Image image) {
			++this.plainImages;
		}

		@Override
		public void drawImage(final Image image, final GroupEffects effects) {
			++this.effectsImages;
		}
	}

	private record BoundsText(double advance, double ascent, double descent) implements Text {
		@Override
		public net.zamasoft.pdfg2d.gc.font.FontStyle getFontStyle() {
			return null;
		}

		@Override
		public net.zamasoft.pdfg2d.gc.font.FontMetrics getFontMetrics() {
			return null;
		}

		@Override
		public int getCharOffset() {
			return 0;
		}

		@Override
		public double getAdvance() {
			return this.advance;
		}

		@Override
		public double getAscent() {
			return this.ascent;
		}

		@Override
		public double getDescent() {
			return this.descent;
		}

		@Override
		public char[] getChars() {
			return new char[0];
		}

		@Override
		public int getCharCount() {
			return 0;
		}

		@Override
		public int[] getGlyphIds() {
			return new int[0];
		}

		@Override
		public byte[] getClusterLengths() {
			return new byte[0];
		}

		@Override
		public int getGlyphCount() {
			return 0;
		}

		@Override
		public double getLetterSpacing() {
			return 0;
		}

		@Override
		public void toGlyphs(final GlyphHandler gh) {
			// Bounds tracking only needs the metrics above.
		}

		@Override
		public GlyphAdvances xAdvances() {
			return null;
		}
	}
}
