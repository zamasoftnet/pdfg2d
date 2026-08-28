package net.zamasoft.pdfg2d.pdf.font;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import java.util.List;
import java.util.Map;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.font.table.GenericCmapFormat;
import net.zamasoft.pdfg2d.font.table.UvsCmapFormat;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.gc.font.Panose;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.cid.identity.OpenTypeCIDIdentityFontSource;

/**
 * フォントディレクトリスキャンの永続索引です(2026-08-01)。
 *
 * <p>
 * fonts.xmlの{@code <font-dir>}は従来、JVM起動のたびに全フォント
 * ファイルを開いてname/OS2/cmap等をパースしていた(O(ファイル数)、
 * 290フォントで約1秒・数千フォントで数十秒)。この索引は選択に必要な
 * メタデータ(名前・別名・weight/italic/PANOSE・メトリクス・圧縮cmap)を
 * (パス, サイズ, mtime, スキャン条件)キーで永続化し、ヒット時は
 * フォントファイルを一切開かずに{@link OpenTypeFontSource}を再構築する。
 * グリフ実データが必要になった時点で初めてファイルが開かれる。
 * </p>
 *
 * <p>
 * 設定ファイル(fonts.xml)の仕様変更はない——{@code DirectSession}が
 * 従来から渡していた{@code fonts.xml.db}のパス(長らく無視されていた
 * 引数)をそのまま使う。索引の読み込み失敗・バージョン不一致・
 * 鮮度不一致は全て「その項目だけ従来どおりパース」へ静かに縮退する。
 * 書き込みは一時ファイル+renameで、壊れた索引が残らないようにする。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class FontIndex {
	private static final Logger LOG = Logger.getLogger(FontIndex.class.getName());

	/** 形式変更時はこの値を上げる(旧版は黙って捨てられ再構築される)。 */
	private static final int MAGIC = 0x43504649; // "CPFI"
	/**
	 * 2: font-dir走査のitalic/weightがOS/2由来になった(2026-08-27、
	 * FontLoader.readTTFのjavadoc参照)。旧索引はnormal/400固定の値を
	 * 再生するため破棄して再構築する。
	 */
	private static final int VERSION = 2;

	private static final int SUBTYPE_EMBEDDED = 0;
	private static final int SUBTYPE_CID_IDENTITY = 1;

	/** 1ファイル分のスキャン結果です。 */
	static final class FileEntry {
		final long size;
		final long lastModified;
		final String scanKey;
		final int numFonts;
		final List<SourceRecord> sources;

		FileEntry(final long size, final long lastModified, final String scanKey, final int numFonts,
				final List<SourceRecord> sources) {
			this.size = size;
			this.lastModified = lastModified;
			this.scanKey = scanKey;
			this.numFonts = numFonts;
			this.sources = sources;
		}
	}

	/** 1 FontSource分の再構築メタデータです。 */
	static final class SourceRecord {
		final int subtype;
		final Direction direction;
		final int ttcIndex;
		final String fontName;
		final String[] aliases;
		final boolean italic;
		final Weight weight;
		final Panose panose;
		final short upm;
		final BBox bbox;
		final short ascent, descent, spaceAdvance;
		final GenericCmapFormat cmap;
		final UvsCmapFormat uvsCmap;

		SourceRecord(final int subtype, final Direction direction, final int ttcIndex, final String fontName,
				final String[] aliases, final boolean italic, final Weight weight, final Panose panose,
				final short upm, final BBox bbox, final short ascent, final short descent, final short spaceAdvance,
				final GenericCmapFormat cmap, final UvsCmapFormat uvsCmap) {
			this.subtype = subtype;
			this.direction = direction;
			this.ttcIndex = ttcIndex;
			this.fontName = fontName;
			this.aliases = aliases;
			this.italic = italic;
			this.weight = weight;
			this.panose = panose;
			this.upm = upm;
			this.bbox = bbox;
			this.ascent = ascent;
			this.descent = descent;
			this.spaceAdvance = spaceAdvance;
			this.cmap = cmap;
			this.uvsCmap = uvsCmap;
		}
	}

	private final File file;

	private final Map<String, FileEntry> pathToEntry = new HashMap<>();

	private boolean dirty = false;

	/**
	 * 索引を読み込みます。ファイルが無い・読めない・形式が違う場合は
	 * 空の索引になる(致命的エラーにはしない)。
	 *
	 * @param file 索引ファイル(通常はfonts.xml.db)
	 */
	public FontIndex(final File file) {
		this.file = file;
		if (file == null || !file.isFile()) {
			return;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
			if (in.readInt() != MAGIC || in.readInt() != VERSION) {
				return;
			}
			final int entryCount = in.readInt();
			for (int i = 0; i < entryCount; ++i) {
				final String path = in.readUTF();
				final long size = in.readLong();
				final long lastModified = in.readLong();
				final String scanKey = in.readUTF();
				final int numFonts = in.readInt();
				// cmap/UVSはファイル内の全ソース(縦横×types)で共有される
				// ため、エントリ毎のプールで一度だけ格納する(索引サイズと
				// ウォーム再構築時のヒープの両方を約1/4にする)
				final GenericCmapFormat[] cmapPool = new GenericCmapFormat[in.readUnsignedShort()];
				for (int j = 0; j < cmapPool.length; ++j) {
					cmapPool[j] = readCmap(in);
				}
				final UvsCmapFormat[] uvsPool = new UvsCmapFormat[in.readUnsignedShort()];
				for (int j = 0; j < uvsPool.length; ++j) {
					uvsPool[j] = readUvs(in);
				}
				final int sourceCount = in.readInt();
				final List<SourceRecord> sources = new ArrayList<>(sourceCount);
				for (int j = 0; j < sourceCount; ++j) {
					sources.add(readSource(in, cmapPool, uvsPool));
				}
				this.pathToEntry.put(path, new FileEntry(size, lastModified, scanKey, numFonts, sources));
			}
		} catch (final Exception e) {
			// 壊れた索引は捨てて再構築(部分的に読めた分も信用しない)
			LOG.log(Level.WARNING, "Ignoring unreadable font index " + file, e);
			this.pathToEntry.clear();
		}
	}

	private static GenericCmapFormat readCmap(final DataInputStream in) throws IOException {
		final int rangeCount = in.readInt();
		final int[] starts = new int[rangeCount], ends = new int[rangeCount], gids = new int[rangeCount];
		final boolean[] constant = new boolean[rangeCount];
		for (int i = 0; i < rangeCount; ++i) {
			starts[i] = in.readInt();
			ends[i] = in.readInt();
			gids[i] = in.readInt();
			constant[i] = in.readBoolean();
		}
		return new GenericCmapFormat(starts, ends, gids, constant);
	}

	private static UvsCmapFormat readUvs(final DataInputStream in) throws IOException {
		final int pairCount = in.readInt();
		final long[] keys = new long[pairCount];
		final int[] gids = new int[pairCount];
		for (int i = 0; i < pairCount; ++i) {
			keys[i] = in.readLong();
			gids[i] = in.readInt();
		}
		final int selCount = in.readInt();
		final int[] selectors = new int[selCount];
		for (int i = 0; i < selCount; ++i) {
			selectors[i] = in.readInt();
		}
		return new UvsCmapFormat(net.zamasoft.pdfg2d.util.LongIntLookup.fromUnsorted(keys, gids, pairCount),
				selectors);
	}

	private static SourceRecord readSource(final DataInputStream in, final GenericCmapFormat[] cmapPool,
			final UvsCmapFormat[] uvsPool) throws IOException {
		final int subtype = in.readUnsignedByte();
		final Direction direction = Direction.values()[in.readUnsignedByte()];
		final int ttcIndex = in.readInt();
		final String fontName = in.readUTF();
		final int aliasCount = in.readUnsignedShort();
		final String[] aliases = new String[aliasCount];
		for (int i = 0; i < aliasCount; ++i) {
			aliases[i] = in.readUTF();
		}
		final boolean italic = in.readBoolean();
		final Weight weight = Weight.values()[in.readUnsignedByte()];
		final byte[] p = new byte[12];
		in.readFully(p);
		final Panose panose = new Panose(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11]);
		final short upm = in.readShort();
		final BBox bbox = new BBox(in.readShort(), in.readShort(), in.readShort(), in.readShort());
		final short ascent = in.readShort();
		final short descent = in.readShort();
		final short spaceAdvance = in.readShort();
		final GenericCmapFormat cmap = cmapPool[in.readUnsignedShort()];
		final int uvsIndex = in.readShort();
		final UvsCmapFormat uvsCmap = uvsIndex < 0 ? null : uvsPool[uvsIndex];
		return new SourceRecord(subtype, direction, ttcIndex, fontName, aliases, italic, weight, panose, upm, bbox,
				ascent, descent, spaceAdvance, cmap, uvsCmap);
	}

	/**
	 * ファイルの鮮度とスキャン条件が一致する索引項目を探し、あれば
	 * FontSource列を再構築して返します。
	 *
	 * @param fontFile フォントファイル
	 * @param scanKey  スキャン条件(types+face属性のダイジェスト)
	 * @return 再構築したFontSource列(記録時の順序)、索引ミスならnull
	 */
	public List<FontSource> lookup(final File fontFile, final String scanKey) {
		final FileEntry entry = this.pathToEntry.get(fontFile.getPath());
		if (entry == null || entry.size != fontFile.length() || entry.lastModified != fontFile.lastModified()
				|| !entry.scanKey.equals(scanKey)) {
			return null;
		}
		final List<FontSource> sources = new ArrayList<>(entry.sources.size());
		for (final SourceRecord r : entry.sources) {
			sources.add(switch (r.subtype) {
			case SUBTYPE_EMBEDDED -> new OpenTypeEmbeddedCIDFontSource(fontFile, r.ttcIndex, r.direction, r.upm,
					r.bbox, r.fontName, r.aliases, r.italic, r.weight, r.panose, r.ascent, r.descent, r.spaceAdvance,
					r.cmap, r.uvsCmap);
			case SUBTYPE_CID_IDENTITY -> new OpenTypeCIDIdentityFontSource(fontFile, r.ttcIndex, r.direction, r.upm,
					r.bbox, r.fontName, r.aliases, r.italic, r.weight, r.panose, r.ascent, r.descent, r.spaceAdvance,
					r.cmap, r.uvsCmap);
			default -> throw new IllegalStateException(String.valueOf(r.subtype));
			});
		}
		return sources;
	}

	/**
	 * スキャン結果を索引に記録します。OpenType系以外のソースが混じって
	 * いた場合はそのファイルを索引対象外とする(次回も通常パース)。
	 *
	 * @param fontFile フォントファイル
	 * @param scanKey  スキャン条件
	 * @param numFonts TTC内のフォント数
	 * @param sources  スキャンで構築したソース列(face属性適用済み)
	 */
	public void put(final File fontFile, final String scanKey, final int numFonts,
			final List<FontSource> sources) {
		final List<SourceRecord> records = new ArrayList<>(sources.size());
		for (final FontSource source : sources) {
			final int subtype;
			if (source instanceof OpenTypeEmbeddedCIDFontSource) {
				subtype = SUBTYPE_EMBEDDED;
			} else if (source instanceof OpenTypeCIDIdentityFontSource) {
				subtype = SUBTYPE_CID_IDENTITY;
			} else {
				// AWT経由のType1等は再構築できないため索引しない
				return;
			}
			final OpenTypeFontSource ot = (OpenTypeFontSource) source;
			if (ot.getPanose() == null || ot.getCmapFormat() == null) {
				// 再構築に必要なメタデータが欠けるファイルは索引しない
				return;
			}
			records.add(new SourceRecord(subtype, ot.getDirection(), ot.getIndex(), ot.getFontName(),
					ot.getAliases(), ot.isItalic(), ot.getWeight(), ot.getPanose(), ot.getUnitsPerEm(), ot.getBBox(),
					ot.getAscent(), ot.getDescent(), ot.getSpaceAdvance(), ot.getCmapFormat(),
					ot.getUvsCmapFormat()));
		}
		this.pathToEntry.put(fontFile.getPath(),
				new FileEntry(fontFile.length(), fontFile.lastModified(), scanKey, numFonts, records));
		this.dirty = true;
	}

	/**
	 * 索引にあるTTC内フォント数を返します(鮮度一致時のみ)。
	 *
	 * @return フォント数、索引ミスなら-1
	 */
	public int numFonts(final File fontFile, final String scanKey) {
		final FileEntry entry = this.pathToEntry.get(fontFile.getPath());
		if (entry == null || entry.size != fontFile.length() || entry.lastModified != fontFile.lastModified()
				|| !entry.scanKey.equals(scanKey)) {
			return -1;
		}
		return entry.numFonts;
	}

	/**
	 * 変更があれば索引をファイルへ書き出します(一時ファイル+rename)。
	 * 失敗しても警告のみ(次回起動が遅いだけで機能に影響しない)。
	 */
	public void save() {
		if (!this.dirty || this.file == null) {
			return;
		}
		final File tmp = new File(this.file.getPath() + ".tmp");
		try {
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
				out.writeInt(MAGIC);
				out.writeInt(VERSION);
				out.writeInt(this.pathToEntry.size());
				for (final Map.Entry<String, FileEntry> e : this.pathToEntry.entrySet()) {
					final FileEntry entry = e.getValue();
					out.writeUTF(e.getKey());
					out.writeLong(entry.size);
					out.writeLong(entry.lastModified);
					out.writeUTF(entry.scanKey);
					out.writeInt(entry.numFonts);
					// identityで重複排除したcmap/UVSプール(FontFile経由で
					// 同一ファイルの全ソースが同じインスタンスを共有している)
					final java.util.IdentityHashMap<GenericCmapFormat, Integer> cmapToIndex = new java.util.IdentityHashMap<>();
					final java.util.IdentityHashMap<UvsCmapFormat, Integer> uvsToIndex = new java.util.IdentityHashMap<>();
					final List<GenericCmapFormat> cmapPool = new ArrayList<>();
					final List<UvsCmapFormat> uvsPool = new ArrayList<>();
					for (final SourceRecord r : entry.sources) {
						if (cmapToIndex.putIfAbsent(r.cmap, cmapPool.size()) == null) {
							cmapPool.add(r.cmap);
						}
						if (r.uvsCmap != null && uvsToIndex.putIfAbsent(r.uvsCmap, uvsPool.size()) == null) {
							uvsPool.add(r.uvsCmap);
						}
					}
					out.writeShort(cmapPool.size());
					for (final GenericCmapFormat cmap : cmapPool) {
						writeCmap(out, cmap);
					}
					out.writeShort(uvsPool.size());
					for (final UvsCmapFormat uvs : uvsPool) {
						writeUvs(out, uvs);
					}
					out.writeInt(entry.sources.size());
					for (final SourceRecord r : entry.sources) {
						writeSource(out, r, cmapToIndex.get(r.cmap),
								r.uvsCmap == null ? -1 : uvsToIndex.get(r.uvsCmap));
					}
				}
			}
			if (!tmp.renameTo(this.file)) {
				// Windowsでは既存ファイルへのrenameが失敗する——削除してから
				this.file.delete();
				if (!tmp.renameTo(this.file)) {
					throw new IOException("rename failed: " + tmp + " -> " + this.file);
				}
			}
			this.dirty = false;
		} catch (final Exception e) {
			LOG.log(Level.WARNING, "Failed to save font index " + this.file, e);
			tmp.delete();
		}
	}

	private static void writeCmap(final DataOutputStream out, final GenericCmapFormat cmap) throws IOException {
		out.writeInt(cmap.starts().length);
		for (int i = 0; i < cmap.starts().length; ++i) {
			out.writeInt(cmap.starts()[i]);
			out.writeInt(cmap.ends()[i]);
			out.writeInt(cmap.gids()[i]);
			out.writeBoolean(cmap.constant()[i]);
		}
	}

	private static void writeUvs(final DataOutputStream out, final UvsCmapFormat uvs) throws IOException {
		final net.zamasoft.pdfg2d.util.LongIntLookup lookup = uvs.codeToGlyphId();
		out.writeInt(lookup.size());
		for (int i = 0; i < lookup.size(); ++i) {
			out.writeLong(lookup.keyAt(i));
			out.writeInt(lookup.valueAt(i));
		}
		out.writeInt(uvs.selectors().length);
		for (final int sel : uvs.selectors()) {
			out.writeInt(sel);
		}
	}

	private static void writeSource(final DataOutputStream out, final SourceRecord r, final int cmapPoolIndex,
			final int uvsPoolIndex) throws IOException {
		out.writeByte(r.subtype);
		out.writeByte(r.direction.ordinal());
		out.writeInt(r.ttcIndex);
		out.writeUTF(r.fontName);
		out.writeShort(r.aliases.length);
		for (final String alias : r.aliases) {
			out.writeUTF(alias);
		}
		out.writeBoolean(r.italic);
		out.writeByte(r.weight.ordinal());
		out.writeByte(r.panose.familyClassId());
		out.writeByte(r.panose.familySubclass());
		out.writeByte(r.panose.familyType());
		out.writeByte(r.panose.serifStyle());
		out.writeByte(r.panose.weight());
		out.writeByte(r.panose.proportion());
		out.writeByte(r.panose.contrast());
		out.writeByte(r.panose.strokeVariation());
		out.writeByte(r.panose.armStyle());
		out.writeByte(r.panose.letterForm());
		out.writeByte(r.panose.midline());
		out.writeByte(r.panose.xHeight());
		out.writeShort(r.upm);
		out.writeShort(r.bbox.llx());
		out.writeShort(r.bbox.lly());
		out.writeShort(r.bbox.urx());
		out.writeShort(r.bbox.ury());
		out.writeShort(r.ascent);
		out.writeShort(r.descent);
		out.writeShort(r.spaceAdvance);
		out.writeShort(cmapPoolIndex);
		out.writeShort(uvsPoolIndex);
	}
}
