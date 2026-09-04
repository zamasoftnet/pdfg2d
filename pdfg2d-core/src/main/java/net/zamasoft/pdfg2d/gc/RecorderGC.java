package net.zamasoft.pdfg2d.gc;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * A graphics context that records all graphics operations.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class RecorderGC extends NoOpGC {
	public sealed interface Command permits
			Begin, End, BeginTextReplacement, EndTextReplacement,
			SetLineWidth, SetLinePattern, SetLineCap, SetLineJoin,
			SetTextMode, SetStrokePaint, SetFillPaint, SetStrokeAlpha, SetFillAlpha, SetBlendMode,
			Transform, Clip, ResetState, DrawImage, DrawImageEffects, Fill, FillBlurred, Draw, FillDraw, DrawText {
	}

	public record Begin() implements Command {
	}

	public record End() implements Command {
	}

	/** Opens a semantic text-replacement scope during replay. */
	public record BeginTextReplacement(String logicalText) implements Command {
	}

	/** Closes the current semantic text-replacement scope during replay. */
	public record EndTextReplacement() implements Command {
	}

	public record SetLineWidth(double width) implements Command {
	}

	public record SetLinePattern(double[] pattern) implements Command {
	}

	public record SetLineCap(LineCap lineCap) implements Command {
	}

	public record SetLineJoin(LineJoin lineJoin) implements Command {
	}

	public record SetTextMode(TextMode textMode) implements Command {
	}

	public record SetStrokePaint(Paint paint) implements Command {
	}

	public record SetFillPaint(Paint paint) implements Command {
	}

	public record SetStrokeAlpha(float alpha) implements Command {
	}

	public record SetFillAlpha(float alpha) implements Command {
	}

	/** Blend mode change (2026-08-29). */
	public record SetBlendMode(net.zamasoft.pdfg2d.gc.paint.BlendMode mode) implements Command {
	}

	public record Transform(AffineTransform at) implements Command {
	}

	public record Clip(Shape shape) implements Command {
	}

	public record ResetState() implements Command {
	}

	public record DrawImage(Image image) implements Command {
	}

	/** 効果付きの画像描画(2026-08-29)。再生先が対応すれば厳密に、しなければ効果なしで描かれる。 */
	public record DrawImageEffects(Image image, GroupEffects effects) implements Command {
	}

	public record Fill(Shape shape) implements Command {
	}

	/** ぼかし塗り(2026-08-29)。再生先が対応しなければ普通の塗りになる。 */
	public record FillBlurred(Shape shape, double sigma) implements Command {
	}

	public record Draw(Shape shape) implements Command {
	}

	public record FillDraw(Shape shape) implements Command {
	}

	public record DrawText(Text text, double x, double y) implements Command {
	}

	protected final List<Command> contents = new ArrayList<>();

	private final boolean allCapabilities;

	/** Union of visible recorded content in this recorder's local user space. */
	private Rectangle2D contentBounds;

	/** A non-finite drawing operation makes a reduced bound unsafe to use. */
	private boolean contentBoundsUnknown;

	/** Nested replacement scopes are collapsed to their outermost scope. */
	private boolean inTextReplacement;

	/**
	 * Creates a new RecorderGC.
	 * 
	 * @param fm the font manager
	 */
	public RecorderGC(final FontManager fm) {
		this(fm, false);
	}

	/**
	 * Creates a recorder, optionally advertising every capability while it
	 * captures deferred content. The all-capabilities variant prevents callers
	 * from committing to an approximation before the eventual replay backend is
	 * known.
	 *
	 * @param fm              the font manager
	 * @param allCapabilities whether {@link #supports(Capability)} returns true
	 */
	public RecorderGC(final FontManager fm, final boolean allCapabilities) {
		super(fm);
		this.allCapabilities = allCapabilities;
	}

	@Override
	public boolean supports(final Capability capability) {
		return this.allCapabilities && capability != null;
	}

	@Override
	public State begin() {
		final var state = super.begin();
		this.contents.add(new Begin());
		return state;
	}

	@Override
	protected void restoreState() {
		super.restoreState();
		this.contents.add(new End());
	}

	/**
	 * Records one semantic replacement scope for later vector replay. A nested
	 * request is a no-op so the replacement is never duplicated.
	 */
	@Override
	public State beginTextReplacement(final String logicalText) {
		if (this.inTextReplacement) {
			return GC.NO_OP_STATE;
		}
		final String replacement = java.util.Objects.requireNonNull(logicalText, "logicalText");
		this.inTextReplacement = true;
		this.contents.add(new BeginTextReplacement(replacement));
		return new State() {
			private boolean closed;

			@Override
			public void close() {
				if (this.closed) {
					return;
				}
				this.closed = true;
				RecorderGC.this.contents.add(new EndTextReplacement());
				RecorderGC.this.inTextReplacement = false;
			}
		};
	}

	@Override
	public void setLineWidth(final double lineWidth) {
		super.setLineWidth(lineWidth);
		this.contents.add(new SetLineWidth(lineWidth));
	}

	@Override
	public void setLinePattern(final double[] linePattern) {
		final double[] statePattern = linePattern == null ? null : linePattern.clone();
		super.setLinePattern(statePattern);
		this.contents.add(new SetLinePattern(statePattern == null ? null : statePattern.clone()));
	}

	@Override
	public void setLineJoin(final LineJoin lineJoin) {
		super.setLineJoin(lineJoin);
		this.contents.add(new SetLineJoin(lineJoin));
	}

	@Override
	public void setLineCap(final LineCap lineCap) {
		super.setLineCap(lineCap);
		this.contents.add(new SetLineCap(lineCap));
	}

	@Override
	public void setStrokePaint(final Paint paint) throws GraphicsException {
		super.setStrokePaint(paint);
		this.contents.add(new SetStrokePaint(paint));
	}

	@Override
	public void setFillPaint(final Paint paint) throws GraphicsException {
		super.setFillPaint(paint);
		this.contents.add(new SetFillPaint(paint));
	}

	@Override
	public void setStrokeAlpha(final float alpha) {
		super.setStrokeAlpha(alpha);
		this.contents.add(new SetStrokeAlpha(alpha));
	}

	@Override
	public void setFillAlpha(final float alpha) {
		super.setFillAlpha(alpha);
		this.contents.add(new SetFillAlpha(alpha));
	}

	@Override
	public void setBlendMode(final net.zamasoft.pdfg2d.gc.paint.BlendMode mode) {
		super.setBlendMode(mode);
		this.contents.add(new SetBlendMode(this.blendMode));
	}

	@Override
	public void setTextMode(final TextMode textMode) {
		super.setTextMode(textMode);
		this.contents.add(new SetTextMode(textMode));
	}

	@Override
	public void transform(final AffineTransform at) {
		super.transform(at);
		this.contents.add(new Transform(new AffineTransform(at)));
	}

	@Override
	public void clip(final Shape clip) {
		super.clip(clip);
		this.contents.add(new Clip(snapshot(clip)));
	}

	@Override
	public void resetState() {
		super.resetState();
		this.contents.add(new ResetState());
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		super.drawImage(image);
		this.growImageBounds(image, null);
		this.contents.add(new DrawImage(image));
	}

	@Override
	public void drawImage(final Image image, final GroupEffects effects) throws GraphicsException {
		this.growImageBounds(image, effects);
		this.contents.add(new DrawImageEffects(image, snapshot(effects)));
	}

	@Override
	public void fill(final Shape shape) {
		super.fill(shape);
		this.growShapeBounds(shape, 0);
		this.contents.add(new Fill(snapshot(shape)));
	}

	@Override
	public void fillBlurred(final Shape shape, final double sigma) {
		this.growShapeBounds(shape, finitePositive(sigma) ? 3 * sigma : 0);
		this.contents.add(new FillBlurred(snapshot(shape), sigma));
	}

	@Override
	public void draw(final Shape shape) {
		super.draw(shape);
		this.growShapeBounds(shape, Math.abs(this.lineWidth) / 2);
		this.contents.add(new Draw(snapshot(shape)));
	}

	@Override
	public void fillDraw(final Shape shape) {
		super.fillDraw(shape);
		this.growShapeBounds(shape, Math.abs(this.lineWidth) / 2);
		this.contents.add(new FillDraw(snapshot(shape)));
	}

	@Override
	public void drawText(final Text text, final double x, final double y) throws GraphicsException {
		super.drawText(text, x, y);
		if (text != null) {
			final double advance = text.getAdvance();
			final double ascent = text.getAscent();
			final double descent = text.getDescent();
			final double squareRadius = Math.max(Math.abs(advance), Math.abs(ascent + descent));
			this.growTransformedBounds(new Rectangle2D.Double(x, y - ascent, advance, ascent + descent));
			this.growTransformedBounds(new Rectangle2D.Double(x - squareRadius, y - squareRadius,
					2 * squareRadius, 2 * squareRadius));
		}
		this.contents.add(new DrawText(text, x, y));
	}

	private void growShapeBounds(final Shape shape, final double padding) {
		if (shape == null) {
			return;
		}
		final Rectangle2D bounds = shape.getBounds2D();
		if (!finite(bounds) || !Double.isFinite(padding)) {
			this.contentBoundsUnknown = true;
			return;
		}
		this.growTransformedBounds(expand(bounds, Math.max(0, padding)));
	}

	private void growImageBounds(final Image image, final GroupEffects effects) {
		if (image == null) {
			return;
		}
		Rectangle2D bounds = null;
		if (image instanceof RecorderImage recorded) {
			bounds = recorded.getContentBounds();
		}
		if (bounds == null || bounds.isEmpty()) {
			bounds = new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
		}
		if (!finite(bounds)) {
			this.contentBoundsUnknown = true;
			return;
		}
		if (effects != null) {
			final double blurRadius = finitePositive(effects.blurSigma()) ? 3 * effects.blurSigma() : 0;
			final Rectangle2D filtered = expand(bounds, blurRadius);
			bounds = filtered;
			final GroupEffects.DropShadow shadow = effects.dropShadow();
			if (shadow != null) {
				if (!Double.isFinite(shadow.dx()) || !Double.isFinite(shadow.dy())
						|| !Double.isFinite(shadow.sigma())) {
					this.contentBoundsUnknown = true;
					return;
				}
				final double shadowRadius = finitePositive(shadow.sigma()) ? 3 * shadow.sigma() : 0;
				final Rectangle2D shadowBounds = expand(filtered, shadowRadius);
				shadowBounds.setRect(shadowBounds.getX() + shadow.dx(), shadowBounds.getY() + shadow.dy(),
						shadowBounds.getWidth(), shadowBounds.getHeight());
				bounds = bounds.createUnion(shadowBounds);
			}
		}
		this.growTransformedBounds(bounds);
	}

	private void growTransformedBounds(final Rectangle2D bounds) {
		if (bounds == null || bounds.isEmpty()) {
			return;
		}
		final Rectangle2D transformed = this.transform.createTransformedShape(bounds).getBounds2D();
		if (!finite(transformed)) {
			this.contentBoundsUnknown = true;
			return;
		}
		if (this.contentBounds == null) {
			this.contentBounds = new Rectangle2D.Double(transformed.getX(), transformed.getY(),
					transformed.getWidth(), transformed.getHeight());
		} else {
			this.contentBounds = this.contentBounds.createUnion(transformed);
		}
	}

	protected final Rectangle2D contentBoundsSnapshot() {
		if (this.contentBoundsUnknown || this.contentBounds == null) {
			return null;
		}
		return new Rectangle2D.Double(this.contentBounds.getX(), this.contentBounds.getY(),
				this.contentBounds.getWidth(), this.contentBounds.getHeight());
	}

	private static Rectangle2D expand(final Rectangle2D bounds, final double padding) {
		return new Rectangle2D.Double(bounds.getX() - padding, bounds.getY() - padding,
				bounds.getWidth() + 2 * padding, bounds.getHeight() + 2 * padding);
	}

	private static boolean finitePositive(final double value) {
		return Double.isFinite(value) && value > 0;
	}

	private static boolean finite(final Rectangle2D bounds) {
		return bounds != null
				&& Double.isFinite(bounds.getMinX()) && Double.isFinite(bounds.getMinY())
				&& Double.isFinite(bounds.getMaxX()) && Double.isFinite(bounds.getMaxY());
	}

	private static Shape snapshot(final Shape shape) {
		return shape == null ? null : new Path2D.Double(shape);
	}

	private static GroupEffects snapshot(final GroupEffects effects) {
		if (effects == null || effects.colorMatrix() == null) {
			return effects;
		}
		return new GroupEffects(effects.colorMatrix().clone(), effects.blurSigma(), effects.dropShadow(),
				effects.opacity());
	}

	/**
	 * An image that records graphics operations.
	 */
	public static class RecorderImage extends NoOpImage {
		protected final Page page;
		private final Rectangle2D contentBounds;

		/**
		 * Creates a new RecorderImage.
		 * 
		 * @param width  the width
		 * @param height the height
		 * @param page   the page containing recorded operations
		 */
		public RecorderImage(final double width, final double height, final Page page) {
			this(width, height, page, null);
		}

		/**
		 * Creates a recorded image with its conservative visible-content bounds.
		 *
		 * @param width         the nominal width
		 * @param height        the nominal height
		 * @param page          the page containing recorded operations
		 * @param contentBounds visible content in group-local user space, or
		 *                      {@code null} when no bound is available
		 */
		public RecorderImage(final double width, final double height, final Page page,
				final Rectangle2D contentBounds) {
			super(width, height);
			this.page = page;
			this.contentBounds = contentBounds == null ? null
					: new Rectangle2D.Double(contentBounds.getX(), contentBounds.getY(),
							contentBounds.getWidth(), contentBounds.getHeight());
		}

		/**
		 * Returns a defensive copy of the recorded content bounds.
		 *
		 * @return content bounds in group-local user space, or {@code null} when
		 *         nothing was drawn (or a finite conservative bound was unavailable)
		 */
		public Rectangle2D getContentBounds() {
			return this.contentBounds == null ? null
					: new Rectangle2D.Double(this.contentBounds.getX(), this.contentBounds.getY(),
							this.contentBounds.getWidth(), this.contentBounds.getHeight());
		}

		@Override
		public void drawTo(final GC gc) throws GraphicsException {
			this.page.drawTo(gc);
		}
	}

	/**
	 * A group image graphics context that records operations.
	 */
	public static class RecorderGroupImageGC extends RecorderGC implements GroupImageGC {
		private final double width, height;

		/**
		 * Creates a new RecorderGroupImageGC.
		 * 
		 * @param fm     the font manager
		 * @param width  the width
		 * @param height the height
		 */
		public RecorderGroupImageGC(final FontManager fm, final double width, final double height) {
			this(fm, width, height, false);
		}

		/**
		 * Creates a recording group with an explicit capability-advertising mode.
		 *
		 * @param fm              the font manager
		 * @param width           the width
		 * @param height          the height
		 * @param allCapabilities whether all capabilities are advertised while recording
		 */
		public RecorderGroupImageGC(final FontManager fm, final double width, final double height,
				final boolean allCapabilities) {
			super(fm, allCapabilities);
			this.width = width;
			this.height = height;
		}

		@Override
		public Image finish() throws GraphicsException {
			final var page = this.getPage();
			return new RecorderImage(this.width, this.height, page, this.contentBoundsSnapshot());
		}
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		return new RecorderGroupImageGC(this.getFontManager(), width, height, this.allCapabilities);
	}

	/**
	 * Returns the page containing the recorded operations.
	 * 
	 * @return the recorded page
	 */
	public Page getPage() {
		return new Page(List.copyOf(this.contents));
	}

	/**
	 * Represents a page of recorded graphics operations.
	 */
	public record Page(List<Command> commands) {

		/**
		 * Replays the recorded operations to the given graphics context.
		 * 
		 * @param gc the graphics context
		 */
		public void drawTo(final GC gc) {
			// Balanced Begin/End records are guaranteed by construction: an
			// End is only recorded when a State handle is closed.
			final var states = new ArrayDeque<GC.State>();
			final var replacements = new ArrayDeque<GC.State>();
			for (final var cmd : this.commands) {
				switch (cmd) {
					case Begin() -> states.push(gc.begin());
					case End() -> states.pop().close();
					case BeginTextReplacement(String logicalText) ->
						replacements.push(gc.beginTextReplacement(logicalText));
					case EndTextReplacement() -> replacements.pop().close();
					case SetLineWidth(double width) -> gc.setLineWidth(width);
					case SetLinePattern(double[] pattern) -> gc.setLinePattern(pattern == null ? null : pattern.clone());
					case SetLineCap(LineCap lineCap) -> gc.setLineCap(lineCap);
					case SetLineJoin(LineJoin lineJoin) -> gc.setLineJoin(lineJoin);
					case SetTextMode(TextMode textMode) -> gc.setTextMode(textMode);
					case SetStrokePaint(Paint paint) -> gc.setStrokePaint(paint);
					case SetFillPaint(Paint paint) -> gc.setFillPaint(paint);
					case SetStrokeAlpha(float alpha) -> gc.setStrokeAlpha(alpha);
					case SetFillAlpha(float alpha) -> gc.setFillAlpha(alpha);
					case SetBlendMode(net.zamasoft.pdfg2d.gc.paint.BlendMode mode) -> gc.setBlendMode(mode);
					case Transform(AffineTransform at) -> gc.transform(new AffineTransform(at));
					case Clip(Shape shape) -> gc.clip(shape);
					case ResetState() -> gc.resetState();
					case DrawImage(Image image) -> gc.drawImage(materialize(image, gc));
					case DrawImageEffects(Image image, GroupEffects effects) ->
						gc.drawImage(materialize(image, gc), effects);
					case Fill(Shape shape) -> gc.fill(shape);
					case FillBlurred(Shape shape, double sigma) -> gc.fillBlurred(shape, sigma);
					case Draw(Shape shape) -> gc.draw(shape);
					case FillDraw(Shape shape) -> gc.fillDraw(shape);
					case DrawText(Text text, double x, double y) -> gc.drawText(text, x, y);
				}
			}
		}

		private static Image materialize(final Image image, final GC gc) {
			if (!(image instanceof RecorderImage recorded)) {
				return image;
			}
			final GroupImageGC group = gc.createGroupImage(recorded.getWidth(), recorded.getHeight());
			recorded.page.drawTo(group);
			return group.finish();
		}
	}
}
