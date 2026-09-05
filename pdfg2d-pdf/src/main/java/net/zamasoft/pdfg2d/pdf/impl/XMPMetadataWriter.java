package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HexFormat;

import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Writes the XMP metadata stream ({@code /Metadata} in the catalog) that
 * mirrors the document information dictionary, as required for PDF/A
 * conformance and recommended for PDF 1.4+.
 * <p>
 * The packet is serialized by hand rather than through
 * {@code javax.xml.transform}: an XMP packet is an XML <em>fragment</em>
 * wrapped in {@code <?xpacket?>} processing instructions and must not carry
 * an XML declaration, but the JDK identity transformer emits one regardless
 * of {@code OMIT_XML_DECLARATION}, which makes validators reject the packet.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
final class XMPMetadataWriter {

	private static final byte[] XMP_PADDING;

	static {
		XMP_PADDING = new byte[80];
		java.util.Arrays.fill(XMP_PADDING, (byte) ' ');
		XMP_PADDING[79] = '\n';
	}

	private XMPMetadataWriter() {
		// static use only
	}

	/** trailerの第1ファイルIDをXMPのUUID表記へ変換します。 */
	private static String documentId(final byte[] fileId) {
		final var hex = HexFormat.of().formatHex(fileId);
		return "uuid:" + hex.substring(0, 8) + '-' + hex.substring(8, 12) + '-'
				+ hex.substring(12, 16) + '-' + hex.substring(16, 20) + '-' + hex.substring(20);
	}

