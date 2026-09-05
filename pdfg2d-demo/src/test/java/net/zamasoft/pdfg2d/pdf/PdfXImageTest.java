package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.preflight.PdfXPreflight;
import net.zamasoft.pdfg2d.pdf.preflight.PdfXPreflight.Flavour;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

/**
 * PDF/X 色管理 I3: 画像 XObject の色空間・画素配列・{@code /Decode}・
 * {@code /DefaultRGB} が版ごとに正しいことを検査します。
 * <p>
 * 画像は 1 文書 1 枚で生成し、X-1a(CMYK モード)・X-4(PRESERVE)・通常 PDF
 * の 3 版で同じ入力を比べます。
 */
public class PdfXImageTest {

	private static final int W = 4;
	private static final int H = 2;

	private static File rgbPng;
	private static File grayPng;
	private static File alphaPng;
	private static File rgbJpeg;
	private static File grayJpeg;
	private static File cmykJpegAdobe;
	private static File cmykJpegNoApp14;
	private static File ycckJpeg;

	@BeforeAll
	public static void createFixtures() throws Exception {
		rgbPng = writePng("rgb.png", sampleRgb(BufferedImage.TYPE_INT_RGB));
		grayPng = writePng("gray.png", sampleGray());
		alphaPng = writePng("alpha.png", sampleRgb(BufferedImage.TYPE_INT_ARGB));
		rgbJpeg = writeJpeg("rgb.jpg", sampleRgb(BufferedImage.TYPE_INT_RGB));
		grayJpeg = writeJpeg("gray.jpg", sampleGray());
		final var cmyk = stripApp14(writeCmykJpeg(false));
		cmykJpegNoApp14 = write("cmyk-noapp14.jpg", cmyk);
		// Adobe の慣習: APP14 付き CMYK JPEG は反転して格納される
		cmykJpegAdobe = write("cmyk-adobe.jpg", insertApp14(stripApp14(writeCmykJpeg(true)), 0));
		// transform=2 のマーカーだけを差した資材(画素は YCCK ではない)。経路の選択だけを見る
		ycckJpeg = write("ycck-adobe.jpg", insertApp14(cmyk, 2));
	}

	// ---- X-1a (CMYK) -------------------------------------------------

	@Test
	public void testX1aRgbPngBecomesFourByteCmyk() throws Exception {
		try (final var loaded = singleImage(generate("x1a-rgb-png.pdf", PDFParams.Version.V_PDFX1A, rgbPng, false))) {
			final var image = loaded.image();
			assertColorSpaceName("DeviceCMYK", image);
			assertEquals(8, image.getInt(COSName.BITS_PER_COMPONENT));
			assertNull(image.getItem(COSName.DECODE), "Flate CMYK must not carry /Decode");
			final var pixels = decoded(image);
			assertEquals(W * H * 4, pixels.length, "4 bytes per pixel");
			assertCmykProperties(pixels);
		}
	}

	@Test
	public void testX1aRgbJpegIsRecompressedToCmyk() throws Exception {
		try (final var loaded = singleImage(generate("x1a-rgb-jpg.pdf", PDFParams.Version.V_PDFX1A, rgbJpeg, false))) {
			final var image = loaded.image();
			assertColorSpaceName("DeviceCMYK", image);
			assertFalse(filters(image).contains("DCTDecode"), "RGB JPEG must not pass through in X-1a");
			assertNull(image.getItem(COSName.DECODE));
			final var pixels = decoded(image);
			assertEquals(W * H * 4, pixels.length);
			// JPEG の量子化誤差を許して性質だけ見る
			final var whiteK = pixels[3 * 4 + 3] & 0xFF;
			assertTrue(whiteK < 40, "white pixel K must be near 0, was " + whiteK);
		}
	}

	@Test
	public void testX1aGrayStaysDeviceGray() throws Exception {
		try (final var loaded = singleImage(generate("x1a-gray-png.pdf", PDFParams.Version.V_PDFX1A, grayPng, false))) {
			assertColorSpaceName("DeviceGray", loaded.image());
			assertEquals(W * H, decoded(loaded.image()).length);
		}
		try (final var loaded = singleImage(generate("x1a-gray-jpg.pdf", PDFParams.Version.V_PDFX1A, grayJpeg, false))) {
			assertColorSpaceName("DeviceGray", loaded.image());
		}
	}

