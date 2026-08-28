package net.zamasoft.pdfg2d.g2d.gc;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;
import net.zamasoft.pdfg2d.g2d.util.G2DUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.ConicGradient;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.Pattern;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * A {@link GC} implementation that delegates rendering operations to a Java2D
 * {@link java.awt.Graphics2D} context. This class bridges the pdfg2d graphics
 * abstraction layer with the AWT/Java2D painting API, translating internal
 * paint, stroke, transform, and image operations into equivalent {@code Graphics2D}
 * calls.
 *
 * <p>Graphics state (clip, transform, stroke, paints, alpha, text mode) is
 * managed via an explicit push/pop stack: {@link #begin()} pushes a state and
 * closing the returned {@link State} pops it.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class G2DGC implements GC {
	/**
	 * Snapshot of all mutable graphics-state fields belonging to a {@link G2DGC}.
	 * Instances are pushed onto the state stack by {@link G2DGC#begin()} and
	 * popped (restored) when the returned {@link State} is closed.
	 */
	protected static class GraphicsState {
		public final Shape clip;

		public final AffineTransform transform;

		public final Stroke stroke;

		public final Paint strokePaint;

		public final Paint fillPaint;

		public final java.awt.Color strokeColor;

		public final java.awt.Paint awtFillPaint;

		public final AffineTransform fillAt, strokeAt;

		public final float fillAlpha;

		public final TextMode textMode;

		public final Composite composite;

		/**
		 * Captures the current graphics state from the given {@link G2DGC}.
		 *
		 * @param gc the graphics context whose state is to be saved
		 */
		public GraphicsState(G2DGC gc) {
			Graphics2D g = gc.g;
			this.transform = g.getTransform();
			this.clip = g.getClip();
			this.strokePaint = gc.strokePaint;
			this.fillPaint = gc.fillPaint;
			this.stroke = g.getStroke();
			this.strokeColor = g.getColor();
			this.composite = g.getComposite();
			this.awtFillPaint = gc.awtFillPaint;
			this.fillAt = gc.fillAt;
			this.fillAlpha = gc.fillAlpha;
			this.strokeAt = gc.strokeAt;
			this.textMode = gc.textMode;
		}

		/**
		 * Copies a state while translating its device space. The user-space clip and
		 * paints remain unchanged; only the user-to-device transform gains the raster
		 * origin shift used by an off-screen group image.
		 */
		private GraphicsState(final GraphicsState state, final AffineTransform deviceShift) {
			this.clip = state.clip;
			this.transform = new AffineTransform(deviceShift);
			this.transform.concatenate(state.transform);
			this.stroke = state.stroke;
			this.strokePaint = state.strokePaint;
			this.fillPaint = state.fillPaint;
			this.strokeColor = state.strokeColor;
			this.awtFillPaint = state.awtFillPaint;
			this.fillAt = state.fillAt;
			this.strokeAt = state.strokeAt;
			this.fillAlpha = state.fillAlpha;
			this.textMode = state.textMode;
			this.composite = state.composite;
		}

		private GraphicsState shifted(final AffineTransform deviceShift) {
			return new GraphicsState(this, deviceShift);
		}

		/**
		 * Restores the previously captured graphics state into the given {@link G2DGC}.
		 *
		 * @param gc the graphics context to restore state into
		 */
		public void restore(G2DGC gc) {
			Graphics2D g = gc.g;
			g.setTransform(this.transform);
			g.setClip(this.clip);
			g.setStroke(this.stroke);
			g.setColor(this.strokeColor);
			g.setComposite(this.composite);
			gc.fillPaint = this.fillPaint;
			gc.strokePaint = this.strokePaint;
			gc.awtFillPaint = this.awtFillPaint;
			gc.fillAt = this.fillAt;
			gc.fillAlpha = this.fillAlpha;
			gc.strokeAt = this.strokeAt;
			gc.textMode = this.textMode;
		}
	}

	protected final Graphics2D g;

	protected boolean drewAnything = false;

	protected Paint strokePaint;

	protected Paint fillPaint;

	protected java.awt.Paint awtFillPaint;

	protected AffineTransform fillAt, strokeAt;

	protected float fillAlpha = 1;

	protected TextMode textMode = TextMode.FILL;

	protected ArrayList<GraphicsState> stack = new ArrayList<GraphicsState>();

	protected final FontManager fm;

	/**
	 * Constructs a new {@code G2DGC} wrapping the given {@link Graphics2D} context.
	 * The stroke is initialised to a 1-pixel butt-cap miter-join {@link java.awt.BasicStroke}.
	 *
	 * @param g  the underlying Java2D graphics context; must not be {@code null}
	 * @param fm the font manager used for text rendering; must not be {@code null}
	 */
	public G2DGC(Graphics2D g, FontManager fm) {
		this.g = g;
		this.g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		this.awtFillPaint = this.g.getPaint();
		this.fm = fm;
	}

	/**
	 * Returns the font manager associated with this graphics context.
	 *
	 * @return the {@link FontManager}
	 */
	public FontManager getFontManager() {
		return this.fm;
	}

	/**
	 * Returns the underlying Java2D {@link Graphics2D} context.
	 *
	 * @return the wrapped {@code Graphics2D}
	 */
	public Graphics2D getGraphics2D() {
		return this.g;
	}

	/**
	 * Returns {@code true} if any rendering operation has been performed since the
	 * last top-level {@link #begin()} call.
	 *
	 * @return {@code true} if something has been drawn
	 */
	public boolean drewAnything() {
		return this.drewAnything;
	}

	/**
	 * Saves the current graphics state onto the internal stack and resets the
	 * {@code drewAnything} flag when the stack was empty before this call.
	 */
	public State begin() {
		if (this.stack.isEmpty()) {
			this.drewAnything = false;
		}
		this.stack.add(new GraphicsState(this));
		return new State() {
			private boolean closed;

			@Override
			public void close() {
				if (this.closed) {
					return;
				}
				this.closed = true;
				G2DGC.this.restoreState();
			}
		};
	}

	/**
	 * Pops the most recently saved graphics state from the stack and restores it;
	 * invoked exactly once when a {@link State} returned by {@link #begin()} is
	 * closed.
	 */
	private void restoreState() {
		GraphicsState state = (GraphicsState) this.stack.remove(this.stack.size() - 1);
		state.restore(this);
	}

	/**
	 * Sets the stroke line width.
	 *
	 * @param width the new line width in user-space units
	 */
	public void setLineWidth(double width) {
		BasicStroke stroke = (BasicStroke) this.g.getStroke();
		float fwidth = (float) width;
		this.g.setStroke(new BasicStroke(fwidth, stroke.getEndCap(), stroke.getLineJoin(), stroke.getMiterLimit(),
				stroke.getDashArray(), stroke.getDashPhase()));
	}

	public double getLineWidth() {
		return ((BasicStroke) this.g.getStroke()).getLineWidth();
	}

	public void setLinePattern(double[] pattern) {
		BasicStroke stroke = (BasicStroke) this.g.getStroke();
		float[] fpattern;
		if (pattern != null && pattern.length > 0) {
			fpattern = new float[pattern.length];
			for (int i = 0; i < pattern.length; ++i) {
				fpattern[i] = (float) pattern[i];
			}
		} else {
			fpattern = null;
		}
		this.g.setStroke(new BasicStroke(stroke.getLineWidth(), stroke.getEndCap(), stroke.getLineJoin(),
				stroke.getMiterLimit(), fpattern, stroke.getDashPhase()));
	}

	public double[] getLinePattern() {
		float[] da = ((BasicStroke) this.g.getStroke()).getDashArray();
		double[] pattern = new double[da.length];
		for (int i = 0; i < da.length; ++i) {
			pattern[i] = da[i];
		}
		return pattern;
	}

	public void setLineJoin(LineJoin lineJoin) {
		BasicStroke stroke = (BasicStroke) this.g.getStroke();
		this.g.setStroke(new BasicStroke(stroke.getLineWidth(), stroke.getEndCap(), lineJoin.code, stroke.getMiterLimit(),
				stroke.getDashArray(), stroke.getDashPhase()));
	}

	public LineJoin getLineJoin() {
		return G2DUtils.decodeLineJoin((short) ((BasicStroke) this.g.getStroke()).getLineJoin());
	}

	public void setLineCap(LineCap lineCap) {
		BasicStroke stroke = (BasicStroke) this.g.getStroke();
		this.g.setStroke(
				new BasicStroke(stroke.getLineWidth(), lineCap.code, stroke.getLineJoin(), stroke.getMiterLimit(),
						stroke.getDashArray(), stroke.getDashPhase()));
	}

	public LineCap getLineCap() {
		return G2DUtils.decodeLineCap((short) ((BasicStroke) this.g.getStroke()).getEndCap());
	}

	protected void setPaint(Paint paint, boolean fill) throws GraphicsException {
		final java.awt.Paint awtPaint;
		final AffineTransform at;

		switch (paint) {
			case Color color -> {
				awtPaint = G2DUtils.toAwtColor(color);
				at = null;
			}
			case Pattern pattern -> {
				awtPaint = G2DUtils.toAwtPaint(pattern, this);
				at = pattern.getTransform();
			}
			case LinearGradient linearGradient -> {
				awtPaint = G2DUtils.toAwtPaint(linearGradient);
				at = null;
			}
			case RadialGradient radialGradient -> {
				awtPaint = G2DUtils.toAwtPaint(radialGradient);
				at = null;
			}
			case ConicGradient conicGradient -> {
				// TODO(2026-08-29) 厳密な円錐Paintへ置換する。暫定は先頭色。
				awtPaint = G2DUtils.toAwtColor(conicGradient.colors()[0]);
				at = null;
			}
		}

		if (fill) {
			this.fillPaint = paint;
			this.awtFillPaint = awtPaint;
			this.fillAt = at;
		} else {
			this.g.setPaint(awtPaint);
			this.strokePaint = paint;
			this.strokeAt = at;
		}
	}

	public void setStrokePaint(Paint paint) throws GraphicsException {
		this.setPaint(paint, false);
	}

	public Paint getStrokePaint() {
		return this.strokePaint;
	}

	public void setFillPaint(Paint paint) throws GraphicsException {
		this.setPaint(paint, true);
	}

	public Paint getFillPaint() {
		return this.fillPaint;
	}

	public void setStrokeAlpha(float alpha) {
		if (alpha == 1) {
			this.g.setPaintMode();
			return;
		}
		AlphaComposite comp = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha);
		this.g.setComposite(comp);
	}

	public float getStrokeAlpha() {
		return ((AlphaComposite) this.g.getComposite()).getAlpha();
	}

	public void setFillAlpha(float alpha) {
		this.fillAlpha = alpha;
	}

	public float getFillAlpha() {
		return this.fillAlpha;
	}

	public void setTextMode(TextMode textMode) {
		this.textMode = textMode;
	}

	public TextMode getTextMode() {
		return this.textMode;
	}

	public void transform(AffineTransform at) {
		this.g.transform(at);
	}

	public AffineTransform getTransform() {
		return this.g.getTransform();
	}

	public void clip(Shape shape) {
		this.g.clip(shape);
	}

	public void resetState() {
		GraphicsState state = (GraphicsState) this.stack.get(this.stack.size() - 1);
		state.restore(this);
	}

	public void drawImage(Image image) throws GraphicsException {
		this.drewAnything = true;

		Composite composite = this.g.getComposite();
		if (this.fillAlpha == 1) {
			this.g.setPaintMode();
		} else {
			this.g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, this.fillAlpha));
		}
		image.drawTo(this);
		this.g.setComposite(composite);
	}

	public void fill(Shape shape) {
		this.drewAnything = true;
		java.awt.Paint paint = this.g.getPaint();
		this.g.setPaint(this.awtFillPaint);

		Composite composite = this.g.getComposite();
		if (this.fillAlpha == 1) {
			this.g.setPaintMode();
		} else {
			this.g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, this.fillAlpha));
		}

		if (this.fillAt != null) {
			AffineTransform saveAt = g.getTransform();
			this.g.transform(this.fillAt);
			try {
				shape = this.fillAt.createInverse().createTransformedShape(shape);
			} catch (NoninvertibleTransformException e) {
				throw new RuntimeException(e);
			}
			this.g.fill(shape);
			this.g.setTransform(saveAt);
		} else {
			this.g.fill(shape);
		}

		this.g.setPaint(paint);
		this.g.setComposite(composite);
	}

	public void draw(Shape shape) {
		this.drewAnything = true;
		if (this.strokeAt != null) {
			AffineTransform saveAt = g.getTransform();
			this.g.transform(this.strokeAt);
			try {
				shape = this.strokeAt.createInverse().createTransformedShape(shape);
			} catch (NoninvertibleTransformException e) {
				throw new RuntimeException(e);
			}
			this.g.draw(shape);
			this.g.setTransform(saveAt);
		} else {
			this.g.draw(shape);
		}
	}

	public void fillDraw(Shape shape) {
		this.fill(shape);
		this.draw(shape);
	}

	public void drawText(Text text, double x, double y) throws GraphicsException {
		this.drewAnything = true;

		try (final var gcState = this.begin()) {
			this.transform(AffineTransform.getTranslateInstance(x, y));
			Font font = ((FontMetricsImpl) text.getFontMetrics()).getFont();
			try {
				font.drawTo(this, text);
			} catch (IOException e) {
				throw new GraphicsException(e);
			}
		}
	}

	private static class G2dGroupImageGC extends G2DGC implements GroupImageGC {
		final BufferedImage image;
		final AffineTransform at;

		G2dGroupImageGC(Graphics2D g2d, FontManager fm, BufferedImage image, AffineTransform at) {
			super(g2d, fm);
			this.image = image;
			this.at = at;
		}

		public Image finish() throws GraphicsException {
			Image im = new RasterImageImpl(this.image);
			if (this.at != null && !this.at.isIdentity()) {
				im = new TransformedImage(im, this.at);
			}
			return im;
		}
	}

	public GroupImageGC createGroupImage(double width, double height) throws GraphicsException {
		final AffineTransform at = this.g.getTransform();
		final Rectangle2D bounds = at.createTransformedShape(new Rectangle2D.Double(0, 0, width, height))
				.getBounds2D();
		final double minX = Math.floor(bounds.getMinX());
		final double minY = Math.floor(bounds.getMinY());
		final double maxX = Math.ceil(bounds.getMaxX());
		final double maxY = Math.ceil(bounds.getMaxY());
		final double rasterWidth = maxX - minX;
		final double rasterHeight = maxY - minY;
		if (!(rasterWidth > 0) || !(rasterHeight > 0) || rasterWidth > Integer.MAX_VALUE
				|| rasterHeight > Integer.MAX_VALUE) {
			throw new GraphicsException("Invalid transformed group image size: " + rasterWidth + "x" + rasterHeight
					+ " (source=" + width + "x" + height + ", transform=" + at + ")");
		}

		final int w = (int) rasterWidth;
		final int h = (int) rasterHeight;
		final BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2d = (Graphics2D) image.getGraphics();
		g2d.setRenderingHints(this.g.getRenderingHints());

		final AffineTransform deviceShift = AffineTransform.getTranslateInstance(-minX, -minY);
		final AffineTransform imageToUser;
		try {
			imageToUser = at.createInverse();
			imageToUser.translate(minX, minY);
		} catch (NoninvertibleTransformException e) {
			throw new GraphicsException("Cannot place a group image under a non-invertible transform: " + at, e);
		}
		final G2dGroupImageGC gc = new G2dGroupImageGC(g2d, this.getFontManager(), image, imageToUser);
		final GraphicsState state = new GraphicsState(this).shifted(deviceShift);
		state.restore(gc);
		gc.stack = new ArrayList<>(this.stack.size());
		for (final GraphicsState saved : this.stack) {
			gc.stack.add(saved.shifted(deviceShift));
		}
		return gc;
	}
}
