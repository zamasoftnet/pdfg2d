package net.zamasoft.pdfg2d.pdf.gc;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.CMYKColor;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.Pattern;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;
import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.util.ColorUtils;

/**
 * Creates the PDF resources needed to paint with non-color paints: tiling
 * patterns for {@link Pattern} and axial/radial shading patterns for
 * {@link LinearGradient}/{@link RadialGradient}.
 * <p>
 * Resources are cached per document (keyed by their defining geometry) via the
 * writer-level resource cache held by {@link PDFGC}, so that repeated use of
 * an identical paint reuses the same pattern object instead of emitting a
 * duplicate.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
final class PaintResources {

	private PaintResources() {
		// static use only
	}

	/**
	 * Cache key for tiling patterns. The page size participates because the
	 * pattern matrix compensates for the bottom-left PDF origin.
	 */
	private record PatternKey(double pageWidth, double pageHeight, Image image, AffineTransform at) {
	}

	/**
	 * Cache key for shading patterns. Gradients are records of arrays, whose
	 * default equality is identity, so stop colors and fractions are compared
	 * by content here.
	 */
	private record ShadingKey(double pageHeight, AffineTransform transform, Paint paint) {
		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof ShadingKey other))
				return false;
			if (Double.compare(pageHeight, other.pageHeight) != 0)
				return false;
			if (!Objects.equals(transform, other.transform))
				return false;
			if (paint == other.paint)
				return true;
			if (paint == null || other.paint == null)
				return false;
			if (paint.getClass() != other.paint.getClass())
				return false;
			if (paint instanceof LinearGradient lg1 && other.paint instanceof LinearGradient lg2) {
				return Double.compare(lg1.x1(), lg2.x1()) == 0 &&
						Double.compare(lg1.y1(), lg2.y1()) == 0 &&
						Double.compare(lg1.x2(), lg2.x2()) == 0 &&
						Double.compare(lg1.y2(), lg2.y2()) == 0 &&
						Arrays.equals(lg1.colors(), lg2.colors()) &&
						Arrays.equals(lg1.fractions(), lg2.fractions());
			}
			if (paint instanceof RadialGradient rg1 && other.paint instanceof RadialGradient rg2) {
				return Double.compare(rg1.cx(), rg2.cx()) == 0 &&
						Double.compare(rg1.cy(), rg2.cy()) == 0 &&
						Double.compare(rg1.radius(), rg2.radius()) == 0 &&
						Double.compare(rg1.fx(), rg2.fx()) == 0 &&
						Double.compare(rg1.fy(), rg2.fy()) == 0 &&
						Arrays.equals(rg1.colors(), rg2.colors()) &&
						Arrays.equals(rg1.fractions(), rg2.fractions());
			}
			return paint.equals(other.paint);
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(pageHeight, transform);
			if (paint instanceof LinearGradient lg) {
				result = 31 * result + Objects.hash(lg.x1(), lg.y1(), lg.x2(), lg.y2());
				result = 31 * result + Arrays.hashCode(lg.colors());
				result = 31 * result + Arrays.hashCode(lg.fractions());
			} else if (paint instanceof RadialGradient rg) {
				result = 31 * result + Objects.hash(rg.cx(), rg.cy(), rg.radius(), rg.fx(), rg.fy());
				result = 31 * result + Arrays.hashCode(rg.colors());
				result = 31 * result + Arrays.hashCode(rg.fractions());
			} else {
				result = 31 * result + Objects.hashCode(paint);
			}
			return result;
		}
	}

	/**
	 * Returns the PDF resource name for the given paint, creating the backing
	 * pattern or shading object on first use.
	 *
	 * @param gc    the graphics context requesting the paint
	 * @param paint the paint to realize
	 * @return the resource name, or {@code null} when the paint needs no named
	 *         resource (plain colors) or is not representable in the target PDF
	 *         version (gradients before PDF 1.3)
	 * @throws GraphicsException if an error occurs while creating the resource
	 */
	static String paintName(final PDFGC gc, final Paint paint) throws GraphicsException {
		return switch (paint.getPaintType()) {
			case PATTERN -> {
				final var pattern = (Pattern) paint;
				final var image = pattern.getImage();
				var at = gc.getTransform();
				if (at == null) {
					at = pattern.getTransform();
				} else if (pattern.getTransform() != null) {
					at.concatenate(pattern.getTransform());
				}

				final var pout = gc.out;
				final var key = new PatternKey(pout.getWidth(), pout.getHeight(), image, at);

				var name = gc.resourceCache.get(key);
				if (name == null) {
					final var width = image.getWidth();
					final var height = image.getHeight();
					try (final var tout = pout.getPdfWriter().createTilingPattern(width, height, pout.getHeight(),
							at)) {
						final var pgc = new PDFGC(tout, gc.resourceCache);
						image.drawTo(pgc);
						name = tout.getName();
					} catch (IOException e) {
						throw new GraphicsException(e);
					}
					gc.resourceCache.put(key, name);
				}
				yield name;
			}
			case LINEAR_GRADIENT -> {
				// PDF Axial(Type 2) Shading
				if (gc.pdfVersion.v < PDFParams.Version.V_1_3.v) {
					yield null;
				}
				final var gradient = (LinearGradient) paint;

				var at = gc.getTransform();
				if (at == null) {
					at = gradient.transform();
				} else if (gradient.transform() != null) {
					at.concatenate(gradient.transform());
				}

				final var pout = gc.out;
				final var key = new ShadingKey(pout.getHeight(), at, gradient);
				var name = gc.resourceCache.get(key);
				if (name != null) {
					yield name;
				}

				try (final var sout = pout.getPdfWriter().createShadingPattern(pout.getHeight(), at)) {
					sout.writeName("ShadingType");
					sout.writeInt(2);
					sout.lineBreak();

					sout.writeName("Coords");
					sout.startArray();
					sout.writeReal(gradient.x1());
					sout.writeReal(gradient.y1());
					sout.writeReal(gradient.x2());
					sout.writeReal(gradient.y2());
					sout.endArray();
					sout.lineBreak();
					// Dispatch through the GC so subclasses overriding
					// shadingFunction keep their customization.
					gc.shadingFunction(sout, gradient.colors(), gradient.fractions());

					name = sout.getName();
					gc.resourceCache.put(key, name);
					yield name;
				} catch (IOException e) {
					throw new GraphicsException(e);
				}
			}
			case RADIAL_GRADIENT -> {
				// PDF Radial(Type 3) Shading
				if (gc.pdfVersion.v < PDFParams.Version.V_1_3.v) {
					yield null;
				}
				final var gp = (RadialGradient) paint;
				final var radius = gp.radius();

				var at = gc.getTransform();
				if (at == null) {
					at = gp.transform();
				} else if (gp.transform() != null) {
					at.concatenate(gp.transform());
				}

				final var pout = gc.out;
				final var key = new ShadingKey(pout.getHeight(), at, gp);
				var name = gc.resourceCache.get(key);
				if (name != null) {
					yield name;
				}

				// PDF requires the focal point to lie inside the end circle;
				// clamp it just inside when the caller placed it outside.
				var dx = gp.fx() - gp.cx();
				var dy = gp.fy() - gp.cy();
				final var d = Math.sqrt(dx * dx + dy * dy);
				if (d > radius) {
					final var scale = (radius * .9999) / d;
					dx *= scale;
					dy *= scale;
				}

				try (final var sout = pout.getPdfWriter().createShadingPattern(pout.getHeight(), at)) {
					sout.writeName("ShadingType");
					sout.writeInt(3);
					sout.lineBreak();

					sout.writeName("Coords");
					sout.startArray();
					sout.writeReal(gp.cx() + dx);
					sout.writeReal(gp.cy() + dy);
					sout.writeReal(0);
					sout.writeReal(gp.cx());
					sout.writeReal(gp.cy());
					sout.writeReal(radius);
					sout.endArray();
					sout.lineBreak();

					gc.shadingFunction(sout, gp.colors(), gp.fractions());
					name = sout.getName();
					gc.resourceCache.put(key, name);
					yield name;
				} catch (IOException e) {
					throw new GraphicsException(e);
				}
			}
			case COLOR -> null;
		};
	}

	/**
	 * Writes the {@code /ColorSpace}, {@code /Extend} and {@code /Function}
	 * entries of a shading dictionary for the given gradient stops.
	 * <p>
	 * Two-stop gradients map to a single exponential (Type 2) function; more
	 * stops are encoded as a stitching (Type 3) function of Type 2 segments,
	 * with constant segments synthesized before the first and after the last
	 * stop when the fractions do not span the full [0,1] domain.
	 * </p>
	 * <p>
	 * Per-stop alpha is not encoded here: for transparency-capable targets it
	 * is reproduced by a parallel luminosity soft mask (see
	 * {@link #softMaskName}); for PDF/A-1 and PDF/X-1a the alpha is dropped.
	 * </p>
	 *
	 * @param sout      the shading dictionary output
	 * @param params    the PDF generation parameters (drives the color space)
	 * @param colors    the gradient stop colors
	 * @param fractions the gradient stop offsets in [0,1]
	 * @throws IOException if an I/O error occurs
	 */
	static void writeShadingFunction(final PDFOutput sout, final PDFParams params, final Color[] colors,
			final double[] fractions) throws IOException {
		final Color.Type colorType;
		if (params.effectiveColorMode() == PDFParams.ColorMode.GRAY) {
			colorType = Color.Type.GRAY;
		} else if (params.effectiveColorMode() == PDFParams.ColorMode.CMYK) {
			colorType = Color.Type.CMYK;
		} else {
			var type = stopType(colors[0]);
			for (var i = 1; i < colors.length; ++i) {
				if (type != stopType(colors[i])) {
					type = Color.Type.RGB;
				}
			}
			if (type == Color.Type.RGBA) {
				type = Color.Type.RGB;
			}
			colorType = type;
		}
		writeShadingFunction(sout, colorType, colors, fractions);
	}

	/**
	 * Writes the shading function entries with an explicit target color
	 * space. Used directly for luminosity soft masks, whose alpha ramp is
	 * always encoded in DeviceGray.
	 */
	static void writeShadingFunction(final PDFOutput sout, final Color.Type colorType, final Color[] colors,
			final double[] fractions) throws IOException {
		sout.writeName("ColorSpace");
		final var colorSpaceName = switch (colorType) {
			case GRAY -> "DeviceGray";
			case RGB -> "DeviceRGB";
			case CMYK -> "DeviceCMYK";
			default -> throw new IllegalStateException("Unexpected color type: " + colorType);
		};
		sout.writeName(colorSpaceName);
		sout.lineBreak();

		sout.writeName("Extend");
		sout.startArray();
		sout.writeBoolean(true);
		sout.writeBoolean(true);
		sout.endArray();
		sout.lineBreak();

		sout.writeName("Function");
		sout.startHash();
		if (colors.length <= 2
				&& (fractions == null || fractions.length == 0 || (fractions.length == 1 && fractions[0] == 0)
						|| (fractions.length == 2 && fractions[0] == 0 && fractions[1] == 1))) {
			// Simple case
			sout.writeName("FunctionType");
			sout.writeInt(2);
			sout.lineBreak();

			sout.writeName("Domain");
			sout.startArray();
			sout.writeReal(0.0);
			sout.writeReal(1.0);
			sout.endArray();
			sout.lineBreak();

			sout.writeName("N");
			sout.writeReal(1.0);
			sout.lineBreak();

			sout.writeName("C0");
			sout.startArray();
			writeColor(sout, colorType, colors[0]);
			sout.endArray();
			sout.lineBreak();

			sout.writeName("C1");
			sout.startArray();
			writeColor(sout, colorType, colors[1]);
			sout.endArray();
			sout.lineBreak();
		} else {
			// Complex case
			var segments = fractions.length - 1;
			if (fractions[0] != 0) {
				++segments;
			}
			if (fractions[fractions.length - 1] != 1) {
				++segments;
			}

			sout.writeName("FunctionType");
			sout.writeInt(3);
			sout.lineBreak();

			sout.writeName("Domain");
			sout.startArray();
			sout.writeReal(0.0);
			sout.writeReal(1.0);
			sout.endArray();
			sout.lineBreak();

			sout.writeName("Encode");
			sout.startArray();
			for (var i = 0; i < segments; ++i) {
				sout.writeReal(0.0);
				sout.writeReal(1.0);
			}
			sout.endArray();
			sout.lineBreak();

			sout.writeName("Bounds");
			sout.startArray();
			if (fractions[0] != 0) {
				sout.writeReal(fractions[0]);
			}
			for (var i = 1; i < fractions.length - 1; ++i) {
				sout.writeReal(fractions[i]);
			}
			if (fractions[fractions.length - 1] != 1) {
				sout.writeReal(fractions[fractions.length - 1]);
			}
			sout.endArray();
			sout.lineBreak();

			sout.writeName("Functions");
			sout.startArray();
			for (var i = -1; i < fractions.length; ++i) {
				final Color c0, c1;
				if (i == -1) {
					if (fractions[0] != 0) {
						// Constant lead-in segment before the first stop
						c0 = colors[0];
						c1 = colors[0];
					} else {
						continue;
					}
				} else if (i == fractions.length - 1) {
					if (fractions[i] != 1) {
						// Constant tail segment after the last stop
						c0 = colors[i];
						c1 = colors[i];
					} else {
						break;
					}
				} else {
					c0 = colors[i];
					c1 = colors[i + 1];
				}

				sout.startHash();
				sout.writeName("FunctionType");
				sout.writeInt(2);
				sout.lineBreak();

				sout.writeName("Domain");
				sout.startArray();
				sout.writeReal(0.0);
				sout.writeReal(1.0);
				sout.endArray();
				sout.lineBreak();

				sout.writeName("N");
				sout.writeReal(1.0);
				sout.lineBreak();

				sout.writeName("C0");
				sout.startArray();
				writeColor(sout, colorType, c0);
				sout.endArray();
				sout.lineBreak();

				sout.writeName("C1");
				sout.startArray();
				writeColor(sout, colorType, c1);
				sout.endArray();
				sout.lineBreak();
				sout.endHash();
			}
			sout.endArray();
			sout.lineBreak();
		}
		sout.endHash();
		sout.lineBreak();
	}

	/** Returns whether any gradient stop carries an alpha below 1. */
	static boolean hasAlpha(final Color[] colors) {
		for (final var c : colors) {
			if (c.getAlpha() < 1f) {
				return true;
			}
		}
		return false;
	}

	/** Cache key marking the soft-mask ExtGState of an alpha gradient. */
	private record MaskKey(Object shadingKey) {
	}

	/**
	 * Returns the ExtGState resource name applying a luminosity soft mask
	 * that reproduces the per-stop alpha of the given gradient, creating the
	 * grayscale shading, mask form and ExtGState on first use. Returns
	 * {@code null} when the paint is not an alpha gradient or the target
	 * profile does not permit transparency.
	 *
	 * @param gc    the graphics context requesting the paint
	 * @param paint the paint about to be used
	 * @return the ExtGState name, or {@code null}
	 * @throws GraphicsException if an error occurs while creating the mask
	 */
	static String softMaskName(final PDFGC gc, final Paint paint) throws GraphicsException {
		if (paint == null || !gc.pdfVersion.allowsTransparency()) {
			return null;
		}
		final Color[] colors;
		final double[] fractions;
		final AffineTransform paintTransform;
		if (paint instanceof LinearGradient lg) {
			colors = lg.colors();
			fractions = lg.fractions();
			paintTransform = lg.transform();
		} else if (paint instanceof RadialGradient rg) {
			colors = rg.colors();
			fractions = rg.fractions();
			paintTransform = rg.transform();
		} else {
			return null;
		}
		if (!hasAlpha(colors)) {
			return null;
		}

		var at = gc.getTransform();
		if (at == null) {
			at = paintTransform;
		} else if (paintTransform != null) {
			at.concatenate(paintTransform);
		}

		final var pout = gc.out;
		final var key = new MaskKey(new ShadingKey(pout.getHeight(), at, paint));
		var name = gc.resourceCache.get(key);
		if (name != null) {
			return name;
		}

		// The alpha ramp becomes a DeviceGray shading with the same geometry
		// as the color gradient: gray = alpha, so luminosity = opacity.
		final var grays = new Color[colors.length];
		for (var i = 0; i < colors.length; ++i) {
			grays[i] = GrayColor.create(colors[i].getAlpha());
		}

		final String shadingName;
		try (final var sout = pout.getPdfWriter().createShadingPattern(pout.getHeight(), at)) {
			if (paint instanceof LinearGradient lg) {
				sout.writeName("ShadingType");
				sout.writeInt(2);
				sout.lineBreak();
				sout.writeName("Coords");
				sout.startArray();
				sout.writeReal(lg.x1());
				sout.writeReal(lg.y1());
				sout.writeReal(lg.x2());
				sout.writeReal(lg.y2());
				sout.endArray();
				sout.lineBreak();
			} else {
				final var rg = (RadialGradient) paint;
				var dx = rg.fx() - rg.cx();
				var dy = rg.fy() - rg.cy();
				final var d = Math.sqrt(dx * dx + dy * dy);
				if (d > rg.radius()) {
					final var scale = (rg.radius() * .9999) / d;
					dx *= scale;
					dy *= scale;
				}
				sout.writeName("ShadingType");
				sout.writeInt(3);
				sout.lineBreak();
				sout.writeName("Coords");
				sout.startArray();
				sout.writeReal(rg.cx() + dx);
				sout.writeReal(rg.cy() + dy);
				sout.writeReal(0);
				sout.writeReal(rg.cx());
				sout.writeReal(rg.cy());
				sout.writeReal(rg.radius());
				sout.endArray();
				sout.lineBreak();
			}
			writeShadingFunction(sout, Color.Type.GRAY, grays, fractions);
			shadingName = sout.getName();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}

		try {
			name = pout.getPdfWriter().createLuminositySoftMask(shadingName, pout.getWidth(), pout.getHeight());
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		gc.resourceCache.put(key, name);
		return name;
	}

	/**
	 * Returns the (cached) ExtGState that removes any active soft mask
	 * ({@code /SMask /None}), creating it on first use.
	 *
	 * @param gc the graphics context
	 * @return the ExtGState name
	 * @throws GraphicsException if an error occurs while creating it
	 */
	static String smaskNoneName(final PDFGC gc) throws GraphicsException {
		final var key = "SMask/None";
		var name = gc.resourceCache.get(key);
		if (name == null) {
			try (final var gsOut = gc.out.getPdfWriter().createSpecialGraphicsState()) {
				gsOut.writeName("SMask");
				gsOut.writeName("None");
				gsOut.lineBreak();
				name = gsOut.getName();
			} catch (IOException e) {
				throw new GraphicsException(e);
			}
			gc.resourceCache.put(key, name);
		}
		return name;
	}

	/**
	 * Writes color components converted to the target color space.
	 *
	 * @param sout      the output stream
	 * @param colorType the target color space type
	 * @param color     the color object
	 * @throws IOException if an I/O error occurs
	 */
	/** Gradient stops: named-color stops contribute their alternate's type. */
	private static Color.Type stopType(final Color color) {
		return switch (color.getColorType()) {
			case SPOT -> ((net.zamasoft.pdfg2d.gc.paint.SpotColor) color).effectiveColor().getColorType();
			case DEVICEN -> ((net.zamasoft.pdfg2d.gc.paint.DeviceNColor) color).effectiveColor().getColorType();
			default -> color.getColorType();
		};
	}

	private static void writeColor(final PDFOutput sout, final Color.Type colorType, final Color color)
			throws IOException {
		// Gradient interpolation happens in a process color space; spot and
		// DeviceN stops are flattened to their tinted alternates.
		if (color.getColorType() == Color.Type.SPOT) {
			writeColor(sout, colorType, ((net.zamasoft.pdfg2d.gc.paint.SpotColor) color).effectiveColor());
			return;
		}
		if (color.getColorType() == Color.Type.DEVICEN) {
			writeColor(sout, colorType, ((net.zamasoft.pdfg2d.gc.paint.DeviceNColor) color).effectiveColor());
			return;
		}
		switch (colorType) {
			case GRAY -> {
				if (color instanceof GrayColor gray) {
					sout.writeReal(gray.getComponent(0));
				} else {
					sout.writeReal(ColorUtils.toGray(color.getRed(), color.getGreen(), color.getBlue()));
				}
			}
			case RGB -> {
				sout.writeReal(color.getRed());
				sout.writeReal(color.getGreen());
				sout.writeReal(color.getBlue());
			}
			case CMYK -> {
				final var cmyk = ColorUtils.toCMYK(color);
				sout.writeReal(cmyk.getComponent(CMYKColor.C));
				sout.writeReal(cmyk.getComponent(CMYKColor.M));
				sout.writeReal(cmyk.getComponent(CMYKColor.Y));
				sout.writeReal(cmyk.getComponent(CMYKColor.K));
			}
			default -> throw new IllegalStateException("Unexpected color type: " + colorType);
		}
	}
}
