package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.annot.LinkAnnot;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class PDFLinkTest {

    @Test
    public void testHyperlinks() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "link_test.pdf");
        final var params = PDFParams.createDefault();

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);

            try (final var page = pdf.nextPage(595, 842)) {
                // Add Link Annotation
                final var link = new LinkAnnot();
                link.setShape(new Rectangle(50, 700, 100, 20)); // x, y, w, h
                final var uri = new URI("https://www.example.com");
                link.setURI(uri);

                page.addAnnotation(link);
            }
            pdf.close();
            builder.close();
        }

        try (final var doc = Loader.loadPDF(file)) {
            final var page = doc.getPage(0);
            final var annots = page.getAnnotations();
            assertNotNull(annots);
            assertEquals(1, annots.size());

            final var annot = annots.get(0);
            if (annot instanceof PDAnnotationLink displayLink) {
                final var action = (PDActionURI) displayLink.getAction();
                assertEquals("https://www.example.com", action.getURI());
            } else {
                throw new AssertionError("Annotation is not a Link: " + annot.getClass().getName());
            }
        }
    }
}
