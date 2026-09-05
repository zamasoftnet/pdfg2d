package net.zamasoft.pdfg2d.pdf.font.cid.embedded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** Glyph-ledger regressions below the PDF object writer. */
public class OpenTypeEmbeddedCIDFontSubsetTest {
	private static final File FONT = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");

	private record RenderedFont(String pdf, int ordinaryCid, int aliasCid, int verticalDashCid,
			int boxDrawingCid) {
	}

	private record Pair(OpenTypeEmbeddedCIDFont horizontal, OpenTypeEmbeddedCIDFont vertical,
			OpenTypeEmbeddedCIDFontSubset subset) {
	}

	private static Pair pair() throws Exception {
		return pair(FONT);
	}

	private static Pair pair(final File font) throws Exception {
		final var horizontalSource = new OpenTypeEmbeddedCIDFontSource(font, 0, Direction.LTR);
		final var verticalSource = new OpenTypeEmbeddedCIDFontSource(font, 0, Direction.TB);
		final var subset = horizontalSource.createSubset();
		final var horizontal = (OpenTypeEmbeddedCIDFont) horizontalSource.createFont("H", null, subset);
		final var vertical = (OpenTypeEmbeddedCIDFont) verticalSource.createFont("V", null, subset);
		return new Pair(horizontal, vertical, subset);
	}

	private static void drawCids(final PDFPageOutput page, final OpenTypeEmbeddedCIDFont font,
			final int... cids) throws Exception {
		page.useResource("Font", font.getName());
		page.writeOperator("BT");
		page.writeName(font.getName());
		page.writeInt(12);
		page.writeOperator("Tf");
		page.writeBytes16(cids, 0, cids.length);
		page.writeOperator("Tj");
		page.writeOperator("ET");
	}

	private static RenderedFont renderAliases() throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var params = PDFParams.createDefault()
				.withCompression(PDFParams.Compression.NONE)
				.withFileId(new byte[16]);
		final var pdf = new PDFWriterImpl(builder, params);
		final var page = pdf.nextPage(100, 100);
		final var horizontalSource = new OpenTypeEmbeddedCIDFontSource(FONT, 0, Direction.LTR);
		final var verticalSource = new OpenTypeEmbeddedCIDFontSource(FONT, 0, Direction.TB);
		final var horizontal = (OpenTypeEmbeddedCIDFont) pdf.useFont(horizontalSource);
		final var vertical = (OpenTypeEmbeddedCIDFont) pdf.useFont(verticalSource);

		final int ordinaryCid = horizontal.toGID(')', ')', FontFeatureSet.EMPTY);
		final int aliasCid = horizontal.toGID(')', '(', FontFeatureSet.EMPTY);
		final int verticalDashCid = vertical.toGID(0x2014);
		final int boxDrawingCid = vertical.toGID(0x2500);
		drawCids(page, horizontal, ordinaryCid, aliasCid);
		drawCids(page, vertical, verticalDashCid, boxDrawingCid);