	@Test
	public void testX1aAlphaPngUsesStencilMask() throws Exception {
		try (final var loaded = singleImage(generate("x1a-alpha-png.pdf", PDFParams.Version.V_PDFX1A, alphaPng, false))) {
			final var image = loaded.image();
			assertColorSpaceName("DeviceCMYK", image);
			assertEquals(W * H * 4, decoded(image).length);
			assertNull(image.getItem(COSName.SMASK), "X-1a forbids soft masks");
			assertNotNull(image.getItem(COSName.MASK), "alpha must become a 1-bit /Mask in X-1a");
		}
	}

	@Test
	public void testX1aAdobeCmykJpegPassesThroughWithInvertedDecode() throws Exception {
		try (final var loaded = singleImage(
				generate("x1a-cmyk-adobe.pdf", PDFParams.Version.V_PDFX1A, cmykJpegAdobe, false))) {
			final var image = loaded.image();
			assertColorSpaceName("DeviceCMYK", image);
			assertTrue(filters(image).contains("DCTDecode"), "CMYK JPEG must pass through");
			final var decode = image.getCOSArray(COSName.DECODE);
			assertNotNull(decode, "Adobe transform 0 needs an inverted /Decode");
			assertEquals(8, decode.size());
			final var values = decode.toFloatArray();
			assertEquals(1f, values[0], 0f);
			assertEquals(0f, values[1], 0f);
		}
	}

	@Test
	public void testX1aCmykJpegWithoutApp14HasNoDecode() throws Exception {
		try (final var loaded = singleImage(
				generate("x1a-cmyk-noapp14.pdf", PDFParams.Version.V_PDFX1A, cmykJpegNoApp14, false))) {
			final var image = loaded.image();
			assertColorSpaceName("DeviceCMYK", image);
			assertTrue(filters(image).contains("DCTDecode"));
			assertNull(image.getItem(COSName.DECODE), "no APP14 means no inversion");
		}
	}

	@Test
	public void testX1aYcckJpegIsRecompressed() throws Exception {
		try (final var loaded = singleImage(generate("x1a-ycck.pdf", PDFParams.Version.V_PDFX1A, ycckJpeg, false))) {
			final var image = loaded.image();
			assertColorSpaceName("DeviceCMYK", image);
			assertFalse(filters(image).contains("DCTDecode"), "YCCK must be re-encoded, not passed through");
			assertNull(image.getItem(COSName.DECODE));
			assertEquals(W * H * 4, decoded(image).length);
		}
	}

	@Test
	public void testX1aGeneratedImageBecomesCmyk() throws Exception {
		try (final var loaded = singleImage(generate("x1a-generated.pdf", PDFParams.Version.V_PDFX1A, null, true))) {
			assertColorSpaceName("DeviceCMYK", loaded.image());
			assertCmykProperties(decoded(loaded.image()));
		}
	}

	// ---- X-4 (PRESERVE) ----------------------------------------------

	@Test
	public void testX4RgbPngUsesIccBasedAndDefaultRgb() throws Exception {
		try (final var loaded = singleImage(generate("x4-rgb-png.pdf", PDFParams.Version.V_PDFX4, rgbPng, false))) {
			assertIccBased(loaded.image(), 3);
			assertEquals(W * H * 3, decoded(loaded.image()).length, "RGB stays 3 bytes per pixel");
			assertDefaultRgb(loaded.document());
		}
	}

	@Test
	public void testX4RgbJpegPassesThroughAsIccBased() throws Exception {
		try (final var loaded = singleImage(generate("x4-rgb-jpg.pdf", PDFParams.Version.V_PDFX4, rgbJpeg, false))) {
			assertTrue(filters(loaded.image()).contains("DCTDecode"), "X-4 keeps the JPEG bytes");
			assertIccBased(loaded.image(), 3);
		}
	}

