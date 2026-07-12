package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/**
 * The OpenType COLR (color layer) table, version 0: each base glyph maps to a
 * sequence of layers, each a glyph id painted with a {@link CpalTable} palette
 * entry.
 *
 * @param baseGlyphLayers base glyph id to its layer records
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record ColrTable(Map<Integer, Layer[]> baseGlyphLayers) implements Table {

	/**
	 * One layer of a color glyph.
	 *
	 * @param glyphId      the layer's glyph id (its outline)
	 * @param paletteEntry the CPAL color/entry index (0xFFFF = text color)
	 */
	public record Layer(int glyphId, int paletteEntry) {
	}

	protected ColrTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private ColrTable(final ColrTable other) {
		this(other.baseGlyphLayers);
	}

	private static Map<Integer, Layer[]> readData(final DirectoryEntry de, final RandomAccessFile raf)
			throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());
			raf.readUnsignedShort(); // version (0)
			final int numBaseGlyphRecords = raf.readUnsignedShort();
			final int baseGlyphRecordsOffset = raf.readInt();
			final int layerRecordsOffset = raf.readInt();
			final int numLayerRecords = raf.readUnsignedShort();

			// Layer records: (glyphId, paletteEntry) pairs.
			raf.seek(de.offset() + layerRecordsOffset);
			final int[] layerGlyphs = new int[numLayerRecords];
			final int[] layerPalettes = new int[numLayerRecords];
			for (int i = 0; i < numLayerRecords; i++) {
				layerGlyphs[i] = raf.readUnsignedShort();
				layerPalettes[i] = raf.readUnsignedShort();
			}

			// Base glyph records: (glyphId, firstLayerIndex, numLayers).
			raf.seek(de.offset() + baseGlyphRecordsOffset);
			final var map = new HashMap<Integer, Layer[]>();
			for (int i = 0; i < numBaseGlyphRecords; i++) {
				final int baseGid = raf.readUnsignedShort();
				final int firstLayer = raf.readUnsignedShort();
				final int layerCount = raf.readUnsignedShort();
				final Layer[] layers = new Layer[layerCount];
				for (int j = 0; j < layerCount; j++) {
					final int k = firstLayer + j;
					layers[j] = new Layer(layerGlyphs[k], layerPalettes[k]);
				}
				map.put(baseGid, layers);
			}
			return map;
		}
	}

	/**
	 * Returns the layers of a base color glyph, or {@code null} if the glyph is
	 * not a color glyph.
	 *
	 * @param baseGlyphId the base glyph id
	 * @return the layers, or {@code null}
	 */
	public Layer[] getLayers(final int baseGlyphId) {
		return this.baseGlyphLayers.get(baseGlyphId);
	}

	@Override
	public int getType() {
		return COLR;
	}

	@Override
	public String toString() {
		return "COLR";
	}
}
