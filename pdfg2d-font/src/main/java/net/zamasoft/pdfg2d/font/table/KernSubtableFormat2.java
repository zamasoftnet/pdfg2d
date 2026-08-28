package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Kerning subtable format 2.
 * 
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public record KernSubtableFormat2() implements KernSubtable {

	/**
	 * Reads a KernSubtableFormat2 from the given file.
	 * 
	 * @param raf the file to read from
	 * @return a new KernSubtableFormat2
	 * @throws IOException if an I/O error occurs
	 */
	protected static KernSubtableFormat2 read(final RandomAccessFile raf) throws IOException {
		raf.readUnsignedShort(); // rowWidth
		raf.readUnsignedShort(); // leftClassTable
		raf.readUnsignedShort(); // rightClassTable
		raf.readUnsignedShort(); // array
		return new KernSubtableFormat2();
	}

	@Override
	public int getKerningPairCount() {
		return 0;
	}

	@Override
	public KerningPair getKerningPair(final int i) {
		return null;
	}

	@Override
	public boolean isHorizontal() {
		// 実質未対応のスタブ(ペアを提供しない)
		return false;
	}
}
