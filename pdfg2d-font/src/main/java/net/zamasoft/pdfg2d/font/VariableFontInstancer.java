package net.zamasoft.pdfg2d.font;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可変フォント(OpenType Font Variations)の静的インスタンス化です
 * (2026-08-20新設)。
 *
 * <p>
 * 指定の軸座標(例: {@code wght=700})で fvar/avar/gvar を評価し、
 * glyf のアウトライン座標と hmtx の送り幅を書き換えた<b>静的な
 * TrueType フォント</b>を生成します(WeasyPrint が fonttools の
 * instancer で行っているのと同じ「pinned instance」方式)。可変系の
 * テーブル(fvar/gvar/avar/HVAR/MVAR/STAT/cvar)は出力から除きます。
 * </p>
 *
 * <p>
 * <b>対応範囲</b>: TrueType アウトライン(glyf)のみ。CFF2 は対象外
 * (isVariable が false を返す)。送り幅は gvar のファントムポイント
 * (各グリフ末尾の4点)から更新し、HVAR は評価せず削除する——Google
 * Fonts 配信の TrueType 可変フォントは gvar にファントムのデルタを
 * 持つのが通例で、実測(Mulish/Segoe UI VF)でも幅が一致する。
 * </p>
 */
public final class VariableFontInstancer {

	private VariableFontInstancer() {
		// utility
	}

	/** sfnt(TTF/OTF)ファイルが fvar と glyf を持つ可変フォントか。 */
	public static boolean isVariable(final File sfnt) {
		try {
			final ByteBuffer bb = ByteBuffer.wrap(Files.readAllBytes(sfnt.toPath())).order(ByteOrder.BIG_ENDIAN);
			final Map<String, int[]> tables = readDirectory(bb, 0);
			return tables.containsKey("fvar") && tables.containsKey("glyf");
		} catch (final Exception e) {
			return false;
		}
	}

	/** fvar の軸タグ列を返します(可変でなければ空)。 */
	public static List<String> axisTags(final File sfnt) throws IOException {
		final ByteBuffer bb = ByteBuffer.wrap(Files.readAllBytes(sfnt.toPath())).order(ByteOrder.BIG_ENDIAN);
		final Map<String, int[]> tables = readDirectory(bb, 0);
		final List<String> tags = new ArrayList<>();
		final int[] fvar = tables.get("fvar");
		if (fvar == null) {
			return tags;
		}
		final Axis[] axes = readFvar(bb, fvar[0]);
		for (final Axis a : axes) {
			tags.add(a.tag);
		}
		return tags;
	}

