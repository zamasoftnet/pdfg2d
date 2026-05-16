package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.awt.Color;
import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.PDFParams.Compression;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class PDFCompressionTest {

    @ParameterizedTest
    @MethodSource("compressionModes")
    public void testContentStreamFiltersMatchCompressionMode(final Compression compression,
            final List<String> expectedFilters) throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(),
                "compression-" + compression.name().toLowerCase() + ".pdf");
        final var params = PDFParams.createDefault().withCompression(compression);

        try (final var g2d = new PDFGraphics2D(file, 200, 200, params)) {
            g2d.setPaint(Color.BLACK);
            g2d.drawString("Compression " + compression.name(), 24, 80);
            g2d.drawRect(20, 40, 120, 60);
        }

        try (final var document = Loader.loadPDF(file)) {
            assertEquals(1, document.getNumberOfPages());
            final COSBase contents = document.getPage(0).getCOSObject().getDictionaryObject(COSName.CONTENTS);
            final var filters = getFilterNames((COSStream) contents);
            assertIterableEquals(expectedFilters, filters,
                    "The page content stream filters should reflect the configured compression mode");
        }
    }

    private static Stream<Arguments> compressionModes() {
        return Stream.of(
                Arguments.of(Compression.NONE, List.of()),
                Arguments.of(Compression.ASCII, List.of("ASCII85Decode", "FlateDecode")),
                Arguments.of(Compression.BINARY, List.of("FlateDecode")));
    }

    private static List<String> getFilterNames(final COSStream stream) {
        final COSBase filter = stream.getDictionaryObject(COSName.FILTER);
        if (filter == null) {
            return List.of();
        }
        if (filter instanceof COSName name) {
            return List.of(name.getName());
        }
        final var array = (COSArray) filter;
        return array.toList().stream()
                .map(COSName.class::cast)
                .map(COSName::getName)
                .toList();
    }
}
