package net.zamasoft.pdfg2d.gc;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * Represents a graphics context.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface GC {
	/**
	 * Represents the line join style.
	 */
	public enum LineJoin {
		MITER((short) 0), ROUND((short) 1), BEVEL((short) 2);

		public final short code;

		LineJoin(final short code) {
			this.code = code;
		}
	}

	/**
	 * Represents the line cap style.
	 */
	public enum LineCap {
		BUTT((short) 0), ROUND((short) 1), SQUARE((short) 2);

		public final short code;

		LineCap(final short c) {
			this.code = c;
		}
	}

	public static final double[] STROKE_SOLID = new double[0];

	/**
	 * Represents the text rendering mode.
	 */
	public enum TextMode {
		FILL((short) 0), STROKE((short) 1), FILL_STROKE((short) 2);

		public final short code;

		TextMode(final short t) {
			this.code = t;
		}
	}

	/**
	 * A graphics state saved by {@link #begin()}. Closing it restores the
	 * saved state; closing it more than once has no effect.
	 */
	public interface State extends AutoCloseable {
		/**
		 * Restores the saved graphics state.
		 *
		 * @throws GraphicsException if a graphics error occurs
		 */
		@Override
		public void close() throws GraphicsException;
	}

	/**
	 * Returns the font manager.
	 *
	 * @return the font manager
	 */
	public FontManager getFontManager();

	/**
	 * Begins a new graphics state and returns a handle that restores the
	 * previous state when closed, so that saved states cannot be left
	 * unbalanced:
	 *
	 * <pre>{@code
	 * try (var state = gc.begin()) {
	 *     gc.transform(...);
	 *     gc.fill(...);
	 * } // the previous state is restored here
	 * }</pre>
	 *
	 * When the save and restore cannot share a lexical scope (for example a
	 * page begun in one method and finished in another), keep the returned
	 * {@code State} and call {@link State#close()} explicitly.
	 *
	 * @return a handle that restores the previous state when closed
	 * @throws GraphicsException if a graphics error occurs
	 */
	public State begin() throws GraphicsException;

	/** A {@link State} whose {@code close()} does nothing. */
	public static final State NO_OP_STATE = new State() {
		@Override
		public void close() throws GraphicsException {
			// nothing to restore
		}
	};

	/**
	 * Opens an artifact scope: everything drawn until the returned handle is
	 * closed is emitted as decorative content (a PDF {@code /Artifact} marked
	 * content sequence) instead of real, extractable content. Real content
	 * marks that the drawing operations would open themselves are suppressed
	 * for the duration of the scope, so text stays text/vector operators but
	 * carries no structure element and is skipped by text extraction and
	 * assistive technology.
	 *
	 * <p>
	 * Always use it with try-with-resources so that the scope is closed
	 * symmetrically:
	 * </p>
	 *
	 * <pre>{@code
	 * try (GC.State artifact = gc.beginArtifactScope()) {
	 *     drawable.draw(gc, x, y);
	 * }
	 * }</pre>
	 *
	 * <p>
	 * The default implementation does nothing and returns {@link #NO_OP_STATE}:
	 * back-ends without a logical structure (untagged PDF, patterns, group
	 * images, Graphics2D bridges, recorders) emit exactly the same output as
	 * before. Only tagged PDF page content streams behave differently.
	 * </p>
	 *
	 * @return a handle that closes the artifact scope; never {@code null}
	 * @throws GraphicsException if a graphics error occurs
	 */
	public default State beginArtifactScope() throws GraphicsException {
		return NO_OP_STATE;
	}

	/**
	 * Opens a semantic replacement scope. Everything drawn until the returned
	 * handle is closed is represented to text extractors and assistive
	 * technology by {@code logicalText}, while the enclosed drawing operations
	 * remain unchanged visually. A typical use is one visually reordered line
	 * whose glyph runs are painted in visual order.
	 *
	 * <p>
	 * Always close the returned state, preferably with try-with-resources. Calls
	 * made while a replacement scope is already open may return a no-op state;
	 * callers must therefore put the complete replacement text on the outermost
	 * scope.
	 * </p>
	 *
	 * <p>
	 * The default implementation does nothing and returns
	 * {@link #NO_OP_STATE}, preserving output for back-ends that do not expose a
	 * semantic text replacement facility.
	 * </p>
	 *
	 * @param logicalText the replacement text in logical reading order
	 * @return a handle that closes the replacement scope; never {@code null}
	 * @throws GraphicsException if a graphics error occurs
	 * @since 1.3
	 */
	public default State beginTextReplacement(final String logicalText) throws GraphicsException {
		return NO_OP_STATE;
	}

	/**
	 * Resets the current graphics state to the initial state.
	 *
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void resetState() throws GraphicsException;

	/**
	 * Sets the stroke paint.
	 * 
	 * @param paint the paint object
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void setStrokePaint(final Paint paint) throws GraphicsException;

	/**
	 * Returns the stroke paint.
	 * 
	 * @return the stroke paint object
	 */
	public Paint getStrokePaint();

	/**
	 * Sets the fill paint.
	 * 
	 * @param paint the paint object
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void setFillPaint(final Paint paint) throws GraphicsException;

	/**
	 * Returns the fill paint.
	 * 
	 * @return the fill paint object
	 */
	public Paint getFillPaint();

	/**
	 * Returns the stroke alpha.
	 *
	 * @return the stroke alpha
	 */
	public float getStrokeAlpha();

	/**
	 * Sets the stroke alpha.
	 *
	 * <p>
	 * Alpha is graphics state, applied to whatever is drawn while it is in
	 * effect (in PDF terms, {@code CA} in the ExtGState). Setting a paint
	 * that carries its own alpha, such as
	 * {@link net.zamasoft.pdfg2d.gc.paint.RGBAColor}, replaces this state
	 * alpha with the color's alpha component — the two are one channel, and
	 * the last one set wins. Use this method to scope translucency to a
	 * state block, and {@code RGBAColor} when the alpha naturally travels
	 * with the color value (for example gradient stops).
	 *
	 * @param strokeAlpha the stroke alpha
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void setStrokeAlpha(final float strokeAlpha) throws GraphicsException;

	/**
	 * Returns the fill alpha.
	 *
	 * @return the fill alpha
	 */
	public float getFillAlpha();

	/**
	 * Sets the fill alpha.
	 *
	 * <p>
	 * See {@link #setStrokeAlpha(float)} for how state alpha relates to
	 * paint-level alpha ({@code RGBAColor}).
	 *
	 * @param fillAlpha the fill alpha
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void setFillAlpha(final float fillAlpha) throws GraphicsException;

	/**
	 * Sets the blend mode applied to subsequent fills, strokes, text and images
	 * (2026-08-29, additive). The default implementation ignores the mode so
	 * that backends without native blending keep their previous output; see
	 * {@link net.zamasoft.pdfg2d.gc.paint.BlendMode}.
	 *
	 * @param mode the blend mode; {@code null} is treated as {@code NORMAL}.
	 */
	public default void setBlendMode(final net.zamasoft.pdfg2d.gc.paint.BlendMode mode) throws GraphicsException {
		// no-op by default
	}

	/**
	 * @return the current blend mode; {@code NORMAL} for backends that do not
	 *         track it.
	 */
	/**
	 * 出力先が厳密に描ける描画機能(2026-08-29)。
	 *
	 * <p>
	 * 出力形式ごとに厳密化できる機能が異なる。PDF は生成画像によるぼかし・
	 * 要素単位のフィルタ合成と Type 4 メッシュによる円錐グラデーションに対応する。
	 * 利用側はまずこれで問い合わせ、できるなら
	 * 厳密経路({@link #supports(Capability)}がtrueの機能用のAPI)へ、
	 * できなければ近似へ進み、近似したことを利用者へ知らせる。
	 * 既定は全てfalse(=近似)。
	 * </p>
	 */
	public enum Capability {
		/** ガウスぼかし(box-shadow/text-shadowのblur、filter:blur())。 */
		GAUSSIAN_BLUR,
		/** 円錐グラデーション(conic-gradient)のPaint。 */
		CONIC_GRADIENT,
		/** 周期を無限に繰り返すグラデーション(repeating-*)。 */
		REPEATING_GRADIENT,
		/** 要素全体を1つの層にしてから色行列・ぼかし等を掛けるフィルタ合成。 */
		GROUP_FILTER,
		/** 描いた内容のシルエットからの落とし影(filter:drop-shadow())。 */
		DROP_SHADOW,
		/** 要素全体を1つの層としてブレンドする(mix-blend-mode/isolation)。 */
		BLEND_GROUP
	}

	/**
	 * Reports how {@link #drawGroupEffects(Image, GroupEffects)} rendered a
	 * captured group.
	 */
	public enum GroupEffectsResult {
		/** The group and its effects remained vector content. */
		VECTOR,
		/** The affected group alone was rasterized. */
		RASTERIZED,
		/** A resource limit was exceeded, so the group was drawn without effects. */
		LIMIT_FALLBACK,
		/** The backend cannot render the requested group effects; nothing was drawn. */
		UNSUPPORTED
	}

	/** 出力先が{@code capability}を厳密に描けるなら true。既定は false。 */
	public default boolean supports(final Capability capability) {
		return false;
	}

	/**
	 * Returns whether group effects accepted by this graphics context are
	 * implemented by rasterizing the captured group. Callers may use this to
	 * avoid routing content, such as text shadows, through a path that would
	 * change its semantic representation.
	 *
	 * @return {@code true} when non-trivial group effects are rasterized
	 */
	public default boolean rasterizesGroupEffects() {
		return false;
	}

	public default net.zamasoft.pdfg2d.gc.paint.BlendMode getBlendMode() {
		return net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL;
	}

	/**
	 * Sets the line width.
	 * 
	 * @param width the line width
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void setLineWidth(final double width) throws GraphicsException;

	/**
	 * Returns the line width.
	 * 
	 * @return the line width
	 */
	public double getLineWidth();

	/**
	 * Sets the line pattern.
	 * 
	 * @param pattern the line pattern
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void setLinePattern(final double[] pattern) throws GraphicsException;

	/**
	 * Returns the line pattern.
	 * 
	 * @return the line pattern
	 */
	public double[] getLinePattern();

	/**
	 * Sets the line join style.
	 * 
	 * @param style the line join style
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void setLineJoin(final LineJoin style) throws GraphicsException;

	/**
	 * Returns the line join style.
	 * 
	 * @return the line join style
	 */
	public LineJoin getLineJoin();

	/**
	 * Sets the line cap style.
	 * 
	 * @param style the line cap style
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void setLineCap(final LineCap style) throws GraphicsException;

	/**
	 * Returns the line cap style.
	 * 
	 * @return the line cap style
	 */
	public LineCap getLineCap();

	/**
	 * Sets the text rendering mode.
	 * 
	 * @param textMode the text rendering mode
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void setTextMode(final TextMode textMode) throws GraphicsException;

	/**
	 * Returns the text rendering mode.
	 * 
	 * @return the text rendering mode
	 */
	public TextMode getTextMode();

	/**
	 * Concatenates the current transform with the given transform.
	 * 
	 * @param at the transform to concatenate
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void transform(final AffineTransform at) throws GraphicsException;

	/**
	 * Returns the current transform.
	 * 
	 * @return the current transform
	 */
	public AffineTransform getTransform();

	/**
	 * Intersects the current clip with the given shape.
	 * 
	 * @param shape the clip shape
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void clip(final Shape shape) throws GraphicsException;

	/**
	 * Draws the outline of the given shape.
	 * 
	 * @param shape the shape to draw
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void draw(final Shape shape) throws GraphicsException;

	/**
	 * Fills the interior of the given shape.
	 * 
	 * @param shape the shape to fill
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void fill(final Shape shape) throws GraphicsException;

	/**
	 * Fills and then draws the outline of the given shape.
	 * 
	 * @param shape the shape to fill and draw
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void fillDraw(final Shape shape) throws GraphicsException;

	/**
	 * Draws an image.
	 * 
	 * @param image the image to draw
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void drawImage(final Image image) throws GraphicsException;

	/**
	 * 現在の塗りで形をガウスぼかし付きで塗る(box-shadow/text-shadowのblur用)。
	 * {@link Capability#GAUSSIAN_BLUR} に対応しない出力先では単に {@link #fill(Shape)} する。
	 *
	 * @param shape 塗る形
	 * @param sigma ぼかしの標準偏差(ユーザー空間単位)
	 */
	public default void fillBlurred(final Shape shape, final double sigma) throws GraphicsException {
		this.fill(shape);
	}

	/**
	 * Attempts to fill a shape with an exact Gaussian blur.
	 *
	 * <p>
	 * A {@code false} result guarantees that nothing was drawn. Callers can
	 * therefore emit their own approximation without risking duplicate output.
	 * The default implementation delegates to {@link #fillBlurred(Shape, double)}
	 * only when {@link Capability#GAUSSIAN_BLUR} is supported.
	 * </p>
	 *
	 * @param shape the shape to fill
	 * @param sigma the blur standard deviation in user-space units
	 * @return {@code true} if the blurred fill was drawn; {@code false} if
	 *         nothing was drawn
	 * @throws GraphicsException if a graphics error occurs while drawing
	 */
	public default boolean tryFillBlurred(final Shape shape, final double sigma) throws GraphicsException {
		if (!this.supports(Capability.GAUSSIAN_BLUR)) {
			return false;
		}
		this.fillBlurred(shape, sigma);
		return true;
	}

	/**
	 * 画像(通常は {@link #createGroupImage} で描いた層)に効果を掛けて描く。
	 * 対応しない出力先では効果を無視して {@link #drawImage(Image)} する。
	 */
	public default void drawImage(final Image image, final GroupEffects effects) throws GraphicsException {
		this.drawImage(image);
	}

	/**
	 * Draws a captured group with element-wide effects and reports the rendering
	 * path actually used.
	 *
	 * <p>
	 * {@link GroupEffectsResult#UNSUPPORTED} guarantees that nothing was drawn,
	 * so a caller can safely choose its own approximation. Other results mean
	 * that this method completed the draw, including a limit fallback when
	 * reported. The default implementation uses the backend's existing
	 * {@link #drawImage(Image, GroupEffects)} path when
	 * {@link Capability#GROUP_FILTER} is supported.
	 * </p>
	 *
	 * @param image   captured group image
	 * @param effects effects to apply to the whole group
	 * @return the rendering path used
	 * @throws GraphicsException if a graphics error occurs
	 */
	public default GroupEffectsResult drawGroupEffects(final Image image, final GroupEffects effects)
			throws GraphicsException {
		if (!this.supports(Capability.GROUP_FILTER)) {
			return GroupEffectsResult.UNSUPPORTED;
		}
		this.drawImage(image, effects);
		return GroupEffectsResult.VECTOR;
	}

	/**
	 * Draws text at the specified location.
	 * 
	 * @param text the text to draw
	 * @param x    the x-coordinate
	 * @param y    the y-coordinate
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void drawText(final Text text, final double x, final double y) throws GraphicsException;

	/**
	 * Creates a new group image graphics context.
	 * 
	 * @param width  the width of the group image
	 * @param height the height of the group image
	 * @return the group image graphics context
	 * @throws GraphicsException if a graphics error occurs
	 */
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException;

	/**
	 * Creates the group used to capture an element that may later receive
	 * element-wide filter effects.
	 *
	 * <p>
	 * Backends that need deferred replay may override this independently of
	 * {@link #createGroupImage(double, double)}. The default keeps the ordinary
	 * group implementation, preserving existing vector/group semantics.
	 * </p>
	 *
	 * @param width  the width of the captured group
	 * @param height the height of the captured group
	 * @return a group graphics context suitable for later
	 *         {@link #drawGroupEffects(Image, GroupEffects)}
	 * @throws GraphicsException if a graphics error occurs
	 */
	public default GroupImageGC createFilterGroup(final double width, final double height) throws GraphicsException {
		return this.createGroupImage(width, height);
	}
}
