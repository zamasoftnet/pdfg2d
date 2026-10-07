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
import java.util.Locale;
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
 * Builds the font database from fonts.xml declarations
 * (2026-08-01, 90-point plan increment 8). Separated XML syntax interpretation
 * from file resolution, font parsing, index updates, and registration,
 * which the 593-line SAX handler previously handled together.
 * The SAX handler ({@link PDFFontSourceManagerConfigurationHandler}) now only converts
 * attributes to typed values and calls this class, which can be unit-tested without XML.
 *
 * @author MIYABE Tatsuhiko
 */
final class FontCatalogBuilder {
	private static final Logger LOG = Logger.getLogger(FontCatalogBuilder.class.getName());

	private final URI base;

	private final SourceResolver resolver;

	/** Persistent index for font-dir scanning (null means parse every time without an index). */
	private final FontIndex fontIndex;

	private final Map<String, Encoding> nameToEncoding = new HashMap<>();

	private Encoding defaultEncoding;

	private GlyphMap unicodeEncoding;

	private final Map<String, CMap> nameToCMap = new HashMap<>();

	final Map<String, Object> nameToFonts = new HashMap<>();

	final Map<String, FontFamilyList> genericToFamily = new HashMap<>();

	final Map<String, List<PDFFontSourceManager.GenericFamily>> genericToLangFamily = new HashMap<>();

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

	/** {@code <encoding src>}: loads the mapping from codes to character names. */
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

	/** {@code <core-fonts>}: sets the default encoding and Unicode mapping. */
	void beginCoreFonts(final String encoding, final String unicodeSrc) throws IOException, URISyntaxException {
		this.defaultEncoding = this.nameToEncoding.get(encoding);
		final Source source = this.resolve(unicodeSrc);
		try {
			this.unicodeEncoding = GlyphMap.parse(source.getInputStream());
		} finally {
			this.resolver.release(source);
		}
	}

	/** {@code <letter-font>}: builds an AFM core font for Latin text. */
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

	/** {@code <symbol-font>}: builds an AFM symbol core font. */
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

	/** {@code <cmap>}: loads a CMap. */
	void addCMap(final String src, final String javaEncoding) throws IOException, URISyntaxException {
		final Source source = this.resolve(src);
		final CMap cmap = new CMap(source, javaEncoding);
		this.nameToCMap.put(cmap.getEncoding(), cmap);
	}

	/** {@code <cid-keyed-font>}: builds a logical CID-keyed font. */
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

	/** {@code <font-file>}: builds fonts from a TTF/OTF/TTC file. */
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
	 * {@code <font-dir>}: scans a directory and registers all fonts
	 * (if the persistent index is enabled, index hits do not open files).
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
					// Index hit: reconstruct without opening the font file.
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
						// Directory scans have no face declarations, so derive italic/weight
						// from the file's OS/2 (see FontLoader.readTTF's Javadoc).
						if (types.indexOf("cid-identity") != -1) {
							FontLoader.readTTF(list, face, FontLoader.Type.CID_IDENTITY, ttfFile, j, this.nameToCMap,
									true);
						}
						if (types.indexOf("embedded") != -1) {
							FontLoader.readTTF(list, face, FontLoader.Type.EMBEDDED, ttfFile, j, this.nameToCMap,
									true);
						}
					}
					if (numFonts == 1) {
						FontLoader.addWeightInstances(list, ttfFile);
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

	/** {@code <system-font>}: builds a system font through AWT. */
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

	/** {@code <all-system-fonts>}: registers all system fonts. */
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
				// Preserve the previous behavior: this path also adds wrappers to allFonts
				// (font-dir adds raw sources; retain this historical asymmetry).
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

	/** Registers one {@code <generic-fonts>} entry. */
	void genericFamily(final String genericFamily, final FontFamilyList family) {
		this.genericFamily(genericFamily, null, family);
	}

	/**
	 * Registers language-specific {@code <generic-fonts>} entries in document order.
	 * {@code lang} can list multiple whitespace-separated BCP-47 tags
	 * ({@code lang="zh-Hant zh-TW zh-MO"}). Region-only tags ({@code zh-TW}) do not
	 * automatically link to tags with a script ({@code zh-Hant}),
	 * so the configuration explicitly specifies which tags share a chain.
	 */
	void genericFamily(final String genericFamily, final String lang, final FontFamilyList family) {
		if (lang == null || lang.isBlank()) {
			this.genericToFamily.put(genericFamily, family);
			return;
		}
		for (final String tag : lang.trim().split("\\s+")) {
			final Locale locale = Locale.forLanguageTag(tag);
			this.genericToLangFamily.computeIfAbsent(genericFamily, key -> new ArrayList<>())
					.add(new PDFFontSourceManager.GenericFamily(locale, family));
		}
	}

	/** Registers a font in the name index and the list of all fonts. */
	void register(final FontSource source) {
		this.allFonts.add(source);
		FontLoader.add(source, this.nameToFonts);
	}

	/**
	 * Registers font-dir scan results (preserving the previous behavior:
	 * raw sources in the list of all fonts, wrappers in the name index).
	 */
	private void registerAll(final List<FontSource> list) {
		this.allFonts.addAll(list);
		for (final FontSource source : list) {
			FontLoader.add(new PDFFontSourceManagerConfigurationHandler.PdfFontSourceWrapper((PDFFontSource) source),
					this.nameToFonts);
		}
	}
}
