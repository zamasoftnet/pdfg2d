package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.paint.SpotColor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.preflight.PdfXPreflight;
import net.zamasoft.pdfg2d.pdf.preflight.PdfXPreflight.Flavour;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** {@link PdfXPreflight} の規則別positive/negative試験です。 */
public class PdfXPreflightTest {
	private static final byte[] FILE_ID = {
			0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x46, 0x77,
			(byte) 0x88, (byte) 0x99, (byte) 0xAA, (byte) 0xBB,
			(byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF
	};
	private static final long CREATE_DATE = 1_757_030_400_000L;

	@FunctionalInterface
	private interface DocumentMutation {
		void apply(PDDocument document) throws Exception;
	}

	private byte[] generate(final Flavour flavour) throws Exception {
		final var meta = new PDFMetaInfo();
		meta.setTitle("PDF/X preflight fixture");
		meta.setCreationDate(CREATE_DATE);
		meta.setModDate(CREATE_DATE + 3_600_000L);
		final var version = flavour == Flavour.X1A ? PDFParams.Version.V_PDFX1A : PDFParams.Version.V_PDFX4;
		final var params = PDFParams.createDefault()
				.withVersion(version)
				.withCompression(PDFParams.Compression.NONE)
				.withFileId(FILE_ID)
				.withMetaInfo(meta);
		final var out = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(out);
		final var pdf = new PDFWriterImpl(builder, params);
		try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
			gc.setFillPaint(RGBColor.create(1, 0, 0));
			gc.fill(new Rectangle2D.Double(50, 50, 200, 100));
			gc.setFillPaint(new LinearGradient(50, 180, 250, 180, new double[] { 0, 1 },
					new Color[] { RGBColor.create(1, 0, 0), RGBColor.create(0, 0, 1) },
					new AffineTransform()));
			gc.fill(new Rectangle2D.Double(50, 180, 200, 80));
			gc.setFillPaint(new LinearGradient(50, 290, 250, 290, new double[] { 0, 1 },
					new Color[] { GrayColor.create(.25f), RGBColor.create(.75f, .75f, .75f) },
					new AffineTransform()));
			gc.fill(new Rectangle2D.Double(50, 290, 200, 80));
			gc.setFillPaint(SpotColor.create("I3 Spot", RGBColor.create(.8f, .1f, .2f)));
			gc.fill(new Rectangle2D.Double(50, 400, 200, 80));

			final var image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
			image.setRGB(0, 0, 0x80FF0000);
			image.setRGB(1, 0, 0xFF00FF00);
			image.setRGB(2, 0, 0xFF0000FF);
			image.setRGB(3, 0, 0xFFFFFFFF);
			gc.drawImage(pdf.addImage(image));

			if (flavour == Flavour.X4) {
				gc.setFillAlpha(.5f);
				gc.setFillPaint(RGBColor.create(0, 0, 1));
				gc.fill(new Rectangle2D.Double(300, 50, 100, 100));
				gc.setFillAlpha(1);
				final var group = pdf.createGroupImage(20, 20);
				try (final var groupGC = new PDFGC(group)) {
					groupGC.setFillPaint(RGBColor.create(0, 1, 0));
					groupGC.fill(new Rectangle2D.Double(0, 0, 20, 20));
				}
				gc.drawImage(group);
				pdf.createOptionalContentGroup("I3 layer", true, true, true, false);
			}
		}
		pdf.close();
		builder.close();
		return out.toByteArray();
	}

	private byte[] mutate(final byte[] pdf, final DocumentMutation mutation) throws Exception {
		try (final var document = Loader.loadPDF(pdf); final var out = new ByteArrayOutputStream()) {
			mutation.apply(document);
			// PDFBoxが保存時にCatalog由来の上位版へヘッダを昇格しないよう、
			// negative対象以外のR1条件を元PDFと同じに保つ。
			document.setVersion(Float.parseFloat(new String(pdf, 5, 3, StandardCharsets.US_ASCII)));
			document.save(out);
			final var result = out.toByteArray();
			result[7] = pdf[7];
			return result;
		}
	}

