package com.idrsolutions.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class JDeli {
    private JDeli() {
    }

    public static void write(final BufferedImage image, final String format, final OutputStream out) throws IOException {
        out.write(("JDELI:" + format + ":" + image.getWidth() + "x" + image.getHeight()).getBytes(StandardCharsets.ISO_8859_1));
    }
}
