package net.zamasoft.pdfg2d.demo;

import java.awt.Font;
import java.io.File;
import java.io.IOException;

import net.zamasoft.zstream.io.impl.FileFragmentedOutput;
import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.util.PDFUtils;

/**
 * Demonstrates multi-page PDF generation.
 * <p>
 * This app creates a PDF with multiple pages and draws content on each page.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PagesApp {
	/**
	 * Entry point. Generates {@code output/pages.pdf} with two A4 pages, each
	 * labelled with its page number.
	 *
	 * @param args command-line arguments (not used)
	 * @throws IOException if an error occurs during PDF generation
	 */
	public static void main(final String[] args) throws IOException {
		try (final var pdf = new PDFWriterImpl(
				new FileFragmentedOutput(new File(DemoUtils.getOutputDir(), "pages.pdf")),
				PDFParams.createDefault())) {
			try (final var g2d = new PDFGraphics2D(pdf.nextPage(PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM),
					PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM)))) {
				g2d.setFont(new Font(Font.SERIF, Font.PLAIN, 38));
				g2d.drawString("Page 1", 10, 100);
			}
			try (final var g2d = new PDFGraphics2D(pdf.nextPage(PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM),
					PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM)))) {
				g2d.setFont(new Font(Font.SERIF, Font.PLAIN, 38));
				g2d.drawString("Page 2", 10, 100);
			}
		}
	}
}
