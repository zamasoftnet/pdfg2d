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
 * sRGBの色を出力インテントのCMYK ICCプロファイルへ変換します。
 * <p>
 * JDKの公開APIではrendering intentを選択できず、
 * {@link ICC_ColorSpace#fromRGB(float[])} はperceptual固定です。
 * このクラスは{@code PDFWriterImpl}ごとに1個所有されるため、キャッシュは同期せず、
 * インスタンス自体もスレッドセーフではありません。
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
	 * CMYK ICCプロファイルを使う変換器を作成します。入力値はJDKが定める
	 * {@link ColorSpace#CS_sRGB}の成分として扱われます。
	 *
	 * @param cmykProfile CMYK ICCプロファイルのバイト列
	 * @throws NullPointerException     プロファイルが{@code null}の場合
	 * @throws IllegalArgumentException ICCプロファイルでない、またはCMYKでない場合
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
	 * sRGBをCMYKへ変換します。厳密に無彩色({@code r == g == b})なら、
	 * 墨文字などがリッチブラックにならないようK単色を返します。
	 *
	 * @return 呼び出し側専用の新しい4成分配列
	 */
	public float[] toCMYK(final float red, final float green, final float blue) {
		if (red == green && green == blue) {
			return new float[] { 0, 0, 0, 1 - red };
		}
		return this.toCMYKNoNeutralRule(red, green, blue);
	}

	/**
	 * 無彩色のK単色化を行わず、sRGBを純粋にICC変換します。
	 * グラデーション停止色とメッシュ頂点の変換に使用します。
	 *
	 * @return 呼び出し側専用の新しい4成分配列
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
	 * RGB画像を出力インテントのCMYK色空間へ全画素変換します。画像の色空間が
	 * {@link ICC_ColorSpace}ならそのプロファイルを入力に使い、それ以外はsRGBと
	 * して扱います。アルファは変換せず出力画像へ保持します。
	 *
	 * @param source RGB画像
	 * @return 8bit CMYK（アルファがあればCMYK+A）の画像
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
