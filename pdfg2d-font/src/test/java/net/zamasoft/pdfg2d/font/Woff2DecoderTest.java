package net.zamasoft.pdfg2d.font;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import net.zamasoft.pdfg2d.font.table.CmapTable;
import net.zamasoft.pdfg2d.font.table.GlyfTable;
import net.zamasoft.pdfg2d.font.truetype.GlyfDescript;
import net.zamasoft.pdfg2d.font.table.Table;

/**
 * Verifies WOFF2 reconstruction by <b>comparing with another format of the same font</b>.
 *
 * <p>
 * WOFF2 reconstruction rebuilds glyph point coordinates, so <b>mistakes do not throw exceptions;
 * only the output shapes differ</b>. Merely checking that the font can be read is therefore insufficient.
 * Reads both the WOFF2 and uncompressed (TTF/WOFF) versions of the same font and verifies
 * that <b>all outline points of every glyph match</b>.
 * </p>
 *
 * <p>
 * Place comparison fonts in {@code src/test/resources/data/woff2/}
 * (a pair consists of two files with the same name and different extensions).
 * Skip this check if they are absent: users decide whether third-party fonts may be bundled,
 * and <b>other checks must not fail just because these fonts cannot be included</b>.
 * </p>
 */
public class Woff2DecoderTest {

	/**
	 * Directory for comparison fonts. Can be set externally with {@code -Dpdfg2d.woff2PairsDir=...}
	 * to run against local resources <b>without putting third-party fonts in the public repository</b>.
	 * Checks the bundled location if unspecified.
	 */
	private static final File DIR = new File(
			System.getProperty("pdfg2d.woff2PairsDir", "src/test/resources/data/woff2"));

	static boolean hasPairs() {
		return pairs().length > 0;
	}

	/** Returns pairs with WOFF2 and uncompressed versions under the same name. */
	static File[][] pairs() {
		final File[] files = DIR.listFiles();
		if (files == null) {
			return new File[0][];
		}
		final java.util.List<File[]> out = new java.util.ArrayList<>();
		for (final File f : files) {
			if (!f.getName().endsWith(".woff2")) {
				continue;
			}
			final String base = f.getName().substring(0, f.getName().length() - ".woff2".length());
			for (final String ext : new String[] { ".ttf", ".otf", ".woff" }) {
				final File other = new File(DIR, base + ext);
				if (other.isFile()) {
					out.add(new File[] { f, other });
					break;
				}
			}
		}
		return out.toArray(new File[0][]);
	}

	/**
	 * Compares composite glyphs <b>as shapes</b>.
	 *
	 * <p>
	 * <b>WOFF and WOFF2 produced from the same original font do not match byte for byte.</b>
	 * Different generators choose different encodings and rendering hints. On 2026-08-05,
	 * real files showed these differences: word-sized arguments (LindenHill), grid rounding (VictorMono),
	 * overlapping contours (cyrillic), presence of instructions (VictorMono), and reserved bits (LindenHill).
	 * None of these <b>changes point positions</b>.
	 * </p>
	 *
	 * <p>
	 * Therefore, compares only <b>bounding boxes, component counts, each component's glyph ID, and component positions</b>.
	 * Incorrect reconstruction necessarily corrupts the component count or glyph IDs,
	 * so this verifies the intended property: correctly reversing the transform.
	 * </p>
	 */
	private static void assertSameComposite(final byte[] expected, final byte[] actual, final String message) {
		for (int i = 0; i < 10; ++i) {
			assertEquals(expected[i], actual[i], message + "の輪郭数か外枠(" + i + "バイト目)");
		}
		final java.util.List<int[]> ce = components(expected);
		final java.util.List<int[]> ca = components(actual);
		assertEquals(ce.size(), ca.size(), message + "の部品の数");
		for (int i = 0; i < ce.size(); ++i) {
			assertEquals(ce.get(i)[0], ca.get(i)[0], message + "の部品" + i + "の字形番号");
			assertEquals(ce.get(i)[1], ca.get(i)[1], message + "の部品" + i + "の位置1");
			assertEquals(ce.get(i)[2], ca.get(i)[2], message + "の部品" + i + "の位置2");
		}
	}

	/** Returns composite glyph components as a sequence of {glyph ID, argument1, argument2}. */
	private static java.util.List<int[]> components(final byte[] b) {
		final java.util.List<int[]> out = new java.util.ArrayList<>();
		final int[] p = { 10 };
		boolean more;
		do {
			final int flags = u16(b, p[0]);
			final int index = u16(b, p[0] + 2);
			p[0] += 4;
			final boolean xy = (flags & 0x0002) != 0;
			final int a1 = arg(b, p, flags, xy);
			final int a2 = arg(b, p, flags, xy);
			out.add(new int[] { index, a1, a2 });
			if ((flags & 0x0008) != 0) {
				p[0] += 2;
			} else if ((flags & 0x0040) != 0) {
				p[0] += 4;
			} else if ((flags & 0x0080) != 0) {
				p[0] += 8;
			}
			more = (flags & 0x0020) != 0;
		} while (more && p[0] + 4 <= b.length);
		return out;
	}

	/** Reads one component argument and advances (flags determine word/byte size and signedness). */
	private static int arg(final byte[] b, final int[] p, final int flags, final boolean signed) {
		final int v;
		if ((flags & 0x0001) != 0) {
			v = signed ? (short) u16(b, p[0]) : u16(b, p[0]);
			p[0] += 2;
		} else {
			v = signed ? b[p[0]] : (b[p[0]] & 0xff);
			p[0] += 1;
		}
		return v;
	}

