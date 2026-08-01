package net.zamasoft.pdfg2d.pdf.font;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xml.sax.Attributes;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.font.table.Table;
import net.zamasoft.pdfg2d.font.AbstractFontSource;
import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.gc.font.Panose;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.CMap;
import net.zamasoft.pdfg2d.pdf.font.cid.WArray;
import net.zamasoft.pdfg2d.pdf.font.cid.Width;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.SystemEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.OpenTypeCIDIdentityFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.SystemCIDIdentityFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.keyed.CIDKeyedFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.keyed.OpenTypeCIDKeyedFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.keyed.SystemCIDKeyedFontSource;
import net.zamasoft.pdfg2d.pdf.font.type1.AFMFontInfo;
import net.zamasoft.pdfg2d.pdf.font.type1.AFMParser;
import net.zamasoft.pdfg2d.pdf.font.type1.Encoding;
import net.zamasoft.pdfg2d.pdf.font.type1.GlyphMap;
import net.zamasoft.pdfg2d.pdf.font.type1.LetterType1FontSource;
import net.zamasoft.pdfg2d.pdf.font.type1.SymbolicType1FontSource;
import net.zamasoft.pdfg2d.pdf.font.util.MultimapUtils;
import net.zamasoft.pdfg2d.util.NumberUtils;

public final class FontLoader {
	private static final Logger LOG = Logger.getLogger(FontLoader.class.getName());

	public static enum Type {

		EMBEDDED, CID_IDENTITY, CID_KEYED;
	}

	private FontLoader() {
		// unused
	}

	/**
	 * Loads a TrueType or Type1 font file.
	 * 
	 * @param list
	 * @param face
	 * @param type
	 * @param ttfFile
	 * @param index
	 * @param nameToCMap
	 * @throws IOException
	 */
	public static void readTTF(List<FontSource> list, FontFace face, Type type, File ttfFile, int index,
			Map<String, CMap> nameToCMap) throws IOException {
		String fileName = ttfFile.getName();
		if (fileName.endsWith(".pfa") || fileName.endsWith(".PFA") || fileName.endsWith(".pfb")
				|| fileName.endsWith(".PFB") || fileName.endsWith(".f3b") || fileName.endsWith(".F3B")) {
			// Load Type1 font(旧実装はJDK 1.4互換の反射経由だった——
			// Font.createFont(int, File)はJava 5からの標準API、直呼びへ。
			// TYPE1_FONT定数の値は反射時代に渡していた1と同じ)
			try {
				java.awt.Font awtFont = java.awt.Font.createFont(java.awt.Font.TYPE1_FONT, ttfFile);
				list.add(FontLoader.readSystemFont(face, type, awtFont, nameToCMap));
				return;
			} catch (Exception e) {
				LOG.log(Level.WARNING, "Failed to load Type1 font.", e);
			}
		}

		LOG.fine("TrueType font: " + face.fontFamily);

		switch (type) {
			case EMBEDDED:
				for (int i = 0; i < 2; ++i) {
					OpenTypeEmbeddedCIDFontSource tefont = new OpenTypeEmbeddedCIDFontSource(ttfFile, index,
							i == 0 ? Direction.LTR : Direction.TB);
					if (face.panose != null) {
						tefont.setPanose(face.panose);
					}
					if (face.fontFamily != null) {
						tefont.setFontName(face.fontFamily.get(0).getName());
					}
					tefont.setItalic(face.fontStyle == Style.ITALIC);
					tefont.setWeight(face.fontWeight);
					if (i == 0) {
						list.add(tefont);
						if (tefont.getOpenTypeFont().getTable(Table.GSUB) == null) {
							break;
						}
					} else {
						list.add(list.size() - 1, tefont);
					}
				}
				break;
			case CID_IDENTITY:
				for (int i = 0; i < 2; ++i) {
					OpenTypeCIDIdentityFontSource tifont = new OpenTypeCIDIdentityFontSource(ttfFile, index,
							i == 0 ? Direction.LTR : Direction.TB);
					if (face.panose != null) {
						tifont.setPanose(face.panose);
					}
					if (face.fontFamily != null) {
						tifont.setFontName(face.fontFamily.get(0).getName());
					}
					tifont.setItalic(face.fontStyle == Style.ITALIC);
					tifont.setWeight(face.fontWeight);
					if (i == 0) {
						list.add(tifont);
						if (tifont.getOpenTypeFont().getTable(Table.GSUB) == null) {
							break;
						}
					} else {
						list.add(list.size() - 1, tifont);
					}
				}
				break;
			case CID_KEYED:
				CMap cmapObj = (CMap) nameToCMap.get(face.cmap);
				CMap vcmapObj = (face.vcmap == null ? null : (CMap) nameToCMap.get(face.vcmap));
				for (int i = 0; i < 2; ++i) {
					OpenTypeCIDKeyedFontSource ckfont = new OpenTypeCIDKeyedFontSource(cmapObj,
							i == 0 ? vcmapObj : null,
							ttfFile, index);
					if (face.panose != null) {
						ckfont.setPanose(face.panose);
					}
					if (face.fontFamily != null) {
						ckfont.setFontName(face.fontFamily.get(0).getName());
					}
					ckfont.setItalic(face.fontStyle == Style.ITALIC);
					ckfont.setWeight(face.fontWeight);
					list.add(ckfont);
					if (vcmapObj == null) {
						break;
					}
				}
				break;
			default:
				throw new IllegalArgumentException();
		}
	}

