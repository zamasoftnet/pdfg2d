package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code hmtx} (Horizontal Metrics) table.
 * <p>
 * Stores advance widths and left side bearings for each glyph in the font.
 * The first {@code numberOfHMetrics} entries (from the {@code hhea} table)
 * each hold a packed int with the advance width in the high 16 bits and the
 * left side bearing in the low 16 bits.  Remaining glyphs share the last
 * advance width and store only their left side bearing in a separate array.
 * </p>
 *
 * @param xMetrics        packed int array of advance width / LSB pairs for the first n glyphs
 * @param leftSideBearing additional left side bearings for glyphs beyond the metric count
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 */
public record HmtxTable(int[] xMetrics, short[] leftSideBearing) implements XmtxTable {

	private static final long serialVersionUID = 0L;

	/**
	 * Reads a HmtxTable from the given file.
	 * 
	 * @param de               the directory entry
	 * @param raf              the file to read from
	 * @param numberOfHMetrics the number of horizontal metrics
	 * @param lsbCount         the number of additional left side bearings
	 * @return a new HmtxTable
	 * @throws IOException if an I/O error occurs
	 */
	public static HmtxTable read(
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
			return new HmtxTable(xMetrics, leftSideBearing);
		}
	}

	/**
	 * Returns the advance width for the glyph at the given index.
	 * If the index exceeds the number of stored metrics the last stored
	 * advance width is returned (as per the OpenType specification).
	 *
	 * @param i the glyph index
	 * @return the advance width in font units
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
	 * Returns the left side bearing for the glyph at the given index.
	 * Indices within the metric count are read from the packed xMetrics array;
	 * indices beyond that are read from the separate leftSideBearing array.
	 *
	 * @param i the glyph index
	 * @return the left side bearing in font units
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
		return HMTX;
	}
}
