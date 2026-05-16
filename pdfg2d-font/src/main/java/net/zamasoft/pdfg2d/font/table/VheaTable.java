package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code vhea} (Vertical Header) table.
 * <p>
 * Contains metric values for vertical text layout, including ascender,
 * descender, line gap, and the count of vertical metrics stored in the
 * {@code vmtx} table.  The structure mirrors {@link HheaTable} but applies
 * to vertical writing modes.
 * </p>
 *
 * @param ascender            the typographic ascender for the font in vertical writing mode
 * @param descender           the typographic descender for the font in vertical writing mode (negative value)
 * @param lineGap             the typographic line gap in vertical writing mode
 * @param advanceWidthMax     the maximum advance height value in the {@code vmtx} table
 * @param minLeftSideBearing  the minimum top side bearing value in the {@code vmtx} table
 * @param minRightSideBearing the minimum bottom side bearing value in the {@code vmtx} table
 * @param xMaxExtent          the maximum Y extent: max(tsb + (yMax - yMin))
 * @param caretSlopeRise      the rise of the caret slope for vertical text
 * @param caretSlopeRun       the run of the caret slope for vertical text
 * @param metricDataFormat    0 for current format
 * @param numberOfHMetrics    number of vMetric entries in the {@code vmtx} table
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record VheaTable(
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

	protected VheaTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private VheaTable(VheaTable other) {
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

	private static VheaTable readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
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
			return new VheaTable(
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
		return VHEA;
	}
}