	/**
	 * Loads a system font.
	 * 
	 * @param face
	 * @param type
	 * @param awtFont
	 * @return
	 * @throws IOException
	 */
	public static PDFFontSource readSystemFont(FontFace face, Type type, java.awt.Font awtFont,
			Map<String, CMap> nameToCMap) throws IOException {
		CIDFontSource source;
		switch (type) {
			case EMBEDDED:
				SystemEmbeddedCIDFontSource sefont = new SystemEmbeddedCIDFontSource(awtFont);
				source = sefont;
				if (face.panose != null) {
					sefont.setPanose(face.panose);
				}
				if (face.fontFamily != null) {
					sefont.setFontName(face.fontFamily.get(0).getName());
				}
				break;
			case CID_IDENTITY:
				SystemCIDIdentityFontSource scfont = new SystemCIDIdentityFontSource(awtFont);
				source = scfont;
				if (face.panose != null) {
					scfont.setPanose(face.panose);
				}
				if (face.fontFamily != null) {
					scfont.setFontName(face.fontFamily.get(0).getName());
				}
				break;
			case CID_KEYED:
				CMap cmapObj = (CMap) nameToCMap.get(face.cmap);
				CMap vcmapObj = (face.vcmap == null ? null : (CMap) nameToCMap.get(face.vcmap));
				SystemCIDKeyedFontSource ckfont = new SystemCIDKeyedFontSource(cmapObj, vcmapObj, awtFont);
				source = ckfont;
				if (face.panose != null) {
					ckfont.setPanose(face.panose);
				}
				if (face.fontFamily != null) {
					ckfont.setFontName(face.fontFamily.get(0).getName());
				}
				break;
			default:
				throw new IllegalArgumentException();
		}

		((AbstractFontSource) source).setItalic(face.fontStyle == Style.ITALIC);
		((AbstractFontSource) source).setWeight(face.fontWeight);

		LOG.fine("System font: " + source);
		return source;
	}

