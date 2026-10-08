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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

	protected record GenericFamily(Locale lang, FontFamilyList family) {
	}

	protected Map<String, List<GenericFamily>> genericToLangFamily = new HashMap<String, List<GenericFamily>>();

	protected Map<URI, File> uriToFile = new HashMap<URI, File>();

	/**
	 * Variable font copies created for {@code @font-face} (2026-10-05).
	 * {@link #close()} deletes them along with resources fetched into {@link #uriToFile}.
	 */
	private final List<File> instanceFiles = new ArrayList<File>();

	protected Collection<FontSource> allFonts = new ArrayList<FontSource>();

	/**
	 * Font selection reads family/weight/style/direction/text-orientation/policy/width/lang
	 * (size and OpenType features concern shaping, not selection), so cache keys include only those components.
	 * Using all of FontStyle as the key needlessly fragments the cache among many styles
	 * that differ only in feature sets or sizes (2026-07-31,
	 * consult-codex-2026-07-31-font-features.txt §3.8).
	 */
	protected record SelectionKey(net.zamasoft.pdfg2d.gc.font.FontFamilyList family, FontStyle.Weight weight,
			FontStyle.Style style, FontStyle.Direction direction, FontStyle.TextOrientation textOrientation,
			net.zamasoft.pdfg2d.gc.font.FontPolicyList policy, int widthClass, Locale lang) {
		static SelectionKey of(final FontStyle fontStyle) {
			return new SelectionKey(fontStyle.getFamily(), fontStyle.getWeight(), fontStyle.getStyle(),
					fontStyle.getDirection(), fontStyle.getTextOrientation(), fontStyle.getPolicy(),
					fontStyle.getWidthClass(), fontStyle.getLang());
		}
	}

	transient protected Map<SelectionKey, FontSource[]> fontListCache = null;

	protected final boolean strictMatchName;

	/**
	 * Whether the embedded forms of fonts the font policy does not select stay candidates, ranked after those it does
	 * (2026-10-08). Set for the fonts a document loads itself ({@code @font-face}): the author asked for them by name,
	 * and under the default policy (core and CID-keyed fonts only) they were dropped without a word.
	 */
	protected final boolean outsidePolicy;

	public PDFFontSourceManager(boolean strictMatchName) {
		this(strictMatchName, false);
	}

	public PDFFontSourceManager(final boolean strictMatchName, final boolean outsidePolicy) {
		this.strictMatchName = strictMatchName;
		this.outsidePolicy = outsidePolicy;
	}

	public PDFFontSourceManager() {
		this(false);
	}

	/**
	 * Deletes font files created by this manager (fetched resources and variable font copies), closing opened fonts as well.
	 */
	public void close() {
		final List<File> files = new ArrayList<File>(this.uriToFile.values());
		files.addAll(this.instanceFiles);
		for (final File file : files) {
			net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource.release(file);
			file.delete();
		}
		this.uriToFile.clear();
		this.instanceFiles.clear();
	}

	private File instantiate(final File sfnt, final Map<String, Double> axes) throws IOException {
		final File instance = net.zamasoft.pdfg2d.font.VariableFontInstancer.instantiate(sfnt, axes);
		this.instanceFiles.add(instance);
		return instance;
	}

	public synchronized void addFontFace(FontFace face) throws IOException {
		final List<FontSource> list = new ArrayList<FontSource>();
		// A document's own fonts ({@link #outsidePolicy}) get only the embedded form (2026-10-08): the CID-identity
		// form, which leaves the font out of the PDF, ranked above the embedded one under the cid-identity policy, and
		// the reader does not have the document's font
		final boolean cidIdentityForm = !this.outsidePolicy;
		// Selections made before this face may have to take it (2026-10-08)
		this.fontListCache = null;
		if (face.local != null) {
			try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
				final var embedded = executor.submit(
						() -> FontLoader.readSystemFont(face, FontLoader.Type.EMBEDDED, face.local, null));
				final var cidIdentity = cidIdentityForm ? executor.submit(
						() -> FontLoader.readSystemFont(face, FontLoader.Type.CID_IDENTITY, face.local, null)) : null;
				list.add(await(embedded));
				if (cidIdentity != null) {
					list.add(await(cidIdentity));
				}
			}
		} else {
			File file;
			if (face.src.isFile()) {
				file = face.src.getFile();
			} else {
				file = this.uriToFile.get(face.src.getURI());
				if (file == null) {
					file = File.createTempFile("copper-font-face", ".font");
					// Own the file until registration and delete it if fetching fails (until 2026-10-04, failures excluded it from close()
					// cleanup, leaving it until process exit through deleteOnExit).
					try (InputStream in = face.src.getInputStream(); OutputStream out = new FileOutputStream(file)) {
						in.transferTo(out);
					} catch (final IOException | RuntimeException e) {
						file.delete();
						throw e;
					}
					this.uriToFile.put(face.src.getURI(), file);
				}
			}
			// **Create and register static instances at representative weights for variable fonts (fvar+glyf)**
			// (2026-08-20). Google Fonts began distributing variable fonts by default;
			// previously only one default instance (not necessarily Regular) was available,
			// collapsing all intermediate font-weight values. Generate nine instances with wght pinned
			// at CSS weight steps (100-900), then use each instance's OS/2
			// usWeightClass for normal weight selection. Other axes use their defaults.
			// Each entry = (font file, weight assigned to that file).
			final java.util.List<Object[]> fontEntries = new ArrayList<>();
			try (final net.zamasoft.pdfg2d.font.FontFile ff = new net.zamasoft.pdfg2d.font.FontFile(file)) {
				// Decompressed WOFF/WOFF2 files are no longer needed after creating copies. Close and delete them.
				final File sfnt = ff.getSfntFile();
				if (net.zamasoft.pdfg2d.font.VariableFontInstancer.isVariable(sfnt)) {
					final java.util.List<String> axisTags = net.zamasoft.pdfg2d.font.VariableFontInstancer
							.axisTags(sfnt);
					// Use the font-variation-settings descriptor's coordinates as the base
					// (2026-08-20). If wght is explicit, do not sweep weights.
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
							fontEntries.add(new Object[] { this.instantiate(sfnt, baseAxes), weight });
						}
						// Empty baseAxes and no wght axis: no instantiation needed (use default).
					} else if (face.fontWeight != net.zamasoft.pdfg2d.gc.font.FontStyle.Weight.W_400) {
						// Explicit weight in the descriptor: pin only that instance.
						final java.util.Map<String, Double> axes = new java.util.LinkedHashMap<>(baseAxes);
						axes.put("wght", (double) face.fontWeight.w);
						fontEntries.add(new Object[] { this.instantiate(sfnt, axes), face.fontWeight });
					} else {
						// Unspecified (default 400): expand to nine CSS weight steps.
						for (int w = 100; w <= 900; w += 100) {
							final java.util.Map<String, Double> axes = new java.util.LinkedHashMap<>(baseAxes);
							axes.put("wght", (double) w);
							fontEntries.add(new Object[] { this.instantiate(sfnt, axes),
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
					wface.widthClass = face.widthClass;
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
					final var cidIdentity = cidIdentityForm ? executor.submit(() -> {
						final var sources = new ArrayList<FontSource>();
						FontLoader.readTTF(sources, wface, FontLoader.Type.CID_IDENTITY, fontFile, wface.index, null);
						return sources;
					}) : null;
					list.addAll(await(embedded));
					if (cidIdentity != null) {
						list.addAll(await(cidIdentity));
					}
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

	/**
	 * Appends the candidates of one family of the style's list to {@code fontList}, in this manager's order
	 * (2026-10-08). {@code FontManagerImpl} takes the families in order and, within each, the document's own faces
	 * before the installed fonts.
	 *
	 * @param fontStyle the font style (its family list is not used)
	 * @param family    the family
	 * @param fontList  the list to append to
	 */
	public synchronized void lookupFamily(final FontStyle fontStyle, final FontFamily family,
			final List<FontSource> fontList) {
		this.lookup(fontStyle, new FontFamilyList(family), fontList, false);
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

	/**
	 * Selects language-specific generic family chains in order: exact match, language + script, language only.
	 * For equal priority, retains the earlier configuration in document order.
	 */
	protected FontFamilyList resolveGenericFamily(final String name, final Locale requestedLang) {
		if (requestedLang == null || requestedLang.getLanguage().isEmpty()) {
			return this.genericToFamily.get(name);
		}
		final Locale requested = Locale.forLanguageTag(requestedLang.toLanguageTag());
		final List<GenericFamily> candidates = this.genericToLangFamily.get(name);
		GenericFamily best = null;
		int bestScore = 0;
		if (candidates != null) {
			for (final GenericFamily candidate : candidates) {
				final Locale configured = candidate.lang();
				int score = 0;
				if (configured.toLanguageTag().equalsIgnoreCase(requested.toLanguageTag())) {
					score = 3;
				} else if (!configured.getScript().isEmpty() && configured.getCountry().isEmpty()
						&& configured.getVariant().isEmpty()
						&& configured.getLanguage().equalsIgnoreCase(requested.getLanguage())
						&& configured.getScript().equalsIgnoreCase(requested.getScript())) {
					score = 2;
				} else if (configured.getScript().isEmpty() && configured.getCountry().isEmpty()
						&& configured.getVariant().isEmpty()
						&& configured.getLanguage().equalsIgnoreCase(requested.getLanguage())) {
					score = 1;
				}
				if (score > bestScore) {
					best = candidate;
					bestScore = score;
				}
			}
		}
		return best == null ? this.genericToFamily.get(name) : best.family();
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
				FontFamilyList gfamily = this.resolveGenericFamily(name, fontStyle.getLang());
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
						// Only the embedded form: readers do not have a document's own font installed
						if (!this.outsidePolicy || type != Type.EMBEDDED) {
							continue;
						}
						order = 1;
					}
				} else {
					order = 1;
				}

				// Select vertical/horizontal font sources according to text-orientation. mixed keeps
				// both as candidates, with each source's canDisplay distinguishing character types.
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

				// Width class (font-stretch, 2026-08-29). Among fonts tied on italic/weight,
				// select the closest width class. Order follows css-fonts-4 §5.2 step 1:
				// if the requested width is normal or narrower, try all narrower widths first (nearest first), then wider widths.
				// Reverse that order if the requested width is wider than normal. Five bits (penalty 0..16).
				order <<= 5;
				order |= 31 - widthPenalty(fontStyle.getWidthClass(), font.getWidthClass());

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

	/**
	 * Penalty for font width class {@code have} relative to requested width class {@code want} (0=match).
	 * The preferred side (narrower for want≤5, wider for want>5) uses the distance itself (0..8);
	 * the other side uses 8+distance (9..16), so every font on the preferred side is selected
	 * before any on the opposite side (css-fonts-4 §5.2 step 1, 2026-08-29).
	 */
	static int widthPenalty(final int want, final int have) {
		final boolean preferred = want <= net.zamasoft.pdfg2d.font.FontSource.NORMAL_WIDTH_CLASS ? have <= want
				: have >= want;
		final int distance = Math.abs(want - have);
		return preferred ? distance : 8 + distance;
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

		@Override
		public boolean canDisplayUpright(int c) {
			return this.includes.canDisplay(c) && this.source.canDisplayUpright(c);
		}

		public PDFFont createFont(String name, ObjectRef fontRef) {
			return ((PDFFontSource) this.source).createFont(name, fontRef);
		}

		public Type getType() {
			return ((PDFFontSource) this.source).getType();
		}
	}
}
