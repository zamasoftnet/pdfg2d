package net.zamasoft.pdfg2d.pdf;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class PDFBookmarkTest {

    @Test
    public void testBookmarks() throws Exception {
        final var tempFile = TestOutputFiles.outputFile(getClass(), "test-bookmarks.pdf");

        final var params = PDFParams.createDefault().withBookmarks(true);

        // PDFG2D creates bookmarks via PDFPageOutput API.
        // We ensure that setting the bookmark flag and calling start/endBookmark works.

        try (final var out = new FileOutputStream(tempFile)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);

            try (final var gfx = pdf.nextPage(595, 842)) {
                // Add bookmark
                if (gfx instanceof final PDFPageOutput page) {
                    // Add bookmark pointing to (0, 842) - top of page
                    page.startBookmark("Chapter 1", new java.awt.geom.Point2D.Double(0, 842));
                    page.endBookmark();
                }
            }
            pdf.close();
            builder.close();
        }

        try (final var doc = Loader.loadPDF(tempFile)) {
            final var outline = doc.getDocumentCatalog().getDocumentOutline();
            Assertions.assertNotNull(outline, "Document outline should exist");
            final var item = outline.getFirstChild();
            Assertions.assertNotNull(item, "Should have at least one bookmark");
            Assertions.assertEquals("Chapter 1", item.getTitle());
        }
    }
}