	/**
	 * 指定軸座標の静的インスタンスを生成して一時ファイルへ書き出します。
	 *
	 * @param sfnt 解凍済みの sfnt ファイル(TTC 不可、単一フォント)
	 * @param userAxes 軸タグ→ユーザー座標(例: {@code {"wght": 700}})。
	 *        指定の無い軸は既定値で固定される
	 * @return 生成された静的フォントの一時ファイル
	 */
	public static File instantiate(final File sfnt, final Map<String, Double> userAxes) throws IOException {
		final byte[] src = Files.readAllBytes(sfnt.toPath());
		final ByteBuffer bb = ByteBuffer.wrap(src).order(ByteOrder.BIG_ENDIAN);
		final Map<String, int[]> tables = readDirectory(bb, 0);
		final int[] fvarLoc = tables.get("fvar");
		final int[] gvarLoc = tables.get("gvar");
		final int[] glyfLoc = tables.get("glyf");
		final int[] locaLoc = tables.get("loca");
		final int[] headLoc = tables.get("head");
		final int[] maxpLoc = tables.get("maxp");
		final int[] hheaLoc = tables.get("hhea");
		final int[] hmtxLoc = tables.get("hmtx");
		if (fvarLoc == null || glyfLoc == null || locaLoc == null || headLoc == null || maxpLoc == null
				|| hheaLoc == null || hmtxLoc == null) {
			throw new IOException("not an instantiable variable font (missing tables)");
		}

		// 軸の正規化座標
		final Axis[] axes = readFvar(bb, fvarLoc[0]);
		final double[] coords = new double[axes.length];
		for (int i = 0; i < axes.length; ++i) {
			final Axis a = axes[i];
			final Double user = userAxes.get(a.tag);
			final double v = user != null ? Math.max(a.min, Math.min(a.max, user)) : a.def;
			double n;
			if (v < a.def) {
				n = a.def == a.min ? 0 : (v - a.def) / (a.def - a.min);
			} else if (v > a.def) {
				n = a.def == a.max ? 0 : (v - a.def) / (a.max - a.def);
			} else {
				n = 0;
			}
			coords[i] = n;
		}
		final int[] avarLoc = tables.get("avar");
		if (avarLoc != null) {
			applyAvar(bb, avarLoc[0], coords);
		}

		// glyf/loca/hmtx の読み出し
		final int numGlyphs = bb.getShort(maxpLoc[0] + 4) & 0xFFFF;
		final boolean longLoca = bb.getShort(headLoc[0] + 50) != 0;
		final int[] loca = new int[numGlyphs + 1];
		for (int i = 0; i <= numGlyphs; ++i) {
			loca[i] = longLoca ? bb.getInt(locaLoc[0] + 4 * i) : (bb.getShort(locaLoc[0] + 2 * i) & 0xFFFF) * 2;
		}
		final int numHMetrics = bb.getShort(hheaLoc[0] + 34) & 0xFFFF;
		final int[] advances = new int[numGlyphs];
		final int[] lsbs = new int[numGlyphs];
		for (int i = 0; i < numGlyphs; ++i) {
			if (i < numHMetrics) {
				advances[i] = bb.getShort(hmtxLoc[0] + 4 * i) & 0xFFFF;
				lsbs[i] = bb.getShort(hmtxLoc[0] + 4 * i + 2);
			} else {
				advances[i] = advances[numHMetrics - 1];
				lsbs[i] = bb.getShort(hmtxLoc[0] + 4 * numHMetrics + 2 * (i - numHMetrics));
			}
		}

		// gvar を各グリフへ適用
		final byte[][] newGlyphs = new byte[numGlyphs][];
		if (gvarLoc != null) {
			final Gvar gvar = readGvarHeader(bb, gvarLoc[0]);
			for (int gid = 0; gid < numGlyphs; ++gid) {
				final int glyphOff = glyfLoc[0] + loca[gid];
				final int glyphLen = loca[gid + 1] - loca[gid];
				final Glyph glyph = glyphLen == 0 ? Glyph.empty() : parseGlyph(bb, glyphOff, glyphLen);
				final double[][] deltas = computeDeltas(bb, gvar, gvarLoc[0], gid, coords, glyph);
				if (deltas == null) {
					newGlyphs[gid] = glyphLen == 0 ? new byte[0] : slice(src, glyphOff, glyphLen);
				} else {
					applyDeltas(glyph, deltas);
					// ファントム: [n]=LSB原点X, [n+1]=送りX(横書き)
					final int n = glyph.pointCount();
					final double advDelta = deltas[0][n + 1] - deltas[0][n];
					advances[gid] = Math.max(0, (int) Math.round(advances[gid] + advDelta));
					if (glyph.empty) {
						newGlyphs[gid] = new byte[0];
					} else {
						newGlyphs[gid] = serializeGlyph(glyph);
						if (!glyph.composite) {
							lsbs[gid] = glyph.xMin;
						}
					}
				}
			}
		} else {
			for (int gid = 0; gid < numGlyphs; ++gid) {
				newGlyphs[gid] = slice(src, glyfLoc[0] + loca[gid], loca[gid + 1] - loca[gid]);
			}
		}

		// 新しい glyf/loca/hmtx を構築
		int glyfSize = 0;
		for (final byte[] g : newGlyphs) {
			glyfSize += (g.length + 3) & ~3;
		}
		final byte[] newGlyf = new byte[glyfSize];
		final int[] newLoca = new int[numGlyphs + 1];
		{
			int off = 0;
			for (int i = 0; i < numGlyphs; ++i) {
				newLoca[i] = off;
				System.arraycopy(newGlyphs[i], 0, newGlyf, off, newGlyphs[i].length);
				off += (newGlyphs[i].length + 3) & ~3;
			}
			newLoca[numGlyphs] = off;
		}
		final byte[] newLocaBytes = new byte[(numGlyphs + 1) * 4];
		{
			final ByteBuffer lb = ByteBuffer.wrap(newLocaBytes).order(ByteOrder.BIG_ENDIAN);
			for (int i = 0; i <= numGlyphs; ++i) {
				lb.putInt(newLoca[i]);
			}
		}
		final byte[] newHmtx = new byte[4 * numGlyphs];
		{
			final ByteBuffer hb = ByteBuffer.wrap(newHmtx).order(ByteOrder.BIG_ENDIAN);
			for (int i = 0; i < numGlyphs; ++i) {
				hb.putShort((short) advances[i]);
				hb.putShort((short) lsbs[i]);
			}
		}

		// テーブルの差し替え・削除をして新しい sfnt を組む
		final Map<String, byte[]> out = new LinkedHashMap<>();
		for (final Map.Entry<String, int[]> e : tables.entrySet()) {
			final String tag = e.getKey();
			switch (tag) {
			case "fvar":
			case "gvar":
			case "avar":
			case "cvar":
			case "HVAR":
			case "VVAR":
			case "MVAR":
			case "STAT":
				continue; // 可変系は落とす
			case "glyf":
				out.put(tag, newGlyf);
				continue;
			case "loca":
				out.put(tag, newLocaBytes);
				continue;
			case "hmtx":
				out.put(tag, newHmtx);
				continue;
			case "head": {
				final byte[] head = slice(src, e.getValue()[0], e.getValue()[1]);
				final ByteBuffer hb = ByteBuffer.wrap(head).order(ByteOrder.BIG_ENDIAN);
				hb.putShort(50, (short) 1); // indexToLocFormat = long
				hb.putInt(8, 0); // checkSumAdjustment はリーダが無視するので0
				out.put(tag, head);
				continue;
			}
			case "hhea": {
				final byte[] hhea = slice(src, e.getValue()[0], e.getValue()[1]);
				final ByteBuffer hb = ByteBuffer.wrap(hhea).order(ByteOrder.BIG_ENDIAN);
				hb.putShort(34, (short) numGlyphs); // numberOfHMetrics = 全グリフ
				out.put(tag, hhea);
				continue;
			}
			case "OS/2": {
				// usWeightClass/usWidthClassをインスタンス座標に合わせる
				// (フォント選択のウェイトマッチはここを見る)
				final byte[] os2 = slice(src, e.getValue()[0], e.getValue()[1]);
				final ByteBuffer ob = ByteBuffer.wrap(os2).order(ByteOrder.BIG_ENDIAN);
				final Double wght = userAxes.get("wght");
				if (wght != null) {
					ob.putShort(4, (short) Math.max(1, Math.min(1000, Math.round(wght))));
				}
				out.put(tag, os2);
				continue;
			}
			default:
				out.put(tag, slice(src, e.getValue()[0], e.getValue()[1]));
			}
		}

		final File dst = File.createTempFile("copper-vf-instance", ".ttf");
		dst.deleteOnExit();
		Files.write(dst.toPath(), buildSfnt(out));
		return dst;
	}

