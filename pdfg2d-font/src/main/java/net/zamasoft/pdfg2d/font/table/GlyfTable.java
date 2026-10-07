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
	 * Glyph IDs currently being read (per thread).
	 *
	 * <p>
	 * Composite glyph components are read again through {@link #getDescription(int)}, so malformed fonts
	 * whose components point back to themselves (directly or through a cycle) recurse indefinitely
	 * and cause {@code StackOverflowError}. This crashed the production font list on 2026-09-01:
	 * after adding script identification by looking up representative code point glyphs for each font,
	 * a font in the font pack triggered this cycle.
	 * </p>
	 *
	 * <p>
	 * <b>A depth limit cannot stop it.</b> {@code GlyfCompositeDescript.getPointCount()} calls itself on
	 * a <b>separately constructed</b> descriptor obtained by rereading, so the depth merely oscillates
	 * between the limit and limit minus 1 while stack frames accumulate. Track the glyph IDs
	 * currently being read and drop components that lead back to them.
	 * </p>
	 *
	 * <p>
	 * A {@code record} cannot have instance fields, so store the state per thread.
	 * Even when multiple threads read the same table, their paths remain independent.
	 * </p>
	 */
	private static final ThreadLocal<java.util.Set<Integer>> READING = ThreadLocal
			.withInitial(java.util.HashSet::new);

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
		final java.util.Set<Integer> reading = READING.get();
		if (!reading.add(i)) {
			// This glyph is already being read: a malformed font has a component that points back to itself.
			// Treat it like a glyph with no shape so the reader drops the component.
			return null;
		}
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
		} finally {
			reading.remove(i);
		}
		return desc;
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return GLYF;
	}
}
