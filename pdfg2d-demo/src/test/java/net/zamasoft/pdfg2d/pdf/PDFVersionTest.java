package net.zamasoft.pdfg2d.pdf;

import java.io.File;
import java.io.FileOutputStream;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
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
            // Derived from the profile metadata so new versions are covered
            // automatically.
            final var expected = Float.parseFloat(version.baseVersion());

            Assertions.assertEquals(expected, pdfVersion, 0.001f, "PDF Version mismatch for " + version);
        }
    }

    private static Stream<Version> provideVersions() {
        return Stream.of(Version.values());
    }
}