	/**
	 * Loads a CID-keyed font.
	 * 
	 * @param warrayData
	 * @param face
	 * @param nameToCMap
	 * @return
	 * @throws IOException
	 */
	public static PDFFontSource[] readCIDKeyedFont(Source warrayData, FontFace face, Map<String, CMap> nameToCMap)
			throws IOException {
		CMap cmapObj = (CMap) nameToCMap.get(face.cmap);
		CMap vcmapObj = (face.vcmap == null ? null : (CMap) nameToCMap.get(face.vcmap));
		PDFFontSource[] result = new PDFFontSource[vcmapObj == null ? 1 : 2];

		for (int k = 0; k < result.length; ++k) {
			CIDKeyedFontSource source = new CIDKeyedFontSource(cmapObj, k == 0 ? vcmapObj : null);
			source.setFontName(face.fontFamily.get(0).getName());

			if (face.panose != null) {
				source.setPanose(face.panose);
			}

			// Read warray file
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(warrayData.getInputStream(), "ISO-8859-1"))) {
				for (String line = in.readLine(); line != null; line = in.readLine()) {
					if (line.length() > 0 && line.charAt(0) == '#') {
						continue;
					}
					int colon = line.indexOf(':');
					if (colon == -1) {
						continue;
					}
					String name = line.substring(0, colon).trim();
					String value = line.substring(colon + 1).trim();
					if (name.equalsIgnoreCase("bbox")) {
						String[] values = value.split(" ");
						BBox bbox = new BBox(Short.parseShort(values[0]), Short.parseShort(values[1]),
								Short.parseShort(values[2]), Short.parseShort(values[3]));
						source.setBBox(bbox);
					} else if (name.equalsIgnoreCase("ascent")) {
						source.setAscent(Short.parseShort(value));
					} else if (name.equalsIgnoreCase("descent")) {
						source.setDescent(Short.parseShort(value));
					} else if (name.equalsIgnoreCase("capHeight")) {
						source.setCapHeight(Short.parseShort(value));
					} else if (name.equalsIgnoreCase("xHeight")) {
						source.setXHeight(Short.parseShort(value));
					} else if (name.equalsIgnoreCase("StemH")) {
						source.setStemH(Short.parseShort(value));
					} else if (name.equalsIgnoreCase("StemV")) {
						source.setStemV(Short.parseShort(value));
					} else if (name.equalsIgnoreCase("warray")) {
						int count = Integer.parseInt(in.readLine());
						short defaultWidth = Short.parseShort(in.readLine());
						Width[] widths = new Width[count];
						for (int i = 0; i < count; ++i) {
							String wline = in.readLine();
							String[] values = wline.split(" ");
							short[] w = new short[values.length - 2];
							for (int j = 0; j < w.length; ++j) {
								w[j] = Short.parseShort(values[j + 2]);
							}
							widths[i] = new Width(Short.parseShort(values[0]), Short.parseShort(values[1]), w);
						}
						WArray warrayObj = new WArray(defaultWidth, widths);
						source.setWArray(warrayObj);
					}
				}
			}

			source.setItalic(face.fontStyle == Style.ITALIC);
			source.setWeight(face.fontWeight);

