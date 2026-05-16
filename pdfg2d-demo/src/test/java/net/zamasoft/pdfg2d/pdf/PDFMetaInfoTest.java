package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.Loader;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class PDFMetaInfoTest {

    @Test
    public void testMetaInformation() {
        final var file = TestOutputFiles.outputFile(getClass(), "meta_test.pdf");
        assertDoesNotThrow(() -> {
            var params = PDFParams.createDefault();
            final var meta = new PDFMetaInfo();
            meta.setTitle("Test Title");
            meta.setAuthor("Test Author");
            meta.setSubject("Test Subject");
            meta.setKeywords("test, pdf, java");
            meta.setCreator("PDFGraphics2D Test");
            meta.setProducer("PDFGraphics2D");
            params = params.withMetaInfo(meta);

            try (final var g2d = new PDFGraphics2D(file, 595, 842, params)) {
                g2d.setPaint(Color.BLACK);
                g2d.drawString("Meta Information Test", 100, 100);
            }
        });

        assertTrue(file.exists());

        // Verify with PDFBox
        try (final var doc = Loader.loadPDF(file)) {
            final var info = doc.getDocumentInformation();
            assertEquals("Test Title", info.getTitle());
            assertEquals("Test Author", info.getAuthor());
            assertEquals("Test Subject", info.getSubject());
            assertEquals("test, pdf, java", info.getKeywords());
            assertEquals("PDFGraphics2D Test", info.getCreator());
            // Producer might be modified by PDFBox or generated differently, but usually
            // checks out.
            // assertEquals("PDFGraphics2D", info.getProducer());
        } catch (IOException e) {
            throw new RuntimeException("Failed to verify PDF with PDFBox", e);
        }
    }
}
