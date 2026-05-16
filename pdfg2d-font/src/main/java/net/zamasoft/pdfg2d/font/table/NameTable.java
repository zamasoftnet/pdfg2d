package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code name} (Naming) table.
 * <p>
 * Stores strings that provide human-readable names for features in the font,
 * including the family name, style name, copyright notice, and PostScript
 * name.  Each string is stored as a {@link NameRecord} which is identified
 * by a platform, encoding, language, and name ID.
 * </p>
 *
 * @param records the array of naming records for this font
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 */
public record NameTable(NameRecord[] records) implements Table {

	protected NameTable(final DirectoryEntry entry, final RandomAccessFile raf) throws IOException {
		this(readData(entry, raf));
	}

	private static NameRecord[] readData(final DirectoryEntry entry, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(entry.offset());
			raf.readShort(); // formatSelector
			final int numberOfNameRecords = raf.readShort();
			final int stringStorageOffset = raf.readShort();
			final NameRecord[] records = new NameRecord[numberOfNameRecords];

			// Load the records (without strings)
			for (int i = 0; i < numberOfNameRecords; i++) {
				records[i] = NameRecord.read(raf);
			}

			// Now load the strings
			for (int i = 0; i < numberOfNameRecords; i++) {
				records[i] = records[i].withLoadedString(raf, entry.offset() + stringStorageOffset);
			}
			return records;
		}
	}

	/**
	 * Returns the string value for the first record matching the given name ID.
	 *
	 * @param nameId the name ID (see {@link Table#NAME_FONT_FAMILY_NAME} etc.)
	 * @return the record string, or an empty string if no matching record is found
	 */
	public String getRecord(final short nameId) {
		// Search for the first instance of this name ID
		for (final NameRecord record : this.records) {
			if (record.getNameId() == nameId) {
				return record.getRecordString();
			}
		}
		return "";
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return NAME;
	}

	/**
	 * Returns the name record at the given index.
	 *
	 * @param i the zero-based record index
	 * @return the {@link NameRecord}
	 */
	public NameRecord get(final int i) {
		return this.records[i];
	}

	/**
	 * Returns the total number of name records in this table.
	 *
	 * @return the record count
	 */
	public int size() {
		return this.records.length;
	}
}
