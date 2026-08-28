package net.zamasoft.pdfg2d.pdf.font;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.pdf.font.cid.CMap;
import net.zamasoft.pdfg2d.pdf.font.type1.Encoding;
import net.zamasoft.pdfg2d.pdf.font.type1.GlyphMap;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.resolver.util.URIHelper;

/**
 * fonts.xmlの宣言からフォントデータベースを構築する実行側です
 * (2026-08-01、90点計画増分8——593行のSAXハンドラがXML構文解釈と
 * ファイル解決・フォントパース・索引更新・登録を全部抱えていたのを
 * 分離した)。SAXハンドラ({@link PDFFontSourceManagerConfigurationHandler})は
 * 属性を型へ変換してこのクラスを呼ぶだけになり、こちらはXMLなしで
 * 単体テストできる。
 *
 * @author MIYABE Tatsuhiko
 */
final class FontCatalogBuilder {
	private static final Logger LOG = Logger.getLogger(FontCatalogBuilder.class.getName());

	private final URI base;

	private final SourceResolver resolver;

	/** font-dirスキャンの永続索引(nullなら索引なしで毎回パース)。 */
	private final FontIndex fontIndex;

	private final Map<String, Encoding> nameToEncoding = new HashMap<>();

	private Encoding defaultEncoding;

	private GlyphMap unicodeEncoding;

	private final Map<String, CMap> nameToCMap = new HashMap<>();

	final Map<String, Object> nameToFonts = new HashMap<>();

	final Map<String, FontFamilyList> genericToFamily = new HashMap<>();

	final Collection<FontSource> allFonts = new ArrayList<>();

	FontCatalogBuilder(final URI base, final FontIndex fontIndex) {
		this.base = base;
		this.fontIndex = fontIndex;
		this.resolver = CompositeSourceResolver.createGenericCompositeSourceResolver();
	}

	private Source resolve(final String src) throws IOException, URISyntaxException {
		return this.resolver.resolve(URIHelper.resolve("UTF-8", this.base, src));
	}

	private File toFile(final String src) throws IOException, URISyntaxException {
		final Source source = this.resolve(src);
		try {
			return source.getFile();
		} finally {
			this.resolver.release(source);
		}
	}

	/** {@code <encoding src>}: コードと文字名の対応表を読み込みます。 */
	void addEncoding(final String src) throws IOException, URISyntaxException {
		Source source = null;
		try {
			source = this.resolve(src);
			final Encoding encoding = Encoding.parse(source.getInputStream());
			this.nameToEncoding.put(encoding.name, encoding);
		} finally {
			if (source != null) {
				this.resolver.release(source);
			}
		}
	}

	/** {@code <core-fonts>}: 既定エンコーディングとUnicode対応表を設定します。 */
	void beginCoreFonts(final String encoding, final String unicodeSrc) throws IOException, URISyntaxException {
		this.defaultEncoding = this.nameToEncoding.get(encoding);
		final Source source = this.resolve(unicodeSrc);
		try {
			this.unicodeEncoding = GlyphMap.parse(source.getInputStream());
		} finally {
			this.resolver.release(source);
		}
	}

	/** {@code <letter-font>}: AFMの欧文コアフォントを構築します。 */
	FontSource letterFont(final String src, final String encoding)
			throws IOException, URISyntaxException, java.text.ParseException {
		Source source = null;
		try {
			source = this.resolve(src);
			final Encoding pdfEncoding = encoding != null ? this.nameToEncoding.get(encoding) : this.defaultEncoding;
			return FontLoader.readLetterType1Font(this.unicodeEncoding, pdfEncoding, source.getInputStream());
		} finally {
			if (source != null) {
				this.resolver.release(source);
			}
		}
	}

	/** {@code <symbol-font>}: AFMのシンボルコアフォントを構築します。 */
	FontSource symbolFont(final String src, final String encodingSrc)
			throws IOException, URISyntaxException, java.text.ParseException {
		Source source = null, encodingSource = null;
		try {
			source = this.resolve(src);
			encodingSource = this.resolve(encodingSrc);
			return FontLoader.readSymbolType1Font(source.getInputStream(), encodingSource);
		} finally {
			if (source != null) {
				this.resolver.release(source);
			}
			if (encodingSource != null) {
				this.resolver.release(encodingSource);
			}
		}
	}

