package net.zamasoft.pdfg2d.pdf.preflight;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * pdfg2dが生成するPDF/Xを規則単位で検査する回帰プリフライトです。
 * <p>
 * ISO規格の完全な適合判定器ではなく、実装済み規則だけを検査します。未実装規則は
 * {@link #unimplementedRules()} で確認できます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class PdfXPreflight {
	private static final double RECT_EPSILON = 1e-3;
	private static final COSName GTS_PDFX_VERSION = COSName.getPDFName("GTS_PDFXVersion");
	private static final COSName ALTERNATE_PRESENTATIONS = COSName.getPDFName("AlternatePresentations");
	private static final COSName JAVA_SCRIPT = COSName.getPDFName("JavaScript");
	private static final COSName DEFAULT_RGB = COSName.getPDFName("DefaultRGB");
	private static final COSName DEVICE_RGB = COSName.getPDFName("DeviceRGB");
	private static final COSName ICC_BASED = COSName.getPDFName("ICCBased");
	private static final COSName CAL_RGB = COSName.getPDFName("CalRGB");
	private static final COSName LAB = COSName.getPDFName("Lab");
	private static final COSName SEPARATION = COSName.getPDFName("Separation");
	private static final COSName DEVICE_N = COSName.getPDFName("DeviceN");
	private static final COSName INDEXED = COSName.getPDFName("Indexed");
	private static final COSName TR = COSName.getPDFName("TR");
	private static final COSName TR2 = COSName.getPDFName("TR2");
	private static final COSName HTP = COSName.getPDFName("HTP");
	private static final COSName HT = COSName.getPDFName("HT");
	private static final COSName HALFTONE_TYPE = COSName.getPDFName("HalftoneType");
	private static final COSName PS = COSName.getPDFName("PS");
	private static final COSName OPI = COSName.getPDFName("OPI");
	private static final COSName REF = COSName.getPDFName("Ref");
	private static final COSName IMAGE_MASK = COSName.getPDFName("ImageMask");
	private static final COSName SMASK = COSName.getPDFName("SMask");
	private static final COSName GROUP = COSName.getPDFName("Group");
	private static final COSName CS = COSName.getPDFName("CS");
	private static final COSName TRANSPARENCY = COSName.getPDFName("Transparency");
	private static final COSName CA_STROKE = COSName.getPDFName("CA");
	private static final COSName CA_NONSTROKE = COSName.getPDFName("ca");
	private static final COSName BM = COSName.getPDFName("BM");
	private static final COSName NORMAL = COSName.getPDFName("Normal");
	private static final COSName COMPATIBLE = COSName.getPDFName("Compatible");
	private static final COSName OC_PROPERTIES = COSName.getPDFName("OCProperties");
	private static final COSName DEFAULT_CONFIG = COSName.getPDFName("D");
	private static final COSName CONFIGS = COSName.getPDFName("Configs");
	private static final COSName INTENT = COSName.getPDFName("Intent");
	private static final COSName VIEW = COSName.getPDFName("View");
	private static final COSName DESIGN = COSName.getPDFName("Design");
	private static final COSName USAGE_APPLICATION = COSName.getPDFName("AS");

	private static final String PDFX_ID_NS = "http://www.npes.org/pdfx/ns/id/";
	private static final String PDF_NS = "http://ns.adobe.com/pdf/1.3/";
	private static final String XMP_NS = "http://ns.adobe.com/xap/1.0/";
	private static final String XMP_MM_NS = "http://ns.adobe.com/xap/1.0/mm/";
	private static final String DC_NS = "http://purl.org/dc/elements/1.1/";

	private PdfXPreflight() {
		// static use only
	}

	/** 検査対象のPDF/Xフレーバーです。 */
	public enum Flavour {
		/** PDF/X-1a:2003。 */
		X1A,
		/** PDF/X-4。 */
		X4
	}

	/**
	 * 1件の規則違反です。
	 *
	 * @param rule    規則番号
	 * @param message 違反内容
	 */
	public record Violation(String rule, String message) {
		public Violation {
			Objects.requireNonNull(rule, "rule");
			Objects.requireNonNull(message, "message");
		}
	}

	/**
	 * 実装済み規則でPDFを検査します。
	 *
	 * @param pdf     PDFバイト列
	 * @param flavour 対象フレーバー
	 * @return 違反一覧。空なら合格
	 */
	public static List<Violation> check(final byte[] pdf, final Flavour flavour) {
		Objects.requireNonNull(pdf, "pdf");
		Objects.requireNonNull(flavour, "flavour");
		final var violations = new ArrayList<Violation>();
		try (final var document = Loader.loadPDF(pdf)) {
			violations.addAll(checkR1(pdf, document, flavour));
			violations.addAll(checkR2(document));
			violations.addAll(checkR3(document));
			if (flavour == Flavour.X4) {
				violations.addAll(checkR4(document));
			}
			violations.addAll(checkR5(document));
			violations.addAll(checkR6(document));
			violations.addAll(checkR7(document, flavour));
			violations.addAll(checkR8(document, flavour));
			violations.addAll(checkR9(document));
			violations.addAll(checkR10(document));
			violations.addAll(checkR11(document));
			violations.addAll(checkR12(document, flavour));
			violations.addAll(checkR13(document, flavour));
		} catch (final IOException e) {
			violations.add(new Violation("PDF", "PDFを解析できません: " + e.getMessage()));
		}
		return List.copyOf(violations);
	}

	/**
	 * PDFが実装済み規則に適合することを表明します。
	 *
	 * @param pdf     PDFバイト列
	 * @param flavour 対象フレーバー
	 * @throws AssertionError 違反が1件以上ある場合
	 */
	public static void assertConforms(final byte[] pdf, final Flavour flavour) {
		final var violations = check(pdf, flavour);
		if (!violations.isEmpty()) {
			final var message = new StringBuilder("PDF/X回帰プリフライト違反:");
			for (final var violation : violations) {
				message.append(System.lineSeparator()).append(violation.rule()).append(": ")
						.append(violation.message());
			}
			throw new AssertionError(message.toString());
		}
	}

	/**
	 * 現在未実装で、{@link #check(byte[], Flavour)} の結果に含めない規則を返します。
	 *
	 * @return 未実装規則番号
	 */
	public static List<String> unimplementedRules() {
		return List.of();
	}

	private static List<Violation> checkR1(final byte[] pdf, final PDDocument document, final Flavour flavour) {
		final var violations = new ArrayList<Violation>();
		final var expectedHeader = flavour == Flavour.X1A ? "%PDF-1.4" : "%PDF-1.6";
		final var firstEnd = lineEnd(pdf, 0);
		if (firstEnd < 0 || !expectedHeader.equals(new String(pdf, 0, firstEnd, StandardCharsets.US_ASCII))) {
			violations.add(new Violation("R1", "ヘッダ版が" + expectedHeader + "ではありません"));
		}
		final var secondStart = nextLineStart(pdf, firstEnd);
		final var secondEnd = lineEnd(pdf, secondStart);
		var binaryBytes = 0;
		if (secondStart < 0 || secondStart >= pdf.length || pdf[secondStart] != '%' || secondEnd < 0) {
			violations.add(new Violation("R1", "ヘッダ直後にバイナリ識別コメントがありません"));
		} else {
			for (var i = secondStart + 1; i < secondEnd; ++i) {
				if ((pdf[i] & 0xFF) >= 0x80) {
					++binaryBytes;
				}
			}
			if (binaryBytes < 4) {
				violations.add(new Violation("R1", "バイナリ識別コメントの高位バイトが4個未満です"));
			}
		}

		final var ids = document.getDocument().getDocumentID();
		if (ids == null || ids.size() != 2 || !(ids.getObject(0) instanceof COSString first)
				|| first.getBytes().length == 0 || !(ids.getObject(1) instanceof COSString second)
				|| second.getBytes().length == 0) {
			violations.add(new Violation("R1", "trailerの/IDが2要素の文字列配列ではありません"));
		}
		return violations;
	}

	private static int lineEnd(final byte[] bytes, final int start) {
		if (start < 0) {
			return -1;
		}
		for (var i = start; i < bytes.length; ++i) {
			if (bytes[i] == '\r' || bytes[i] == '\n') {
				return i;
			}
		}
		return -1;
	}

	private static int nextLineStart(final byte[] bytes, final int end) {
		if (end < 0 || end >= bytes.length) {
			return -1;
		}
		var next = end + 1;
		if (bytes[end] == '\r' && next < bytes.length && bytes[next] == '\n') {
			++next;
		}
		return next;
	}

	private static List<Violation> checkR2(final PDDocument document) {
		final var violations = new ArrayList<Violation>();
		final var catalog = document.getDocumentCatalog().getCOSObject();
		final var intents = catalog.getCOSArray(COSName.OUTPUT_INTENTS);
		if (intents == null) {
			return List.of(new Violation("R2", "カタログに/OutputIntents配列がありません"));
		}

		var count = 0;
		for (var i = 0; i < intents.size(); ++i) {
			final var intent = asDictionary(intents.getObject(i));
			if (intent == null || !"GTS_PDFX".equals(intent.getNameAsString(COSName.S))) {
				continue;
			}
			++count;
			final var identifier = intent.getString(COSName.OUTPUT_CONDITION_IDENTIFIER);
			if (isBlank(identifier)) {
				violations.add(new Violation("R2", "OutputConditionIdentifierが空です"));
			} else if (identifier.startsWith("Probe")) {
				violations.add(new Violation("R2", "OutputConditionIdentifierがProbeで始まります"));
			}
			if (isBlank(intent.getString(COSName.REGISTRY_NAME))) {
				violations.add(new Violation("R2", "RegistryNameが空です"));
			}

			final var profile = asStream(intent.getDictionaryObject(COSName.DEST_OUTPUT_PROFILE));
			if (profile == null) {
				violations.add(new Violation("R2", "DestOutputProfileがありません"));
				continue;
			}
			if (profile.getInt(COSName.N, -1) != 4) {
				violations.add(new Violation("R2", "DestOutputProfileの/Nが4ではありません"));
			}
			try (final var in = profile.createInputStream()) {
				final var header = in.readNBytes(20);
				if (header.length < 20) {
					violations.add(new Violation("R2", "ICCプロファイルヘッダが短すぎます"));
				} else {
					final var profileClass = new String(header, 12, 4, StandardCharsets.US_ASCII);
					final var colorSpace = new String(header, 16, 4, StandardCharsets.US_ASCII);
					if (!"prtr".equals(profileClass)) {
						violations.add(new Violation("R2", "ICCプロファイルclassがprtrではありません"));
					}
					if (!"CMYK".equals(colorSpace)) {
						violations.add(new Violation("R2", "ICCプロファイル色空間がCMYKではありません"));
					}
				}
			} catch (final IOException e) {
				violations.add(new Violation("R2", "ICCプロファイルを読み込めません: " + e.getMessage()));
			}
		}
		if (count != 1) {
			violations.add(new Violation("R2", "/S /GTS_PDFXのOutputIntentが" + count + "個あります"));
		}
		return violations;
	}

	private static List<Violation> checkR3(final PDDocument document) {
		final var violations = new ArrayList<Violation>();
		final var info = asDictionary(document.getDocument().getTrailer().getDictionaryObject(COSName.INFO));
		if (info == null) {
			return List.of(new Violation("R3", "Info辞書がありません"));
		}
		if (isBlank(info.getString(GTS_PDFX_VERSION))) {
			violations.add(new Violation("R3", "InfoのGTS_PDFXVersionがありません"));
		}
		final var trapped = nameOrString(info.getDictionaryObject(COSName.TRAPPED));
		if (!"True".equals(trapped) && !"False".equals(trapped)) {
			violations.add(new Violation("R3", "InfoのTrappedがTrueまたはFalseではありません"));
		}
		if (isBlank(info.getString(COSName.TITLE))) {
			violations.add(new Violation("R3", "InfoのTitleがありません"));
		}
		if (info.getDictionaryObject(COSName.CREATION_DATE) == null) {
			violations.add(new Violation("R3", "InfoのCreationDateがありません"));
		}
		if (info.getDictionaryObject(COSName.MOD_DATE) == null) {
			violations.add(new Violation("R3", "InfoのModDateがありません"));
		}
		return violations;
	}

	private static List<Violation> checkR4(final PDDocument document) {
		final var violations = new ArrayList<Violation>();
		final var metadata = asStream(document.getDocumentCatalog().getCOSObject()
				.getDictionaryObject(COSName.METADATA));
		if (metadata == null) {
			return List.of(new Violation("R4", "カタログにXMP Metadataがありません"));
		}

		try (final var in = metadata.createInputStream()) {
			final var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			final var xmp = factory.newDocumentBuilder().parse(in);

			requireXmp(violations, xmp, PDFX_ID_NS, "GTS_PDFXVersion", "pdfxid:GTS_PDFXVersion");
			final var xmpTrapped = requireXmp(violations, xmp, PDF_NS, "Trapped", "pdf:Trapped");
			final var create = requireXmp(violations, xmp, XMP_NS, "CreateDate", "xmp:CreateDate");
			final var modify = requireXmp(violations, xmp, XMP_NS, "ModifyDate", "xmp:ModifyDate");
			final var metadataDate = requireXmp(violations, xmp, XMP_NS, "MetadataDate", "xmp:MetadataDate");
			final var xmpDocumentId = requireXmp(violations, xmp, XMP_MM_NS, "DocumentID", "xmpMM:DocumentID");
			final var versionId = requireXmp(violations, xmp, XMP_MM_NS, "VersionID", "xmpMM:VersionID");
			final var rendition = requireXmp(violations, xmp, XMP_MM_NS, "RenditionClass",
					"xmpMM:RenditionClass");
			final var xmpTitle = requireXmp(violations, xmp, DC_NS, "title", "dc:title");

			final var info = document.getDocumentInformation();
			if (xmpTrapped != null && !Objects.equals(xmpTrapped, info.getTrapped())) {
				violations.add(new Violation("R4", "pdf:TrappedとInfoのTrappedが一致しません"));
			}
			if (xmpTitle != null && !Objects.equals(xmpTitle, info.getTitle())) {
				violations.add(new Violation("R4", "dc:titleとInfoのTitleが一致しません"));
			}
			compareDate(violations, "xmp:CreateDate", create, info.getCreationDate());
			compareDate(violations, "xmp:ModifyDate", modify, info.getModificationDate());
			if (create != null && metadataDate != null && !sameXmpDate(create, metadataDate)) {
				violations.add(new Violation("R4", "xmp:MetadataDateとxmp:CreateDateが一致しません"));
			}
			if (xmpDocumentId != null) {
				final var ids = document.getDocument().getDocumentID();
				if (ids == null || ids.size() == 0 || !(ids.getObject(0) instanceof COSString id)
						|| !documentId(id.getBytes()).equals(xmpDocumentId)) {
					violations.add(new Violation("R4", "xmpMM:DocumentIDがtrailerの第1 IDと一致しません"));
				}
			}
			if (versionId != null && !"1".equals(versionId)) {
				violations.add(new Violation("R4", "xmpMM:VersionIDが1ではありません"));
			}
			if (rendition != null && !"default".equals(rendition)) {
				violations.add(new Violation("R4", "xmpMM:RenditionClassがdefaultではありません"));
			}
		} catch (final IOException | ParserConfigurationException | SAXException e) {
			violations.add(new Violation("R4", "XMPを解析できません: " + e.getMessage()));
		}
		return violations;
	}

	private static String requireXmp(final List<Violation> violations, final Document xmp, final String namespace,
			final String localName, final String displayName) {
		final var elements = xmp.getElementsByTagNameNS(namespace, localName);
		if (elements.getLength() == 0 || isBlank(elements.item(0).getTextContent())) {
			violations.add(new Violation("R4", displayName + "がありません"));
			return null;
		}
		return elements.item(0).getTextContent().trim();
	}

	private static void compareDate(final List<Violation> violations, final String name, final String xmpDate,
			final Calendar infoDate) {
		if (xmpDate == null || infoDate == null) {
			return;
		}
		try {
			if (OffsetDateTime.parse(xmpDate).toInstant().getEpochSecond() != infoDate.toInstant().getEpochSecond()) {
				violations.add(new Violation("R4", name + "とInfoの日付が一致しません"));
			}
		} catch (final DateTimeParseException e) {
			violations.add(new Violation("R4", name + "の日付形式が不正です"));
		}
	}

	private static boolean sameXmpDate(final String first, final String second) {
		try {
			return OffsetDateTime.parse(first).toInstant().equals(OffsetDateTime.parse(second).toInstant());
		} catch (final DateTimeParseException e) {
			return false;
		}
	}

	private static String documentId(final byte[] id) {
		if (id.length != 16) {
			return "";
		}
		final var hex = HexFormat.of().formatHex(id);
		return "uuid:" + hex.substring(0, 8) + '-' + hex.substring(8, 12) + '-'
				+ hex.substring(12, 16) + '-' + hex.substring(16, 20) + '-' + hex.substring(20);
	}

	private static List<Violation> checkR5(final PDDocument document) {
		final var violations = new ArrayList<Violation>();
		final var trailer = document.getDocument().getTrailer();
		if (trailer.getDictionaryObject(COSName.ENCRYPT) != null) {
			violations.add(new Violation("R5", "/Encryptがあります"));
		}
		final var catalog = document.getDocumentCatalog().getCOSObject();
		if (catalog.getDictionaryObject(COSName.OPEN_ACTION) != null) {
			violations.add(new Violation("R5", "カタログに/OpenActionがあります"));
		}
		if (catalog.getDictionaryObject(COSName.AA) != null) {
			violations.add(new Violation("R5", "カタログに/AAがあります"));
		}
		if (catalog.getDictionaryObject(ALTERNATE_PRESENTATIONS) != null) {
			violations.add(new Violation("R5", "カタログに/AlternatePresentationsがあります"));
		}
		final var names = asDictionary(catalog.getDictionaryObject(COSName.NAMES));
		if (names != null && names.getDictionaryObject(JAVA_SCRIPT) != null) {
			violations.add(new Violation("R5", "/Namesに/JavaScriptがあります"));
		}
		return violations;
	}

	private static List<Violation> checkR6(final PDDocument document) {
		final var violations = new ArrayList<Violation>();
		final Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		var pageNumber = 0;
		for (final var page : document.getPages()) {
			++pageNumber;
			if (page.getResources() != null) {
				checkResourceFonts(page.getResources().getCOSObject(), "ページ" + pageNumber, visited, violations);
			}
		}
		return violations;
	}

	private static void checkResourceFonts(final COSDictionary resources, final String location,
			final Set<COSDictionary> visited, final List<Violation> violations) {
		if (!visited.add(resources)) {
			return;
		}
		final var fonts = asDictionary(resources.getDictionaryObject(COSName.FONT));
		if (fonts != null) {
			for (final var entry : fonts.entrySet()) {
				final var font = asDictionary(resolve(entry.getValue()));
				if (font != null) {
					checkFont(font, location + "の/" + entry.getKey().getName(), violations);
				}
			}
		}
		checkNestedResources(resources, COSName.XOBJECT, location, visited, violations);
		checkNestedResources(resources, COSName.PATTERN, location, visited, violations);
	}

	private static void checkNestedResources(final COSDictionary resources, final COSName resourceType,
			final String location, final Set<COSDictionary> visited, final List<Violation> violations) {
		final var children = asDictionary(resources.getDictionaryObject(resourceType));
		if (children == null) {
			return;
		}
		for (final var entry : children.entrySet()) {
			final var child = asDictionary(resolve(entry.getValue()));
			if (child == null) {
				continue;
			}
			final var nested = asDictionary(child.getDictionaryObject(COSName.RESOURCES));
			if (nested != null) {
				checkResourceFonts(nested, location + "の/" + entry.getKey().getName(), visited, violations);
			}
		}
	}

	private static void checkFont(final COSDictionary font, final String location,
			final List<Violation> violations) {
		if (COSName.TYPE3.equals(font.getCOSName(COSName.SUBTYPE))) {
			return;
		}
		if (COSName.TYPE0.equals(font.getCOSName(COSName.SUBTYPE))) {
			final var descendants = font.getCOSArray(COSName.DESCENDANT_FONTS);
			if (descendants == null || descendants.size() == 0) {
				violations.add(new Violation("R6", location + "のType0フォントにDescendantFontsがありません"));
				return;
			}
			for (var i = 0; i < descendants.size(); ++i) {
				checkFontDescriptor(asDictionary(descendants.getObject(i)),
						location + "のDescendantFonts[" + i + ']', violations);
			}
		} else {
			checkFontDescriptor(font, location, violations);
		}
	}

	private static void checkFontDescriptor(final COSDictionary font, final String location,
			final List<Violation> violations) {
		if (font == null) {
			violations.add(new Violation("R6", location + "がフォント辞書ではありません"));
			return;
		}
		final var descriptor = asDictionary(font.getDictionaryObject(COSName.FONT_DESC));
		if (descriptor == null) {
			violations.add(new Violation("R6", location + "にFontDescriptorがありません"));
			return;
		}
		if (descriptor.getDictionaryObject(COSName.FONT_FILE) == null
				&& descriptor.getDictionaryObject(COSName.FONT_FILE2) == null
				&& descriptor.getDictionaryObject(COSName.FONT_FILE3) == null) {
			violations.add(new Violation("R6", location + "のFontDescriptorにFontFile*がありません"));
		}
	}

	private static List<Violation> checkR10(final PDDocument document) {
		final var violations = new ArrayList<Violation>();
		var pageNumber = 0;
		for (final var page : document.getPages()) {
			++pageNumber;
			final var pageDict = page.getCOSObject();
			final var trim = rectangle(pageDict.getDictionaryObject(COSName.TRIM_BOX));
			final var art = rectangle(pageDict.getDictionaryObject(COSName.ART_BOX));
			if ((trim == null) == (art == null)) {
				violations.add(new Violation("R10", "ページ" + pageNumber + "はTrimBoxとArtBoxの一方だけを持ちません"));
				continue;
			}
			final var target = trim != null ? trim : art;
			final var media = page.getMediaBox();
			if (media == null || !contains(media, target)) {
				violations.add(new Violation("R10", "ページ" + pageNumber + "のTrim/ArtBoxがMediaBox内にありません"));
			}
			final var bleed = rectangle(pageDict.getDictionaryObject(COSName.BLEED_BOX));
			final var effectiveBleed = bleed != null ? bleed : target;
			if (bleed != null && (!contains(bleed, target) || media == null || !contains(media, bleed))) {
				violations.add(new Violation("R10", "ページ" + pageNumber + "のTrim/Art・Bleed・MediaBoxの包含が不正です"));
			}
			// CropBoxは継承可能で、省略時はMediaBoxになる。PDPageで意味的に
			// 解決しておけば、明示・継承・既定のいずれでも同じ包含検査になる。
			final var crop = page.getCropBox();
			if (crop == null || !contains(crop, effectiveBleed) || media == null || !contains(media, crop)) {
				violations.add(new Violation("R10", "ページ" + pageNumber + "のBleed・Crop・MediaBoxの包含が不正です"));
			}
		}
		return violations;
	}

	private static List<Violation> checkR11(final PDDocument document) {
		final var violations = new ArrayList<Violation>();
		var pageNumber = 0;
		for (final var page : document.getPages()) {
			++pageNumber;
			final var pageDict = page.getCOSObject();
			final var annots = pageDict.getCOSArray(COSName.ANNOTS);
			if (annots == null) {
				continue;
			}
			final var trim = rectangle(pageDict.getDictionaryObject(COSName.TRIM_BOX));
			final var art = rectangle(pageDict.getDictionaryObject(COSName.ART_BOX));
			final var finished = trim != null ? trim : art;
			final var bleed = rectangle(pageDict.getDictionaryObject(COSName.BLEED_BOX));
			for (var i = 0; i < annots.size(); ++i) {
				final var annot = asDictionary(annots.getObject(i));
				if (annot == null) {
					continue;
				}
				final var rect = rectangle(annot.getDictionaryObject(COSName.RECT));
				final var printerMark = "PrinterMark".equals(annot.getNameAsString(COSName.SUBTYPE));
				final var prohibited = printerMark ? finished : (bleed != null ? bleed : finished);
				if (rect != null && prohibited != null && intersects(rect, prohibited)) {
					violations.add(new Violation("R11", "ページ" + pageNumber + "の注釈" + (i + 1)
							+ "が" + (printerMark ? "Trim/ArtBox" : "Bleed/TrimBox") + "と交差します"));
				}
			}
		}
		return violations;
	}

	private static List<Violation> checkR12(final PDDocument document, final Flavour flavour) {
		final var violations = new ArrayList<Violation>();
		final Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		visitStreams(document.getDocument().getTrailer(), flavour, visited, violations);
		for (final var key : document.getDocument().getXrefTable().keySet()) {
			visitStreams(document.getDocument().getObjectFromPool(key), flavour, visited, violations);
		}
		return violations;
	}

	private static void visitStreams(final COSBase base, final Flavour flavour, final Set<COSBase> visited,
			final List<Violation> violations) {
		if (base == null || !visited.add(base)) {
			return;
		}
		if (base instanceof COSObject object) {
			visitStreams(object.getObject(), flavour, visited, violations);
			return;
		}
		if (base instanceof COSStream stream) {
			checkStream(stream, flavour, violations);
		}
		if (base instanceof COSDictionary dictionary) {
			for (final var value : dictionary.getValues()) {
				visitStreams(value, flavour, visited, violations);
			}
		} else if (base instanceof COSArray array) {
			for (final var value : array) {
				visitStreams(value, flavour, visited, violations);
			}
		}
	}

	private static void checkStream(final COSStream stream, final Flavour flavour,
			final List<Violation> violations) {
		final var filters = stream.getDictionaryObject(COSName.FILTER);
		if (filters instanceof COSName name) {
			checkFilter(name, flavour, violations);
		} else if (filters instanceof COSArray array) {
			for (var i = 0; i < array.size(); ++i) {
				if (array.getObject(i) instanceof COSName name) {
					checkFilter(name, flavour, violations);
				}
			}
		}
		if (COSName.IMAGE.equals(stream.getCOSName(COSName.SUBTYPE))
				&& stream.getInt(COSName.BITS_PER_COMPONENT, -1) == 16) {
			violations.add(new Violation("R12", "画像XObjectのBitsPerComponentが16です"));
		}
	}

	private static void checkFilter(final COSName filter, final Flavour flavour,
			final List<Violation> violations) {
		final var name = filter.getName();
		if ("LZWDecode".equals(name) || "LZW".equals(name) || "JBIG2Decode".equals(name)
				|| "Crypt".equals(name) || (flavour == Flavour.X1A && "JPXDecode".equals(name))) {
			violations.add(new Violation("R12", "禁止されたストリームフィルタ/" + name + "があります"));
		}
	}

	private record NamedColorDefinition(COSBase alternate, COSBase tintTransform, String location) {
	}

	private record ResourceLocation(COSDictionary resources, String location) {
	}

	private static final class ColorCheckContext {
		final Flavour flavour;
		final List<Violation> violations = new ArrayList<>();
		final Set<COSDictionary> visitedResources = Collections.newSetFromMap(new IdentityHashMap<>());
		final Set<COSStream> visitedContents = Collections.newSetFromMap(new IdentityHashMap<>());
		final Map<String, NamedColorDefinition> separations = new HashMap<>();
		final Map<String, NamedColorDefinition> deviceNs = new HashMap<>();
		final List<ResourceLocation> resourceLocations = new ArrayList<>();
		boolean hasRGBObject;

		ColorCheckContext(final Flavour flavour) {
			this.flavour = flavour;
		}
	}

	/** R7の内容ストリーム、画像、色空間と透明グループ色空間を検査します。 */
	private static List<Violation> checkR7(final PDDocument document, final Flavour flavour) {
		final var context = new ColorCheckContext(flavour);
		var pageNumber = 0;
		for (final var page : document.getPages()) {
			++pageNumber;
			final var resources = page.getResources() == null ? null : page.getResources().getCOSObject();
			final var location = "ページ" + pageNumber;
			checkGroupColorSpace(page.getCOSObject(), resources, location, context);
			checkContents(page.getCOSObject().getDictionaryObject(COSName.CONTENTS), resources, location, context);
			checkColorResources(resources, location, context);
		}
		if (flavour == Flavour.X4 && context.hasRGBObject) {
			for (final var entry : context.resourceLocations) {
				if (!hasDefaultRGB(entry.resources())) {
					context.violations.add(new Violation("R7", entry.location()
							+ "のResourcesに/DefaultRGB [/ICCBased ...]がありません"));
				}
			}
		}
		return context.violations;
	}

	private static void checkContents(final COSBase contents, final COSDictionary resources, final String location,
			final ColorCheckContext context) {
		final var resolved = resolve(contents);
		if (resolved instanceof COSArray array) {
			for (var i = 0; i < array.size(); ++i) {
				checkContents(array.get(i), resources, location + "の内容" + (i + 1), context);
			}
			return;
		}
		if (!(resolved instanceof COSStream stream) || !context.visitedContents.add(stream)) {
			return;
		}
		try (final var in = stream.createInputStream()) {
			final var parser = new PDFStreamParser(in.readAllBytes());
			final var operands = new ArrayList<COSBase>();
			try {
				for (final var token : parser.parse()) {
					if (token instanceof Operator operator) {
						final var name = operator.getName();
						if ("rg".equals(name) || "RG".equals(name)) {
							context.hasRGBObject = true;
							if (context.flavour == Flavour.X1A) {
								context.violations.add(new Violation("R7", location + "に" + name + "演算子があります"));
							} else if (!hasDefaultRGB(resources)) {
								context.violations.add(new Violation("R7", location + "に" + name
										+ "演算子がありますが/DefaultRGBがありません"));
							}
						} else if ("cs".equals(name) || "CS".equals(name)) {
							if (operands.isEmpty() || !(resolve(operands.get(operands.size() - 1)) instanceof COSName colorSpace)) {
								context.violations.add(new Violation("R7", location + "の" + name
										+ "演算子に色空間名がありません"));
							} else {
								context.hasRGBObject |= isRGBColorSpace(colorSpace, resources, 0);
								checkColorSpaceName(colorSpace, resources, location + "の/" + colorSpace.getName(),
										context, 0);
							}
						}
						operands.clear();
					} else if (token instanceof COSBase operand) {
						operands.add(operand);
					}
				}
			} finally {
				parser.close();
			}
		} catch (final IOException e) {
			context.violations.add(new Violation("R7", location + "の内容ストリームを解析できません: "
					+ e.getMessage()));
		}
	}

	private static void checkColorResources(final COSDictionary resources, final String location,
			final ColorCheckContext context) {
		if (resources == null || !context.visitedResources.add(resources)) {
			return;
		}
		context.resourceLocations.add(new ResourceLocation(resources, location));
		final var colorSpaces = asDictionary(resources.getDictionaryObject(COSName.COLORSPACE));
		if (colorSpaces != null) {
			for (final var entry : colorSpaces.entrySet()) {
				checkColorSpace(entry.getValue(), resources,
						location + "の/ColorSpace/" + entry.getKey().getName(), context, 0);
			}
		}

		final var shadings = asDictionary(resources.getDictionaryObject(COSName.SHADING));
		if (shadings != null) {
			for (final var entry : shadings.entrySet()) {
				checkShading(entry.getValue(), resources, location + "の/Shading/" + entry.getKey().getName(), context);
			}
		}

		final var patterns = asDictionary(resources.getDictionaryObject(COSName.PATTERN));
		if (patterns != null) {
			for (final var entry : patterns.entrySet()) {
				final var pattern = asDictionary(entry.getValue());
				if (pattern == null) {
					continue;
				}
				final var patternLocation = location + "の/Pattern/" + entry.getKey().getName();
				if (pattern.getDictionaryObject(COSName.SHADING) != null) {
					checkShading(pattern.getDictionaryObject(COSName.SHADING), resources,
							patternLocation + "の/Shading", context);
				}
				if (pattern instanceof COSStream stream && pattern.getInt(COSName.PATTERN_TYPE, 0) == 1) {
					final var nested = asDictionary(pattern.getDictionaryObject(COSName.RESOURCES));
					checkContents(stream, nested, patternLocation, context);
					checkColorResources(nested, patternLocation, context);
				}
			}
		}

		final var xobjects = asDictionary(resources.getDictionaryObject(COSName.XOBJECT));
		if (xobjects != null) {
			for (final var entry : xobjects.entrySet()) {
				final var xobject = asStream(entry.getValue());
				if (xobject == null) {
					continue;
				}
				final var xobjectLocation = location + "の/XObject/" + entry.getKey().getName();
				if (COSName.IMAGE.equals(xobject.getCOSName(COSName.SUBTYPE))) {
					checkImageColorSpace(xobject, resources, xobjectLocation, context);
				} else if (COSName.FORM.equals(xobject.getCOSName(COSName.SUBTYPE))) {
					final var nested = asDictionary(xobject.getDictionaryObject(COSName.RESOURCES));
					checkGroupColorSpace(xobject, nested, xobjectLocation, context);
					checkContents(xobject, nested, xobjectLocation, context);
					checkColorResources(nested, xobjectLocation, context);
				}
			}
		}

		final var extGStates = asDictionary(resources.getDictionaryObject(COSName.EXT_G_STATE));
		if (extGStates != null) {
			for (final var entry : extGStates.entrySet()) {
				final var state = asDictionary(entry.getValue());
				final var softMask = state == null ? null : asDictionary(state.getDictionaryObject(SMASK));
				final var form = softMask == null ? null : asStream(softMask.getDictionaryObject(COSName.G));
				if (form == null) {
					continue;
				}
				final var formLocation = location + "の/ExtGState/" + entry.getKey().getName() + "の/SMask/G";
				final var nested = asDictionary(form.getDictionaryObject(COSName.RESOURCES));
				checkGroupColorSpace(form, nested, formLocation, context);
				checkContents(form, nested, formLocation, context);
				checkColorResources(nested, formLocation, context);
			}
		}

		final var fonts = asDictionary(resources.getDictionaryObject(COSName.FONT));
		if (fonts != null) {
			for (final var entry : fonts.entrySet()) {
				final var font = asDictionary(entry.getValue());
				if (font == null || !COSName.TYPE3.equals(font.getCOSName(COSName.SUBTYPE))) {
					continue;
				}
				final var fontLocation = location + "のType3/" + entry.getKey().getName();
				final var nested = asDictionary(font.getDictionaryObject(COSName.RESOURCES));
				final var charProcs = asDictionary(font.getDictionaryObject(COSName.CHAR_PROCS));
				if (charProcs != null) {
					for (final var charProc : charProcs.entrySet()) {
						checkContents(charProc.getValue(), nested,
								fontLocation + "の/CharProcs/" + charProc.getKey().getName(), context);
					}
				}
				checkColorResources(nested, fontLocation, context);
			}
		}
	}

	private static void checkShading(final COSBase base, final COSDictionary resources, final String location,
			final ColorCheckContext context) {
		COSBase shadingBase = base;
		if (resolve(base) instanceof COSName name) {
			final var shadings = resources == null ? null
					: asDictionary(resources.getDictionaryObject(COSName.SHADING));
			shadingBase = shadings == null ? null : shadings.getItem(name);
		}
		final var shading = asDictionary(shadingBase);
		if (shading == null) {
			context.violations.add(new Violation("R7", location + "のシェーディングを解決できません"));
			return;
		}
		final var colorSpace = shading.getItem(COSName.COLORSPACE);
		if (colorSpace == null) {
			context.violations.add(new Violation("R7", location + "に/ColorSpaceがありません"));
			return;
		}
		checkColorSpace(colorSpace, resources, location + "の/ColorSpace", context, 0);
		context.hasRGBObject |= isRGBColorSpace(colorSpace, resources, 0);
	}

	private static void checkImageColorSpace(final COSStream image, final COSDictionary resources,
			final String location, final ColorCheckContext context) {
		if (!image.getBoolean(IMAGE_MASK, false)) {
			final var colorSpace = image.getItem(COSName.COLORSPACE);
			if (colorSpace == null) {
				context.violations.add(new Violation("R7", location + "に/ColorSpaceがありません"));
			} else {
				context.hasRGBObject |= isRGBColorSpace(colorSpace, resources, 0);
				checkColorSpace(colorSpace, resources, location + "の/ColorSpace", context, 0);
			}
		}
		final var softMask = asStream(image.getDictionaryObject(SMASK));
		if (softMask != null && !isDeviceGrayColorSpace(softMask.getItem(COSName.COLORSPACE), resources, 0)) {
			context.violations.add(new Violation("R7", location + "の/SMaskが/DeviceGrayではありません"));
		}
	}

	private static void checkGroupColorSpace(final COSDictionary owner, final COSDictionary resources,
			final String location, final ColorCheckContext context) {
		final var group = asDictionary(owner.getDictionaryObject(GROUP));
		if (group == null || group.getItem(CS) == null) {
			return;
		}
		final var colorSpace = group.getItem(CS);
		context.hasRGBObject |= isRGBColorSpace(colorSpace, resources, 0);
		if (isDeviceRGBColorSpace(colorSpace, resources, 0)) {
			context.violations.add(new Violation("R7", location + "の/Group/CSが/DeviceRGBです"));
		} else {
			checkColorSpace(colorSpace, resources, location + "の/Group/CS", context, 0);
		}
	}

	private static void checkColorSpaceName(final COSName name, final COSDictionary resources,
			final String location, final ColorCheckContext context, final int depth) {
		if (isDeviceColorSpace(name) || COSName.PATTERN.equals(name) || CAL_RGB.equals(name) || LAB.equals(name)) {
			checkColorSpace(name, resources, location, context, depth);
			return;
		}
		final var colorSpaces = resources == null ? null
				: asDictionary(resources.getDictionaryObject(COSName.COLORSPACE));
		final var definition = colorSpaces == null ? null : colorSpaces.getItem(name);
		if (definition == null) {
			context.violations.add(new Violation("R7", location + "をResourcesの/ColorSpaceで解決できません"));
			return;
		}
		checkColorSpace(definition, resources, location, context, depth + 1);
	}

	private static void checkColorSpace(final COSBase base, final COSDictionary resources, final String location,
			final ColorCheckContext context, final int depth) {
		if (depth > 32) {
			context.violations.add(new Violation("R7", location + "の色空間参照が循環しています"));
			return;
		}
		final var colorSpace = resolve(base);
		if (colorSpace instanceof COSName name) {
			if (DEVICE_RGB.equals(name)) {
				if (context.flavour == Flavour.X1A) {
					context.violations.add(new Violation("R7", location + "が/DeviceRGBです"));
				} else if (!hasDefaultRGB(resources)) {
					context.violations.add(new Violation("R7", location
							+ "が/DeviceRGBですが同じResourcesに/DefaultRGBがありません"));
				}
			} else if (context.flavour == Flavour.X1A && (CAL_RGB.equals(name) || LAB.equals(name))) {
				context.violations.add(new Violation("R7", location + "が/" + name.getName() + "です"));
			} else if (!isDeviceColorSpace(name) && !COSName.PATTERN.equals(name)) {
				checkColorSpaceName(name, resources, location, context, depth + 1);
			}
			return;
		}
		if (!(colorSpace instanceof COSArray array) || array.size() == 0
				|| !(array.getObject(0) instanceof COSName family)) {
			context.violations.add(new Violation("R7", location + "が有効な色空間ではありません"));
			return;
		}
		if (ICC_BASED.equals(family)) {
			final var profile = array.size() > 1 ? asStream(array.get(1)) : null;
			final var components = profile == null ? -1 : profile.getInt(COSName.N, -1);
			if (profile == null || (components != 1 && components != 3 && components != 4)) {
				context.violations.add(new Violation("R7", location + "のICCBasedプロファイル/Nが不正です"));
			} else if (context.flavour == Flavour.X1A) {
				// ISO 15930-4 6.2.1: ICCBased colour spaces shall not be used
				context.violations.add(new Violation("R7", location + "が" + components + "成分ICCBasedです"));
			}
			// ICCBasedストリームの/Alternateはreaderが無視するため検査しない。
			return;
		}
		if (CAL_RGB.equals(family) || LAB.equals(family)) {
			if (context.flavour == Flavour.X1A) {
				context.violations.add(new Violation("R7", location + "が/" + family.getName() + "です"));
			}
			return;
		}
		if (SEPARATION.equals(family)) {
			if (array.size() < 4) {
				context.violations.add(new Violation("R7", location + "のSeparation定義が不完全です"));
				return;
			}
			registerNamedColor(context.separations, nameOrString(array.get(1)), array.get(2), array.get(3),
					location, "Separation", context.violations);
			checkColorSpace(array.get(2), resources, location + "のalternate", context, depth + 1);
			return;
		}
		if (DEVICE_N.equals(family)) {
			if (array.size() < 4) {
				context.violations.add(new Violation("R7", location + "のDeviceN定義が不完全です"));
				return;
			}
			final var names = resolve(array.get(1)) instanceof COSArray colorants
					? colorantNames(colorants) : null;
			registerNamedColor(context.deviceNs, names, array.get(2), array.get(3), location, "DeviceN",
					context.violations);
			checkColorSpace(array.get(2), resources, location + "のalternate", context, depth + 1);
			if (array.size() > 4) {
				final var attributes = asDictionary(array.get(4));
				final var colorants = attributes == null ? null
						: asDictionary(attributes.getDictionaryObject(COSName.getPDFName("Colorants")));
				if (colorants != null) {
					for (final var entry : colorants.entrySet()) {
						checkColorSpace(entry.getValue(), resources,
								location + "の/Colorants/" + entry.getKey().getName(), context, depth + 1);
					}
				}
			}
			return;
		}
		if ((INDEXED.equals(family) || COSName.PATTERN.equals(family)) && array.size() > 1) {
			checkColorSpace(array.get(1), resources, location + "の基底色空間", context, depth + 1);
			return;
		}
		checkColorSpace(family, resources, location, context, depth + 1);
	}

	private static boolean isDeviceColorSpace(final COSName name) {
		return COSName.DEVICEGRAY.equals(name) || DEVICE_RGB.equals(name) || COSName.DEVICECMYK.equals(name);
	}

	private static boolean isRGBColorSpace(final COSBase base, final COSDictionary resources, final int depth) {
		if (base == null || depth > 32) {
			return false;
		}
		final var colorSpace = resolve(base);
		if (colorSpace instanceof COSName name) {
			if (DEVICE_RGB.equals(name) || CAL_RGB.equals(name) || LAB.equals(name)) {
				return true;
			}
			if (isDeviceColorSpace(name) || COSName.PATTERN.equals(name)) {
				return false;
			}
			final var colorSpaces = resources == null ? null
					: asDictionary(resources.getDictionaryObject(COSName.COLORSPACE));
			return colorSpaces != null && isRGBColorSpace(colorSpaces.getItem(name), resources, depth + 1);
		}
		if (!(colorSpace instanceof COSArray array) || array.size() == 0
				|| !(array.getObject(0) instanceof COSName family)) {
			return false;
		}
		if (ICC_BASED.equals(family)) {
			final var profile = array.size() > 1 ? asStream(array.get(1)) : null;
			return profile != null && profile.getInt(COSName.N, -1) == 3;
		}
		if (CAL_RGB.equals(family) || LAB.equals(family)) {
			return true;
		}
		if ((INDEXED.equals(family) || COSName.PATTERN.equals(family)) && array.size() > 1) {
			return isRGBColorSpace(array.get(1), resources, depth + 1);
		}
		if ((SEPARATION.equals(family) || DEVICE_N.equals(family)) && array.size() > 2) {
			return isRGBColorSpace(array.get(2), resources, depth + 1);
		}
		return isRGBColorSpace(family, resources, depth + 1);
	}

	private static boolean isDeviceRGBColorSpace(final COSBase base, final COSDictionary resources,
			final int depth) {
		if (base == null || depth > 32) {
			return false;
		}
		final var colorSpace = resolve(base);
		if (!(colorSpace instanceof COSName name)) {
			return false;
		}
		if (DEVICE_RGB.equals(name)) {
			return true;
		}
		if (isDeviceColorSpace(name) || CAL_RGB.equals(name) || LAB.equals(name) || COSName.PATTERN.equals(name)) {
			return false;
		}
		final var colorSpaces = resources == null ? null
				: asDictionary(resources.getDictionaryObject(COSName.COLORSPACE));
		return colorSpaces != null && isDeviceRGBColorSpace(colorSpaces.getItem(name), resources, depth + 1);
	}

	private static boolean isDeviceGrayColorSpace(final COSBase base, final COSDictionary resources,
			final int depth) {
		if (base == null || depth > 32) {
			return false;
		}
		final var colorSpace = resolve(base);
		if (!(colorSpace instanceof COSName name)) {
			return false;
		}
		if (COSName.DEVICEGRAY.equals(name)) {
			return true;
		}
		if (isDeviceColorSpace(name) || CAL_RGB.equals(name) || LAB.equals(name) || COSName.PATTERN.equals(name)) {
			return false;
		}
		final var colorSpaces = resources == null ? null
				: asDictionary(resources.getDictionaryObject(COSName.COLORSPACE));
		return colorSpaces != null && isDeviceGrayColorSpace(colorSpaces.getItem(name), resources, depth + 1);
	}

	private static boolean hasDefaultRGB(final COSDictionary resources) {
		final var colorSpaces = resources == null ? null
				: asDictionary(resources.getDictionaryObject(COSName.COLORSPACE));
		final var defaultRGB = colorSpaces == null ? null : resolve(colorSpaces.getItem(DEFAULT_RGB));
		if (!(defaultRGB instanceof COSArray array) || array.size() < 2
				|| !ICC_BASED.equals(array.getObject(0))) {
			return false;
		}
		final var profile = asStream(array.get(1));
		return profile != null && profile.getInt(COSName.N, -1) == 3;
	}

	private static String colorantNames(final COSArray colorants) {
		final var names = new StringBuilder();
		for (var i = 0; i < colorants.size(); ++i) {
			if (i != 0) {
				names.append(',');
			}
			final var name = nameOrString(colorants.get(i));
			if (name == null) {
				return null;
			}
			names.append(name);
		}
		return names.toString();
	}

	private static void registerNamedColor(final Map<String, NamedColorDefinition> definitions, final String name,
			final COSBase alternate, final COSBase tintTransform, final String location, final String kind,
			final List<Violation> violations) {
		if (name == null) {
			violations.add(new Violation("R7", location + "の" + kind + "名が不正です"));
			return;
		}
		final var definition = new NamedColorDefinition(alternate, tintTransform, location);
		final var previous = definitions.putIfAbsent(name, definition);
		if (previous != null && (!sameCosObject(previous.alternate(), alternate)
				|| !sameCosObject(previous.tintTransform(), tintTransform))) {
			violations.add(new Violation("R7", kind + " '" + name + "' のalternate/tintTransformが"
					+ previous.location() + "と" + location + "で一致しません"));
		}
	}

	private static boolean sameCosObject(final COSBase first, final COSBase second) {
		return sameCosObject(first, second, new IdentityHashMap<>());
	}

	private static boolean sameCosObject(final COSBase firstBase, final COSBase secondBase,
			final Map<COSBase, Set<COSBase>> compared) {
		final var first = resolve(firstBase);
		final var second = resolve(secondBase);
		if (first == second) {
			return true;
		}
		if (first == null || second == null) {
			return false;
		}
		if (first instanceof COSNumber firstNumber && second instanceof COSNumber secondNumber) {
			return Float.compare(firstNumber.floatValue(), secondNumber.floatValue()) == 0;
		}
		if (first.getClass() != second.getClass()) {
			return false;
		}
		final var seconds = compared.computeIfAbsent(first,
				ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
		if (!seconds.add(second)) {
			return true;
		}
		if (first instanceof COSName firstName) {
			return firstName.equals(second);
		}
		if (first instanceof COSString firstString && second instanceof COSString secondString) {
			return Arrays.equals(firstString.getBytes(), secondString.getBytes());
		}
		if (first instanceof COSArray firstArray && second instanceof COSArray secondArray) {
			if (firstArray.size() != secondArray.size()) {
				return false;
			}
			for (var i = 0; i < firstArray.size(); ++i) {
				if (!sameCosObject(firstArray.get(i), secondArray.get(i), compared)) {
					return false;
				}
			}
			return true;
		}
		if (first instanceof COSStream firstStream && second instanceof COSStream secondStream) {
			if (!sameDictionary(firstStream, secondStream, compared, true)) {
				return false;
			}
			try (final var firstIn = firstStream.createInputStream();
					final var secondIn = secondStream.createInputStream()) {
				return Arrays.equals(firstIn.readAllBytes(), secondIn.readAllBytes());
			} catch (final IOException e) {
				return false;
			}
		}
		if (first instanceof COSDictionary firstDictionary && second instanceof COSDictionary secondDictionary) {
			return sameDictionary(firstDictionary, secondDictionary, compared, false);
		}
		return first.equals(second);
	}

	private static boolean sameDictionary(final COSDictionary first, final COSDictionary second,
			final Map<COSBase, Set<COSBase>> compared, final boolean stream) {
		for (final var key : first.keySet()) {
			if (stream && isStreamEncodingKey(key)) {
				continue;
			}
			if (!second.containsKey(key) || !sameCosObject(first.getItem(key), second.getItem(key), compared)) {
				return false;
			}
		}
		for (final var key : second.keySet()) {
			if (!(stream && isStreamEncodingKey(key)) && !first.containsKey(key)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isStreamEncodingKey(final COSName key) {
		return COSName.LENGTH.equals(key) || COSName.FILTER.equals(key) || COSName.DECODE_PARMS.equals(key)
				|| "DL".equals(key.getName());
	}

	/** R8のPDF/X-1aで禁止される透明機能を検査します。 */
	private static List<Violation> checkR8(final PDDocument document, final Flavour flavour) {
		if (flavour != Flavour.X1A) {
			return List.of();
		}
		final var violations = new ArrayList<Violation>();
		final Set<COSDictionary> visitedResources = Collections.newSetFromMap(new IdentityHashMap<>());
		final Set<COSStream> visitedXObjects = Collections.newSetFromMap(new IdentityHashMap<>());
		var pageNumber = 0;
		for (final var page : document.getPages()) {
			++pageNumber;
			final var location = "ページ" + pageNumber;
			checkTransparencyGroup(page.getCOSObject(), location, violations);
			final var resources = page.getResources() == null ? null : page.getResources().getCOSObject();
			checkTransparencyResources(resources, location, visitedResources, visitedXObjects, violations);
		}
		return violations;
	}

	private static void checkTransparencyResources(final COSDictionary resources, final String location,
			final Set<COSDictionary> visitedResources, final Set<COSStream> visitedXObjects,
			final List<Violation> violations) {
		if (resources == null || !visitedResources.add(resources)) {
			return;
		}
		final var extGStates = asDictionary(resources.getDictionaryObject(COSName.EXT_G_STATE));
		if (extGStates != null) {
			for (final var entry : extGStates.entrySet()) {
				checkTransparencyState(asDictionary(entry.getValue()),
						location + "の/ExtGState/" + entry.getKey().getName(), violations);
			}
		}

		final var patterns = asDictionary(resources.getDictionaryObject(COSName.PATTERN));
		if (patterns != null) {
			for (final var entry : patterns.entrySet()) {
				final var pattern = asDictionary(entry.getValue());
				if (pattern != null) {
					final var patternLocation = location + "の/Pattern/" + entry.getKey().getName();
					checkTransparencyState(asDictionary(pattern.getDictionaryObject(COSName.EXT_G_STATE)),
							patternLocation + "の/ExtGState", violations);
					checkTransparencyResources(asDictionary(pattern.getDictionaryObject(COSName.RESOURCES)),
							patternLocation, visitedResources, visitedXObjects, violations);
				}
			}
		}

		final var xobjects = asDictionary(resources.getDictionaryObject(COSName.XOBJECT));
		if (xobjects != null) {
			for (final var entry : xobjects.entrySet()) {
				final var xobject = asStream(entry.getValue());
				if (xobject == null || !visitedXObjects.add(xobject)) {
					continue;
				}
				final var xobjectLocation = location + "の/XObject/" + entry.getKey().getName();
				if (COSName.IMAGE.equals(xobject.getCOSName(COSName.SUBTYPE))) {
					if (xobject.containsKey(SMASK) && !COSName.NONE.equals(resolve(xobject.getItem(SMASK)))) {
						violations.add(new Violation("R8", xobjectLocation + "に/SMaskがあります"));
					}
				} else if (COSName.FORM.equals(xobject.getCOSName(COSName.SUBTYPE))) {
					checkTransparencyGroup(xobject, xobjectLocation, violations);
					checkTransparencyResources(asDictionary(xobject.getDictionaryObject(COSName.RESOURCES)),
							xobjectLocation, visitedResources, visitedXObjects, violations);
				}
			}
		}

		final var fonts = asDictionary(resources.getDictionaryObject(COSName.FONT));
		if (fonts != null) {
			for (final var entry : fonts.entrySet()) {
				final var font = asDictionary(entry.getValue());
				if (font != null && COSName.TYPE3.equals(font.getCOSName(COSName.SUBTYPE))) {
					checkTransparencyResources(asDictionary(font.getDictionaryObject(COSName.RESOURCES)),
							location + "のType3/" + entry.getKey().getName(), visitedResources,
							visitedXObjects, violations);
				}
			}
		}
	}

	private static void checkTransparencyState(final COSDictionary state, final String location,
			final List<Violation> violations) {
		if (state == null) {
			return;
		}
		if (state.containsKey(SMASK) && !COSName.NONE.equals(resolve(state.getItem(SMASK)))) {
			violations.add(new Violation("R8", location + "の/SMaskが/Noneではありません"));
		}
		if (state.getFloat(CA_NONSTROKE, 1) < 1) {
			violations.add(new Violation("R8", location + "の/caが1未満です"));
		}
		if (state.getFloat(CA_STROKE, 1) < 1) {
			violations.add(new Violation("R8", location + "の/CAが1未満です"));
		}
		if (state.containsKey(BM) && !hasOnlyAllowedBlendModes(state.getItem(BM))) {
			violations.add(new Violation("R8", location + "の/BMが/Normalまたは/Compatibleではありません"));
		}
	}

	private static boolean hasOnlyAllowedBlendModes(final COSBase base) {
		final var blendMode = resolve(base);
		if (blendMode instanceof COSName name) {
			return NORMAL.equals(name) || COMPATIBLE.equals(name);
		}
		if (blendMode instanceof COSArray array && array.size() != 0) {
			for (final var value : array) {
				if (!hasOnlyAllowedBlendModes(value)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	private static void checkTransparencyGroup(final COSDictionary owner, final String location,
			final List<Violation> violations) {
		final var group = asDictionary(owner.getDictionaryObject(GROUP));
		if (group != null && TRANSPARENCY.equals(group.getCOSName(COSName.S))) {
			violations.add(new Violation("R8", location + "に透明グループがあります"));
		}
	}

	/** R9の転送関数、ハーフトーン、外部参照・ファイル仕様を検査します。 */
	private static List<Violation> checkR9(final PDDocument document) {
		final var violations = new ArrayList<Violation>();
		final Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		visitR9(document.getDocument().getTrailer(), visited, violations);
		for (final var key : document.getDocument().getXrefTable().keySet()) {
			visitR9(document.getDocument().getObjectFromPool(key), visited, violations);
		}
		return violations;
	}

	private static void visitR9(final COSBase base, final Set<COSBase> visited,
			final List<Violation> violations) {
		if (base == null || !visited.add(base)) {
			return;
		}
		if (base instanceof COSObject object) {
			visitR9(object.getObject(), visited, violations);
			return;
		}
		if (base instanceof COSDictionary dictionary) {
			checkR9Dictionary(dictionary, violations);
			for (final var value : dictionary.getValues()) {
				visitR9(value, visited, violations);
			}
		} else if (base instanceof COSArray array) {
			for (final var value : array) {
				visitR9(value, visited, violations);
			}
		}
	}

	private static void checkR9Dictionary(final COSDictionary dictionary, final List<Violation> violations) {
		checkDefaultTransfer(dictionary, TR, violations);
		checkDefaultTransfer(dictionary, TR2, violations);
		if (dictionary.containsKey(HTP)) {
			violations.add(new Violation("R9", "/HTPがあります"));
		}
		if (dictionary.containsKey(HALFTONE_TYPE)) {
			final var type = dictionary.getInt(HALFTONE_TYPE, -1);
			if (type != 1 && type != 5) {
				violations.add(new Violation("R9", "/HalftoneTypeが1または5ではありません"));
			}
		}
		if (dictionary.containsKey(HT)) {
			final var halftone = resolve(dictionary.getItem(HT));
			if (halftone instanceof COSName name && !COSName.DEFAULT.equals(name)) {
				violations.add(new Violation("R9", "/HTが/DefaultまたはHalftone辞書ではありません"));
			} else if (halftone instanceof COSDictionary halftoneDictionary
					&& !halftoneDictionary.containsKey(HALFTONE_TYPE)) {
				violations.add(new Violation("R9", "/HTのHalftone辞書に/HalftoneTypeがありません"));
			} else if (!(halftone instanceof COSName) && !(halftone instanceof COSDictionary)) {
				violations.add(new Violation("R9", "/HTが/DefaultまたはHalftone辞書ではありません"));
			}
		}
		final var subtype = dictionary.getCOSName(COSName.SUBTYPE);
		if (PS.equals(subtype)) {
			violations.add(new Violation("R9", "PostScript XObjectがあります"));
		}
		if (dictionary.containsKey(OPI)) {
			violations.add(new Violation("R9", "/OPIがあります"));
		}
		if (COSName.FORM.equals(subtype) && dictionary.containsKey(REF)) {
			violations.add(new Violation("R9", "Form XObjectに/Refがあります"));
		}
		if (dictionary.containsKey(COSName.FS) || dictionary.containsKey(COSName.EF)
				|| COSName.FILESPEC.equals(dictionary.getCOSName(COSName.TYPE))
				|| COSName.FILESPEC.equals(subtype)) {
			violations.add(new Violation("R9", "file specificationがあります"));
		}
	}

	private static void checkDefaultTransfer(final COSDictionary dictionary, final COSName key,
			final List<Violation> violations) {
		if (dictionary.containsKey(key) && !COSName.DEFAULT.equals(resolve(dictionary.getItem(key)))) {
			violations.add(new Violation("R9", "/" + key.getName() + "が/Defaultではありません"));
		}
	}

	/** R13のOptional Content構成を検査します。 */
	private static List<Violation> checkR13(final PDDocument document, final Flavour flavour) {
		final var violations = new ArrayList<Violation>();
		final var catalog = document.getDocumentCatalog().getCOSObject();
		final var propertiesBase = catalog.getItem(OC_PROPERTIES);
		if (propertiesBase == null) {
			return violations;
		}
		if (flavour == Flavour.X1A) {
			violations.add(new Violation("R13", "/OCPropertiesがあります"));
			return violations;
		}
		final var properties = asDictionary(propertiesBase);
		if (properties == null) {
			violations.add(new Violation("R13", "/OCPropertiesが辞書ではありません"));
			return violations;
		}
		final var defaultConfig = asDictionary(properties.getDictionaryObject(DEFAULT_CONFIG));
		if (defaultConfig == null) {
			violations.add(new Violation("R13", "/OCPropertiesに/D既定構成がありません"));
		} else {
			checkOptionalContentIntent(defaultConfig.getItem(INTENT), "/OCProperties/D", violations);
			checkUsageApplication(defaultConfig, "/OCProperties/D", violations);
		}
		if (properties.containsKey(CONFIGS)) {
			final var configs = resolve(properties.getItem(CONFIGS));
			if (!(configs instanceof COSArray array)) {
				violations.add(new Violation("R13", "/OCProperties/Configsが配列ではありません"));
			} else {
				for (var i = 0; i < array.size(); ++i) {
					final var config = asDictionary(array.get(i));
					if (config == null) {
						violations.add(new Violation("R13", "/OCProperties/Configs[" + i + "]が辞書ではありません"));
					} else {
						checkOptionalContentIntent(config.getItem(INTENT),
								"/OCProperties/Configs[" + i + "]", violations);
						checkUsageApplication(config, "/OCProperties/Configs[" + i + "]", violations);
					}
				}
			}
		}
		return violations;
	}

	/** ISO 15930-7 6.24: 構成辞書に/AS(usage application)があってはならない。 */
	private static void checkUsageApplication(final COSDictionary config, final String location,
			final List<Violation> violations) {
		if (config.containsKey(USAGE_APPLICATION)) {
			violations.add(new Violation("R13", location + "に/ASがあります"));
		}
	}

	private static void checkOptionalContentIntent(final COSBase intentBase, final String location,
			final List<Violation> violations) {
		if (intentBase == null) {
			return;
		}
		final var intent = resolve(intentBase);
		if (intent instanceof COSName name) {
			if (!VIEW.equals(name) && !DESIGN.equals(name)) {
				violations.add(new Violation("R13", location + "の/Intentに禁止値/" + name.getName() + "があります"));
			}
			return;
		}
		if (intent instanceof COSArray array) {
			for (final var value : array) {
				checkOptionalContentIntent(value, location, violations);
			}
			return;
		}
		violations.add(new Violation("R13", location + "の/Intentが名前または配列ではありません"));
	}

	private static COSBase resolve(final COSBase base) {
		return base instanceof COSObject object ? object.getObject() : base;
	}

	private static COSDictionary asDictionary(final COSBase base) {
		final var resolved = resolve(base);
		return resolved instanceof COSDictionary dictionary ? dictionary : null;
	}

	private static COSStream asStream(final COSBase base) {
		final var resolved = resolve(base);
		return resolved instanceof COSStream stream ? stream : null;
	}

	private static String nameOrString(final COSBase base) {
		final var resolved = resolve(base);
		if (resolved instanceof COSName name) {
			return name.getName();
		}
		if (resolved instanceof COSString string) {
			return string.getString();
		}
		return null;
	}

	private static boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}

	private static PDRectangle rectangle(final COSBase base) {
		final var resolved = resolve(base);
		if (!(resolved instanceof COSArray array) || array.size() != 4) {
			return null;
		}
		for (var i = 0; i < 4; ++i) {
			if (!(array.getObject(i) instanceof COSNumber)) {
				return null;
			}
		}
		return new PDRectangle(array);
	}

	private static boolean contains(final PDRectangle outer, final PDRectangle inner) {
		return outer.getLowerLeftX() <= inner.getLowerLeftX() + RECT_EPSILON
				&& outer.getLowerLeftY() <= inner.getLowerLeftY() + RECT_EPSILON
				&& outer.getUpperRightX() + RECT_EPSILON >= inner.getUpperRightX()
				&& outer.getUpperRightY() + RECT_EPSILON >= inner.getUpperRightY();
	}

	private static boolean intersects(final PDRectangle first, final PDRectangle second) {
		return Math.min(first.getUpperRightX(), second.getUpperRightX())
				- Math.max(first.getLowerLeftX(), second.getLowerLeftX()) > RECT_EPSILON
				&& Math.min(first.getUpperRightY(), second.getUpperRightY())
						- Math.max(first.getLowerLeftY(), second.getLowerLeftY()) > RECT_EPSILON;
	}
}
