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

	/** {@link #hasVerticalLayout()} の結果(未計算なら null)。 */
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
				// 16/17はtypographic family/subfamily(2026-08-27)。可変フォント
				// の多くはlegacy family(1)が既定インスタンス名込み
				// (例: Bitterのname1="Bitter Thin")で、16の"Bitter"を
				// 拾わないと素のファミリ名で照合できない
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
	 * name ID 6のうち、PDF/CFFにそのまま使えるASCII PostScript名を
	 * 優先します。同じフォントにASCIIとローカライズ済みの名が両方ある場合、
	 * レコード順の最後の名で上書きすると非ASCII名が選ばれていた。
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
	 * 可変フォントの軸を固定する座標です(2026-10-04)。null なら{@link #file}をそのまま使う。
	 *
	 * <p>
	 * 書体ディレクトリの可変フォントは、既定の実体のほかに太さ(wght)を固定した写しを登録する。
	 * 写しの寸法・cmap は既定の実体のものを借り、静的フォントは字を描く・幅を測るときに初めて作る
	 * ({@link #instanceFile})。Google Fonts 一式のように可変フォントが数百あっても、起動時に
	 * 実体化しない。
	 * </p>
	 */
	private Map<String, Double> variation;

	/** 実体化した静的フォントの一時ファイル(ファイルの鮮度+座標→ファイル)。 */
	private static final Map<String, File> INSTANCES = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * 可変フォントの軸を固定します。単一フォント(TTC でない)のファイルに限ります。
	 *
	 * @param variation 軸タグ→ユーザー座標(例: {@code {"wght": 700}})、null で解除
	 */
	public void setVariation(final Map<String, Double> variation) {
		this.variation = variation == null ? null : Map.copyOf(variation);
	}

	/**
	 * 可変フォントの固定座標を返します。
	 *
	 * @return 軸タグ→ユーザー座標、固定していなければ null
	 */
	public Map<String, Double> getVariation() {
		return this.variation;
	}

	private static File instanceFile(final File file, final Map<String, Double> variation) {
		final String key = file.getPath() + '\u0000' + file.lastModified() + '\u0000'
				+ new java.util.TreeMap<>(variation);
		return INSTANCES.computeIfAbsent(key, k -> {
			try (final FontFile fontFile = new FontFile(file)) {
				// 書体ディレクトリの書体の写しはプロセスの間使い回すので、消すのは終わるとき
				final File instance = net.zamasoft.pdfg2d.font.VariableFontInstancer
						.instantiate(fontFile.getSfntFile(), variation);
				instance.deleteOnExit();
				return instance;
			} catch (final Exception e) {
				// 作れなければ既定の実体で組む(太さは合わないが字は出る)
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
				if (fontFile != loadedFontFile) {
					// 並行ロードに負けた側は解凍した一時ファイルごと捨てる
					loadedFontFile.close();
				}
			}
			return fontFile.getFont(index);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * {@code file} から開いた書体をキャッシュから外して閉じます(2026-10-05)。文書ごとに作った書体ファイル
	 * ({@code @font-face} の取得結果・可変フォントの写し)の持ち主が、消す前に呼ぶ。外さないと、キャッシュが
	 * 解凍した一時ファイルを握ったまま、常駐するサーバーに溜まった。
	 *
	 * @param file 書体ファイル
	 */
	public static void release(final File file) {
		final java.lang.ref.SoftReference<FontFile> ref;
		synchronized (fileToFont) {
			ref = fileToFont.remove(file.getPath());
		}
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
	 * 縦組みで横倒しにする半角の字か(U+00FF 以下と半角形 U+FF60〜U+FFDF)。縦組みの元は{@link #canDisplay(int)}で
	 * これらを外し(横組みの書体を回して置く)、{@code text-orientation: upright}のときだけ受け持つ
	 * ({@link #canDisplayUpright(int)})。
	 *
	 * @param c 字
	 * @return 半角の字なら{@code true}
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
	 * 縦組のmixed用除外を掛けず、基礎cmapに字形があるかを返す。
	 * text-orientation: uprightのフォント選択だけが使う。
	 *
	 * <p>
	 * 縦組みの組み方を持たない書体({@link #hasVerticalLayout()}が偽、STIX Two Text のような欧文書体)は
	 * 正立の半角の字を受け持たない(2026-10-06)。縦の送りも原点も無く横組みの書体として書き出されるので、
	 * 正立に置くと字が次の字に重なった。font-family の次の書体へ回す。
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
	 * cmapに登録があるのに字形が空かどうかを返します。
	 *
	 * <p>
	 * 空の字形をcmapに載せたフォントが実在します——JejuGothic /
	 * JejuHallasan / JejuMyeongjoは漢字(代表字の{@code 漢}を含む)が全て
	 * 中身の無いglyfを指し、Adobe Blankは全字がそうです。cmapだけを見て
	 * 「表示できる」と答えると、代替フォントを探さず警告も出ないまま
	 * **文字が黙って消えます**(2026-09-01、ハングルのフォント見本で発覚)。
	 * </p>
	 *
	 * <p>
	 * 空白・制御・書式文字は字形が無いのが正常なので除外します。
	 * </p>
	 *
	 * @param gid cmapが返したglyph ID
	 * @param c   もとの文字
	 * @return 字形が無く、その文字にとって不正なら{@code true}
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
			// 字形が読めないことを理由にフォントを外さない(従来どおり選ぶ)
			LOG.log(Level.FINE, "Failed to read a glyph: " + this.file, e);
			return false;
		}
	}

	/**
	 * 縦組みの組み方(縦組みの元で、vrt2 か vert の置き換えと vmtx)を持つか(2026-10-06)。
	 * {@link net.zamasoft.pdfg2d.font.otf.OpenTypeFont} が縦組みの書体として組み・書き出す条件と同じ。
	 *
	 * @return 縦組みの書体として使えるなら{@code true}
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
