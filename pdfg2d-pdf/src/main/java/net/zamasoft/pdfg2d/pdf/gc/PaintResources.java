package net.zamasoft.pdfg2d.pdf.gc;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.CMYKColor;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.ConicGradient;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.Pattern;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.util.ColorUtils;

/**
 * Creates the PDF resources needed to paint with non-color paints: tiling
 * patterns for {@link Pattern}, axial/radial shading patterns for
 * {@link LinearGradient}/{@link RadialGradient}, and Type 4 mesh patterns for
 * {@link ConicGradient}.
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
	private static final double MAX_CONIC_STEP = 1.0 / 180.0;
	private static final double CONIC_INNER_RADIUS = 0.01;

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
	private record ShadingKey(double pageWidth, double pageHeight, AffineTransform transform, Paint paint) {
		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof ShadingKey other))
				return false;
			if (Double.compare(pageWidth, other.pageWidth) != 0
					|| Double.compare(pageHeight, other.pageHeight) != 0)
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
			if (paint instanceof ConicGradient cg1 && other.paint instanceof ConicGradient cg2) {
				return Double.compare(cg1.cx(), cg2.cx()) == 0 &&
						Double.compare(cg1.cy(), cg2.cy()) == 0 &&
						Double.compare(cg1.startAngle(), cg2.startAngle()) == 0 &&
						cg1.spread() == cg2.spread() &&
						Arrays.equals(cg1.colors(), cg2.colors()) &&
						Arrays.equals(cg1.fractions(), cg2.fractions());
			}
			return paint.equals(other.paint);
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(pageWidth, pageHeight, transform);
			if (paint instanceof LinearGradient lg) {
				result = 31 * result + Objects.hash(lg.x1(), lg.y1(), lg.x2(), lg.y2());
				result = 31 * result + Arrays.hashCode(lg.colors());
				result = 31 * result + Arrays.hashCode(lg.fractions());
			} else if (paint instanceof RadialGradient rg) {
				result = 31 * result + Objects.hash(rg.cx(), rg.cy(), rg.radius(), rg.fx(), rg.fy());
				result = 31 * result + Arrays.hashCode(rg.colors());
				result = 31 * result + Arrays.hashCode(rg.fractions());
			} else if (paint instanceof ConicGradient cg) {
				result = 31 * result + Objects.hash(cg.cx(), cg.cy(), cg.startAngle(), cg.spread());
				result = 31 * result + Arrays.hashCode(cg.colors());
				result = 31 * result + Arrays.hashCode(cg.fractions());
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
				final var key = new ShadingKey(pout.getWidth(), pout.getHeight(), at, gradient);
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
				final var key = new ShadingKey(pout.getWidth(), pout.getHeight(), at, gp);
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
			case CONIC_GRADIENT -> conicPaintName(gc, (ConicGradient) paint);
			case COLOR -> null;
		};
	}

	private record ConicMesh(double[] decode, byte[] vertexData) {
	}

	private static String conicPaintName(final PDFGC gc, final ConicGradient gradient) {
		if (gc.pdfVersion.v < PDFParams.Version.V_1_3.v) {
			return null;
		}
		var at = gc.getTransform();
		if (at == null) {
			at = new AffineTransform(gradient.transform());
		} else {
			at.concatenate(gradient.transform());
		}
		final var pout = gc.out;
		final var key = new ShadingKey(pout.getWidth(), pout.getHeight(), at, gradient);
		var name = gc.resourceCache.get(key);
		if (name != null) {
			return name;
		}

		final var colorType = shadingColorType(pout.getPdfWriter().getParams(), gradient.colors());
		final var mesh = createConicMesh(gradient, at, pout.getWidth(), pout.getHeight(), colorType, false);
		try {
			name = pout.getPdfWriter().createType4ShadingPattern(pout.getHeight(), at, colorType,
					mesh.decode(), mesh.vertexData());
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		gc.resourceCache.put(key, name);
		return name;
	}

	private static ConicMesh createConicMesh(final ConicGradient gradient, final AffineTransform patternMatrix,
			final double width, final double height, final Color.Type colorType, final boolean alphaOnly) {
		if (!Double.isFinite(gradient.cx()) || !Double.isFinite(gradient.cy())
				|| !Double.isFinite(gradient.startAngle()) || !Double.isFinite(width) || !Double.isFinite(height)
				|| !(width > 0) || !(height > 0)) {
			throw new GraphicsException("Conic gradient geometry must be finite and non-empty.");
		}
		final int n = Math.min(gradient.fractions().length, gradient.colors().length);
		if (n == 0) {
			throw new GraphicsException("A conic gradient requires at least one color stop.");
		}
		for (var i = 0; i < n; ++i) {
			final double fraction = gradient.fractions()[i];
			if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1
					|| (i > 0 && fraction < gradient.fractions()[i - 1])) {
				throw new GraphicsException("Conic gradient fractions must be finite and sorted within 0..1.");
			}
			if (gradient.colors()[i] == null) {
				throw new GraphicsException("Conic gradient colors cannot contain null.");
			}
		}

		final double[] corners = { 0, 0, width, 0, width, height, 0, height };
		try {
			final var pdfPatternMatrix = new AffineTransform(patternMatrix);
			pdfPatternMatrix.preConcatenate(new AffineTransform(1, 0, 0, -1, 0, height));
			pdfPatternMatrix.createInverse().transform(corners, 0, corners, 0, 4);
		} catch (NoninvertibleTransformException e) {
			throw new GraphicsException("The conic gradient pattern matrix is not invertible.", e);
		}
		double radius = 0;
		for (var i = 0; i < corners.length; i += 2) {
			final double distance = Math.hypot(corners[i] - gradient.cx(), corners[i + 1] - gradient.cy());
			if (!Double.isFinite(distance)) {
				throw new GraphicsException("The conic gradient extent is not finite.");
			}
			radius = Math.max(radius, distance);
		}
		radius *= 1.05;
		if (!(radius > 0) || !Double.isFinite(radius)) {
			throw new GraphicsException("The conic gradient extent is empty.");
		}

		final int components = alphaOnly ? 1 : switch (colorType) {
			case GRAY -> 1;
			case RGB -> 3;
			case CMYK -> 4;
			default -> throw new GraphicsException("Unsupported conic mesh color type: " + colorType);
		};
		final double[] decode = new double[4 + 2 * components];
		decode[0] = gradient.cx() - radius;
		decode[1] = gradient.cx() + radius;
		decode[2] = gradient.cy() - radius;
		decode[3] = gradient.cy() + radius;
		for (var i = 0; i < components; ++i) {
			decode[4 + 2 * i] = 0;
			decode[5 + 2 * i] = 1;
		}

		// One supplied turn is authoritative. FolioJet expands repeating conics
		// before constructing this paint, so no second periodic expansion belongs
		// in the PDF backend.
		final var data = new ByteArrayOutputStream();
		if (n == 1) {
			writeConicSegment(data, gradient, decode, colorType, alphaOnly, radius,
					0, 1, gradient.colors()[0], gradient.colors()[0]);
		} else {
			final double first = gradient.fractions()[0];
			if (first > 0) {
				writeConicSegment(data, gradient, decode, colorType, alphaOnly, radius,
						0, first, gradient.colors()[0], gradient.colors()[0]);
			}
			for (var i = 0; i < n - 1; ++i) {
				writeConicSegment(data, gradient, decode, colorType, alphaOnly, radius,
						gradient.fractions()[i], gradient.fractions()[i + 1],
						gradient.colors()[i], gradient.colors()[i + 1]);
			}
			final double last = gradient.fractions()[n - 1];
			if (last < 1) {
				writeConicSegment(data, gradient, decode, colorType, alphaOnly, radius,
						last, 1, gradient.colors()[n - 1], gradient.colors()[n - 1]);
			}
		}
		return new ConicMesh(decode, data.toByteArray());
	}

	private static void writeConicSegment(final ByteArrayOutputStream out, final ConicGradient gradient,
			final double[] decode, final Color.Type colorType, final boolean alphaOnly, final double outerRadius,
			final double start, final double end, final Color startColor, final Color endColor) {
		final double extent = end - start;
		final int steps = extent == 0 ? 1 : Math.max(1, (int) Math.ceil(extent / MAX_CONIC_STEP));
		for (var i = 0; i < steps; ++i) {
			final double ratio0 = (double) i / steps;
			final double ratio1 = (double) (i + 1) / steps;
			final double fraction0 = start + extent * ratio0;
			final double fraction1 = start + extent * ratio1;
			final Color color0 = interpolate(startColor, endColor, ratio0);
			final Color color1 = interpolate(startColor, endColor, ratio1);
			writeConicSector(out, gradient, decode, colorType, alphaOnly, outerRadius,
					fraction0, fraction1, color0, color1);
		}
	}

	private static Color interpolate(final Color a, final Color b, final double ratio) {
		// Always flatten through straight RGB, including at endpoints. This keeps
		// CMYK/spot stops consistent with the Java2D conic paint before the sampled
		// result is converted to the mesh's effective process color space.
		final float f = (float) Math.max(0, Math.min(1, ratio));
		return RGBAColor.create(
				a.getRed() + (b.getRed() - a.getRed()) * f,
				a.getGreen() + (b.getGreen() - a.getGreen()) * f,
				a.getBlue() + (b.getBlue() - a.getBlue()) * f,
				a.getAlpha() + (b.getAlpha() - a.getAlpha()) * f);
	}

	private static void writeConicSector(final ByteArrayOutputStream out, final ConicGradient gradient,
			final double[] decode, final Color.Type colorType, final boolean alphaOnly, final double outerRadius,
			final double fraction0, final double fraction1, final Color color0, final Color color1) {
		final double angle0 = gradient.startAngle() + 2 * Math.PI * fraction0;
		final double angle1 = gradient.startAngle() + 2 * Math.PI * fraction1;
		final double ix0 = gradient.cx() + Math.sin(angle0) * CONIC_INNER_RADIUS;
		final double iy0 = gradient.cy() - Math.cos(angle0) * CONIC_INNER_RADIUS;
		final double ix1 = gradient.cx() + Math.sin(angle1) * CONIC_INNER_RADIUS;
		final double iy1 = gradient.cy() - Math.cos(angle1) * CONIC_INNER_RADIUS;
		final double ox0 = gradient.cx() + Math.sin(angle0) * outerRadius;
		final double oy0 = gradient.cy() - Math.cos(angle0) * outerRadius;
		final double ox1 = gradient.cx() + Math.sin(angle1) * outerRadius;
		final double oy1 = gradient.cy() - Math.cos(angle1) * outerRadius;

		writeMeshVertex(out, decode, colorType, alphaOnly, ix0, iy0, color0);
		writeMeshVertex(out, decode, colorType, alphaOnly, ox0, oy0, color0);
		writeMeshVertex(out, decode, colorType, alphaOnly, ox1, oy1, color1);
		writeMeshVertex(out, decode, colorType, alphaOnly, ix0, iy0, color0);
		writeMeshVertex(out, decode, colorType, alphaOnly, ox1, oy1, color1);
		writeMeshVertex(out, decode, colorType, alphaOnly, ix1, iy1, color1);
	}

	private static void writeMeshVertex(final ByteArrayOutputStream out, final double[] decode,
			final Color.Type colorType, final boolean alphaOnly, final double x, final double y, final Color color) {
		out.write(0); // no edge reuse: every triangle contains three explicit vertices
		writeUInt32(out, quantize32(x, decode[0], decode[1]));
		writeUInt32(out, quantize32(y, decode[2], decode[3]));
		if (alphaOnly) {
			writeUInt16(out, quantize16(color.getAlpha()));
			return;
		}
		switch (colorType) {
			case GRAY -> writeUInt16(out, quantize16(ColorUtils.toGray(
					color.getRed(), color.getGreen(), color.getBlue())));
			case RGB -> {
				writeUInt16(out, quantize16(color.getRed()));
				writeUInt16(out, quantize16(color.getGreen()));
				writeUInt16(out, quantize16(color.getBlue()));
			}
			case CMYK -> {
				final var cmyk = ColorUtils.toCMYK(color);
				writeUInt16(out, quantize16(cmyk.getComponent(CMYKColor.C)));
				writeUInt16(out, quantize16(cmyk.getComponent(CMYKColor.M)));
				writeUInt16(out, quantize16(cmyk.getComponent(CMYKColor.Y)));
				writeUInt16(out, quantize16(cmyk.getComponent(CMYKColor.K)));
			}
			default -> throw new GraphicsException("Unsupported conic mesh color type: " + colorType);
		}
	}

	private static long quantize32(final double value, final double minimum, final double maximum) {
		final double normalized = Math.max(0, Math.min(1, (value - minimum) / (maximum - minimum)));
		return Math.round(normalized * 0xffff_ffffL);
	}

	private static int quantize16(final double value) {
		return (int) Math.round(Math.max(0, Math.min(1, value)) * 0xffff);
	}

	private static void writeUInt32(final ByteArrayOutputStream out, final long value) {
		out.write((int) (value >>> 24) & 0xff);
		out.write((int) (value >>> 16) & 0xff);
		out.write((int) (value >>> 8) & 0xff);
		out.write((int) value & 0xff);
	}

	private static void writeUInt16(final ByteArrayOutputStream out, final int value) {
		out.write(value >>> 8);
		out.write(value);
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
		writeShadingFunction(sout, shadingColorType(params, colors), colors, fractions);
	}

	private static Color.Type shadingColorType(final PDFParams params, final Color[] colors) {
		if (params.effectiveColorMode() == PDFParams.ColorMode.GRAY) {
			return Color.Type.GRAY;
		}
		if (params.effectiveColorMode() == PDFParams.ColorMode.CMYK) {
			return Color.Type.CMYK;
		}
		if (colors.length == 0) {
			throw new IllegalArgumentException("A shading requires at least one color.");
		}
		var type = stopType(colors[0]);
		for (var i = 1; i < colors.length; ++i) {
			if (type != stopType(colors[i])) {
				type = Color.Type.RGB;
			}
		}
		return type == Color.Type.RGBA ? Color.Type.RGB : type;
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
		} else if (paint instanceof ConicGradient cg) {
			colors = cg.colors();
			fractions = cg.fractions();
			paintTransform = cg.transform();
		} else {
			return null;
		}
		if (!hasAlpha(colors)) {
			return null;
		}

		var at = gc.getTransform();
		if (at == null) {
			at = paintTransform != null ? new AffineTransform(paintTransform) : new AffineTransform();
		} else if (paintTransform != null) {
			at.concatenate(paintTransform);
		}

		final var pout = gc.out;
		final var key = new MaskKey(new ShadingKey(pout.getWidth(), pout.getHeight(), at, paint));
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
		try {
			if (paint instanceof ConicGradient cg) {
				final var mesh = createConicMesh(cg, at, pout.getWidth(), pout.getHeight(), Color.Type.GRAY, true);
				shadingName = pout.getPdfWriter().createType4ShadingPattern(pout.getHeight(), at, Color.Type.GRAY,
						mesh.decode(), mesh.vertexData());
			} else {
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
				}
			}
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
