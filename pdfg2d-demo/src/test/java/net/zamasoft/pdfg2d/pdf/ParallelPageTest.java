package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.util.ParallelPageRenderer;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Parallel page generation: painters run on worker threads into recording
 * contexts and are replayed sequentially, so the output must be identical to
 * serial generation while the CPU-bound painting scales with cores.
 */
public class ParallelPageTest {

	private static final int PAGES = 24;

	/**
	 * A painter modeling the real workload profile: expensive layout
	 * computation (the parallelizable part) with moderate drawing output.
	 */
	private static ParallelPageRenderer.PagePainter painter(final int pageIndex) {
		return gc -> {
			// CPU-heavy "layout" phase
			var v = pageIndex * 0.001;
			for (var i = 0; i < 3_000_000; ++i) {
				v += Math.sin(i * 0.001 + pageIndex) * Math.cos(i * 0.002 + v * 1e-9);
			}
			// Moderate drawing phase; v participates so the loop is not elided
			final var path = new GeneralPath();
			for (var i = 0; i < 200; ++i) {
				path.moveTo(50 + (i % 400), 50 + ((i * 7) % 700) + (v % 1) * 1e-6);
				path.lineTo(60 + (i % 380), 55 + ((i * 11) % 700));
			}
			gc.setStrokePaint(RGBColor.create((pageIndex % 3) / 2f, 0.2f, 0.8f));
			gc.setLineWidth(0.1);
			gc.draw(path);
			gc.setFillPaint(RGBColor.create(1, 0, 0));
			gc.fill(new Rectangle2D.Double(10, 10 + pageIndex * 5, 100, 40));
		};
	}

	private File generate(final String name, final int parallelism) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
			final var painters = new ArrayList<ParallelPageRenderer.PagePainter>();
			for (var i = 0; i < PAGES; ++i) {
				painters.add(painter(i));
			}
			final var target = new ParallelPageRenderer.PageTarget() {
				@Override
				public GC nextPage() throws GraphicsException {
					try {
						return new PDFGC(pdf.nextPage(595, 842));
					} catch (IOException e) {
						throw new GraphicsException(e);
					}
				}

				@Override
				public void closePage(final GC gc) throws GraphicsException {
					try {
						((PDFGC) gc).close();
					} catch (IOException e) {
						throw new GraphicsException(e);
					}
				}
			};
			ParallelPageRenderer.render(painters.iterator(), null, parallelism, target);
			pdf.close();
			builder.close();
		}
		return file;
	}

	@Test
	public void testParallelOutputMatchesSerial() throws Exception {
		final var serial = generate("parallel_1.pdf", 1);
		final var parallel = generate("parallel_4.pdf", 4);

		try (final var docS = Loader.loadPDF(serial); final var docP = Loader.loadPDF(parallel)) {
			assertEquals(PAGES, docS.getNumberOfPages());
			assertEquals(PAGES, docP.getNumberOfPages());
			// Page order and content must be identical
			for (var i = 0; i < PAGES; ++i) {
				final byte[] cs, cp;
				try (final var in = docS.getPage(i).getContents()) {
					cs = in.readAllBytes();
				}
				try (final var in = docP.getPage(i).getContents()) {
					cp = in.readAllBytes();
				}
				org.junit.jupiter.api.Assertions.assertArrayEquals(cs, cp,
						"Page " + i + " content must match the serial output");
			}
		}
	}

	@Test
	@Tag("benchmark")
	public void benchmarkParallelSpeedup() throws Exception {
		// Warm-up
		generate("parallel_warm.pdf", 4);

		final var t1 = System.nanoTime();
		generate("parallel_bench1.pdf", 1);
		final var serialMs = (System.nanoTime() - t1) / 1_000_000;

		final var cores = Math.min(4, Runtime.getRuntime().availableProcessors());
		final var t2 = System.nanoTime();
		generate("parallel_bench4.pdf", cores);
		final var parallelMs = (System.nanoTime() - t2) / 1_000_000;

		System.out.println("[benchmark] serial=" + serialMs + "ms parallel(" + cores + ")=" + parallelMs + "ms");
		if (cores > 1) {
			assertTrue(parallelMs < serialMs,
					"Parallel painting should be faster: " + parallelMs + "ms vs " + serialMs + "ms");
		}
	}
}
