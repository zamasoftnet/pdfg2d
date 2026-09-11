package net.zamasoft.pdfg2d.pdf.font.cid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.OpenTypeCIDIdentityFontSource;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** End-to-end Type0/descendant metric checks in both CID spaces. */
class OpenTypeVerticalMetricsPDFTest {
	private record Triple(int advance, double x, int y) { }
	private record Rendered(String pdf, Map<Character, Integer> cids) { }

	private static Rendered render(final boolean embedded, final File file, final String chars) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder,
				PDFParams.createDefault().withCompression(PDFParams.Compression.NONE));
		final var cids = new HashMap<Character, Integer>();
		try (final var page = pdf.nextPage(150, 200)) {
			final CIDFontSource source = embedded
					? new OpenTypeEmbeddedCIDFontSource(file, 0, FontStyle.Direction.TB)
					: new OpenTypeCIDIdentityFontSource(file, 0, FontStyle.Direction.TB);
			final var style = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL,
					FontStyle.Weight.W_400, FontStyle.Direction.TB, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
			final var metrics = new FontMetricsImpl(pdf, source, style);
			final var font = (PDFFont) metrics.getFont();
			final var text = new TextImpl(0, style, metrics);
			for (final char c : chars.toCharArray()) {
				final int cid = font.toGID(c);
				cids.put(c, cid);
				text.appendGlyph(new char[] { c }, 0, (byte) 1, cid);
			}
			text.pack();
			page.useResource("Font", font.getName());
			font.drawTo(new PDFGC(page), text);
		}
		pdf.close();
		builder.close();
		return new Rendered(bytes.toString(StandardCharsets.ISO_8859_1), cids);
	}

	private static Map<Integer, Triple> explicitMetrics(final String pdf) {
		final int start = pdf.indexOf("/W2 ");
		assertTrue(start >= 0, "missing W2");
		final String data = pdf.substring(start + 4, pdf.indexOf(">>", start))
				.replace("[", " [ ").replace("]", " ] ");
		final var result = new HashMap<Integer, Triple>();
		try (final var scanner = new Scanner(data).useLocale(java.util.Locale.ROOT)) {
			assertEquals("[", scanner.next());
			while (!scanner.hasNext("\\]")) {
				final int first = scanner.nextInt();
				if (scanner.hasNext("\\[")) {
					scanner.next();
					int cid = first;
					while (!scanner.hasNext("\\]")) {
						result.put(cid++, new Triple(scanner.nextInt(), scanner.nextDouble(), scanner.nextInt()));
					}
					scanner.next();
				} else {
					final int last = scanner.nextInt();
					final var triple = new Triple(scanner.nextInt(), scanner.nextDouble(), scanner.nextInt());
					for (int cid = first; cid <= last; ++cid) {
						result.put(cid, triple);
					}
				}
			}
		}
		return result;
	}

	private static void assertDefault(final String pdf, final int origin) {
		assertTrue(Pattern.compile("/DW2\\s*\\[\\s*" + origin + "\\s+-1000\\s*\\]")
				.matcher(pdf).find(), "unexpected DW2");
	}

	@Test
	void proportionalVerticalMetricsReachEmbeddedAndIdentityPDFs() throws Exception {
		for (final boolean embedded : new boolean[] { true, false }) {
			final var rendered = render(embedded,
					new File("../pdfg2d-core/src/test/resources/pgothic-vert-subset.ttf"), "「A組乙");
			assertDefault(rendered.pdf, 880);
			final var metrics = explicitMetrics(rendered.pdf);
			assertEquals(new Triple(-570, 500, 450), metrics.get(rendered.cids.get('「')));
			assertEquals(new Triple(-1000, 318.5, 829), metrics.get(rendered.cids.get('A')));
			assertFalse(metrics.containsKey(rendered.cids.get('組')));
			assertFalse(metrics.containsKey(rendered.cids.get('乙')));
			assertEquals(2, metrics.size());
		}
	}

	@Test
	void ipaexDefaultOmits879AndEmitsSpaceAndHyphenExceptions() throws Exception {
		for (final boolean embedded : new boolean[] { true, false }) {
			for (final String chars : new String[] { "「組", "「組　", "「組‐", "「組　‐" }) {
				final var rendered = render(embedded, new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf"), chars);
				assertDefault(rendered.pdf, 879);
				final var metrics = explicitMetrics(rendered.pdf);
				assertFalse(metrics.containsKey(rendered.cids.get('「')));
				assertFalse(metrics.containsKey(rendered.cids.get('組')));
				// CID 0 belongs to embedded subsets even when .notdef is not used by text.
				assertFalse(metrics.values().stream().anyMatch(value -> value.y == 879));
				if (chars.indexOf('　') >= 0) {
					assertEquals(new Triple(-1000, 500, 880), metrics.get(rendered.cids.get('　')));
				}
				if (chars.indexOf('‐') >= 0) {
					assertEquals(new Triple(-1000, 500, 859), metrics.get(rendered.cids.get('‐')));
				}
				if (chars.equals("「組")) {
					assertTrue(metrics.isEmpty());
				}
			}
		}
	}
}
