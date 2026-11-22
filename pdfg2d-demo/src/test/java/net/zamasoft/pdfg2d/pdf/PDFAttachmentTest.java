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

import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
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
            // The key is the file name; the description is the filespec's /Desc
            assertTrue(embeddedFiles.containsKey("test.txt"), "Should contain key 'test.txt': " + embeddedFiles.keySet());

            final var spec = embeddedFiles.get("test.txt");
            assertEquals("Test Text File", spec.getFileDescription());
            final var embeddedFile = spec.getEmbeddedFile();
            assertNotNull(embeddedFile, "Embedded file stream should exist");

            // Verify content
            final var extracted = new String(embeddedFile.toByteArray(), StandardCharsets.UTF_8);
            assertEquals("This is a test attachment.", extracted);
        }
    }

    /**
     * Attachments that share a description, and even a file name, all stay in the name tree. The description was
     * the key of the name tree, so the second attachment replaced the first.
     */
    @Test
    public void testSameDescriptionAndSameNameKeepEveryFile() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "attachment_same_description.pdf");
        final var params = PDFParams.createDefault().withVersion(PDFParams.Version.V_1_4);
        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            try (final var page = pdf.nextPage(595, 842)) {
                // page content
            }
            final String[][] attachments = { { "a.txt", "FIRST" }, { "b.txt", "SECOND" }, { "a.txt", "THIRD" } };
            for (final var attachment : attachments) {
                try (var attachOut = pdf.addAttachment(attachment[0], new Attachment("Supporting", "text/plain"))) {
                    attachOut.write(attachment[1].getBytes(StandardCharsets.UTF_8));
                }
            }
            pdf.close();
            builder.close();
        }

        try (final var doc = Loader.loadPDF(file)) {
            final Map<String, PDComplexFileSpecification> embeddedFiles = doc.getDocumentCatalog().getNames()
                    .getEmbeddedFiles().getNames();
            assertEquals(3, embeddedFiles.size(), "every attachment must be reachable: " + embeddedFiles.keySet());
            assertEquals("FIRST", content(embeddedFiles.get("a.txt")));
            assertEquals("SECOND", content(embeddedFiles.get("b.txt")));
            assertEquals("THIRD", content(embeddedFiles.get("a (2).txt")));
            for (final var spec : embeddedFiles.values()) {
                assertEquals("Supporting", spec.getFileDescription());
            }
            // The file keeps the name it was given; only the key is numbered
            assertEquals("a.txt", embeddedFiles.get("a (2).txt").getFile());
        }
    }

    private static String content(final PDComplexFileSpecification spec) throws java.io.IOException {
        assertNotNull(spec, "the attachment must be in the name tree");
        return new String(spec.getEmbeddedFile().toByteArray(), StandardCharsets.UTF_8);
    }
}
