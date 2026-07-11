package net.zamasoft.pdfg2d.pdf.gc;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.CMYKColor;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.Pattern;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.PDFGraphicsOutput;
import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSource.Type;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/* PDF Operator Reference
 * 
 * w	line width
 * J	line cap
 * j	line join
 * M	miter limit
 * d	line dash pattern
 * ri	rendering intents
 * i	flatness tolerance
 * gs	special graphics state
 * 
 * q	save graphics state
 * Q	restore	graphics state
 * cm	current transformation matrics
 * 
 * m	moveTo
 * l	lineTo
 * c	curveTo (1,2,3)
 * v	curveTo (2,3)
 * y	curveTo (1,3)
 * h	closePath
 * re	rectangle
 * 
 * S	stroke
 * s	[h S]
 * f	fill Bonzero Winding Number Rule
 * F	[f]
 * f*	fill Even-Odd Rule
 * B	[f S]
 * B*	[f* S]
 * b	[h B]
 * b*	[h B*]
 * n	nop
 * 
 * W	clip Bonzero Winding Number Rule
 * W*	clip Even-Odd Rule
 * 
 * BT
 * ET
 * 
 * Tc
 * Tw
 * Tz
 * TL
 * Tf
 * Tr
 * Ts
 * 
 * Td
 * TD
 * Tm
 * T*
 * 
 * Tj
 * TJ
 * '
 * "
 * 
 * d0
 * d1
 * 
 * CS	stroke color space
 * cs	nonstroke color space
 * SC	stroke color
 * SCN
 * sc
 * scn
 * G
 * g
 * RG
 * rg
 * K
 * k
 * 
 * sh
 * 
 * BI
 * ID
 * EI
 * 
 * Do	draw object
 * 
 * MP
 * DP
 * BMC
 * BDC
 * EMC
 * 
 * BX
 * EX
 * 
 * sh	shading pattern
 */

