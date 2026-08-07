package net.zamasoft.pdfg2d.g2d.gc;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.Hashtable;
import java.util.Map;

import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;
import net.zamasoft.pdfg2d.g2d.util.G2DUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.SimpleLayoutGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.util.TextUtils;

/**
 * A bridge class that implements {@link Graphics2D} by delegating to a
 * {@link GC} instance.
 * This allow existing Java2D-based code to generate PDF content through the
 * pdfg2d library.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class BridgeGraphics2D extends Graphics2D implements Cloneable {
	protected GC gc;

	/**
	 * {@link #create()}時に積んだGC状態のハンドル。{@link #dispose()}で閉じる。
	 * <p>
	 * Graphics2Dの契約ではcreate()で得た子コンテキストの状態変更(クリップ・
	 * 変換等)はdispose()で破棄されるが、GCのクリップは交差のみで狭める方向
	 * にしか変更できないため、create()時に{@link GC#begin()}で状態を保存し
	 * dispose()で復元しないと子のクリップが親へ漏れる。Batik(SVG)は
	 * クリップ付き要素ごとにcreate()→clip()→描画→dispose()を繰り返すため、
	 * この対応がないと2つ目以降のクリップが前のクリップと交差し続けて
	 * 描画が消える(2026-08-07、yahoo.co.jpのアイコンで発覚)。
	 * </p>
	 */
	protected GC.State gcState;

	protected final GraphicsConfiguration config;

	protected static final AffineTransform DEFAULT_TRANSFORM = new AffineTransform();

	protected AffineTransform transform = new AffineTransform();

	protected Shape clip = null;

	protected Composite composite;

	protected java.awt.Paint paint = java.awt.Color.black;

	protected java.awt.Color foreground = java.awt.Color.BLACK;

	protected java.awt.Color background = java.awt.Color.WHITE;

	protected Stroke stroke = new BasicStroke();

	protected RenderingHints hints = new RenderingHints(null);

	protected Font font = new Font("serif", Font.PLAIN, 12);

	public BridgeGraphics2D(final GC gc, final GraphicsConfiguration config) {
		this.gc = gc;
		this.config = config;
	}

	public BridgeGraphics2D(final GC gc) {
		this(gc, null);
	}

	public GC getGC() {
		return this.gc;
	}

	private void restoreState() {
		this.gc.resetState();
		final var gcolor = G2DUtils.fromAwtColor(this.foreground);
		this.gc.setStrokePaint(gcolor);
		this.gc.setFillPaint(gcolor);
		if (this.stroke instanceof final BasicStroke bs) {
			this.gc.setLineWidth(bs.getLineWidth());
			final var da = bs.getDashArray();
			if (da != null) {
				final var dda = new double[da.length];
				for (var i = 0; i < da.length; ++i) {
					dda[i] = da[i];
				}
				this.gc.setLinePattern(dda);
			} else {
				this.gc.setLinePattern(null);
			}
			this.gc.setLineCap(G2DUtils.decodeLineCap((short) bs.getEndCap()));
			this.gc.setLineJoin(G2DUtils.decodeLineJoin((short) bs.getLineJoin()));
		} else {
			throw new IllegalStateException("Unsupported stroke type: " + this.stroke.getClass().getName());
		}
		if (this.transform != null) {
			this.gc.transform(this.transform);
		}
		if (this.clip != null) {
			Shape s = this.clip;
			if (this.transform != null) {
				try {
					s = this.transform.createInverse().createTransformedShape(s);
				} catch (NoninvertibleTransformException e) {
					s = null;
				}
			}
			this.gc.clip(s);
		}
	}

	public GraphicsConfiguration getDeviceConfiguration() {
		return this.config;
	}

	public void setRenderingHint(final Key key, final Object value) {
		this.hints.put(key, value);
	}

	public Object getRenderingHint(final Key key) {
		return this.hints.get(key);
	}

	@SuppressWarnings("unchecked")
	public void setRenderingHints(final Map<?, ?> hints) {
		this.hints = new RenderingHints((Map<RenderingHints.Key, ?>) hints);
	}

	public void addRenderingHints(final Map<?, ?> hints) {
		this.hints.putAll(hints);
	}

	public RenderingHints getRenderingHints() {
		return this.hints;
	}

	public void transform(final AffineTransform at) {
		this.transform.concatenate(at);
		this.gc.transform(at);
	}

	public void setTransform(final AffineTransform at) {
		this.transform = new AffineTransform(at);
		this.restoreState();
	}

	public AffineTransform getTransform() {
		return new AffineTransform(this.transform);
	}

	public void setPaint(final java.awt.Paint paint) {
		if (paint == null) {
			return;
		}
		this.paint = paint;
		if (paint instanceof final java.awt.Color c) {
			this.foreground = c;
		}
		final var spaint = G2DUtils.fromAwtPaint(paint);
		if (spaint != null) {
			this.gc.setStrokePaint(spaint);
			this.gc.setFillPaint(spaint);
		}
	}

	public java.awt.Paint getPaint() {
		return this.paint;
	}

	public void setComposite(final Composite composite) {
		this.composite = composite;
		if (composite instanceof final AlphaComposite ac) {
			final var alpha = ac.getAlpha();
			this.gc.setFillAlpha(alpha);
			this.gc.setStrokeAlpha(alpha);
		}
	}

	public Composite getComposite() {
		return this.composite;
	}

	public void setBackground(final java.awt.Color background) {
		this.background = background;
	}

	public java.awt.Color getBackground() {
		return this.background;
	}

	public void setStroke(final Stroke stroke) {
		this.stroke = stroke;
		if (stroke instanceof final BasicStroke bs) {
			this.gc.setLineWidth(bs.getLineWidth());
			final var da = bs.getDashArray();
			if (da != null) {
				final var dda = new double[da.length];
				for (var i = 0; i < da.length; ++i) {
					dda[i] = da[i];
				}
				this.gc.setLinePattern(dda);
			} else {
				this.gc.setLinePattern(null);
			}
			this.gc.setLineCap(G2DUtils.decodeLineCap((short) bs.getEndCap()));
			this.gc.setLineJoin(G2DUtils.decodeLineJoin((short) bs.getLineJoin()));
		}
	}

	public Stroke getStroke() {
		return this.stroke;
	}

	public java.awt.Color getColor() {
		return this.foreground;
	}

	public void setColor(final java.awt.Color color) {
		this.setPaint(color);
	}

	@Override
	public void clip(final Shape clip) {
		var s = clip;
		if (s != null) {
			s = this.transform.createTransformedShape(s);
		}
		if (this.clip != null) {
			final var newClip = new Area(this.clip);
			newClip.intersect(new Area(s));
			this.clip = new GeneralPath(newClip);
		} else {
			this.clip = s;
		}

		this.gc.clip(clip);
	}

	@Override
	public void clipRect(final int x, final int y, final int width, final int height) {
		this.clip(new Rectangle(x, y, width, height));
	}

	public void setClip(final Shape clip) {
		this.clip = (clip != null) ? this.transform.createTransformedShape(clip) : null;
		this.restoreState();
	}

	public void setClip(final int x, final int y, final int width, final int height) {
		this.setClip(new Rectangle(x, y, width, height));
	}

	public Shape getClip() {
		try {
			return transform.createInverse().createTransformedShape(this.clip);
		} catch (NoninvertibleTransformException e) {
			return null;
		}
	}

	public void draw(final Shape shape) {
		if (this.stroke instanceof BasicStroke) {
			this.gc.draw(shape);
		} else {
			this.gc.fill(this.stroke.createStrokedShape(shape));
		}
	}

	public void fill(final Shape shape) {
		this.gc.fill(shape);
	}

	@Override
	public FontRenderContext getFontRenderContext() {
		var antialiasingHint = this.hints.get(RenderingHints.KEY_TEXT_ANTIALIASING);
		var isAntialiased = true;
		if (antialiasingHint != RenderingHints.VALUE_TEXT_ANTIALIAS_ON
				&& antialiasingHint != RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT) {
			if (antialiasingHint != RenderingHints.VALUE_TEXT_ANTIALIAS_OFF) {
				antialiasingHint = this.hints.get(RenderingHints.KEY_ANTIALIASING);
				if (antialiasingHint != RenderingHints.VALUE_ANTIALIAS_ON
						&& antialiasingHint != RenderingHints.VALUE_ANTIALIAS_DEFAULT) {
					if (antialiasingHint == RenderingHints.VALUE_ANTIALIAS_OFF) {
						isAntialiased = false;
					}
				}
			} else {
				isAntialiased = false;
			}
		}

		final var useFractionalMetrics = this.hints
				.get(RenderingHints.KEY_FRACTIONALMETRICS) != RenderingHints.VALUE_FRACTIONALMETRICS_OFF;
		return new FontRenderContext(DEFAULT_TRANSFORM, isAntialiased, useFractionalMetrics);
	}

	public Font getFont() {
		return this.font;
	}

	public void setFont(Font font) {
		this.font = font;

	}

	class MyFontMetrics extends FontMetrics {
		private static final long serialVersionUID = 1L;

		MyFontMetrics(Font font) {
			super(font);
		}

		public int stringWidth(String text) {
			SimpleLayoutGlyphHandler lgh = new SimpleLayoutGlyphHandler();
			drawString(lgh, new AttributedString(text, this.getFont().getAttributes()).getIterator());
			return (int) lgh.getAdvance();
		}
	}

	public FontMetrics getFontMetrics(final Font font) {
		return new MyFontMetrics(font);
	}

	public void drawString(final String text, final int x, final int y) {
		this.drawString(text, (float) x, (float) y);
	}

	public void drawString(final String text, final float x, final float y) {
		this.drawString(new AttributedString(text, this.font.getAttributes()).getIterator(), x, y);
	}

	public void drawString(final AttributedCharacterIterator aci, final int x, final int y) {
		this.drawString(aci, (float) x, (float) y);
	}

	private void drawString(final GlyphHandler gh, final AttributedCharacterIterator aci) {
		try (final var tlf = new TextLayoutHandler(this.gc, TextBreakingRulesBundle.getRules("ja"), gh)) {
			final var atts = this.font.getAttributes();
			tlf.setFontFamilies(FontFamilyList.create(this.font.getFamily()));
			final var style = this.font.getStyle();
			tlf.setFontWeight(TextUtils.toFontWeight((Float) atts.get(TextAttribute.WEIGHT),
					(style & Font.BOLD) != 0 ? Weight.W_600 : Weight.W_400));
			tlf.setFontSize(this.font.getSize2D());
			tlf.setFontStyle(TextUtils.toFontStyle((Float) atts.get(TextAttribute.POSTURE),
					(style & Font.ITALIC) != 0 ? Style.ITALIC : Style.NORMAL));
			tlf.characters(aci);
		}
	}

	@Override
	public void drawString(final AttributedCharacterIterator aci, final float x, final float y) {
		try (final var gcState = this.gc.begin()) {
			this.gc.transform(AffineTransform.getTranslateInstance(x, y));

			final var lgh = new SimpleLayoutGlyphHandler();
			lgh.setGC(this.gc);
			this.drawString(lgh, aci);

		}
	}

	@Override
	public void drawGlyphVector(final GlyphVector gv, final float x, final float y) {
		this.gc.fill(gv.getOutline(x, y));
	}

	protected void drawBufferedImage(final BufferedImage image, final AffineTransform at) {
		try (final var gcState = this.gc.begin()) {
			this.gc.transform(at);
			this.gc.drawImage(new RasterImageImpl(image));
		}
	}

	@Override
	public void drawRenderedImage(final RenderedImage image, final AffineTransform at) {
		final var bimage = (image instanceof final BufferedImage bi) ? bi
				: new BufferedImage(image.getColorModel(), image.copyData(null),
						image.getColorModel().isAlphaPremultiplied(), new Hashtable<>());
		this.drawBufferedImage(bimage, at);
	}

	@Override
	public void drawRenderableImage(final RenderableImage image, final AffineTransform at) {
		this.drawRenderedImage(image.createDefaultRendering(), at);
	}

	@Override
	public void drawImage(BufferedImage image, final BufferedImageOp op, final int x, final int y) {
		final var at = AffineTransform.getTranslateInstance(x, y);
		if (op != null) {
			image = op.createCompatibleDestImage(image, image.getColorModel());
		}
		this.drawRenderedImage(image, at);
	}

	@Override
	public boolean drawImage(final Image image, final AffineTransform at, final ImageObserver obs) {
		if (image instanceof final RenderedImage ri) {
			this.drawRenderedImage(ri, at);
		} else if (image instanceof final RenderableImage rai) {
			this.drawRenderableImage(rai, at);
		} else {
			final var bimage = new BufferedImage(image.getWidth(obs), image.getHeight(obs),
					BufferedImage.TYPE_INT_ARGB);
			bimage.getGraphics().drawImage(image, 0, 0, null);
			this.drawBufferedImage(bimage, at);
		}
		return true;
	}

	@Override
	public boolean drawImage(final Image image, final int x, final int y, final ImageObserver obs) {
		return this.drawImage(image, x, y, image.getWidth(null), image.getHeight(null), obs);
	}

	@Override
	public boolean drawImage(final Image image, final int x, final int y, final int width, final int height,
			final ImageObserver obs) {
		final var at = new AffineTransform((double) width / image.getWidth(null), 0, 0,
				(double) height / image.getHeight(null), x, y);
		return this.drawImage(image, at, obs);
	}

	@Override
	public boolean drawImage(final Image image, final int x, final int y, final java.awt.Color color,
			final ImageObserver obs) {
		return this.drawImage(image, x, y, image.getWidth(null), image.getHeight(null), obs);
	}

	@Override
	public boolean drawImage(final Image image, final int x, final int y, final int width, final int height,
			final java.awt.Color color, final ImageObserver obs) {
		final var p = this.getPaint();
		this.setPaint(color);
		this.fillRect(x, y, width, height);
		this.setPaint(p);
		return this.drawImage(image, x, y, width, height, obs);
	}

	@Override
	public boolean drawImage(final Image image, final int dx1, final int dy1, final int dx2, final int dy2,
			final int sx1, final int sy1, final int sx2, final int sy2, final ImageObserver obs) {
		final var w = dx2 - dx1;
		final var h = dy2 - dy1;
		final var bimage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		bimage.getGraphics().drawImage(image, 0, 0, w, h, sx1, sy1, sx2, sy2, null);
		return this.drawImage(bimage, dx1, dy1, obs);
	}

	@Override
	public boolean drawImage(final Image image, final int dx1, final int dy1, final int dx2, final int dy2,
			final int sx1, final int sy1, final int sx2, final int sy2, final java.awt.Color color,
			final ImageObserver obs) {
		if (color != null) {
			final var p = this.getPaint();
			this.setPaint(color);
			this.fillRect(dx1, dy1, dx2 - dx1, dy2 - dy1);
			this.setPaint(p);
		}
		return this.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, obs);
	}

	public Graphics create() {
		try {
			BridgeGraphics2D g = (BridgeGraphics2D) this.clone();
			// 子の状態変更(特にクリップ)をdispose()で復元できるように
			// GC状態を保存する(gcStateフィールドのコメント参照)
			g.gcState = this.gc.begin();
			return g;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	public void setPaintMode() {
		this.setComposite(null);
	}

	public void setXORMode(final java.awt.Color color) {
		this.setComposite(AlphaComposite.Xor);
	}

	public Rectangle getClipBounds() {
		final var c = this.getClip();
		return (c == null) ? null : c.getBounds();
	}

	public boolean hit(final Rectangle rect, final Shape shape, final boolean onStroke) {
		throw new UnsupportedOperationException();
	}

	public void translate(final int x, final int y) {
		this.translate((double) x, (double) y);
	}

	public void translate(final double x, final double y) {
		this.transform(AffineTransform.getTranslateInstance(x, y));
	}

	public void rotate(final double theta) {
		this.transform(AffineTransform.getRotateInstance(theta));
	}

	public void rotate(final double theta, final double x, final double y) {
		this.transform(AffineTransform.getRotateInstance(theta, x, y));
	}

	public void scale(final double sx, final double sy) {
		this.transform(AffineTransform.getScaleInstance(sx, sy));
	}

	public void shear(final double shx, final double shy) {
		this.transform(AffineTransform.getShearInstance(shx, shy));
	}

	public void copyArea(final int x, final int y, final int width, final int height, final int dx, final int dy) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void drawLine(final int x1, final int y1, final int x2, final int y2) {
		this.gc.fill(new Line2D.Float(x1, y1, x2, y2));
	}

	@Override
	public void drawRect(final int x, final int y, final int width, final int height) {
		this.draw(new Rectangle(x, y, width, height));
	}

	@Override
	public void fillRect(final int x, final int y, final int width, final int height) {
		this.gc.fill(new Rectangle(x, y, width, height));
	}

	@Override
	public void clearRect(final int x, final int y, final int width, final int height) {
		final var bg = G2DUtils.fromAwtColor(this.getBackground());
		final var fg = G2DUtils.fromAwtColor(this.getColor());
		this.gc.setFillPaint(bg);
		this.gc.fill(new Rectangle(x, y, width, height));
		this.gc.setStrokePaint(fg);
	}

	@Override
	public void drawRoundRect(final int x, final int y, final int width, final int height, final int rx, final int ry) {
		this.draw(new RoundRectangle2D.Float(x, y, width, height, rx, ry));
	}

	@Override
	public void fillRoundRect(final int x, final int y, final int width, final int height, final int rx, final int ry) {
		this.gc.fill(new RoundRectangle2D.Float(x, y, width, height, rx, ry));
	}

	@Override
	public void drawOval(final int x, final int y, final int width, final int height) {
		this.draw(new Ellipse2D.Float(x, y, width, height));
	}

	@Override
	public void fillOval(final int x, final int y, final int width, final int height) {
		this.gc.fill(new Ellipse2D.Float(x, y, width, height));
	}

	@Override
	public void drawArc(final int x, final int y, final int width, final int height, final int startAngle,
			final int arcAngle) {
		this.draw(new Arc2D.Float(x, y, width, height, startAngle, arcAngle, Arc2D.CHORD));
	}

	@Override
	public void fillArc(final int x, final int y, final int width, final int height, final int startAngle,
			final int arcAngle) {
		this.gc.fill(new Arc2D.Float(x, y, width, height, startAngle, arcAngle, Arc2D.PIE));
	}

	@Override
	public void drawPolyline(final int[] xpoints, final int[] ypoints, final int npoints) {
		if (npoints <= 0) {
			return;
		}
		final var path = new GeneralPath();
		path.moveTo(xpoints[0], ypoints[0]);
		for (var i = 1; i < npoints; ++i) {
			path.lineTo(xpoints[i], ypoints[i]);
		}
		this.draw(path);
	}

	@Override
	public void drawPolygon(final int[] xpoints, final int[] ypoints, final int npoints) {
		this.draw(new Polygon(xpoints, ypoints, npoints));
	}

	@Override
	public void fillPolygon(final int[] xpoints, final int[] ypoints, final int npoints) {
		this.gc.fill(new Polygon(xpoints, ypoints, npoints));
	}

	public void dispose() {
		if (this.gcState != null) {
			// create()で保存したGC状態を復元する(クリップ・変換の巻き戻し)
			this.gcState.close();
			this.gcState = null;
		}
		if (this.gc != null) {
			this.gc = null;
		}
	}

	protected Object clone() throws CloneNotSupportedException {
		BridgeGraphics2D clone = (BridgeGraphics2D) super.clone();
		clone.transform = new AffineTransform(clone.transform);
		// GC状態ハンドルは複製しない(closeの責務は元のインスタンスにある)
		clone.gcState = null;
		return clone;
	}
}
