package net.zamasoft.pdfg2d.font.emoji;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.zamasoft.pdfg2d.font.AbstractFontSource;
import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.Panose;

/**
 * Font source for emoji characters using Google Noto Emoji SVG files.
 * 
 * <p>
 * This class provides emoji font metadata and creates {@link EmojiFont}
 * instances.
 * Emoji mappings are loaded from the bundled emoji.zip resource file containing
 * an INDEX of available emoji codes.
 * 
 * <p>
 * Two singleton instances are provided for left-to-right and top-to-bottom
 * text directions: {@link #INSTANCES_LTR} and {@link #INSTANCES_TB}.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class EmojiFontSource extends AbstractFontSource {

	private static final long serialVersionUID = 1L;

	/** Bounding box for emoji glyphs (1000 units square). */
	private static final BBox BBOX = new BBox((short) 0, DEFAULT_DESCENT, (short) 1000, DEFAULT_ASCENT);

	/** Map from emoji code string to font glyph ID. */
	protected static final Map<String, Integer> codeToGid;

	/** Map from font glyph ID to emoji code string. */
	protected static final Map<Integer, String> gidToCode;

	// Static initializer to load emoji index from bundled resource
	static {
		final var ctog = new HashMap<String, Integer>();
		final var gtoc = new HashMap<Integer, String>();

		try (final var is = EmojiFontSource.class.getResourceAsStream("emoji.zip")) {
			if (is != null) {
				try (final var zis = new ZipInputStream(new BufferedInputStream(is))) {
					ZipEntry entry;
					while ((entry = zis.getNextEntry()) != null) {
						if ("INDEX".equals(entry.getName())) {
							// Read INDEX file containing emoji code mappings
							final var in = new BufferedReader(new InputStreamReader(zis, StandardCharsets.ISO_8859_1));
							int gid = 0;
							for (String code = in.readLine(); code != null; code = in.readLine()) {
								++gid;
								ctog.put(code, gid);
								gtoc.put(gid, code);
							}
							// Do not close 'in' as it would close the underlying ZipInputStream
							break;
						}
					}
				}
			} else {
				// System.err.println("Warning: emoji.zip not found. Emoji support will be
				// disabled.");
			}
		} catch (final Exception e) {
			e.printStackTrace();
			// Continue with empty maps
		}

		codeToGid = Collections.unmodifiableMap(ctog);
		gidToCode = Collections.unmodifiableMap(gtoc);
	}

	/** Singleton instance for left-to-right text direction. */
	public static final EmojiFontSource INSTANCES_LTR = new EmojiFontSource(Direction.LTR);

	/** Singleton instance for top-to-bottom text direction. */
	public static final EmojiFontSource INSTANCES_TB = new EmojiFontSource(Direction.TB);

	/** Text direction for this font source. */
	private final Direction direction;

	/**
	 * Creates a new emoji font source with the specified text direction.
	 *
	 * @param direction the text direction (LTR or TB)
	 */
	private EmojiFontSource(final Direction direction) {
		this.direction = direction;
	}

	/**
	 * Returns the text direction for this font source.
	 *
	 * @return the text direction
	 */
	@Override
	public Direction getDirection() {
		return this.direction;
	}

	/**
	 * Returns the font name.
	 *
	 * @return always "emoji"
	 */
	@Override
	public String getFontName() {
		return "emoji";
	}

	/**
	 * Returns the bounding box for emoji glyphs.
	 *
	 * @return the bounding box (1000 units square)
	 */
	@Override
	public BBox getBBox() {
		return BBOX;
	}

	/**
	 * Returns the ascent height.
	 *
	 * @return the default ascent value
	 */
	@Override
	public short getAscent() {
		return DEFAULT_ASCENT;
	}

	/**
	 * Returns the descent depth.
	 *
	 * @return the default descent value
	 */
	@Override
	public short getDescent() {
		return DEFAULT_DESCENT;
	}

	/**
	 * Returns the capital letter height.
	 *
	 * @return the default cap height value
	 */
	@Override
	public short getCapHeight() {
		return DEFAULT_CAP_HEIGHT;
	}

	/**
	 * Returns the x-height (height of lowercase 'x').
	 *
	 * @return the default x-height value
	 */
	@Override
	public short getXHeight() {
		return DEFAULT_X_HEIGHT;
	}

	/**
	 * Returns the advance width for space character.
	 *
	 * @return half of the units per em
	 */
	@Override
	public short getSpaceAdvance() {
		return FontSource.DEFAULT_UNITS_PER_EM / 2;
	}

	/**
	 * Returns the horizontal stem width.
	 *
	 * @return always 0 (not applicable for emoji)
	 */
	@Override
	public short getStemH() {
		return 0;
	}

	/**
	 * Returns the vertical stem width.
	 *
	 * @return always 0 (not applicable for emoji)
	 */
	@Override
	public short getStemV() {
		return 0;
	}

	/**
	 * Checks if this font can display the specified character.
	 *
	 * @param c the Unicode code point to check
	 * @return true if an emoji exists for this code point
	 */
	@Override
	public boolean canDisplay(final int c) {
		return codeToGid.containsKey(Integer.toHexString(c));
	}

	/**
	 * Returns the Panose classification for this font.
	 *
	 * @return always null (not applicable for emoji)
	 */
	public Panose getPanose() {
		return null;
	}

	/** Lazily opened ZipFile for streaming individual emoji SVG entries. */
	private static java.util.zip.ZipFile emojiZip;

	/**
	 * Returns an input stream for a named entry inside the bundled emoji.zip.
	 *
	 * <p>
	 * On the first call the zip archive is extracted to a temporary file so that
	 * random-access entry reads are possible. The temporary file is deleted when the
	 * JVM exits. Subsequent calls reuse the same open {@link java.util.zip.ZipFile}.
	 *
	 * @param name the entry name to look up (e.g. {@code "emoji_u1f600.svg"})
	 * @return an input stream for the entry, or {@code null} if the entry does not
	 *         exist
	 * @throws java.io.IOException if the zip archive cannot be extracted or opened
	 */
	public static synchronized java.io.InputStream getEmojiStream(final String name) throws java.io.IOException {
		if (emojiZip == null) {
			final java.io.File tempFile = java.io.File.createTempFile("emoji", ".zip");
			tempFile.deleteOnExit();
			try (final var is = EmojiFontSource.class.getResourceAsStream("emoji.zip")) {
				if (is == null) {
					throw new java.io.FileNotFoundException("emoji.zip not found in classpath");
				}
				java.nio.file.Files.copy(is, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			emojiZip = new java.util.zip.ZipFile(tempFile);
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					emojiZip.close();
				} catch (final Exception e) {
					// ignore
				}
			}));
		}
		final ZipEntry entry = emojiZip.getEntry(name);
		return entry != null ? emojiZip.getInputStream(entry) : null;
	}

	/**
	 * Creates a new font instance from this font source.
	 *
	 * @return a new EmojiFont instance
	 */
	@Override
	public Font createFont() {
		return new EmojiFont(this);
	}
}
