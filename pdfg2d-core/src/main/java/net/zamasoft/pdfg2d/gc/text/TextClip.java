package net.zamasoft.pdfg2d.gc.text;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.NoOpGC;
import net.zamasoft.pdfg2d.gc.font.FontManager;

/**
 * Text that clips painting to the shape of its glyphs (CSS {@code background-clip: text}), for
 * {@link GC#clipToText(TextClip, GC.Painter)}.
 *
 * <p>
 * The clip is a list of text runs. Each run is placed by a transform from the run's own space, where
 * {@link GC#drawText(Text, double, double)} would draw it at (0, 0), to the user space of the graphics context that
 * receives the clip. The runs keep the text itself rather than glyph outlines, so a backend can clip with glyphs it
 * cannot outline locally: PDF shows them with a text rendering mode that adds them to the clip.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class TextClip {
	/**
	 * One text run of a clip.
	 *
	 * @param text      the text
	 * @param transform the transform from the run's space (its origin at (0, 0)) to the user space of the
	 *                  receiving graphics context
	 */
	public record Run(Text text, AffineTransform transform) {
		/**
		 * Creates a run, copying the transform.
		 *
		 * @param text      the text
		 * @param transform the transform that places the text
		 */
		public Run {
			transform = new AffineTransform(transform);
		}

		/**
		 * Returns a copy of the transform that places the text.
		 *
		 * @return the transform
		 */
		@Override
		public AffineTransform transform() {
			return new AffineTransform(this.transform);
		}
	}

	private final List<Run> runs = new ArrayList<>();

	/**
	 * Adds a text run. Text without glyphs is ignored.
	 *
	 * @param text      the text
	 * @param transform the transform from the run's space to the user space of the receiving graphics context
	 */
	public void add(final Text text, final AffineTransform transform) {
		if (text.getGlyphCount() > 0) {
			this.runs.add(new Run(text, transform));
		}
	}

	/**
	 * Returns the runs in the order they were added.
	 *
	 * @return an unmodifiable view of the runs
	 */
	public List<Run> getRuns() {
		return Collections.unmodifiableList(this.runs);
	}

	/**
	 * Returns whether the clip has no runs, so that nothing would be painted inside it.
	 *
	 * @return true if there are no runs
	 */
	public boolean isEmpty() {
		return this.runs.isEmpty();
	}

	/**
	 * Returns the union of the glyph outlines of the given runs. The outlines are the ones that each font's
	 * {@link net.zamasoft.pdfg2d.font.Font#drawTo} fills on a graphics context other than PDF, the same outlines
	 * that Java2D and SVG output paint for the text. Glyphs drawn as images (emoji images) have no outline and are
	 * left out.
	 *
	 * @param runs the runs
	 * @param fm   the font manager
	 * @return the outlines in the user space of the receiving graphics context
	 * @throws GraphicsException if a font cannot draw its glyphs
	 */
	public static Shape outline(final List<Run> runs, final FontManager fm) throws GraphicsException {
		final OutlineGC gc = new OutlineGC(fm);
		for (final Run run : runs) {
			try (final GC.State state = gc.begin()) {
				gc.transform(run.transform);
				gc.drawText(run.text, 0, 0);
			}
		}
		return gc.path;
	}

	/** Collects what fonts fill into one path; paints nothing. */
	private static final class OutlineGC extends NoOpGC {
		final GeneralPath path = new GeneralPath(Path2D.WIND_NON_ZERO);

		OutlineGC(final FontManager fm) {
			super(fm);
		}

		@Override
		public void fill(final Shape shape) {
			this.path.append(this.transform.createTransformedShape(shape), false);
		}

		@Override
		public void fillDraw(final Shape shape) {
			// The stroke of synthetic bold widens a glyph; the clip keeps its fill only, as PDF's clip does
			this.fill(shape);
		}

		@Override
		public void drawText(final Text text, final double x, final double y) throws GraphicsException {
			try (final GC.State state = this.begin()) {
				this.transform(AffineTransform.getTranslateInstance(x, y));
				((FontMetricsImpl) text.getFontMetrics()).getFont().drawTo(this, text);
			} catch (final IOException e) {
				throw new GraphicsException(e);
			}
		}
	}
}
