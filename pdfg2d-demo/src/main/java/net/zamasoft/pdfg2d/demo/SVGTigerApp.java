package net.zamasoft.pdfg2d.demo;

import java.io.File;
import java.net.URI;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGOMSVGElement;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.svg.SVGDocument;

import net.zamasoft.zstream.io.impl.FileFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.util.PDFUtils;
import net.zamasoft.pdfg2d.svg.SVGBridgeGraphics2D;
import net.zamasoft.pdfg2d.svg.SVGImage;

/**
 * Demonstrates rendering of a complex SVG image (Ghostscript Tiger) fetched
 * from a URL.
 * <p>
 * This app downloads the Ghostscript Tiger SVG from Wikimedia Commons
 * and renders it into a PDF page.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class SVGTigerApp {
	/**
	 * Entry point. Downloads the Ghostscript Tiger SVG from Wikimedia Commons and
	 * generates {@code output/svg-tiger.pdf}.
	 *
	 * @param args command-line arguments (not used)
	 * @throws Exception if the SVG cannot be fetched or the PDF cannot be written
	 */
	public static void main(final String[] args) throws Exception {
		final var url = "https://upload.wikimedia.org/wikipedia/commons/f/fd/Ghostscript_Tiger.svg";
		final var parser = XMLResourceDescriptor.getXMLParserClassName();
		final var f = new SAXSVGDocumentFactory(parser);
		final SVGDocument doc;
		try (final var in = URI.create(url).toURL().openStream()) {
			doc = f.createSVGDocument(url, in);
		}
		final var root = (SVGOMSVGElement) doc.getDocumentElement();
		final var width = String.valueOf(PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM));
		root.setAttribute("width", width);
		root.setAttribute("height", width);
		final var gvt = new GVTBuilder();
		final var ctx = new BridgeContext(new UserAgentAdapter());
		final var gvtRoot = gvt.build(ctx, doc);
		final var dim = ctx.getDocumentSize();
		final var image = new SVGImage(gvtRoot, dim.getWidth(), dim.getHeight());

		try (final var pdf = new PDFWriterImpl(
				new FileFragmentedOutput(new File(DemoUtils.getOutputDir(), "svg-tiger.pdf")));
				final var gc = new PDFGC(pdf.nextPage(PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM),
						PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM)))) {
			gc.drawImage(image);
		}
	}
}
