package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Represents a lookup list in an OpenType font.
 * 
 * @param lookups the array of lookups
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public record LookupList(Lookup[] lookups) {

	/**
	 * Creates a new LookupList by reading from the given file.
	 * 
	 * @param raf     the file to read from
	 * @param offset  the offset to seek to
	 * @param factory the factory for creating subtables
	 * @throws IOException if an I/O error occurs
	 */
	public LookupList(final RandomAccessFile raf, final int offset, final LookupSubtableFactory factory)
			throws IOException {
		this(readData(raf, offset, factory));
	}

	private static Lookup[] readData(final RandomAccessFile raf, final int offset, final LookupSubtableFactory factory)
			throws IOException {
		synchronized (raf) {
			raf.seek(offset);
			final int lookupCount = raf.readUnsignedShort();
			final int[] lookupOffsets = new int[lookupCount];
			final Lookup[] lookups = new Lookup[lookupCount];
			for (int i = 0; i < lookupCount; i++) {
				lookupOffsets[i] = raf.readUnsignedShort();
			}
			for (int i = 0; i < lookupCount; i++) {
				lookups[i] = new Lookup(factory, raf, offset + lookupOffsets[i]);
			}
			return lookups;
		}
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		sb.append(this.lookups.length).append(':');
		for (final Lookup lookup : this.lookups) {
			final int tag = lookup.getType();
			sb.append((char) ((tag >> 24) & 0xff))
					.append((char) ((tag >> 16) & 0xff))
					.append((char) ((tag >> 8) & 0xff))
					.append((char) ((tag) & 0xff))
					.append('/');
		}
		return sb.toString();
	}
}