	@Test
	public void testX4GrayAndCmykKeepDeviceSpaces() throws Exception {
		try (final var loaded = singleImage(generate("x4-gray-png.pdf", PDFParams.Version.V_PDFX4, grayPng, false))) {
			assertColorSpaceName("DeviceGray", loaded.image());
		}
		try (final var loaded = singleImage(
				generate("x4-cmyk-adobe.pdf", PDFParams.Version.V_PDFX4, cmykJpegAdobe, false))) {
			assertColorSpaceName("DeviceCMYK", loaded.image());
		}
	}

	@Test
	public void testX4AlphaPngUsesGraySoftMask() throws Exception {
		try (final var loaded = singleImage(generate("x4-alpha-png.pdf", PDFParams.Version.V_PDFX4, alphaPng, false))) {
			assertIccBased(loaded.image(), 3);
			final var smask = (COSStream) resolve(loaded.image().getItem(COSName.SMASK));
			assertNotNull(smask, "alpha must become /SMask in X-4");
			assertColorSpaceName("DeviceGray", smask);
		}
	}

	@Test
	public void testX4GeneratedImageUsesIccBased() throws Exception {
		try (final var loaded = singleImage(generate("x4-generated.pdf", PDFParams.Version.V_PDFX4, null, true))) {
			assertIccBased(loaded.image(), 3);
			assertDefaultRgb(loaded.document());
		}
	}

	// ---- plain PDF ---------------------------------------------------

	@Test
	public void testPlainPdfKeepsDeviceRgb() throws Exception {
		try (final var loaded = singleImage(generate("plain-rgb-png.pdf", PDFParams.Version.V_1_5, rgbPng, false))) {
			assertColorSpaceName("DeviceRGB", loaded.image());
			assertEquals(W * H * 3, decoded(loaded.image()).length);
			final var colorSpaces = loaded.document().getPage(0).getResources().getCOSObject()
					.getCOSDictionary(COSName.COLORSPACE);
			assertTrue(colorSpaces == null || !colorSpaces.containsKey("DefaultRGB"),
					"plain PDF must not gain /DefaultRGB");
		}
		try (final var loaded = singleImage(generate("plain-rgb-jpg.pdf", PDFParams.Version.V_1_5, rgbJpeg, false))) {
			assertColorSpaceName("DeviceRGB", loaded.image());
			assertTrue(filters(loaded.image()).contains("DCTDecode"));
		}
	}

	// ---- color modes and lossy paths (codex review 2026-09-05) --------

	@Test
	public void testGrayModeProducesDeviceGrayImage() throws Exception {
		final var params = params(PDFParams.Version.V_PDFX4).withColorMode(PDFParams.ColorMode.GRAY);
		try (final var loaded = singleImage(generate("x4-gray-mode-rgb-png.pdf", params, rgbPng, false))) {
			assertColorSpaceName("DeviceGray", loaded.image());
			final var pixels = decoded(loaded.image());
			assertEquals(W * H, pixels.length, "1 byte per pixel");
			assertEquals(255, pixels[3] & 0xFF, "white stays white");
			assertEquals(0, pixels[W] & 0xFF, "black stays black");
			final var mid = pixels[W + 1] & 0xFF;
			assertTrue(mid > 100 && mid < 156, "#808080 must stay mid gray (no gamma LUT), was " + mid);
		}
		try (final var loaded = singleImage(generate("x4-gray-mode-alpha-png.pdf", params, alphaPng, false))) {
			assertColorSpaceName("DeviceGray", loaded.image());
			assertNotNull(loaded.image().getItem(COSName.SMASK), "alpha survives the gray conversion");
		}
	}

	@Test
	public void testGrayPngKeepsRawSamples() throws Exception {
		try (final var loaded = singleImage(generate("x4-gray-samples.pdf", PDFParams.Version.V_PDFX4, grayPng, false))) {
			final var pixels = decoded(loaded.image());
			assertEquals(0, pixels[0] & 0xFF);
			assertEquals(255, pixels[W * H - 1] & 0xFF);
			// (x+y*W)*255/7: 36, 72, 109 ... の等間隔。LUT を通ると 128 が 188 になる
			assertEquals(109, pixels[3] & 0xFF, 1);
		}
	}

