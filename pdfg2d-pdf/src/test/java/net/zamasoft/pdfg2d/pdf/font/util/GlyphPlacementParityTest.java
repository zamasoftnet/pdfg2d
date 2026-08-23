package net.zamasoft.pdfg2d.pdf.font.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.pdf.PDFGraphicsOutput;

/**
 * グリフ位置進行のbackend別方針を固定する特性テストです(2026-08-01、
 * 2026-08-23更新)。
 *
 * <p>
 * {@code xadvance[i]}はグリフiの直前に適用する。計測、PDF CID、AWT fallback、
 * アウトライン、SVGの各経路でこの意味を揃える。ルビの均等配置や
 * style-run境界の和文約物詰めは先頭補正を使うため、{@code [0]}を無視したり
 * 次のグリフへ送ったりしてはならない。アウトライン経路は
 * {@code FontUtilsLeadingXAdvanceTest}、統合経路はfoliojetのvisual goldenで固定する。
 * </p>
 */
public class GlyphPlacementParityTest {

	private static final class FixedMetrics implements FontMetrics {
		private static final long serialVersionUID = 1L;

		private final double kerning;

		FixedMetrics(final double kerning) {
			this.kerning = kerning;
		}

		public double getFontSize() {
			return 10;
		}

		public double getXHeight() {
			return 5;
		}

		public double getAscent() {
			return 8;
		}

		public double getDescent() {
			return 2;
		}

		public double getAdvance(final int gid) {
			return 10;
		}

		public double getWidth(final int gid) {
			return 10;
		}

		public double getSpaceAdvance() {
			return 10;
		}

		public double getKerning(final int gid, final int sgid) {
			return this.kerning;
		}

		public FontSource getFontSource() {
			return null;
		}
	}

	private static TextImpl text(final int glyphs, final double kerning) {
		final TextImpl text = new TextImpl(0, new FontStyleImpl(FontFamilyList.SERIF, 10, FontStyle.Style.NORMAL,
				FontStyle.Weight.W_400, FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE),
				new FixedMetrics(kerning));
		for (int i = 0; i < glyphs; ++i) {
			text.appendGlyph(new char[] { (char) ('a' + i) }, 0, (byte) 1, i + 1);
		}
		text.pack();
		return text;
	}

	private static String drawCid(final TextImpl text) throws Exception {
		// PDFGraphicsOutputのコンストラクタはPDFWriter.getParams()しか使わない
		// ため、動的Proxyで既定Paramsだけ返す最小スタブを作る
		final net.zamasoft.pdfg2d.pdf.PDFWriter writer = (net.zamasoft.pdfg2d.pdf.PDFWriter) java.lang.reflect.Proxy
				.newProxyInstance(GlyphPlacementParityTest.class.getClassLoader(),
						new Class<?>[] { net.zamasoft.pdfg2d.pdf.PDFWriter.class }, (proxy, method, args) -> {
							if ("getParams".equals(method.getName())) {
								return net.zamasoft.pdfg2d.pdf.params.PDFParams.createDefault();
							}
							throw new UnsupportedOperationException(method.getName());
						});
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (PDFGraphicsOutput out = new PDFGraphicsOutput(writer, buffer, 100, 100) {
			@Override
			public void useResource(final String type, final String name) {
				// テストでは資源参照を記録しない
			}
		}) {
			PDFFontUtils.drawCIDTo(out, text, false);
		}
		return buffer.toString(StandardCharsets.ISO_8859_1);
	}

	@Test
	public void testMeasurementIncludesLeadingXAdvance() {
		// 計測(getAdvance)は先頭グリフのxadvanceも合算する——ルビの
		// 先頭半アキが行幅に入るのはこの仕様による
		final TextImpl text = text(3, 0);
		final double base = text.getAdvance();
		text.addXAdvance(0, 5);
		assertEquals(base + 5, text.getAdvance(), 0.0001);
	}

	@Test
	public void testPdfCidPathAppliesLeadingXAdvance() throws Exception {
		// PDF CID経路(TJ配列)は先頭グリフのxadvance[0]も出力する。
		// TJ値は -xadvance × 1000 / fontSize = -5×1000/10 = -500
		final TextImpl text = text(3, 0);
		text.addXAdvance(0, 5);
		final String tj = drawCid(text);
		assertTrue(tj.contains("-500"), "先頭調整-500がTJに現れる: " + tj);
		// 先頭グリフのバイト列(gid=1)より前に調整が出る
		final int adjustmentAt = tj.indexOf("-500");
		final int glyphsAt = tj.indexOf("0001");
		assertTrue(glyphsAt >= 0, "gid=1の16bitバイト列: " + tj);
		assertTrue(adjustmentAt < glyphsAt, "調整はグリフより前: " + tj);
	}

	@Test
	public void testPdfCidPathFoldsKerningIntoTJ() throws Exception {
		// カーニングはTJ調整へ畳まれる: xadvance -= kerning(2) → TJ値は
		// -(-2)×1000/10 = +200(正=横書きでペンを戻す)
		final TextImpl text = text(2, 2);
		final String tj = drawCid(text);
		assertTrue(tj.contains("200"), "カーニング+200がTJに現れる: " + tj);
	}
}