	/** {@code <cmap>}: CMapを読み込みます。 */
	void addCMap(final String src, final String javaEncoding) throws IOException, URISyntaxException {
		final Source source = this.resolve(src);
		final CMap cmap = new CMap(source, javaEncoding);
		this.nameToCMap.put(cmap.getEncoding(), cmap);
	}

	/** {@code <cid-keyed-font>}: 論理CIDフォントを構築します。 */
	PDFFontSource[] cidKeyedFont(final FontFace face, final String warraySrc)
			throws IOException, URISyntaxException {
		Source source = null;
		try {
			source = this.resolve(warraySrc);
			return FontLoader.readCIDKeyedFont(source, face, this.nameToCMap);
		} finally {
			if (source != null) {
				this.resolver.release(source);
			}
		}
	}

	/** {@code <font-file>}: TTF/OTF/TTCファイルからフォントを構築します。 */
	List<FontSource> fontFile(final String src, final String types, final int index, final FontFace face)
			throws IOException, URISyntaxException {
		final File ttfFile = this.toFile(src);
		final List<FontSource> list = new ArrayList<>();
		if (types.indexOf("cid-keyed") != -1) {
			FontLoader.readTTF(list, face, FontLoader.Type.CID_KEYED, ttfFile, index, this.nameToCMap);
		}
		if (types.indexOf("cid-identity") != -1) {
			FontLoader.readTTF(list, face, FontLoader.Type.CID_IDENTITY, ttfFile, index, this.nameToCMap);
		}
		if (types.indexOf("embedded") != -1) {
			FontLoader.readTTF(list, face, FontLoader.Type.EMBEDDED, ttfFile, index, this.nameToCMap);
		}
		return list;
	}