	private static int u16(final byte[] b, final int i) {
		return (b[i] & 0xff) << 8 | (b[i + 1] & 0xff);
	}

	/**
	 * Compares contents. <b>Ignores trailing padding</b>: glyph alignment padding varies by file
	 * (4 bytes here, often 2 in the original font) and does not represent a content difference.
	 * However, <b>nonzero excess bytes count as a difference</b>.
	 */
	private static void assertSameContent(final byte[] expected, final byte[] actual, final String message) {
		final int common = Math.min(expected.length, actual.length);
		for (int i = 0; i < common; ++i) {
			assertEquals(expected[i], actual[i], message + "(" + i + "バイト目)");
		}
		final byte[] longer = expected.length > actual.length ? expected : actual;
		for (int i = common; i < longer.length; ++i) {
			assertEquals((byte) 0, longer[i], message + "(はみ出した" + i + "バイト目が詰め物でない)");
		}
	}

	/** Extracts raw glyph bytes (for comparison without decoding). */
	private static byte[] raw(final GlyfTable g, final int gid) throws java.io.IOException {
		final int from = g.loca().getOffset(gid);
		final int len = g.loca().getOffset(gid + 1) - from;
		if (len <= 0) {
			return new byte[0];
		}
		final byte[] b = new byte[len];
		synchronized (g.raf()) {
			g.raf().seek(g.de().offset() + from);
			g.raf().readFully(b);
		}
		return b;
	}

	@Test
	@EnabledIf("hasPairs")
	public void testOutlinesMatchUncompressed() throws Exception {
		for (final File[] pair : pairs()) {
			final String name = pair[0].getName();
			try {
			final FontFile a = new FontFile(pair[0]);
			final FontFile b = new FontFile(pair[1]);
			try (OpenTypeFont fa = a.getFont(); OpenTypeFont fb = b.getFont()) {
				assertNotNull(fa, name);
				assertNotNull(fb, name);

				final CmapTable ca = (CmapTable) fa.getTable(Table.CMAP);
				final CmapTable cb = (CmapTable) fb.getTable(Table.CMAP);
				assertNotNull(ca, name + ": WOFF2側にcmapが無い");
				assertNotNull(cb, name);

				final GlyfTable ga = (GlyfTable) fa.getTable(Table.GLYF);
				if (ga == null) {
					continue; // No glyph table (CFF family). Reading cmap successfully is sufficient.
				}
				final GlyfTable gb = (GlyfTable) fb.getTable(Table.GLYF);
				assertNotNull(gb, name + ": 比較対象にglyfが無い");

				final int n = fa.getNumGlyphs();
				assertEquals(fb.getNumGlyphs(), n, name + ": 字形の数が違う");
				int compared = 0;
				for (int gid = 0; gid < n; ++gid) {
					final byte[] rawA = raw(ga, gid);
					final byte[] rawB = raw(gb, gid);
					if (rawA.length == 0 || rawB.length == 0) {
						assertEquals(rawB.length == 0, rawA.length == 0, name + ": 字形" + gid + "の有無が違う");
						continue;
					}
					final boolean compositeA = ((rawA[0] & 0xff) << 8 | (rawA[1] & 0xff)) > 0x7fff;
					final boolean compositeB = ((rawB[0] & 0xff) << 8 | (rawB[1] & 0xff)) > 0x7fff;
					assertEquals(compositeB, compositeA, name + ": 字形" + gid + "の合成の別が違う");
					if (compositeA) {
						// **Compare composite glyphs as raw bytes.** Their component sequences are
						// not transformed and are copied unchanged,
						// so they should match. Passing through the decoder (GlyfCompositeDescript)
						// can produce **different results for the same bytes depending on the file**,
						// so it cannot serve as the comparison reference
						// (found with CashSans-MediumItalic on 2026-08-05).
						assertSameComposite(rawB, rawA, name + ": 字形" + gid + "の合成");
						++compared;
						continue;
					}
					// Simple glyphs are reconstructed, so compare their outline points.
					final GlyfDescript da = ga.getDescription(gid);
					final GlyfDescript db = gb.getDescription(gid);
					assertNotNull(da, name + ": 字形" + gid);
					assertNotNull(db, name + ": 字形" + gid);
					assertEquals(db.getPointCount(), da.getPointCount(), name + ": 字形" + gid + "の点の数が違う");
					assertEquals(db.getContourCount(), da.getContourCount(), name + ": 字形" + gid + "の輪郭の数が違う");
					for (int i = 0; i < da.getPointCount(); ++i) {
						assertEquals(db.getXCoordinate(i), da.getXCoordinate(i),
								name + ": 字形" + gid + "の点" + i + "のx");
						assertEquals(db.getYCoordinate(i), da.getYCoordinate(i),
								name + ": 字形" + gid + "の点" + i + "のy");
						assertEquals(db.getFlags(i) & GlyfDescript.onCurve, da.getFlags(i) & GlyfDescript.onCurve,
								name + ": 字形" + gid + "の点" + i + "の曲線上の別");
					}
					++compared;
				}
				assertTrue(compared > 0, name + ": 1つも突き合わせていない");
			}
			} catch (final RuntimeException e) {
				// **Always report which font failed.** An exception alone does not identify
				// which of the 170 pairs caused it, wasting time on isolation.
				throw new AssertionError(name + " の突き合わせで失敗: " + e, e);
			}
		}
	}
}
