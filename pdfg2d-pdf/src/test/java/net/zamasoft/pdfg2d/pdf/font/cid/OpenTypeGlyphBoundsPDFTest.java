package net.zamasoft.pdfg2d.pdf.font.cid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.GlyphBounds;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.OpenTypeCIDIdentityFontSource;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** Exercise bounds on the actual font instances registered with a PDF writer. */
class OpenTypeGlyphBoundsPDFTest {
	private static final File PGOTHIC = new File("../pdfg2d-core/src/test/resources/pgothic-vert-subset.ttf");

	private static void assertBounds(final GlyphBounds expected, final GlyphBounds actual) {
		assertNotNull(actual);
		assertEquals(expected.minX(), actual.minX(), 0.0001);
		assertEquals(expected.minY(), actual.minY(), 0.0001);
		assertEquals(expected.maxX(), actual.maxX(), 0.0001);
		assertEquals(expected.maxY(), actual.maxY(), 0.0001);
	}

	private static void render(final boolean embedded, final Direction direction) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		try (final var builder = new StreamFragmentedOutput(bytes);
				final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault())) {
			try (final var page = pdf.nextPage(150, 200)) {
				final OpenTypeFontSource source = embedded
						? new OpenTypeEmbeddedCIDFontSource(PGOTHIC, 0, direction)
						: new OpenTypeCIDIdentityFontSource(PGOTHIC, 0, direction);
				final var style = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL,
						FontStyle.Weight.W_400, direction, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
				final var metrics = new FontMetricsImpl(pdf, source, style);
				final var font = (PDFFont) metrics.getFont();
				final var shaped = (ShapedFont) font;
				final var text = new TextImpl(0, style, metrics);
				for (final char c : "「─A".toCharArray()) {
					final int cid = font.toGID(c);
					final var bounds = shaped.getGlyphBounds(cid);
					assertSame(bounds, shaped.getGlyphBounds(cid));
					final var pathBounds = shaped.getShapeByGID(cid).getBounds2D();
					assertBounds(new GlyphBounds(pathBounds.getMinX(), pathBounds.getMinY(),
							pathBounds.getMaxX(), pathBounds.getMaxY()), bounds);
					if (c == '「') {
						final int sourceGid = direction == Direction.TB ? 28 : 15;
						assertEquals(embedded ? 1 : sourceGid, cid);
						assertBounds(direction == Direction.TB ? new GlyphBounds(222, -234, 948, 38)
								: new GlyphBounds(216, -828, 488, -102), bounds);
						if (embedded) {
							assertNotEquals(sourceGid, cid, "the API must accept a subset CID");
						}
					} else if (c == '─' && direction == Direction.TB) {
						// This fixture leaves the box-drawing GID unchanged and records rotation.
						final int sourceGid = source.getCmapFormat().mapCharCode(c);
						final var raw = source.getOpenTypeFont().getGlyph(sourceGid).path();
						final var b = raw.getBounds2D();
						final var rotated = AffineTransform.getRotateInstance(Math.PI / 2,
								b.getCenterX(), b.getCenterY()).createTransformedShape(raw).getBounds2D();
						assertBounds(new GlyphBounds(rotated.getMinX(), rotated.getMinY(),
								rotated.getMaxX(), rotated.getMaxY()), bounds);
						assertTrue(bounds.maxY() - bounds.minY() > bounds.maxX() - bounds.minX());
					}
					text.appendGlyph(new char[] { c }, 0, (byte) 1, cid);
				}
				text.pack();
				page.useResource("Font", font.getName());
				font.drawTo(new PDFGC(page), text);
			}
		}
		assertTrue(bytes.toString(StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
	}

	@Test
	void embeddedBoundsUseSubsetCidsAndRecordedRotation() throws Exception {
		for (final var direction : new Direction[] { Direction.LTR, Direction.TB }) {
			render(true, direction);
		}
	}

	@Test
	void identityBoundsUseSourceGidsAndAdjustedOutlines() throws Exception {
		for (final var direction : new Direction[] { Direction.LTR, Direction.TB }) {
			render(false, direction);
		}
	}

	@Test
	void bothCidFontsReturnNullForRealSpaces() throws Exception {
		final var file = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");
		for (final var source : new OpenTypeFontSource[] {
				new OpenTypeEmbeddedCIDFontSource(file, 0, Direction.TB),
				new OpenTypeCIDIdentityFontSource(file, 0, Direction.TB) }) {
			final var font = (ShapedFont) source.createFont();
			final int gid = font.toGID(' ');
			assertNotEquals(0, gid);
			assertNull(font.getGlyphBounds(gid));
			assertNull(font.getGlyphBounds(gid));
		}
	}
}
