package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Interface for kerning subtables.
 * 
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public interface KernSubtable {

	/**
	 * Returns the number of kerning pairs.
	 * 
	 * @return the kerning pair count
	 */
	int getKerningPairCount();

	/**
	 * Returns a kerning pair at the given index.
	 *
	 * @param i the index
	 * @return the kerning pair
	 */
	KerningPair getKerningPair(int i);

	/**
	 * Returns whether this subtable holds plain horizontal pair kerning
	 * (coverage: horizontal, not minimum, not cross-stream).
	 *
	 * @return true if applicable to horizontal advance adjustment
	 */
	boolean isHorizontal();

	/**
	 * Reads a KernSubtable from the given file.
	 *
	 * @param raf the file to read from
	 * @return the kerning subtable, or null if unknown format
	 * @throws IOException if an I/O error occurs
	 */
	static KernSubtable read(final RandomAccessFile raf) throws IOException {
		final long start = raf.getFilePointer();
		raf.readUnsignedShort(); // version
		final int length = raf.readUnsignedShort();
		final int coverage = raf.readUnsignedShort();
		final int format = coverage >> 8;
		// coverage: bit0=horizontal, bit1=minimum, bit2=cross-stream
		final boolean horizontal = (coverage & 0x01) != 0 && (coverage & 0x06) == 0;

		final KernSubtable table = switch (format) {
			case 0 -> KernSubtableFormat0.read(raf, horizontal);
			case 2 -> KernSubtableFormat2.read(raf);
			default -> null;
		};
		// Advance to the next subtable. Skip unknown formats by their declared length. length is
		// 16-bit, and some real fonts have large pair tables with overflowed values shorter than their actual size
		// (a known issue in Arial, etc.), so never move backward past the parsed position.
		final long declaredEnd = start + length;
		if (raf.getFilePointer() < declaredEnd) {
			raf.seek(declaredEnd);
		}
		return table;
	}
}
