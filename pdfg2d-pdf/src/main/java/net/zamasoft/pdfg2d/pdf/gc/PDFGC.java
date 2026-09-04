package net.zamasoft.pdfg2d.pdf.gc;

import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
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
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.RecorderGC;
import net.zamasoft.pdfg2d.gc.RecorderGC.RecorderImage;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;
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
import net.zamasoft.pdfg2d.g2d.gc.G2DGC;
import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;
import net.zamasoft.pdfg2d.g2d.util.RasterEffects;

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
	private static final long MAX_BLUR_LAYER_PIXELS = 16_000_000L;
	private static final long MAX_FILTER_LAYER_PIXELS = 16_000_000L;

	private record BlurRegion(double x, double y, int width, int height) {
	}

	private record ExtGStateKey(float strokeAlpha, float fillAlpha, byte strokeOverprint, byte fillOverprint,
			BlendMode blendMode) {
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
			AffineTransform actualTransform,
			BlendMode blendMode) {

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
					gc.actualTransform != null ? new AffineTransform(gc.actualTransform) : null,
					gc.blendMode);
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
			gc.blendMode = this.blendMode;
		}

		GraphicsState withXState(final XGraphicsState xState) {
			return new GraphicsState(
					xState, lineWidth, lineCap, lineJoin, linePattern, strokePaint, fillPaint,
					textMode, strokeAlpha, fillAlpha, strokeOverprint, fillOverprint,
					actualTransform, blendMode);
		}

		GraphicsState withoutXState() {
			return new GraphicsState(
					null, lineWidth, lineCap, lineJoin, linePattern, strokePaint, fillPaint,
					textMode, strokeAlpha, fillAlpha, strokeOverprint, fillOverprint,
					actualTransform, blendMode);
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
			String smask,
			BlendMode blendMode) {

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
					gc.xsmask,
					gc.xblendMode);
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
			gc.xblendMode = this.blendMode;
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

	/** Blend mode (2026-08-29; {@code /BM} in the shared alpha ExtGState). */
	BlendMode blendMode = BlendMode.NORMAL;

	/** Blend mode currently active in the PDF content stream. */
	BlendMode xblendMode = BlendMode.NORMAL;

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

	/** Whether this GC currently owns an {@code /ActualText} replacement span. */
	private boolean inTextReplacement = false;

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
	public boolean supports(final Capability capability) {
		return switch (capability) {
			case GAUSSIAN_BLUR, GROUP_FILTER, DROP_SHADOW -> this.pdfVersion.allowsTransparency();
			case CONIC_GRADIENT -> this.pdfVersion.v >= PDFParams.Version.V_1_3.v;
			default -> false;
		};
	}

	/** PDF implements non-trivial group filters by rasterizing only the group. */
	@Override
	public boolean rasterizesGroupEffects() {
		return true;
	}

	/**
	 * Captures filter content for deferred vector or raster replay. Ordinary
	 * {@link #createGroupImage(double, double)} calls still create real Form
	 * XObjects immediately.
	 */
	@Override
	public GroupImageGC createFilterGroup(final double width, final double height) {
		return new RecorderGC.RecorderGroupImageGC(this.getFontManager(), width, height, true);
	}

	@Override
	public GroupEffectsResult drawGroupEffects(final Image image, final GroupEffects effects)
			throws GraphicsException {
		if (!this.supports(Capability.GROUP_FILTER)) {
			return GroupEffectsResult.UNSUPPORTED;
		}
		if (!(image instanceof RecorderImage recorded)) {
			return GC.super.drawGroupEffects(image, effects);
		}

		if (effects == null || effects.isIdentity()) {
			this.drawImage(this.materialize(recorded));
			return GroupEffectsResult.VECTOR;
		}
		if (effects.colorMatrix() == null && effects.blurSigma() <= 0
				&& effects.dropShadow() == null && effects.opacity() < 1) {
			final Image group = this.materialize(recorded);
			final float groupAlpha = (float) Math.max(0, Math.min(1, this.fillAlpha * effects.opacity()));
			try (final State state = this.begin()) {
				this.setStrokePaint(GrayColor.BLACK);
				this.setFillPaint(GrayColor.BLACK);
				this.setStrokeAlpha(1);
				this.setFillAlpha(groupAlpha);
				this.drawImage(group);
			}
			return GroupEffectsResult.VECTOR;
		}

		return this.rasterizeGroup(recorded, effects);
	}

	private Image materialize(final RecorderImage recorded) {
		final GroupImageGC group = this.createGroupImage(recorded.getWidth(), recorded.getHeight());
		recorded.drawTo(group);
		return group.finish();
	}

	private GroupEffectsResult rasterizeGroup(final RecorderImage recorded, final GroupEffects effects) {
		final AffineTransform current = this.actualTransform == null
				? new AffineTransform()
				: new AffineTransform(this.actualTransform);
		final double singularValue = maxSingularValue(current);
		final double localScale = (this.getPdfWriter().getParams().filterRasterDpi() / 72.0) * singularValue;
		final double blurSigma = effects.blurSigma() > 0 ? effects.blurSigma() * localScale : 0;
		final GroupEffects.DropShadow shadow = effects.dropShadow();
		final double shadowDx = shadow == null ? 0 : shadow.dx() * localScale;
		final double shadowDy = shadow == null ? 0 : shadow.dy() * localScale;
		final double shadowSigma = shadow != null && shadow.sigma() > 0 ? shadow.sigma() * localScale : 0;
		final double padValue = Math.ceil(3 * blurSigma) + Math.ceil(3 * shadowSigma)
				+ Math.ceil(Math.max(Math.abs(shadowDx), Math.abs(shadowDy))) + 1;
		if (!Double.isFinite(localScale) || !(localScale > 0)
				|| !Double.isFinite(blurSigma) || !Double.isFinite(shadowSigma)
				|| !Double.isFinite(shadowDx) || !Double.isFinite(shadowDy)
				|| !Double.isFinite(padValue) || padValue > Integer.MAX_VALUE) {
			return this.drawFilterFallback(recorded);
		}
		final int pad = (int) padValue;
		final Rectangle2D nominalBounds = new Rectangle2D.Double(0, 0,
				recorded.getWidth(), recorded.getHeight());
		final Rectangle2D recordedBounds = recorded.getContentBounds();
		final Rectangle2D rasterBounds = recordedBounds == null || recordedBounds.isEmpty()
				? nominalBounds
				: recordedBounds.createIntersection(nominalBounds);
		if (rasterBounds.isEmpty()) {
			// The recorder contains drawing, but all of it lies outside the nominal
			// group box and would have been clipped by the full-size raster as well.
			return GroupEffectsResult.VECTOR;
		}
		final BlurRegion region = blurRegion(rasterBounds, localScale, pad);
		if (region == null || region.width * (long) region.height > MAX_FILTER_LAYER_PIXELS) {
			return this.drawFilterFallback(recorded);
		}

		final BufferedImage layer = new BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_ARGB);
		final var graphics = layer.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			graphics.translate(-region.x, -region.y);
			graphics.scale(localScale, localScale);
			recorded.drawTo(new FilterRasterGC(graphics, this.getFontManager()));
		} finally {
			graphics.dispose();
		}

		final int width = region.width, height = region.height;
		float[][] planes = RasterEffects.toPlanes(layer);
		if (effects.colorMatrix() != null) {
			RasterEffects.applyColorMatrix(planes, effects.colorMatrix());
		}
		RasterEffects.premultiply(planes);
		if (blurSigma > 0) {
			RasterEffects.gaussianBlur(planes, width, height, blurSigma);
		}
		if (shadow != null) {
			final Color color = shadow.color();
			final float[] rgba = color == null ? new float[] { 0, 0, 0, 1 }
					: new float[] { color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() };
			planes = RasterEffects.dropShadow(planes, width, height, shadowDx, shadowDy, shadowSigma, rgba);
		}
		if (effects.opacity() < 1) {
			RasterEffects.scale(planes, (float) Math.max(0, effects.opacity()));
		}
		final BufferedImage filtered = RasterEffects.toPremultipliedImage(planes, width, height);
		final Image generated;
		try {
			generated = this.getPdfWriter().addGeneratedImage(filtered);
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
			layer.flush();
		}

		final String recordedAlt = recorded.getAltString();
		final String alt = recordedAlt == null || recordedAlt.isEmpty() ? "filter" : recordedAlt;
		final float outerAlpha = this.fillAlpha;
		try (final State state = this.begin()) {
			this.setStrokePaint(GrayColor.BLACK);
			this.setFillPaint(GrayColor.BLACK);
			this.setStrokeAlpha(1);
			this.setFillAlpha(outerAlpha);
			final var placement = AffineTransform.getTranslateInstance(
					region.x / localScale, region.y / localScale);
			placement.scale(1 / localScale, 1 / localScale);
			this.transform(placement);
			this.drawImage(generated, alt);
		}
		return GroupEffectsResult.RASTERIZED;
	}

	private GroupEffectsResult drawFilterFallback(final RecorderImage recorded) {
		this.drawImage(this.materialize(recorded));
		return GroupEffectsResult.LIMIT_FALLBACK;
	}

	/**
	 * A raster replay target that substitutes lazily supplied pixels for
	 * PDF-only image handles. PixelBackedImage lives in the consuming UA, so its
	 * public getPixels contract is discovered without adding a reverse module
	 * dependency. A PDF image or Form without such pixels is intentionally empty.
	 */
	private static class FilterRasterGC extends G2DGC {
		private static final Paint TRANSPARENT = RGBAColor.create(0, 0, 0, 0);

		FilterRasterGC(final java.awt.Graphics2D graphics, final FontManager fontManager) {
			super(graphics, fontManager);
		}

		/**
		 * Raster replay deliberately drops semantic replacement scopes: the
		 * resulting pixels contain no searchable text and must not duplicate an
		 * outer replacement attached by a higher-level caller.
		 */
		@Override
		public State beginTextReplacement(final String logicalText) {
			return GC.NO_OP_STATE;
		}

		@Override
		public void drawImage(final Image image) throws GraphicsException {
			final Image raster = rasterImage(image);
			if (raster != null) {
				super.drawImage(raster);
			}
		}

		@Override
		public void setStrokePaint(final Paint paint) throws GraphicsException {
			super.setStrokePaint(rasterPaint(paint));
		}

		@Override
		public void setFillPaint(final Paint paint) throws GraphicsException {
			super.setFillPaint(rasterPaint(paint));
		}

		@Override
		public GroupImageGC createGroupImage(final double width, final double height) {
			final AffineTransform at = this.g.getTransform();
			final Rectangle2D bounds = at
					.createTransformedShape(new Rectangle2D.Double(0, 0, width, height)).getBounds2D();
			final double minX = Math.floor(bounds.getMinX()), minY = Math.floor(bounds.getMinY());
			final double maxX = Math.ceil(bounds.getMaxX()), maxY = Math.ceil(bounds.getMaxY());
			final double rasterWidth = maxX - minX, rasterHeight = maxY - minY;
			if (!(rasterWidth > 0) || !(rasterHeight > 0)
					|| rasterWidth > Integer.MAX_VALUE || rasterHeight > Integer.MAX_VALUE) {
				throw new GraphicsException("Invalid transformed filter group size: " + rasterWidth + "x"
						+ rasterHeight);
			}
			final BufferedImage groupImage = new BufferedImage((int) rasterWidth, (int) rasterHeight,
					BufferedImage.TYPE_INT_ARGB);
			final var groupGraphics = groupImage.createGraphics();
			groupGraphics.setRenderingHints(this.g.getRenderingHints());
			final AffineTransform deviceShift = AffineTransform.getTranslateInstance(-minX, -minY);
			final AffineTransform shifted = new AffineTransform(deviceShift);
			shifted.concatenate(at);
			final AffineTransform imageToUser;
			try {
				imageToUser = at.createInverse();
				imageToUser.translate(minX, minY);
			} catch (NoninvertibleTransformException e) {
				groupGraphics.dispose();
				throw new GraphicsException("Cannot place a filter group under a non-invertible transform", e);
			}
			final var group = new FilterRasterGroupGC(groupGraphics, this.getFontManager(), groupImage,
					imageToUser);
			new G2DGC.GraphicsState(this).restore(group);
			group.g.setTransform(shifted);
			group.g.setClip(this.g.getClip());
			return group;
		}

		private static Paint rasterPaint(final Paint paint) {
			if (!(paint instanceof Pattern pattern)) {
				return paint;
			}
			final Image raster = rasterImage(pattern.getImage());
			return raster == null ? TRANSPARENT : new Pattern(raster, pattern.getTransform());
		}

		private static Image rasterImage(final Image image) {
			try {
				final var method = image.getClass().getMethod("getPixels");
				if (Image.class.isAssignableFrom(method.getReturnType())) {
					final Object pixels = method.invoke(image);
					if (pixels instanceof Image pixelImage && pixelImage != image) {
						return rasterImage(pixelImage);
					}
					return null;
				}
			} catch (NoSuchMethodException e) {
				// Not a lazy pixel-backed wrapper.
			} catch (ReflectiveOperationException | SecurityException e) {
				return null;
			}
			if (image instanceof TransformedImage transformed) {
				final Image original = transformed.getImage();
				final Image raster = rasterImage(original);
				if (raster == null) {
					return null;
				}
				return raster == original ? image
						: new TransformedImage(raster, new AffineTransform(transformed.getTransform()));
			}
			return image instanceof PDFImage || image instanceof PDFGroupImage ? null : image;
		}
	}

	private static final class FilterRasterGroupGC extends FilterRasterGC implements GroupImageGC {
		private final BufferedImage image;
		private final AffineTransform imageToUser;

		FilterRasterGroupGC(final java.awt.Graphics2D graphics, final FontManager fontManager,
				final BufferedImage image, final AffineTransform imageToUser) {
			super(graphics, fontManager);
			this.image = image;
			this.imageToUser = imageToUser;
		}

		@Override
		public Image finish() {
			Image result = new RasterImageImpl(this.image);
			if (!this.imageToUser.isIdentity()) {
				result = new TransformedImage(result, this.imageToUser);
			}
			return result;
		}
	}

	/** Returns the largest singular value of the transform's linear part. */
	private static double maxSingularValue(final AffineTransform at) {
		final double[] m = new double[6];
		at.getMatrix(m);
		for (final double value : m) {
			if (!Double.isFinite(value)) {
				return Double.NaN;
			}
		}
		final double determinant = at.getDeterminant();
		if (!Double.isFinite(determinant) || determinant == 0) {
			return Double.NaN;
		}
		// Stable closed form for the larger singular value of a 2x2 matrix.
		final double p = Math.hypot(m[0] + m[3], m[1] - m[2]);
		final double q = Math.hypot(m[0] - m[3], m[1] + m[2]);
		return (p + q) / 2;
	}

	/** Converts a supported solid fill to the raster layer's RGBA color. */
	private java.awt.Color blurColor() {
		if (!(this.fillPaint instanceof Color color)) {
			return null;
		}
		switch (color.getColorType()) {
			case RGB, RGBA, GRAY, CMYK, SPOT -> {
				// CMYK and spot colors expose their process/alternate RGB approximation.
			}
			default -> {
				return null;
			}
		}
		final float red = color.getRed();
		final float green = color.getGreen();
		final float blue = color.getBlue();
		final float alpha = this.fillAlpha;
		if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue)
				|| !Float.isFinite(alpha)
				|| red < 0 || red > 1 || green < 0 || green > 1 || blue < 0 || blue > 1
				|| alpha < 0 || alpha > 1) {
			return null;
		}
		return new java.awt.Color(red, green, blue, alpha);
	}

	private static BlurRegion blurRegion(final Rectangle2D bounds, final double scale, final int pad) {
		if (bounds == null || bounds.isEmpty()
				|| !Double.isFinite(bounds.getMinX()) || !Double.isFinite(bounds.getMinY())
				|| !Double.isFinite(bounds.getMaxX()) || !Double.isFinite(bounds.getMaxY())) {
			return null;
		}
		final double x = Math.floor(bounds.getMinX() * scale) - pad;
		final double y = Math.floor(bounds.getMinY() * scale) - pad;
		final double right = Math.ceil(bounds.getMaxX() * scale) + pad;
		final double bottom = Math.ceil(bounds.getMaxY() * scale) + pad;
		final double width = right - x;
		final double height = bottom - y;
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
				|| !(width > 0) || !(height > 0)
				|| width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
			return null;
		}
		return new BlurRegion(x, y, (int) width, (int) height);
	}

	@Override
	public boolean tryFillBlurred(final Shape shape, final double sigma) throws GraphicsException {
		if (!this.supports(Capability.GAUSSIAN_BLUR) || shape == null
				|| !Double.isFinite(sigma) || sigma < 0) {
			return false;
		}

		final AffineTransform current = this.actualTransform == null
				? new AffineTransform()
				: new AffineTransform(this.actualTransform);
		final double singularValue = maxSingularValue(current);
		if (!Double.isFinite(singularValue) || !(singularValue > 0)) {
			return false;
		}
		final double localScale = (this.getPdfWriter().getParams().blurRasterDpi() / 72.0) * singularValue;
		final double pixelSigma = sigma * localScale;
		final double radius = 3 * pixelSigma;
		if (!Double.isFinite(localScale) || !(localScale > 0)
				|| !Double.isFinite(pixelSigma) || pixelSigma < 0
				|| !Double.isFinite(radius) || radius > Integer.MAX_VALUE) {
			return false;
		}

		final java.awt.Color color = this.blurColor();
		if (color == null) {
			return false;
		}
		final int pad = RasterEffects.kernelRadius(pixelSigma);
		final BlurRegion region = blurRegion(shape.getBounds2D(), localScale, pad);
		if (region == null || region.width * (long) region.height > MAX_BLUR_LAYER_PIXELS) {
			return false;
		}

		final BufferedImage layer = new BufferedImage(region.width, region.height,
				BufferedImage.TYPE_INT_ARGB_PRE);
		final var graphics = layer.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.translate(-region.x, -region.y);
			graphics.scale(localScale, localScale);
			graphics.setPaint(color);
			graphics.fill(shape);
		} finally {
			graphics.dispose();
		}

		final BufferedImage blurred;
		try {
			blurred = RasterEffects.blurPremultiplied(layer, pixelSigma);
		} finally {
			layer.flush();
		}
		final Image image;
		try {
			image = this.getPdfWriter().addGeneratedImage(blurred);
		} catch (IOException e) {
			throw new GraphicsException(e);
		}

		try (final State state = this.begin()) {
			// The source color and effective alpha are already baked into the image.
			// Solid paints also force any paint soft mask back to /None, including a
			// mask inherited from the stroke paint. Reset both overprint channels.
			this.setStrokePaint(GrayColor.BLACK);
			this.setFillPaint(GrayColor.BLACK);
			this.setStrokeAlpha(1);
			this.setFillAlpha(1);

			final var placement = AffineTransform.getTranslateInstance(
					region.x / localScale, region.y / localScale);
			placement.scale(1 / localScale, 1 / localScale);
			this.transform(placement);
			try (final State artifact = this.beginArtifactScope()) {
				// Keep the caller's blend mode: the final image must blend with the page.
				this.drawImage(image);
			}
		}
		return true;
	}

	@Override
	public void fillBlurred(final Shape shape, final double sigma) throws GraphicsException {
		if (!this.tryFillBlurred(shape, sigma)) {
			this.fill(shape);
		}
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
	 * {@inheritDoc}
	 *
	 * <p>
	 * Opens an {@code /Artifact} marked content sequence on tagged page
	 * streams and keeps {@link #inMark} set for its whole duration, so the
	 * drawing operations performed inside (fills, strokes, images and above
	 * all text) do not open real content marks of their own: their output
	 * lands unchanged inside the artifact. Untagged documents, patterns and
	 * group images open nothing, so their output is byte-for-byte identical
	 * to a call without the scope.
	 * </p>
	 *
	 * <p>
	 * Pending transform/clip state is flushed before the sequence is opened
	 * (as {@link #fill} and friends do), so that the {@code q}/{@code cm}
	 * belonging to the enclosing state does not end up inside the artifact.
	 * </p>
	 */
	@Override
	public State beginArtifactScope() throws GraphicsException {
		if (DEBUG) {
			LOG.fine("beginArtifactScope");
		}
		final boolean began;
		try {
			this.applyTransform();
			this.applyClip();
			began = this.beginArtifactTagged();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		if (!began) {
			// Nothing was opened (untagged output, or an enclosing mark is
			// already active): closing must be a no-op.
			return GC.NO_OP_STATE;
		}
		return new State() {
			private boolean closed;

			@Override
			public void close() throws GraphicsException {
				if (this.closed) {
					return;
				}
				this.closed = true;
				try {
					PDFGC.this.endTagged(true);
				} catch (IOException e) {
					throw new GraphicsException(e);
				}
			}
		};
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Line-level replacement is opt-in through
	 * {@link PDFParams#withActualTextReplacement(boolean)}. It is disabled by
	 * default because measurements show that PDFium (Chrome and Edge) extracts
	 * bidirectional text correctly from visual glyphs but incorrectly with the
	 * line-level replacement span, while MuPDF is not improved. Enable it only
	 * for Acrobat-centric text extraction workflows.
	 * </p>
	 *
	 * <p>
	 * When enabled, PDF 1.5 and later use one {@code /Span} marked-content
	 * sequence carrying {@code /ActualText}. The replacement state is independent
	 * of tagged content, artifacts and optional-content layers, so those
	 * marked-content sequences remain properly nested inside or outside it.
	 * Disabled replacement, nested calls and target versions earlier than PDF 1.5
	 * return a no-op state.
	 * </p>
	 */
	@Override
	public State beginTextReplacement(final String logicalText) throws GraphicsException {
		if (!this.getPdfWriter().getParams().actualTextReplacement()
				|| this.inTextReplacement || this.pdfVersion.v < PDFParams.Version.V_1_5.v) {
			return GC.NO_OP_STATE;
		}
		try {
			this.applyTransform();
			this.applyClip();
			this.out.beginActualText(java.util.Objects.requireNonNull(logicalText, "logicalText"));
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		this.inTextReplacement = true;
		return new State() {
			private boolean closed;

			@Override
			public void close() throws GraphicsException {
				if (this.closed) {
					return;
				}
				this.closed = true;
				try {
					PDFGC.this.out.endActualText();
				} catch (IOException e) {
					throw new GraphicsException(e);
				} finally {
					PDFGC.this.inTextReplacement = false;
				}
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
		// A transform applied inside the closed state but never flushed (no
		// drawing followed it, so no q/cm was written) belongs to that state
		// alone: drop it like the pending clip. Keeping it would flush it into
		// the enclosing state at the next begin()/draw and apply it twice
		// (2026-09-03: filter layers opened as begin/transform/createFilterGroup/close).
		this.transform = null;
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
	public void setBlendMode(final BlendMode mode) {
		this.blendMode = mode == null ? BlendMode.NORMAL : mode;
	}

	@Override
	public BlendMode getBlendMode() {
		return this.blendMode;
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
		this.drawImage(image, image.getAltString());
	}

	private void drawImage(final Image image, final String alt) throws GraphicsException {
		if (DEBUG) {
			LOG.fine("drawImage: " + image);
		}
		try {
			this.applyStates();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		this.pendingAlt = alt;
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
				case PATTERN, LINEAR_GRADIENT, RADIAL_GRADIENT, CONIC_GRADIENT -> {
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
				case PATTERN, LINEAR_GRADIENT, RADIAL_GRADIENT, CONIC_GRADIENT -> {
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
		// Blend modes (2026-08-29) ride on the same ExtGState; like alpha they
		// need the transparency model, so PDF/X-1a and PDF 1.3 emit Normal.
		final var blendMode = supportAlpha ? this.blendMode : BlendMode.NORMAL;
		if ((supportAlpha && (!this.out.equals(this.strokeAlpha, this.xstrokeAlpha)
				|| !this.out.equals(this.fillAlpha, this.xfillAlpha)))
				|| blendMode != this.xblendMode
				|| (this.strokeOverprint != this.xstrokeOverprint || this.fillOverprint != this.xfillOverprint)) {
			this.xstrokeAlpha = this.strokeAlpha;
			this.xfillAlpha = this.fillAlpha;
			this.xblendMode = blendMode;
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
					this.fillOverprint,
					blendMode);
			var name = gsCache.get(key);
			if (name == null) {
				try (final var gsOut = this.out.getPdfWriter().createSpecialGraphicsState()) {
					if (supportAlpha) {
						gsOut.writeName("CA");
						gsOut.writeReal(this.strokeAlpha);
						gsOut.writeName("ca");
						gsOut.writeReal(this.fillAlpha);
					}
					if (blendMode != BlendMode.NORMAL) {
						gsOut.writeName("BM");
						gsOut.writeName(blendMode.pdfName);
					}
					// ExtGState entries are partial dictionaries: omitting OP/op would
					// leave a previously enabled overprint mode active. Always write the
					// booleans so transitions back to the normal compositing path work.
					gsOut.writeName("OP");
					gsOut.writeBoolean(this.strokeOverprint != CMYKColor.OVERPRINT_NONE);
					if (this.strokeOverprint != CMYKColor.OVERPRINT_NONE) {
						if (this.strokeOverprint == CMYKColor.OVERPRINT_ILLUSTRATOR) {
							gsOut.writeName("OPM");
							gsOut.writeInt(1);
						}
					}
					gsOut.writeName("op");
					gsOut.writeBoolean(this.fillOverprint != CMYKColor.OVERPRINT_NONE);
					if (this.fillOverprint != CMYKColor.OVERPRINT_NONE) {
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
