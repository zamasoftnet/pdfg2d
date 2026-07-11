package net.zamasoft.pdfg2d.pdf.font.cid;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.zamasoft.pdfg2d.font.FontSource;

/**
 * Cross-document cache of generated subset font programs.
 * <p>
 * Building the embedded CFF program is the most expensive step of font
 * embedding (outline decoding and Type 2 charstring encoding). Batch
 * workloads that generate many documents from the same
 * {@code FontSourceManager} typically use the same glyphs over and over, so
 * the finished program bytes are cached keyed by the subset mapping. Entries
 * are held weakly by font source: dropping the font source releases its
 * cached subsets.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
final class FontSubsetCache {

	/** Maximum cached subsets per font source. */
	private static final int MAX_SUBSETS_PER_SOURCE = 8;

	private static final Map<FontSource, Map<Key, byte[]>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

	private FontSubsetCache() {
		// static use only
	}

	/**
	 * Identity of one subset: the width arrays and the CID-to-Unicode mapping
	 * fully determine which glyphs the subset contains and in which order.
	 */
	static final class Key {
		private final short[] w;
		private final short[] w2;
		private final int[] unicodes;
		private final int hash;

		Key(final short[] w, final short[] w2, final int[] unicodes) {
			this.w = w;
			this.w2 = w2;
			this.unicodes = unicodes;
			var h = Arrays.hashCode(w);
			h = h * 31 + Arrays.hashCode(w2);
			h = h * 31 + Arrays.hashCode(unicodes);
			this.hash = h;
		}

		/** A stable content hash, also used to derive the subset tag. */
		int contentHash() {
			return this.hash;
		}

		@Override
		public int hashCode() {
			return this.hash;
		}

		@Override
		public boolean equals(final Object o) {
			return o instanceof Key other && Arrays.equals(this.w, other.w) && Arrays.equals(this.w2, other.w2)
					&& Arrays.equals(this.unicodes, other.unicodes);
		}
	}

	/**
	 * Returns the cached subset program, or {@code null}.
	 *
	 * @param source the font source the subset was built from
	 * @param key    the subset identity
	 * @return the program bytes, or {@code null} on a miss
	 */
	static byte[] get(final FontSource source, final Key key) {
		final var perSource = CACHE.get(source);
		if (perSource == null) {
			return null;
		}
		synchronized (perSource) {
			return perSource.get(key);
		}
	}

	/**
	 * Stores a generated subset program.
	 *
	 * @param source  the font source the subset was built from
	 * @param key     the subset identity
	 * @param program the program bytes
	 */
	static void put(final FontSource source, final Key key, final byte[] program) {
		final var perSource = CACHE.computeIfAbsent(source,
				s -> Collections.synchronizedMap(new LinkedHashMap<Key, byte[]>(16, 0.75f, true) {
					private static final long serialVersionUID = 1L;

					@Override
					protected boolean removeEldestEntry(final Map.Entry<Key, byte[]> eldest) {
						return this.size() > MAX_SUBSETS_PER_SOURCE;
					}
				}));
		perSource.put(key, program);
	}
}
