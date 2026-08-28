package net.zamasoft.pdfg2d.pdf.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.util.MultimapUtils;

/**
 * {@code font-stretch}の幅級(OS/2 usWidthClass)による書体選択を固定します
 * (2026-08-29)。同族でitalic/weightが同点の面が複数あるとき、要求幅級に
 * 近い面——通常幅以下の要求なら狭い側を先に、広い要求なら広い側を先に
 * (css-fonts-4 §5.2 step 1)——を選ぶ。
 */
public class FontStretchSelectionTest {
	private static final File FONT = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");
	/** WSL/Ubuntuの標準フォント。あればOS/2からの幅級読取りも検査する。 */
	private static final File DEJAVU_CONDENSED = new File(
			"/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf");
	private static final FontPolicyList EMBEDDED = new FontPolicyList(
			new FontPolicyList.FontPolicy[] { FontPolicyList.FontPolicy.EMBEDDED });
	private static final String FAMILY = "StretchFamily";

	private static final class Manager extends PDFFontSourceManager {
		Manager() {
			super(false);
		}

		void add(final FontSource source) {
			this.allFonts.add(source);
			MultimapUtils.putDirect(this.nameToFonts, FontUtils.normalizeName(source.getFontName()), source);
		}
	}

	/** 同じ字形ファイルから、幅級だけ違う同族の面を作る。 */
	private static OpenTypeEmbeddedCIDFontSource face(final int widthClass) throws Exception {
		final var source = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.LTR);
		source.setFontName(FAMILY);
		source.setWidthClass(widthClass);
		return source;
	}

	private static FontStyle style(final int widthClass) {
		return new FontStyleImpl(FontFamilyList.create(FAMILY), 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, EMBEDDED, FontFeatureSet.EMPTY, true, true,
				FontStyle.TextOrientation.MIXED, widthClass);
	}

	@Test
	public void widthPenaltyPrefersNarrowerForCondensedAndWiderForExpanded() {
		assertEquals(0, PDFFontSourceManager.widthPenalty(3, 3));
		// 要求condensed(3): 狭い側(1,2)はどれも広い側(4..)より先
		assertTrue(PDFFontSourceManager.widthPenalty(3, 1) < PDFFontSourceManager.widthPenalty(3, 4));
		assertTrue(PDFFontSourceManager.widthPenalty(3, 2) < PDFFontSourceManager.widthPenalty(3, 1));
		// 要求expanded(7): 広い側(8,9)が狭い側(6..)より先
		assertTrue(PDFFontSourceManager.widthPenalty(7, 9) < PDFFontSourceManager.widthPenalty(7, 6));
		assertTrue(PDFFontSourceManager.widthPenalty(7, 8) < PDFFontSourceManager.widthPenalty(7, 9));
		// 要求normal(5): 狭い側が先(仕様: 100%以下は下位から)
		assertTrue(PDFFontSourceManager.widthPenalty(5, 4) < PDFFontSourceManager.widthPenalty(5, 6));
		// 5ビットに収まる
		assertTrue(PDFFontSourceManager.widthPenalty(1, 9) <= 31);
		assertTrue(PDFFontSourceManager.widthPenalty(9, 1) <= 31);
	}

	@Test
	public void stretchSelectsNearestWidthClassAmongEqualWeightFaces() throws Exception {
		final var manager = new Manager();
		manager.add(face(5)); // normal
		manager.add(face(3)); // condensed

		assertEquals(3, manager.lookup(style(3))[0].getWidthClass(), "condensed → 幅級3の面");
		assertEquals(5, manager.lookup(style(5))[0].getWidthClass(), "normal → 幅級5の面");
		assertEquals(3, manager.lookup(style(4))[0].getWidthClass(), "semi-condensed → 狭い側を優先");
		assertEquals(5, manager.lookup(style(7))[0].getWidthClass(), "expanded → 広い面が無いので最寄りの5");
		assertEquals(3, manager.lookup(style(1))[0].getWidthClass(), "ultra-condensed → 最寄りの狭い面");
		assertEquals(2, manager.lookup(style(3)).length, "候補は両方残る(フォールバック用)");
	}

	@Test
	public void weightStillOutranksWidth() throws Exception {
		final var manager = new Manager();
		final var boldNormal = face(5);
		boldNormal.setWeight(FontStyle.Weight.W_700);
		manager.add(boldNormal);
		manager.add(face(3));
		// 要求: condensed かつ 700。weightの一致が幅級より優先される
		final FontStyle bold = new FontStyleImpl(FontFamilyList.create(FAMILY), 12, FontStyle.Style.NORMAL,
				FontStyle.Weight.W_700, FontStyle.Direction.LTR, EMBEDDED, FontFeatureSet.EMPTY, true, true,
				FontStyle.TextOrientation.MIXED, 3);
		assertEquals(FontStyle.Weight.W_700, manager.lookup(bold)[0].getWeight());
	}

	@Test
	public void styleKeyDistinguishesWidthClass() {
		assertTrue(FontUtils.equals(style(3), style(3)));
		assertTrue(!FontUtils.equals(style(3), style(5)));
		assertTrue(FontUtils.hashCode(style(3)) != FontUtils.hashCode(style(5)));
		assertEquals(5, new FontStyleImpl(FontFamilyList.create(FAMILY), 12, FontStyle.Style.NORMAL,
				FontStyle.Weight.W_400, FontStyle.Direction.LTR, EMBEDDED).getWidthClass(), "互換コンストラクタは通常幅");
	}

	@Test
	public void fontFaceDescriptorDefinesWidthClass() throws Exception {
		final FontFace face = new FontFace();
		face.widthClass = 3;
		final List<FontSource> list = new ArrayList<>();
		FontLoader.readTTF(list, face, FontLoader.Type.EMBEDDED, FONT, 0, new HashMap<>());
		assertEquals(3, list.get(0).getWidthClass(), "@font-face経路はディスクリプタの幅級");
		final List<FontSource> fromFile = new ArrayList<>();
		FontLoader.readTTF(fromFile, face, FontLoader.Type.EMBEDDED, FONT, 0, new HashMap<>(), true);
		assertEquals(5, fromFile.get(0).getWidthClass(), "font-dir経路はファイルのOS/2(IPAexは通常幅)");
	}

	@Test
	public void os2WidthClassIsReadFromFontDirScan() throws Exception {
		Assumptions.assumeTrue(DEJAVU_CONDENSED.isFile(), "DejaVu Sans Condensedが無い環境では省略");
		final List<FontSource> list = new ArrayList<>();
		FontLoader.readTTF(list, new FontFace(), FontLoader.Type.EMBEDDED, DEJAVU_CONDENSED, 0, new HashMap<>(),
				true);
		assertEquals(4, list.get(0).getWidthClass(), "DejaVuSansCondensedのusWidthClassは4(semi-condensed、fc-query width=87)");
	}

	@Test
	public void indexRoundTripKeepsWidthClass() throws Exception {
		final List<FontSource> list = new ArrayList<>();
		FontLoader.readTTF(list, new FontFace(), FontLoader.Type.EMBEDDED, FONT, 0, new HashMap<>());
		((OpenTypeEmbeddedCIDFontSource) list.get(0)).setWidthClass(3);

		final File dir = Files.createTempDirectory("font-stretch").toFile();
		final File dbFile = new File(dir, "fonts.db");
		try {
			final FontIndex writeIndex = new FontIndex(dbFile);
			writeIndex.put(FONT, "key", 1, list);
			writeIndex.save();
			final List<FontSource> restored = new FontIndex(dbFile).lookup(FONT, "key");
			assertNotNull(restored);
			assertEquals(3, restored.get(0).getWidthClass(), "索引は幅級を保存する(V3)");
		} finally {
			dbFile.delete();
			dir.delete();
		}
	}
}
