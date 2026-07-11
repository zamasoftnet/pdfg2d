package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.helpers.AttributesImpl;

import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Writes the XMP metadata stream ({@code /Metadata} in the catalog) that
 * mirrors the document information dictionary, as required for PDF/A
 * conformance and recommended for PDF 1.4+.
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
	 * @throws IOException if an I/O error occurs
	 */
	static void write(final PDFFragmentOutputImpl xmpmetaFlow, final PDFParams.Version version, final String author,
			final String creator, final String producer, final String title, final String keywords, final long create,
			final long modify) throws IOException {
		xmpmetaFlow.startHash();

		xmpmetaFlow.writeName("Type");
		xmpmetaFlow.writeName("Metadata");
		xmpmetaFlow.lineBreak();

		xmpmetaFlow.writeName("Subtype");
		xmpmetaFlow.writeName("XML");
		xmpmetaFlow.lineBreak();

		try (final var xout = xmpmetaFlow.startStreamFromHash(PDFFragmentOutput.Mode.RAW)) {
			xout.write("<?xpacket begin='".getBytes(StandardCharsets.UTF_8));
			xout.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
			xout.write("' id='W5M0MpCehiHzreSzNTczkc9d'?>\n".getBytes(StandardCharsets.UTF_8));
			final var handler = ((SAXTransformerFactory) SAXTransformerFactory.newInstance()).newTransformerHandler();
			handler.setResult(new StreamResult(xout));
			final var t = handler.getTransformer();
			t.setOutputProperty(OutputKeys.METHOD, "xml");
			t.setOutputProperty(OutputKeys.INDENT, "yes");
			t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

			final var attsi = new AttributesImpl();
			final var xURI = "adobe:ns:meta/";
			final var rdfURI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
			final var pdfaidURI = "http://www.aiim.org/pdfa/ns/id/";
			final var pdfURI = "http://ns.adobe.com/pdf/1.3/";
			final var dcURI = "http://purl.org/dc/elements/1.1/";
			final var xmpURI = "http://ns.adobe.com/xap/1.0/";

			handler.startDocument();
			attsi.addAttribute("", "x", "xmlns:x", "CDATA", xURI);

			handler.startElement(xURI, "xmpmeta", "x:xmpmeta", attsi);
			attsi.clear();
			attsi.addAttribute("", "rdf", "xmlns:rdf", "CDATA", rdfURI);
			handler.startElement(rdfURI, "RDF", "rdf:RDF", attsi);
			attsi.clear();

			// PDF/A ID
			if (version == PDFParams.Version.V_PDFA1B) {
				attsi.addAttribute("", "pdfaid", "xmlns:pdfaid", "CDATA", pdfaidURI);
				attsi.addAttribute(rdfURI, "about", "rdf:about", "CDATA", "");
				handler.startElement(rdfURI, "Description", "rdf:Description", attsi);
				attsi.clear();
				handler.startElement(pdfaidURI, "part", "pdfaid:part", attsi);
				handler.characters("1".toCharArray(), 0, 1);
				handler.endElement(pdfaidURI, "part", "pdfaid:part");
				handler.startElement(pdfaidURI, "conformance", "pdfaid:conformance", attsi);
				handler.characters("A".toCharArray(), 0, 1);
				handler.endElement(pdfaidURI, "conformance", "pdfaid:conformance");
				handler.endElement(rdfURI, "Description", "rdf:Description");
			}

			// PDF
			attsi.addAttribute("", "pdf", "xmlns:pdf", "CDATA", pdfURI);
			attsi.addAttribute(rdfURI, "about", "rdf:about", "CDATA", "");
			handler.startElement(rdfURI, "Description", "rdf:Description", attsi);
			attsi.clear();
			if (keywords != null) {
				handler.startElement(pdfURI, "Keywords", "pdf:Keywords", attsi);
				handler.characters(keywords.toCharArray(), 0, keywords.length());
				handler.endElement(pdfURI, "Keywords", "pdf:Keywords");
			}
			if (producer != null) {
				handler.startElement(pdfURI, "Producer", "pdf:Producer", attsi);
				handler.characters(producer.toCharArray(), 0, producer.length());
				handler.endElement(pdfURI, "Producer", "pdf:Producer");
			}
			handler.endElement(rdfURI, "Description", "rdf:Description");

			// DC
			attsi.addAttribute(rdfURI, "about", "rdf:about", "CDATA", "");
			attsi.addAttribute("", "dc", "xmlns:dc", "CDATA", dcURI);
			handler.startElement(rdfURI, "Description", "rdf:Description", attsi);
			attsi.clear();

			final String format = "application/pdf";
			handler.startElement(dcURI, "format", "dc:format", attsi);
			handler.characters(format.toCharArray(), 0, format.length());
			handler.endElement(dcURI, "format", "dc:format");

			if (title != null) {
				handler.startElement(dcURI, "title", "dc:title", attsi);
				handler.startElement(rdfURI, "Alt", "rdf:Alt", attsi);
				attsi.addAttribute("", "lang", "xml:lang", "CDATA", "x-default");
				handler.startElement(rdfURI, "li", "rdf:li", attsi);
				attsi.clear();
				handler.characters(title.toCharArray(), 0, title.length());
				handler.endElement(rdfURI, "li", "rdf:li");
				handler.endElement(rdfURI, "Alt", "rdf:Alt");
				handler.endElement(dcURI, "title", "dc:title");
			}

			if (author != null) {
				handler.startElement(dcURI, "creator", "dc:creator", attsi);
				handler.startElement(rdfURI, "Seq", "rdf:Seq", attsi);
				handler.startElement(rdfURI, "li", "rdf:li", attsi);
				handler.characters(author.toCharArray(), 0, author.length());
				handler.endElement(rdfURI, "li", "rdf:li");
				handler.endElement(rdfURI, "Seq", "rdf:Seq");
				handler.endElement(dcURI, "creator", "dc:creator");
			}
			attsi.clear();
			handler.endElement(rdfURI, "Description", "rdf:Description");

			// XMP
			attsi.addAttribute("", "xmp", "xmlns:xmp", "CDATA", xmpURI);
			attsi.addAttribute(rdfURI, "about", "rdf:about", "CDATA", "");
			handler.startElement(rdfURI, "Description", "rdf:Description", attsi);
			attsi.clear();
			if (creator != null) {
				handler.startElement(xmpURI, "CreatorTool", "xmp:CreatorTool", attsi);
				handler.characters(creator.toCharArray(), 0, creator.length());
				handler.endElement(xmpURI, "CreatorTool", "xmp:CreatorTool");
			}
			final var dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
			handler.startElement(xmpURI, "CreateDate", "xmp:CreateDate", attsi);
			final var createStr = dateFormat.format(new Date(create));
			handler.characters(createStr.toCharArray(), 0, createStr.length());
			handler.endElement(xmpURI, "CreateDate", "xmp:CreateDate");
			if (modify != -1L) {
				handler.startElement(xmpURI, "ModifyDate", "xmp:ModifyDate", attsi);
				final var modifyStr = dateFormat.format(new Date(modify));
				handler.characters(modifyStr.toCharArray(), 0, modifyStr.length());
				handler.endElement(xmpURI, "ModifyDate", "xmp:ModifyDate");
			}
			handler.endElement(rdfURI, "Description", "rdf:Description");

			handler.endElement(rdfURI, "RDF", "rdf:RDF");
			handler.endElement(xURI, "xmpmeta", "x:xmpmeta");

			handler.endDocument();
			// The XMP spec recommends 2-4KB of trailing padding so tools can
			// update the packet in place without rewriting the file.
			for (var i = 0; i < 26; ++i) {
				xout.write(XMP_PADDING);
			}
			xout.write("<?xpacket end='w'?>\n".getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		xmpmetaFlow.endObject();
	}
}
