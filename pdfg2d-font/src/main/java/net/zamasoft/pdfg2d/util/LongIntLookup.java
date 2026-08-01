package net.zamasoft.pdfg2d.util;

import java.io.Serializable;

/**
 * ソート済みlongキー→int値の不変引きです(2026-08-01、95点計画増分1)。
 *
 * <p>
 * boxed {@code Map<Long, Integer>}の置換先。UVS cmap・GSUB合字索引の
 * ように「構築後は読むだけ」の写像を、追加オブジェクトなしの2本の
 * プリミティブ配列+二分探索で表す。構築時のソートはキー・値を随伴
 * させたin-place実装で、一時オブジェクトを作らない。
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
	 * 未整列のキー・値の並びから構築します。渡した配列はソートに使われる
	 * ため呼び出し後は所有権を手放すこと(コピーしない)。
	 *
	 * @param keys   キー(長さsize以上)
	 * @param values キーに随伴する値(同)
	 * @param size   有効要素数
	 * @return 構築した索引
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

	/** キーと値を随伴させたクイックソート(in-place、一時配列なし)。 */
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
	 * キーに対応する値を返します。
	 *
	 * @param key     キー
	 * @param missing キーが無い場合に返す値
	 * @return 値
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

	/** 要素数を返します。 */
	public int size() {
		return this.keys.length;
	}

	/**
	 * i番目(キー昇順)のキーを返します(直列化用)。
	 */
	public long keyAt(final int i) {
		return this.keys[i];
	}

	/**
	 * i番目(キー昇順)の値を返します(直列化用)。
	 */
	public int valueAt(final int i) {
		return this.values[i];
	}
}
