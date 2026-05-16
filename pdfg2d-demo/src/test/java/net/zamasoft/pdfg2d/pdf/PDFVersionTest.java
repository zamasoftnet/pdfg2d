package net.zamasoft.pdfg2d.pdf;

import java.io.File;
import java.io.FileOutputStream;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.PDFParams.Version;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class PDFVersionTest {

    @ParameterizedTest
    @MethodSource("provideVersions")
    public void testPDFVersions(final Version version) throws Exception {
        final var tempFile = TestOutputFiles.outputFile(getClass(), "test-version-" + version + ".pdf");

        final var params = PDFParams.createDefault().withVersion(version);

        try (final var out = new FileOutputStream(tempFile)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            try (final var page = pdf.nextPage(595, 842)) {
                // Empty page is enough to check version header
            }
            pdf.close();
            builder.close();
        }

        try (final var doc = Loader.loadPDF(tempFile)) {
            final var pdfVersion = doc.getVersion();
            // Map our enum to float version
            final var expected = switch (version) {
                case V_1_2 -> 1.2f;
                case V_1_3 -> 1.3f;
                case V_1_4, V_PDFA1B, V_PDFX1A -> 1.4f; // PDF/A-1b and PDF/X-1a are based on 1.4
                case V_1_5 -> 1.5f;
                case V_1_6 -> 1.6f;
                case V_1_7 -> 1.7f;
            };

            Assertions.assertEquals(expected, pdfVersion, 0.001f, "PDF Version mismatch for " + version);
        }
    }

    private static Stream<Version> provideVersions() {
        return Stream.of(
                Version.V_1_2,
                Version.V_1_3,
                Version.V_1_4,
                Version.V_1_5,
                Version.V_1_6,
                Version.V_1_7,
                Version.V_PDFA1B,
                Version.V_PDFX1A);
    }
}
