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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

	private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(PDFFontSourceManager.class.getName());
	protected Map<String, Object> nameToFonts = new HashMap<String, Object>();

	protected Map<String, FontFamilyList> genericToFamily = new HashMap<String, FontFamilyList>();

	protected Map<URI, File> uriToFile = new HashMap<URI, File>();

	protected Collection<FontSource> allFonts = new ArrayList<FontSource>();

	/**
	 * フォント選択はfamily/weight/style/direction/text-orientation/policyだけを読む(size・
	 * OpenType featureは整形の話で選択に関与しない)ため、キャッシュキーは
	 * その5成分に限定する——FontStyle全体をキーにするとfeature集合や
	 * サイズ違いだけの大量のstyleで無駄に分裂する(2026-07-31、
	 * consult-codex-2026-07-31-font-features.txt §3.8)。
	 */
	protected record SelectionKey(net.zamasoft.pdfg2d.gc.font.FontFamilyList family, FontStyle.Weight weight,
			FontStyle.Style style, FontStyle.Direction direction, FontStyle.TextOrientation textOrientation,
			net.zamasoft.pdfg2d.gc.font.FontPolicyList policy) {
		static SelectionKey of(final FontStyle fontStyle) {
			return new SelectionKey(fontStyle.getFamily(), fontStyle.getWeight(), fontStyle.getStyle(),
					fontStyle.getDirection(), fontStyle.getTextOrientation(), fontStyle.getPolicy());
		}
	}

	transient protected Map<SelectionKey, FontSource[]> fontListCache = null;

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
			try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
				final var embedded = executor.submit(
						() -> FontLoader.readSystemFont(face, FontLoader.Type.EMBEDDED, face.local, null));
				final var cidIdentity = executor.submit(
						() -> FontLoader.readSystemFont(face, FontLoader.Type.CID_IDENTITY, face.local, null));
				list.add(await(embedded));
				list.add(await(cidIdentity));
			}
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
			// **可変フォント(fvar+glyf)は代表ウェイトを静的インスタンス化して
			// 並べる**(2026-08-20)。Google Fonts配信の既定が可変になり、
			// 従来は既定インスタンス(Regularとは限らない)1本しか使えず
			// font-weightの中間値が全て潰れていた。wght軸をCSSのウェイト
			// 段階(100〜900)で固定した9本を生成し、それぞれのOS/2
			// usWeightClassで通常のウェイト選択に乗せる。他の軸は既定値
			// 各エントリ = (フォントファイル, そのファイルに与えるウェイト)
			final java.util.List<Object[]> fontEntries = new ArrayList<>();
			try {
				final net.zamasoft.pdfg2d.font.FontFile ff = new net.zamasoft.pdfg2d.font.FontFile(file);
				final File sfnt = ff.getSfntFile();
				if (net.zamasoft.pdfg2d.font.VariableFontInstancer.isVariable(sfnt)) {
					final java.util.List<String> axisTags = net.zamasoft.pdfg2d.font.VariableFontInstancer
							.axisTags(sfnt);
					// font-variation-settingsディスクリプタの座標を基礎に置く
					// (2026-08-20)。wghtが明示されていればウェイト掃引はしない
					final java.util.Map<String, Double> baseAxes = face.variationSettings == null
							? java.util.Map.of()
							: face.variationSettings;
					if (baseAxes.containsKey("wght") || !axisTags.contains("wght")) {
						if (!baseAxes.isEmpty()) {
							final Double wght = baseAxes.get("wght");
							final net.zamasoft.pdfg2d.gc.font.FontStyle.Weight weight = wght == null
									? face.fontWeight
									: net.zamasoft.pdfg2d.gc.font.FontStyle.Weight.valueOf(
											"W_" + Math.max(1, Math.min(9, Math.round(wght / 100.0))) * 100);
							fontEntries.add(new Object[] {
									net.zamasoft.pdfg2d.font.VariableFontInstancer.instantiate(sfnt, baseAxes),
									weight });
						}
						// baseAxes空かつwght軸なし: インスタンス化不要(既定へ)
					} else if (face.fontWeight != net.zamasoft.pdfg2d.gc.font.FontStyle.Weight.W_400) {
						// ディスクリプタでウェイト明示——その1本だけを固定
						final java.util.Map<String, Double> axes = new java.util.LinkedHashMap<>(baseAxes);
						axes.put("wght", (double) face.fontWeight.w);
						fontEntries.add(new Object[] {
								net.zamasoft.pdfg2d.font.VariableFontInstancer.instantiate(sfnt, axes),
								face.fontWeight });
					} else {
						// 未指定(既定400)——CSSのウェイト段階9本を展開
						for (int w = 100; w <= 900; w += 100) {
							final java.util.Map<String, Double> axes = new java.util.LinkedHashMap<>(baseAxes);
							axes.put("wght", (double) w);
							fontEntries.add(new Object[] {
									net.zamasoft.pdfg2d.font.VariableFontInstancer.instantiate(sfnt, axes),
									net.zamasoft.pdfg2d.gc.font.FontStyle.Weight.valueOf("W_" + w) });
						}
					}
				}
			} catch (final Exception e) {
				LOG.log(java.util.logging.Level.WARNING, "variable font instantiation failed; using default instance",
						e);
				fontEntries.clear();
			}
			if (fontEntries.isEmpty()) {
				fontEntries.add(new Object[] { file, face.fontWeight });
			}
			for (final Object[] entry : fontEntries) {
				final File fontFile = (File) entry[0];
				final FontFace wface;
				if (entry[1] == face.fontWeight) {
					wface = face;
				} else {
					wface = new FontFace();
					wface.src = face.src;
					wface.index = face.index;
					wface.local = face.local;
					wface.fontFamily = face.fontFamily;
					wface.fontWeight = (net.zamasoft.pdfg2d.gc.font.FontStyle.Weight) entry[1];
					wface.fontStyle = face.fontStyle;
					wface.unicodeRange = face.unicodeRange;
					wface.panose = face.panose;
					wface.cmap = face.cmap;
					wface.vcmap = face.vcmap;
				}
				try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
					final var embedded = executor.submit(() -> {
						final var sources = new ArrayList<FontSource>();
						FontLoader.readTTF(sources, wface, FontLoader.Type.EMBEDDED, fontFile, wface.index, null);
						return sources;
					});
					final var cidIdentity = executor.submit(() -> {
						final var sources = new ArrayList<FontSource>();
						FontLoader.readTTF(sources, wface, FontLoader.Type.CID_IDENTITY, fontFile, wface.index, null);
						return sources;
					});
					list.addAll(await(embedded));
					list.addAll(await(cidIdentity));
				}
			}
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

	private static <T> T await(final Future<T> future) throws IOException {
		try {
			return future.get();
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while loading font sources.", e);
		} catch (final ExecutionException e) {
			final var cause = e.getCause();
			if (cause instanceof final IOException ioException) {
				throw ioException;
			}
			if (cause instanceof final RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof final Error error) {
				throw error;
			}
			throw new IOException("Failed to load font sources.", cause);
		}
	}

	public synchronized FontSource[] lookup(final FontStyle fontStyle) {
		if (fontStyle == null) {
			return this.allFonts.toArray(new FontSource[this.allFonts.size()]);
		}

		final SelectionKey key = SelectionKey.of(fontStyle);
		FontSource[] fonts;
		if (this.fontListCache != null) {
			fonts = this.fontListCache.get(key);
			if (fonts != null) {
				return fonts;
			}
		}

		final List<FontSource> fontList = new ArrayList<FontSource>();
		this.lookup(fontStyle, fontStyle.getFamily(), fontList, false);
		fonts = fontList.toArray(new FontSource[fontList.size()]);
		if (this.fontListCache == null) {
			this.fontListCache = new LRUCache<SelectionKey, FontSource[]>(128);
		}
		this.fontListCache.put(key, fonts);
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

				// text-orientationに応じて縦/横font sourceを選ぶ。mixedは
				// 両方を候補に残し、各sourceのcanDisplayで字種別に分かれる。
				Direction direction = fontStyle.getDirection();
				Direction fsDirection = font.getDirection();
				if (direction != Direction.TB && fsDirection == Direction.TB) {
					continue;
				}
				if (direction == Direction.TB) {
					if (fontStyle.getTextOrientation() == FontStyle.TextOrientation.UPRIGHT
							&& fsDirection != Direction.TB) {
						continue;
					}
					if (fontStyle.getTextOrientation() == FontStyle.TextOrientation.SIDEWAYS
							&& fsDirection == Direction.TB) {
						continue;
					}
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
