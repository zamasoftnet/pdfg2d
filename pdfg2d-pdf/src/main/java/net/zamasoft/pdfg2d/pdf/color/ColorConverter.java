package net.zamasoft.pdfg2d.pdf.color;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Converts sRGB colors to the output intent's CMYK ICC profile.
 * <p>
 * The JDK public API cannot select a rendering intent;
 * {@link ICC_ColorSpace#fromRGB(float[])} always uses perceptual.
 * Each {@code PDFWriterImpl} owns one instance, so the cache is unsynchronized
 * and the instance itself is not thread-safe.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class ColorConverter {
	private static final ColorSpace SRGB = ColorSpace.getInstance(ColorSpace.CS_sRGB);

	private record RGBKey(int red, int green, int blue) {
		static RGBKey of(final float red, final float green, final float blue) {
			return new RGBKey(Float.floatToIntBits(red), Float.floatToIntBits(green),
					Float.floatToIntBits(blue));
		}
	}

	private final ICC_ColorSpace cmykColorSpace;
	private final Map<RGBKey, float[]> cache = new HashMap<>();

	/**
	 * Creates a converter using a CMYK ICC profile. Treats input values as components
	 * of the JDK-defined {@link ColorSpace#CS_sRGB}.
	 *
	 * @param cmykProfile bytes of the CMYK ICC profile
	 * @throws NullPointerException     if the profile is {@code null}
	 * @throws IllegalArgumentException if not an ICC profile or not CMYK
	 */
	public ColorConverter(final byte[] cmykProfile) {
		Objects.requireNonNull(cmykProfile, "cmykProfile");
		final var profile = ICC_Profile.getInstance(cmykProfile);
		if (profile.getColorSpaceType() != ColorSpace.TYPE_CMYK || profile.getNumComponents() != 4) {
			throw new IllegalArgumentException("A four-component CMYK ICC profile is required.");
		}
		this.cmykColorSpace = new ICC_ColorSpace(profile);
	}

	/**
	 * Converts sRGB to CMYK. For exactly achromatic colors ({@code r == g == b}),
	 * returns K-only output so black text and similar content do not become rich black.
	 *
	 * @return a new four-component array owned exclusively by the caller
	 */
	public float[] toCMYK(final float red, final float green, final float blue) {
		if (red == green && green == blue) {
			return new float[] { 0, 0, 0, 1 - red };
		}
		return this.toCMYKNoNeutralRule(red, green, blue);
	}

	/**
	 * Performs pure ICC conversion from sRGB without mapping achromatic colors to K-only output.
	 * Used for gradient stops and mesh vertices.
	 *
	 * @return a new four-component array owned exclusively by the caller
	 */
	public float[] toCMYKNoNeutralRule(final float red, final float green, final float blue) {
		final var key = RGBKey.of(red, green, blue);
		var converted = this.cache.get(key);
		if (converted == null) {
			final var rgb = new float[SRGB.getNumComponents()];
			rgb[0] = red;
			rgb[1] = green;
			rgb[2] = blue;
			converted = this.cmykColorSpace.fromRGB(rgb);
			this.cache.put(key, converted);
		}
		return converted.clone();
	}

	/**
	 * Converts all pixels of an RGB image to the output intent's CMYK color space.
	 * If the image color space is {@link ICC_ColorSpace}, uses its profile as input;
	 * otherwise, treats it as sRGB. Preserves alpha in the output image without conversion.
	 *
	 * @param source RGB image
	 * @return 8-bit CMYK image (CMYK+A if alpha is present)
	 */
	public BufferedImage toCMYKImage(final BufferedImage source) {
		Objects.requireNonNull(source, "source");
		final var sourceColorSpace = source.getColorModel().getColorSpace() instanceof ICC_ColorSpace icc
				? icc : SRGB;
		final var hasAlpha = source.getColorModel().hasAlpha();
		final var colorModel = new ComponentColorModel(this.cmykColorSpace, hasAlpha, false,
				hasAlpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
		final var raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, source.getWidth(),
				source.getHeight(), hasAlpha ? 5 : 4, null);
		final var converted = new BufferedImage(colorModel, raster, false, null);
		new ColorConvertOp(sourceColorSpace, this.cmykColorSpace, null).filter(source, converted);
		return converted;
	}
}
