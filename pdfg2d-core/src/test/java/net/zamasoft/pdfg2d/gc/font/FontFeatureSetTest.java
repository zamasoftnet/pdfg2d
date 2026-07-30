package net.zamasoft.pdfg2d.gc.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FontFeatureSet}: tag validation, canonical form
 * (sorted, duplicates last-wins), the unspecified-vs-zero distinction, and
 * the override merge used to layer {@code font-feature-settings} over
 * {@code font-variant-east-asian}.
 */
public class FontFeatureSetTest {

	private static final int PALT = FontFeatureSet.packTag("palt");
	private static final int JP78 = FontFeatureSet.packTag("jp78");
	private static final int RUBY = FontFeatureSet.packTag("ruby");

	@Test
	public void testPackTagValidation() {
		assertEquals(('p' << 24) | ('a' << 16) | ('l' << 8) | 't', PALT);
		assertThrows(IllegalArgumentException.class, () -> FontFeatureSet.packTag("pal"));
		assertThrows(IllegalArgumentException.class, () -> FontFeatureSet.packTag("palto"));
		assertThrows(IllegalArgumentException.class, () -> FontFeatureSet.packTag("palあ"));
	}

	@Test
	public void testCanonicalFormIsOrderIndependent() {
		final var a = FontFeatureSet.of(new int[] { PALT, JP78 }, new int[] { 1, 1 });
		final var b = FontFeatureSet.of(new int[] { JP78, PALT }, new int[] { 1, 1 });
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void testDuplicateLastWins() {
		final var set = FontFeatureSet.of(new int[] { PALT, PALT }, new int[] { 1, 0 });
		assertEquals(1, set.size());
		assertEquals(0, set.value(PALT));
	}

	@Test
	public void testUnspecifiedVersusExplicitZero() {
		final var set = FontFeatureSet.of(new int[] { PALT }, new int[] { 0 });
		assertEquals(0, set.value(PALT), "explicit off");
		assertEquals(-1, set.value(JP78), "unspecified");
		assertNotEquals(FontFeatureSet.EMPTY, set);
	}

	@Test
	public void testEmptyCanonicalization() {
		assertSame(FontFeatureSet.EMPTY, FontFeatureSet.of(new int[0], new int[0]));
		assertTrue(FontFeatureSet.EMPTY.isEmpty());
	}

	@Test
	public void testNegativeValueRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> FontFeatureSet.of(new int[] { PALT }, new int[] { -1 }));
	}

	@Test
	public void testOverride() {
		final var base = FontFeatureSet.of(new int[] { JP78, RUBY }, new int[] { 1, 1 });
		final var merged = base.override(FontFeatureSet.of(new int[] { JP78, PALT }, new int[] { 0, 1 }));
		assertEquals(0, merged.value(JP78), "override wins");
		assertEquals(1, merged.value(RUBY), "base survives");
		assertEquals(1, merged.value(PALT), "override adds");
		assertSame(base, base.override(FontFeatureSet.EMPTY), "empty override is identity");
		assertSame(merged, FontFeatureSet.EMPTY.override(merged), "empty base adopts override");
	}

	@Test
	public void testToString() {
		final var set = FontFeatureSet.of(new int[] { PALT, JP78 }, new int[] { 0, 3 });
		assertEquals("FontFeatureSet[jp78=3,palt=0]", set.toString());
	}
}
