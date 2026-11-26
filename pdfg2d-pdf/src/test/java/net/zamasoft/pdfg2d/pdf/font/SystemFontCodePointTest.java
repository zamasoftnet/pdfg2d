package net.zamasoft.pdfg2d.pdf.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.io.File;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.font.cid.embedded.SystemEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.SystemCIDIdentityFontSource;

/**
 * Fonts read through Java (installed fonts, {@code <all-system-fonts>}) take a character outside the BMP as a code
 * point (codex, 2026-11-26): {@code (char) c} kept its low 16 bits, so U+20041 counted as 'A' wherever the font has
 * 'A', and that glyph was drawn for it.
 */
public class SystemFontCodePointTest {
	private static final File FONT = new File("../pdfg2d-core/src/test/resources/pgothic-novert-subset.ttf");

	/** A BMP character of the font whose plane-2 twin (the same low 16 bits) the subset does not have. */
	private static int bmpCharacter(final Font font) {
		for (int c = 0x21; c < 0xFFFF; ++c) {
			if (font.canDisplay(c) && !font.canDisplay(0x20000 | c)) {
				return c;
			}
		}
		throw new AssertionError("no character");
	}

	@Test
	public void supplementaryCharacterIsNotItsLow16Bits() throws Exception {
		final Font font = Font.createFont(Font.TRUETYPE_FONT, FONT);
		final int bmp = bmpCharacter(font);
		final int supplementary = 0x20000 | bmp;

		final var embedded = new SystemEmbeddedCIDFontSource(font);
		assertTrue(embedded.canDisplay(bmp));
		assertFalse(embedded.canDisplay(supplementary));

		final var identity = new SystemCIDIdentityFontSource(font);
		assertTrue(identity.canDisplay(bmp));
		assertFalse(identity.canDisplay(supplementary));
		// the glyph of the BMP character, the missing glyph for the other
		assertNotEquals(identity.toGID(bmp), identity.toGID(supplementary));
		assertEquals(0, identity.toGID(supplementary));
	}
}
