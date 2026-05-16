package net.zamasoft.pdfg2d.font;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.Cleaner;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.font.cff.CFFGlyphList;
import net.zamasoft.pdfg2d.font.cff.CFFTable;
import net.zamasoft.pdfg2d.font.table.DirectoryEntry;
import net.zamasoft.pdfg2d.font.table.GlyfTable;
import net.zamasoft.pdfg2d.font.table.HeadTable;
import net.zamasoft.pdfg2d.font.table.HheaTable;
import net.zamasoft.pdfg2d.font.table.HmtxTable;
import net.zamasoft.pdfg2d.font.table.LocaTable;
import net.zamasoft.pdfg2d.font.table.MaxpTable;
import net.zamasoft.pdfg2d.font.table.Table;
import net.zamasoft.pdfg2d.font.table.TableDirectory;
import net.zamasoft.pdfg2d.font.table.TableFactory;
import net.zamasoft.pdfg2d.font.table.VheaTable;
import net.zamasoft.pdfg2d.font.table.VmtxTable;
import net.zamasoft.pdfg2d.font.truetype.TrueTypeGlyphList;

/**
 * Represents an OpenType (or TrueType / CFF) font loaded from a
 * {@link java.io.RandomAccessFile}.  Font tables are read lazily on first
 * access; once parsed they are cached for reuse.  The underlying file handle
 * is released automatically via a {@link java.lang.ref.Cleaner} so the font
 * behaves correctly even if {@link #close()} is never called explicitly.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class OpenTypeFont implements AutoCloseable {
	private static final Logger LOG = Logger.getLogger(OpenTypeFont.class.getName());

	private final TableDirectory tableDirectory;
	private final Table[] tables;
	private final HeadTable head;
	private final HheaTable hhea;
	private final HmtxTable hmtx;
	private final VheaTable vhea;
	private final VmtxTable vmtx;
	private final GlyfTable glyf;
	private final LocaTable loca;
	private final MaxpTable maxp;
	private final GlyphList glyphList;
	private final RandomAccessFile raf;

	private static final Cleaner cleaner = Cleaner.create();
	private final Cleaner.Cleanable cleanable;

	private record ResourceReleaser(RandomAccessFile raf, Runnable onClose) implements Runnable {
		@Override
		public void run() {
			try {
				this.raf.close();
			} catch (final IOException e) {
				// ignore
			}
			if (this.onClose != null) {
				this.onClose.run();
			}
		}
	}

	/**
	 * Creates a new OpenTypeFont backed by the given random-access file.
	 * The file is closed automatically when this font is garbage-collected.
	 *
	 * @param raf the {@link RandomAccessFile} positioned at the start of the font
	 * @throws IOException if the font data cannot be read
	 */
	public OpenTypeFont(final RandomAccessFile raf) throws IOException {
		this(raf, null);
	}

	/**
	 * Creates a new OpenTypeFont backed by the given random-access file with an
	 * optional close callback.  The callback (e.g., to delete a temp file) is
	 * invoked in addition to closing the file when this font is cleaned up.
	 *
	 * @param raf     the {@link RandomAccessFile} positioned at the start of the font
	 * @param onClose an optional {@link Runnable} invoked after the file is closed;
	 *                may be {@code null}
	 * @throws IOException if the font data cannot be read
	 */
	public OpenTypeFont(final RandomAccessFile raf, final Runnable onClose) throws IOException {
		this.raf = raf;
		this.cleanable = cleaner.register(this, new ResourceReleaser(this.raf, onClose));
		this.tableDirectory = new TableDirectory(raf);
		this.tables = new Table[this.tableDirectory.getNumTables()];

		// Get references to commonly used tables
		this.head = (HeadTable) this.getTable(Table.HEAD);
		this.hhea = (HheaTable) this.getTable(Table.HHEA);
		this.maxp = (MaxpTable) this.getTable(Table.MAXP);

		if (this.hhea != null && this.maxp != null) {
			final DirectoryEntry entry = this.tableDirectory.getEntryByTag(Table.HMTX);
			if (entry != null) {
				this.hmtx = HmtxTable.read(entry, raf, this.hhea.getNumberOfHMetrics(),
						this.maxp.getNumGlyphs() - this.hhea.getNumberOfHMetrics());
			} else {
				this.hmtx = null;
			}
		} else {
			this.hmtx = null;
		}
		LOG.fine("Reading head, hhea, hmtx table");

		this.vhea = (VheaTable) this.getTable(Table.VHEA);
		if (this.vhea != null && this.maxp != null) {
			final DirectoryEntry entry = this.tableDirectory.getEntryByTag(Table.VMTX);
			if (entry != null) {
				this.vmtx = VmtxTable.read(entry, raf, this.vhea.getNumberOfHMetrics(),
						this.maxp.getNumGlyphs() - this.vhea.getNumberOfHMetrics());
			} else {
				this.vmtx = null;
			}
		} else {
			this.vmtx = null;
		}

		final DirectoryEntry glyfEntry = this.tableDirectory.getEntryByTag(Table.GLYF);
		if (glyfEntry != null) {
			// TrueType
			final DirectoryEntry locaEntry = this.tableDirectory.getEntryByTag(Table.LOCA);
			if (locaEntry != null) {
				this.loca = LocaTable.read(locaEntry, raf, this.maxp.getNumGlyphs(),
						this.head.getIndexToLocFormat() == 0);
				this.glyf = new GlyfTable(glyfEntry, this.loca, raf);
			} else {
				throw new IOException("Missing loca table for TrueType font.");
			}
			this.glyphList = new TrueTypeGlyphList(this.glyf, this.head, this.maxp);
		} else {
			this.loca = null;
			this.glyf = null;
			final CFFTable cff = (CFFTable) this.getTable(Table.CFF);
			if (cff != null) {
				this.glyphList = new CFFGlyphList(cff, this.head, this.maxp);
			} else {
				throw new IOException("Unsupported font file format.");
			}
		}
	}

	/**
	 * Returns a font table by its four-character tag constant defined in
	 * {@link Table}.  Certain tables ({@code hmtx}, {@code vmtx}, {@code glyf},
	 * {@code loca}) are pre-loaded during construction and returned directly;
	 * all other tables are loaded on demand and cached.
	 *
	 * @param tableType the table tag (e.g., {@link Table#CMAP}, {@link Table#KERN})
	 * @return the table, or {@code null} if the table is not present or cannot be read
	 */
	public Table getTable(final int tableType) {
		// Special cases for tables that are pre-loaded
		if (tableType == Table.HMTX) {
			return this.hmtx;
		}
		if (tableType == Table.VMTX) {
			return this.vmtx;
		}
		if (tableType == Table.GLYF) {
			return this.glyf;
		}
		if (tableType == Table.LOCA) {
			return this.loca;
		}

		// Load each of the tables
		for (int i = 0; i < this.tableDirectory.getNumTables(); i++) {
			final DirectoryEntry entry = this.tableDirectory.getEntry(i);
			if (entry.tag() == tableType) {
				if (this.tables[i] == null) {
					try {
						this.tables[i] = TableFactory.create(this, entry, this.raf);
					} catch (final IOException e) {
						LOG.log(Level.WARNING, "Can't read font file.", e);
						return null;
					}
				}
				return this.tables[i];
			}
		}
		return null;
	}

	/**
	 * Returns the typographic ascender value from the {@code hhea} table.
	 *
	 * @return the ascender in font units
	 */
	public int getAscent() {
		return this.hhea.getAscender();
	}

	/**
	 * Returns the typographic descender value from the {@code hhea} table.
	 * The value is typically negative.
	 *
	 * @return the descender in font units
	 */
	public int getDescent() {
		return this.hhea.getDescender();
	}

	/**
	 * Returns the total number of glyphs in the font as reported by the
	 * {@code maxp} table.
	 *
	 * @return the number of glyphs
	 */
	public int getNumGlyphs() {
		return this.maxp.getNumGlyphs();
	}

	/**
	 * Returns the glyph at the given glyph index (GID).
	 *
	 * @param i the glyph index
	 * @return the {@link Glyph}, or {@code null} if the index is out of range
	 */
	public Glyph getGlyph(final int i) {
		return this.glyphList.getGlyph(i);
	}

	/**
	 * Returns the table directory of this font.
	 *
	 * @return the {@link TableDirectory}
	 */
	public TableDirectory getTableDirectory() {
		return this.tableDirectory;
	}

	/**
	 * Releases the underlying file handle and, if a close callback was provided,
	 * invokes it.  Calling {@code close()} more than once is safe.
	 */
	@Override
	public void close() {
		this.cleanable.clean();
	}
}
