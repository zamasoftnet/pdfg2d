package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * The OpenType CPAL (color palette) table: one or more palettes of sRGB
 * colors, indexed by the layer records of a {@link ColrTable}.
 *
 * @param numPaletteEntries colors per palette
 * @param colorRecordIndices the first color-record index of each palette
 * @param colors             all color records, as packed 0xAARRGGBB
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record CpalTable(int numPaletteEntries, int[] colorRecordIndices, int[] colors) implements Table {

	protected CpalTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private CpalTable(final CpalTable other) {
		this(other.numPaletteEntries, other.colorRecordIndices, other.colors);
	}

	private static CpalTable readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());
			raf.readUnsignedShort(); // version
			final int numPaletteEntries = raf.readUnsignedShort();
			final int numPalettes = raf.readUnsignedShort();
			final int numColorRecords = raf.readUnsignedShort();
			final int offsetFirstColorRecord = raf.readInt();
			final int[] indices = new int[numPalettes];
			for (int i = 0; i < numPalettes; i++) {
				indices[i] = raf.readUnsignedShort();
			}
			raf.seek(de.offset() + offsetFirstColorRecord);
			final int[] colors = new int[numColorRecords];
			for (int i = 0; i < numColorRecords; i++) {
				// Color records are stored blue, green, red, alpha.
				final int b = raf.readUnsignedByte();
				final int g = raf.readUnsignedByte();
				final int r = raf.readUnsignedByte();
				final int a = raf.readUnsignedByte();
				colors[i] = (a << 24) | (r << 16) | (g << 8) | b;
			}
			return new CpalTable(numPaletteEntries, indices, colors);
		}
	}

	/**
	 * Returns the packed {@code 0xAARRGGBB} color for a palette entry index in
	 * the given palette.
	 *
	 * @param palette      the palette index (usually 0)
	 * @param paletteEntry the color/entry index from a layer record
	 * @return the packed color, or opaque black if out of range
	 */
	public int getColor(final int palette, final int paletteEntry) {
		if (palette < 0 || palette >= this.colorRecordIndices.length) {
			return 0xFF000000;
		}
		final int index = this.colorRecordIndices[palette] + paletteEntry;
		return (index >= 0 && index < this.colors.length) ? this.colors[index] : 0xFF000000;
	}

	@Override
	public int getType() {
		return CPAL;
	}

	@Override
	public String toString() {
		return "CPAL";
	}
}
