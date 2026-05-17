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
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontSourceWrapper;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.UnicodeRange;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.font.cid.CMap;
import net.zamasoft.pdfg2d.pdf.font.type1.Encoding;
import net.zamasoft.pdfg2d.pdf.font.type1.GlyphMap;

class PDFFontSourceManagerConfigurationHandler extends DefaultHandler {
	private static final Logger LOG = Logger.getLogger(PDFFontSourceManagerConfigurationHandler.class.getName());

	private final URI base;

	private final SourceResolver resolver;

	private static final byte IN_FONTS = 0;

	private static final byte IN_ENCODINGS = 1;

	private static final byte IN_CORE_FONTS = 2;

	private static final byte IN_CMAPS = 3;

	private static final byte IN_CID_FONTS = 5;

	private static final byte IN_GENERIC_FONTS = 6;

	private byte state = IN_FONTS;

	private PdfFontSourceWrapper[] fontSources;

	private GlyphMap unicodeEncoding;

	private final Map<String, Encoding> nameToEncoding = new HashMap<String, Encoding>();

	private Encoding defaultEncoding;

	private final Map<String, CMap> nameToCMap = new HashMap<String, CMap>();

	final Map<String, Object> nameToFonts = new HashMap<String, Object>();

	final Map<String, FontFamilyList> genericToFamily = new HashMap<String, FontFamilyList>();

	final Collection<FontSource> allFonts = new ArrayList<FontSource>();

	PDFFontSourceManagerConfigurationHandler(URI base) throws IOException {
		this.base = base;
		this.resolver = CompositeSourceResolver.createGenericCompositeSourceResolver();
	}

