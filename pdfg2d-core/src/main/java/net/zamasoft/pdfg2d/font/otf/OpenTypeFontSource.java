package net.zamasoft.pdfg2d.font.otf;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
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

	protected static Map<File, FontFile> fileToFont = new WeakHashMap<>();

	protected final File file;

	protected final int index;

	protected final short upm;

	protected String fontName;

	protected final BBox bbox;

	protected final short ascent, descent, xHeight, capHeight, spaceAdvance, stemH, stemV;

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
			for (int i = 0; i < name.size(); ++i) {
				final var record = name.get(i);
				final short nameId = record.getNameId();
				if (nameId == 1 || nameId == 3 || nameId == 4) {
					aliases.add(record.getRecordString());
				} else if (nameId == 6) {
					fontName = record.getRecordString();
				}
			}
		}
		this.aliases = aliases.toArray(new String[0]);

		if (fontName == null) {
			throw new NullPointerException();
		}
		this.fontName = fontName;

		{
			final var os2 = (Os2Table) ttFont.getTable(Table.OS_2);
			final var weight = TextUtils.decodeFontWeight((short) os2.getWeightClass());
			this.setWeight(weight);
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

		{
			final var font = this.getOpenTypeFont();
			final var gx = font.getGlyph(this.cmap.mapCharCode('x'));
			this.xHeight = (gx == null || gx.path() == null) ? DEFAULT_X_HEIGHT
					: (short) gx.path().getBounds().height;
			final var gh = font.getGlyph(this.cmap.mapCharCode('H'));
			this.capHeight = (gh == null || gh.path() == null) ? DEFAULT_CAP_HEIGHT
					: (short) gh.path().getBounds().height;
		}

		this.stemH = 0;
		this.stemV = 0;

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("new font: " + this.getFontName());
		}

		this.direction = direction;
	}

	/**
	 * Returns the OpenType font instance.
	 * 
	 * @return the OpenType font
	 */
	public OpenTypeFont getOpenTypeFont() {
		return getOpenTypeFont(this.file, this.index);
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
			final var timestamp = file.lastModified();
			var fontFile = getCachedFontFile(file, timestamp);
			if (fontFile == null) {
				final var loadedFontFile = new FontFile(file);
				synchronized (fileToFont) {
					fontFile = fileToFont.get(file);
					if (fontFile == null || fontFile.timestamp != timestamp) {
						fileToFont.put(file, loadedFontFile);
						fontFile = loadedFontFile;
					}
				}
			}
			return fontFile.getFont(index);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static FontFile getCachedFontFile(final File file, final long timestamp) {
		synchronized (fileToFont) {
			final var fontFile = fileToFont.get(file);
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
		return this.xHeight;
	}

	@Override
	public short getCapHeight() {
		return this.capHeight;
	}

	@Override
	public short getSpaceAdvance() {
		return this.spaceAdvance;
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

	@Override
	public boolean canDisplay(final int c) {
		if (this.getDirection() == Direction.TB) {
			if (c <= 0xFF || (c >= 0xFF60 && c <= 0xFFDF)) {
				return false;
			}
		}
		if (this.cmap.mapCharCode(c) != 0) {
			return true;
		}
		if (this.uvsCmap != null && this.uvsCmap.isVarSelector(c)) {
			return true;
		}
		return false;
	}

	@Override
	public Font createFont() {
		return new OpenTypeFontImpl(this);
	}
}