/**
 * PDF Graphics Context — translates high-level {@link GC} drawing calls into
 * the binary PDF operator stream written to a {@link PDFGraphicsOutput}.
 * <p>
 * Each drawing operation (path construction, painting, text, image placement,
 * clipping, transparency, colour-space selection) is mapped to the
 * corresponding PDF content-stream operators defined in ISO 32000-1 (PDF 1.7).
 * A short reference to the operators used is kept in the block comment above
 * this class declaration.
 * </p>
 * <p>
 * Graphics-state save/restore ({@code q}/{@code Q}) is modelled by the
 * {@link GraphicsState} record.  Shadings (gradients) and extended graphics
 * states (transparency) are cached by structural equality so that duplicate
 * resource entries are not emitted.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PDFGC implements GC, Closeable {
	private static final Logger LOG = Logger.getLogger(PDFGC.class.getName());

	private static final boolean DEBUG = false;

	protected final PDFGraphicsOutput out;

	private static final double ONE_THIRD = 1.0 / 3.0;
	private static final double TWO_THIRD = 2.0 / 3.0;

	private record ExtGStateKey(float strokeAlpha, float fillAlpha, byte strokeOverprint, byte fillOverprint) {
	}

	/**
	 * Snapshot of the graphics state for gsave/grestore operations.
	 */
	record GraphicsState(
			XGraphicsState gstate,
			double lineWidth,
			LineCap lineCap,
			LineJoin lineJoin,
			double[] linePattern,
			Paint strokePaint,
			Paint fillPaint,
			TextMode textMode,
			float strokeAlpha,
			float fillAlpha,
			byte strokeOverprint,
			byte fillOverprint,
			AffineTransform actualTransform) {

		GraphicsState(final PDFGC gc) {
			this(
					null,
					gc.lineWidth,
					gc.lineCap,
					gc.lineJoin,
					gc.linePattern,
					gc.strokePaint,
					gc.fillPaint,
					gc.textMode,
					gc.strokeAlpha,
					gc.fillAlpha,
					gc.strokeOverprint,
					gc.fillOverprint,
					gc.actualTransform != null ? new AffineTransform(gc.actualTransform) : null);
		}

		/**
		 * Restores this state back to the specified GC.
		 *
		 * @param gc The target graphics context.
		 */
		void restore(final PDFGC gc) {
			gc.lineWidth = this.lineWidth;
			gc.lineCap = this.lineCap;
			gc.lineJoin = this.lineJoin;
			gc.linePattern = this.linePattern;
			gc.strokePaint = this.strokePaint;
			gc.fillPaint = this.fillPaint;
			gc.textMode = this.textMode;
			gc.strokeAlpha = this.strokeAlpha;
			gc.fillAlpha = this.fillAlpha;
			gc.strokeOverprint = this.strokeOverprint;
			gc.fillOverprint = this.fillOverprint;
			gc.actualTransform = this.actualTransform;
		}

		GraphicsState withXState(final XGraphicsState xState) {
			return new GraphicsState(
					xState, lineWidth, lineCap, lineJoin, linePattern, strokePaint, fillPaint,
					textMode, strokeAlpha, fillAlpha, strokeOverprint, fillOverprint,
					actualTransform);
		}

		GraphicsState withoutXState() {
			return new GraphicsState(
					null, lineWidth, lineCap, lineJoin, linePattern, strokePaint, fillPaint,
					textMode, strokeAlpha, fillAlpha, strokeOverprint, fillOverprint,
					actualTransform);
		}
	}

	/**
	 * Represents the current PDF graphics environment.
	 */
	record XGraphicsState(
			double lineWidth,
			LineCap lineCap,
			LineJoin lineJoin,
			double[] linePattern,
			Paint strokePaint,
			Paint fillPaint,
			float fillAlpha,
			float strokeAlpha,
			byte fillOverprint,
			byte strokeOverprint,
			double letterSpacing,
			TextMode textMode,
			String smask) {

		XGraphicsState(final PDFGC gc) {
			this(
					gc.xlineWidth,
					gc.xlineCap,
					gc.xlineJoin,
					gc.xlinePattern,
					gc.xstrokePaint,
					gc.xfillPaint,
					gc.xfillAlpha,
					gc.xstrokeAlpha,
					gc.xfillOverprint,
					gc.xstrokeOverprint,
					gc.xletterSpacing,
					gc.xtextMode,
					gc.xsmask);
		}

		/**
		 * Restores the PDF environment state back to the GC.
		 *
		 * @param gc The target graphics context.
		 */
		void restore(final PDFGC gc) {
			gc.xlineWidth = this.lineWidth;
			gc.xlineCap = this.lineCap;
			gc.xlineJoin = this.lineJoin;
			gc.xlinePattern = this.linePattern;
			gc.xstrokePaint = this.strokePaint;
			gc.xfillPaint = this.fillPaint;
			gc.xletterSpacing = this.letterSpacing;
			gc.xtextMode = this.textMode;
			gc.xfillAlpha = this.fillAlpha;
			gc.xstrokeAlpha = this.strokeAlpha;
			gc.xfillOverprint = this.fillOverprint;
			gc.xstrokeOverprint = this.strokeOverprint;
			gc.xsmask = this.smask;
		}
	}

	private final List<GraphicsState> stack = new ArrayList<>();

	AffineTransform transform = null;

	private AffineTransform actualTransform = null;

	private Shape clip = null;

	/** Line cap style. */
	private LineCap lineCap = LineCap.SQUARE;

	/** Current PDF line cap style. */
	private LineCap xlineCap = LineCap.SQUARE;

	/** Line join style. */
	private LineJoin lineJoin = LineJoin.MITER;

	/** Current PDF line join style. */
	private LineJoin xlineJoin = LineJoin.MITER;

	/** Line width. */
	private double lineWidth = 1;

	/** Current PDF line width. */
	private double xlineWidth = 1;

	/** Line dash pattern. */
	private double[] linePattern = STROKE_SOLID;

	/** Current PDF line dash pattern. */
	private double[] xlinePattern = STROKE_SOLID;

	/** Stroke paint. */
	Paint strokePaint = GrayColor.BLACK;

	/** Current PDF stroke paint. */
	Paint xstrokePaint = GrayColor.BLACK;

	/** Fill paint. */
	Paint fillPaint = GrayColor.BLACK;

	/** Current PDF fill paint. */
	Paint xfillPaint = GrayColor.BLACK;

	double xletterSpacing = 0;

	/** Text rendering mode. */
	TextMode textMode = TextMode.FILL;

	/** Current PDF text rendering mode. */
	TextMode xtextMode = TextMode.FILL;

	/** Stroke opacity. */
	public float strokeAlpha = 1;

	/** Current PDF stroke opacity. */
	public float xstrokeAlpha = 1;

	/** Fill opacity. */
	public float fillAlpha = 1;

	/** Current PDF fill opacity. */
	public float xfillAlpha = 1;

	/** Stroke overprint mode. */
	public byte strokeOverprint = 0;

	/** Current PDF stroke overprint mode. */
	public byte xstrokeOverprint = 0;

	/** Fill overprint mode. */
	public byte fillOverprint = 0;

	/** Current PDF fill overprint mode. */
	public byte xfillOverprint = 0;

	/**
	 * ExtGState name of the soft mask currently active in the PDF, or
	 * {@code null}. Set when painting gradients with per-stop alpha and reset
	 * with an {@code /SMask /None} ExtGState for other paints.
	 */
	String xsmask = null;

	/** Document-wide cache of pattern/shading resource names (see {@link PaintResources}). */
	final Map<Object, String> resourceCache;

	private final double[] cord = new double[6];

	private int qDepth = 0;

	/** Whether the default rendering intent has been written to this stream. */
	private boolean riWritten = false;

	/**
	 * True while a marked-content sequence opened by this GC is active;
	 * suppresses nested marks (e.g. fills performed inside outline text).
	 */
	private boolean inMark = false;

	/** Alternate text for the image currently being drawn, if any. */
	private String pendingAlt = null;

	final PDFParams.Version pdfVersion;

	/** True when the target profile forbids non-embedded fonts (PDF/A, PDF/X, PDF/UA). */
	final boolean requireEmbeddedFonts;

	@SuppressWarnings("unchecked")
	PDFGC(final PDFGraphicsOutput out, final Map<Object, String> resourceCache) {
		this.out = out;
		if (resourceCache == null) {
			final var writer = out.getPdfWriter();
			var cache = (Map<Object, String>) writer.getAttribute("sfResCache");
			if (cache == null) {
				cache = new HashMap<>();
				writer.putAttribute("sfResCache", cache);
			}
			this.resourceCache = cache;
		} else {
			this.resourceCache = resourceCache;
		}
		final var params = this.out.getPdfWriter().getParams();
		this.pdfVersion = params.version();
		this.requireEmbeddedFonts = this.pdfVersion.isPdfA() || this.pdfVersion.isPdfX()
				|| (params.tagged() != null && params.tagged().pdfua());
		this.stack.add(new GraphicsState(this));
	}

	public PDFGC(final PDFGraphicsOutput out) {
		this(out, null);
	}

	public FontManager getFontManager() {
		return this.getPdfWriter().getFontManager();
	}

	public PDFGraphicsOutput getPDFGraphicsOutput() {
		return this.out;
	}

	public PDFWriter getPdfWriter() {
		return this.out.getPdfWriter();
	}

	@Override
	public State begin() throws GraphicsException {
		if (DEBUG) {
			LOG.fine("begin");
		}
		try {
			this.applyTransform();
			this.applyClip();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		this.stack.add(new GraphicsState(this));
		return new State() {
			private boolean closed;

			@Override
			public void close() throws GraphicsException {
				if (this.closed) {
					return;
				}
				this.closed = true;
				PDFGC.this.restoreState();
			}
		};
	}

	/**
	 * Restores the most recently saved graphics state; invoked exactly once
	 * when a {@link State} returned by {@link #begin()} is closed.
	 */
	private void restoreState() throws GraphicsException {
		if (DEBUG) {
			LOG.fine("end");
		}
		try {
			this.grestore();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		final var state = this.stack.removeLast();
		state.restore(this);
		if (this.stack.isEmpty()) {
			this.transform = null;
		}
		this.clip = null;
	}

	@Override
	public void resetState() throws GraphicsException {
		if (DEBUG) {
			LOG.fine("reset");
		}
		try {
			this.grestore();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		final var state = this.stack.getLast();
		state.restore(this);
		this.transform = null;
		this.clip = null;
	}

	@Override
	public void setLineWidth(final double lineWidth) {
		if (DEBUG) {
			System.err.println("setLineWidth: " + lineWidth);
		}
		this.lineWidth = lineWidth;
	}

	@Override
	public double getLineWidth() {
		return this.lineWidth;
	}

	@Override
	public void setLinePattern(final double[] linePattern) {
		if (DEBUG) {
			System.err.println("setLinePattern: " + linePattern);
		}
		if (linePattern != null && linePattern.length > 0) {
			this.linePattern = linePattern;
		} else {
			this.linePattern = STROKE_SOLID;
		}
	}

	@Override
	public double[] getLinePattern() {
		return this.linePattern;
	}

	@Override
	public void setLineJoin(final LineJoin lineJoin) {
		if (DEBUG) {
			System.err.println("setLineJoin: " + lineJoin);
		}
		this.lineJoin = lineJoin;
	}

	@Override
	public LineJoin getLineJoin() {
		return this.lineJoin;
	}

	@Override
	public void setLineCap(final LineCap lineCap) {
		if (DEBUG) {
			System.err.println("setLineCap: " + lineCap);
		}
		this.lineCap = lineCap;
	}

	@Override
	public LineCap getLineCap() {
		return this.lineCap;
	}

	@Override
	public void setStrokePaint(final Paint paint) throws GraphicsException {
		if (DEBUG) {
			System.err.println("setStrokePaint: " + paint);
		}
		this.setPaint(paint, false);
	}

	@Override
	public Paint getStrokePaint() {
		return this.strokePaint;
	}

	@Override
	public void setFillPaint(final Paint paint) throws GraphicsException {
		if (DEBUG) {
			System.err.println("setFillPaint: " + paint);
		}
		this.setPaint(paint, true);
	}

	@Override
	public Paint getFillPaint() {
		return this.fillPaint;
	}

	protected void setPaint(final Paint paint, final boolean fill) throws GraphicsException {
		if (fill) {
			this.fillPaint = paint;
			this.fillAlpha = 1;
			this.fillOverprint = CMYKColor.OVERPRINT_NONE;
		} else {
			this.strokePaint = paint;
			this.strokeAlpha = 1;
			this.strokeOverprint = CMYKColor.OVERPRINT_NONE;
		}

		switch (paint) {
			case RGBAColor rgba -> {
				if (fill) {
					this.fillAlpha = rgba.getAlpha();
				} else {
					this.strokeAlpha = rgba.getAlpha();
				}
			}
			case CMYKColor cmyk -> {
				if (fill) {
					this.fillOverprint = cmyk.getOverprint();
				} else {
					this.strokeOverprint = cmyk.getOverprint();
				}
			}
			case net.zamasoft.pdfg2d.gc.paint.SpotColor spot -> {
				if (fill) {
					this.fillOverprint = spot.overprint();
				} else {
					this.strokeOverprint = spot.overprint();
				}
			}
			case Color color -> {
				// Other color types (RGB, Gray) - defaults already set
			}
			case Paint other -> {
				// Pattern, gradients - defaults already set
			}
		}
	}

	@Override
	public float getStrokeAlpha() {
		return this.strokeAlpha;
	}

	@Override
	public void setStrokeAlpha(final float strokeAlpha) {
		this.strokeAlpha = strokeAlpha;
	}

	@Override
	public float getFillAlpha() {
		return this.fillAlpha;
	}

	@Override
	public void setFillAlpha(final float fillAlpha) {
		this.fillAlpha = fillAlpha;
	}

	@Override
	public void setTextMode(final TextMode textMode) {
		if (DEBUG) {
			LOG.fine("setTextMode: " + textMode);
		}
		this.textMode = textMode;
	}

	@Override
	public TextMode getTextMode() {
		return this.textMode;
	}

	@Override
	public void transform(final AffineTransform at) throws GraphicsException {
		if (DEBUG) {
			LOG.fine("transform: " + at);
		}
		if (at == null || at.isIdentity()) {
			return;
		}
		assert !Double.isNaN(at.getTranslateX());
		assert !Double.isNaN(at.getTranslateY());
		assert !Double.isNaN(at.getScaleX());
		assert !Double.isNaN(at.getScaleY());
		assert !Double.isNaN(at.getShearX());
		assert !Double.isNaN(at.getShearY());
		try {
			this.applyClip();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		if (this.transform == null) {
			this.transform = new AffineTransform(at);
		} else {
			this.transform.concatenate(at);
		}
		if (this.actualTransform == null) {
			this.actualTransform = new AffineTransform(at);
		} else {
			this.actualTransform.concatenate(at);
		}
	}

	@Override
	public AffineTransform getTransform() {
		return this.actualTransform == null ? null : new AffineTransform(this.actualTransform);
	}

	@Override
	public void clip(final Shape clip) throws GraphicsException {
		if (DEBUG) {
			LOG.fine("clip: " + (clip == null ? clip : clip.getBounds2D()));
		}
		try {
			this.applyTransform();
			this.applyClip();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		this.clip = clip;
	}

	@Override
	public void fill(final Shape shape) throws GraphicsException {
		if (DEBUG) {
			LOG.fine("fill: " + shape.getBounds2D());
		}
		try {
			this.applyStates();
			// Plain vector paths default to artifacts (typically decoration
			// such as backgrounds and rules); real content is marked by
			// drawText/drawImage.
			final var marked = this.beginArtifactTagged();
			final int winding;
			if (shape instanceof Rectangle2D r) {
				if (this.out.equals(r.getWidth(), 0.0) || this.out.equals(r.getHeight(), 0.0)) {
					return;
				}
				winding = PathIterator.WIND_NON_ZERO;
				this.plotRect(r);
			} else {
				final var i = shape.getPathIterator(null);
				winding = i.getWindingRule();
				this.plot(i);
			}

			final var operator = switch (winding) {
				case PathIterator.WIND_NON_ZERO -> "f";
				case PathIterator.WIND_EVEN_ODD -> "f*";
				default -> throw new IllegalStateException("Unknown winding rule: " + winding);
			};
			this.out.writeOperator(operator);
			this.endTagged(marked);
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	@Override
	public void draw(final Shape shape) throws GraphicsException {
		if (DEBUG) {
			LOG.fine("draw: " + shape.getBounds2D());
		}
		try {
			this.applyStates();
			final var marked = this.beginArtifactTagged();
			final boolean close;
			if (shape instanceof Rectangle2D r) {
				close = false;
				this.plotRect(r);
			} else {
				final var i = shape.getPathIterator(null);
				close = this.plot(i);
			}

			this.out.writeOperator(close ? "s" : "S");
			this.endTagged(marked);
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	@Override
	public void fillDraw(final Shape shape) throws GraphicsException {
		if (DEBUG) {
			LOG.fine("fillDraw: " + shape.getBounds2D());
		}
		try {
			this.applyStates();
			final var marked = this.beginArtifactTagged();
			final int winding;
			final boolean close;
			if (shape instanceof Rectangle2D r) {
				winding = PathIterator.WIND_NON_ZERO;
				close = false;
				this.plotRect(r);
			} else {
				final var i = shape.getPathIterator(null);
				winding = i.getWindingRule();
				close = this.plot(i);
			}

			final var operator = switch (winding) {
				case PathIterator.WIND_NON_ZERO -> close ? "b" : "B";
				case PathIterator.WIND_EVEN_ODD -> close ? "b*" : "B*";
				default -> throw new IllegalStateException("Unknown winding rule: " + winding);
			};
			this.out.writeOperator(operator);
			this.endTagged(marked);
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		if (DEBUG) {
			LOG.fine("drawImage: " + image);
		}
		try {
			this.applyStates();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		this.pendingAlt = image.getAltString();
		try {
			image.drawTo(this);
		} finally {
			this.pendingAlt = null;
		}
	}

	public void drawPDFImage(final String name, final double width, final double height) throws GraphicsException {
		try {
			this.applyStates();
			try (final var state = this.begin()) {
				// Images are real content: tag as Figure with an alternate
				// description when available.
				final var alt = (this.pendingAlt != null) ? this.pendingAlt : "Image";
				final var mcid = this.beginTagged("Figure", alt);

				this.gsave();
				this.out.writeReal(width);
				this.out.writeReal(0);
				this.out.writeReal(0);
				this.out.writeReal(height);
				this.out.writePosition(0, height);
				this.out.writeOperator("cm");

				this.out.useResource("XObject", name);
				this.out.writeName(name);
				this.out.writeOperator("Do");

				state.close();
				this.endTagged(mcid >= 0);
			}
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	@Override
	public void drawText(final Text text, final double x, final double y) throws GraphicsException {
		if (DEBUG) {
			System.err.println("drawText: " + text);
		}
		if (text.getGlyphCount() <= 0) {
			return;
		}

		final int textMcid;
		try {
			textMcid = this.beginTagged("P", null);
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		try {
			this.drawTextContent(text, x, y);
		} finally {
			try {
				this.endTagged(textMcid >= 0);
			} catch (IOException e) {
				throw new GraphicsException(e);
			}
		}
	}

	private void drawTextContent(final Text text, final double x, final double y) throws GraphicsException {
		PDFTextRenderer.drawText(this, text, x, y);
	}

	private static class PdfGroupImageGC extends PDFGC implements GroupImageGC {
		PdfGroupImageGC(PDFGroupImage image) {
			super(image);
		}

		public Image finish() throws GraphicsException {
			PDFGroupImage image = (PDFGroupImage) this.out;
			try {
				image.close();
			} catch (IOException e) {
				throw new GraphicsException(e);
			}
			return image;
		}
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) {
		try {
			final var image = this.getPdfWriter().createGroupImage(width, height);
			return new PdfGroupImageGC(image);
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	/**
	 * Opens an optional-content (layer) marked-content sequence: content
	 * drawn until {@link #endLayer()} belongs to the given layer and follows
	 * its view/print visibility. May be nested with other marked content.
	 *
	 * @param layer the layer to attribute content to
	 * @throws GraphicsException if an I/O error occurs
	 */
	public void beginLayer(final net.zamasoft.pdfg2d.pdf.PDFOptionalContentGroup layer) throws GraphicsException {
		try {
			this.applyStates();
			this.out.useResource("Properties", layer.getResourceName());
			this.out.writeName("OC");
			this.out.writeName(layer.getResourceName());
			this.out.writeOperator("BDC");
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	/**
	 * Closes the layer opened by {@link #beginLayer}.
	 *
	 * @throws GraphicsException if an I/O error occurs
	 */
	public void endLayer() throws GraphicsException {
		try {
			this.out.writeOperator("EMC");
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	/**
	 * Opens a real-content marked-content sequence when this GC draws onto a
	 * page of a tagged document. Nested calls are suppressed.
	 *
	 * @return the MCID, or {@code -1} when nothing was opened
	 */
	private int beginTagged(final String role, final String alt) throws IOException {
		if (this.inMark) {
			return -1;
		}
		final var mcid = this.out.beginMark(role, alt);
		if (mcid >= 0) {
			this.inMark = true;
		}
		return mcid;
	}

	/** Opens an artifact sequence (decorative content) when tagged. */
	private boolean beginArtifactTagged() throws IOException {
		if (this.inMark) {
			return false;
		}
		final var began = this.out.beginArtifact();
		if (began) {
			this.inMark = true;
		}
		return began;
	}

	private void endTagged(final boolean began) throws IOException {
		if (began) {
			this.out.endMark();
			this.inMark = false;
		}
	}

	/**
	 * Outputs the current transform instruction (cm) and clears the transform
	 * buffer.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	protected void applyTransform() throws IOException {
		if (this.transform != null) {
			this.gsave();
			this.out.writeTransform(this.transform);
			this.out.writeOperator("cm");
			this.transform = null;
		}
	}

	/**
	 * Outputs the current clip instruction (W or W*) and clears the clip buffer.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	protected void applyClip() throws IOException {
		if (this.clip != null) {
			this.gsave();
			final int winding;
			if (this.clip instanceof Rectangle2D r) {
				winding = PathIterator.WIND_NON_ZERO;
				this.out.writeRect(r.getX(), r.getY(), r.getWidth(), r.getHeight());
				this.out.writeOperator("re");
			} else {
				final var i = this.clip.getPathIterator(null);
				winding = i.getWindingRule();
				this.plot(i);
			}

			final var operator = switch (winding) {
				case PathIterator.WIND_NON_ZERO -> "W";
				case PathIterator.WIND_EVEN_ODD -> "W*";
				default -> throw new IllegalStateException("Unknown winding rule: " + winding);
			};
			this.out.writeOperator(operator);
			this.out.writeOperator("n");
			this.clip = null;
		}
	}

	/**
	 * Retrieves the PDF resource name for the given paint.
	 *
	 * @param paint The paint object.
	 * @return The resource name.
	 * @throws GraphicsException if an error occurs while creating the resource.
	 */
	private String getPaintName(final Paint paint) throws GraphicsException {
		return PaintResources.paintName(this, paint);
	}

	/**
	 * Writes the shading function entries for gradients. This remains a
	 * protected hook so subclasses can customize gradient encoding; the default
	 * implementation lives in {@link PaintResources}.
	 *
	 * @param sout      The output stream.
	 * @param colors    Array of colors.
	 * @param fractions Array of color stop fractions.
	 * @throws IOException if an I/O error occurs.
	 */
	protected void shadingFunction(final PDFOutput sout, final Color[] colors, final double[] fractions)
			throws IOException {
		PaintResources.writeShadingFunction(sout, this.getPdfWriter().getParams(), colors, fractions);
	}

	/**
	 * Synchronizes the current graphics state with the PDF output.
	 *
	 * @throws IOException if an I/O error occurs.
	 */
	protected void applyStates() throws IOException {
		final var out = this.out;

		// Default rendering intent, once per content stream
		if (!this.riWritten) {
			this.riWritten = true;
			final var ri = out.getPdfWriter().getParams().renderingIntent();
			if (ri != null) {
				out.writeName(ri.pdfName());
				out.writeOperator("ri");
			}
		}
		// Transform
		this.applyTransform();
		this.applyClip();

		// Stroke
		if (this.lineWidth != this.xlineWidth) {
			this.xlineWidth = this.lineWidth;
			out.writeReal(this.lineWidth);
			out.writeOperator("w");
		}
		if (this.lineCap != this.xlineCap) {
			this.xlineCap = this.lineCap;
			out.writeInt(this.lineCap.code);
			out.writeOperator("J");
		}
		if (this.lineJoin != this.xlineJoin) {
			this.xlineJoin = this.lineJoin;
			out.writeInt(this.lineJoin.code);
			out.writeOperator("j");
		}
		if (!Arrays.equals(this.linePattern, this.xlinePattern)) {
			this.xlinePattern = this.linePattern;
			out.startArray();
			if (this.linePattern != null) {
				for (final var p : this.linePattern) {
					out.writeReal(p);
				}
			}
			out.endArray();
			out.writeInt(0);
			out.writeOperator("d");
		}

		// Color
		if (this.strokePaint != null && !this.strokePaint.equals(this.xstrokePaint)) {
			switch (this.strokePaint.getPaintType()) {
				case COLOR -> {
					if (this.xstrokePaint != null && this.xstrokePaint.getPaintType() != Paint.Type.COLOR) {
						out.writeName("DeviceRGB");
						out.writeOperator("CS");
					}
					out.writeStrokeColor((Color) this.strokePaint);
				}
				case PATTERN, LINEAR_GRADIENT, RADIAL_GRADIENT -> {
					final var name = this.getPaintName(this.strokePaint);
					if (name != null) {
						out.writeName("Pattern");
						out.writeOperator("CS");
						out.useResource("Pattern", name);
						out.writeName(name);
						out.writeOperator("SCN");
					}
				}
				default -> throw new IllegalStateException("Unexpected paint type: " + this.strokePaint.getPaintType());
			}
			this.xstrokePaint = this.strokePaint;
		}
		if (this.fillPaint != null && !this.fillPaint.equals(this.xfillPaint)) {
			switch (this.fillPaint.getPaintType()) {
				case COLOR -> {
					if (this.xfillPaint != null && this.xfillPaint.getPaintType() != Paint.Type.COLOR) {
						out.writeName("DeviceRGB");
						out.writeOperator("cs");
					}
					out.writeFillColor((Color) this.fillPaint);
				}
				case PATTERN, LINEAR_GRADIENT, RADIAL_GRADIENT -> {
					final var name = this.getPaintName(this.fillPaint);
					if (name != null) {
						out.writeName("Pattern");
						out.writeOperator("cs");
						out.useResource("Pattern", name);
						out.writeName(name);
						out.writeOperator("scn");
					}
				}
				default -> throw new IllegalStateException("Unexpected paint type: " + this.fillPaint.getPaintType());
			}
			this.xfillPaint = this.fillPaint;
		}

		// Soft mask reproducing per-stop gradient alpha. Both paints share
		// one mask state; the fill paint wins when both are alpha gradients.
		if (this.pdfVersion.allowsTransparency()) {
			var desired = PaintResources.softMaskName(this, this.fillPaint);
			if (desired == null) {
				desired = PaintResources.softMaskName(this, this.strokePaint);
			}
			if (!java.util.Objects.equals(desired, this.xsmask)) {
				final var gsName = (desired != null) ? desired : PaintResources.smaskNoneName(this);
				out.useResource("ExtGState", gsName);
				out.writeName(gsName);
				out.writeOperator("gs");
				this.xsmask = desired;
			}
		}

		// Opacity
		final var supportAlpha = this.pdfVersion.allowsTransparency();
		// When transparency is supported
		if ((supportAlpha && (!this.out.equals(this.strokeAlpha, this.xstrokeAlpha)
				|| !this.out.equals(this.fillAlpha, this.xfillAlpha)))
				|| (this.strokeOverprint != this.xstrokeOverprint || this.fillOverprint != this.xfillOverprint)) {
			this.xstrokeAlpha = this.strokeAlpha;
			this.xfillAlpha = this.fillAlpha;
			this.xstrokeOverprint = this.strokeOverprint;
			this.xfillOverprint = this.fillOverprint;
			@SuppressWarnings("unchecked")
			var gsCache = (Map<ExtGStateKey, String>) this.out.getPdfWriter().getAttribute("sfGsCache");
			if (gsCache == null) {
				gsCache = new HashMap<>();
				this.out.getPdfWriter().putAttribute("sfGsCache", gsCache);
			}
			final var key = new ExtGStateKey(
					supportAlpha ? this.strokeAlpha : 1.0f,
					supportAlpha ? this.fillAlpha : 1.0f,
					this.strokeOverprint,
					this.fillOverprint);
			var name = gsCache.get(key);
			if (name == null) {
				try (final var gsOut = this.out.getPdfWriter().createSpecialGraphicsState()) {
					if (supportAlpha) {
						gsOut.writeName("CA");
						gsOut.writeReal(this.strokeAlpha);
						gsOut.writeName("ca");
						gsOut.writeReal(this.fillAlpha);
					}
					if (this.strokeOverprint != CMYKColor.OVERPRINT_NONE) {
						gsOut.writeName("OP");
						gsOut.writeBoolean(true);
						if (this.strokeOverprint == CMYKColor.OVERPRINT_ILLUSTRATOR) {
							gsOut.writeName("OPM");
							gsOut.writeInt(1);
						}
					}
					if (this.fillOverprint != CMYKColor.OVERPRINT_NONE) {
						gsOut.writeName("op");
						gsOut.writeBoolean(true);
						if (this.fillOverprint == CMYKColor.OVERPRINT_ILLUSTRATOR) {
							gsOut.writeName("opm");
							gsOut.writeInt(1);
						}
					}
					name = gsOut.getName();
					gsCache.put(key, name);
				}
			}
			out.useResource("ExtGState", name);
			out.writeName(name);
			out.writeOperator("gs");
		}
	}

	/**
	 * If the current graphics state is applied for the first time,
	 * outputs the graphics context start instruction (q) and saves the current
	 * graphics state.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	private void gsave() throws IOException {
		if (this.stack.isEmpty()) {
			return;
		}
		final var state = this.stack.getLast();
		if (state.gstate == null) {
			this.q();
			final var newState = state.withXState(new XGraphicsState(this));
			this.stack.set(this.stack.size() - 1, newState);
		}
	}

	/**
	 * If a previous graphics state is saved,
	 * outputs the graphics context end instruction (Q) and restores the graphics
	 * state.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	private void grestore() throws IOException {
		final var state = this.stack.getLast();
		if (state.gstate != null) {
			this.Q();
			state.gstate.restore(this);
			this.stack.set(this.stack.size() - 1, state.withoutXState());
		}
	}

	/**
	 * Outputs the graphics context start instruction (q).
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void q() throws IOException {
		++this.qDepth;
		if (this.pdfVersion.v == PDFParams.Version.V_PDFA1B.v) {
			if (this.qDepth > 28) {
				throw new IllegalStateException("PDF/A-1 cannot nest graphic states more than 28 levels.");
			}
		}
		this.out.writeOperator("q");
	}

	/**
	 * Outputs the graphics context end instruction (Q).
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void Q() throws IOException {
		--this.qDepth;
		this.out.writeOperator("Q");
	}

	/**
	 * Plots a rectangle in the PDF.
	 *
	 * @param r The rectangle to plot.
	 * @throws IOException if an I/O error occurs.
	 */
	protected void plotRect(final Rectangle2D r) throws IOException {
		this.out.writeRect(r.getX(), r.getY(), r.getWidth(), r.getHeight());
		this.out.writeOperator("re");
	}

	/**
	 * Plots a path in the PDF.
	 *
	 * @param i The path iterator.
	 * @return true if the path is closed.
	 * @throws IOException if an I/O error occurs.
	 */
	protected boolean plot(final PathIterator i) throws IOException {
		final var out = this.out;
		final var c = this.cord;

		var sx = 0.0;
		var sy = 0.0;
		var px = 0.0;
		var py = 0.0;
		var first = true;

		while (!i.isDone()) {
			final var type = i.currentSegment(c);
			switch (type) {
				case PathIterator.SEG_LINETO -> {
					final var x = c[0];
					final var y = c[1];
					if (first || !out.equals(x, px) || !out.equals(y, py)) {
						out.writePosition(x, y);
						out.writeOperator("l");
						px = x;
						py = y;
					}
					sx = x;
					sy = y;
					first = false;
				}
				case PathIterator.SEG_MOVETO -> {
					sx = px = c[0];
					sy = py = c[1];
					out.writePosition(sx, sy);
					out.writeOperator("m");
					first = false;
				}
				case PathIterator.SEG_CUBICTO -> {
					out.writePosition(c[0], c[1]);
					out.writePosition(c[2], c[3]);
					sx = c[4];
					sy = c[5];
					out.writePosition(sx, sy);
					out.writeOperator("c");
					px = sx;
					py = sy;
					first = false;
				}
				case PathIterator.SEG_QUADTO -> {
					final var cx = c[0];
					final var cy = c[1];
					final var ex = c[2];
					final var ey = c[3];
					out.writePosition(sx * ONE_THIRD + cx * TWO_THIRD, sy * ONE_THIRD + cy * TWO_THIRD);
					out.writePosition(ex * ONE_THIRD + cx * TWO_THIRD, ey * ONE_THIRD + cy * TWO_THIRD);
					sx = ex;
					sy = ey;
					out.writePosition(sx, sy);
					out.writeOperator("c");
					px = sx;
					py = sy;
					first = false;
				}
				case PathIterator.SEG_CLOSE -> {
					i.next();
					if (i.isDone()) {
						return true;
					}
					out.writeOperator("h");
					px = sx;
					py = sy;
					continue;
				}
				default -> throw new IllegalStateException("Unknown segment type: " + type);
			}
			i.next();
		}
		return false;
	}

	public void close() throws IOException {
		this.out.close();
	}
}