		page.close();
		pdf.close();
		builder.close();
		return new RenderedFont(bytes.toString(StandardCharsets.ISO_8859_1), ordinaryCid, aliasCid,
				verticalDashCid, boxDrawingCid);
	}

	private static String objectBody(final String pdf, final int objectNumber) {
		final var matcher = Pattern.compile("(?s)\\b" + objectNumber + "\\s+0\\s+obj\\b(.*?)endobj")
				.matcher(pdf);
		assertTrue(matcher.find(), "missing object " + objectNumber);
		return matcher.group(1);
	}

	private static String toUnicodeCMap(final String pdf, final String encoding) {
		final var font = Pattern.compile("(?s)\\b\\d+\\s+0\\s+obj\\s*<<(?:(?!endobj).)*?"
				+ "/Encoding\\s*/" + Pattern.quote(encoding)
				+ "\\b(?:(?!endobj).)*?/ToUnicode\\s+(\\d+)\\s+0\\s+R")
				.matcher(pdf);
		assertTrue(font.find(), "missing Type0 font with /Encoding /" + encoding);
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

	private static int codePoint(final String hex) {
		final byte[] bytes = java.util.HexFormat.of().parseHex(hex);
		final String value = new String(bytes, StandardCharsets.UTF_16BE);
		return value.codePointAt(0);
	}

	/** Parses both legal ToUnicode forms so sparse-CID output is not assumed contiguous. */
	private static Map<Integer, Integer> parseToUnicode(final String cmap) {
		final var mappings = new HashMap<Integer, Integer>();
		final var bfchar = Pattern.compile("(?s)\\d+\\s+beginbfchar(.*?)endbfchar").matcher(cmap);
		while (bfchar.find()) {
			final var row = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>")
					.matcher(bfchar.group(1));
			while (row.find()) {
				mappings.put(Integer.parseInt(row.group(1), 16), codePoint(row.group(2)));
			}
		}

		final var bfrange = Pattern.compile("(?s)\\d+\\s+beginbfrange(.*?)endbfrange").matcher(cmap);
		while (bfrange.find()) {
			final var row = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*"
					+ "(?:<([0-9A-Fa-f]+)>|\\[((?:\\s*<[0-9A-Fa-f]+>)+)\\s*\\])")
					.matcher(bfrange.group(1));
			while (row.find()) {
				final int first = Integer.parseInt(row.group(1), 16);
				final int last = Integer.parseInt(row.group(2), 16);
				if (row.group(3) != null) {
					final int firstCodePoint = codePoint(row.group(3));
					for (int cid = first; cid <= last; ++cid) {
						mappings.put(cid, firstCodePoint + cid - first);
					}
				} else {
					final var value = Pattern.compile("<([0-9A-Fa-f]+)>").matcher(row.group(4));
					for (int cid = first; cid <= last; ++cid) {
						assertTrue(value.find(), "short bfrange array for CID " + cid);
						mappings.put(cid, codePoint(value.group(1)));
					}
				}
			}
		}
		return mappings;
	}

	private static int count(final String value, final String needle) {
		int count = 0;
		for (int at = 0; (at = value.indexOf(needle, at)) >= 0; at += needle.length()) {
			++count;
		}
		return count;
	}

	private static int[] directionMappings(final boolean verticalFirst) throws Exception {
		final var pair = pair();
		final int sourceGid = ((OpenTypeEmbeddedCIDFontSource) pair.horizontal.getFontSource())
				.getCmapFormat().mapCharCode('A');
		final int horizontalCid;
		final int verticalCid;
		if (verticalFirst) {
			verticalCid = pair.vertical.addGID('B', sourceGid);
			horizontalCid = pair.horizontal.addGID('A', sourceGid);
		} else {
			horizontalCid = pair.horizontal.addGID('A', sourceGid);
			verticalCid = pair.vertical.addGID('B', sourceGid);
		}
		return new int[] { horizontalCid, verticalCid, pair.horizontal.toChar(horizontalCid),
				pair.vertical.toChar(verticalCid) };
	}

	@Test
	public void sharedOutlineKeepsDirectionLocalUnicodeInEitherOrder() throws Exception {
		assertEquals(java.util.List.of(1, 1, (int) 'A', (int) 'B'),
				java.util.Arrays.stream(directionMappings(false)).boxed().toList());
		assertEquals(java.util.List.of(1, 1, (int) 'A', (int) 'B'),
				java.util.Arrays.stream(directionMappings(true)).boxed().toList());
	}

	@Test
	public void verticalAlternateGetsASeparateCidAndLeavesHorizontalGapUnmapped() throws Exception {
		final var pair = pair();
		final int sourceGid = ((OpenTypeEmbeddedCIDFontSource) pair.horizontal.getFontSource())
				.getCmapFormat().mapCharCode(0x3001);
		final int horizontalCid = pair.horizontal.addGID(0x3001, sourceGid);
		final int verticalCid = pair.vertical.addGID(0x3001, sourceGid);
		assertNotEquals(horizontalCid, verticalCid, "vrt2/vert outline must remain a distinct CID");
		assertNotEquals(pair.subset.sourceGid(horizontalCid), pair.subset.sourceGid(verticalCid));
		assertEquals(-1, pair.horizontal.toChar(verticalCid),
				"the other direction's CID must not receive an inferred Unicode mapping");
	}

	@Test
	public void manualVerticalRotationIsPartOfThePhysicalGlyphIdentity() throws Exception {
		final var pair = pair();
		final int sourceGid = ((OpenTypeEmbeddedCIDFontSource) pair.horizontal.getFontSource())
				.getCmapFormat().mapCharCode('A');
		final int horizontalCid = pair.horizontal.addGID(0xFF0D, sourceGid);
		final int verticalCid = pair.vertical.addGID(0xFF0D, sourceGid);
		assertNotEquals(horizontalCid, verticalCid);
		assertEquals(0, pair.subset.shapeFlags(horizontalCid));
		assertTrue(pair.subset.shapeFlags(verticalCid) != 0);
		final var horizontalBounds = pair.horizontal.getShape(horizontalCid).getBounds2D();
		final var verticalBounds = pair.vertical.getShape(verticalCid).getBounds2D();
		assertEquals(horizontalBounds.getWidth(), verticalBounds.getHeight(), 1.0);
		assertEquals(horizontalBounds.getHeight(), verticalBounds.getWidth(), 1.0);
	}

	@Test
	public void verticalBoxDrawingHorizontalClosesBothGaps() throws Exception {
		// IPAexはU+2500の縦字形(vert)を持ち、字面が1emいっぱいなので詰め量は0が正しい。
		// 検証するのは「送り幅+詰め量=字面の高さ」(隙間が無い)であって符号ではない。
		final var pair = pair();
		final int cid = pair.vertical.toGID(0x2500);
		final int[] cids = { cid, pair.vertical.toGID(0x2500), pair.vertical.toGID(0x2500) };
		assertArrayEquals(new int[] { cid, cid, cid }, cids);
		final var bounds = pair.vertical.getShape(cid).getBounds2D();
		assertTrue(bounds.getHeight() > bounds.getWidth(), "U+2500 must use a vertical outline");
		for (int i = 1; i < cids.length; ++i) {
			// PDFのTJ配列では詰め量が負値(または0)になり、送り幅から字面間の空きだけを除く。
			final int adjustment = -pair.vertical.getKerning(cids[i - 1], cids[i]);
			assertTrue(adjustment <= 0, "consecutive U+2500 glyphs must not be spread apart");
			assertEquals(bounds.getHeight(), pair.vertical.getAdvance(cids[i - 1]) + adjustment, 1.0);
		}
		assertEquals(0x2500, pair.vertical.toChar(cid));
	}

	@Test
	public void verticalBoxDrawingHorizontalFallsBackToVerticalRule() throws Exception {
		// IPAexのサブセットからU+2500のvertだけを外した試験用フォント(fontToolsで生成、
		// src/test/resources)。縦字形が無いのでU+2502の横組み字形(縦線)へ代用する。
		// ToUnicodeと意味変種は元のU+2500を保つ。
		final var pair = pair(new File("src/test/resources/ipaexm-novert2500.ttf"));
		final int cid = pair.vertical.toGID(0x2500);
		final var bounds = pair.vertical.getShape(cid).getBounds2D();
		assertTrue(bounds.getHeight() > bounds.getWidth() * 4, "U+2500 must fall back to a vertical rule outline");
		final int adjustment = -pair.vertical.getKerning(cid, cid);
		assertTrue(adjustment <= 0, "consecutive U+2500 glyphs must not be spread apart");
		assertEquals(bounds.getHeight(), pair.vertical.getAdvance(cid) + adjustment, 1.0);
		assertEquals(0x2500, pair.vertical.toChar(cid));
		assertEquals(0x2500, pair.subset.signature()[cid * 3 + 2],
				"the vertical box-drawing fallback must keep a distinct semantic variant");
	}

	@Test
	public void displayGlyphCanHaveDistinctLogicalUnicodeAliases() throws Exception {
		final var pair = pair();
		final int displayCid = pair.horizontal.toGID(')', ')', FontFeatureSet.EMPTY);
		final int logicalAliasCid = pair.horizontal.toGID(')', '(', FontFeatureSet.EMPTY);
		final int verticalDashCid = pair.vertical.toGID(0x2014);
		assertNotEquals(displayCid, logicalAliasCid);
		assertEquals(pair.subset.sourceGid(displayCid), pair.subset.sourceGid(logicalAliasCid),
				"both CIDs must draw the same source outline");
		assertEquals((int) ')', pair.horizontal.toChar(displayCid));
		assertEquals((int) '(', pair.horizontal.toChar(logicalAliasCid));
		assertEquals(logicalAliasCid, pair.horizontal.toGID(')', '(', FontFeatureSet.EMPTY),
				"the semantic alias must be stable");
		final int[] signature = pair.subset.signature();
		assertEquals((int) '(', signature[logicalAliasCid * 3 + 2]);
		assertEquals(0x2014, signature[verticalDashCid * 3 + 2],
				"the vertical em-dash fallback and mirrored punctuation must coexist in one ledger");
	}

	@Test
	public void realPdfMapsSemanticAliasesAndKeepsTheSharedVerticalVariant() throws Exception {
		final var rendered = renderAliases();
		final var horizontal = parseToUnicode(toUnicodeCMap(rendered.pdf, "Identity-H"));
		final var vertical = parseToUnicode(toUnicodeCMap(rendered.pdf, "Identity-V"));
		assertEquals((int) ')', horizontal.get(rendered.ordinaryCid).intValue());
		assertEquals((int) '(', horizontal.get(rendered.aliasCid).intValue());
		assertEquals(0x2014, vertical.get(rendered.verticalDashCid).intValue());
		assertEquals(0x2500, vertical.get(rendered.boxDrawingCid).intValue());
		assertNotEquals(rendered.ordinaryCid, rendered.aliasCid);
		assertEquals(1, count(rendered.pdf, "/FontFile3"),
				"horizontal aliases and the vertical semantic variant must share one physical subset");
		assertEquals(1, count(rendered.pdf, "/Subtype /CIDFontType0")
				- count(rendered.pdf, "/Subtype /CIDFontType0C"));
		assertTrue(rendered.pdf.contains(String.format("<%04X%04X> Tj", rendered.ordinaryCid, rendered.aliasCid)),
				"both uses of the display glyph must be present in the page content stream");
	}

	@Test
	public void legacyApisKeepTheOriginalCidSequence() throws Exception {
		final var pair = pair();
		assertArrayEquals(new int[] { 1, 2, 3 }, new int[] {
				pair.horizontal.toGID('A'),
				pair.horizontal.toGID(')', FontFeatureSet.EMPTY),
				pair.vertical.toGID(0x2014) },
				"a subset that never calls the three-argument API must retain legacy numbering");
	}
}
