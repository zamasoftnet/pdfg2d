package net.zamasoft.pdfg2d.font.otf;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.font.FontFile;
import net.zamasoft.pdfg2d.font.OpenTypeFont;
import net.zamasoft.pdfg2d.font.table.CmapTable;
import net.zamasoft.pdfg2d.font.table.GenericCmapFormat;
import net.zamasoft.pdfg2d.font.table.HeadTable;
import net.zamasoft.pdfg2d.font.table.HheaTable;
import net.zamasoft.pdfg2d.font.table.NameTable;
import net.zamasoft.pdfg2d.font.table.Os2Table;
import net.zamasoft.pdfg2d.font.table.Table;
import net.zamasoft.pdfg2d.font.table.UvsCmapFormat;
import net.zamasoft.pdfg2d.font.table.XmtxTable;
import net.zamasoft.pdfg2d.font.AbstractFontSource;
import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.Panose;
import net.zamasoft.pdfg2d.gc.text.util.TextUtils;

/**
 * Represents a source of an OpenType font loaded from a font file.
 * <p>
 * This class reads font metadata (metrics, glyph data, etc.) from a TTF/OTF
 * file at construction time, caching the resulting {@code OpenTypeFont} instance
 * in a weak-reference map keyed by file path.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class OpenTypeFontSource extends AbstractFontSource {
	private static final Logger LOG = Logger.getLogger(OpenTypeFontSource.class.getName());

	private static final long serialVersionUID = 4L;

	/**
	 * Cache of parsed font files.
	 *
	 * <p>
	 * The old implementation used {@code WeakHashMap<File, FontFile>}, but each FontSource
	 * held a strong reference to the File key instance as {@code this.file}, so entries
	 * were never collected, making the cache effectively permanent.
	 * All tables (loca, GSUB, name, etc.) of all declared fonts stayed on the heap
	 * (about 66 MB for 290 fonts, measured on 2026-08-01). Changed to path string keys
	 * and SoftReference values: fonts in use remain until heap pressure arises, and
	 * tables of unused fonts are collected before an OutOfMemoryError.
	 * </p>
	 */
	protected static final Map<String, java.lang.ref.SoftReference<FontFile>> fileToFont = new java.util.HashMap<>();

	/**
	 * How long a font file's last-modified time read from the disk is trusted (2026-10-08).
	 *
	 * <p>
	 * The cache above reloads a font whose file was replaced on the disk while the process runs (a server whose font
	 * directory is updated). That check read {@link File#lastModified()} on every {@link #getOpenTypeFont()}, which
	 * runs per character and per fallback candidate: a stat call each time. It became visible once {@code @font-face}
	 * fonts were used under the default font policy (rustdoc-std on WSL: about 15 seconds with the document on ext4
	 * and over 300 seconds with it on a DrvFs mount, 8 and 11 seconds after this change). The time is now read at
	 * most once per second per file, so a replaced file is still picked up, within a second.
	 * </p>
	 */
	private static final long LAST_MODIFIED_TTL_NANOS = 1_000_000_000L;

	/** A last-modified time read from the disk and when it was read ({@link System#nanoTime()}). */
	private record LastModified(long time, long readAt) {
	}

	/** Path -> the last-modified time last read for it. */
	private static final Map<String, LastModified> LAST_MODIFIED = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * The last-modified time of {@code file}, read from the disk at most once per
	 * {@link #LAST_MODIFIED_TTL_NANOS} (2026-10-08).
	 */
	private static long lastModified(final File file) {
		final String path = file.getPath();
		final long now = System.nanoTime();
		final LastModified known = LAST_MODIFIED.get(path);
		if (known != null && now - known.readAt() < LAST_MODIFIED_TTL_NANOS) {
			return known.time();
		}
		final long time = file.lastModified();
		LAST_MODIFIED.put(path, new LastModified(time, now));
		return time;
	}

	protected final File file;

	protected final int index;

	protected final short upm;

	protected String fontName;

	protected final BBox bbox;

	protected final short ascent, descent, spaceAdvance, stemH, stemV;

	/**
	 * Defers xHeight/capHeight until first access because they require parsing
	 * the actual 'x'/'H' glyph data (glyf/CFF) (2026-08-01). Previously computed
	 * in the constructor, so parsing glyph tables for every declared font
	 * added to startup time.
	 */
	private transient short xHeight, capHeight;

	private transient volatile boolean heightsComputed;

	/** Result of {@link #hasVerticalLayout()} (null if not yet computed). */
	private transient volatile Boolean verticalLayout;

	protected Panose panose;

	protected final Direction direction;

	protected final GenericCmapFormat cmap;

	protected final UvsCmapFormat uvsCmap;

	/**
	 * Creates a new OpenTypeFontSource.
	 * 
	 * @param file      the font file
	 * @param index     the font index within the file
	 * @param direction the layout direction
	 * @throws IOException if an error occurs while reading the font file
	 */
	public OpenTypeFontSource(final File file, final int index, final Direction direction) throws IOException {
		this.index = index;
		this.file = file;
		final var ttFont = this.getOpenTypeFont();

		// Font metric information
		{
			final var head = (HeadTable) ttFont.getTable(Table.HEAD);
			this.upm = head.getUnitsPerEm();
			final short llx = (short) (head.getXMin() * FontSource.DEFAULT_UNITS_PER_EM / this.upm);
			final short lly = (short) (head.getYMin() * FontSource.DEFAULT_UNITS_PER_EM / this.upm);
			final short urx = (short) (head.getXMax() * FontSource.DEFAULT_UNITS_PER_EM / this.upm);
			final short ury = (short) (head.getYMax() * FontSource.DEFAULT_UNITS_PER_EM / this.upm);
			this.bbox = new BBox(llx, lly, urx, ury);
			this.setItalic((head.getMacStyle() & 2) != 0);
		}

		final Set<String> aliases = new TreeSet<>();
		String fontName = null;
		{
			final var name = (NameTable) ttFont.getTable(Table.NAME);
			fontName = selectPostScriptName(name);
			for (int i = 0; i < name.size(); ++i) {
				final var record = name.get(i);
				final short nameId = record.getNameId();
				// 16/17 are the typographic family/subfamily (2026-08-27). Many variable fonts
				// include the default instance name in the legacy family (1)
				// (e.g. Bitter has name1="Bitter Thin"), so without picking up "Bitter" from 16,
				// the plain family name cannot match.
				if (nameId == 1 || nameId == 3 || nameId == 4 || nameId == 16) {
					aliases.add(record.getRecordString());
				}
			}
		}
		this.aliases = aliases.toArray(new String[0]);

		if (fontName == null) {
			throw new IOException("Font has no PostScript name: " + file);
		}
		this.fontName = fontName;

		{
			final var os2 = (Os2Table) ttFont.getTable(Table.OS_2);
			final var weight = TextUtils.decodeFontWeight((short) os2.getWeightClass());
			this.setWeight(weight);
			// For font-stretch selection (2026-08-29). The setter defaults out-of-range values to normal width.
			this.setWidthClass(os2.getWidthClass());
			final short cFamilyClass = os2.getFamilyClass();
			final var panose = os2.getPanose();
			this.panose = new Panose(cFamilyClass, panose.code());
		}

		{
			final var hhea = (HheaTable) ttFont.getTable(Table.HHEA);
			this.ascent = (short) (hhea.getAscender() * FontSource.DEFAULT_UNITS_PER_EM / this.upm);
			this.descent = (short) (-hhea.getDescender() * FontSource.DEFAULT_UNITS_PER_EM / this.upm);
		}

		final var cmapt = (CmapTable) ttFont.getTable(Table.CMAP);
		var cmap = (GenericCmapFormat) cmapt.getCmapFormat(Table.PLATFORM_MICROSOFT, Table.ENCODING_UCS4);
		if (cmap == null) {
			cmap = (GenericCmapFormat) cmapt.getCmapFormat(Table.PLATFORM_MICROSOFT, Table.ENCODING_UCS2);
		}
		if (cmap == null) {
			cmap = (GenericCmapFormat) cmapt.getCmapFormat(Table.PLATFORM_UNICODE, Table.ENCODING_BMP);
		}
		if (cmap == null) {
			cmap = (GenericCmapFormat) cmapt.getCmapFormat(Table.PLATFORM_UNICODE, Table.ENCODING_NON_BMP);
		}
		if (cmap == null) {
			cmap = (GenericCmapFormat) cmapt.getCmapFormat(Table.PLATFORM_UNICODE, Table.ENCODING_UNDEFINED);
		}
		if (cmap == null) {
			cmap = (GenericCmapFormat) cmapt.getCmapFormat(Table.PLATFORM_UNICODE, (short) -1);
		}
		this.cmap = cmap;

		this.uvsCmap = (UvsCmapFormat) cmapt.getCmapFormat(Table.PLATFORM_UNICODE, Table.ENCODING_UVS);

		{
			final int gid = this.cmap.mapCharCode(' ');
			final var hmtx = (XmtxTable) ttFont.getTable(Table.HMTX);
			this.spaceAdvance = (short) (hmtx.getAdvanceWidth(gid) * FontSource.DEFAULT_UNITS_PER_EM / this.upm);
		}

		this.stemH = 0;
		this.stemV = 0;

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("new font: " + this.getFontName());
		}

		this.direction = direction;
	}

	/**
	 * Prefers an ASCII PostScript name among name ID 6 records that can be used directly in PDF/CFF.
	 * When a font had both ASCII and localized names, overwriting with the last name
	 * in record order selected a non-ASCII name.
	 */
	static String selectPostScriptName(final NameTable name) {
		String asciiName = null;
		int asciiPriority = Integer.MAX_VALUE;
		String fallbackName = null;
		int fallbackPriority = Integer.MAX_VALUE;
		for (int i = 0; i < name.size(); ++i) {
			final var record = name.get(i);
			if (record.getNameId() != Table.NAME_POSTSCRIPT_NAME) {
				continue;
			}
			final String candidate = record.getRecordString();
			if (candidate == null || candidate.isEmpty()) {
				continue;
			}
			final int priority = postScriptNamePriority(record.platformId(), record.encodingId(), false);
			if (priority < fallbackPriority) {
				fallbackName = candidate;
				fallbackPriority = priority;
			}
			if (isSafeAsciiPostScriptName(candidate)) {
				final int safePriority = postScriptNamePriority(record.platformId(), record.encodingId(), true);
				if (safePriority < asciiPriority) {
					asciiName = candidate;
					asciiPriority = safePriority;
				}
			}
		}
		return asciiName != null ? asciiName : fallbackName;
	}

	private static int postScriptNamePriority(final short platformId, final short encodingId,
			final boolean ascii) {
		if (ascii && platformId == Table.PLATFORM_MACINTOSH && encodingId == Table.ENCODING_ROMAN) {
			return 0;
		}
		if (platformId == Table.PLATFORM_MICROSOFT
				&& (encodingId == Table.ENCODING_UCS2 || encodingId == Table.ENCODING_UCS4)) {
			return ascii ? 1 : 0;
		}
		if (platformId == Table.PLATFORM_UNICODE) {
			return ascii ? 2 : 1;
		}
		if (!ascii && platformId == Table.PLATFORM_MACINTOSH && encodingId == Table.ENCODING_ROMAN) {
			return 2;
		}
		if (platformId == Table.PLATFORM_ISO && encodingId == Table.ENCODING_ASCII) {
			return 3;
		}
		return 4;
	}

	private static boolean isSafeAsciiPostScriptName(final String name) {
		for (int i = 0; i < name.length(); ++i) {
			final char c = name.charAt(i);
			if (c < '!' || c > '~' || c == '#' || c == '(' || c == ')' || c == '<' || c == '>' || c == '['
					|| c == ']' || c == '{' || c == '}' || c == '/' || c == '%') {
				return false;
			}
		}
		return !name.isEmpty();
	}

	/**
	 * Constructor for reconstruction from the persistent font index (2026-08-01).
	 * Performs no file I/O: opens the font file only when actual glyph data is needed
	 * (through {@link #getOpenTypeFont()}).
	 *
	 * @param file         font file (not opened at this point)
	 * @param index        index in the TTC
	 * @param direction    writing direction
	 * @param upm          units per em
	 * @param bbox         font BBox (normalized to 1000 upm)
	 * @param fontName     PostScript name
	 * @param aliases      aliases (sorted)
	 * @param italic       whether italic
	 * @param weight       weight
	 * @param panose       PANOSE classification
	 * @param ascent       ascent (normalized to 1000 upm)
	 * @param descent      descent (same normalization)
	 * @param spaceAdvance space advance (same normalization)
	 * @param cmap         character-to-GID mapping (compressed ranges)
	 * @param uvsCmap      UVS mapping, or null if absent
	 */
	protected OpenTypeFontSource(final File file, final int index, final Direction direction, final short upm,
			final BBox bbox, final String fontName, final String[] aliases, final boolean italic,
			final net.zamasoft.pdfg2d.gc.font.FontStyle.Weight weight, final Panose panose, final short ascent,
			final short descent, final short spaceAdvance, final GenericCmapFormat cmap,
			final UvsCmapFormat uvsCmap) {
		this.file = file;
		this.index = index;
		this.direction = direction;
		this.upm = upm;
		this.bbox = bbox;
		this.fontName = fontName;
		this.aliases = aliases;
		this.setItalic(italic);
		this.setWeight(weight);
		this.panose = panose;
		this.ascent = ascent;
		this.descent = descent;
		this.spaceAdvance = spaceAdvance;
		this.cmap = cmap;
		this.uvsCmap = uvsCmap;
		this.stemH = 0;
		this.stemV = 0;
	}

	/**
	 * Returns the font index within the file (TTC collections).
	 *
	 * @return the zero-based font index
	 */
	public int getIndex() {
		return this.index;
	}

	/**
	 * Returns the font file. Together with {@link #getIndex()} it identifies the
	 * face, for example to hand the same face to a library that draws through
	 * AWT (MathML).
	 *
	 * @return the font file
	 */
	public File getFile() {
		return this.variation == null ? this.file : instanceFile(this.file, this.variation);
	}

	/**
	 * Returns the OpenType font instance.
	 *
	 * @return the OpenType font
	 */
	public OpenTypeFont getOpenTypeFont() {
		return this.variation == null ? getOpenTypeFont(this.file, this.index)
				: getOpenTypeFont(instanceFile(this.file, this.variation), 0);
	}

	/**
	 * Coordinates that pin variable font axes (2026-10-04). Uses {@link #file} unchanged if null.
	 *
	 * <p>
	 * For variable fonts in a font directory, registers copies with pinned weight (wght) in addition to the default instance.
	 * Copies borrow the default instance's metrics and cmap; the static font is created only when drawing characters
	 * or measuring widths ({@link #instanceFile}). Even with hundreds of variable fonts, such as the entire
	 * Google Fonts collection, no instantiation occurs at startup.
	 * </p>
	 */
	private Map<String, Double> variation;

	/** Temporary files for instantiated static fonts (file freshness + coordinates -> file). */
	private static final Map<String, File> INSTANCES = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Pins variable font axes. Only single-font files (not TTCs) are supported.
	 *
	 * @param variation axis tag -> user coordinate (e.g. {@code {"wght": 700}}), or null to unpin
	 */
	public void setVariation(final Map<String, Double> variation) {
		this.variation = variation == null ? null : Map.copyOf(variation);
	}

	/**
	 * Returns the pinned coordinates of the variable font.
	 *
	 * @return axis tag -> user coordinate, or null if not pinned
	 */
	public Map<String, Double> getVariation() {
		return this.variation;
	}

	private static File instanceFile(final File file, final Map<String, Double> variation) {
		final String key = file.getPath() + '\u0000' + lastModified(file) + '\u0000'
				+ new java.util.TreeMap<>(variation);
		return INSTANCES.computeIfAbsent(key, k -> {
			try (final FontFile fontFile = new FontFile(file)) {
				// Font copies from font directories are reused throughout the process, so delete them only on exit.
				final File instance = net.zamasoft.pdfg2d.font.VariableFontInstancer
						.instantiate(fontFile.getSfntFile(), variation);
				instance.deleteOnExit();
				return instance;
			} catch (final Exception e) {
				// If creation fails, lay out with the default instance (the weight differs, but the characters appear).
				LOG.log(Level.WARNING, "variable font instantiation failed; using default instance: " + file, e);
				return file;
			}
		});
	}

	/**
	 * Returns the PostScript name selected from OpenType name ID 6 records.
	 * Unlike {@link #getFontName()}, this is not affected by a configured family
	 * name override.
	 *
	 * @return the preferred PostScript name, or {@code null} if none exists
	 */
	public String getPostScriptName() {
		final var name = (NameTable) this.getOpenTypeFont().getTable(Table.NAME);
		return selectPostScriptName(name);
	}

	/**
	 * Returns the OpenType font for the given file and index.
	 * 
	 * @param file  the font file
	 * @param index the font index
	 * @return the OpenType font
	 */
	public static OpenTypeFont getOpenTypeFont(final File file, final int index) {
		try {
			final var timestamp = lastModified(file);
			final String key = file.getPath();
			var fontFile = getCachedFontFile(key, timestamp);
			if (fontFile == null) {
				final var loadedFontFile = new FontFile(file);
				// The file may have changed since the time above was read: trust the time the font was loaded with
				LAST_MODIFIED.put(key, new LastModified(loadedFontFile.timestamp, System.nanoTime()));
				synchronized (fileToFont) {
					// Use the winner of concurrent loading (the same double-check as before).
					final var ref = fileToFont.get(key);
					fontFile = ref == null ? null : ref.get();
					if (fontFile == null || fontFile.timestamp != loadedFontFile.timestamp) {
						fileToFont.put(key, new java.lang.ref.SoftReference<>(loadedFontFile));
						fontFile = loadedFontFile;
					}
				}
				if (fontFile != loadedFontFile) {
					// Discard the loser of concurrent loading, including its decompressed temporary files.
					loadedFontFile.close();
				}
			}
			return fontFile.getFont(index);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Removes the font opened from {@code file} from the cache and closes it (2026-10-05). Owners of font files
	 * created per document (fetched {@code @font-face} resources and variable font copies) call this before deletion.
	 * Without removal, the cache retained decompressed temporary files, which accumulated in long-running servers.
	 *
	 * @param file font file
	 */
	public static void release(final File file) {
		final java.lang.ref.SoftReference<FontFile> ref;
		synchronized (fileToFont) {
			ref = fileToFont.remove(file.getPath());
		}
		LAST_MODIFIED.remove(file.getPath());
		final FontFile fontFile = ref == null ? null : ref.get();
		if (fontFile != null) {
			fontFile.close();
		}
	}

	private static FontFile getCachedFontFile(final String key, final long timestamp) {
		synchronized (fileToFont) {
			final var ref = fileToFont.get(key);
			final var fontFile = ref == null ? null : ref.get();
			return fontFile != null && fontFile.timestamp == timestamp ? fontFile : null;
		}
	}

	@Override
	public Direction getDirection() {
		return this.direction;
	}

	/**
	 * Overrides the font name.
	 *
	 * @param fontName the new font name
	 */
	public void setFontName(final String fontName) {
		this.fontName = fontName;
	}

	/**
	 * Returns the PANOSE classification for this font.
	 *
	 * @return the PANOSE object
	 */
	public Panose getPanose() {
		return this.panose;
	}

	/**
	 * Overrides the PANOSE classification for this font.
	 *
	 * @param panose the new PANOSE object
	 */
	public void setPanose(final Panose panose) {
		this.panose = panose;
	}

	@Override
	public BBox getBBox() {
		return this.bbox;
	}

	@Override
	public String getFontName() {
		return this.fontName;
	}

	@Override
	public short getXHeight() {
		this.computeHeights();
		return this.xHeight;
	}

	@Override
	public short getCapHeight() {
		this.computeHeights();
		return this.capHeight;
	}

	private void computeHeights() {
		if (this.heightsComputed) {
			return;
		}
		synchronized (this) {
			if (this.heightsComputed) {
				return;
			}
			final var font = this.getOpenTypeFont();
			final var gx = font.getGlyph(this.cmap.mapCharCode('x'));
			this.xHeight = (gx == null || gx.path() == null) ? DEFAULT_X_HEIGHT
					: (short) gx.path().getBounds().height;
			final var gh = font.getGlyph(this.cmap.mapCharCode('H'));
			this.capHeight = (gh == null || gh.path() == null) ? DEFAULT_CAP_HEIGHT
					: (short) gh.path().getBounds().height;
			this.heightsComputed = true;
		}
	}

	@Override
	public short getSpaceAdvance() {
		return this.spaceAdvance;
	}

	@Override
	public short getEmbeddingLicenseFlags() {
		final var os2 = (Os2Table) this.getOpenTypeFont().getTable(Table.OS_2);
		return os2 == null ? 0 : os2.getLicenseType();
	}

	@Override
	public short getAscent() {
		return this.ascent;
	}

	@Override
	public short getDescent() {
		return this.descent;
	}

	@Override
	public short getStemH() {
		return this.stemH;
	}

	@Override
	public short getStemV() {
		return this.stemV;
	}

	/**
	 * Returns the number of font design units per em.
	 *
	 * @return the units-per-em value
	 */
	public short getUnitsPerEm() {
		return this.upm;
	}

	/**
	 * Returns the primary cmap format used for character-to-glyph mapping.
	 *
	 * @return the cmap format
	 */
	public GenericCmapFormat getCmapFormat() {
		return this.cmap;
	}

	/**
	 * Returns the Unicode Variation Sequences cmap format, or {@code null} if
	 * the font does not contain one.
	 *
	 * @return the UVS cmap format, or {@code null}
	 */
	public UvsCmapFormat getUvsCmapFormat() {
		return this.uvsCmap;
	}

	/**
	 * Whether this is a half-width character turned sideways in vertical writing (U+00FF and below, and half-width forms
	 * U+FF60 through U+FFDF). Vertical writing sources exclude these in {@link #canDisplay(int)} (using rotated
	 * horizontal writing fonts) and handle them only for {@code text-orientation: upright}
	 * ({@link #canDisplayUpright(int)}).
	 *
	 * @param c character
	 * @return {@code true} for a half-width character
	 */
	public static boolean isSidewaysHalfWidth(final int c) {
		return c <= 0xFF || (c >= 0xFF60 && c <= 0xFFDF);
	}

	@Override
	public boolean canDisplay(final int c) {
		if (this.getDirection() == Direction.TB && isSidewaysHalfWidth(c)) {
			return false;
		}
		final int gid = this.cmap.mapCharCode(c);
		if (gid != 0) {
			return !this.isGlyphless(gid, c);
		}
		if (this.uvsCmap != null && this.uvsCmap.isVarSelector(c)) {
			return true;
		}
		return false;
	}

	/**
	 * Returns whether the base cmap contains a glyph, without applying exclusions for mixed vertical writing.
	 * Used only for font selection with text-orientation: upright.
	 *
	 * <p>
	 * Fonts without vertical layout ({@link #hasVerticalLayout()} is false, e.g. Latin fonts such as STIX Two Text)
	 * do not handle upright half-width characters (2026-10-06). They have neither vertical advances nor origins
	 * and are output as horizontal writing fonts, so upright glyphs overlapped the next character.
	 * Try the next font in font-family.
	 * </p>
	 */
	@Override
	public boolean canDisplayUpright(final int c) {
		if (!this.hasVerticalLayout()) {
			return this.canDisplay(c);
		}
		final int gid = this.cmap.mapCharCode(c);
		if (gid != 0) {
			return !this.isGlyphless(gid, c);
		}
		return this.uvsCmap != null && this.uvsCmap.isVarSelector(c);
	}

	/**
	 * Returns whether a glyph is empty despite having a cmap entry.
	 *
	 * <p>
	 * Some real fonts list empty glyphs in cmap: all kanji in JejuGothic /
	 * JejuHallasan / JejuMyeongjo (including the representative character {@code 漢})
	 * point to empty glyf data, as do all characters in Adobe Blank. Reporting
	 * "displayable" based only on cmap makes **characters silently disappear**
	 * without searching for a fallback font or issuing a warning
	 * (2026-09-01, found in a Hangul font specimen).
	 * </p>
	 *
	 * <p>
	 * Excludes whitespace, control, and format characters, which normally have no glyph.
	 * </p>
	 *
	 * @param gid glyph ID returned by cmap
	 * @param c   original character
	 * @return {@code true} if the glyph is absent and its absence is invalid for the character
	 */
	private boolean isGlyphless(final int gid, final int c) {
		if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
			return false;
		}
		switch (Character.getType(c)) {
			case Character.CONTROL:
			case Character.FORMAT:
			case Character.SURROGATE:
			case Character.PRIVATE_USE:
			case Character.UNASSIGNED:
				return false;
			default:
				break;
		}
		try {
			final var glyph = this.getOpenTypeFont().getGlyph(gid);
			return glyph == null || glyph.isBlank();
		} catch (final RuntimeException e) {
			// Do not exclude a font because its glyph cannot be read (select it as before).
			LOG.log(Level.FINE, "Failed to read a glyph: " + this.file, e);
			return false;
		}
	}

	/**
	 * Whether vertical layout is available (a vertical writing source with vrt2 or vert substitution and vmtx)
	 * (2026-10-06). These are the same conditions under which
	 * {@link net.zamasoft.pdfg2d.font.otf.OpenTypeFont} lays out and outputs a vertical writing font.
	 *
	 * @return {@code true} if usable as a vertical writing font
	 */
	public boolean hasVerticalLayout() {
		if (this.getDirection() != Direction.TB) {
			return false;
		}
		Boolean layout = this.verticalLayout;
		if (layout == null) {
			final var font = this.getOpenTypeFont();
			final var gsub = (net.zamasoft.pdfg2d.font.table.GsubTable) font.getTable(Table.GSUB);
			layout = gsub != null && font.getTable(Table.VMTX) != null
					&& (!gsub.collectSingleSubstitutions(net.zamasoft.pdfg2d.font.otf.OpenTypeFont.TAG_VRT2).isEmpty()
							|| !gsub.collectSingleSubstitutions(net.zamasoft.pdfg2d.font.otf.OpenTypeFont.TAG_VERT)
									.isEmpty());
			this.verticalLayout = layout;
		}
		return layout;
	}

	@Override
	public Font createFont() {
		return new OpenTypeFontImpl(this);
	}
}
