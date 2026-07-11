package net.zamasoft.pdfg2d.pdf;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.FileOutputStream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Rough wall-clock benchmark for the PDF generation hot path (content stream
 * operator output). Not a precision benchmark — used to sanity-check that
 * serializer changes move throughput in the right direction. Prints the
 * timing to stdout; always passes.
 */
public class GenerationBenchmarkTest {

	private static final int PAGES = 200;
	private static final int SHAPES_PER_PAGE = 2000;

	@Test
	@Tag("benchmark")
	public void benchmarkVectorHeavyDocument() throws Exception {
		// Warm-up round + 3 measured rounds
		runOnce("warmup");
		long best = Long.MAX_VALUE;
		for (var i = 0; i < 3; ++i) {
			best = Math.min(best, runOnce("round" + i));
		}
		System.out.println("[benchmark] best of 3: " + best + " ms for " + PAGES + " pages x "
				+ SHAPES_PER_PAGE + " shapes");
	}

	private long runOnce(final String label) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "benchmark_" + label + ".pdf");
		final var start = System.nanoTime();
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
			for (var p = 0; p < PAGES; ++p) {
				try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
					final var g2d = new BridgeGraphics2D(gc);
					g2d.setStroke(new BasicStroke(0.5f));
					for (var i = 0; i < SHAPES_PER_PAGE; ++i) {
						g2d.setColor(new Color((i * 37) % 256, (i * 91) % 256, (i * 13) % 256));
						final double x = (i * 7) % 500;
						final double y = (i * 11) % 780;
						if ((i & 1) == 0) {
							g2d.fill(new Rectangle2D.Double(x, y, 40.25, 20.5));
						} else {
							g2d.draw(new Line2D.Double(x, y, x + 55.5, y + 33.75));
						}
					}
					g2d.dispose();
				}
			}
			pdf.close();
			builder.close();
		}
		final var elapsed = (System.nanoTime() - start) / 1_000_000;
		System.out.println("[benchmark] " + label + ": " + elapsed + " ms, size=" + file.length());
		return elapsed;
	}
}
