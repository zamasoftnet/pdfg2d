package net.zamasoft.pdfg2d.pdf.font;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontSourceManager;
import net.zamasoft.pdfg2d.font.FontSourceWrapper;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList.FontPolicy;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.gc.font.UnicodeRangeList;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSource.Type;
import net.zamasoft.pdfg2d.pdf.font.util.MultimapUtils;
import net.zamasoft.pdfg2d.util.NumberUtils;

/**
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PDFFontSourceManager implements FontSourceManager, Closeable {
	protected Map<String, Object> nameToFonts = new HashMap<String, Object>();

	protected Map<String, FontFamilyList> genericToFamily = new HashMap<String, FontFamilyList>();

	protected Map<URI, File> uriToFile = new HashMap<URI, File>();

	protected Collection<FontSource> allFonts = new ArrayList<FontSource>();

	transient protected Map<FontStyle, FontSource[]> fontListCache = null;

	protected final boolean strictMatchName;

	public PDFFontSourceManager(boolean strictMatchName) {
		this.strictMatchName = strictMatchName;
	}

	public PDFFontSourceManager() {
		this(false);
	}

	public void close() {
		for (Iterator<File> i = this.uriToFile.values().iterator(); i.hasNext();) {
			File file = i.next();
			file.delete();
		}
	}

	public synchronized void addFontFace(FontFace face) throws IOException {
		final List<FontSource> list = new ArrayList<FontSource>();
		if (face.local != null) {
			list.add(FontLoader.readSystemFont(face, FontLoader.Type.EMBEDDED, face.local, null));
			list.add(FontLoader.readSystemFont(face, FontLoader.Type.CID_IDENTITY, face.local, null));
		} else {
			File file;
			if (face.src.isFile()) {
				file = face.src.getFile();
			} else {
				file = this.uriToFile.get(face.src.getURI());
				if (file == null) {
					byte[] buff = new byte[8192];
					file = File.createTempFile("copper-font-face", ".font");
					file.deleteOnExit();
					try (InputStream in = face.src.getInputStream(); OutputStream out = new FileOutputStream(file)) {
						for (int len = in.read(buff); len != -1; len = in.read(buff)) {
							out.write(buff, 0, len);
						}
					}
					this.uriToFile.put(face.src.getURI(), file);
				}
			}
			FontLoader.readTTF(list, face, FontLoader.Type.EMBEDDED, file, face.index, null);
			FontLoader.readTTF(list, face, FontLoader.Type.CID_IDENTITY, file, face.index, null);
		}

		if (face.unicodeRange != null && !face.unicodeRange.isEmpty()) {
			for (int i = 0; i < list.size(); ++i) {
				PDFFontSource source = (PDFFontSource) list.get(i);
				list.set(i, new PdfFontSourceWrapper(source, face.unicodeRange));
			}
		}
		this.allFonts.addAll(list);

		final List<String> m = new ArrayList<String>();
		if (face.fontFamily != null) {
			for (int i = 0; i < face.fontFamily.getLength(); ++i) {
				final FontFamily family = face.fontFamily.get(i);
				String name = family.getName();
				if (family.isGenericFamily()) {
					// Override generic font name
					final FontFamilyList generics = this.genericToFamily.get(name);
					if (generics == null) {
						this.genericToFamily.put(name, new FontFamilyList(new FontFamily(name)));
					} else {
						boolean found = false;
						for (int j = 0; j < generics.getLength(); ++j) {
							if (generics.get(j).getName().equals(name)) {
								found = true;
								break;
							}
						}
						if (!found) {
							final FontFamily[] families = new FontFamily[generics.getLength() + 1];
							for (int j = 0; j < generics.getLength(); ++j) {
								families[j] = generics.get(j);
							}
							families[generics.getLength()] = new FontFamily(name);
							this.genericToFamily.put(name, new FontFamilyList(families));
						}
					}
				}
				name = FontUtils.normalizeName(name);
				if (m.contains(name)) {
					continue;
				}
				for (int j = 0; j < list.size(); ++j) {
					FontSource source = list.get(j);
					MultimapUtils.putDirect(this.nameToFonts, name, source);
				}
				m.add(name);
			}
		}
		// Insert font names obtained from FontSource
		// Commented out since not needed for CSS @font-face
		// for (int j = 0; j < list.size(); ++j) {
		// FontSource source = (FontSource) list.get(j);
		// String[] aliases = source.getAliases();
		// if (aliases != null) {
		// for (int i = 0; i < aliases.length; ++i) {
		// String name = FontUtils.normalizeName(aliases[i]);
		// if (m.contains(name)) {
		// continue;
		// }
		// MultimapUtils.putDirect(this.nameToFonts, name, source);
		// m.add(name);
		// }
		// }
		// String name = FontUtils.normalizeName(source.getFontName());
		// if (m.contains(name)) {
		// continue;
		// }
		// MultimapUtils.putDirect(this.nameToFonts, name, source);
		// m.add(name);
		// }
	}

	public synchronized FontSource[] lookup(final FontStyle fontStyle) {
		if (fontStyle == null) {
			return this.allFonts.toArray(new FontSource[this.allFonts.size()]);
		}

		FontSource[] fonts;
		if (this.fontListCache != null) {
			fonts = this.fontListCache.get(fontStyle);
			if (fonts != null) {
				return fonts;
			}
		}

		final List<FontSource> fontList = new ArrayList<FontSource>();
		this.lookup(fontStyle, fontStyle.getFamily(), fontList, false);
		fonts = fontList.toArray(new FontSource[fontList.size()]);
		if (this.fontListCache == null) {
			this.fontListCache = new LRUCache<FontStyle, FontSource[]>(128);
		}
		this.fontListCache.put(fontStyle, fonts);
		return fonts;
	}

	private static class LRUCache<K, V> extends LinkedHashMap<K, V> {
		private static final long serialVersionUID = 0;

		private final int maxEntries;

		LRUCache(final int maxEntries) {
			super(maxEntries + 1, 0.75f, true);
			this.maxEntries = maxEntries;
		}

		protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
			return this.size() > this.maxEntries;
		}
	}

	protected void lookup(FontStyle fontStyle, FontFamilyList family, List<FontSource> fontList, boolean recurse) {
		for (int i = 0; i < family.getLength(); ++i) {
			FontSource[] fonts;
			FontFamily entry = family.get(i);
			String name = entry.getName();

			// Get fonts matching the family name
			if (entry.isGenericFamily()) {
				if (recurse) {
					throw new IllegalStateException("Generic font defined by another generic font");
				}
				FontFamilyList gfamily = this.genericToFamily.get(name);
				if (gfamily != null) {
					this.lookup(fontStyle, gfamily, fontList, true);
				}
				continue;
			} else {
				name = FontUtils.normalizeName(name);
				fonts = MultimapUtils.get(this.nameToFonts, name);
				if (fonts == null) {
					continue;
				}
			}

			// Match against each condition
			FontPolicyList policy = fontStyle.getPolicy();
			Object[][] orders = new Object[fonts.length][2];
			for (int j = 0; j < fonts.length; ++j) {
				FontSource font = fonts[j];
				int order = 0;

				// Font type is the highest priority condition
				if (font instanceof PDFFontSource) {
					PDFFontSource pdfFont = (PDFFontSource) font;
					Type type = pdfFont.getType();
					for (int k = 0; k < policy.getLength(); ++k) {
						FontPolicy policyCode = policy.get(k);
						switch (policyCode) {
							case CORE:
								// CORE
								if (type != Type.CORE) {
									continue;
								}
								break;

							case CID_KEYED:
								// CID-Keyed
								if (type != Type.CID_KEYED) {
									continue;
								}
								break;

							case CID_IDENTITY:
								// CID Identity
								if (type != Type.CID_IDENTITY) {
									continue;
								}
								break;

							case EMBEDDED:
								// Embedded
								if (type != Type.EMBEDDED) {
									continue;
								}
								break;

							case OUTLINES:
								// Outlines
								continue;

							default:
								throw new IllegalStateException();
						}
						order = policy.getLength() - k + 1;
						break;
					}
					if (order == 0) {
						continue;
					}
				} else {
					order = 1;
				}

				// Exclude vertical fonts in horizontal writing mode
				Direction direction = fontStyle.getDirection();
				Direction fsDirection = font.getDirection();
				if (direction != Direction.TB && fsDirection == Direction.TB) {
					continue;
				}

				// Prioritize exact family name matches
				order <<= 4;
				String fontName = FontUtils.normalizeName(font.getFontName());
				if (fontName.equals(name)) {
					order |= 1;
				} else if (this.strictMatchName) {
					continue;
				}

				// Italic check has higher priority than weight
				order <<= 4;
				Style style = fontStyle.getStyle();
				if (style == Style.ITALIC) {
					if (font.isItalic()) {
						order |= 1;
					}
				} else if (style == Style.NORMAL) {
					if (!font.isItalic()) {
						order |= 1;
					}
				}

				// Weight check
				order <<= 4;
				Weight weight = fontStyle.getWeight();
				int delta = Math.abs(font.getWeight().w - weight.w);
				order |= (0xF & ((1000 - delta) / 100));

				// Oblique has lower priority since it can use transformation
				order <<= 4;
				if (style == Style.OBLIQUE) {
					if (font.isItalic()) {
						order |= 1;
					}
				}
				orders[j][0] = NumberUtils.intValue(order);
				orders[j][1] = font;
			}
			Arrays.sort(orders, FONT_COMP);
			for (int j = 0; j < fonts.length; ++j) {
				Integer order = (Integer) orders[j][0];
				if (order != null) {
					FontSource font = (FontSource) orders[j][1];
					fontList.add(font);
				}
			}
		}
	}

	private static final Comparator<Object[]> FONT_COMP = new Comparator<Object[]>() {
		public int compare(Object[] f1, Object[] f2) {
			Integer i1 = (Integer) f1[0];
			Integer i2 = (Integer) f2[0];
			if (i1 == null & i2 == null) {
				return 0;
			}
			if (i1 == null & i2 != null) {
				return 1;
			}
			if (i1 != null & i2 == null) {
				return -1;
			}
			if (i1.intValue() < i2.intValue()) {
				return 1;
			}
			if (i1.intValue() > i2.intValue()) {
				return -1;
			}
			return 0;
		}
	};

	private static class PdfFontSourceWrapper extends FontSourceWrapper implements PDFFontSource {
		private static final long serialVersionUID = 1L;

		protected final UnicodeRangeList includes;

		public PdfFontSourceWrapper(PDFFontSource source, UnicodeRangeList includes) {
			super(source);
			this.includes = includes;
			assert !includes.isEmpty();
		}

		public boolean canDisplay(int c) {
			if (this.includes.canDisplay(c)) {
				return this.source.canDisplay(c);
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
}
