package net.zamasoft.pdfg2d.pdf.font.cid.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** Semantic-alias behaviour of identity-mapped CID fonts. */
public class OpenTypeCIDIdentityFontTest {
	private static final File FONT = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");

	private record Rendered(String pdf, int displayGid, String toUnicode) {
	}

	private static String objectBody(final String pdf, final int objectNumber) {
		final var matcher = Pattern.compile("(?s)\\b" + objectNumber + "\\s+0\\s+obj\\b(.*?)endobj")
				.matcher(pdf);
		assertTrue(matcher.find(), "missing object " + objectNumber);
		return matcher.group(1);
	}

	private static String toUnicodeCMap(final String pdf) {
		final var font = Pattern.compile("(?s)\\b\\d+\\s+0\\s+obj\\s*<<(?:(?!endobj).)*?"
				+ "/Encoding\\s*/Identity-H\\b(?:(?!endobj).)*?/ToUnicode\\s+(\\d+)\\s+0\\s+R")
				.matcher(pdf);
		assertTrue(font.find(), "missing identity Type0 font");
		final String object = objectBody(pdf, Integer.parseInt(font.group(1)));
		final int marker = object.indexOf("stream");
		assertTrue(marker >= 0, "ToUnicode object has no stream");
		int begin = marker + "stream".length();
		if (begin < object.length() && object.charAt(begin) == '\r') {
			++begin;
		}
		if (begin < object.length() && object.charAt(begin) == '\n') {
			++begin;
		}
		final int end = object.lastIndexOf("endstream");
		assertTrue(end >= begin, "unterminated ToUnicode stream");
		return object.substring(begin, end);
	}

	private static Rendered render(final boolean semanticApi) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var params = PDFParams.createDefault()
				.withCompression(PDFParams.Compression.NONE)
				.withFileId(new byte[16]);
		final var pdf = new PDFWriterImpl(builder, params);
		final var page = pdf.nextPage(100, 100);
		final var source = new OpenTypeCIDIdentityFontSource(FONT, 0, Direction.LTR);
		final var font = (OpenTypeCIDIdentityFont) pdf.useFont(source);
		final int displayGid = semanticApi ? font.toGID(')', '(', FontFeatureSet.EMPTY) : font.toGID(')');
		page.useResource("Font", font.getName());
		page.writeOperator("BT");
		page.writeName(font.getName());
		page.writeInt(12);
		page.writeOperator("Tf");
		page.writeBytes16(displayGid);
		page.writeOperator("Tj");
		page.writeOperator("ET");
		page.close();
		pdf.close();
		builder.close();
		final String result = bytes.toString(StandardCharsets.ISO_8859_1);
		return new Rendered(result, displayGid, toUnicodeCMap(result));
	}

	@Test
	public void semanticAliasReturnsThePlainDisplayGid() throws Exception {
		final var source = new OpenTypeCIDIdentityFontSource(FONT, 0, Direction.LTR);
		final var font = (OpenTypeCIDIdentityFont) source.createFont();
		final int displayGid = font.toGID(')', FontFeatureSet.EMPTY);
		assertEquals(displayGid, font.toGID(')', '(', FontFeatureSet.EMPTY));
	}

	@Test
	public void semanticRequestKeepsToUnicodeAndDrawsTheDisplayGid() throws Exception {
		final var legacy = render(false);
		final var semantic = render(true);
		assertEquals(legacy.displayGid, semantic.displayGid);
		assertEquals(legacy.toUnicode, semantic.toUnicode,
				"identity fonts must retain their display-GID ToUnicode behaviour");
		assertTrue(semantic.toUnicode.contains("<0029>"), "the display character must remain in ToUnicode");
		assertTrue(semantic.pdf.contains(String.format("<%04X> Tj", semantic.displayGid)),
				"the content stream must draw the display GID");
	}
}
