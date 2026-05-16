package net.zamasoft.pdfg2d.util;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * An {@link IntMap} backed by a {@link Map}.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class MapIntMap implements IntMap {
	protected final Map<Integer, Integer> map = new TreeMap<>();

	@Override
	public void set(final int key, final int value) {
		this.map.put(key, value);
	}

	@Override
	public int get(final int key) {
		return this.map.get(key);
	}

	@Override
	public boolean contains(final int key) {
		return this.map.containsKey(key);
	}

	/**
	 * Returns an {@link IntMapIterator} that iterates over the entries in ascending
	 * key order (backed by a {@link java.util.TreeMap}).
	 *
	 * @return an iterator over the map entries
	 */
	@Override
	public IntMapIterator getIterator() {
		final var i = this.map.entrySet().iterator();
		return new IntMapIterator() {
			private Entry<Integer, Integer> e;

			@Override
			public boolean next() {
				if (!i.hasNext()) {
					return false;
				}
				this.e = i.next();
				return true;
			}

			@Override
			public int key() {
				return this.e.getKey();
			}

			@Override
			public int value() {
				return this.e.getValue();
			}
		};
	}
}