	/**
	 * {@code <font-dir>}: ディレクトリを走査して全フォントを登録します
	 * (永続索引が有効ならヒット分はファイルを開かない)。
	 */
	void fontDir(final String dir, final String types, final FontFace face, final String scanKey)
			throws IOException, URISyntaxException {
		final File dirFile = this.toFile(dir);
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("scan: " + dirFile);
		}
		final File[] files = dirFile.listFiles();
		if (files == null) {
			return;
		}
		for (final File ttfFile : files) {
			if (ttfFile.isDirectory()) {
				continue;
			}
			final String name = ttfFile.getName().toLowerCase();
			if (!name.endsWith(".ttf") && !name.endsWith(".ttc") && !name.endsWith(".otf")
					&& !name.endsWith(".woff")) {
				continue;
			}
			try {
				List<FontSource> list = null;
				if (this.fontIndex != null) {
					// 索引ヒット: フォントファイルを開かずに再構築
					list = this.fontIndex.lookup(ttfFile, scanKey);
				}
				if (list == null) {
					list = new ArrayList<>();

					int numFonts = 1;
					try (RandomAccessFile raf = new RandomAccessFile(ttfFile, "r")) {
						final byte[] tagBytes = new byte[4];
						raf.readFully(tagBytes);
						final String tag = new String(tagBytes, "ISO-8859-1");
						if ("ttcf".equals(tag)) {
							// TTC
							raf.skipBytes(4);
							numFonts = raf.readInt();
						}
					}

					for (int j = 0; j < numFonts; ++j) {
						// ディレクトリ走査はfaceに宣言が無いため、italic/weightを
						// ファイルのOS/2から導出する(FontLoader.readTTFのjavadoc参照)
						if (types.indexOf("cid-identity") != -1) {
							FontLoader.readTTF(list, face, FontLoader.Type.CID_IDENTITY, ttfFile, j, this.nameToCMap,
									true);
						}
						if (types.indexOf("embedded") != -1) {
							FontLoader.readTTF(list, face, FontLoader.Type.EMBEDDED, ttfFile, j, this.nameToCMap,
									true);
						}
					}
					if (this.fontIndex != null) {
						this.fontIndex.put(ttfFile, scanKey, numFonts, list);
					}
				}
				this.registerAll(list);
			} catch (final Exception e) {
				LOG.log(Level.WARNING, "Failed to get font info for '" + ttfFile + "'.", e);
			}
		}
	}

	/** {@code <system-font>}: AWT経由でシステムフォントを構築します。 */
	List<FontSource> systemFont(final String src, final String file, final String dir, final String types,
			final FontFace face) throws IOException, URISyntaxException, java.awt.FontFormatException {
		final List<FontSource> list = new ArrayList<>();
		if (file != null) {
			final File theFile = this.toFile(file);
			try (InputStream in = new FileInputStream(theFile)) {
				final java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in);
				FontLoader.readSystemFont(face, list, types, font, this.nameToCMap);
			}
		} else if (dir != null) {
			final File theDir = this.toFile(dir);
			final File[] files = theDir.listFiles();
			for (final File theFile : files) {
				final String name = theFile.getName().toLowerCase();
				if (name.endsWith(".ttf") || name.endsWith(".ttc") || name.endsWith(".otf")
						|| name.endsWith(".woff")) {
					try (InputStream in = new FileInputStream(theFile)) {
						final java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in);
						FontLoader.readSystemFont(face, list, types, font, this.nameToCMap);
					}
				}
			}
		} else {
			final java.awt.Font font = java.awt.Font.decode(src);
			FontLoader.readSystemFont(face, list, types, font, this.nameToCMap);
		}
		return list;
	}

	/** {@code <all-system-fonts>}: 全システムフォントを登録します。 */
	void allSystemFonts(final String dir, final String types, final FontFace face)
			throws IOException, URISyntaxException {
		java.awt.Font[] fonts;
		if (dir != null) {
			final File dirFile = this.toFile(dir);
			final File[] files = dirFile.listFiles();
			final List<java.awt.Font> fontList = new ArrayList<>();
			for (final File file : files) {
				try {
					try (InputStream in = new FileInputStream(file)) {
						fontList.add(java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in));
					}
				} catch (final Exception e) {
					LOG.log(Level.WARNING, "Failed to load font file.", e);
				}
			}
			fonts = fontList.toArray(new java.awt.Font[fontList.size()]);
		} else {
			fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
		}
		for (final java.awt.Font font : fonts) {
			try {
				// 従来の挙動どおり、こちらはラッパーをallFontsにも入れる
				// (font-dirは生ソース——歴史的な非対称を挙動保存)
				if (types.indexOf("cid-keyed") != -1) {
					this.register(new PDFFontSourceManagerConfigurationHandler.PdfFontSourceWrapper(
							FontLoader.readSystemFont(face, FontLoader.Type.CID_KEYED, font, this.nameToCMap)));
				}
				if (types.indexOf("cid-identity") != -1) {
					this.register(new PDFFontSourceManagerConfigurationHandler.PdfFontSourceWrapper(
							FontLoader.readSystemFont(face, FontLoader.Type.CID_IDENTITY, font, this.nameToCMap)));
				}
				if (types.indexOf("embedded") != -1) {
					this.register(new PDFFontSourceManagerConfigurationHandler.PdfFontSourceWrapper(
							FontLoader.readSystemFont(face, FontLoader.Type.EMBEDDED, font, this.nameToCMap)));
				}
			} catch (final Exception e) {
				LOG.log(Level.WARNING, "Failed to get font info for '" + font.getFontName() + "'.", e);
			}
		}
	}

	/** {@code <generic-fonts>}の1エントリを登録します。 */
	void genericFamily(final String genericFamily, final FontFamilyList family) {
		this.genericToFamily.put(genericFamily, family);
	}

	/** フォントを名前索引と全フォント一覧へ登録します。 */
	void register(final FontSource source) {
		this.allFonts.add(source);
		FontLoader.add(source, this.nameToFonts);
	}

	/**
	 * font-dir走査結果を登録します(従来の挙動どおり、全フォント一覧には
	 * 生のソース・名前索引にはラッパーが入る)。
	 */
	private void registerAll(final List<FontSource> list) {
		this.allFonts.addAll(list);
		for (final FontSource source : list) {
			FontLoader.add(new PDFFontSourceManagerConfigurationHandler.PdfFontSourceWrapper((PDFFontSource) source),
					this.nameToFonts);
		}
	}
}
