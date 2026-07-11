package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.File;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

/**
 * Tests for the configurable deflate compression level: output must remain
 * valid at every level, and higher levels must not produce larger files than
 * store-only for compressible content.
 */
public class DeflateLevelTest {

	private File generate(final String name, final int level) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		final var params = PDFParams.createDefault().withDeflateLevel(level);
		try (final var g2d = new PDFGraphics2D(file, 400, 400, params)) {
			// Repetitive vector content compresses well
			for (var i = 0; i < 500; ++i) {
				g2d.setColor(new Color(i % 255, 100, 200));
				g2d.fill(new Rectangle2D.Double((i * 7) % 350, (i * 11) % 350, 40, 20));
			}
		}
		return file;
	}

	@Test
	public void testLevelsProduceValidAndOrderedSizes() throws Exception {
		final var store = generate("deflate_store.pdf", 0);
		final var fast = generate("deflate_fast.pdf", 1);
		final var best = generate("deflate_best.pdf", 9);

		for (final var f : new File[] { store, fast, best }) {
			try (final var doc = Loader.loadPDF(f)) {
				assertTrue(doc.getNumberOfPages() == 1, f.getName() + " must stay valid");
			}
		}
		assertTrue(fast.length() < store.length(),
				"BEST_SPEED must beat store-only: " + fast.length() + " vs " + store.length());
		assertTrue(best.length() <= fast.length(),
				"BEST_COMPRESSION must not exceed BEST_SPEED: " + best.length() + " vs " + fast.length());
	}

	@Test
	public void testInvalidLevelIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> PDFParams.createDefault().withDeflateLevel(10));
		assertThrows(IllegalArgumentException.class, () -> PDFParams.createDefault().withDeflateLevel(-2));
	}
}
