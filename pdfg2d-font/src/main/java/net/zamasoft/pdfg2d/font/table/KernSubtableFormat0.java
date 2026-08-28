package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Kerning subtable format 0.
 *
 * @param horizontal   whether this subtable applies to horizontal advances
 * @param kerningPairs the array of kerning pairs
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public record KernSubtableFormat0(boolean horizontal, KerningPair[] kerningPairs) implements KernSubtable {

	/**
	 * Reads a KernSubtableFormat0 from the given file.
	 *
	 * @param raf        the file to read from
	 * @param horizontal whether the enclosing coverage marks this subtable
	 *                   horizontal
	 * @return a new KernSubtableFormat0
	 * @throws IOException if an I/O error occurs
	 */
	protected static KernSubtableFormat0 read(final RandomAccessFile raf, final boolean horizontal)
			throws IOException {
		final int nPairs = raf.readUnsignedShort();
		raf.readUnsignedShort(); // searchRange
		raf.readUnsignedShort(); // entrySelector
		raf.readUnsignedShort(); // rangeShift
		final KerningPair[] kerningPairs = new KerningPair[nPairs];
		for (int i = 0; i < nPairs; i++) {
			kerningPairs[i] = KerningPair.read(raf);
		}
		return new KernSubtableFormat0(horizontal, kerningPairs);
	}

	@Override
	public int getKerningPairCount() {
		return this.kerningPairs.length;
	}

	@Override
	public KerningPair getKerningPair(final int i) {
		return this.kerningPairs[i];
	}

	@Override
	public boolean isHorizontal() {
		return this.horizontal;
	}
}
