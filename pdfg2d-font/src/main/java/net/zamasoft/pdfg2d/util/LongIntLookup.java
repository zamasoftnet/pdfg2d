package net.zamasoft.pdfg2d.util;

import java.io.Serializable;

/**
 * Immutable lookup from sorted long keys to int values (2026-08-01, 95-point plan increment 1).
 *
 * <p>
 * Replaces boxed {@code Map<Long, Integer>}. Represents mappings that are read-only after construction,
 * such as UVS cmap and GSUB ligature indexes, with two primitive arrays and binary search,
 * without additional objects. Construction sorts keys and values together in place,
 * without creating temporary objects.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class LongIntLookup implements Serializable {
	private static final long serialVersionUID = 1L;

	private final long[] keys;
	private final int[] values;

	private LongIntLookup(final long[] keys, final int[] values) {
		this.keys = keys;
		this.values = values;
	}

	/**
	 * Constructs from unsorted key and value sequences. Relinquish ownership of the supplied arrays
	 * after the call because sorting uses them directly (no copy).
	 *
	 * @param keys   keys (length at least size)
	 * @param values associated values (same minimum length)
	 * @param size   number of valid elements
	 * @return constructed index
	 */
	public static LongIntLookup fromUnsorted(final long[] keys, final int[] values, final int size) {
		sortPairs(keys, values, 0, size - 1);
		if (keys.length == size) {
			return new LongIntLookup(keys, values);
		}
		final long[] k = new long[size];
		final int[] v = new int[size];
		System.arraycopy(keys, 0, k, 0, size);
		System.arraycopy(values, 0, v, 0, size);
		return new LongIntLookup(k, v);
	}

	/** Quicksort that keeps keys and values together (in place, without temporary arrays). */
	private static void sortPairs(final long[] keys, final int[] values, final int low, final int high) {
		if (low >= high) {
			return;
		}
		final long pivot = keys[low + (high - low) / 2];
		int i = low, j = high;
		while (i <= j) {
			while (keys[i] < pivot) {
				++i;
			}
			while (keys[j] > pivot) {
				--j;
			}
			if (i <= j) {
				final long tk = keys[i];
				keys[i] = keys[j];
				keys[j] = tk;
				final int tv = values[i];
				values[i] = values[j];
				values[j] = tv;
				++i;
				--j;
			}
		}
		sortPairs(keys, values, low, j);
		sortPairs(keys, values, i, high);
	}

	/**
	 * Returns the value for the key.
	 *
	 * @param key     key
	 * @param missing value to return if the key is absent
	 * @return value
	 */
	public int getOrDefault(final long key, final int missing) {
		int low = 0, high = this.keys.length - 1;
		while (low <= high) {
			final int mid = (low + high) >>> 1;
			final long k = this.keys[mid];
			if (k < key) {
				low = mid + 1;
			} else if (k > key) {
				high = mid - 1;
			} else {
				return this.values[mid];
			}
		}
		return missing;
	}

	/** Returns the number of elements. */
	public int size() {
		return this.keys.length;
	}

	/**
	 * Returns the key at index i (ascending key order), for serialization.
	 */
	public long keyAt(final int i) {
		return this.keys[i];
	}

	/**
	 * Returns the value at index i (ascending key order), for serialization.
	 */
	public int valueAt(final int i) {
		return this.values[i];
	}
}
