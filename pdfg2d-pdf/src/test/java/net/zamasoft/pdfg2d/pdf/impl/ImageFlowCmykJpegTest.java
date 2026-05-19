package net.zamasoft.pdfg2d.pdf.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;

import com.twelvemonkeys.imageio.color.ColorSpaces;

import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

class ImageFlowCmykJpegTest {
    private static final Pattern CMYK_DECODE = Pattern
            .compile("/Decode\\s*\\[\\s*1\\s+0\\s+1\\s+0\\s+1\\s+0\\s+1\\s+0\\s*\\]");

    @Test
    void testCmykJpegCanBeLoadedFromSource() throws Exception {
        final var jpegFile = TestOutputFiles.outputFile(getClass(), "cmyk.jpg");
        writeCmykJpeg(jpegFile);

        final var out = new ByteArrayOutputStream();
        final var builder = new StreamFragmentedOutput(out);
        final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
        try (final var gc = new PDFGC(pdf.nextPage(20, 20))) {
            gc.drawImage(pdf.loadImage(new FileSource(jpegFile)));
        }
        pdf.close();
        builder.close();

        final var pdfSource = out.toString(StandardCharsets.ISO_8859_1);
        assertTrue(pdfSource.contains("/ColorSpace /DeviceCMYK"),
                "A CMYK JPEG loaded from Source should be embedded as DeviceCMYK");
        assertTrue(pdfSource.contains("/DCTDecode"), "The CMYK JPEG should be embedded as a JPEG image stream");
        assertTrue(CMYK_DECODE.matcher(pdfSource).find(),
                "Inverted CMYK JPEG samples should have a decode array");
    }

    private static void writeCmykJpeg(final File file) throws IOException {
        final var image = createCmykImage();
        final var writer = findTwelveMonkeysJpegWriter();
        final var writeParam = writer.getDefaultWriteParam();
        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(0.9f);
        }

        try (ImageOutputStream imageOut = ImageIO.createImageOutputStream(file)) {
            assertNotNull(imageOut, "ImageIO should create an output stream for the test JPEG");
            writer.setOutput(imageOut);
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
            image.flush();
        }
    }

    private static ImageWriter findTwelveMonkeysJpegWriter() throws IOException {
        ImageIO.scanForPlugins();
        final var writers = ImageIO.getImageWritersByFormatName("jpeg");
        while (writers.hasNext()) {
            final var writer = writers.next();
            if (writer.getClass().getName().startsWith("com.twelvemonkeys.")) {
                return writer;
            }
            writer.dispose();
        }
        throw new IOException("TwelveMonkeys JPEG writer is required to create a CMYK JPEG fixture.");
    }

    private static BufferedImage createCmykImage() {
        final ColorSpace cmyk = ColorSpaces.getColorSpace(ColorSpaces.CS_GENERIC_CMYK);
        final var colorModel = new ComponentColorModel(cmyk, false, false, Transparency.OPAQUE,
                DataBuffer.TYPE_BYTE);
        final WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, 8, 8, 4, null);
        final var image = new BufferedImage(colorModel, raster, false, null);

        for (var y = 0; y < image.getHeight(); ++y) {
            for (var x = 0; x < image.getWidth(); ++x) {
                raster.setPixel(x, y, new int[] {
                        x < 4 ? 220 : 0,
                        y < 4 ? 180 : 0,
                        x >= 4 ? 200 : 0,
                        y >= 4 ? 80 : 0
                });
            }
        }
        return image;
    }
}
