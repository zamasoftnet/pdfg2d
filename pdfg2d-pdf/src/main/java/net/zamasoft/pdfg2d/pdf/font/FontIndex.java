package net.zamasoft.pdfg2d.pdf.font;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import java.util.List;
import java.util.Map;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.font.table.GenericCmapFormat;
import net.zamasoft.pdfg2d.font.table.UvsCmapFormat;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.gc.font.Panose;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.OpenTypeCIDIdentityFontSource;

/**
 * Persistent index for font directory scanning (2026-08-01).
 *
 * <p>
 * Previously, {@code <font-dir>} in fonts.xml opened every font file and parsed name/OS2/cmap, etc.
 * on every JVM startup (O(number of files), about one second for 290 fonts and tens of seconds
 * for thousands). This index persists selection metadata (name, aliases, weight/italic/PANOSE,
 * metrics, compressed cmap) keyed by (path, size, mtime, scan conditions).
 * On a hit, reconstructs {@link OpenTypeFontSource} without opening the font file.
 * The file opens only when actual glyph data is needed.
 * </p>
 *
 * <p>
 * The configuration file (fonts.xml) format is unchanged: uses the {@code fonts.xml.db} path
 * already passed by {@code DirectSession} (an argument long ignored).
 * Index read failures, version mismatches, and stale entries all silently fall back
 * to parsing only the affected entry as before.
 * Writes via a temporary file and rename to avoid leaving a corrupt index.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class FontIndex {
	private static final Logger LOG = Logger.getLogger(FontIndex.class.getName());

	/** Increment when the format changes (older versions are silently discarded and rebuilt). */
	private static final int MAGIC = 0x43504649; // "CPFI"
	/**
	 * 2: font-dir scans derive italic/weight from OS/2 (2026-08-27,
	 * see FontLoader.readTTF's Javadoc). Discard and rebuild old indexes,
	 * which restore fixed normal/400 values.
	 * 3: records the width class (OS/2 usWidthClass) (2026-08-29, font-stretch selection).
	 * Added one widthClass byte to each record.
	 * 4: discards localized names stored in old indexes to reflect the preference
	 * for ASCII PostScript names in name ID 6 (2026-09-01).
	 * 5: records variable font weight copies (pinned wght coordinates) (2026-10-04).
	 * Added two bytes for wght to each record (0 means unpinned).
	 */
	private static final int VERSION = 5;

	private static final int SUBTYPE_EMBEDDED = 0;
	private static final int SUBTYPE_CID_IDENTITY = 1;

	/** Scan results for one file. */
	static final class FileEntry {
		final long size;
		final long lastModified;
		final String scanKey;
		final int numFonts;
		final List<SourceRecord> sources;

		FileEntry(final long size, final long lastModified, final String scanKey, final int numFonts,
				final List<SourceRecord> sources) {
			this.size = size;
			this.lastModified = lastModified;
			this.scanKey = scanKey;
			this.numFonts = numFonts;
			this.sources = sources;
		}
	}

	/** Reconstruction metadata for one FontSource. */
	static final class SourceRecord {
		final int subtype;
		final Direction direction;
		final int ttcIndex;
		final String fontName;
		final String[] aliases;
		final boolean italic;
		final Weight weight;
		/** OS/2 usWidthClass 1..9(2026-08-29)。 */
		final int widthClass;
		final Panose panose;
		final short upm;
		final BBox bbox;
		final short ascent, descent, spaceAdvance;
		final GenericCmapFormat cmap;
		final UvsCmapFormat uvsCmap;
		/** Pinned wght coordinate for a variable font copy, or 0 otherwise (2026-10-04). */
		final int wght;

		SourceRecord(final int subtype, final Direction direction, final int ttcIndex, final String fontName,
				final String[] aliases, final boolean italic, final Weight weight, final int widthClass,
				final Panose panose, final short upm, final BBox bbox, final short ascent, final short descent,
				final short spaceAdvance, final GenericCmapFormat cmap, final UvsCmapFormat uvsCmap, final int wght) {
			this.subtype = subtype;
			this.direction = direction;
			this.ttcIndex = ttcIndex;
			this.fontName = fontName;
			this.aliases = aliases;
			this.italic = italic;
			this.weight = weight;
			this.widthClass = widthClass;
			this.panose = panose;
			this.upm = upm;
			this.bbox = bbox;
			this.ascent = ascent;
			this.descent = descent;
			this.spaceAdvance = spaceAdvance;
			this.cmap = cmap;
			this.uvsCmap = uvsCmap;
			this.wght = wght;
		}
	}

	private final File file;

	private final Map<String, FileEntry> pathToEntry = new HashMap<>();

	private boolean dirty = false;

	/**
	 * Loads the index. A missing, unreadable, or differently formatted file produces
	 * an empty index (not a fatal error).
	 *
	 * @param file index file (usually fonts.xml.db)
	 */
	public FontIndex(final File file) {
		this.file = file;
		if (file == null || !file.isFile()) {
			return;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
			if (in.readInt() != MAGIC || in.readInt() != VERSION) {
				return;
			}
			final int entryCount = in.readInt();
			for (int i = 0; i < entryCount; ++i) {
				final String path = in.readUTF();
				final long size = in.readLong();
				final long lastModified = in.readLong();
				final String scanKey = in.readUTF();
				final int numFonts = in.readInt();
				// All sources in a file (vertical/horizontal × types) share cmap/UVS,
				// so store them only once in a pool per entry (reduces both index size
				// and heap use during warm reconstruction to about one quarter).
				final GenericCmapFormat[] cmapPool = new GenericCmapFormat[in.readUnsignedShort()];
				for (int j = 0; j < cmapPool.length; ++j) {
					cmapPool[j] = readCmap(in);
				}
				final UvsCmapFormat[] uvsPool = new UvsCmapFormat[in.readUnsignedShort()];
				for (int j = 0; j < uvsPool.length; ++j) {
					uvsPool[j] = readUvs(in);
				}
				final int sourceCount = in.readInt();
				final List<SourceRecord> sources = new ArrayList<>(sourceCount);
				for (int j = 0; j < sourceCount; ++j) {
					sources.add(readSource(in, cmapPool, uvsPool));
				}
				this.pathToEntry.put(path, new FileEntry(size, lastModified, scanKey, numFonts, sources));
			}
		} catch (final Exception e) {
			// Discard and rebuild a corrupt index (do not trust even the portions successfully read).
			LOG.log(Level.WARNING, "Ignoring unreadable font index " + file, e);
			this.pathToEntry.clear();
		}
	}

	private static GenericCmapFormat readCmap(final DataInputStream in) throws IOException {
		final int rangeCount = in.readInt();
		final int[] starts = new int[rangeCount], ends = new int[rangeCount], gids = new int[rangeCount];
		final boolean[] constant = new boolean[rangeCount];
		for (int i = 0; i < rangeCount; ++i) {
			starts[i] = in.readInt();
			ends[i] = in.readInt();
			gids[i] = in.readInt();
			constant[i] = in.readBoolean();
		}
		return new GenericCmapFormat(starts, ends, gids, constant);
	}

	private static UvsCmapFormat readUvs(final DataInputStream in) throws IOException {
		final int pairCount = in.readInt();
		final long[] keys = new long[pairCount];
		final int[] gids = new int[pairCount];
		for (int i = 0; i < pairCount; ++i) {
			keys[i] = in.readLong();
			gids[i] = in.readInt();
		}
		final int selCount = in.readInt();
		final int[] selectors = new int[selCount];
		for (int i = 0; i < selCount; ++i) {
			selectors[i] = in.readInt();
		}
		return new UvsCmapFormat(net.zamasoft.pdfg2d.util.LongIntLookup.fromUnsorted(keys, gids, pairCount),
				selectors);
	}

	private static SourceRecord readSource(final DataInputStream in, final GenericCmapFormat[] cmapPool,
			final UvsCmapFormat[] uvsPool) throws IOException {
		final int subtype = in.readUnsignedByte();
		final Direction direction = Direction.values()[in.readUnsignedByte()];
		final int ttcIndex = in.readInt();
		final String fontName = in.readUTF();
		final int aliasCount = in.readUnsignedShort();
		final String[] aliases = new String[aliasCount];
		for (int i = 0; i < aliasCount; ++i) {
			aliases[i] = in.readUTF();
		}
		final boolean italic = in.readBoolean();
		final Weight weight = Weight.values()[in.readUnsignedByte()];
		final int widthClass = in.readUnsignedByte();
		final byte[] p = new byte[12];
		in.readFully(p);
		final Panose panose = new Panose(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11]);
		final short upm = in.readShort();
		final BBox bbox = new BBox(in.readShort(), in.readShort(), in.readShort(), in.readShort());
		final short ascent = in.readShort();
		final short descent = in.readShort();
		final short spaceAdvance = in.readShort();
		final GenericCmapFormat cmap = cmapPool[in.readUnsignedShort()];
		final int uvsIndex = in.readShort();
		final UvsCmapFormat uvsCmap = uvsIndex < 0 ? null : uvsPool[uvsIndex];
		final int wght = in.readUnsignedShort();
		return new SourceRecord(subtype, direction, ttcIndex, fontName, aliases, italic, weight, widthClass, panose,
				upm, bbox, ascent, descent, spaceAdvance, cmap, uvsCmap, wght);
	}

	/**
	 * Looks for an index entry matching file freshness and scan conditions,
	 * then reconstructs and returns the FontSource sequence if found.
	 *
	 * @param fontFile font file
	 * @param scanKey  scan conditions (digest of types + face attributes)
	 * @return reconstructed FontSource sequence (in recorded order), or null on an index miss
	 */
	public List<FontSource> lookup(final File fontFile, final String scanKey) {
		final FileEntry entry = this.pathToEntry.get(fontFile.getPath());
		if (entry == null || entry.size != fontFile.length() || entry.lastModified != fontFile.lastModified()
				|| !entry.scanKey.equals(scanKey)) {
			return null;
		}
		final List<FontSource> sources = new ArrayList<>(entry.sources.size());
		for (final SourceRecord r : entry.sources) {
			final OpenTypeFontSource source = switch (r.subtype) {
			case SUBTYPE_EMBEDDED -> new OpenTypeEmbeddedCIDFontSource(fontFile, r.ttcIndex, r.direction, r.upm,
					r.bbox, r.fontName, r.aliases, r.italic, r.weight, r.panose, r.ascent, r.descent, r.spaceAdvance,
					r.cmap, r.uvsCmap);
			case SUBTYPE_CID_IDENTITY -> new OpenTypeCIDIdentityFontSource(fontFile, r.ttcIndex, r.direction, r.upm,
					r.bbox, r.fontName, r.aliases, r.italic, r.weight, r.panose, r.ascent, r.descent, r.spaceAdvance,
					r.cmap, r.uvsCmap);
			default -> throw new IllegalStateException(String.valueOf(r.subtype));
			};
			// Restore width class through a setter without adding reconstruction constructor arguments (2026-08-29).
			source.setWidthClass(r.widthClass);
			if (r.wght != 0) {
				source.setVariation(Map.of("wght", (double) r.wght));
			}
			sources.add(source);
		}
		return sources;
	}

	/**
	 * Records scan results in the index. If sources include non-OpenType fonts,
	 * excludes the file from indexing (parses normally again next time).
	 *
	 * @param fontFile font file
	 * @param scanKey  scan conditions
	 * @param numFonts number of fonts in the TTC
	 * @param sources  source sequence built by the scan (face attributes already applied)
	 */
	public void put(final File fontFile, final String scanKey, final int numFonts,
			final List<FontSource> sources) {
		final List<SourceRecord> records = new ArrayList<>(sources.size());
		for (final FontSource source : sources) {
			final int subtype;
			if (source instanceof OpenTypeEmbeddedCIDFontSource) {
				subtype = SUBTYPE_EMBEDDED;
			} else if (source instanceof OpenTypeCIDIdentityFontSource) {
				subtype = SUBTYPE_CID_IDENTITY;
			} else {
				// Do not index Type1 and similar fonts accessed through AWT, which cannot be reconstructed.
				return;
			}
			final OpenTypeFontSource ot = (OpenTypeFontSource) source;
			if (ot.getPanose() == null || ot.getCmapFormat() == null) {
				// Do not index files lacking the metadata needed for reconstruction.
				return;
			}
			final Map<String, Double> variation = ot.getVariation();
			int wght = 0;
			if (variation != null) {
				final Double w = variation.get("wght");
				if (variation.size() != 1 || w == null || w < 1 || w > 1000 || w != Math.rint(w)) {
					// The index can restore only integer wght coordinates.
					return;
				}
				wght = w.intValue();
			}
			records.add(new SourceRecord(subtype, ot.getDirection(), ot.getIndex(), ot.getFontName(),
					ot.getAliases(), ot.isItalic(), ot.getWeight(), ot.getWidthClass(), ot.getPanose(),
					ot.getUnitsPerEm(), ot.getBBox(),
					ot.getAscent(), ot.getDescent(), ot.getSpaceAdvance(), ot.getCmapFormat(),
					ot.getUvsCmapFormat(), wght));
		}
		this.pathToEntry.put(fontFile.getPath(),
				new FileEntry(fontFile.length(), fontFile.lastModified(), scanKey, numFonts, records));
		this.dirty = true;
	}

	/**
	 * Writes the index to a file if changed (temporary file + rename).
	 * Failure only produces a warning (slows the next startup but does not affect functionality).
	 */
	public void save() {
		if (!this.dirty || this.file == null) {
			return;
		}
		final File tmp = new File(this.file.getPath() + ".tmp");
		try {
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
				out.writeInt(MAGIC);
				out.writeInt(VERSION);
				out.writeInt(this.pathToEntry.size());
				for (final Map.Entry<String, FileEntry> e : this.pathToEntry.entrySet()) {
					final FileEntry entry = e.getValue();
					out.writeUTF(e.getKey());
					out.writeLong(entry.size);
					out.writeLong(entry.lastModified);
					out.writeUTF(entry.scanKey);
					out.writeInt(entry.numFonts);
					// cmap/UVS pool deduplicated by identity (through FontFile,
					// all sources for the same file share the same instances).
					final java.util.IdentityHashMap<GenericCmapFormat, Integer> cmapToIndex = new java.util.IdentityHashMap<>();
					final java.util.IdentityHashMap<UvsCmapFormat, Integer> uvsToIndex = new java.util.IdentityHashMap<>();
					final List<GenericCmapFormat> cmapPool = new ArrayList<>();
					final List<UvsCmapFormat> uvsPool = new ArrayList<>();
					for (final SourceRecord r : entry.sources) {
						if (cmapToIndex.putIfAbsent(r.cmap, cmapPool.size()) == null) {
							cmapPool.add(r.cmap);
						}
						if (r.uvsCmap != null && uvsToIndex.putIfAbsent(r.uvsCmap, uvsPool.size()) == null) {
							uvsPool.add(r.uvsCmap);
						}
					}
					out.writeShort(cmapPool.size());
					for (final GenericCmapFormat cmap : cmapPool) {
						writeCmap(out, cmap);
					}
					out.writeShort(uvsPool.size());
					for (final UvsCmapFormat uvs : uvsPool) {
						writeUvs(out, uvs);
					}
					out.writeInt(entry.sources.size());
					for (final SourceRecord r : entry.sources) {
						writeSource(out, r, cmapToIndex.get(r.cmap),
								r.uvsCmap == null ? -1 : uvsToIndex.get(r.uvsCmap));
					}
				}
			}
			if (!tmp.renameTo(this.file)) {
				// On Windows, renaming over an existing file fails: delete it first.
				this.file.delete();
				if (!tmp.renameTo(this.file)) {
					throw new IOException("rename failed: " + tmp + " -> " + this.file);
				}
			}
			this.dirty = false;
		} catch (final Exception e) {
			LOG.log(Level.WARNING, "Failed to save font index " + this.file, e);
			tmp.delete();
		}
	}

	private static void writeCmap(final DataOutputStream out, final GenericCmapFormat cmap) throws IOException {
		out.writeInt(cmap.starts().length);
		for (int i = 0; i < cmap.starts().length; ++i) {
			out.writeInt(cmap.starts()[i]);
			out.writeInt(cmap.ends()[i]);
			out.writeInt(cmap.gids()[i]);
			out.writeBoolean(cmap.constant()[i]);
		}
	}

	private static void writeUvs(final DataOutputStream out, final UvsCmapFormat uvs) throws IOException {
		final net.zamasoft.pdfg2d.util.LongIntLookup lookup = uvs.codeToGlyphId();
		out.writeInt(lookup.size());
		for (int i = 0; i < lookup.size(); ++i) {
			out.writeLong(lookup.keyAt(i));
			out.writeInt(lookup.valueAt(i));
		}
		out.writeInt(uvs.selectors().length);
		for (final int sel : uvs.selectors()) {
			out.writeInt(sel);
		}
	}

	private static void writeSource(final DataOutputStream out, final SourceRecord r, final int cmapPoolIndex,
			final int uvsPoolIndex) throws IOException {
		out.writeByte(r.subtype);
		out.writeByte(r.direction.ordinal());
		out.writeInt(r.ttcIndex);
		out.writeUTF(r.fontName);
		out.writeShort(r.aliases.length);
		for (final String alias : r.aliases) {
			out.writeUTF(alias);
		}
		out.writeBoolean(r.italic);
		out.writeByte(r.weight.ordinal());
		out.writeByte(r.widthClass);
		out.writeByte(r.panose.familyClassId());
		out.writeByte(r.panose.familySubclass());
		out.writeByte(r.panose.familyType());
		out.writeByte(r.panose.serifStyle());
		out.writeByte(r.panose.weight());
		out.writeByte(r.panose.proportion());
		out.writeByte(r.panose.contrast());
		out.writeByte(r.panose.strokeVariation());
		out.writeByte(r.panose.armStyle());
		out.writeByte(r.panose.letterForm());
		out.writeByte(r.panose.midline());
		out.writeByte(r.panose.xHeight());
		out.writeShort(r.upm);
		out.writeShort(r.bbox.llx());
		out.writeShort(r.bbox.lly());
		out.writeShort(r.bbox.urx());
		out.writeShort(r.bbox.ury());
		out.writeShort(r.ascent);
		out.writeShort(r.descent);
		out.writeShort(r.spaceAdvance);
		out.writeShort(cmapPoolIndex);
		out.writeShort(uvsPoolIndex);
		out.writeShort(r.wght);
	}
}
