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
 * SAX handler for fonts.xml. Only interprets XML syntax and converts attribute types;
 * delegates file resolution, font parsing, indexing, and registration to {@link FontCatalogBuilder}
 * (2026-08-01, 90-point plan increment 8: previously a single 593-line class
 * held all these responsibilities).
 *
 * <p>
 * The {@code alias}/{@code include}/{@code exclude} child elements modify the preceding font declaration,
 * so declaration completion is deferred until the parent's endElement.
 * This buffering ({@link #fontSources}) is the only state remaining in the handler.
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

	/** Execution side (I/O, parsing, registration). Also owns the collections of constructed results. */
	final FontCatalogBuilder catalog;

	PDFFontSourceManagerConfigurationHandler(URI base) throws IOException {
		this(base, null);
	}

	PDFFontSourceManagerConfigurationHandler(URI base, FontIndex fontIndex) throws IOException {
		this.catalog = new FontCatalogBuilder(base, fontIndex);
	}

	/**
	 * Scan conditions used to check index freshness. Changes to types or face attributes
	 * (overrides applied by readTTF after construction) count as different conditions and cause an index miss.
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
							// The old implementation called toArray on raw sources with PdfFontSourceWrapper[],
							// leaving a latent ArrayStoreException (not triggered because system-font elements
							// were absent from test configurations). Wrap sources as in the other paths.
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
			return this.inRanges(c) && this.source.canDisplay(c);
		}

		@Override
		public boolean canDisplayUpright(int c) {
			return this.inRanges(c) && this.source.canDisplayUpright(c);
		}

		/** Whether the configured include/exclude ranges let the character through. */
		private boolean inRanges(int c) {
			for (int i = 0; i < this.excludes.size(); ++i) {
				if (this.excludes.get(i).contains(c)) {
					return false;
				}
			}
			if (this.includes.isEmpty()) {
				return true;
			}
			for (int i = 0; i < this.includes.size(); ++i) {
				if (this.includes.get(i).contains(c)) {
					return true;
				}
			}
			return false;
		}

		public PDFFont createFont(String name, ObjectRef fontRef) {
			return ((PDFFontSource) this.source).createFont(name, fontRef);
		}

		public Type getType() {
			return ((PDFFontSource) this.source).getType();
		}
	}
};
