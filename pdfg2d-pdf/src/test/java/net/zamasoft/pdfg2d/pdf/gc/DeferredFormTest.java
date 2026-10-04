package net.zamasoft.pdfg2d.pdf.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.TaggedParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Tests for {@link PDFGC#drawDeferredForm}: a page draws a Form XObject at once
 * and the writer paints its content when it closes (one-pass page numbers).
 */
public class DeferredFormTest {

	private static String render(final PDFParams params, final DeferredFormPainter painter) throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(buff);
		final var pdf = new PDFWriterImpl(builder, params);
		final var page = pdf.nextPage(200, 200);
		try (final var gc = new PDFGC(page)) {
			gc.setFillPaint(RGBColor.BLACK);
			gc.drawDeferredForm(30, 10, painter);
		}
		pdf.close();
		builder.close();
		return new String(buff.toByteArray(), StandardCharsets.ISO_8859_1);
	}

	private static PDFParams uncompressed() {
		return PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);
	}

	/** The painter runs at close, and the page draws the reserved form at the rectangle's bottom-left. */
	@Test
	public void testFormIsPaintedAtClose() throws Exception {
		final boolean[] painted = { false };
		final var pdf = render(uncompressed(), gc -> {
			painted[0] = true;
			gc.fill(new Rectangle2D.Double(0, 0, 30, 10));
			return null;
		});
		assertTrue(painted[0]);
		assertTrue(pdf.contains("/Subtype /Form"), pdf);
		assertFalse(pdf.contains("/Group"), "a deferred form has no transparency group");
		assertTrue(Pattern.compile("1 0 0 1 0 190 cm\\s+/T0 Do").matcher(pdf).find(), pdf);
		assertTrue(Pattern.compile("/BBox \\[\\s*0 0 30 10\\s*\\]").matcher(pdf).find(), pdf);
	}

	/** Content left of the rectangle (an overflowing page number) widens the bounding box. */
	@Test
	public void testBoundingBoxFollowsPaintedBounds() throws Exception {
		final var pdf = render(uncompressed(), gc -> {
			gc.fill(new Rectangle2D.Double(-12, 0, 42, 10));
			return new Rectangle2D.Double(-12, -2, 42, 14);
		});
		assertTrue(Pattern.compile("/BBox \\[\\s*-12 -2 30 12\\s*\\]").matcher(pdf).find(), pdf);
	}

	/** PDF 1.3 cannot have transparency groups; a deferred form does not need one. */
	@Test
	public void testPdf13() throws Exception {
		final var pdf = render(uncompressed().withVersion(PDFParams.Version.V_1_3), gc -> {
			gc.fill(new Rectangle2D.Double(0, 0, 30, 10));
			return null;
		});
		assertTrue(pdf.contains("/T0 Do"), pdf);
	}

	/**
	 * Tagged output: the form is text, so the page tags it like text (no Figure,
	 * no alternate description), and nothing inside the form is marked.
	 */
	@Test
	public void testTaggedLikeText() throws Exception {
		final var params = uncompressed().withTagged(new TaggedParams("ja", 0));
		final var pdf = render(params, gc -> {
			gc.fill(new Rectangle2D.Double(0, 0, 30, 10));
			return null;
		});
		assertTrue(Pattern.compile("/P\\s*<<\\s*/MCID 0\\s*>>\\s*BDC[\\s\\S]*?/T0 Do[\\s\\S]*?EMC").matcher(pdf).find(), pdf);
		assertFalse(pdf.contains("/Figure"), pdf);
		assertFalse(pdf.contains("/Alt"), pdf);
		final int form = pdf.indexOf("/Subtype /Form");
		final int stream = pdf.indexOf("stream", form);
		final int end = pdf.indexOf("endstream", stream);
		final String content = pdf.substring(stream, end);
		assertEquals(-1, content.indexOf("BDC"), "nothing is marked inside the form: " + content);
		assertEquals(-1, content.indexOf("BMC"), "nothing is marked inside the form: " + content);
	}
}
