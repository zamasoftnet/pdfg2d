package net.zamasoft.pdfg2d.pdf.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ImageFlowJpeg2000Test {
    @Test
    void testJdeliIsDetectedOnClasspath() {
        assertTrue(ImageFlow.isJDeliAvailable(), "Test classpath should expose the JDeli shim");
    }

    @Test
    void testJpeg2000EncodingPrefersJdeliWhenAvailable() throws Exception {
        final var image = new BufferedImage(7, 5, BufferedImage.TYPE_INT_RGB);
        final var out = new ByteArrayOutputStream();

        ImageFlow.writeJpeg2000WithJDeli(image, out);

        assertEquals("JDELI:jpx:7x5", out.toString(StandardCharsets.ISO_8859_1),
                "JPEG2000 encoding should route through JDeli when it is available");
    }
}
