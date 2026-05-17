package net.zamasoft.pdfg2d.demo;

import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.zstream.io.impl.AbstractTempFileOutput;
import net.zamasoft.zstream.io.impl.FileFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import org.apache.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Demonstrates the performance of recording PDF content using PdfBoxGraphics2D.
 * <p>
 * This demo downloads a large PDF report (OECD Education at a Glance 2024),
 * renders each page onto a PdfBoxGraphics2D context, and saves the result as a
 * new PDF.
 * This effectively measures the time taken to re-record PDF drawing commands.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PdfBoxGraphics2dPerformanceDemo {
    private static final String PDF_URL = "https://www.oecd.org/content/dam/oecd/en/publications/reports/2024/09/education-at-a-glance-2024_5ea68448/c00cad36-en.pdf";
    private static final String FILE_NAME = "education-at-a-glance-2024.pdf";

    /**
     * Entry point. Downloads the OECD report PDF if not already cached and runs
     * the zamasoft PDFGraphics2D performance measurement (the rototor variant is
     * commented out but available for comparison).
     *
     * @param args command-line arguments (not used)
     * @throws IOException if the PDF cannot be downloaded or written
     */
    public static void main(final String[] args) throws IOException {
        final var file = new File(DemoUtils.getOutputDir(), FILE_NAME);
        if (!file.exists()) {
            System.out.println("Downloading " + PDF_URL + "...");
            try (final var in = java.net.URI.create(PDF_URL).toURL().openStream()) {
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("Downloaded to " + file.getAbsolutePath());
        } else {
            System.out.println("Using existing file " + file.getAbsolutePath());
        }

        // measureRototorPdfBoxGraphics2D(file);
        System.out.println("--------------------------------------------------");
        measureZamasoftPDFGraphics2D(file);
    }

    /**
     * Measures PDF re-recording performance using
     * {@code de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D}.
     * <p>
     * Each page of the source document is rendered into a
     * {@code PdfBoxGraphics2D} context and the resulting XForm object is placed
     * onto a new destination page.
     * </p>
     *
     * @param file the source PDF file to process
     * @throws IOException if the source cannot be read or the output cannot be saved
     */
    private static void measureRototorPdfBoxGraphics2D(final File file) throws IOException {
        System.out.println("Measuring de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D...");
        final var outFile = new File(DemoUtils.getOutputDir(), "performance-test-rototor.pdf");

        try (final var sourceDoc = Loader.loadPDF(file)) {
            final var renderer = new PDFRenderer(sourceDoc);
            final int pageCount = sourceDoc.getNumberOfPages();
            System.out.println("Starting rendering of " + pageCount + " pages...");
            final long startTime = System.currentTimeMillis();

            try (final var destDoc = new PDDocument()) {

                for (int i = 0; i < pageCount; i++) {
                    final var sourcePage = sourceDoc.getPage(i);
                    final var mediaBox = sourcePage.getMediaBox();

                    // Create graphics
                    final var g2d = new PdfBoxGraphics2D(destDoc, mediaBox.getWidth(), mediaBox.getHeight());

                    // Render source to graphics
                    renderer.renderPageToGraphics(i, g2d);
                    g2d.dispose();

                    // Create form object from the recorded graphics
                    final var xFormObject = g2d.getXFormObject();

                    // Create destination page with the same size
                    final var destPage = new PDPage(mediaBox);
                    destDoc.addPage(destPage);

                    // Draw the form object onto the destination page
                    try (final var contentStream = new PDPageContentStream(destDoc, destPage)) {
                        contentStream.drawForm(xFormObject);
                    }

                    System.out.print("\rRototor Rendered page " + (i + 1) + "/" + pageCount);
                }
                System.out.println(); // Newline after progress

                destDoc.save(outFile);
            }
            final long endTime = System.currentTimeMillis();
            final long duration = endTime - startTime;

            System.out.println("Completed in " + duration + "ms");
            System.out.println("Output saved to " + outFile.getAbsolutePath());
        }
    }

    /**
     * Measures PDF re-recording performance using
     * {@link net.zamasoft.pdfg2d.PDFGraphics2D}.
     * <p>
     * Pages from the source document are rendered into a {@code PDFGraphics2D}
     * context backed by a linearized {@link PDFWriterImpl}, and the result is
     * saved to {@code output/performance-test-zamasoft.pdf}.
     * </p>
     *
     * @param file the source PDF file to process
     * @throws IOException if the source cannot be read or the output cannot be saved
     */
    private static void measureZamasoftPDFGraphics2D(final File file) throws IOException {
        System.out.println("Measuring net.zamasoft.pdfg2d.PDFGraphics2D...");
        final var outFile = new File(DemoUtils.getOutputDir(), "performance-test-zamasoft.pdf");

        try (final var sourceDoc = Loader.loadPDF(file)) {
            final var params = PDFParams.createDefault().withLinearized(true);
            final int pageCount = sourceDoc.getNumberOfPages();
            System.out.println("Starting rendering of " + pageCount + " pages...");
            final long startTime = System.currentTimeMillis();

            try {
                try (final var pdfWriter = new PDFWriterImpl(
                        new FileFragmentedOutput(outFile, AbstractTempFileOutput.Config.ON_MEMORY), params)) {
                    final var renderer = new PDFRenderer(sourceDoc);

                    for (int i = 0; i < 1; i++) {
                        final var sourcePage = sourceDoc.getPage(i);
                        final var mediaBox = sourcePage.getMediaBox();

                        // Create graphics for the new page
                        try (final var g2d = new PDFGraphics2D(
                                pdfWriter.nextPage(mediaBox.getWidth(), mediaBox.getHeight()))) {
                            // Render source to graphics
                            renderer.renderPageToGraphics(i, g2d);
                        }

                        System.out.print("\rZamasoft Rendered page " + (i + 1) + "/" + pageCount);
                    }
                    System.out.println(); // Newline after progress
                }
            } catch (Exception e) {
                if (e instanceof IOException ioe) {
                    throw ioe;
                } else {
                    throw new IOException(e);
                }
            }

            final long endTime = System.currentTimeMillis();
            final long duration = endTime - startTime;

            System.out.println("Completed in " + duration + "ms");
            System.out.println("Output saved to " + outFile.getAbsolutePath());
        }
    }
}
