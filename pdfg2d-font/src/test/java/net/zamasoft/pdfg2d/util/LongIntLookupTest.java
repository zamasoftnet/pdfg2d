package net.zamasoft.pdfg2d.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * {@link LongIntLookup}のテストです(2026-08-01、95点計画増分1)。
 */
public class LongIntLookupTest {

	@Test
	public void testEmpty() {
		final LongIntLookup lookup = LongIntLookup.fromUnsorted(new long[0], new int[0], 0);
		assertEquals(0, lookup.size());
		assertEquals(-1, lookup.getOrDefault(42, -1));
	}

	@Test
	public void testSingle() {
		final LongIntLookup lookup = LongIntLookup.fromUnsorted(new long[] { 7 }, new int[] { 99 }, 1);
		assertEquals(99, lookup.getOrDefault(7, -1));
		assertEquals(-1, lookup.getOrDefault(6, -1));
		assertEquals(-1, lookup.getOrDefault(8, -1));
	}

	@Test
	public void testOversizedBackingArrays() {
		// 有効長より大きい配列を渡した場合は切り詰める
		final LongIntLookup lookup = LongIntLookup.fromUnsorted(new long[] { 3, 1, 2, 0, 0, 0 },
				new int[] { 30, 10, 20, 0, 0, 0 }, 3);
		assertEquals(3, lookup.size());
		assertEquals(10, lookup.getOrDefault(1, -1));
		assertEquals(20, lookup.getOrDefault(2, -1));
		assertEquals(30, lookup.getOrDefault(3, -1));
		assertEquals(-1, lookup.getOrDefault(0, -1));
	}

	@Test
	public void testRandomAgainstReference() {
		final Random random = new Random(20260801L);
		for (int trial = 0; trial < 20; ++trial) {
			final int n = random.nextInt(200);
			final java.util.TreeMap<Long, Integer> reference = new java.util.TreeMap<>();
			while (reference.size() < n) {
				reference.put(random.nextLong(), random.nextInt());
			}
			final long[] keys = new long[n];
			final int[] values = new int[n];
			// 挿入順(=ランダム順)で構築
			final java.util.List<Long> shuffled = new java.util.ArrayList<>(reference.keySet());
			java.util.Collections.shuffle(shuffled, random);
			for (int i = 0; i < n; ++i) {
				keys[i] = shuffled.get(i);
				values[i] = reference.get(shuffled.get(i));
			}
			final LongIntLookup lookup = LongIntLookup.fromUnsorted(keys, values, n);
			assertEquals(n, lookup.size());
			for (final java.util.Map.Entry<Long, Integer> e : reference.entrySet()) {
				assertEquals(e.getValue().intValue(), lookup.getOrDefault(e.getKey(), Integer.MIN_VALUE));
			}
			for (int i = 0; i < 100; ++i) {
				final long probe = random.nextLong();
				assertEquals(reference.getOrDefault(probe, -7), lookup.getOrDefault(probe, -7));
			}
			// keyAt/valueAtはキー昇順
			long prev = Long.MIN_VALUE;
			for (int i = 0; i < lookup.size(); ++i) {
				assertEquals(true, lookup.keyAt(i) > prev);
				assertEquals(reference.get(lookup.keyAt(i)).intValue(), lookup.valueAt(i));
				prev = lookup.keyAt(i);
			}
		}
	}
}
