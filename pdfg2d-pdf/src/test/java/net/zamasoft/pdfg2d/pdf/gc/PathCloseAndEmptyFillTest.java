package net.zamasoft.pdfg2d.pdf.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.TaggedParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Two defects of {@link PDFGC} found by the 2026-10-04 review.
 *
 * <ul>
 * <li>Filling an empty rectangle returned after the artifact mark was opened, so
 * a tagged page kept an unclosed {@code /Artifact BMC}.</li>
 * <li>After {@code h} the current point is the start of the subpath, but the
 * path plotter kept the last point: a line straight after a close back to that
 * point was dropped, and a quadratic segment was converted from the wrong
 * start.</li>
 * </ul>
 */
public class PathCloseAndEmptyFillTest {

	private static String content(final PDFParams params, final Consumer<GC> body) throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(buff);
		final var pdf = new PDFWriterImpl(builder, params);
		final var page = pdf.nextPage(200, 200);
		try (final var gc = new PDFGC(page)) {
			gc.setFillPaint(RGBColor.BLACK);
			body.accept(gc);
		}
		pdf.close();
		builder.close();
		final var raw = new String(buff.toByteArray(), StandardCharsets.ISO_8859_1);
		final var sb = new StringBuilder();
		for (int at = 0;;) {
			final int begin = raw.indexOf("stream", at);
			if (begin < 0) {
				break;
			}
			final int end = raw.indexOf("endstream", begin);
			sb.append(raw, begin + "stream".length(), end);
			at = end + "endstream".length();
		}
		return sb.toString();
	}

	/** Counts the needle as a token delimited by white space (the ICC profile's hex dump also contains "BDC"). */
	private static int count(final String haystack, final String needle) {
		int n = 0;
		for (final String token : haystack.split("\\s+")) {
			if (token.equals(needle)) {
				++n;
			}
		}
		return n;
	}

	private static PDFParams uncompressed() {
		return PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);
	}

	@Test
	public void testEmptyRectangleLeavesTheMarksBalanced() throws Exception {
		final var params = uncompressed().withTagged(new TaggedParams("ja", false));
		final var content = content(params, gc -> {
			gc.fill(new Rectangle2D.Double(10, 10, 0, 20));
			gc.fill(new Rectangle2D.Double(10, 40, 50, 20));
		});
		assertEquals(count(content, "BMC") + count(content, "BDC"), count(content, "EMC"), content);
		assertEquals(1, count(content, "re"), content);
	}

	@Test
	public void testLineAfterCloseStartsFromTheSubpathStart() throws Exception {
		final var path = new Path2D.Double();
		path.moveTo(0, 0);
		path.lineTo(10, 0);
		path.lineTo(10, 10);
		path.closePath();
		// After the close the current point is (0, 0), so this line must be drawn
		path.lineTo(10, 10);
		final var content = content(uncompressed(), gc -> gc.draw(path));
		assertEquals(2, content.split("10 190 l", -1).length - 1, content);
		assertTrue(content.contains(" h"), content);
	}

	@Test
	public void testQuadAfterCloseIsConvertedFromTheSubpathStart() throws Exception {
		final var path = new Path2D.Double();
		path.moveTo(0, 0);
		path.lineTo(30, 0);
		path.closePath();
		// From (0, 0) through (0, 30) to (30, 30): the cubic control points are (0, 20) and (10, 30);
		// the page is 200 high and PDF counts y from the bottom
		path.quadTo(0, 30, 30, 30);
		final var content = content(uncompressed(), gc -> gc.draw(path));
		assertTrue(content.contains("0 180 10 170 30 170 c"), content);
	}
}
