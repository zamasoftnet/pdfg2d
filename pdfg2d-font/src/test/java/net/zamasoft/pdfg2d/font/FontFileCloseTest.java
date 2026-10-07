package net.zamasoft.pdfg2d.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

/**
 * Ensures that {@link FontFile#close()} deletes decompressed WOFF files and leaves raw font files untouched (2026-10-05).
 * Calls that used only sfnt, such as variable font detection, left decompressed files without an owner.
 */
public class FontFileCloseTest {
	@Test
	public void closeDeletesTheExtractedFile() throws Exception {
		final File woff = new File("src/test/resources/data/test.woff");
		final File sfnt;
		try (final FontFile fontFile = new FontFile(woff)) {
			sfnt = fontFile.getSfntFile();
			assertNotEquals(woff, sfnt);
			assertTrue(sfnt.isFile());
		}
		assertFalse(sfnt.exists());
		assertTrue(woff.isFile());
	}

	@Test
	public void closeAfterOpeningAFontDeletesTheExtractedFile() throws Exception {
		final File sfnt;
		try (final FontFile fontFile = new FontFile(new File("src/test/resources/data/test.woff"))) {
			sfnt = fontFile.getSfntFile();
			fontFile.getFont();
		}
		assertFalse(sfnt.exists());
	}

	@Test
	public void closeLeavesARawFontAlone() throws Exception {
		final File ttf = new File("src/test/resources/data/test.ttf");
		try (final FontFile fontFile = new FontFile(ttf)) {
			assertEquals(ttf, fontFile.getSfntFile());
			fontFile.getFont();
		}
		assertTrue(ttf.isFile());
	}
}
