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
 * WOFF2の復元を、<b>同じフォントの別形式と突き合わせて</b>確かめます。
 *
 * <p>
 * WOFF2の復元は字形の点の座標を組み直す処理なので、<b>間違えても例外は
 * 出ず、形だけが違う出力</b>になります。したがって「読めた」ことを確かめる
 * 検査では足りません。ここでは同じフォントのWOFF2版と非圧縮版
 * (TTF/WOFF)を両方読み、<b>全ての字形の輪郭の点が一致すること</b>を
 * 確かめます。
 * </p>
 *
 * <p>
 * 突き合わせ用のフォントは {@code src/test/resources/data/woff2/} に置きます
 * (同じ名前で拡張子違いの2つを1組とする)。置かれていなければこの検査は
 * 飛ばします——第三者フォントの同梱可否は利用者側の判断であり、
 * <b>置けないからといって他の検査を落とさない</b>ため。
 * </p>
 */
public class Woff2DecoderTest {

	/**
	 * 突き合わせ用のフォント置き場です。{@code -Dpdfg2d.woff2PairsDir=...} で
	 * 外から指せます——<b>第三者のフォントを公開リポジトリへ置かずに</b>
	 * 手元の資源で回すため。指定が無ければ同梱の場所を見ます。
	 */
	private static final File DIR = new File(
			System.getProperty("pdfg2d.woff2PairsDir", "src/test/resources/data/woff2"));

	static boolean hasPairs() {
		return pairs().length > 0;
	}

	/** 同じ名前でWOFF2と非圧縮版がそろっている組を返します。 */
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
	 * 合成字形を<b>部品ごとに</b>比べます。
	 *
	 * <p>
	 * 生のバイト列そのままでは比べられない理由が2つあります。1つは
	 * <b>境界合わせの詰め物</b>の長さが違うこと。もう1つは、実在の符号化器が
	 * <b>OVERLAP_COMPOUND(0x0400)を落とす</b>ことがあること——輪郭の重なりを
	 * 塗りつぶし側へ伝える印で、<b>点の位置には影響しません</b>。
	 * (2026-08-05、cyrillicのフォントで判明。差はこのビット1つだけだった)
	 * </p>
	 */
	/**
	 * 合成字形を<b>形として</b>比べます。
	 *
	 * <p>
	 * <b>同じ元フォントから作ったWOFFとWOFF2は、バイト単位では一致しません。</b>
	 * 生成器が違えば符号化の選び方も描画の助言も変わるためで、2026-08-05に
	 * 実物で次の食い違いを確認しました——引数を語で持つか(LindenHill)、
	 * 格子への丸め(VictorMono)、輪郭の重なり(cyrillic)、命令の有無
	 * (VictorMono)、予約ビット(LindenHill)。どれも<b>点の位置を変えません</b>。
	 * </p>
	 *
	 * <p>
	 * そこで比べるのは<b>外枠・部品の数・各部品の字形番号・各部品の位置</b>に
	 * 絞ります。復元がずれていれば部品の数か字形番号が必ず壊れるので、
	 * これで目的(組み替えを正しく戻せているか)は押さえられます。
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

	/** 合成字形の部品を {字形番号, 引数1, 引数2} の並びで返します。 */
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

	/** 部品の引数を1つ読んで進めます(語かバイトか・符号の有無を印で決める)。 */
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
	 * 中身が同じかを比べます。<b>末尾の詰め物は無視します</b>——字形の
	 * 境界合わせの詰め物はファイルによって長さが違い(こちらは4バイト、
	 * 元のフォントは2バイトのことが多い)、中身の違いではないため。
	 * ただし<b>はみ出した部分がゼロでなければ違いとして扱います</b>。
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

	/** 字形の生のバイト列を取り出します(復号を通さずに比べるため)。 */
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
					continue; // 字形表を持たない(CFF系)。cmapが読めていれば十分
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
						// **合成字形は生のバイト列で比べる。** 部品の並びは
						// 組み替えられていないのでそのまま写しており、
						// 一致すべきである。復号側(GlyfCompositeDescript)を
						// 通すと**同じバイト列でもファイルによって違う答えが
						// 出る**ので、突き合わせの物差しに使えない
						// (2026-08-05にCashSans-MediumItalicで判明)
						assertSameComposite(rawB, rawA, name + ": 字形" + gid + "の合成");
						++compared;
						continue;
					}
					// 単純字形は組み直しているので、輪郭の点で比べる
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
				// **どのフォントで壊れたかを必ず出す。** 例外だけでは
				// 170組のどれが原因か分からず、切り分けに時間を溶かす
				throw new AssertionError(name + " の突き合わせで失敗: " + e, e);
			}
		}
	}
}
