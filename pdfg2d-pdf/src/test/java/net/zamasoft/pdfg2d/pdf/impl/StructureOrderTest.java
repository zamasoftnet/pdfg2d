package net.zamasoft.pdfg2d.pdf.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList.FontPolicy;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.pdf.PDFMetaInfo;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.StructureOrder;
import net.zamasoft.pdfg2d.pdf.StructureRef;
import net.zamasoft.pdfg2d.pdf.annot.LinkAnnot;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.TaggedParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** Object-level regressions for logical structure ordering hints. */
public class StructureOrderTest {
	private static final java.io.File FONT = new java.io.File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");
	private static final FontPolicyList EMBEDDED = new FontPolicyList(new FontPolicy[] { FontPolicy.EMBEDDED });

	private record Rendered(String pdf, int firstMcid, int secondMcid, int thirdMcid) {
	}

	private static PDFParams params(final boolean pdfUa) {
		final var meta = new PDFMetaInfo();
		meta.setTitle("Structure order test");
		meta.setCreationDate(0);
		meta.setModDate(0);
		return PDFParams.createDefault()
				.withCompression(PDFParams.Compression.NONE)
				.withFileId(new byte[16])
				.withMetaInfo(meta)
				.withTagged(pdfUa ? TaggedParams.pdfua("en") : new TaggedParams("en", false));
	}

