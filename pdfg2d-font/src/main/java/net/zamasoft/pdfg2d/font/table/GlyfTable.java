package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

import net.zamasoft.pdfg2d.font.truetype.GlyfCompositeDescript;
import net.zamasoft.pdfg2d.font.truetype.GlyfDescript;
import net.zamasoft.pdfg2d.font.truetype.GlyfSimpleDescript;

/**
 * OpenType {@code glyf} (Glyph Data) table.
 * <p>
 * Holds the raw outline data for each TrueType glyph.  Glyph outlines are
 * read on demand using the byte offsets supplied by the companion
 * {@link LocaTable}.  Both simple glyphs ({@link net.zamasoft.pdfg2d.font.truetype.GlyfSimpleDescript
 * GlyfSimpleDescript}) and composite glyphs
 * ({@link net.zamasoft.pdfg2d.font.truetype.GlyfCompositeDescript GlyfCompositeDescript}) are
 * supported.
 * </p>
 *
 * @param de   the directory entry that locates this table in the font file
 * @param loca the {@code loca} table used to map glyph indices to byte offsets
 * @param raf  the random-access file from which glyph data is read
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 */
public record GlyfTable(DirectoryEntry de, LocaTable loca, RandomAccessFile raf) implements Table {

	/**
	 * Reads and returns the glyph description for the glyph at the given index.
	 *
	 * @param i the glyph index (GID)
	 * @return the {@link GlyfDescript} for the glyph, or {@code null} if the
	 *         glyph has no outline (e.g., space character)
	 * @throws RuntimeException wrapping an {@link java.io.IOException} if the
	 *                          glyph data cannot be read
	 */
	public GlyfDescript getDescription(final int i) {
		GlyfDescript desc = null;
		try {
			final int len = this.loca.getOffset((i + 1)) - this.loca.getOffset(i);
			if (len <= 0) {
				return null;
			}
			synchronized (this.raf) {
				this.raf.seek(this.de.offset() + this.loca.getOffset(i));
				final int numberOfContours = this.raf.readShort();
				if (numberOfContours >= 0) {
					desc = GlyfSimpleDescript.read(this, numberOfContours, this.raf);
				} else {
					desc = GlyfCompositeDescript.read(this, this.raf);
				}
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		return desc;
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return GLYF;
	}
}