	/** Escapes text for use in XML element content. */
	private static String xml(final String s) {
		final var sb = new StringBuilder(s.length() + 16);
		for (var i = 0; i < s.length(); ++i) {
			final char c = s.charAt(i);
			switch (c) {
				case '&' -> sb.append("&amp;");
				case '<' -> sb.append("&lt;");
				case '>' -> sb.append("&gt;");
				default -> sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * Appends the PDF/A extension-schema description for the Factur-X
	 * {@code fx:} namespace so that PDF/A validators accept its custom
	 * properties (ISO 19005 6.6.2.3).
	 */
	private static void appendFacturXExtensionSchema(final StringBuilder sb) {
		sb.append("  <rdf:Description rdf:about=\"\"")
				.append(" xmlns:pdfaExtension=\"http://www.aiim.org/pdfa/ns/extension/\"")
				.append(" xmlns:pdfaSchema=\"http://www.aiim.org/pdfa/ns/schema#\"")
				.append(" xmlns:pdfaProperty=\"http://www.aiim.org/pdfa/ns/property#\">\n");
		sb.append("   <pdfaExtension:schemas>\n");
		sb.append("    <rdf:Bag>\n");
		sb.append("     <rdf:li rdf:parseType=\"Resource\">\n");
		sb.append("      <pdfaSchema:schema>Factur-X PDFA Extension Schema</pdfaSchema:schema>\n");
		sb.append("      <pdfaSchema:namespaceURI>urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#")
				.append("</pdfaSchema:namespaceURI>\n");
		sb.append("      <pdfaSchema:prefix>fx</pdfaSchema:prefix>\n");
		sb.append("      <pdfaSchema:property>\n");
		sb.append("       <rdf:Seq>\n");
		appendExtensionProperty(sb, "DocumentFileName", "Text",
				"The name of the embedded XML invoice file");
		appendExtensionProperty(sb, "DocumentType", "Text", "INVOICE or ORDER");
		appendExtensionProperty(sb, "Version", "Text", "The actual version of the Factur-X data");
		appendExtensionProperty(sb, "ConformanceLevel", "Text", "The conformance level of the embedded data");
		sb.append("       </rdf:Seq>\n");
		sb.append("      </pdfaSchema:property>\n");
		sb.append("     </rdf:li>\n");
		sb.append("    </rdf:Bag>\n");
		sb.append("   </pdfaExtension:schemas>\n");
		sb.append("  </rdf:Description>\n");
	}

	/** Appends one pdfaProperty entry to a Factur-X extension schema. */
	private static void appendExtensionProperty(final StringBuilder sb, final String name, final String valueType,
			final String description) {
		sb.append("        <rdf:li rdf:parseType=\"Resource\">\n");
		sb.append("         <pdfaProperty:name>").append(name).append("</pdfaProperty:name>\n");
		sb.append("         <pdfaProperty:valueType>").append(valueType).append("</pdfaProperty:valueType>\n");
		sb.append("         <pdfaProperty:category>external</pdfaProperty:category>\n");
		sb.append("         <pdfaProperty:description>").append(xml(description))
				.append("</pdfaProperty:description>\n");
		sb.append("        </rdf:li>\n");
	}

	/**
	 * Writes the complete metadata object (dictionary and XMP packet stream) to
	 * the given flow. The flow must be positioned at the start of the object
	 * body; this method closes the object.
	 *
	 * @param xmpmetaFlow the fragment holding the metadata object
	 * @param version     the target PDF version (drives PDF/A identification)
	 * @param author      document author, or {@code null}
	 * @param creator     creating application, or {@code null}
	 * @param producer    producing library, or {@code null}
	 * @param title       document title, or {@code null}
	 * @param keywords    document keywords, or {@code null}
	 * @param create      creation timestamp in epoch milliseconds
	 * @param modify      modification timestamp in epoch milliseconds, or
	 *                    {@code -1} to omit
	 * @param fileId      trailerの第1ファイルID(16バイト)
	 * @throws IOException if an I/O error occurs
	 */
	static void write(final PDFFragmentOutputImpl xmpmetaFlow, final PDFParams.Version version, final int pdfuaPart,
			final String author, final String creator, final String producer, final String title,
			final String keywords, final long create, final long modify,
			final net.zamasoft.pdfg2d.pdf.FacturX facturX, final byte[] fileId) throws IOException {
		xmpmetaFlow.startHash();

		xmpmetaFlow.writeName("Type");
		xmpmetaFlow.writeName("Metadata");
		xmpmetaFlow.lineBreak();

		xmpmetaFlow.writeName("Subtype");
		xmpmetaFlow.writeName("XML");
		xmpmetaFlow.lineBreak();

		// The metadata stream must stay uncompressed (Mode.RAW) so that
		// non-PDF-aware tools can locate the XMP packet.
		try (final var xout = xmpmetaFlow.startStreamFromHash(PDFFragmentOutput.Mode.RAW)) {
			xout.write("<?xpacket begin='".getBytes(StandardCharsets.UTF_8));
			xout.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
			xout.write("' id='W5M0MpCehiHzreSzNTczkc9d'?>\n".getBytes(StandardCharsets.UTF_8));

			final var sb = new StringBuilder(2048);
			sb.append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n");
			sb.append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n");

			// PDF/A identification schema
			if (version.isPdfA()) {
				sb.append("  <rdf:Description rdf:about=\"\"")
						.append(" xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\">\n");
				sb.append("   <pdfaid:part>").append(version.pdfaPart()).append("</pdfaid:part>\n");
				if (version.pdfaPart() >= 4) {
					// PDF/A-4 identifies the standard's revision year instead
					// of a conformance level (except the E/F variants).
					sb.append("   <pdfaid:rev>2020</pdfaid:rev>\n");
				}
				if (version.pdfaConformance() != null) {
					sb.append("   <pdfaid:conformance>").append(version.pdfaConformance())
							.append("</pdfaid:conformance>\n");
				}
				sb.append("  </rdf:Description>\n");
			}

			// PDF/UA identification schema
			if (pdfuaPart > 0) {
				sb.append("  <rdf:Description rdf:about=\"\"")
						.append(" xmlns:pdfuaid=\"http://www.aiim.org/pdfua/ns/id/\">\n");
				sb.append("   <pdfuaid:part>").append(pdfuaPart).append("</pdfuaid:part>\n");
				if (pdfuaPart >= 2) {
					// PDF/UA-2 (ISO 14289-2:2024) identifies the standard's
					// revision year, like PDF/A-4.
					sb.append("   <pdfuaid:rev>2024</pdfuaid:rev>\n");
				}
				sb.append("  </rdf:Description>\n");
			}

			// PDF/X identification schema (required by PDF/X-4 and later;
			// harmless and recommended for PDF/X-1a)
			if (version.isPdfX()) {
				sb.append("  <rdf:Description rdf:about=\"\"")
						.append(" xmlns:pdfxid=\"http://www.npes.org/pdfx/ns/id/\">\n");
				sb.append("   <pdfxid:GTS_PDFXVersion>").append(xml(version.pdfxVersion()))
						.append("</pdfxid:GTS_PDFXVersion>\n");
				sb.append("  </rdf:Description>\n");
			}

			// Adobe PDF schema
			if (version.isPdfX() || keywords != null || producer != null) {
				sb.append("  <rdf:Description rdf:about=\"\"")
						.append(" xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">\n");
				if (keywords != null) {
					sb.append("   <pdf:Keywords>").append(xml(keywords)).append("</pdf:Keywords>\n");
				}
				if (producer != null) {
					sb.append("   <pdf:Producer>").append(xml(producer)).append("</pdf:Producer>\n");
				}
				if (version.isPdfX()) {
					sb.append("   <pdf:Trapped>False</pdf:Trapped>\n");
				}
				sb.append("  </rdf:Description>\n");
			}

			// Dublin Core schema
			sb.append("  <rdf:Description rdf:about=\"\"")
					.append(" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
			sb.append("   <dc:format>application/pdf</dc:format>\n");
			if (title != null) {
				sb.append("   <dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">").append(xml(title))
						.append("</rdf:li></rdf:Alt></dc:title>\n");
			}
			if (author != null) {
				sb.append("   <dc:creator><rdf:Seq><rdf:li>").append(xml(author))
						.append("</rdf:li></rdf:Seq></dc:creator>\n");
			}
			sb.append("  </rdf:Description>\n");

			// XMP basic schema
			sb.append("  <rdf:Description rdf:about=\"\"")
					.append(" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n");
			if (creator != null) {
				sb.append("   <xmp:CreatorTool>").append(xml(creator)).append("</xmp:CreatorTool>\n");
			}
			final var dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
			final var createDate = dateFormat.format(new Date(create));
			sb.append("   <xmp:CreateDate>").append(createDate)
					.append("</xmp:CreateDate>\n");
			if (modify != -1L) {
				sb.append("   <xmp:ModifyDate>").append(dateFormat.format(new Date(modify)))
						.append("</xmp:ModifyDate>\n");
			}
			if (version.isPdfX()) {
				sb.append("   <xmp:MetadataDate>").append(createDate).append("</xmp:MetadataDate>\n");
			}
			sb.append("  </rdf:Description>\n");

			// XMP Media Management schema (PDF/X-4必須プロパティ)
			if (version.isPdfX()) {
				sb.append("  <rdf:Description rdf:about=\"\"")
						.append(" xmlns:xmpMM=\"http://ns.adobe.com/xap/1.0/mm/\">\n");
				sb.append("   <xmpMM:DocumentID>").append(documentId(fileId))
						.append("</xmpMM:DocumentID>\n");
				sb.append("   <xmpMM:VersionID>1</xmpMM:VersionID>\n");
				sb.append("   <xmpMM:RenditionClass>default</xmpMM:RenditionClass>\n");
				sb.append("  </rdf:Description>\n");
			}

			// Factur-X / ZUGFeRD electronic-invoice schema. The fx: properties
			// tell an e-invoice reader which embedded file holds the structured
			// invoice XML and which profile it conforms to.
			if (facturX != null) {
				sb.append("  <rdf:Description rdf:about=\"\"")
						.append(" xmlns:fx=\"urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#\">\n");
				sb.append("   <fx:DocumentType>").append(xml(facturX.documentType()))
						.append("</fx:DocumentType>\n");
				sb.append("   <fx:DocumentFileName>").append(xml(facturX.documentFileName()))
						.append("</fx:DocumentFileName>\n");
				sb.append("   <fx:Version>").append(xml(facturX.version())).append("</fx:Version>\n");
				sb.append("   <fx:ConformanceLevel>").append(xml(facturX.conformanceLevel()))
						.append("</fx:ConformanceLevel>\n");
				sb.append("  </rdf:Description>\n");

				// PDF/A extension schema: PDF/A validators reject XMP properties
				// from namespaces they do not know unless the document declares
				// the schema here (ISO 19005 6.6.2.3).
				appendFacturXExtensionSchema(sb);
			}

			sb.append(" </rdf:RDF>\n");
			sb.append("</x:xmpmeta>\n");
			xout.write(sb.toString().getBytes(StandardCharsets.UTF_8));

			// The XMP spec recommends 2-4KB of trailing padding so tools can
			// update the packet in place without rewriting the file.
			for (var i = 0; i < 26; ++i) {
				xout.write(XMP_PADDING);
			}
			xout.write("<?xpacket end='w'?>\n".getBytes(StandardCharsets.UTF_8));
		}
		xmpmetaFlow.endObject();
	}
}