	private static Rendered render() throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder, params(false));
		final PDFPageOutput page = pdf.nextPage(200, 200);
		final StructureRef paragraph = page.declareStructElement(null, "P", null);
		final StructureRef link = page.declareStructElement(paragraph, "Link", null);

		final int first = mark(page, paragraph, new StructureOrder(7, 20, 0), "paint-first");
		final int second = mark(page, link, new StructureOrder(7, 10, 1), "link");

		final var annotation = new LinkAnnot();
		annotation.setShape(new Rectangle2D.Double(10, 10, 20, 10));
		annotation.setURI(URI.create("https://example.com/"));
		page.beginStructContent(link, new StructureOrder(7, 10, 0));
		page.addAnnotation(annotation);
		page.endStructContent();

		final int third = mark(page, paragraph, new StructureOrder(7, 0, 0), "paint-last");
		page.close();
		pdf.close();
		builder.close();
		return new Rendered(bytes.toString(StandardCharsets.ISO_8859_1), first, second, third);
	}

	private static int mark(final PDFPageOutput page, final StructureRef target,
			final StructureOrder order, final String text) throws Exception {
		page.beginStructContent(target, order);
		final int mcid = page.beginMark("Span", null);
		page.writeOperator("BT");
		page.writeString(text);
		page.writeOperator("Tj");
		page.writeOperator("ET");
		page.endMark();
		page.endStructContent();
		return mcid;
	}

	private static String structureKids(final String pdf, final String role) {
		final var matcher = Pattern.compile("(?s)/Type\\s*/StructElem\\s*/S\\s*/" + role
				+ "\\b(?:(?!endobj).)*?/K\\s*\\[(.*?)\\]").matcher(pdf);
		assertTrue(matcher.find(), "missing /S /" + role + " structure element\n" + pdf);
		return matcher.group(1).replaceAll("\\s+", " ").trim();
	}

	private static String structureRef(final String pdf, final String role) {
		final var matcher = Pattern.compile("(?s)(\\d+)\\s+0\\s+obj\\s*<<"
				+ "(?:(?!endobj).)*?/Type\\s*/StructElem\\b(?:(?!endobj).)*?/S\\s*/" + role + "\\b")
				.matcher(pdf);
		assertTrue(matcher.find(), "missing /S /" + role + " structure element\n" + pdf);
		return matcher.group(1) + " 0 R";
	}

	private static String pageRef(final String pdf, final int structParents) {
		final var matcher = Pattern.compile("(?s)(\\d+)\\s+0\\s+obj\\s*<<"
				+ "(?=(?:(?!endobj).)*/Type\\s*/Page\\b)"
				+ "(?=(?:(?!endobj).)*/StructParents\\s*" + structParents + "\\b)"
				+ "(?:(?!endobj).)*?endobj").matcher(pdf);
		assertTrue(matcher.find(), "missing page with /StructParents " + structParents + "\n" + pdf);
		return matcher.group(1) + " 0 R";
	}

	private static String objectBody(final String pdf, final String ref) {
		final String objectNumber = ref.substring(0, ref.indexOf(' '));
		final var matcher = Pattern.compile("(?s)\\b" + objectNumber
				+ "\\s+0\\s+obj\\s*(.*?)endobj").matcher(pdf);
		assertTrue(matcher.find(), "missing object " + ref + "\n" + pdf);
		return matcher.group(1).replaceAll("\\s+", " ").trim();
	}

	private static void assertKidRoleOrder(final String pdf, final String parentRole, final String... roles) {
		final String kids = structureKids(pdf, parentRole);
		int previous = -1;
		for (final var role : roles) {
			final String ref = structureRef(pdf, role);
			final int at = kids.indexOf(ref);
			assertTrue(at > previous, "/" + parentRole + " /K must order " + List.of(roles) + "; got " + kids);
			previous = at;
		}
	}

	private static byte[] renderNullOrders(final boolean twoArgumentApi) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder, params(false));
		final var page = pdf.nextPage(200, 200);
		final var paragraph = page.declareStructElement(null, "P", null);
		for (final var value : List.of("first", "second")) {
			if (twoArgumentApi) {
				page.beginStructContent(paragraph, null);
			} else {
				page.beginStructContent(paragraph);
			}
			page.beginMark("Span", null);
			textOperators(page, value);
			page.endMark();
			page.endStructContent();
		}
		page.close();
		pdf.close();
		builder.close();
		return bytes.toByteArray();
	}

	/** Removes only the deliberately random four-byte PDF binary marker. */
	private static byte[] comparablePdf(final byte[] pdf) {
		final byte[] copy = pdf.clone();
		int firstLineEnd = 0;
		while (firstLineEnd < copy.length && copy[firstLineEnd] != '\n') {
			++firstLineEnd;
		}
		final int marker = firstLineEnd + 2;
		assertTrue(marker + 4 <= copy.length && copy[firstLineEnd + 1] == '%',
				"missing PDF binary marker");
		Arrays.fill(copy, marker, marker + 4, (byte) 0x80);
		return copy;
	}

	private static void textOperators(final PDFPageOutput page, final String text) throws Exception {
		page.writeOperator("BT");
		page.writeString(text);
		page.writeOperator("Tj");
		page.writeOperator("ET");
	}

	private static String renderRoleOrder(final String[] declarationOrder, final String[] paintOrder,
			final StructureOrder[] orders) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder, params(false));
		final var page = pdf.nextPage(200, 200);
		final var refs = new java.util.LinkedHashMap<String, StructureRef>();
		for (final var role : declarationOrder) {
			refs.put(role, page.declareStructElement(null, role, null));
		}
		for (int i = 0; i < paintOrder.length; ++i) {
			mark(page, refs.get(paintOrder[i]), orders[i], paintOrder[i]);
		}
		page.close();
		pdf.close();
		builder.close();
		return bytes.toString(StandardCharsets.ISO_8859_1);
	}

	private static TextImpl text(final PDFWriterImpl pdf, final OpenTypeEmbeddedCIDFontSource source,
			final char c) throws Exception {
		final var style = new FontStyleImpl(FontFamilyList.create(source.getFontName()), 12,
				FontStyle.Style.NORMAL, FontStyle.Weight.W_400, FontStyle.Direction.LTR, EMBEDDED);
		final var metrics = new FontMetricsImpl(pdf, source, style);
		final var font = metrics.getFont();
		final var text = new TextImpl(0, style, metrics);
		text.appendGlyph(new char[] { c }, 0, (byte) 1, font.toGID(c, FontFeatureSet.EMPTY));
		text.pack();
		return text;
	}

	@Test
	public void kUsesLogicalHintsWhileMcidAndParentTreeStayInPaintOrder() throws Exception {
		final var rendered = render();
		assertEquals(0, rendered.firstMcid);
		assertEquals(1, rendered.secondMcid);
		assertEquals(2, rendered.thirdMcid);

		final int mcid0 = rendered.pdf.indexOf("/MCID 0");
		final int mcid1 = rendered.pdf.indexOf("/MCID 1");
		final int mcid2 = rendered.pdf.indexOf("/MCID 2");
		assertTrue(mcid0 >= 0 && mcid0 < mcid1 && mcid1 < mcid2,
				"content-stream MCIDs must remain in paint order\n" + rendered.pdf);

		final String paragraphKids = structureKids(rendered.pdf, "P");
		assertTrue(Pattern.compile("^2\\s+\\d+\\s+0\\s+R\\s+0$").matcher(paragraphKids).matches(),
				"paragraph /K must be logical: MCR 2, Link element, MCR 0; got " + paragraphKids);

		final String linkKids = structureKids(rendered.pdf, "Link");
		assertTrue(linkKids.startsWith("<< /Type /OBJR"),
				"OBJR and MCR must share ordered slots: " + linkKids);
		assertTrue(linkKids.endsWith("1"), "the link MCR must follow its lower tie-breaker OBJR: " + linkKids);

		final var parentTree = Pattern.compile("(?s)/Nums\\s*\\[\\s*\\d+\\s*\\[\\s*"
				+ "(\\d+\\s+0\\s+R)\\s+(\\d+\\s+0\\s+R)\\s+(\\d+\\s+0\\s+R)\\s*\\]")
				.matcher(rendered.pdf);
		assertTrue(parentTree.find(), "missing three-entry MCID parent array\n" + rendered.pdf);
		assertEquals(parentTree.group(1), parentTree.group(3), "MCID 0 and 2 belong to the paragraph");
		assertTrue(!parentTree.group(1).equals(parentTree.group(2)), "MCID 1 belongs to the Link element");
	}

	@Test
	public void allNullOrdersAreByteIdenticalToTheOneArgumentApi() throws Exception {
		assertArrayEquals(comparablePdf(renderNullOrders(false)), comparablePdf(renderNullOrders(true)));
	}

	@Test
	public void nullTargetDoesNotLeakItsOrderToAnImplicitRootKid() throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder, params(false));
		final var page = pdf.nextPage(200, 200);
		page.beginStructContent(null, new StructureOrder(1, 20, 0));
		page.beginMark("H1", null);
		textOperators(page, "implicit");
		page.endMark();
		page.endStructContent();

		final var ordered = page.declareStructElement(null, "H2", null);
		mark(page, ordered, new StructureOrder(1, 10, 0), "declared");
		page.close();
		pdf.close();
		builder.close();

		final String result = bytes.toString(StandardCharsets.ISO_8859_1);
		assertKidRoleOrder(result, "Document", "H1", "H2");
	}

	@Test
	public void equalKeysUsePaintSequenceAsTheStableTieBreaker() throws Exception {
		final var key = new StructureOrder(3, 4, 5);
		final String pdf = renderRoleOrder(new String[] { "H1", "H2", "H3" },
				new String[] { "H2", "H3", "H1" }, new StructureOrder[] { key, key, key });
		assertKidRoleOrder(pdf, "Document", "H2", "H3", "H1");
	}

	@Test
	public void nullKidsKeepTheirSlotsWhileHintedKidsSortAroundThem() throws Exception {
		final String pdf = renderRoleOrder(new String[] { "H1", "H2", "H3", "H4" },
				new String[] { "H1", "H2", "H3", "H4" },
				new StructureOrder[] { new StructureOrder(1, 30, 0), null,
						new StructureOrder(1, 10, 0), null });
		assertKidRoleOrder(pdf, "Document", "H3", "H2", "H1", "H4");
	}

	@Test
	public void pdfUaMultiPageOrderUsesMcrForTheNonPrimaryPage() throws Exception {
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder, params(true));
		final var source = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.LTR);

		final var firstPage = pdf.nextPage(200, 200);
		final var paragraph = firstPage.declareStructElement(null, "P", null);
		firstPage.beginStructContent(paragraph, new StructureOrder(1, 20, 0));
		try (final var gc = new PDFGC(firstPage)) {
			gc.drawText(text(pdf, source, 'A'), 20, 30);
		}
		firstPage.endStructContent();

		final var secondPage = pdf.nextPage(200, 200);
		secondPage.beginStructContent(paragraph, new StructureOrder(1, 10, 0));
		try (final var gc = new PDFGC(secondPage)) {
			gc.drawText(text(pdf, source, 'B'), 20, 30);
		}
		secondPage.endStructContent();
		pdf.close();
		builder.close();

		final String result = bytes.toString(StandardCharsets.ISO_8859_1);
		final String paragraphKids = structureKids(result, "P");
		final String firstPageRef = pageRef(result, 0);
		final String secondPageRef = pageRef(result, 1);
		assertEquals("<< /Type /MCR /Pg " + secondPageRef + " /MCID 0 >> 0", paragraphKids,
				"logical page 2 MCR must precede page 1 MCID");
		assertEquals(2, count(result, "/StructParents "), "both pages need parent-tree keys");
		final String paragraphRef = structureRef(result, "P");
		assertTrue(objectBody(result, paragraphRef).contains("/Pg " + firstPageRef),
				"the paragraph primary page must remain page 1");
		assertTrue(Pattern.compile("(?s)/Nums\\s*\\[\\s*0\\s*\\[\\s*" + Pattern.quote(paragraphRef)
				+ "\\s*\\]\\s*1\\s*\\[\\s*" + Pattern.quote(paragraphRef) + "\\s*\\]")
				.matcher(result).find(), "both page-local MCID 0 entries must resolve to the paragraph\n" + result);
		assertTrue(result.contains("/StructTreeRoot"));
		assertTrue(result.contains("/Marked true"));
		assertTrue(result.contains("/Lang (en)"));
		assertTrue(result.contains("/DisplayDocTitle true"));
	}

	private static int count(final String value, final String needle) {
		int count = 0;
		for (int at = 0; (at = value.indexOf(needle, at)) >= 0; at += needle.length()) {
			++count;
		}
		return count;
	}
}
