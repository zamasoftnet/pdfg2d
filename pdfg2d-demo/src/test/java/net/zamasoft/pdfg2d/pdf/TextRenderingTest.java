package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.utils.TextInspector;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class TextRenderingTest {

    @Test
    public void testTextAttributes() throws Exception {
        final var tempFile = TestOutputFiles.outputFile(getClass(), "test-text-rendering.pdf");

        // 1. Generate PDF with text
        try (final var out = new FileOutputStream(tempFile)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());

            try (final var gc = new PDFGC(pdf.nextPage(400, 400))) {
                final var g = new BridgeGraphics2D(gc);

                g.setColor(Color.BLACK);
                g.setFont(new Font("Serif", Font.PLAIN, 12));
                g.drawString("TestString", 50, 100);
            }
            pdf.close();
            builder.close();
        }

        // 2. Verify with TextInspector
        try (final var document = Loader.loadPDF(tempFile)) {
            final var inspector = new TextInspector();
            final var fullText = inspector.getText(document);
            assertTrue(fullText.contains("TestString"), "Text content missing");

            final var infos = inspector.getTextInfos();
            // Filter info for our string
            final var targetInfos = infos.stream()
                    .filter(i -> i.text.equals("T")) // Check first char position roughly
                    .collect(Collectors.toList());

            assertFalse(targetInfos.isEmpty(), "Character info not found");

            // Check Y position
            final var y = targetInfos.get(0).y;
            assertEquals(100.0f, y, 5.0f, "Text Y position should be roughly 100");
        }
    }
}
