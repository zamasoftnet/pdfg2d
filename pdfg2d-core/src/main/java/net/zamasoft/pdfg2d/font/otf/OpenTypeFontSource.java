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
	 * パース済みフォントファイルのキャッシュです。
	 *
	 * <p>
	 * 旧実装は{@code WeakHashMap<File, FontFile>}だったが、キーのFile
	 * インスタンスを各FontSourceが{@code this.file}として強参照するため
	 * エントリが決して回収されず、実質的に永久キャッシュだった——
	 * 全宣言フォントの全テーブル(loca・GSUB・name等)がヒープに残る
	 * (290フォントで約66MB、2026-08-01実測)。パス文字列キー+
	 * SoftReference値に変更: 使用中のフォントはヒープ圧まで温存され、
	 * 未使用フォントの表はOutOfMemoryErrorより先に回収される。
	 * </p>
	 */
	protected static final Map<String, java.lang.ref.SoftReference<FontFile>> fileToFont = new java.util.HashMap<>();

	protected final File file;

	protected final int index;

	protected final short upm;

	protected String fontName;

	protected final BBox bbox;

	protected final short ascent, descent, spaceAdvance, stemH, stemV;

	/**
	 * xHeight/capHeightは'x'/'H'のグリフ実データ(glyf/CFF)のパースを
	 * 要するため、初回参照まで遅延します(2026-08-01)。従来は
	 * コンストラクタで計算しており、宣言しただけの全フォントの
	 * グリフ表パースが起動時間に乗っていた。
	 */
	private transient short xHeight, capHeight;

	private transient volatile boolean heightsComputed;

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
				// 16/17はtypographic family/subfamily(2026-08-27)。可変フォント
				// の多くはlegacy family(1)が既定インスタンス名込み
				// (例: Bitterのname1="Bitter Thin")で、16の"Bitter"を
				// 拾わないと素のファミリ名で照合できない
				if (nameId == 1 || nameId == 3 || nameId == 4 || nameId == 16) {
					aliases.add(record.getRecordString());
				} else if (nameId == 6) {
					fontName = record.getRecordString();
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
			// font-stretchの書体選択用(2026-08-29)。範囲外はsetter側で通常幅へ
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
	 * 永続フォント索引からの再構築コンストラクタです(2026-08-01)。
	 * ファイルI/Oを一切行わない——グリフ実データが必要になったとき
	 * ({@link #getOpenTypeFont()}経由)に初めてフォントファイルを開く。
	 *
	 * @param file         フォントファイル(この時点では開かない)
	 * @param index        TTC内インデックス
	 * @param direction    組方向
	 * @param upm          units per em
	 * @param bbox         フォントBBox(1000upm正規化済み)
	 * @param fontName     PostScript名
	 * @param aliases      別名(ソート済み)
	 * @param italic       斜体か
	 * @param weight       ウェイト
	 * @param panose       PANOSE分類
	 * @param ascent       アセント(1000upm正規化済み)
	 * @param descent      ディセント(同)
	 * @param spaceAdvance 空白幅(同)
	 * @param cmap         文字→GID写像(圧縮範囲)
	 * @param uvsCmap      UVS写像、無ければnull
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
			final String key = file.getPath();
			var fontFile = getCachedFontFile(key, timestamp);
			if (fontFile == null) {
				final var loadedFontFile = new FontFile(file);
				synchronized (fileToFont) {
					// 並行ロードの勝者を採る(従来と同じdouble-check)
					final var ref = fileToFont.get(key);
					fontFile = ref == null ? null : ref.get();
					if (fontFile == null || fontFile.timestamp != timestamp) {
						fileToFont.put(key, new java.lang.ref.SoftReference<>(loadedFontFile));
						fontFile = loadedFontFile;
					}
				}
			}
			return fontFile.getFont(index);
		} catch (final Exception e) {
			throw new RuntimeException(e);
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

	/**
	 * 縦組のmixed用除外を掛けず、基礎cmapに字形があるかを返す。
	 * text-orientation: uprightのフォント選択だけが使う。
	 */
	public boolean canDisplayUpright(final int c) {
		if (this.cmap.mapCharCode(c) != 0) {
			return true;
		}
		return this.uvsCmap != null && this.uvsCmap.isVarSelector(c);
	}

	@Override
	public Font createFont() {
		return new OpenTypeFontImpl(this);
	}
}
