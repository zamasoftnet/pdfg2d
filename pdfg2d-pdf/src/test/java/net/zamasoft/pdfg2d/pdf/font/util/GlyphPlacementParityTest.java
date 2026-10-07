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
 * Characterization tests for each backend's glyph positioning policy (2026-08-01,
 * updated 2026-08-23).
 *
 * <p>
 * Apply {@code xadvance[i]} immediately before glyph i. Measurement, PDF CID, AWT fallback,
 * outline, and SVG paths share this meaning. Evenly spaced ruby and Japanese punctuation kerning
 * at style-run boundaries use a leading adjustment, so do not ignore {@code [0]} or apply it
 * to the next glyph. {@code FontUtilsLeadingXAdvanceTest} covers the outline path;
 * foliojet's visual golden tests cover the integrated path.
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
		// The PDFGraphicsOutput constructor only uses PDFWriter.getParams(), so use a dynamic proxy
		// to create a minimal stub that returns only the default Params
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
				// Do not record resource references in this test
			}
		}) {
			PDFFontUtils.drawCIDTo(out, text, false);
		}
		return buffer.toString(StandardCharsets.ISO_8859_1);
	}

	@Test
	public void testMeasurementIncludesLeadingXAdvance() {
		// Measurement (getAdvance) includes the first glyph's xadvance in the sum; this is why
		// the leading half-space for ruby contributes to the line width
		final TextImpl text = text(3, 0);
		final double base = text.getAdvance();
		text.addXAdvance(0, 5);
		assertEquals(base + 5, text.getAdvance(), 0.0001);
	}

	@Test
	public void testPdfCidPathAppliesLeadingXAdvance() throws Exception {
		// The PDF CID path (TJ array) also emits xadvance[0] for the first glyph.
		// TJ value = -xadvance × 1000 / fontSize = -5×1000/10 = -500
		final TextImpl text = text(3, 0);
		text.addXAdvance(0, 5);
		final String tj = drawCid(text);
		assertTrue(tj.contains("-500"), "先頭調整-500がTJに現れる: " + tj);
		// The adjustment precedes the first glyph's bytes (gid=1)
		final int adjustmentAt = tj.indexOf("-500");
		final int glyphsAt = tj.indexOf("0001");
		assertTrue(glyphsAt >= 0, "gid=1の16bitバイト列: " + tj);
		assertTrue(adjustmentAt < glyphsAt, "調整はグリフより前: " + tj);
	}

	@Test
	public void testPdfCidPathFoldsKerningIntoTJ() throws Exception {
		// Kerning is folded into the TJ adjustment: xadvance -= kerning(2) → TJ value
		// -(-2)×1000/10 = +200 (positive moves the pen backward in horizontal writing)
		final TextImpl text = text(2, 2);
		final String tj = drawCid(text);
		assertTrue(tj.contains("200"), "カーニング+200がTJに現れる: " + tj);
	}
}