	@Test
	public void testLossyAlphaGrayUsesGrayJpeg() throws Exception {
		final var params = params(PDFParams.Version.V_PDFX4).withColorMode(PDFParams.ColorMode.GRAY)
				.withImageCompression(PDFParams.ImageCompression.JPEG).withImageCompressionLossless(0);
		try (final var loaded = singleImage(generate("x4-lossy-gray-alpha.pdf", params, alphaPng, false))) {
			assertColorSpaceName("DeviceGray", loaded.image());
			assertTrue(filters(loaded.image()).contains("DCTDecode"));
			final var jpeg = decodedRaw(loaded.image());
			assertEquals(1, jpegComponents(jpeg), "gray dictionary needs a 1-component JPEG");
		}
	}

	@Test
	public void testLossyAlphaCmykFallsBackToFlate() throws Exception {
		final var params = params(PDFParams.Version.V_PDFX1A)
				.withImageCompression(PDFParams.ImageCompression.JPEG).withImageCompressionLossless(0);
		try (final var loaded = singleImage(generate("x1a-lossy-cmyk-alpha.pdf", params, alphaPng, false))) {
			assertColorSpaceName("DeviceCMYK", loaded.image());
			assertFalse(filters(loaded.image()).contains("DCTDecode"), "CMYK+alpha must not be JPEG encoded as RGB");
			assertEquals(W * H * 4, decoded(loaded.image()).length);
		}
	}

	/** CMYK 画像の縮小は成分ごと(RGB 往復で灰 K が 4 色にならない)。 */
	@Test
	public void testCmykResizeKeepsPlates() throws Exception {
		final var cmykSpace = new java.awt.color.ICC_ColorSpace(java.awt.color.ICC_Profile.getInstance(
				PDFWriterImpl.class.getResourceAsStream("ISOcoated_v2_300_eci.icc").readAllBytes()));
		final var cm = new java.awt.image.ComponentColorModel(cmykSpace, false, false, java.awt.Transparency.OPAQUE,
				DataBuffer.TYPE_BYTE);
		final var image = new BufferedImage(cm, cm.createCompatibleWritableRaster(4, 2), false, null);
		for (var y = 0; y < 2; ++y) {
			for (var x = 0; x < 4; ++x) {
				image.getRaster().setPixel(x, y, new int[] { 0, 0, 0, 128 });
			}
		}
		final var params = params(PDFParams.Version.V_PDFX1A).withMaxImageWidth(2).withMaxImageHeight(1);
		final var out = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(out);
		final var pdf = new PDFWriterImpl(builder, params);
		try (final var gc = new PDFGC(pdf.nextPage(200, 200))) {
			gc.drawImage(pdf.addImage(image));
		}
		pdf.close();
		builder.close();
		try (final var loaded = singleImage(out.toByteArray())) {
			assertColorSpaceName("DeviceCMYK", loaded.image());
			assertEquals(2, loaded.image().getInt(COSName.WIDTH));
			final var pixels = decoded(loaded.image());
			assertEquals(2 * 1 * 4, pixels.length);
			for (var i = 0; i < pixels.length; i += 4) {
				assertEquals(0, pixels[i] & 0xFF, "C");
				assertEquals(0, pixels[i + 1] & 0xFF, "M");
				assertEquals(0, pixels[i + 2] & 0xFF, "Y");
				assertEquals(128, pixels[i + 3] & 0xFF, 1, "K");
			}
		}
	}

	@Test
	public void testPlainPdfGeneratedImageAddsNoDefaultRgb() throws Exception {
		try (final var loaded = singleImage(generate("plain-generated.pdf", PDFParams.Version.V_1_5, null, true))) {
			assertIccBased(loaded.image(), 3);
			final var colorSpaces = loaded.document().getPage(0).getResources().getCOSObject()
					.getCOSDictionary(COSName.COLORSPACE);
			assertTrue(colorSpaces == null || !colorSpaces.containsKey("DefaultRGB"),
					"plain PDF must not gain /DefaultRGB from a generated image");
		}
	}

	// ---- preflight over every fixture --------------------------------

