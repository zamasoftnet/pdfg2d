package net.zamasoft.pdfg2d.pdf.font.cid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ToUnicodeSparseTest {
	@Test
	public void negativeEntriesStayUnmapped() {
		final var entries = ToUnicode.buildFromSparseChars(new int[] { -1, 'A', -1, 'B' }).getUnicodes();
		assertEquals(2, entries.length);
		assertEquals(1, entries[0].getFirstCode());
		assertEquals(1, entries[0].getLastCode());
		assertArrayEquals(new int[] { 'A' }, entries[0].getUnicodes());
		assertEquals(3, entries[1].getFirstCode());
		assertEquals(3, entries[1].getLastCode());
		assertArrayEquals(new int[] { 'B' }, entries[1].getUnicodes());

		final int[] boundary = new int[257];
		java.util.Arrays.fill(boundary, -1);
		boundary[255] = 'X';
		boundary[256] = 'Y';
		final var boundaryEntries = ToUnicode.buildFromSparseChars(boundary).getUnicodes();
		assertEquals(2, boundaryEntries.length, "a CMap range must not cross a 256-code boundary");
		assertEquals(255, boundaryEntries[0].getFirstCode());
		assertEquals(256, boundaryEntries[1].getFirstCode());
	}
}
