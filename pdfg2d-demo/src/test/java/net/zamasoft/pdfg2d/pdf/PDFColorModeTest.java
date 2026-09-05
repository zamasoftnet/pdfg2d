package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.PDFParams.ColorMode;
import net.zamasoft.pdfg2d.pdf.utils.GraphicsOperatorInspector;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

/**
 * Tests for PDF Color Modes: RGB, Gray, and CMYK.
 */
public class PDFColorModeTest {

    @Test
    public void testColorModeRGB() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "color_mode_rgb.pdf");
        final var params = PDFParams.createDefault().withColorMode(ColorMode.PRESERVE); // Used for RGB

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
                final var g = new BridgeGraphics2D(gc);
                g.setColor(Color.RED);
                g.fillRect(100, 100, 100, 100);
            }
            pdf.close();
            builder.close();
        }

        try (final var doc = Loader.loadPDF(file)) {
            final var inspector = new GraphicsOperatorInspector(doc.getPage(0));
            inspector.run();
            final var commands = inspector.getCommands();

            // Check for red fill
            // In RGB, red is (1.0, 0.0, 0.0) -> requires 3 components
            boolean found = commands.stream().anyMatch(cmd -> {
                if (cmd.currentColor != null && cmd.currentColor.length == 3) {
                    return Math.abs(cmd.currentColor[0] - 1.0f) < 0.01f &&
                            Math.abs(cmd.currentColor[1] - 0.0f) < 0.01f &&
                            Math.abs(cmd.currentColor[2] - 0.0f) < 0.01f;
                }
                return false;
            });
            assertTrue(found, "Should contain RGB Red (1, 0, 0) drawing operation");
        }
    }

    @Test
    public void testColorModeGray() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "color_mode_gray.pdf");
        final var params = PDFParams.createDefault().withColorMode(ColorMode.GRAY);

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
                final var g = new BridgeGraphics2D(gc);
                g.setColor(Color.RED); // Should be converted to gray
                g.fillRect(100, 100, 100, 100);
            }
            pdf.close();
            builder.close();
        }

        try (final var doc = Loader.loadPDF(file)) {
            final var inspector = new GraphicsOperatorInspector(doc.getPage(0));
            inspector.run();
            final var commands = inspector.getCommands();

            // Check for gray fill
            // Red converted to grayscale: 0.299*R + 0.587*G + 0.114*B = 0.299
            // Color space should have 1 component
            boolean found = commands.stream().anyMatch(cmd -> {
                // System.out.println("Gray cmd: " +
                // java.util.Arrays.toString(cmd.currentColor));
                if (cmd.currentColor != null && cmd.currentColor.length == 1) {
                    return Math.abs(cmd.currentColor[0] - 0.299f) < 0.05f;
                }
                return false;
            });
            assertTrue(found, "Should contain Grayscale equivalent of Red (approx 0.3) drawing operation");
        }
    }

    @Test
    public void testColorModeCMYK() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "color_mode_cmyk.pdf");
        final var params = PDFParams.createDefault().withColorMode(ColorMode.CMYK);

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
                final var g = new BridgeGraphics2D(gc);
                g.setColor(Color.RED); // Should be converted to CMYK
                g.fillRect(100, 100, 100, 100);
            }
            pdf.close();
            builder.close();
        }

        try (final var doc = Loader.loadPDF(file)) {
            final var inspector = new GraphicsOperatorInspector(doc.getPage(0));
            inspector.run();
            final var commands = inspector.getCommands();

			// ICC/FOGRA39の正確な値はJDK/LCMSのpatchで揺れるため、
			// 赤に必要なインキ量の性質だけを検査する。
            boolean found = commands.stream().anyMatch(cmd -> {
                if (cmd.currentColor != null && cmd.currentColor.length == 4) {
					return cmd.currentColor[0] < .05f &&
							cmd.currentColor[1] > .85f &&
							cmd.currentColor[2] > .85f &&
							cmd.currentColor[3] < .05f;
                }
                return false;
            });
			assertTrue(found, "Should contain the ICC CMYK equivalent of red");
        }
    }
}
