package net.zamasoft.pdfg2d.gc.font;

import java.io.Serializable;
import java.util.Arrays;

/**
 * An immutable, canonical set of OpenType feature settings (tag to value),
 * carried by {@link FontStyle} from CSS {@code font-feature-settings} /
 * {@code font-variant-east-asian} down to shaping.
 *
 * <p>
 * Canonical form: tags are stored as packed big-endian 4-byte ints in
 * ascending order, so content-equal sets are representation-equal — safe as
 * part of cache keys ({@code FontStyle} equality). A tag that is absent means
 * "unspecified" (the engine's default behaviour applies, e.g. {@code liga} and
 * {@code kern} stay enabled); an explicit value of {@code 0} disables the
 * feature. Values greater than 1 are preserved for features that select an
 * alternate by index.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class FontFeatureSet implements Serializable {
	private static final long serialVersionUID = 1L;

	/** The empty set (every feature unspecified). */
	public static final FontFeatureSet EMPTY = new FontFeatureSet(new int[0], new int[0]);

	/** Packed feature tags, ascending. */
	private final int[] tags;

	/** Values parallel to {@link #tags} (0 = explicitly off). */
	private final int[] values;

	private final int hash;

	private FontFeatureSet(final int[] tags, final int[] values) {
		this.tags = tags;
		this.values = values;
		this.hash = Arrays.hashCode(tags) * 31 + Arrays.hashCode(values);
	}

	/**
	 * Packs a 4-character feature tag. Each character must be in U+0020..U+007E
	 * (the OpenType tag alphabet).
	 *
	 * @param tag the feature tag, e.g. {@code "palt"}
	 * @return the packed big-endian tag
	 * @throws IllegalArgumentException if the tag is not 4 printable ASCII
	 *                                  characters
	 */
	public static int packTag(final String tag) {
		if (tag.length() != 4) {
			throw new IllegalArgumentException("feature tag must be 4 characters: " + tag);
		}
		int packed = 0;
		for (int i = 0; i < 4; ++i) {
			final char c = tag.charAt(i);
			if (c < 0x20 || c > 0x7E) {
				throw new IllegalArgumentException("feature tag must be printable ASCII: " + tag);
			}
			packed = (packed << 8) | c;
		}
		return packed;
	}

	/**
	 * Builds a canonical set from parallel tag/value arrays. Later duplicates
	 * of the same tag win (CSS's "last declaration wins").
	 *
	 * @param tags   packed tags (see {@link #packTag})
	 * @param values non-negative values parallel to {@code tags}
	 * @return the canonical set ({@link #EMPTY} when no entries remain)
	 */
	public static FontFeatureSet of(final int[] tags, final int[] values) {
		if (tags.length != values.length) {
			throw new IllegalArgumentException(tags.length + " tags, " + values.length + " values");
		}
		if (tags.length == 0) {
			return EMPTY;
		}
		// Last duplicate wins, then sort by tag for the canonical form.
		final int[][] entries = new int[tags.length][];
		int count = 0;
		outer: for (int i = 0; i < tags.length; ++i) {
			if (values[i] < 0) {
				throw new IllegalArgumentException("negative feature value: " + values[i]);
			}
			for (int j = 0; j < count; ++j) {
				if (entries[j][0] == tags[i]) {
					entries[j][1] = values[i];
					continue outer;
				}
			}
			entries[count++] = new int[] { tags[i], values[i] };
		}
		Arrays.sort(entries, 0, count, (a, b) -> Integer.compare(a[0], b[0]));
		final int[] canonTags = new int[count];
		final int[] canonValues = new int[count];
		for (int i = 0; i < count; ++i) {
			canonTags[i] = entries[i][0];
			canonValues[i] = entries[i][1];
		}
		return new FontFeatureSet(canonTags, canonValues);
	}

	/**
	 * Returns this set with the entries of {@code overrides} applied on top
	 * (used to layer {@code font-feature-settings} over the tags derived from
	 * {@code font-variant-east-asian}).
	 *
	 * @param overrides the overriding set
	 * @return the merged canonical set
	 */
	public FontFeatureSet override(final FontFeatureSet overrides) {
		if (overrides.tags.length == 0) {
			return this;
		}
		if (this.tags.length == 0) {
			return overrides;
		}
		final int[] mergedTags = new int[this.tags.length + overrides.tags.length];
		final int[] mergedValues = new int[mergedTags.length];
		System.arraycopy(this.tags, 0, mergedTags, 0, this.tags.length);
		System.arraycopy(this.values, 0, mergedValues, 0, this.values.length);
		System.arraycopy(overrides.tags, 0, mergedTags, this.tags.length, overrides.tags.length);
		System.arraycopy(overrides.values, 0, mergedValues, this.values.length, overrides.values.length);
		return of(mergedTags, mergedValues);
	}

	/**
	 * Returns the value set for a feature, or {@code -1} when unspecified.
	 *
	 * @param packedTag the packed tag (see {@link #packTag})
	 * @return the non-negative value, or {@code -1} when the tag is absent
	 */
	public int value(final int packedTag) {
		final int i = Arrays.binarySearch(this.tags, packedTag);
		return i >= 0 ? this.values[i] : -1;
	}

	/** @return whether no feature is specified */
	public boolean isEmpty() {
		return this.tags.length == 0;
	}

	/** @return the number of specified features */
	public int size() {
		return this.tags.length;
	}

	/**
	 * @param i the entry index (in canonical tag order)
	 * @return the packed tag of the {@code i}-th entry
	 */
	public int tagAt(final int i) {
		return this.tags[i];
	}

	/**
	 * @param i the entry index (in canonical tag order)
	 * @return the value of the {@code i}-th entry
	 */
	public int valueAt(final int i) {
		return this.values[i];
	}

	@Override
	public boolean equals(final Object o) {
		return o instanceof FontFeatureSet other && Arrays.equals(this.tags, other.tags)
				&& Arrays.equals(this.values, other.values);
	}

	@Override
	public int hashCode() {
		return this.hash;
	}

	@Override
	public String toString() {
		if (this.tags.length == 0) {
			return "FontFeatureSet[]";
		}
		final StringBuilder buff = new StringBuilder("FontFeatureSet[");
		for (int i = 0; i < this.tags.length; ++i) {
			if (i > 0) {
				buff.append(',');
			}
			final int tag = this.tags[i];
			buff.append((char) (tag >>> 24)).append((char) ((tag >>> 16) & 0xFF)).append((char) ((tag >>> 8) & 0xFF))
					.append((char) (tag & 0xFF)).append('=').append(this.values[i]);
		}
		return buff.append(']').toString();
	}
}
