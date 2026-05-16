package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class PDFAttachmentTest {

    @Test
    public void testFileAttachments() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "attachment_test.pdf");
        // Version 1.4+ required for attachments
        final var params = PDFParams.createDefault()
                .withVersion(PDFParams.Version.V_1_4);

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);

            // Add dummy page
            try (final var page = pdf.nextPage(595, 842)) {
                // page content
            }

            // Add Attachment
            final var attachName = "test.txt";
            final var attachContent = "This is a test attachment.";
            final var attachBytes = attachContent.getBytes(StandardCharsets.UTF_8);

            final var attachment = new Attachment("Test Text File", "text/plain");

            // Write attachment data
            try (var attachOut = pdf.addAttachment(attachName, attachment)) {
                attachOut.write(attachBytes);
            }

            pdf.close();
            builder.close();
        }

        try (final var doc = Loader.loadPDF(file)) {
            final var names = doc.getDocumentCatalog().getNames();
            assertNotNull(names, "Document Names dictionary should exist");
            assertNotNull(names.getEmbeddedFiles(), "EmbeddedFiles should exist");

            final Map<String, PDComplexFileSpecification> embeddedFiles = names.getEmbeddedFiles().getNames();
            assertNotNull(embeddedFiles, "EmbeddedFiles map should not be null");
            assertTrue(embeddedFiles.containsKey("Test Text File"), "Should contain key 'Test Text File'"); // Key is
                                                                                                            // description
                                                                                                            // usually
                                                                                                            // if
                                                                                                            // provided

            final var spec = embeddedFiles.get("Test Text File");
            final var embeddedFile = spec.getEmbeddedFile();
            assertNotNull(embeddedFile, "Embedded file stream should exist");

            // Verify content
            final var extracted = new String(embeddedFile.toByteArray(), StandardCharsets.UTF_8);
            assertEquals("This is a test attachment.", extracted);
        }
    }
}
