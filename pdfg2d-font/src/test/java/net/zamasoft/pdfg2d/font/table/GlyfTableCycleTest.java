package net.zamasoft.pdfg2d.font.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 合成グリフが自分自身を指すフォントで、字形の読み出しが止まることを押さえます。
 *
 * <p>
 * 2026-09-01、本番のフォント一覧が{@code StackOverflowError}で500になった。
 * 書体ごとに代表符号位置の字形を引いて{@code scripts}を名乗るようにしたところ、
 * フォントパックの1書体で合成グリフの成分が循環していて、
 * {@code GlyfCompositeDescript.read}→{@code GlyfTable.getDescription}が
 * 無限再帰した。入れ子に上限を入れて断ってある。
 * </p>
 */
public class GlyfTableCycleTest {

	/** 自分自身を1つだけ成分に持つ合成グリフ。 */
	private static byte[] selfReferencingComposite() {
		return new byte[] { //
				(byte) 0xFF, (byte) 0xFF, // numberOfContours = -1(合成)
				0, 0, 0, 0, 0, 0, 0, 0, // xMin, yMin, xMax, yMax
				0, 0x02, // flags = ARGS_ARE_XY_VALUES のみ(MORE_COMPONENTSなし)
				0, 0, // glyphIndex = 0 ← 自分自身
				0, 0 // argument1, argument2(1バイトずつ)
		};
	}

	@Test
	public void selfReferencingCompositeDoesNotRecurseForever() throws IOException {
		final byte[] glyf = selfReferencingComposite();
		final Path file = Files.createTempFile("glyf-cycle", ".bin");
		try {
			Files.write(file, glyf);
			try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
				final LocaTable loca = new LocaTable(new int[] { 0, glyf.length }, (short) 1);
				final DirectoryEntry de = new DirectoryEntry(Table.GLYF, 0, 0, glyf.length);
				final GlyfTable glyfTable = new GlyfTable(de, loca, raf);

				// 循環を切れていなければStackOverflowErrorになるか、返ってこない
				assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
					final var desc = glyfTable.getDescription(0);
					assertNotNull(desc);
					// 自分へ戻る唯一の成分は落ちるので、点も輪郭も無い
					assertEquals(0, desc.getPointCount());
					assertEquals(0, desc.getContourCount());
				});
			}
		} finally {
			Files.deleteIfExists(file);
		}
	}
}
