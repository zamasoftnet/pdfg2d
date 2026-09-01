package net.zamasoft.pdfg2d.pdf.font.cid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class CIDUtilsSubsetNameTest {
	private static final short[] WIDTHS = { 500, 600 };
	private static final int[] SIGNATURE = { 0, 1 };

	@Test
	public void stripsNonAsciiAndPdfNameDelimiters() {
		final String safe = CIDUtils.createEmbeddedSubsetName(WIDTHS, null, SIGNATURE, "GothicA1-Regular");
		final String localized = CIDUtils.createEmbeddedSubsetName(WIDTHS, null, SIGNATURE,
				"고딕 A1-#Reg()<>[]{}/%ular");

		assertEquals(safe.substring(0, 7) + "A1-Regular", localized);
		assertTrue(localized.matches("[A-P]{6}\\+[!-~]+"));
	}

	@Test
	public void emptySanitizedNameGetsStableFallback() {
		final String name = CIDUtils.createEmbeddedSubsetName(WIDTHS, null, SIGNATURE, " 고딕#()<>[]{}/%");

		assertEquals(name.substring(0, 7) + "SubsetFont", name);
	}
}