	@Test
	public void testEveryImageKindPassesPreflight() throws Exception {
		for (final var flavour : Flavour.values()) {
			final var version = flavour == Flavour.X1A ? PDFParams.Version.V_PDFX1A : PDFParams.Version.V_PDFX4;
			final var bytes = generateAll("preflight-" + flavour.name().toLowerCase() + ".pdf", version);
			PdfXPreflight.assertConforms(bytes, flavour);
		}
	}

	// ---- generation --------------------------------------------------

	private static PDFParams params(final PDFParams.Version version) {
		final var meta = new PDFMetaInfo();
		meta.setTitle("PDF/X image fixture");
		meta.setCreationDate(1_700_000_000_000L);
		meta.setModDate(1_700_000_000_000L);
		return PDFParams.createDefault()
				.withVersion(version)
				.withCompression(PDFParams.Compression.BINARY)
				.withFileId(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 })
				.withMetaInfo(meta);
	}

	private static byte[] generate(final String name, final PDFParams.Version version, final File source,
			final boolean generated) throws Exception {
		return generate(name, params(version), source, generated);
	}

	private static byte[] generate(final String name, final PDFParams params, final File source,
			final boolean generated) throws Exception {
		final var out = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(out);
		final var pdf = new PDFWriterImpl(builder, params);
		try (final var gc = new PDFGC(pdf.nextPage(200, 200))) {
			gc.drawImage(generated ? pdf.addGeneratedImage(sampleRgb(BufferedImage.TYPE_INT_RGB))
					: pdf.loadImage(new FileSource(source)));
		}
		pdf.close();
		builder.close();
		final var bytes = out.toByteArray();
		Files.write(TestOutputFiles.outputFile(PdfXImageTest.class, name).toPath(), bytes);
		return bytes;
	}

	private static byte[] generateAll(final String name, final PDFParams.Version version) throws Exception {
		final var out = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(out);
		final var pdf = new PDFWriterImpl(builder, params(version));
		try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
			var y = 0;
			for (final var file : List.of(rgbPng, grayPng, alphaPng, rgbJpeg, grayJpeg, cmykJpegAdobe,
					cmykJpegNoApp14, ycckJpeg)) {
				try (var state = gc.begin()) {
					gc.transform(java.awt.geom.AffineTransform.getTranslateInstance(0, y));
					gc.drawImage(pdf.loadImage(new FileSource(file)));
				}
				y += 10;
			}
			try (var state = gc.begin()) {
				gc.transform(java.awt.geom.AffineTransform.getTranslateInstance(0, y));
				gc.drawImage(pdf.addGeneratedImage(sampleRgb(BufferedImage.TYPE_INT_RGB)));
			}
			gc.setFillPaint(net.zamasoft.pdfg2d.gc.paint.RGBColor.create(1, 0, 0));
			gc.fill(new Rectangle2D.Double(100, 100, 50, 50));
		}
		pdf.close();
		builder.close();
		final var bytes = out.toByteArray();
		Files.write(TestOutputFiles.outputFile(PdfXImageTest.class, name).toPath(), bytes);
		return bytes;
	}

	// ---- fixtures ----------------------------------------------------

	/** 上段: 半透明の赤・緑・青・白、下段: 黒・灰・黄・シアン。 */
	private static BufferedImage sampleRgb(final int type) {
		final var image = new BufferedImage(W, H, type);
		final var alpha = type == BufferedImage.TYPE_INT_ARGB;
		image.setRGB(0, 0, (alpha ? 0x80000000 : 0xFF000000) | 0xFF0000);
		image.setRGB(1, 0, 0xFF00FF00);
		image.setRGB(2, 0, 0xFF0000FF);
		image.setRGB(3, 0, 0xFFFFFFFF);
		image.setRGB(0, 1, 0xFF000000);
		image.setRGB(1, 1, 0xFF808080);
		image.setRGB(2, 1, 0xFFFFFF00);
		image.setRGB(3, 1, 0xFF00FFFF);
		return image;
	}

	private static BufferedImage sampleGray() {
		final var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
		for (var y = 0; y < H; ++y) {
			for (var x = 0; x < W; ++x) {
				// setRGB() は sRGB→線形の LUT を通す(128→55)ので、ファイルに書く
				// 値を決めるためにサンプルへ直接置く(gamma 付き gray PNG と同じ形)
				image.getRaster().setSample(x, y, 0, (x + y * W) * 255 / (W * H - 1));
			}
		}
		return image;
	}

	private static File writePng(final String name, final BufferedImage image) throws Exception {
		final var file = TestOutputFiles.outputFile(PdfXImageTest.class, name);
		ImageIO.write(image, "png", file);
		return file;
	}

	private static File writeJpeg(final String name, final BufferedImage image) throws Exception {
		final var file = TestOutputFiles.outputFile(PdfXImageTest.class, name);
		ImageIO.write(image, "jpeg", file);
		return file;
	}

	private static File write(final String name, final byte[] bytes) throws Exception {
		final var file = TestOutputFiles.outputFile(PdfXImageTest.class, name);
		Files.write(file.toPath(), bytes);
		return file;
	}

	/** JDK の JPEG writer に 4 バンドの Raster を渡して 4 成分 JPEG を作る(色変換なし)。 */
	private static byte[] writeCmykJpeg(final boolean inverted) throws Exception {
		final var raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, W, H, 4, null);
		final int[][] cmyk = {
				{ 0, 255, 255, 0 }, { 255, 0, 255, 0 }, { 255, 255, 0, 0 }, { 0, 0, 0, 0 },
				{ 0, 0, 0, 255 }, { 0, 0, 0, 128 }, { 0, 0, 255, 0 }, { 255, 0, 0, 0 } };
		for (var i = 0; i < cmyk.length; ++i) {
			final var px = cmyk[i].clone();
			if (inverted) {
				for (var c = 0; c < 4; ++c) {
					px[c] = 255 - px[c];
				}
			}
			raster.setPixel(i % W, i / W, px);
		}
		ImageWriter writer = null;
		for (final var itr = ImageIO.getImageWritersByFormatName("jpeg"); itr.hasNext();) {
			final var candidate = itr.next();
			if (candidate.canWriteRasters()) {
				writer = candidate;
				break;
			}
		}
		assertNotNull(writer, "a JPEG writer that accepts rasters");
		final var out = new ByteArrayOutputStream();
		try (final var ios = new MemoryCacheImageOutputStream(out)) {
			writer.setOutput(ios);
			writer.write(null, new IIOImage(raster, null, null), writer.getDefaultWriteParam());
		} finally {
			writer.dispose();
		}
		return out.toByteArray();
	}

	/** APP14 セグメントを全部取り除く。 */
	private static byte[] stripApp14(final byte[] jpeg) {
		final var out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2);
		var pos = 2;
		while (pos + 4 <= jpeg.length) {
			if ((jpeg[pos] & 0xFF) != 0xFF) {
				break;
			}
			final var marker = jpeg[pos + 1] & 0xFF;
			if (marker == 0xDA) {
				break;
			}
			final var length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
			if (marker != 0xEE) {
				out.write(jpeg, pos, length + 2);
			}
			pos += length + 2;
		}
		out.write(jpeg, pos, jpeg.length - pos);
		return out.toByteArray();
	}

	/** SOI 直後に Adobe APP14(transform 指定)を差し込む。 */
	private static byte[] insertApp14(final byte[] jpeg, final int transform) {
		final byte[] app14 = { (byte) 0xFF, (byte) 0xEE, 0x00, 0x0E, 'A', 'd', 'o', 'b', 'e', 0x00, 0x64, 0x00, 0x00,
				0x00, 0x00, (byte) transform };
		final var out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2);
		out.write(app14, 0, app14.length);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	// ---- assertions --------------------------------------------------

	/** 文書を開いたまま画像を保持する(閉じると PDFBox のストリームが読めなくなる)。 */
	private record Loaded(PDDocument document, COSStream image) implements AutoCloseable {
		@Override
		public void close() throws Exception {
			this.document.close();
		}
	}

	private static Loaded singleImage(final byte[] pdf) throws Exception {
		final var document = Loader.loadPDF(pdf);
		final var images = new ArrayList<COSStream>();
		collectImages(document, images);
		assertEquals(1, images.size(), "exactly one image XObject");
		return new Loaded(document, images.get(0));
	}

	private static void collectImages(final PDDocument document, final List<COSStream> images) throws Exception {
		final var resources = document.getPage(0).getResources();
		for (final var name : resources.getXObjectNames()) {
			if (resources.getXObject(name) instanceof PDImageXObject image) {
				images.add(image.getCOSObject());
			}
		}
	}

	private static COSBase resolve(final COSBase base) {
		return base instanceof COSObject object ? object.getObject() : base;
	}

	private static void assertColorSpaceName(final String expected, final COSStream image) {
		final var cs = resolve(image.getItem(COSName.COLORSPACE));
		assertTrue(cs instanceof COSName, "expected /" + expected + " but was " + cs);
		assertEquals(expected, ((COSName) cs).getName());
	}

	private static void assertIccBased(final COSStream image, final int n) throws Exception {
		final var cs = resolve(image.getItem(COSName.COLORSPACE));
		assertTrue(cs instanceof COSArray, "expected [/ICCBased ...] but was " + cs);
		final var array = (COSArray) cs;
		assertEquals("ICCBased", ((COSName) array.get(0)).getName());
		final var profile = (COSStream) resolve(array.get(1));
		assertEquals(n, profile.getInt(COSName.N));
	}

	private static void assertDefaultRgb(final PDDocument document) throws Exception {
		final var colorSpaces = document.getPage(0).getResources().getCOSObject()
				.getCOSDictionary(COSName.COLORSPACE);
		assertNotNull(colorSpaces, "page Resources must have /ColorSpace");
		final var defaultRgb = resolve(colorSpaces.getItem("DefaultRGB"));
		assertTrue(defaultRgb instanceof COSArray, "/DefaultRGB must be [/ICCBased ...], was " + defaultRgb);
		assertEquals("ICCBased", ((COSName) ((COSArray) defaultRgb).get(0)).getName());
	}

	private static List<String> filters(final COSStream stream) {
		final var filter = resolve(stream.getItem(COSName.FILTER));
		final var names = new ArrayList<String>();
		if (filter instanceof COSName name) {
			names.add(name.getName());
		} else if (filter instanceof COSArray array) {
			for (final var item : array) {
				names.add(((COSName) resolve(item)).getName());
			}
		}
		return names;
	}

	private static byte[] decodedRaw(final COSStream stream) throws Exception {
		try (final var in = stream.createRawInputStream()) {
			return in.readAllBytes();
		}
	}

	/** SOF セグメントの成分数。 */
	private static int jpegComponents(final byte[] jpeg) {
		var pos = 2;
		while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
			final var marker = jpeg[pos + 1] & 0xFF;
			final var length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
			if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
				return jpeg[pos + 9] & 0xFF;
			}
			pos += length + 2;
		}
		return -1;
	}

	private static byte[] decoded(final COSStream stream) throws Exception {
		try (final var in = stream.createInputStream()) {
			return in.readAllBytes();
		}
	}

	/** 白→全 0、黒→K が最大成分、赤→M,Y が 200 超(4 バイト/画素、上段の並び)。 */
	private static void assertCmykProperties(final byte[] p) {
		// (3,0) white
		final var white = 3 * 4;
		assertTrue((p[white] & 0xFF) < 8 && (p[white + 1] & 0xFF) < 8 && (p[white + 2] & 0xFF) < 8
				&& (p[white + 3] & 0xFF) < 8, "white must convert to (almost) no ink");
		// (0,1) black
		final var black = W * 4;
		final var k = p[black + 3] & 0xFF;
		assertTrue(k >= (p[black] & 0xFF) && k >= (p[black + 1] & 0xFF) && k >= (p[black + 2] & 0xFF),
				"black must be carried mainly by K");
		assertTrue(k > 200, "black K was " + k);
		// (0,0) red
		assertTrue((p[1] & 0xFF) > 200 && (p[2] & 0xFF) > 200, "red must be M,Y > 200: "
				+ (p[1] & 0xFF) + "," + (p[2] & 0xFF));
		assertTrue((p[0] & 0xFF) < 40, "red must have little C");
	}
}
