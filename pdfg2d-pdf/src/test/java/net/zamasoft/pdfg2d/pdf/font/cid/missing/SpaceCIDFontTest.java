package net.zamasoft.pdfg2d.pdf.font.cid.missing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 空白フォントが幅付きの固定幅空白(U+2000..U+200A 等)を引き受けることの試験(2026-09-04)。 */
class SpaceCIDFontTest {
	@Test
	void fixedWidthSpacesAreDisplayableWithTheirAdvance() {
		final var source = SpaceCIDFontSource.INSTANCES_LTR;
		final int[] spaces = { 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009,
				0x200A, 0x205F, 0x3000 };
		for (final int c : spaces) {
			assertTrue(source.canDisplay(c), "U+" + Integer.toHexString(c));
		}
		assertFalse(source.canDisplay('A'));
		final var font = (SpaceCIDFont) source.createFont("SPACE", null);
		assertEquals(200, font.getAdvance(font.toGID(0x2009)));
		assertEquals(100, font.getAdvance(font.toGID(0x200A)));
		assertEquals(1000, font.getAdvance(font.toGID(0x3000)));
		assertEquals(500, font.getAdvance(font.toGID(0x0020)));
		assertEquals(0, font.getAdvance(font.toGID(0x200B)));
	}
}
