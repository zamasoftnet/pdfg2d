package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.utils.GraphicsOperatorInspector;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class GraphicsDrawingTest {

    @Test
    public void testShapeDrawingOperators() throws Exception {
        final var tempFile = TestOutputFiles.outputFile(getClass(), "test-graphics-shapes.pdf");

        // 1. Generate PDF with specific shapes
        try (final var out = new FileOutputStream(tempFile)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());

            try (final var gc = new PDFGC(pdf.nextPage(400, 400))) {
                final var g = new BridgeGraphics2D(gc);

                // Draw a red rectangle
                g.setColor(Color.RED);
                g.fillRect(10, 20, 100, 50);

                // Draw a blue line
                g.setColor(Color.BLUE);
                g.setStroke(new BasicStroke(2.0f));
                g.drawLine(200, 200, 300, 300);
            }
            pdf.close();
            builder.close();
        }

        // 2. Verify with Inspector
        try (final var document = Loader.loadPDF(tempFile)) {
            final var inspector = new GraphicsOperatorInspector(document.getPage(0));
            inspector.run();
            final var commands = inspector.getCommands();

            // Check for rectangle fill (usually 're' followed by 'f' or 'f*')
            final var hasRedFill = commands.stream()
                    .anyMatch(cmd -> (cmd.operation.equals("f") || cmd.operation.equals("f*")) &&
                            cmd.currentColor[0] == 1.0f && cmd.currentColor[1] == 0.0f && cmd.currentColor[2] == 0.0f);
            assertTrue(hasRedFill, "Should have a red fill operation");

            // Check for blue stroke OR blue fill (if stroke is converted to filled shape)
            final var hasBlue = commands.stream().anyMatch(
                    cmd -> (cmd.operation.equals("S") || cmd.operation.equals("f") || cmd.operation.equals("f*")) &&
                            cmd.currentColor[0] == 0.0f && cmd.currentColor[1] == 0.0f && cmd.currentColor[2] == 1.0f);
            assertTrue(hasBlue, "Should have a blue drawing operation");
        }
    }
}
