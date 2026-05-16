package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.pdf.utils.GraphicsOperatorInspector;
import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class TransparencyTest {

    @Test
    public void testAlphaTransparency() throws Exception {
        final var tempFile = TestOutputFiles.outputFile(getClass(), "test-transparency.pdf");

        // 1. Generate PDF with transparency
        try (final var out = new FileOutputStream(tempFile)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder,
                    PDFParams.createDefault().withVersion(PDFParams.Version.V_1_4));

            try (final var gc = new PDFGC(pdf.nextPage(400, 400))) {
                gc.setFillPaint(RGBAColor.create(1.0f, 0.0f, 0.0f, 0.5f));
                gc.fill(new Rectangle2D.Double(100, 100, 200, 200));
            }
            pdf.close();
            builder.close();
        }

        // 2. Verify with Inspector
        try (final var document = Loader.loadPDF(tempFile)) {
            final var inspector = new GraphicsOperatorInspector(document.getPage(0));
            inspector.run();
            final var commands = inspector.getCommands();

            // Debug
            commands.forEach(System.out::println);

            // Check that the red rectangle is present in the content stream.
            final var hasRedFill = commands.stream()
                    .anyMatch(cmd -> (cmd.operation.equals("f") || cmd.operation.equals("f*")) &&
                            cmd.currentColor[0] == 1.0f && cmd.currentColor[1] == 0.0f && cmd.currentColor[2] == 0.0f
                    );

            final var hasExtGState = document.getPage(0).getResources().getCOSObject().containsKey(COSName.EXT_G_STATE);
            final String stream;
            try (final var contents = document.getPage(0).getContents()) {
                stream = new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
            final var usesGraphicsState = stream.contains(" gs");

            assertTrue(hasRedFill, "Should have a red fill operation");
            assertTrue(hasExtGState || usesGraphicsState, "Transparency should emit an ExtGState or gs operator");
        }
    }
}
