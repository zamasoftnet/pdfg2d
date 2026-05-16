package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.action.JavaScriptAction;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class PDFOpenActionTest {

    @Test
    public void testOpenJavaScriptAction() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "open_action_test.pdf");
        // Define JavaScript Open Action
        final var jsCode = "app.alert('Welcome to this PDF!');";
        final var params = PDFParams.createDefault()
                .withOpenAction(new JavaScriptAction(jsCode));

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);

            try (final var page = pdf.nextPage(595, 842)) {
                // page content
            }
            pdf.close();
            builder.close();
        }

        try (final var doc = Loader.loadPDF(file)) {
            var action = doc.getDocumentCatalog().getOpenAction();
            assertNotNull(action, "Should have an Open Action");

            assertTrue(action instanceof PDActionJavaScript, "Open Action should be JavaScript");
            PDActionJavaScript jsAction = (PDActionJavaScript) action;
            // Note: PDFBox might return the whole JS stream or string
            String extractedJs = jsAction.getAction();
            assertEquals(jsCode, extractedJs);
        }
    }
}