	private File toFile(String src) throws SAXException {
		try {
			Source source = this.resolver.resolve(URIHelper.resolve("UTF-8", this.base, src));
			try {
				return source.getFile();
			} finally {
				this.resolver.release(source);
			}
		} catch (IOException e) {
			throw new SAXException(e);
		} catch (URISyntaxException e) {
			throw new SAXException(e);
		}
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		switch (this.state) {
			case IN_FONTS:
				if (qName.equals("encodings")) {
					// <encodings>

					this.state = IN_ENCODINGS;
				} else if (qName.equals("core-fonts")) {
					// <core-fonts>
					String encoding = atts.getValue("encoding");
					String unicodeSrc = atts.getValue("unicode-src");

					this.defaultEncoding = (Encoding) this.nameToEncoding.get(encoding);

					try {
						Source source = this.resolver.resolve(URIHelper.resolve("UTF-8", this.base, unicodeSrc));
						try {
							this.unicodeEncoding = GlyphMap.parse(source.getInputStream());
						} finally {
							this.resolver.release(source);
						}
					} catch (IOException e) {
						throw new SAXException(e);
					} catch (URISyntaxException e) {
						throw new SAXException(e);
					}
					this.state = IN_CORE_FONTS;
				} else if (qName.equals("cmaps")) {
					// <cmaps>

					this.state = IN_CMAPS;
				} else if (qName.equals("cid-fonts")) {
					// <cid-fonts>
					this.state = IN_CID_FONTS;
				} else if (qName.equals("generic-fonts")) {
					// <generic-fonts>
					this.state = IN_GENERIC_FONTS;
				}
				break;
			case IN_ENCODINGS:
				if (qName.equals("encoding")) {
					// <encoding>
					String src = atts.getValue("src");
					Source source = null;
					try {
						source = this.resolver.resolve(URIHelper.resolve("UTF-8", this.base, src));
						Encoding encoding = Encoding.parse(source.getInputStream());
						this.nameToEncoding.put(encoding.name, encoding);
					} catch (IOException e) {
						throw new SAXException(e);
					} catch (URISyntaxException e) {
						throw new SAXException(e);
					} finally {
						if (source != null) {
							this.resolver.release(source);
						}
					}
				}
				break;

			case IN_CORE_FONTS:
				if (qName.equals("letter-font")) {
					// letter-font
					String src = atts.getValue("src");
					Source source = null;
					try {
						source = this.resolver.resolve(URIHelper.resolve("UTF-8", this.base, src));

						Encoding pdfEncoding;
						String encoding = atts.getValue("encoding");
						if (encoding != null) {
							pdfEncoding = (Encoding) this.nameToEncoding.get(encoding);
						} else {
							pdfEncoding = this.defaultEncoding;
						}

						this.fontSources = new PdfFontSourceWrapper[] { new PdfFontSourceWrapper(FontLoader
								.readLetterType1Font(this.unicodeEncoding, pdfEncoding, source.getInputStream())) };
					} catch (Exception e) {
						LOG.log(Level.SEVERE, "Failed to load AFM file '" + src + "'.", e);
						throw new SAXException(e);
					} finally {
						if (source != null) {
							this.resolver.release(source);
						}
					}

				} else if (qName.equals("symbol-font")) {
					// symbol-font
					String src = atts.getValue("src");
					Source source = null, encodingSource = null;
					try {
						source = this.resolver.resolve(URIHelper.resolve("UTF-8", this.base, src));

						String encodingSrc = atts.getValue("encoding-src");
						encodingSource = this.resolver.resolve(URIHelper.resolve("UTF-8", this.base, encodingSrc));

						this.fontSources = new PdfFontSourceWrapper[] { new PdfFontSourceWrapper(
								FontLoader.readSymbolType1Font(source.getInputStream(), encodingSource)) };
					} catch (Exception e) {
						LOG.log(Level.SEVERE, "Failed to load AFM file '" + src + "'.", e);
						throw new SAXException(e);
					} finally {
						if (source != null) {
							this.resolver.release(source);
						}
						if (encodingSource != null) {
							this.resolver.release(encodingSource);
						}
					}
				}
				break;

			case IN_CMAPS:
				if (qName.equals("cmap")) {
					// <cmap>
					String src = atts.getValue("src");
					String javaEncoding = atts.getValue("java-encoding");
					try {
						Source source = this.resolver.resolve(URIHelper.resolve("UTF-8", this.base, src));
						CMap cmap = new CMap(source, javaEncoding);
						this.nameToCMap.put(cmap.getEncoding(), cmap);
					} catch (Exception e) {
						LOG.log(Level.SEVERE, "Failed to load CMap file '" + src + "'.", e);
						throw new SAXException(e);
					}
				}
				break;

			case IN_CID_FONTS:
				if (qName.equals("cid-keyed-font")) {
					String warraySrc = atts.getValue("warray");
					Source source = null;
					try {
						source = this.resolver.resolve(URIHelper.resolve("UTF-8", this.base, warraySrc));

						FontFace face = FontLoader.toFontFace(atts);
						PDFFontSource[] sources = FontLoader.readCIDKeyedFont(source, face, this.nameToCMap);
						this.fontSources = new PdfFontSourceWrapper[sources.length];
						for (int i = 0; i < sources.length; ++i) {
							this.fontSources[i] = new PdfFontSourceWrapper(sources[i]);
						}
					} catch (Exception e) {
						LOG.log(Level.SEVERE, "Failed to load '" + source.getURI() + "'.", e);
						throw new SAXException(e);
					} finally {
						if (source != null) {
							this.resolver.release(source);
						}
					}
				} else if (qName.equals("font-file")) {
					String src = atts.getValue("src");
					String types = atts.getValue("types");

					try {
						File ttfFile = this.toFile(src);
						int index;
						try {
							index = Integer.parseInt(atts.getValue("index"));
						} catch (Exception e) {
							index = 0;
						}

						List<FontSource> list = new ArrayList<FontSource>();
						FontFace face = FontLoader.toFontFace(atts);

						if (types.indexOf("cid-keyed") != -1) {
							FontLoader.readTTF(list, face, FontLoader.Type.CID_KEYED, ttfFile, index, this.nameToCMap);
						}
						if (types.indexOf("cid-identity") != -1) {
							FontLoader.readTTF(list, face, FontLoader.Type.CID_IDENTITY, ttfFile, index,
									this.nameToCMap);
						}
						if (types.indexOf("embedded") != -1) {
							FontLoader.readTTF(list, face, FontLoader.Type.EMBEDDED, ttfFile, index, this.nameToCMap);
						}
						this.fontSources = new PdfFontSourceWrapper[list.size()];
						for (int i = 0; i < list.size(); ++i) {
							this.fontSources[i] = new PdfFontSourceWrapper((PDFFontSource) list.get(i));
						}

					} catch (Exception e) {
						LOG.log(Level.WARNING, "Failed to get font info for '" + src + "'.", e);
						this.fontSources = null;
					}
				} else if (qName.equals("font-dir")) {
					String dir = atts.getValue("dir");
					String types = atts.getValue("types");

					File dirFile = this.toFile(dir);
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("scan: " + dirFile);
					}
					File[] files = dirFile.listFiles();
					if (files != null) {
						FontFace face = FontLoader.toFontFace(atts);
						for (int i = 0; i < files.length; ++i) {
							File ttfFile = files[i];
							if (ttfFile.isDirectory()) {
								continue;
							}
							String name = ttfFile.getName().toLowerCase();
							if (!name.endsWith(".ttf") && !name.endsWith(".ttc") && !name.endsWith(".otf")
									&& !name.endsWith(".woff")) {
								continue;
							}
							try {
								List<FontSource> list = new ArrayList<FontSource>();

								int numFonts = 1;
								try (RandomAccessFile raf = new RandomAccessFile(ttfFile, "r");) {
									byte[] tagBytes = new byte[4];
									raf.readFully(tagBytes);
									String tag = new String(tagBytes, "ISO-8859-1");
									if ("ttcf".equals(tag)) {
										// TTC
										raf.skipBytes(4);
										numFonts = raf.readInt();
									}
								}

								for (int j = 0; j < numFonts; ++j) {
									if (types.indexOf("cid-identity") != -1) {
										FontLoader.readTTF(list, face, FontLoader.Type.CID_IDENTITY, ttfFile, j,
												this.nameToCMap);
									}
									if (types.indexOf("embedded") != -1) {
										FontLoader.readTTF(list, face, FontLoader.Type.EMBEDDED, ttfFile, j,
												this.nameToCMap);
									}
								}
								this.allFonts.addAll(list);
								for (int j = 0; j < list.size(); ++j) {
									FontLoader.add(new PdfFontSourceWrapper((PDFFontSource) list.get(j)),
											this.nameToFonts);
								}
							} catch (Exception e) {
								LOG.log(Level.WARNING, "Failed to get font info for '" + ttfFile + "'.", e);
							}
						}
					}
				} else if (qName.equals("system-font")) {
					String src = atts.getValue("src");
					String file = atts.getValue("file");
					String dir = atts.getValue("dir");
					String types = atts.getValue("types");

					List<FontSource> list = new ArrayList<FontSource>();
					FontFace face = FontLoader.toFontFace(atts);

					try {
						if (file != null) {
							File theFile = this.toFile(file);
							try (InputStream in = new FileInputStream(theFile)) {
								java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in);
								FontLoader.readSystemFont(face, list, types, font, this.nameToCMap);
							}
						} else if (dir != null) {
							File theDir = this.toFile(dir);
							File[] files = theDir.listFiles();
							for (int i = 0; i < files.length; ++i) {
								File theFile = files[i];
								String name = theFile.getName().toLowerCase();
								if (name.endsWith(".ttf") || name.endsWith(".ttc") || name.endsWith(".otf")
										|| name.endsWith(".woff")) {
									try (InputStream in = new FileInputStream(theFile)) {
										java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in);
										FontLoader.readSystemFont(face, list, types, font, this.nameToCMap);
									}
								}
							}
						} else {
							java.awt.Font font = java.awt.Font.decode(src);
							FontLoader.readSystemFont(face, list, types, font, this.nameToCMap);
						}
						this.fontSources = (PdfFontSourceWrapper[]) list.toArray(new PdfFontSourceWrapper[list.size()]);
					} catch (Exception e) {
						LOG.log(Level.WARNING, "Failed to get font info for '" + src + "'.", e);
						this.fontSources = null;
					}
				} else if (qName.equals("all-system-fonts")) {
					String types = atts.getValue("types");
					String dir = atts.getValue("dir");

					java.awt.Font[] fonts;
					if (dir != null) {
						File dirFile = this.toFile(dir);
						File[] files = dirFile.listFiles();
						List<java.awt.Font> fontList = new ArrayList<java.awt.Font>();
						for (int i = 0; i < files.length; ++i) {
							try {
								try (InputStream in = new FileInputStream(files[i])) {
									fontList.add(java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in));
								}
							} catch (Exception e) {
								LOG.log(Level.WARNING, "Failed to load font file.", e);
							}
						}
						fonts = (java.awt.Font[]) fontList.toArray(new java.awt.Font[fontList.size()]);
					} else {
						fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
					}
					FontFace face = FontLoader.toFontFace(atts);
					for (int i = 0; i < fonts.length; ++i) {
						java.awt.Font font = fonts[i];
						try {
							if (types.indexOf("cid-keyed") != -1) {
								PdfFontSourceWrapper fontSource = new PdfFontSourceWrapper(
										FontLoader.readSystemFont(face, FontLoader.Type.CID_KEYED, font,
												this.nameToCMap));
								this.allFonts.add(fontSource);
								FontLoader.add(fontSource, this.nameToFonts);
							}
							if (types.indexOf("cid-identity") != -1) {
								PdfFontSourceWrapper fontSource = new PdfFontSourceWrapper(
										FontLoader.readSystemFont(face,
												FontLoader.Type.CID_IDENTITY, font, this.nameToCMap));
								this.allFonts.add(fontSource);
								FontLoader.add(fontSource, this.nameToFonts);
							}
							if (types.indexOf("embedded") != -1) {
								PdfFontSourceWrapper fontSource = new PdfFontSourceWrapper(
										FontLoader.readSystemFont(face, FontLoader.Type.EMBEDDED, font,
												this.nameToCMap));
								this.allFonts.add(fontSource);
								FontLoader.add(fontSource, this.nameToFonts);
							}
						} catch (Exception e) {
							LOG.log(Level.WARNING, "Failed to get font info for '" + font.getFontName() + "'.", e);
						}
					}
				}
				break;

			case IN_GENERIC_FONTS:
				String genericFamily = lName;
				String fontFamily = atts.getValue("font-family");

				List<FontFamily> entries = new ArrayList<FontFamily>();
				for (StringTokenizer i = new StringTokenizer(fontFamily, ","); i.hasMoreTokens();) {
					FontFamily entry = new FontFamily(i.nextToken());
					entries.add(entry);
				}

				FontFamilyList family = new FontFamilyList(
						(FontFamily[]) entries.toArray(new FontFamily[entries.size()]));
				this.genericToFamily.put(genericFamily, family);
				break;
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
				this.allFonts.add(this.fontSources[i]);
				FontLoader.add(this.fontSources[i], this.nameToFonts);
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

	private static class PdfFontSourceWrapper extends FontSourceWrapper implements PDFFontSource {
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
