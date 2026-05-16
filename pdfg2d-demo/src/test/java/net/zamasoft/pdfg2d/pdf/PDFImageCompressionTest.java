package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.PDFParams.ImageCompression;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class PDFImageCompressionTest {

    @ParameterizedTest
    @MethodSource("imageCompressionModes")
    public void testImageXObjectFiltersMatchCompressionMode(final ImageCompression imageCompression,
            final List<String> expectedFilters) throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(),
                "image-compression-" + imageCompression.name().toLowerCase() + ".pdf");
        final var params = PDFParams.createDefault()
                .withCompression(PDFParams.Compression.BINARY)
                .withImageCompression(imageCompression);

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                gc.drawImage(pdf.addImage(createSampleImage()));
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            final COSStream imageStream = findFirstImageStream(document);
            assertEquals(expectedFilters, getFilterNames(imageStream),
                    "The image XObject filters should reflect the configured image compression mode");
        }
    }

    private static Stream<Arguments> imageCompressionModes() {
        return Stream.of(
                Arguments.of(ImageCompression.FLATE, List.of("FlateDecode")),
                Arguments.of(ImageCompression.JPEG, List.of("DCTDecode")));
    }

    private static COSStream findFirstImageStream(final org.apache.pdfbox.pdmodel.PDDocument document) throws Exception {
        for (final COSName name : document.getPage(0).getResources().getXObjectNames()) {
            final PDXObject xObject = document.getPage(0).getResources().getXObject(name);
            if (xObject instanceof PDImageXObject image) {
                return image.getCOSObject();
            }
        }
        throw new AssertionError("Expected an image XObject on the first page");
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

    private static BufferedImage createSampleImage() {
        final var image = new BufferedImage(180, 180, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setPaint(new GradientPaint(0, 0, Color.ORANGE, 180, 180, Color.CYAN));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.fillOval(30, 30, 120, 120);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(72, 48, 36, 84);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
