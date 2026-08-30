package net.zamasoft.pdfg2d.g2d.gc;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;
import net.zamasoft.pdfg2d.g2d.util.G2DUtils;
import net.zamasoft.pdfg2d.g2d.util.RasterEffects;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.ConicGradient;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
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

		public final float fillAlpha, strokeAlpha;

		public final BlendMode blendMode;

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
			this.strokeAlpha = gc.strokeAlpha;
			this.blendMode = gc.blendMode;
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
			this.strokeAlpha = state.strokeAlpha;
			this.blendMode = state.blendMode;
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
			gc.strokeAlpha = this.strokeAlpha;
			gc.blendMode = this.blendMode;
			gc.strokeAt = this.strokeAt;
			gc.textMode = this.textMode;
		}
	}

	protected final Graphics2D g;

	protected boolean drewAnything = false;

	/**
	 * 既定は黒。{@link net.zamasoft.pdfg2d.pdf.gc.PDFGC}と同じ契約で、
	 * 明示的に設定される前でも{@code null}を返さない。状態を保存して元へ戻す
	 * 呼び出し側(擬似ボールドの{@code FontUtils.drawText}など)は、読み出した値を
	 * そのまま{@code setStrokePaint}へ渡すため、{@code null}だと復元で落ちる。
	 */
	protected Paint strokePaint = GrayColor.BLACK;

	/** 既定は黒。理由は{@link #strokePaint}と同じ。 */
	protected Paint fillPaint = GrayColor.BLACK;

	protected java.awt.Paint awtFillPaint;

	protected AffineTransform fillAt, strokeAt;

	protected float fillAlpha = 1, strokeAlpha = 1;

	/** ブレンドモード(2026-08-29)。NORMAL 以外は {@link BlendComposite} で画素合成する。 */
	protected BlendMode blendMode = BlendMode.NORMAL;

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
	 * Java2D はラスタなので、ぼかし・円錐グラデーション・繰り返し・層フィルタ・
	 * 落とし影・ブレンドのすべてを厳密に描ける(2026-08-29)。
	 */
	@Override
	public boolean supports(final Capability capability) {
		return capability != null;
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
		// BasicStroke returns null for a solid line, which is the normal case.
		// setLinePattern stores STROKE_SOLID as null, so map it back here.
		float[] da = ((BasicStroke) this.g.getStroke()).getDashArray();
		if (da == null) {
			return GC.STROKE_SOLID;
		}
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
				awtPaint = G2DUtils.toAwtPaint(conicGradient);
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
		this.strokeAlpha = alpha;
		this.g.setComposite(BlendComposite.getInstance(this.blendMode, alpha));
	}

	public float getStrokeAlpha() {
		return this.strokeAlpha;
	}

	@Override
	public void setBlendMode(final BlendMode mode) {
		this.blendMode = mode == null ? BlendMode.NORMAL : mode;
		// 線・文字は g の合成モードで描かれる。塗り・画像は都度 fillAlpha で組み立てる
		this.g.setComposite(BlendComposite.getInstance(this.blendMode, this.strokeAlpha));
	}

	@Override
	public BlendMode getBlendMode() {
		return this.blendMode;
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
		this.g.setComposite(BlendComposite.getInstance(this.blendMode, this.fillAlpha));
		image.drawTo(this);
		this.g.setComposite(composite);
	}

	/**
	 * 層に効果(色行列 → ぼかし → 落とし影 → 不透明度)を掛けて描く(2026-08-29)。
	 * 画像をデバイス空間の ARGB 層へ描いてから画素処理し、恒等変換でクリップと
	 * ブレンドモード・fillAlpha を効かせて戻す。σ と影のずれはユーザー空間で
	 * 受け取り、現在の変換でデバイス空間へ写す。
	 */
	@Override
	public void drawImage(final Image image, final GroupEffects effects) throws GraphicsException {
		if (effects == null || effects.isIdentity()) {
			this.drawImage(image);
			return;
		}
		this.drewAnything = true;
		final AffineTransform at = this.g.getTransform();
		final double blurSigma = RasterEffects.deviceSigma(at, effects.blurSigma());
		final GroupEffects.DropShadow shadow = effects.dropShadow();
		double shadowDx = 0, shadowDy = 0, shadowSigma = 0;
		if (shadow != null) {
			final Point2D d = at.deltaTransform(new Point2D.Double(shadow.dx(), shadow.dy()), null);
			shadowDx = d.getX();
			shadowDy = d.getY();
			shadowSigma = RasterEffects.deviceSigma(at, shadow.sigma());
		}
		final int pad = RasterEffects.kernelRadius(blurSigma) + RasterEffects.kernelRadius(shadowSigma)
				+ (int) Math.ceil(Math.max(Math.abs(shadowDx), Math.abs(shadowDy))) + 1;
		final Rectangle2D bounds = at
				.createTransformedShape(new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight()))
				.getBounds2D();
		final Rectangle region = this.deviceRegion(bounds, pad);
		if (region == null) {
			return;
		}
		if (region.width * (long) region.height > MAX_LAYER_PIXELS) {
			// 層が大きすぎる。効果を諦めてそのまま描く
			this.drawImage(image);
			return;
		}

		final BufferedImage layer = new BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D lg = layer.createGraphics();
		try {
			final G2DGC sub = new G2DGC(lg, this.fm);
			new GraphicsState(this).shifted(AffineTransform.getTranslateInstance(-region.x, -region.y)).restore(sub);
			// 層は孤立させて描き、クリップ・不透明度・ブレンドは戻すときに掛ける
			lg.setRenderingHints(this.g.getRenderingHints());
			lg.setClip(null);
			lg.setComposite(AlphaComposite.SrcOver);
			sub.fillAlpha = 1;
			sub.strokeAlpha = 1;
			sub.blendMode = BlendMode.NORMAL;
			image.drawTo(sub);
		} finally {
			lg.dispose();
		}

		final int w = region.width, h = region.height;
		float[][] planes = RasterEffects.toPlanes(layer);
		if (effects.colorMatrix() != null) {
			RasterEffects.applyColorMatrix(planes, effects.colorMatrix());
		}
		RasterEffects.premultiply(planes);
		if (blurSigma > 0) {
			RasterEffects.gaussianBlur(planes, w, h, blurSigma);
		}
		if (shadow != null) {
			final Color c = shadow.color();
			final float[] rgba = c == null ? new float[] { 0, 0, 0, 1 }
					: new float[] { c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() };
			planes = RasterEffects.dropShadow(planes, w, h, shadowDx, shadowDy, shadowSigma, rgba);
		}
		if (effects.opacity() < 1) {
			RasterEffects.scale(planes, (float) Math.max(0, effects.opacity()));
		}
		this.drawLayer(RasterEffects.toPremultipliedImage(planes, w, h), region.x, region.y);
	}

	/** 効果の層として確保する画素数の上限。超えたら効果なしへ退避する。 */
	private static final long MAX_LAYER_PIXELS = 64L * 1024 * 1024;

	/**
	 * デバイス空間で {@code bounds} を {@code pad} 広げ、クリップとデバイスの範囲
	 * (それぞれ pad 広げたもの)で切った整数矩形を返す。空なら null。
	 */
	private Rectangle deviceRegion(final Rectangle2D bounds, final int pad) {
		Rectangle2D r = new Rectangle2D.Double(bounds.getX() - pad, bounds.getY() - pad,
				bounds.getWidth() + 2.0 * pad, bounds.getHeight() + 2.0 * pad);
		final Shape clip = this.g.getClip();
		if (clip != null) {
			final Rectangle2D cb = this.g.getTransform().createTransformedShape(clip).getBounds2D();
			r = r.createIntersection(new Rectangle2D.Double(cb.getX() - pad, cb.getY() - pad,
					cb.getWidth() + 2.0 * pad, cb.getHeight() + 2.0 * pad));
		}
		final GraphicsConfiguration conf = this.g.getDeviceConfiguration();
		if (conf != null && conf.getDevice() != null
				&& conf.getDevice().getType() == GraphicsDevice.TYPE_IMAGE_BUFFER) {
			final Rectangle db = conf.getBounds();
			r = r.createIntersection(new Rectangle2D.Double(db.getX() - pad, db.getY() - pad,
					db.getWidth() + 2.0 * pad, db.getHeight() + 2.0 * pad));
		}
		if (r.isEmpty()) {
			return null;
		}
		final int x0 = (int) Math.floor(r.getMinX()), y0 = (int) Math.floor(r.getMinY());
		final int x1 = (int) Math.ceil(r.getMaxX()), y1 = (int) Math.ceil(r.getMaxY());
		if (x1 <= x0 || y1 <= y0) {
			return null;
		}
		return new Rectangle(x0, y0, x1 - x0, y1 - y0);
	}

	/** デバイス座標 (x, y) に層を恒等変換で置く。クリップ・fillAlpha・ブレンドモードを効かせる。 */
	private void drawLayer(final BufferedImage layer, final int x, final int y) {
		final AffineTransform saveAt = this.g.getTransform();
		final Composite saveComposite = this.g.getComposite();
		this.g.setTransform(new AffineTransform());
		this.g.setComposite(BlendComposite.getInstance(this.blendMode, this.fillAlpha));
		this.g.drawImage(layer, x, y, null);
		this.g.setComposite(saveComposite);
		this.g.setTransform(saveAt);
	}

	/**
	 * 形を現在の塗りで層へ描き、ガウスぼかしを掛けて戻す(2026-08-29)。
	 * σ はユーザー空間単位で、現在の変換でデバイス空間へ写す。
	 */
	@Override
	public void fillBlurred(Shape shape, final double sigma) throws GraphicsException {
		final AffineTransform at = this.g.getTransform();
		final double deviceSigma = RasterEffects.deviceSigma(at, sigma);
		if (!(deviceSigma > 0)) {
			this.fill(shape);
			return;
		}
		this.drewAnything = true;
		final int pad = RasterEffects.kernelRadius(deviceSigma);
		final Rectangle region = this.deviceRegion(at.createTransformedShape(shape).getBounds2D(), pad);
		if (region == null) {
			return;
		}
		if (region.width * (long) region.height > MAX_LAYER_PIXELS) {
			this.fill(shape);
			return;
		}
		final BufferedImage layer = new BufferedImage(region.width, region.height,
				BufferedImage.TYPE_INT_ARGB_PRE);
		final Graphics2D lg = layer.createGraphics();
		try {
			lg.setRenderingHints(this.g.getRenderingHints());
			lg.translate(-region.x, -region.y);
			lg.transform(at);
			lg.setPaint(this.awtFillPaint);
			if (this.fillAt != null) {
				lg.transform(this.fillAt);
				try {
					shape = this.fillAt.createInverse().createTransformedShape(shape);
				} catch (NoninvertibleTransformException e) {
					throw new RuntimeException(e);
				}
			}
			lg.fill(shape);
		} finally {
			lg.dispose();
		}
		this.drawLayer(RasterEffects.blurPremultiplied(layer, deviceSigma), region.x, region.y);
	}

	public void fill(Shape shape) {
		this.drewAnything = true;
		java.awt.Paint paint = this.g.getPaint();
		this.g.setPaint(this.awtFillPaint);

		Composite composite = this.g.getComposite();
		this.g.setComposite(BlendComposite.getInstance(this.blendMode, this.fillAlpha));

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