	private void assertOnlyRule(final byte[] pdf, final Flavour flavour, final String expectedRule) {
		final var violations = PdfXPreflight.check(pdf, flavour);
		assertFalse(violations.isEmpty(), "negative fixture must fail");
		final Set<String> rules = violations.stream().map(PdfXPreflight.Violation::rule).collect(Collectors.toSet());
		assertEquals(Set.of(expectedRule), rules, violations::toString);
	}

	@Test
	public void testX1aPositive() throws Exception {
		PdfXPreflight.assertConforms(generate(Flavour.X1A), Flavour.X1A);
	}

	@Test
	public void testX4Positive() throws Exception {
		PdfXPreflight.assertConforms(generate(Flavour.X4), Flavour.X4);
	}

	@Test
	public void testUnimplementedRulesAreExcluded() {
		assertEquals(List.of(), PdfXPreflight.unimplementedRules());
	}

	@Test
	public void testR1RejectsWrongHeaderVersion() throws Exception {
		final var pdf = generate(Flavour.X1A).clone();
		pdf[7] = '3';
		assertOnlyRule(pdf, Flavour.X1A, "R1");
	}

	@Test
	public void testR2RejectsMissingRegistryName() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> outputIntent(document)
				.removeItem(COSName.REGISTRY_NAME));
		assertOnlyRule(pdf, Flavour.X1A, "R2");
	}

	@Test
	public void testR3RejectsMissingTrapped() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> document.getDocumentInformation().getCOSObject()
				.removeItem(COSName.TRAPPED));
		assertOnlyRule(pdf, Flavour.X1A, "R3");
	}

	@Test
	public void testR4RejectsMissingDocumentId() throws Exception {
		final var pdf = mutate(generate(Flavour.X4), document -> removeXmpProperty(document,
				"http://ns.adobe.com/xap/1.0/mm/", "DocumentID"));
		assertOnlyRule(pdf, Flavour.X4, "R4");
	}

	@Test
	public void testR5RejectsOpenAction() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> document.getDocumentCatalog().getCOSObject()
				.setItem(COSName.OPEN_ACTION, new COSDictionary()));
		assertOnlyRule(pdf, Flavour.X1A, "R5");
	}

	@Test
	public void testR6RejectsFontWithoutEmbeddedProgram() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var resources = document.getPage(0).getResources().getCOSObject();
			var fonts = resources.getCOSDictionary(COSName.FONT);
			if (fonts == null) {
				fonts = new COSDictionary();
				resources.setItem(COSName.FONT, fonts);
			}
			final var font = new COSDictionary();
			font.setItem(COSName.TYPE, COSName.FONT);
			font.setItem(COSName.SUBTYPE, COSName.TRUE_TYPE);
			font.setItem(COSName.BASE_FONT, COSName.getPDFName("PreflightTest"));
			final var descriptor = new COSDictionary();
			descriptor.setItem(COSName.TYPE, COSName.FONT_DESC);
			descriptor.setItem(COSName.FONT_NAME, COSName.getPDFName("PreflightTest"));
			final var fontFile = document.getDocument().createCOSStream();
			try (final var out = fontFile.createOutputStream()) {
				out.write("test font program".getBytes(StandardCharsets.US_ASCII));
			}
			descriptor.setItem(COSName.FONT_FILE2, fontFile);
			font.setItem(COSName.FONT_DESC, descriptor);
			fonts.setItem(COSName.getPDFName("Fnegative"), font);
			// 埋め込み済み辞書からFontFile2だけを除去してnegativeを作る。
			descriptor.removeItem(COSName.FONT_FILE2);
		});
		assertOnlyRule(pdf, Flavour.X1A, "R6");
	}

	@Test
	public void testR7RejectsDeviceRgbOperator() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var contents = document.getPage(0).getCOSObject().getCOSStream(COSName.CONTENTS);
			assertNotNull(contents);
			try (final var out = contents.createOutputStream()) {
				out.write("1 0 0 rg 50 50 200 100 re f\n".getBytes(StandardCharsets.US_ASCII));
			}
		});
		assertOnlyRule(pdf, Flavour.X1A, "R7");
	}

	@Test
	public void testR7RejectsConflictingSeparationDefinitions() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var resources = document.getPage(0).getResources().getCOSObject();
			var colorSpaces = resources.getCOSDictionary(COSName.COLORSPACE);
			if (colorSpaces == null) {
				colorSpaces = new COSDictionary();
				resources.setItem(COSName.COLORSPACE, colorSpaces);
			}
			colorSpaces.setItem(COSName.getPDFName("Conflict1"),
					separation("Conflict Spot", COSName.DEVICECMYK, new float[] { 0, 1, 1, 0 }));
			colorSpaces.setItem(COSName.getPDFName("Conflict2"),
					separation("Conflict Spot", COSName.DEVICECMYK, new float[] { 1, 0, 0, 0 }));
		});
		assertOnlyRule(pdf, Flavour.X1A, "R7");
	}

	@Test
	public void testR7RejectsRgbImageInX1a() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> firstImage(document)
				.setItem(COSName.COLORSPACE, COSName.DEVICERGB));
		assertOnlyRule(pdf, Flavour.X1A, "R7");
	}

	@Test
	public void testR7RejectsMissingDefaultRgbInX4Resources() throws Exception {
		final var pdf = mutate(generate(Flavour.X4), document -> document.getPage(0).getResources().getCOSObject()
				.getCOSDictionary(COSName.COLORSPACE).removeItem(COSName.getPDFName("DefaultRGB")));
		assertOnlyRule(pdf, Flavour.X4, "R7");
	}

	@Test
	public void testR7RejectsNonGrayImageSoftMask() throws Exception {
		final var pdf = mutate(generate(Flavour.X4), document -> {
			final var softMask = (COSStream) firstImage(document).getDictionaryObject(COSName.getPDFName("SMask"));
			assertNotNull(softMask);
			softMask.setItem(COSName.COLORSPACE, COSName.DEVICERGB);
		});
		assertOnlyRule(pdf, Flavour.X4, "R7");
	}

	@Test
	public void testR7RejectsDeviceRgbGroupColorSpace() throws Exception {
		final var pdf = mutate(generate(Flavour.X4), document -> {
			final var group = new COSDictionary();
			group.setItem(COSName.getPDFName("CS"), COSName.DEVICERGB);
			document.getPage(0).getCOSObject().setItem(COSName.getPDFName("Group"), group);
		});
		assertOnlyRule(pdf, Flavour.X4, "R7");
	}

	@Test
	public void testR8RejectsTransparentExtGState() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var resources = document.getPage(0).getResources().getCOSObject();
			var states = resources.getCOSDictionary(COSName.EXT_G_STATE);
			if (states == null) {
				states = new COSDictionary();
				resources.setItem(COSName.EXT_G_STATE, states);
			}
			final var state = new COSDictionary();
			state.setFloat(COSName.getPDFName("ca"), .5f);
			state.setItem(COSName.getPDFName("BM"), COSName.getPDFName("Multiply"));
			states.setItem(COSName.getPDFName("GStransparent"), state);
		});
		assertOnlyRule(pdf, Flavour.X1A, "R8");
	}

	@Test
	public void testR8RejectsTransparencyGroup() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var form = document.getDocument().createCOSStream();
			form.setItem(COSName.TYPE, COSName.XOBJECT);
			form.setItem(COSName.SUBTYPE, COSName.FORM);
			form.setItem(COSName.RESOURCES, new COSDictionary());
			final var group = new COSDictionary();
			group.setItem(COSName.S, COSName.getPDFName("Transparency"));
			form.setItem(COSName.getPDFName("Group"), group);
			try (final var out = form.createOutputStream()) {
				out.write("q Q\n".getBytes(StandardCharsets.US_ASCII));
			}
			document.getPage(0).getResources().getCOSObject().getCOSDictionary(COSName.XOBJECT)
					.setItem(COSName.getPDFName("Ttransparent"), form);
		});
		assertOnlyRule(pdf, Flavour.X1A, "R8");
	}

	@Test
	public void testR8RejectsImageSoftMask() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var softMask = document.getDocument().createCOSStream();
			softMask.setItem(COSName.TYPE, COSName.XOBJECT);
			softMask.setItem(COSName.SUBTYPE, COSName.IMAGE);
			softMask.setInt(COSName.WIDTH, 4);
			softMask.setInt(COSName.HEIGHT, 2);
			softMask.setInt(COSName.BITS_PER_COMPONENT, 8);
			softMask.setItem(COSName.COLORSPACE, COSName.DEVICEGRAY);
			try (final var out = softMask.createOutputStream()) {
				out.write(new byte[8]);
			}
			firstImage(document).setItem(COSName.getPDFName("SMask"), softMask);
		});
		assertOnlyRule(pdf, Flavour.X1A, "R8");
	}

	@Test
	public void testR7RejectsIccBasedCmykImageInX1a() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var profile = document.getDocument().createCOSStream();
			profile.setInt(COSName.N, 4);
			try (final var out = profile.createOutputStream()) {
				out.write(new byte[128]);
			}
			final var colorSpace = new COSArray();
			colorSpace.add(COSName.getPDFName("ICCBased"));
			colorSpace.add(profile);
			firstImage(document).setItem(COSName.COLORSPACE, colorSpace);
		});
		assertOnlyRule(pdf, Flavour.X1A, "R7");
	}

	@Test
	public void testR7RejectsRgbGroupInSoftMaskForm() throws Exception {
		final var pdf = mutate(generate(Flavour.X4), document -> {
			final var form = document.getDocument().createCOSStream();
			form.setItem(COSName.TYPE, COSName.XOBJECT);
			form.setItem(COSName.SUBTYPE, COSName.FORM);
			form.setItem(COSName.RESOURCES, new COSDictionary());
			final var group = new COSDictionary();
			group.setItem(COSName.S, COSName.getPDFName("Transparency"));
			group.setItem(COSName.getPDFName("CS"), COSName.DEVICERGB);
			form.setItem(COSName.getPDFName("Group"), group);
			try (final var out = form.createOutputStream()) {
				out.write("q Q\n".getBytes(StandardCharsets.US_ASCII));
			}
			final var softMask = new COSDictionary();
			softMask.setItem(COSName.S, COSName.getPDFName("Luminosity"));
			softMask.setItem(COSName.G, form);
			final var state = new COSDictionary();
			state.setItem(COSName.getPDFName("SMask"), softMask);
			final var resources = document.getPage(0).getResources().getCOSObject();
			var states = resources.getCOSDictionary(COSName.EXT_G_STATE);
			if (states == null) {
				states = new COSDictionary();
				resources.setItem(COSName.EXT_G_STATE, states);
			}
			states.setItem(COSName.getPDFName("GSsoftmask"), state);
		});
		assertOnlyRule(pdf, Flavour.X4, "R7");
	}

	@Test
	public void testR8RejectsShadingPatternExtGState() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var patterns = document.getPage(0).getResources().getCOSObject().getCOSDictionary(COSName.PATTERN);
			assertNotNull(patterns);
			final var state = new COSDictionary();
			state.setFloat(COSName.getPDFName("ca"), .5f);
			patterns.getCOSDictionary(patterns.keySet().iterator().next()).setItem(COSName.EXT_G_STATE, state);
		});
		assertOnlyRule(pdf, Flavour.X1A, "R8");
	}

	@Test
	public void testR13RejectsUsageApplicationInX4() throws Exception {
		final var pdf = mutate(generate(Flavour.X4), document -> {
			final var properties = document.getDocumentCatalog().getCOSObject()
					.getCOSDictionary(COSName.getPDFName("OCProperties"));
			assertNotNull(properties);
			properties.getCOSDictionary(COSName.getPDFName("D")).setItem(COSName.getPDFName("AS"), new COSArray());
		});
		assertOnlyRule(pdf, Flavour.X4, "R13");
	}

	@Test
	public void testR13RejectsOptionalContentInX1a() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> document.getDocumentCatalog().getCOSObject()
				.setItem(COSName.getPDFName("OCProperties"), new COSDictionary()));
		assertOnlyRule(pdf, Flavour.X1A, "R13");
	}

	@Test
	public void testR13RejectsForbiddenConfigurationIntent() throws Exception {
		final var pdf = mutate(generate(Flavour.X4), document -> {
			final var properties = document.getDocumentCatalog().getCOSObject()
					.getCOSDictionary(COSName.getPDFName("OCProperties"));
			assertNotNull(properties);
			properties.getCOSDictionary(COSName.getPDFName("D"))
					.setItem(COSName.getPDFName("Intent"), COSName.getPDFName("Print"));
		});
		assertOnlyRule(pdf, Flavour.X4, "R13");
	}

	@Test
	public void testR9RejectsNonDefaultTransferFunction() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var resources = document.getPage(0).getResources().getCOSObject();
			var extGStates = resources.getCOSDictionary(COSName.EXT_G_STATE);
			if (extGStates == null) {
				extGStates = new COSDictionary();
				resources.setItem(COSName.EXT_G_STATE, extGStates);
			}
			final var state = new COSDictionary();
			state.setItem(COSName.getPDFName("TR"), COSName.getPDFName("Identity"));
			extGStates.setItem(COSName.getPDFName("GSnegative"), state);
		});
		assertOnlyRule(pdf, Flavour.X1A, "R9");
	}

	@Test
	public void testR10RejectsTrimAndArtTogether() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var page = document.getPage(0);
			page.setArtBox(new PDRectangle(page.getTrimBox().getCOSArray()));
		});
		assertOnlyRule(pdf, Flavour.X1A, "R10");
	}

	@Test
	public void testR11RejectsAnnotationInsideFinishedPage() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var annotation = new PDAnnotationText();
			annotation.setRectangle(new PDRectangle(100, 100, 40, 20));
			document.getPage(0).getAnnotations().add(annotation);
		});
		assertOnlyRule(pdf, Flavour.X1A, "R11");
	}

	@Test
	public void testR12RejectsLzwDecode() throws Exception {
		final var pdf = mutate(generate(Flavour.X1A), document -> {
			final var contents = document.getPage(0).getCOSObject().getCOSStream(COSName.CONTENTS);
			assertNotNull(contents);
			contents.setItem(COSName.FILTER, COSName.LZW_DECODE);
		});
		assertOnlyRule(pdf, Flavour.X1A, "R12");
	}

	private static COSDictionary outputIntent(final PDDocument document) {
		return (COSDictionary) document.getDocumentCatalog().getCOSObject().getCOSArray(COSName.OUTPUT_INTENTS)
				.getObject(0);
	}

	private static COSStream firstImage(final PDDocument document) {
		final var xobjects = document.getPage(0).getResources().getCOSObject().getCOSDictionary(COSName.XOBJECT);
		for (final var name : xobjects.keySet()) {
			final var value = xobjects.getDictionaryObject(name);
			if (value instanceof COSStream stream && COSName.IMAGE.equals(stream.getCOSName(COSName.SUBTYPE))) {
				return stream;
			}
		}
		throw new AssertionError("image XObject is missing");
	}

	private static COSArray separation(final String name, final COSName alternate, final float[] full) {
		final var function = new COSDictionary();
		function.setInt(COSName.getPDFName("FunctionType"), 2);
		function.setItem(COSName.getPDFName("Domain"), numbers(0, 1));
		function.setInt(COSName.N, 1);
		function.setItem(COSName.getPDFName("C0"), new COSArray(java.util.Collections.nCopies(full.length,
				COSInteger.ZERO)));
		function.setItem(COSName.getPDFName("C1"), numbers(full));

		final var separation = new COSArray();
		separation.add(COSName.getPDFName("Separation"));
		separation.add(COSName.getPDFName(name));
		separation.add(alternate);
		separation.add(function);
		return separation;
	}

	private static COSArray numbers(final float... values) {
		final var array = new COSArray();
		for (final var value : values) {
			array.add(new COSFloat(value));
		}
		return array;
	}

	private static void removeXmpProperty(final PDDocument document, final String namespace,
			final String localName) throws Exception {
		final var metadata = document.getDocumentCatalog().getMetadata();
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		final org.w3c.dom.Document xmp;
		try (final var in = metadata.exportXMPMetadata()) {
			xmp = factory.newDocumentBuilder().parse(in);
		}
		final var nodes = xmp.getElementsByTagNameNS(namespace, localName);
		assertEquals(1, nodes.getLength());
		nodes.item(0).getParentNode().removeChild(nodes.item(0));

		final var out = new ByteArrayOutputStream();
		final var transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.transform(new DOMSource(xmp), new StreamResult(out));
		metadata.importXMPMetadata(out.toByteArray());
	}
}
