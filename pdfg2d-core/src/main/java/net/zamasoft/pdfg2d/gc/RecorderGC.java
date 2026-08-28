package net.zamasoft.pdfg2d.gc;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
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
			Begin, End, SetLineWidth, SetLinePattern, SetLineCap, SetLineJoin,
			SetTextMode, SetStrokePaint, SetFillPaint, SetStrokeAlpha, SetFillAlpha, SetBlendMode,
			Transform, Clip, ResetState, DrawImage, Fill, Draw, FillDraw, DrawText {
	}

	public record Begin() implements Command {
	}

	public record End() implements Command {
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

	public record Fill(Shape shape) implements Command {
	}

	public record Draw(Shape shape) implements Command {
	}

	public record FillDraw(Shape shape) implements Command {
	}

	public record DrawText(Text text, double x, double y) implements Command {
	}

	protected final List<Command> contents = new ArrayList<>();

	/**
	 * Creates a new RecorderGC.
	 * 
	 * @param fm the font manager
	 */
	public RecorderGC(final FontManager fm) {
		super(fm);
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

	@Override
	public void setLineWidth(final double lineWidth) {
		super.setLineWidth(lineWidth);
		this.contents.add(new SetLineWidth(lineWidth));
	}

	@Override
	public void setLinePattern(final double[] linePattern) {
		super.setLinePattern(linePattern);
		this.contents.add(new SetLinePattern(linePattern));
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
		this.contents.add(new Transform(at));
	}

	@Override
	public void clip(final Shape clip) {
		super.clip(clip);
		this.contents.add(new Clip(clip));
	}

	@Override
	public void resetState() {
		super.resetState();
		this.contents.add(new ResetState());
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		super.drawImage(image);
		this.contents.add(new DrawImage(image));
	}

	@Override
	public void fill(final Shape shape) {
		super.fill(shape);
		this.contents.add(new Fill(shape));
	}

	@Override
	public void draw(final Shape shape) {
		super.draw(shape);
		this.contents.add(new Draw(shape));
	}

	@Override
	public void fillDraw(final Shape shape) {
		super.fillDraw(shape);
		this.contents.add(new FillDraw(shape));
	}

	@Override
	public void drawText(final Text text, final double x, final double y) throws GraphicsException {
		super.drawText(text, x, y);
		this.contents.add(new DrawText(text, x, y));
	}

	/**
	 * An image that records graphics operations.
	 */
	public static class RecorderImage extends NoOpImage {
		protected final Page page;

		/**
		 * Creates a new RecorderImage.
		 * 
		 * @param width  the width
		 * @param height the height
		 * @param page   the page containing recorded operations
		 */
		public RecorderImage(final double width, final double height, final Page page) {
			super(width, height);
			this.page = page;
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
			super(fm);
			this.width = width;
			this.height = height;
		}

		@Override
		public Image finish() throws GraphicsException {
			final var page = this.getPage();
			return new RecorderImage(this.width, this.height, page);
		}
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		return new RecorderGroupImageGC(this.getFontManager(), width, height);
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
			for (final var cmd : this.commands) {
				switch (cmd) {
					case Begin() -> states.push(gc.begin());
					case End() -> states.pop().close();
					case SetLineWidth(double width) -> gc.setLineWidth(width);
					case SetLinePattern(double[] pattern) -> gc.setLinePattern(pattern);
					case SetLineCap(LineCap lineCap) -> gc.setLineCap(lineCap);
					case SetLineJoin(LineJoin lineJoin) -> gc.setLineJoin(lineJoin);
					case SetTextMode(TextMode textMode) -> gc.setTextMode(textMode);
					case SetStrokePaint(Paint paint) -> gc.setStrokePaint(paint);
					case SetFillPaint(Paint paint) -> gc.setFillPaint(paint);
					case SetStrokeAlpha(float alpha) -> gc.setStrokeAlpha(alpha);
					case SetFillAlpha(float alpha) -> gc.setFillAlpha(alpha);
					case SetBlendMode(net.zamasoft.pdfg2d.gc.paint.BlendMode mode) -> gc.setBlendMode(mode);
					case Transform(AffineTransform at) -> gc.transform(at);
					case Clip(Shape shape) -> gc.clip(shape);
					case ResetState() -> gc.resetState();
					case DrawImage(Image image) -> gc.drawImage(image);
					case Fill(Shape shape) -> gc.fill(shape);
					case Draw(Shape shape) -> gc.draw(shape);
					case FillDraw(Shape shape) -> gc.fillDraw(shape);
					case DrawText(Text text, double x, double y) -> gc.drawText(text, x, y);
				}
			}
		}
	}
}