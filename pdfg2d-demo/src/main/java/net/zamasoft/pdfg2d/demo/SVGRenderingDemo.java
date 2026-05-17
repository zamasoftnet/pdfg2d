package net.zamasoft.pdfg2d.demo;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.GZIPInputStream;

import javax.swing.JFrame;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.w3c.dom.Document;

import net.zamasoft.zstream.resolver.protocol.file.FileSource;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.g2d.gc.G2DGC;

import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.svg.SVGImage;

/**
 * Demonstrates rendering of SVG content into a PDF.
 * <p>
 * This demo loads a compressed SVG file (.svgz), parses it using Apache Batik,
 * and renders it into the PDF page using the {@link PDFGC}.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class SVGRenderingDemo {
	/**
	 * Entry point. Loads {@code flower.svgz} from the classpath resources,
	 * generates {@code output/svg.pdf}, and opens a Swing preview window.
	 *
	 * @param args command-line arguments (not used)
	 * @throws Exception if the SVG cannot be read or the PDF cannot be written
	 */
	public static void main(final String[] args) throws Exception {
		final var params = PDFParams.createDefault();
		final var width = 300.0;
		final var height = 300.0;

		try (final var out = new BufferedOutputStream(
				new FileOutputStream(new File(DemoUtils.getOutputDir(), "svg.pdf")))) {
			final var builder = new StreamFragmentedOutput(out);
			final PDFWriter pdf = new PDFWriterImpl(builder, params);

			final var svg = loadSampleSvgImage();

			try (final var page = pdf.nextPage(width, height);
					final var gc = new PDFGC(page)) {
				gc.drawImage(svg);
			}

			final var frame = new JFrame("Graphics") {
				private static final long serialVersionUID = 1L;

				@Override
				public void paint(final Graphics g) {
					super.paint(g);
					final var g2d = (Graphics2D) g;
					final var gc = new G2DGC(g2d, pdf.getFontManager());
					try {
						gc.drawImage(svg);
					} catch (final Exception e) {
						e.printStackTrace();
					}
				}
			};
			frame.setSize((int) svg.getWidth(), (int) svg.getHeight());
			frame.setVisible(true);
			pdf.close();
			builder.close();
		}
	}

	static SVGImage loadSampleSvgImage() throws Exception {
		final var userAgent = new UserAgentAdapter();
		final var loader = new DocumentLoader(userAgent);
		final var svgFile = DemoUtils.getResourceFile("flower.svgz");
		try (final var source = new FileSource(svgFile)) {
			final Document doc;
			try (final var in = new GZIPInputStream(source.getInputStream())) {
				doc = loader.loadDocument(source.getURI().toString(), in);
			}
			final var ctx = new BridgeContext(userAgent);
			ctx.setDynamicState(BridgeContext.STATIC);
			final var gvtbuilder = new GVTBuilder();
			final var gvtRoot = gvtbuilder.build(ctx, doc);
			final var dim = ctx.getDocumentSize();
			return new SVGImage(gvtRoot, dim.getWidth(), dim.getHeight());
		}
	}
}
