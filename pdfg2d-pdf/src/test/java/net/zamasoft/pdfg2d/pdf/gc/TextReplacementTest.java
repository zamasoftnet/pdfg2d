package net.zamasoft.pdfg2d.pdf.gc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.NoOpGC;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList.FontPolicy;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.pdf.PDFMetaInfo;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.TaggedParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** Byte-level regressions for logical text replacement scopes. */
public class TextReplacementTest {
	private static final String LOGICAL = "\u05d0\u05d1\u05d2 ABC";
	private static final File FONT = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");
	private static final FontPolicyList EMBEDDED = new FontPolicyList(new FontPolicy[] { FontPolicy.EMBEDDED });

	@FunctionalInterface
	private interface Drawing {
		void draw(PDFGC gc, PDFPageOutput page) throws Exception;
	}

	private static byte[] render(final PDFParams params, final Drawing drawing) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder, params);
		final var page = pdf.nextPage(200, 200);
		try (final var gc = new PDFGC(page)) {
			drawing.draw(gc, page);
		}
		pdf.close();
		builder.close();
		return bytes.toByteArray();
	}

	/** ヘッダ 2 行目(% + 乱数 4 バイト)を 0 に潰した複製。 */
	private static byte[] withoutBinaryComment(final byte[] pdf) {
		final byte[] copy = pdf.clone();
		int i = 0;
		while (i < copy.length && copy[i] != (byte) 10) {
			++i;
		}
		if (i + 5 < copy.length && copy[i + 1] == (byte) '%') {
			for (int k = i + 2; k < i + 6; ++k) {
				copy[k] = 0;
			}
		}
		return copy;
	}

	private static PDFParams defaultUncompressed() {
		final var metaInfo = new PDFMetaInfo();
		metaInfo.setCreationDate(0);
		metaInfo.setModDate(0);
		return PDFParams.createDefault().withCompression(PDFParams.Compression.NONE)
				.withFileId(new byte[16]).withMetaInfo(metaInfo);
	}

	private static PDFParams uncompressed() {
		return defaultUncompressed().withActualTextReplacement(true);
	}

	/** Emits a minimal text object without involving font selection. */
	private static void textOperators(final PDFPageOutput page) throws Exception {
		page.writeOperator("BT");
		page.writeString("visual");
		page.writeOperator("Tj");
		page.writeOperator("ET");
	}

	private static String streams(final byte[] pdf) {
		return String.join("\n", streamContents(pdf));
	}

	private static List<String> streamContents(final byte[] pdf) {
		final var raw = new String(pdf, StandardCharsets.ISO_8859_1);
		final var result = new ArrayList<String>();
		int at = 0;
		while (true) {
			final int begin = raw.indexOf("stream", at);
			if (begin < 0) {
				return result;
			}
			final int end = raw.indexOf("endstream", begin);
			if (end < 0) {
				return result;
			}
			result.add(raw.substring(begin + "stream".length(), end));
			at = end + "endstream".length();
		}
	}

	private static boolean delimiter(final char c) {
		return Character.isWhitespace(c) || "()<>[]{}/%".indexOf(c) >= 0;
	}

	/** Tokenizes PDF operators while skipping strings, hex strings, names and comments. */
	private static List<String> operators(final String content) {
		final var tokens = new ArrayList<String>();
		for (int i = 0; i < content.length();) {
			final char c = content.charAt(i);
			if (Character.isWhitespace(c) || ")[]{}>".indexOf(c) >= 0) {
				++i;
			} else if (c == '%') {
				while (i < content.length() && content.charAt(i) != '\r' && content.charAt(i) != '\n') {
					++i;
				}
			} else if (c == '/') {
				for (++i; i < content.length() && !delimiter(content.charAt(i)); ++i) {
					// Skip a PDF name; an operator-like name is still an operand.
				}
			} else if (c == '(') {
				int depth = 1;
				for (++i; i < content.length() && depth > 0; ++i) {
					final char d = content.charAt(i);
					if (d == '\\' && i + 1 < content.length()) {
						++i;
					} else if (d == '(') {
						++depth;
					} else if (d == ')') {
						--depth;
					}
				}
			} else if (c == '<') {
				if (i + 1 < content.length() && content.charAt(i + 1) == '<') {
					i += 2;
				} else {
					final int end = content.indexOf('>', i + 1);
					i = end < 0 ? content.length() : end + 1;
				}
			} else {
				final int begin = i;
				while (i < content.length() && !delimiter(content.charAt(i))) {
					++i;
				}
				tokens.add(content.substring(begin, i));
			}
		}
		return tokens;
	}

	private static void assertProperNesting(final byte[] pdf) {
		int checked = 0;
		for (final var content : streamContents(pdf)) {
			if (!content.contains("/ActualText")) {
				continue;
			}
			++checked;
			final var stack = new ArrayDeque<String>();
			for (final var operator : operators(content)) {
				switch (operator) {
					case "BDC", "BMC" -> stack.push("MC");
					case "EMC" -> {
						assertTrue(!stack.isEmpty(), "EMC without BDC/BMC\n" + content);
						assertEquals("MC", stack.pop(), "marked content must not cross BT/ET\n" + content);
					}
					case "BT" -> {
						assertTrue(!stack.contains("BT"), "nested BT\n" + content);
						stack.push("BT");
					}
					case "ET" -> {
						assertTrue(!stack.isEmpty(), "ET without BT\n" + content);
						assertEquals("BT", stack.pop(), "text object must not cross marked content\n" + content);
					}
					default -> {
						// Other content operators do not affect these two stacks.
					}
				}
			}
			assertTrue(stack.isEmpty(), "unclosed content scope " + stack + "\n" + content);
		}
		assertTrue(checked > 0, "no ActualText content stream found");
	}

	private static TextImpl text(final PDFGC gc, final char c) throws Exception {
		final var source = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.LTR);
		final var style = new FontStyleImpl(FontFamilyList.create(source.getFontName()), 12,
				FontStyle.Style.NORMAL, FontStyle.Weight.W_400, FontStyle.Direction.LTR, EMBEDDED);
		final var metrics = new FontMetricsImpl((PDFWriterImpl) gc.getPdfWriter(), source, style);
		final var font = metrics.getFont();
		final var text = new TextImpl(0, style, metrics);
		text.appendGlyph(new char[] { c }, 0, (byte) 1, font.toGID(c, FontFeatureSet.EMPTY));
		text.pack();
		return text;
	}

	private static void drawAutoTaggedText(final PDFGC gc, final char c, final double y) throws Exception {
		gc.drawText(text(gc, c), 10, y);
	}

	private static int count(final String text, final String needle) {
		int count = 0;
		for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
			++count;
		}
		return count;
	}

	private static int actualTextCount(final String content) {
		return (int) Pattern.compile("/Span\\s*<<\\s*/ActualText\\b")
				.matcher(content).results().count();
	}

	@Test
	public void backendDefaultIsNoOp() throws Exception {
		final GC gc = new NoOpGC(null);
		assertSame(GC.NO_OP_STATE, gc.beginTextReplacement(LOGICAL));
	}

	@Test
	public void replacementIsDisabledByDefaultWithoutChangingOutput() throws Exception {
		final var params = defaultUncompressed();
		final byte[] plain = render(params, (gc, page) -> textOperators(page));
		final byte[] scoped = render(params, (gc, page) -> {
			try (final var replacement = gc.beginTextReplacement(LOGICAL)) {
				assertSame(GC.NO_OP_STATE, replacement);
				textOperators(page);
			}
		});
		// PDFWriterImpl.writeHeader は二進判別コメントの 4 バイトを SecureRandom で埋めるので、そこだけ除いて比べる
		assertArrayEquals(withoutBinaryComment(plain), withoutBinaryComment(scoped));
		final String content = streams(scoped);
		final String plainContent = streams(plain);
		assertEquals(0, count(content, "/ActualText"), content);
		// streams() は XMP 等も連結するので、絶対数ではなく scope 無しと同数であることを見る
		assertEquals(count(plainContent, "BDC"), count(content, "BDC"), content);
		assertEquals(count(plainContent, "EMC"), count(content, "EMC"), content);
	}

	@Test
	public void replacementWrapsTextOperatorsExactlyOnce() throws Exception {
		final String content = streams(render(uncompressed(), (gc, page) -> {
			try (final var replacement = gc.beginTextReplacement(LOGICAL)) {
				textOperators(page);
			}
		}));
		assertEquals(1, actualTextCount(content), content);
		assertEquals(1, count(content, "EMC"), content);
		assertTrue(content.contains("<FEFF05D005D105D20020004100420043>"), content);
		final int begin = content.indexOf("/Span");
		final int bt = content.indexOf("BT", begin);
		final int tj = content.indexOf("Tj", bt);
		final int et = content.indexOf("ET", tj);
		final int end = content.indexOf("EMC", et);
		assertTrue(begin >= 0 && begin < bt && bt < tj && tj < et && et < end, content);
	}

	@Test
	public void nestedReplacementDoesNotDoubleEmit() throws Exception {
		final String content = streams(render(uncompressed(), (gc, page) -> {
			try (final var outer = gc.beginTextReplacement(LOGICAL)) {
				final GC.State nested = gc.beginTextReplacement("duplicate");
				assertSame(GC.NO_OP_STATE, nested);
				try (nested) {
					textOperators(page);
				}
			}
		}));
		assertEquals(1, actualTextCount(content), content);
		assertEquals(1, count(content, "EMC"), content);
	}

	@Test
	public void replacementWrapsAutoTaggedDrawTextWithoutCrossing() throws Exception {
		final var params = uncompressed().withTagged(new TaggedParams("en", false));
		final byte[] rendered = render(params, (gc, page) -> {
			try (final var replacement = gc.beginTextReplacement(LOGICAL)) {
				drawAutoTaggedText(gc, 'A', 30);
			}
		});
		final String content = streams(rendered);
		assertEquals(1, actualTextCount(content), content);
		assertEquals(1, count(content, "/MCID 0"), "PDFGC.drawText must supply the inner automatic tag");
		assertProperNesting(rendered);
	}

	@Test
	public void replacementsInsideAndOutsideLayerRemainProperlyNested() throws Exception {
		final var params = uncompressed().withTagged(new TaggedParams("en", false));
		final byte[] rendered = render(params, (gc, page) -> {
			final var layer = gc.getPdfWriter().createOptionalContentGroup("text", true, true, true, false);
			try (final var replacement = gc.beginTextReplacement("outside")) {
				gc.beginLayer(layer);
				drawAutoTaggedText(gc, 'A', 30);
				gc.endLayer();
			}
			gc.beginLayer(layer);
			try (final var replacement = gc.beginTextReplacement("inside")) {
				drawAutoTaggedText(gc, 'B', 50);
			}
			gc.endLayer();
		});
		final String content = streams(rendered);
		assertEquals(2, actualTextCount(content), "one ActualText span per replacement\n" + content);
		assertEquals(2, count(content, "/OC"), content);
		assertProperNesting(rendered);
	}

	@Test
	public void replacementInsideArtifactRemainsProperlyNested() throws Exception {
		final var params = uncompressed().withTagged(new TaggedParams("en", false));
		final byte[] rendered = render(params, (gc, page) -> {
			try (final var artifact = gc.beginArtifactScope()) {
				try (final var replacement = gc.beginTextReplacement(LOGICAL)) {
					drawAutoTaggedText(gc, 'A', 30);
				}
			}
		});
		final String content = streams(rendered);
		assertEquals(1, actualTextCount(content), content);
		assertEquals(1, count(content, "/Artifact BMC"), content);
		assertProperNesting(rendered);
	}

	@Test
	public void pre15VersionEmitsNoActualTextMarkedContent() throws Exception {
		final var params = uncompressed().withVersion(PDFParams.Version.V_1_4);
		final String content = streams(render(params, (gc, page) -> {
			assertSame(GC.NO_OP_STATE, gc.beginTextReplacement(LOGICAL));
			try (final var replacement = gc.beginTextReplacement(LOGICAL)) {
				textOperators(page);
			}
		}));
		assertEquals(0, count(content, "/ActualText"), content);
		assertEquals(0, count(content, "EMC"), content);
	}

	private static Image replacementGroup(final PDFGC gc) throws Exception {
		final GroupImageGC group = gc.createFilterGroup(72, 72);
		group.setFillPaint(RGBColor.BLACK);
		try (final var replacement = group.beginTextReplacement(LOGICAL)) {
			try (final var nested = group.beginTextReplacement("duplicate")) {
				group.drawText(text(gc, 'A'), 10, 30);
			}
		}
		return group.finish();
	}

	@Test
	public void recorderReplaysReplacementOnceToOpacityOnlyVectorForm() throws Exception {
		final byte[] rendered = render(uncompressed(), (gc, page) -> assertEquals(GC.GroupEffectsResult.VECTOR,
				gc.drawGroupEffects(replacementGroup(gc), new GroupEffects(null, 0, null, .4))));
		final String content = streams(rendered);
		assertEquals(1, actualTextCount(content), content);
		assertEquals(1, count(content, "EMC"), content);
		assertProperNesting(rendered);
	}

	@Test
	public void recorderDropsReplacementDuringRasterReplay() throws Exception {
		final String pdf = new String(render(uncompressed(), (gc, page) ->
				gc.drawGroupEffects(replacementGroup(gc), new GroupEffects(null, 2, null, 1))),
				StandardCharsets.ISO_8859_1);
		assertEquals(0, actualTextCount(pdf), pdf);
	}
}
