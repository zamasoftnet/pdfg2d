package net.zamasoft.pdfg2d.pdf.font;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontSourceWrapper;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.UnicodeRange;
import net.zamasoft.pdfg2d.pdf.ObjectRef;

/**
 * fonts.xmlのSAXハンドラです。XML構文の解釈と属性の型変換だけを行い、
 * ファイル解決・フォントパース・索引・登録は{@link FontCatalogBuilder}へ
 * 委譲します(2026-08-01、90点計画増分8——従来は593行の単一クラスが
 * 全責務を抱えていた)。
 *
 * <p>
 * {@code alias}/{@code include}/{@code exclude}子要素は直前のフォント宣言を
 * 修飾するため、宣言の完成は親要素のendElementまで遅延する——この
 * バッファリング({@link #fontSources})だけがハンドラに残る状態である。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
class PDFFontSourceManagerConfigurationHandler extends DefaultHandler {
	private static final Logger LOG = Logger.getLogger(PDFFontSourceManagerConfigurationHandler.class.getName());

	private static final byte IN_FONTS = 0;

	private static final byte IN_ENCODINGS = 1;

	private static final byte IN_CORE_FONTS = 2;

	private static final byte IN_CMAPS = 3;

	private static final byte IN_CID_FONTS = 5;

	private static final byte IN_GENERIC_FONTS = 6;

	private byte state = IN_FONTS;

	private PdfFontSourceWrapper[] fontSources;

	/** 実行側(I/O・パース・登録)。構築結果のコレクションもここが持つ。 */
	final FontCatalogBuilder catalog;

	PDFFontSourceManagerConfigurationHandler(URI base) throws IOException {
		this(base, null);
	}

	PDFFontSourceManagerConfigurationHandler(URI base, FontIndex fontIndex) throws IOException {
		this.catalog = new FontCatalogBuilder(base, fontIndex);
	}

	/**
	 * 索引の鮮度判定に使うスキャン条件です。typesとface属性(readTTFが
	 * 構築後に適用する上書き)が変われば別条件として索引ミスにする。
	 */
	private static String toScanKey(final Attributes atts) {
		return String.join(" ", String.valueOf(atts.getValue("types")), String.valueOf(atts.getValue("name")),
				String.valueOf(atts.getValue("italic")), String.valueOf(atts.getValue("weight")),
				String.valueOf(atts.getValue("panose")));
	}

	private static PdfFontSourceWrapper[] wrap(final List<FontSource> sources) {
		final PdfFontSourceWrapper[] wrappers = new PdfFontSourceWrapper[sources.size()];
		for (int i = 0; i < sources.size(); ++i) {
			wrappers[i] = new PdfFontSourceWrapper((PDFFontSource) sources.get(i));
		}
		return wrappers;
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		try {
			switch (this.state) {
				case IN_FONTS:
					if (qName.equals("encodings")) {
						this.state = IN_ENCODINGS;
					} else if (qName.equals("core-fonts")) {
						this.catalog.beginCoreFonts(atts.getValue("encoding"), atts.getValue("unicode-src"));
						this.state = IN_CORE_FONTS;
					} else if (qName.equals("cmaps")) {
						this.state = IN_CMAPS;
					} else if (qName.equals("cid-fonts")) {
						this.state = IN_CID_FONTS;
					} else if (qName.equals("generic-fonts")) {
						this.state = IN_GENERIC_FONTS;
					}
					break;
				case IN_ENCODINGS:
					if (qName.equals("encoding")) {
						this.catalog.addEncoding(atts.getValue("src"));
					}
					break;

				case IN_CORE_FONTS:
					if (qName.equals("letter-font")) {
						this.fontSources = new PdfFontSourceWrapper[] { new PdfFontSourceWrapper(
								(PDFFontSource) this.catalog.letterFont(atts.getValue("src"),
										atts.getValue("encoding"))) };
					} else if (qName.equals("symbol-font")) {
						this.fontSources = new PdfFontSourceWrapper[] { new PdfFontSourceWrapper(
								(PDFFontSource) this.catalog.symbolFont(atts.getValue("src"),
										atts.getValue("encoding-src"))) };
					}
					break;

				case IN_CMAPS:
					if (qName.equals("cmap")) {
						this.catalog.addCMap(atts.getValue("src"), atts.getValue("java-encoding"));
					}
					break;

				case IN_CID_FONTS:
					if (qName.equals("cid-keyed-font")) {
						final FontFace face = FontLoader.toFontFace(atts);
						final PDFFontSource[] sources = this.catalog.cidKeyedFont(face, atts.getValue("warray"));
						this.fontSources = new PdfFontSourceWrapper[sources.length];
						for (int i = 0; i < sources.length; ++i) {
							this.fontSources[i] = new PdfFontSourceWrapper(sources[i]);
						}
					} else if (qName.equals("font-file")) {
						int index;
						try {
							index = Integer.parseInt(atts.getValue("index"));
						} catch (Exception e) {
							index = 0;
						}
						try {
							this.fontSources = wrap(this.catalog.fontFile(atts.getValue("src"),
									atts.getValue("types"), index, FontLoader.toFontFace(atts)));
						} catch (Exception e) {
							LOG.log(Level.WARNING, "Failed to get font info for '" + atts.getValue("src") + "'.", e);
							this.fontSources = null;
						}
					} else if (qName.equals("font-dir")) {
						this.catalog.fontDir(atts.getValue("dir"), atts.getValue("types"),
								FontLoader.toFontFace(atts), toScanKey(atts));
					} else if (qName.equals("system-font")) {
						try {
							final List<FontSource> list = this.catalog.systemFont(atts.getValue("src"),
									atts.getValue("file"), atts.getValue("dir"), atts.getValue("types"),
									FontLoader.toFontFace(atts));
							// 旧実装は生ソースをPdfFontSourceWrapper[]へtoArrayしており
							// ArrayStoreExceptionが潜在していた(system-font要素は
							// テスト構成に無く未発火)。他経路と同じくラップする
							this.fontSources = wrap(list);
						} catch (Exception e) {
							LOG.log(Level.WARNING, "Failed to get font info for '" + atts.getValue("src") + "'.", e);
							this.fontSources = null;
						}
					} else if (qName.equals("all-system-fonts")) {
						this.catalog.allSystemFonts(atts.getValue("dir"), atts.getValue("types"),
								FontLoader.toFontFace(atts));
					}
					break;

				case IN_GENERIC_FONTS:
					final String genericFamily = lName;
					final List<FontFamily> entries = new ArrayList<>();
					for (StringTokenizer i = new StringTokenizer(atts.getValue("font-family"), ","); i
							.hasMoreTokens();) {
						entries.add(new FontFamily(i.nextToken()));
					}
					this.catalog.genericFamily(genericFamily, atts.getValue("lang"),
							new FontFamilyList(entries.toArray(new FontFamily[entries.size()])));
					break;
			}
		} catch (final Exception e) {
			LOG.log(Level.SEVERE, "Failed to load '" + qName + "'.", e);
			throw new SAXException(e);
		}
		if (this.fontSources != null) {
			if (qName.equals("alias")) {
				// alias
				String name = atts.getValue("name");
				for (int i = 0; i < this.fontSources.length; ++i) {
					this.fontSources[i].addAliase(name);
				}
			} else if (qName.equals("include")) {
				String unicodeRange = atts.getValue("unicode-range");
				for (StringTokenizer st = new StringTokenizer(unicodeRange, ","); st.hasMoreTokens();) {
					UnicodeRange range = UnicodeRange.parseRange(st.nextToken());
					for (int i = 0; i < this.fontSources.length; ++i) {
						this.fontSources[i].addInclude(range);
					}
				}
			} else if (qName.equals("exclude")) {
				String unicodeRange = atts.getValue("unicode-range");
				for (StringTokenizer st = new StringTokenizer(unicodeRange, ","); st.hasMoreTokens();) {
					UnicodeRange range = UnicodeRange.parseRange(st.nextToken());
					for (int i = 0; i < this.fontSources.length; ++i) {
						this.fontSources[i].addExclude(range);
					}
				}
			}
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		if (qName.equals("letter-font") || qName.equals("symbol-font") || qName.equals("cid-keyed-font")
				|| qName.equals("font-file") || qName.equals("system-font")) {
			if (this.fontSources == null) {
				throw new SAXException(qName);
			}
			for (int i = 0; i < this.fontSources.length; ++i) {
				this.catalog.register(this.fontSources[i]);
			}
			this.fontSources = null;
		} else if (qName.equals("encodings") || qName.equals("core-fonts") || qName.equals("cmaps")
				|| qName.equals("cid-fonts") || qName.equals("generic-fonts")) {
			if (this.fontSources != null) {
				throw new SAXException(qName);
			}
			this.state = IN_FONTS;
		}
	}

	static class PdfFontSourceWrapper extends FontSourceWrapper implements PDFFontSource {
		private static final long serialVersionUID = 1L;

		protected final List<String> aliasesList = new ArrayList<String>();

		protected final List<UnicodeRange> includes = new ArrayList<UnicodeRange>();

		protected final List<UnicodeRange> excludes = new ArrayList<UnicodeRange>();

		private transient String[] aliases = null;

		public PdfFontSourceWrapper(PDFFontSource source) {
			super(source);
		}

		public final synchronized void addAliase(String aliase) {
			this.aliasesList.add(aliase);
		}

		public final synchronized void addInclude(UnicodeRange range) {
			this.includes.add(range);
		}

		public final synchronized void addExclude(UnicodeRange range) {
			this.excludes.add(range);
		}

		public String[] getAliases() {
			String[] aliases = this.source.getAliases();
			int count = aliases.length + this.aliasesList.size();
			if (this.aliases == null || this.aliases.length != count) {
				Set<String> result = new TreeSet<String>();
				for (int i = 0; i < aliases.length; ++i) {
					result.add(aliases[i]);
				}
				result.addAll(this.aliasesList);
				this.aliases = result.toArray(new String[result.size()]);
			}
			return this.aliases;
		}

		public boolean canDisplay(int c) {
			if (!this.excludes.isEmpty()) {
				for (int i = 0; i < this.excludes.size(); ++i) {
					UnicodeRange range = (UnicodeRange) this.excludes.get(i);
					if (range.contains(c)) {
						return false;
					}
				}
			}
			if (!this.includes.isEmpty()) {
				for (int i = 0; i < this.includes.size(); ++i) {
					UnicodeRange range = (UnicodeRange) this.includes.get(i);
					if (range.contains(c)) {
						return this.source.canDisplay(c);
					}
				}
				return false;
			}
			return this.source.canDisplay(c);
		}

		public PDFFont createFont(String name, ObjectRef fontRef) {
			return ((PDFFontSource) this.source).createFont(name, fontRef);
		}

		public Type getType() {
			return ((PDFFontSource) this.source).getType();
		}
	}
};