			LOG.fine("CID-Keyed font: " + source);
			result[k] = source;
		}
		return result;
	}

	public static void readSystemFont(FontFace face, List<FontSource> list, String types, java.awt.Font font,
			Map<String, CMap> nameToCMap) throws IOException {
		if (types.indexOf("cid-keyed") != -1) {
			list.add(FontLoader.readSystemFont(face, Type.CID_KEYED, font, nameToCMap));
		}
		if (types.indexOf("cid-identity") != -1) {
			list.add(FontLoader.readSystemFont(face, Type.CID_IDENTITY, font, nameToCMap));
		}
		if (types.indexOf("embedded") != -1) {
			list.add(FontLoader.readSystemFont(face, Type.EMBEDDED, font, nameToCMap));
		}
	}

	/**
	 * Parses an AFM (Adobe Font Metrics) file.
	 * 
	 * @param file
	 * @return
	 * @throws ParseException
	 * @throws IOException
	 */
	private static AFMFontInfo parseAFM(InputStream in) throws ParseException, IOException {
		AFMParser afm = new AFMParser();
		AFMFontInfo font = afm.parse(new BufferedInputStream(in));
		return font;
	}

	/**
	 * Loads a letter-based Type1 font.
	 * 
	 * @param unicodeEncoding
	 * @param pdfEncoding
	 * @param in
	 * @return
	 * @throws ParseException
	 * @throws IOException
	 */
	public static PDFFontSource readLetterType1Font(GlyphMap unicodeEncoding, Encoding pdfEncoding, InputStream in)
			throws ParseException, IOException {
		AFMFontInfo font = parseAFM(in);
		LetterType1FontSource source = new LetterType1FontSource(unicodeEncoding, pdfEncoding, font);
		LOG.fine("Core AFM font: " + source);
		return source;
	}

	/**
	 * Loads a symbolic Type1 font.
	 * 
	 * @param in
	 * @param toUnicodeFile
	 * @return
	 * @throws ParseException
	 * @throws IOException
	 */
	public static PDFFontSource readSymbolType1Font(InputStream in, Source toUnicodeFile)
			throws ParseException, IOException {
		AFMFontInfo font = parseAFM(in);
		SymbolicType1FontSource source = new SymbolicType1FontSource(font, toUnicodeFile);
		LOG.fine("Core Symbol font: " + source);
		return source;
	}

	private static boolean parseItalic(String italic) {
		return "true".equals(italic);
	}

	private static Weight parseWeight(String weight) {
		if (weight == null) {
			return Weight.W_400;
		} else if (weight.equals("100")) {
			return Weight.W_100;
		} else if (weight.equals("200")) {
			return Weight.W_200;
		} else if (weight.equals("300")) {
			return Weight.W_300;
		} else if (weight.equals("400")) {
			return Weight.W_400;
		} else if (weight.equals("500")) {
			return Weight.W_500;
		} else if (weight.equals("600")) {
			return Weight.W_600;
		} else if (weight.equals("700")) {
			return Weight.W_700;
		} else if (weight.equals("800")) {
			return Weight.W_800;
		} else if (weight.equals("900")) {
			return Weight.W_900;
		}
		return Weight.W_400;
	}

	private static Panose decodePanose(String panoseStr) {
		String[] codes = panoseStr.split(" ");
		short cFamilyClass = 0;
		cFamilyClass |= Byte.parseByte(codes[0].trim()) << 8;
		cFamilyClass |= Byte.parseByte(codes[1].trim());
		byte[] bytes = new byte[10];
		for (int i = 0; i < bytes.length; ++i) {
			bytes[i] = Byte.parseByte(codes[i + 2].trim());
		}
		Panose panose = new Panose(cFamilyClass, bytes);
		return panose;
	}

	public static FontFace toFontFace(Attributes atts) {
		FontFace face = new FontFace();
		face.cmap = atts.getValue("cmap");
		face.vcmap = atts.getValue("vcmap");
		String italic = atts.getValue("italic");
		if (italic != null) {
			face.fontStyle = FontLoader.parseItalic(italic) ? Style.ITALIC : Style.NORMAL;
		}
		String weight = atts.getValue("weight");
		if (weight != null) {
			face.fontWeight = FontLoader.parseWeight(weight);
		}
		String panose = atts.getValue("panose");
		if (panose != null) {
			face.panose = FontLoader.decodePanose(panose);
		}
		String fontName = atts.getValue("name");
		if (fontName != null) {
			face.fontFamily = new FontFamilyList(FontFamily.create(fontName));
		}

		return face;
	}

	public static void add(FontSource source, Map<String, Object> nameToFonts) {
		List<String> m = new ArrayList<String>();
		if (source.getFontName() != null) {
			String name = FontUtils.normalizeName(source.getFontName());
			m.add(name);
			MultimapUtils.put(nameToFonts, name, source);
		}
		String[] aliases = source.getAliases();
		for (int i = 0; i < aliases.length; ++i) {
			String aliase = aliases[i];
			String name = FontUtils.normalizeName(aliase);
			if (m.contains(name)) {
				continue;
			}
			m.add(name);
			MultimapUtils.put(nameToFonts, name, source);
		}
	}
}
