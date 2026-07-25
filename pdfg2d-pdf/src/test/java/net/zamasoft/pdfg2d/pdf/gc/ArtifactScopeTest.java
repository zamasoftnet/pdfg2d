package net.zamasoft.pdfg2d.pdf.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.NoOpGC;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.TaggedParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Tests for {@link GC#beginArtifactScope()}.
 *
 * <p>
 * The scope exists so that a caller can emit an already-drawn object again as
 * pure decoration (for example the continuation slice of a visually split
 * box): the marks are visually identical but must not enter the logical
 * structure a second time.
 * </p>
 *
 * <p>
 * Two properties are fixed here. First, untagged output must be unaffected
 * byte for byte, because the vast majority of documents are untagged and the
 * scope must never cost them anything. Second, on tagged output the scope must
 * open exactly one {@code /Artifact} sequence and swallow the marks that the
 * enclosed drawing operations would otherwise open themselves.
 * </p>
 */
public class ArtifactScopeTest {

	/** Draws two filled rectangles, optionally wrapped in an artifact scope. */
	private static byte[] render(final PDFParams params, final boolean artifact) throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(buff);
		final var pdf = new PDFWriterImpl(builder, params);
		final var page = pdf.nextPage(200, 200);
		try (final var gc = new PDFGC(page)) {
			gc.setFillPaint(RGBColor.BLACK);
			final Consumer<GC> body = target -> {
				target.fill(new Rectangle2D.Double(10, 10, 50, 20));
				target.fill(new Rectangle2D.Double(10, 40, 50, 20));
			};
			if (artifact) {
				try (final GC.State scope = gc.beginArtifactScope()) {
					body.accept(gc);
				}
			} else {
				body.accept(gc);
			}
		}
		pdf.close();
		builder.close();
		return buff.toByteArray();
	}

	/** Extracts the concatenation of every uncompressed stream body. */
	private static String streams(final byte[] pdf) {
		final var raw = new String(pdf, StandardCharsets.ISO_8859_1);
		final var sb = new StringBuilder();
		int at = 0;
		while (true) {
			final int begin = raw.indexOf("stream", at);
			if (begin < 0) {
				break;
			}
			final int end = raw.indexOf("endstream", begin);
			if (end < 0) {
				break;
			}
			sb.append(raw, begin + "stream".length(), end);
			at = end + "endstream".length();
		}
		return sb.toString();
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

	private static PDFParams uncompressed() {
		return PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);
	}

	/**
	 * Untagged documents must be byte-identical with and without the scope:
	 * the whole point of the default {@code beginArtifact()} returning
	 * {@code false} is that nothing is written.
	 */
	@Test
	public void testUntaggedOutputIsUnchangedByArtifactScope() throws Exception {
		final var plain = streams(render(uncompressed(), false));
		final var scoped = streams(render(uncompressed(), true));
		assertEquals(plain, scoped, "an artifact scope must not alter untagged content streams");
		assertTrue(plain.contains("re"), "the fixture must actually emit rectangles: " + plain);
		assertEquals(0, count(plain, "BMC"), "untagged output carries no marked content");
	}

	/**
	 * On a tagged page each {@code fill} opens its own artifact sequence.
	 * Inside a scope they must be swallowed, leaving exactly one sequence for
	 * the whole scope.
	 */
	@Test
	public void testTaggedScopeOpensExactlyOneArtifactSequence() throws Exception {
		final var params = uncompressed().withTagged(new TaggedParams("ja", false));

		final var plain = streams(render(params, false));
		assertEquals(2, count(plain, "/Artifact BMC"),
				"without a scope each fill marks itself as an artifact: " + plain);
		assertEquals(2, count(plain, "EMC"), plain);

		final var scoped = streams(render(params, true));
		assertEquals(1, count(scoped, "/Artifact BMC"),
				"the scope must open one sequence and suppress the nested ones: " + scoped);
		assertEquals(1, count(scoped, "EMC"), scoped);

		// Both rectangles are still drawn, and both are inside the sequence.
		assertEquals(2, count(scoped, " re"), scoped);
		final int begin = scoped.indexOf("/Artifact BMC");
		final int end = scoped.indexOf("EMC");
		assertTrue(begin >= 0 && end > begin, scoped);
		assertEquals(2, count(scoped.substring(begin, end), " re"),
				"every enclosed operation belongs to the artifact: " + scoped);

		assertNotEquals(plain, scoped, "the tagged marking must actually differ");
	}

	/** Closing the returned handle twice is harmless (mirrors {@link GC#begin()}). */
	@Test
	public void testCloseIsIdempotent() throws Exception {
		final var params = uncompressed().withTagged(new TaggedParams("ja", false));
		final var buff = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(buff);
		final var pdf = new PDFWriterImpl(builder, params);
		final var page = pdf.nextPage(200, 200);
		try (final var gc = new PDFGC(page)) {
			gc.setFillPaint(RGBColor.BLACK);
			final GC.State scope = gc.beginArtifactScope();
			gc.fill(new Rectangle2D.Double(10, 10, 50, 20));
			scope.close();
			scope.close();
			// The mark was released, so the next fill opens its own sequence.
			gc.fill(new Rectangle2D.Double(10, 40, 50, 20));
		}
		pdf.close();
		builder.close();
		final var content = streams(buff.toByteArray());
		assertEquals(2, count(content, "/Artifact BMC"), content);
		assertEquals(2, count(content, "EMC"), content);
	}

	/** Back-ends without a logical structure share the no-op handle. */
	@Test
	public void testDefaultImplementationIsANoOp() throws Exception {
		final GC gc = new NoOpGC(null);
		assertSame(GC.NO_OP_STATE, gc.beginArtifactScope());
		gc.beginArtifactScope().close();
	}
}
