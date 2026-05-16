package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code kern} (Kerning) table.
 * <p>
 * Contains one or more kerning subtables ({@link KernSubtable}) that provide
 * pairwise kerning adjustments.  Format 0 subtables ({@link KernSubtableFormat0})
 * list specific glyph pairs; format 2 subtables ({@link KernSubtableFormat2})
 * use class-based kerning.
 * </p>
 *
 * @param tables the array of kerning subtables
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record KernTable(KernSubtable[] tables) implements Table {

	protected KernTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private static KernSubtable[] readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());
			raf.readUnsignedShort(); // version
			final int nTables = raf.readUnsignedShort();
			final KernSubtable[] tables = new KernSubtable[nTables];
			for (int i = 0; i < nTables; i++) {
				tables[i] = KernSubtable.read(raf);
			}
			return tables;
		}
	}

	/**
	 * Returns the number of kerning subtables.
	 *
	 * @return the subtable count
	 */
	public int getSubtableCount() {
		return this.tables.length;
	}

	/**
	 * Returns the kerning subtable at the given index.
	 *
	 * @param i the zero-based index
	 * @return the {@link KernSubtable}
	 */
	public KernSubtable getSubtable(final int i) {
		return this.tables[i];
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return KERN;
	}
}