	// ------------------------------------------------------------------
	// sfnt 基盤

	private static Map<String, int[]> readDirectory(final ByteBuffer bb, final int base) throws IOException {
		final int tag = bb.getInt(base);
		if (tag != 0x00010000 && tag != 0x4F54544F && tag != 0x74727565) { // 1.0 / OTTO / true
			throw new IOException("not a sfnt: 0x" + Integer.toHexString(tag));
		}
		final int num = bb.getShort(base + 4) & 0xFFFF;
		final Map<String, int[]> tables = new LinkedHashMap<>();
		for (int i = 0; i < num; ++i) {
			final int rec = base + 12 + 16 * i;
			final byte[] t = new byte[4];
			bb.get(rec, t);
			tables.put(new String(t, java.nio.charset.StandardCharsets.ISO_8859_1),
					new int[] { bb.getInt(rec + 8), bb.getInt(rec + 12) });
		}
		return tables;
	}

	private static byte[] slice(final byte[] src, final int off, final int len) {
		final byte[] b = new byte[len];
		System.arraycopy(src, off, b, 0, len);
		return b;
	}

	private static byte[] buildSfnt(final Map<String, byte[]> tables) {
		final int num = tables.size();
		int size = 12 + 16 * num;
		for (final byte[] b : tables.values()) {
			size += (b.length + 3) & ~3;
		}
		final byte[] out = new byte[size];
		final ByteBuffer bb = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN);
		bb.putInt(0x00010000);
		bb.putShort((short) num);
		int entrySelector = 0;
		while ((2 << entrySelector) <= num) {
			++entrySelector;
		}
		final int searchRange = (1 << entrySelector) * 16;
		bb.putShort((short) searchRange);
		bb.putShort((short) entrySelector);
		bb.putShort((short) (num * 16 - searchRange));
		int off = 12 + 16 * num;
		int i = 0;
		for (final Map.Entry<String, byte[]> e : tables.entrySet()) {
			final int rec = 12 + 16 * i++;
			final byte[] tag = e.getKey().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
			bb.put(rec, tag, 0, 4);
			bb.putInt(rec + 4, checksum(e.getValue()));
			bb.putInt(rec + 8, off);
			bb.putInt(rec + 12, e.getValue().length);
			System.arraycopy(e.getValue(), 0, out, off, e.getValue().length);
			off += (e.getValue().length + 3) & ~3;
		}
		return out;
	}

	private static int checksum(final byte[] b) {
		int sum = 0;
		for (int i = 0; i < b.length; i += 4) {
			int v = 0;
			for (int j = 0; j < 4; ++j) {
				v = (v << 8) | (i + j < b.length ? b[i + j] & 0xFF : 0);
			}
			sum += v;
		}
		return sum;
	}

	// ------------------------------------------------------------------
	// fvar / avar

	private record Axis(String tag, double min, double def, double max) {
	}

	private static Axis[] readFvar(final ByteBuffer bb, final int off) {
		final int axesOffset = bb.getShort(off + 4) & 0xFFFF;
		final int axisCount = bb.getShort(off + 8) & 0xFFFF;
		final int axisSize = bb.getShort(off + 10) & 0xFFFF;
		final Axis[] axes = new Axis[axisCount];
		for (int i = 0; i < axisCount; ++i) {
			final int a = off + axesOffset + i * axisSize;
			final byte[] t = new byte[4];
			bb.get(a, t);
			axes[i] = new Axis(new String(t, java.nio.charset.StandardCharsets.ISO_8859_1), f16(bb.getInt(a + 4)),
					f16(bb.getInt(a + 8)), f16(bb.getInt(a + 12)));
		}
		return axes;
	}

	private static double f16(final int fixed) {
		return fixed / 65536.0;
	}

	private static void applyAvar(final ByteBuffer bb, final int off, final double[] coords) {
		final int axisCount = bb.getShort(off + 4) & 0xFFFF;
		int p = off + 8;
		for (int i = 0; i < axisCount && i < coords.length; ++i) {
			final int pairs = bb.getShort(p) & 0xFFFF;
			p += 2;
			double from = coords[i], mapped = from;
			double prevFrom = -1, prevTo = -1;
			boolean done = false;
			for (int j = 0; j < pairs; ++j) {
				final double f = bb.getShort(p) / 16384.0;
				final double t = bb.getShort(p + 2) / 16384.0;
				p += 4;
				if (!done) {
					if (from == f) {
						mapped = t;
						done = true;
					} else if (from < f) {
						if (j == 0) {
							mapped = t;
						} else {
							mapped = prevTo + (t - prevTo) * (from - prevFrom) / (f - prevFrom);
						}
						done = true;
					}
					prevFrom = f;
					prevTo = t;
				}
			}
			if (!done && pairs > 0) {
				mapped = prevTo;
			}
			coords[i] = mapped;
		}
	}

	// ------------------------------------------------------------------
	// glyf の座標モデル

	private static final class Glyph {
		boolean empty;
		boolean composite;
		int numberOfContours;
		int xMin, yMin, xMax, yMax;
		int[] endPts; // simple
		byte[] instructions; // simple
		byte[] flags; // simple: 展開済み(点ごと)
		int[] xs, ys; // simple: 絶対座標(点ごと)
		// composite
		byte[] compositeData; // ヘッダ以降の生データ
		int[] componentOffsets; // compositeData内の各成分のargs位置
		boolean[] componentArgsAreWords;
		boolean[] componentArgsAreXY;
		int componentCount;

		static Glyph empty() {
			final Glyph g = new Glyph();
			g.empty = true;
			return g;
		}

		int pointCount() {
			if (this.empty) {
				return 0;
			}
			if (this.composite) {
				return this.componentCount;
			}
			return this.xs.length;
		}
	}

	private static Glyph parseGlyph(final ByteBuffer bb, final int off, final int len) throws IOException {
		final Glyph g = new Glyph();
		g.numberOfContours = bb.getShort(off);
		g.xMin = bb.getShort(off + 2);
		g.yMin = bb.getShort(off + 4);
		g.xMax = bb.getShort(off + 6);
		g.yMax = bb.getShort(off + 8);
		int p = off + 10;
		if (g.numberOfContours >= 0) {
			// simple
			g.endPts = new int[g.numberOfContours];
			for (int i = 0; i < g.numberOfContours; ++i) {
				g.endPts[i] = bb.getShort(p) & 0xFFFF;
				p += 2;
			}
			final int numPts = g.numberOfContours == 0 ? 0 : g.endPts[g.numberOfContours - 1] + 1;
			final int insLen = bb.getShort(p) & 0xFFFF;
			p += 2;
			g.instructions = new byte[insLen];
			bb.get(p, g.instructions);
			p += insLen;
			g.flags = new byte[numPts];
			for (int i = 0; i < numPts;) {
				final byte f = bb.get(p++);
				g.flags[i++] = f;
				if ((f & 0x08) != 0) { // REPEAT
					int r = bb.get(p++) & 0xFF;
					while (r-- > 0 && i < numPts) {
						g.flags[i++] = f;
					}
				}
			}
			g.xs = new int[numPts];
			int x = 0;
			for (int i = 0; i < numPts; ++i) {
				final byte f = g.flags[i];
				if ((f & 0x02) != 0) { // X_SHORT
					final int d = bb.get(p++) & 0xFF;
					x += (f & 0x10) != 0 ? d : -d;
				} else if ((f & 0x10) == 0) {
					x += bb.getShort(p);
					p += 2;
				}
				g.xs[i] = x;
			}
			g.ys = new int[numPts];
			int y = 0;
			for (int i = 0; i < numPts; ++i) {
				final byte f = g.flags[i];
				if ((f & 0x04) != 0) { // Y_SHORT
					final int d = bb.get(p++) & 0xFF;
					y += (f & 0x20) != 0 ? d : -d;
				} else if ((f & 0x20) == 0) {
					y += bb.getShort(p);
					p += 2;
				}
				g.ys[i] = y;
			}
		} else {
			// composite
			g.composite = true;
			g.compositeData = new byte[len - 10];
			bb.get(p, g.compositeData);
			final List<Integer> offs = new ArrayList<>();
			final List<Boolean> words = new ArrayList<>();
			final List<Boolean> xy = new ArrayList<>();
			int c = 0;
			while (true) {
				final int flags = ((g.compositeData[c] & 0xFF) << 8) | (g.compositeData[c + 1] & 0xFF);
				offs.add(c + 4);
				words.add((flags & 0x0001) != 0); // ARG_1_AND_2_ARE_WORDS
				xy.add((flags & 0x0002) != 0); // ARGS_ARE_XY_VALUES
				c += 4 + ((flags & 0x0001) != 0 ? 4 : 2);
				if ((flags & 0x0008) != 0) { // WE_HAVE_A_SCALE
					c += 2;
				} else if ((flags & 0x0040) != 0) { // X_AND_Y_SCALE
					c += 4;
				} else if ((flags & 0x0080) != 0) { // 2x2
					c += 8;
				}
				if ((flags & 0x0020) == 0) { // MORE_COMPONENTS
					break;
				}
			}
			g.componentCount = offs.size();
			g.componentOffsets = offs.stream().mapToInt(Integer::intValue).toArray();
			g.componentArgsAreWords = new boolean[words.size()];
			g.componentArgsAreXY = new boolean[xy.size()];
			for (int i = 0; i < words.size(); ++i) {
				g.componentArgsAreWords[i] = words.get(i);
				g.componentArgsAreXY[i] = xy.get(i);
			}
		}
		return g;
	}

	private static byte[] serializeGlyph(final Glyph g) {
		if (g.composite) {
			final byte[] out = new byte[10 + g.compositeData.length];
			final ByteBuffer bb = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN);
			bb.putShort((short) g.numberOfContours);
			bb.putShort((short) g.xMin);
			bb.putShort((short) g.yMin);
			bb.putShort((short) g.xMax);
			bb.putShort((short) g.yMax);
			bb.put(10, g.compositeData);
			return out;
		}
		final int numPts = g.xs.length;
		// bboxを再計算
		int xMin = Integer.MAX_VALUE, yMin = Integer.MAX_VALUE, xMax = Integer.MIN_VALUE, yMax = Integer.MIN_VALUE;
		for (int i = 0; i < numPts; ++i) {
			xMin = Math.min(xMin, g.xs[i]);
			xMax = Math.max(xMax, g.xs[i]);
			yMin = Math.min(yMin, g.ys[i]);
			yMax = Math.max(yMax, g.ys[i]);
		}
		if (numPts == 0) {
			xMin = yMin = xMax = yMax = 0;
		}
		g.xMin = xMin;
		g.yMin = yMin;
		g.xMax = xMax;
		g.yMax = yMax;
		// フラグはrepeat圧縮なしで素直に出す(合法)
		final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		final java.io.DataOutputStream d = new java.io.DataOutputStream(out);
		try {
			d.writeShort(g.numberOfContours);
			d.writeShort(xMin);
			d.writeShort(yMin);
			d.writeShort(xMax);
			d.writeShort(yMax);
			for (final int e : g.endPts) {
				d.writeShort(e);
			}
			d.writeShort(g.instructions.length);
			d.write(g.instructions);
			final byte[] newFlags = new byte[numPts];
			for (int i = 0; i < numPts; ++i) {
				int f = g.flags[i] & 0x01; // ON_CURVE のみ引き継ぐ
				final int dx = g.xs[i] - (i == 0 ? 0 : g.xs[i - 1]);
				final int dy = g.ys[i] - (i == 0 ? 0 : g.ys[i - 1]);
				if (dx == 0) {
					f |= 0x10;
				} else if (dx >= -255 && dx <= 255) {
					f |= 0x02 | (dx > 0 ? 0x10 : 0);
				}
				if (dy == 0) {
					f |= 0x20;
				} else if (dy >= -255 && dy <= 255) {
					f |= 0x04 | (dy > 0 ? 0x20 : 0);
				}
				newFlags[i] = (byte) f;
				d.writeByte(f);
			}
			for (int i = 0; i < numPts; ++i) {
				final int dx = g.xs[i] - (i == 0 ? 0 : g.xs[i - 1]);
				final int f = newFlags[i];
				if ((f & 0x02) != 0) {
					d.writeByte(Math.abs(dx));
				} else if ((f & 0x10) == 0) {
					d.writeShort(dx);
				}
			}
			for (int i = 0; i < numPts; ++i) {
				final int dy = g.ys[i] - (i == 0 ? 0 : g.ys[i - 1]);
				final int f = newFlags[i];
				if ((f & 0x04) != 0) {
					d.writeByte(Math.abs(dy));
				} else if ((f & 0x20) == 0) {
					d.writeShort(dy);
				}
			}
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
		return out.toByteArray();
	}

	// ------------------------------------------------------------------
	// gvar

	private record Gvar(int axisCount, int sharedTupleCount, int sharedTuplesOffset, int glyphCount, int flags,
			int glyphVariationDataArrayOffset, int[] dataOffsets) {
	}

	private static Gvar readGvarHeader(final ByteBuffer bb, final int off) {
		final int axisCount = bb.getShort(off + 4) & 0xFFFF;
		final int sharedTupleCount = bb.getShort(off + 6) & 0xFFFF;
		final int sharedTuplesOffset = bb.getInt(off + 8);
		final int glyphCount = bb.getShort(off + 12) & 0xFFFF;
		final int flags = bb.getShort(off + 14) & 0xFFFF;
		final int arrayOffset = bb.getInt(off + 16);
		final int[] dataOffsets = new int[glyphCount + 1];
		final boolean longOffsets = (flags & 1) != 0;
		for (int i = 0; i <= glyphCount; ++i) {
			dataOffsets[i] = longOffsets ? bb.getInt(off + 20 + 4 * i) : (bb.getShort(off + 20 + 2 * i) & 0xFFFF) * 2;
		}
		return new Gvar(axisCount, sharedTupleCount, sharedTuplesOffset, glyphCount, flags, arrayOffset, dataOffsets);
	}

	/**
	 * 指定グリフのデルタ(x,y)を全タプルの合算で返します。適用可能な
	 * タプルが無ければ null。返り値は {@code [2][pointCount+4]}
	 * (末尾4点はファントム)。
	 */
	private static double[][] computeDeltas(final ByteBuffer bb, final Gvar gvar, final int gvarBase, final int gid,
			final double[] coords, final Glyph glyph) throws IOException {
		if (gid >= gvar.glyphCount() || gvar.dataOffsets()[gid] == gvar.dataOffsets()[gid + 1]) {
			return null;
		}
		final int off = gvarBase + gvar.glyphVariationDataArrayOffset() + gvar.dataOffsets()[gid];
		final int tupleCount = bb.getShort(off) & 0xFFFF;
		final int dataOffset = bb.getShort(off + 2) & 0xFFFF;
		final boolean sharedPointNumbers = (tupleCount & 0x8000) != 0;
		final int count = tupleCount & 0x0FFF;
		final int axisCount = gvar.axisCount();
		final int n = glyph.pointCount() + 4;

		int headerP = off + 4;
		int dataP = off + dataOffset;
		int[] sharedPoints = null;
		if (sharedPointNumbers) {
			final int[][] r = readPackedPoints(bb, dataP, n);
			sharedPoints = r[0];
			dataP = r[1][0];
		}

		double[][] sum = null;
		for (int t = 0; t < count; ++t) {
			final int tupleSize = bb.getShort(headerP) & 0xFFFF;
			final int tupleIndex = bb.getShort(headerP + 2) & 0xFFFF;
			headerP += 4;
			final boolean embedded = (tupleIndex & 0x8000) != 0;
			final boolean intermediate = (tupleIndex & 0x4000) != 0;
			final boolean privatePoints = (tupleIndex & 0x2000) != 0;
			final double[] peak = new double[axisCount];
			if (embedded) {
				for (int a = 0; a < axisCount; ++a) {
					peak[a] = bb.getShort(headerP) / 16384.0;
					headerP += 2;
				}
			} else {
				final int shared = tupleIndex & 0x0FFF;
				final int sp = gvarBase + gvar.sharedTuplesOffset() + shared * axisCount * 2;
				for (int a = 0; a < axisCount; ++a) {
					peak[a] = bb.getShort(sp + a * 2) / 16384.0;
				}
			}
			final double[] start = new double[axisCount];
			final double[] end = new double[axisCount];
			if (intermediate) {
				for (int a = 0; a < axisCount; ++a) {
					start[a] = bb.getShort(headerP) / 16384.0;
					headerP += 2;
				}
				for (int a = 0; a < axisCount; ++a) {
					end[a] = bb.getShort(headerP) / 16384.0;
					headerP += 2;
				}
			}

			// スカラー計算
			double scalar = 1;
			for (int a = 0; a < axisCount; ++a) {
				final double p = peak[a];
				final double c = a < coords.length ? coords[a] : 0;
				if (p == 0) {
					continue;
				}
				if (!intermediate) {
					if (c == p) {
						continue;
					}
					if (c == 0 || (c < 0) != (p < 0) || Math.abs(c) > Math.abs(p)) {
						scalar = 0;
						break;
					}
					scalar *= c / p;
				} else {
					final double s = start[a], e2 = end[a];
					if (c < s || c > e2) {
						scalar = 0;
						break;
					}
					if (c == p) {
						continue;
					}
					if (c < p) {
						if (p != s) {
							scalar *= (c - s) / (p - s);
						}
					} else {
						if (p != e2) {
							scalar *= (e2 - c) / (e2 - p);
						}
					}
				}
			}

			int tp = dataP;
			dataP += tupleSize;
			if (scalar == 0) {
				continue;
			}

			int[] points = sharedPoints;
			if (privatePoints) {
				final int[][] r = readPackedPoints(bb, tp, n);
				points = r[0];
				tp = r[1][0];
			}
			final int deltaCount = points == null ? n : points.length;
			final int[] end2 = new int[1];
			final int[] dx = readPackedDeltas(bb, tp, deltaCount, end2);
			tp = end2[0];
			final int[] dy = readPackedDeltas(bb, tp, deltaCount, end2);

			final double[] fx = new double[n];
			final double[] fy = new double[n];
			final boolean[] touched = new boolean[n];
			if (points == null) {
				for (int i = 0; i < n; ++i) {
					fx[i] = dx[i];
					fy[i] = dy[i];
					touched[i] = true;
				}
			} else {
				for (int i = 0; i < points.length; ++i) {
					final int pi = points[i];
					if (pi < n) {
						fx[pi] = dx[i];
						fy[pi] = dy[i];
						touched[pi] = true;
					}
				}
				if (!glyph.composite && !glyph.empty) {
					interpolateUntouched(glyph, fx, fy, touched);
				}
			}
			if (sum == null) {
				sum = new double[2][n];
			}
			for (int i = 0; i < n; ++i) {
				sum[0][i] += fx[i] * scalar;
				sum[1][i] += fy[i] * scalar;
			}
		}
		return sum;
	}

	private static int[][] readPackedPoints(final ByteBuffer bb, int p, final int total) {
		int count = bb.get(p++) & 0xFF;
		if ((count & 0x80) != 0) {
			count = ((count & 0x7F) << 8) | (bb.get(p++) & 0xFF);
		}
		if (count == 0) {
			// 全点
			return new int[][] { null, { p } };
		}
		final int[] points = new int[count];
		int i = 0, last = 0;
		while (i < count) {
			final int control = bb.get(p++) & 0xFF;
			final int runCount = (control & 0x7F) + 1;
			final boolean words = (control & 0x80) != 0;
			for (int r = 0; r < runCount && i < count; ++r) {
				final int d = words ? bb.getShort(p) & 0xFFFF : bb.get(p) & 0xFF;
				p += words ? 2 : 1;
				last += d;
				points[i++] = last;
			}
		}
		return new int[][] { points, { p } };
	}

	private static int[] readPackedDeltas(final ByteBuffer bb, int p, final int count, final int[] endOut) {
		final int[] deltas = new int[count];
		int i = 0;
		while (i < count) {
			final int control = bb.get(p++) & 0xFF;
			final int runCount = (control & 0x3F) + 1;
			if ((control & 0x80) != 0) {
				// zeros
				for (int r = 0; r < runCount && i < count; ++r) {
					deltas[i++] = 0;
				}
			} else if ((control & 0x40) != 0) {
				// words
				for (int r = 0; r < runCount && i < count; ++r) {
					deltas[i++] = bb.getShort(p);
					p += 2;
				}
			} else {
				// bytes
				for (int r = 0; r < runCount && i < count; ++r) {
					deltas[i++] = bb.get(p++);
				}
			}
		}
		endOut[0] = p;
		return deltas;
	}

	/** IUP: 触れられていない点を輪郭ごとに線形補間します(css-fonts/OT仕様)。 */
	private static void interpolateUntouched(final Glyph g, final double[] fx, final double[] fy,
			final boolean[] touched) {
		int start = 0;
		for (final int end : g.endPts) {
			interpolateContour(g.xs, fx, touched, start, end);
			interpolateContour(g.ys, fy, touched, start, end);
			start = end + 1;
		}
	}

	private static void interpolateContour(final int[] orig, final double[] deltas, final boolean[] touched,
			final int start, final int end) {
		// 輪郭内にtouchedが無ければ0のまま、全touchedなら何もしない
		int first = -1;
		for (int i = start; i <= end; ++i) {
			if (touched[i]) {
				first = i;
				break;
			}
		}
		if (first < 0) {
			return;
		}
		int prev = first;
		int i = first;
		do {
			int next = i == end ? start : i + 1;
			if (!touched[next]) {
				// 次のtouchedまで走る
				int j = next;
				while (!touched[j]) {
					j = j == end ? start : j + 1;
				}
				// prev=i(touched), j(touched) の間の各点を補間
				int k = next;
				while (k != j) {
					deltas[k] = interpolate(orig[k], orig[i], orig[j], deltas[i], deltas[j]);
					k = k == end ? start : k + 1;
				}
				i = j;
			} else {
				i = next;
			}
		} while (i != first);
	}

	private static double interpolate(final int v, final int v1, final int v2, final double d1, final double d2) {
		if (v1 == v2) {
			return d1 == d2 ? d1 : 0;
		}
		final int lo = Math.min(v1, v2), hi = Math.max(v1, v2);
		final double dlo = v1 <= v2 ? d1 : d2, dhi = v1 <= v2 ? d2 : d1;
		if (v <= lo) {
			return dlo;
		}
		if (v >= hi) {
			return dhi;
		}
		return dlo + (dhi - dlo) * (v - lo) / (double) (hi - lo);
	}

	private static void applyDeltas(final Glyph g, final double[][] deltas) {
		if (g.empty) {
			return;
		}
		if (g.composite) {
			// 成分オフセット(ARGS_ARE_XY_VALUESのみ)へ適用
			final ByteBuffer bb = ByteBuffer.wrap(g.compositeData).order(ByteOrder.BIG_ENDIAN);
			for (int i = 0; i < g.componentCount; ++i) {
				if (!g.componentArgsAreXY[i]) {
					continue;
				}
				final int o = g.componentOffsets[i];
				final int dx = (int) Math.round(deltas[0][i]);
				final int dy = (int) Math.round(deltas[1][i]);
				if (g.componentArgsAreWords[i]) {
					bb.putShort(o, (short) (bb.getShort(o) + dx));
					bb.putShort(o + 2, (short) (bb.getShort(o + 2) + dy));
				} else {
					bb.put(o, (byte) (bb.get(o) + dx));
					bb.put(o + 1, (byte) (bb.get(o + 1) + dy));
				}
			}
			return;
		}
		for (int i = 0; i < g.xs.length; ++i) {
			g.xs[i] += (int) Math.round(deltas[0][i]);
			g.ys[i] += (int) Math.round(deltas[1][i]);
		}
	}
}
