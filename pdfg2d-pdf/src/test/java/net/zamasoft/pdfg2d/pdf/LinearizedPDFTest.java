package net.zamasoft.pdfg2d.pdf;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import net.zamasoft.pdfg2d.io.impl.AbstractTempFileOutput;
import net.zamasoft.pdfg2d.io.impl.FileFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.pdfg2d.PDFGraphics2D;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LinearizedPDFTest {
    @Test
    public void testLinearizedPDFGeneration() throws IOException {
        final File outFile = TestOutputFiles.outputFile(getClass(), "test-linearized.pdf");

        final PDFParams params = PDFParams.createDefault().withLinearized(true);
        try (PDFWriterImpl pdfWriter = new PDFWriterImpl(
                new FileFragmentedOutput(outFile, AbstractTempFileOutput.Config.DEFAULT), params)) {
            // Page 1
            try (PDFGraphics2D g2d = new PDFGraphics2D(pdfWriter.nextPage(595, 842))) {
                g2d.drawLine(80, 80, 220, 160);
                g2d.drawRect(90, 180, 120, 60);
            }
            // Page 2
            try (PDFGraphics2D g2d = new PDFGraphics2D(pdfWriter.nextPage(595, 842))) {
                g2d.drawLine(100, 110, 260, 190);
                g2d.drawOval(120, 220, 90, 50);
            }
        }

        assertTrue(outFile.exists());
        assertTrue(outFile.length() > 0);

        final byte[] bytes = Files.readAllBytes(outFile.toPath());
        final int headerLength = Math.min(bytes.length, 1024);
        final String header = new String(bytes, 0, headerLength, StandardCharsets.ISO_8859_1);
        assertTrue(header.contains("/Linearized"), "The linearization dictionary should appear within the first 1024 bytes");
        assertTrue(header.contains("/L "), "The linearization dictionary should contain the file length entry");
        assertTrue(header.contains("/H ["), "The linearization dictionary should contain the hint-stream entry");
        assertLinearizedXrefIsWellFormed(bytes);
        assertQpdfCheckPasses(outFile);
    }

    private static void assertLinearizedXrefIsWellFormed(final byte[] bytes) {
        final String content = new String(bytes, StandardCharsets.ISO_8859_1);
        final int xrefOffset = content.indexOf("xref\r\n0 ");
        assertTrue(xrefOffset >= 0, "The linearized PDF should start with a primary xref table");

        final int trailerOffset = content.indexOf("trailer\r\n", xrefOffset);
        assertTrue(trailerOffset > xrefOffset, "The primary xref table should be followed by a trailer");

        final String xrefBlock = content.substring(xrefOffset, trailerOffset);
        final String[] lines = xrefBlock.split("\r\n");
        assertTrue(lines.length >= 3, "The primary xref table should contain a header and entries");
        assertEquals("xref", lines[0]);

        final var headerMatcher = Pattern.compile("0\\s+(\\d+)").matcher(lines[1]);
        assertTrue(headerMatcher.matches(), "The primary xref header should declare the object range");
        final int objectCount = Integer.parseInt(headerMatcher.group(1));
        assertEquals(objectCount + 2, lines.length,
                "The primary xref table should contain exactly one line per declared object");

        final Pattern entryPattern = Pattern.compile("\\d{10} \\d{5} [nf]");
        final Map<Integer, Integer> xrefOffsets = new HashMap<>();
        for (int i = 2; i < lines.length; ++i) {
            assertTrue(entryPattern.matcher(lines[i]).matches(),
                    "Malformed xref entry at index " + (i - 2) + ": " + lines[i]);
            if (i > 2) {
                xrefOffsets.put(i - 2, Integer.parseInt(lines[i].substring(0, 10)));
            }
        }

        final var tMatcher = Pattern.compile("/T\\s+(\\d+)").matcher(content.substring(0, trailerOffset));
        assertTrue(tMatcher.find(), "The linearization dictionary should declare the main xref offset with /T");
        final int mainXrefOffset = Integer.parseInt(tMatcher.group(1));
        final int xrefHeaderOffset = content.lastIndexOf("xref\r\n0 ", mainXrefOffset);
        assertTrue(xrefHeaderOffset >= 0, "The main xref table should appear before the /T offset");
        final String xrefHeader = "xref\r\n0 " + objectCount + "\r\n";
        assertEquals(xrefHeaderOffset + xrefHeader.length(), mainXrefOffset,
                "The /T entry should point to the first main-xref entry");

        final Pattern objectPattern = Pattern.compile("(?m)^(\\d+) 0 obj\\r$");
        final var objectMatcher = objectPattern.matcher(content);
        while (objectMatcher.find()) {
            final int objectNumber = Integer.parseInt(objectMatcher.group(1));
            final Integer objectXrefOffset = xrefOffsets.get(objectNumber);
            if (objectXrefOffset != null) {
                assertEquals(objectMatcher.start(), objectXrefOffset.intValue(),
                        "Primary xref offset mismatch for object " + objectNumber);
            }
        }
    }

    private static void assertQpdfCheckPasses(final File pdfFile) throws IOException {
        Assumptions.assumeTrue(isQpdfAvailable(), "qpdf is required for linearized-PDF validation");
        final Process process = new ProcessBuilder("qpdf", "--check", pdfFile.getAbsolutePath()).start();
        final byte[] output = process.getInputStream().readAllBytes();
        final byte[] errors = process.getErrorStream().readAllBytes();
        try {
            assertEquals(0, process.waitFor(),
                    () -> "qpdf --check failed:\n"
                            + new String(output, StandardCharsets.UTF_8)
                            + new String(errors, StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for qpdf", e);
        }
    }

    private static boolean isQpdfAvailable() {
        try {
            final Process process = new ProcessBuilder("qpdf", "--version").start();
            return process.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
