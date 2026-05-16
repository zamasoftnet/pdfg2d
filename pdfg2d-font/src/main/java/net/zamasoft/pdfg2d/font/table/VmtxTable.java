package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code vmtx} (Vertical Metrics) table.
 * <p>
 * Stores advance heights and top side bearings for each glyph in the font
 * when used in vertical writing mode.  The layout mirrors {@link HmtxTable}:
 * the first {@code numberOfVMetrics} entries (from the {@code vhea} table)
 * each hold a packed int with the advance height in the high 16 bits and the
 * top side bearing in the low 16 bits.
 * </p>
 *
 * @param xMetrics        packed int array of advance height / TSB pairs for the first n glyphs
 * @param leftSideBearing additional top side bearings for glyphs beyond the metric count
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 */
public record VmtxTable(int[] xMetrics, short[] leftSideBearing) implements XmtxTable {

	private static final long serialVersionUID = 0L;

	/**
	 * Reads a VmtxTable from the given file.
	 * 
	 * @param de               the directory entry
	 * @param raf              the file to read from
	 * @param numberOfHMetrics the number of horizontal metrics
	 * @param lsbCount         the number of additional left side bearings
	 * @return a new VmtxTable
	 * @throws IOException if an I/O error occurs
	 */
	public static VmtxTable read(
			final DirectoryEntry de,
			final RandomAccessFile raf,
			final int numberOfHMetrics,
			final int lsbCount) throws IOException {
		synchronized (raf) {
			final int[] xMetrics = new int[numberOfHMetrics];
			raf.seek(de.offset());
			for (int i = 0; i < numberOfHMetrics; i++) {
				xMetrics[i] = raf.readInt();
			}
			short[] leftSideBearing = null;
			if (lsbCount > 0) {
				leftSideBearing = new short[lsbCount];
				for (int i = 0; i < lsbCount; i++) {
					leftSideBearing[i] = raf.readShort();
				}
			}
			return new VmtxTable(xMetrics, leftSideBearing);
		}
	}

	/**
	 * Returns the advance height for the glyph at the given index.
	 * If the index exceeds the number of stored metrics the last stored
	 * advance height is returned (as per the OpenType specification).
	 *
	 * @param i the glyph index
	 * @return the advance height in font units
	 */
	@Override
	public int getAdvanceWidth(final int i) {
		if (i < this.xMetrics.length) {
			return this.xMetrics[i] >> 16;
		} else {
			return this.xMetrics[this.xMetrics.length - 1] >> 16;
		}
	}

	/**
	 * Returns the top side bearing for the glyph at the given index.
	 * Indices within the metric count are read from the packed xMetrics array;
	 * indices beyond that are read from the separate leftSideBearing array.
	 *
	 * @param i the glyph index
	 * @return the top side bearing in font units
	 */
	@Override
	public short getLeftSideBearing(int i) {
		if (i < this.xMetrics.length) {
			return (short) (this.xMetrics[i] & 0xffff);
		} else {
			i -= this.xMetrics.length;
			return this.leftSideBearing[i];
		}
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return VMTX;
	}
}
