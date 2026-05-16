package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code hhea} (Horizontal Header) table.
 * <p>
 * Contains metric values for horizontal text layout, including ascender,
 * descender, line gap, and the count of horizontal metrics stored in the
 * {@code hmtx} table.
 * </p>
 *
 * @param ascender            the typographic ascender for the font
 * @param descender           the typographic descender for the font (negative value)
 * @param lineGap             the typographic line gap
 * @param advanceWidthMax     the maximum advance width value in the {@code hmtx} table
 * @param minLeftSideBearing  the minimum left side bearing value in the {@code hmtx} table
 * @param minRightSideBearing the minimum right side bearing value in the {@code hmtx} table
 * @param xMaxExtent          the maximum X extent: max(lsb + (xMax - xMin))
 * @param caretSlopeRise      the rise of the caret slope used to calculate the slope of the cursor
 * @param caretSlopeRun       the run of the caret slope used to calculate the slope of the cursor
 * @param metricDataFormat    0 for current format
 * @param numberOfHMetrics    number of hMetric entries in the {@code hmtx} table
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record HheaTable(
		short ascender,
		short descender,
		short lineGap,
		short advanceWidthMax,
		short minLeftSideBearing,
		short minRightSideBearing,
		short xMaxExtent,
		short caretSlopeRise,
		short caretSlopeRun,
		short metricDataFormat,
		int numberOfHMetrics) implements XheaTable {

	protected HheaTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private HheaTable(HheaTable other) {
		this(
				other.ascender,
				other.descender,
				other.lineGap,
				other.advanceWidthMax,
				other.minLeftSideBearing,
				other.minRightSideBearing,
				other.xMaxExtent,
				other.caretSlopeRise,
				other.caretSlopeRun,
				other.metricDataFormat,
				other.numberOfHMetrics);
	}

	private static HheaTable readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());
			raf.readInt(); // version
			final short ascender = raf.readShort();
			final short descender = raf.readShort();
			final short lineGap = raf.readShort();
			final short advanceWidthMax = raf.readShort();
			final short minLeftSideBearing = raf.readShort();
			final short minRightSideBearing = raf.readShort();
			final short xMaxExtent = raf.readShort();
			final short caretSlopeRise = raf.readShort();
			final short caretSlopeRun = raf.readShort();
			for (int i = 0; i < 5; i++) {
				raf.readShort();
			}
			final short metricDataFormat = raf.readShort();
			final int numberOfHMetrics = raf.readUnsignedShort();
			return new HheaTable(
					ascender,
					descender,
					lineGap,
					advanceWidthMax,
					minLeftSideBearing,
					minRightSideBearing,
					xMaxExtent,
					caretSlopeRise,
					caretSlopeRun,
					metricDataFormat,
					numberOfHMetrics);
		}
	}

	/** {@inheritDoc} */
	@Override
	public short getAdvanceWidthMax() {
		return this.advanceWidthMax;
	}

	/** {@inheritDoc} */
	@Override
	public short getAscender() {
		return this.ascender;
	}

	/** {@inheritDoc} */
	@Override
	public short getCaretSlopeRise() {
		return this.caretSlopeRise;
	}

	/** {@inheritDoc} */
	@Override
	public short getCaretSlopeRun() {
		return this.caretSlopeRun;
	}

	/** {@inheritDoc} */
	@Override
	public short getDescender() {
		return this.descender;
	}

	/** {@inheritDoc} */
	@Override
	public short getLineGap() {
		return this.lineGap;
	}

	/** {@inheritDoc} */
	@Override
	public short getMetricDataFormat() {
		return this.metricDataFormat;
	}

	/** {@inheritDoc} */
	@Override
	public short getMinLeftSideBearing() {
		return this.minLeftSideBearing;
	}

	/** {@inheritDoc} */
	@Override
	public short getMinRightSideBearing() {
		return this.minRightSideBearing;
	}

	/** {@inheritDoc} */
	@Override
	public int getNumberOfHMetrics() {
		return this.numberOfHMetrics;
	}

	/** {@inheritDoc} */
	@Override
	public short getXMaxExtent() {
		return this.xMaxExtent;
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return HHEA;
	}
}
