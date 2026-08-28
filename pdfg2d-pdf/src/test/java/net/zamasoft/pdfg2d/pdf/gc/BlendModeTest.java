package net.zamasoft.pdfg2d.pdf.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Tests for {@link GC#setBlendMode} on the PDF backend (2026-08-29).
 *
 * <p>
 * The mode travels in the same ExtGState as the alpha values, so two things
 * are fixed: a non-normal mode produces a {@code /BM} entry and a {@code gs}
 * operator, and documents that never touch the mode stay byte-identical (no
 * ExtGState, no {@code gs}).
 * </p>
 */
public class BlendModeTest {

	private static byte[] render(final BlendMode mode, final boolean restore) throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(buff);
		final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault().withCompression(PDFParams.Compression.NONE));
		final var page = pdf.nextPage(200, 200);
		try (final var gc = new PDFGC(page)) {
			gc.setFillPaint(RGBColor.create(1f, 0f, 0f));
			gc.fill(new Rectangle2D.Double(10, 10, 100, 100));
			if (mode != null) {
				try (final GC.State state = gc.begin()) {
					gc.setBlendMode(mode);
					gc.setFillPaint(RGBColor.create(0f, 0f, 1f));
					gc.fill(new Rectangle2D.Double(60, 60, 100, 100));
				}
				if (restore) {
					assertEquals(BlendMode.NORMAL, gc.getBlendMode(), "begin/close must restore the blend mode");
					gc.fill(new Rectangle2D.Double(150, 150, 20, 20));
				}
			}
		}
		pdf.close();
		builder.close();
		return buff.toByteArray();
	}

	@Test
	public void testMultiplyEmitsExtGState() throws Exception {
		final var raw = new String(render(BlendMode.MULTIPLY, true), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/BM /Multiply") || raw.contains("/BM/Multiply"), "ExtGState must carry /BM: " + raw);
		// after the state is closed the next fill must switch back to Normal,
		// which is written as a second ExtGState (same alpha, no /BM)
		assertEquals(1, count(raw, "/BM"), "exactly one ExtGState carries /BM");
		assertEquals(2, count(raw, "/ca 1"), "two alpha ExtGStates expected (Multiply, then back to Normal)");
	}

	@Test
	public void testNormalWritesNoExtGState() throws Exception {
		final var raw = new String(render(null, false), StandardCharsets.ISO_8859_1);
		assertFalse(raw.contains("/BM"), "no /BM when the mode is never set");
		assertFalse(raw.contains("/ca "), "no alpha ExtGState when neither alpha nor blend mode changes");
	}

	@Test
	public void testCssNames() {
		assertEquals(BlendMode.COLOR_DODGE, BlendMode.fromCssName("color-dodge"));
		assertEquals(BlendMode.MULTIPLY, BlendMode.fromCssName("Multiply"));
		assertEquals(null, BlendMode.fromCssName("plus-lighter"));
		assertEquals("SoftLight", BlendMode.SOFT_LIGHT.pdfName);
	}

	private static int count(final String haystack, final String needle) {
		int n = 0;
		int at = 0;
		while (true) {
			final int found = haystack.indexOf(needle, at);
			if (found < 0) {
				return n;
			}
			++n;
			at = found + needle.length();
		}
	}
}
