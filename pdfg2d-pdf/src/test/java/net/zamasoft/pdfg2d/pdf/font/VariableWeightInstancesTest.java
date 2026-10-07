package net.zamasoft.pdfg2d.pdf.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.font.table.Table;
import net.zamasoft.pdfg2d.font.table.XmtxTable;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;

/**
 * Ensures that variable fonts in the font directory have copies registered with fixed wght values (2026-10-04).
 *
 * <p>
 * The default instance of the Mulish variable font is ExtraLight (wght 200, axis range 200–1000).
 * Previously, only the 200 instance was registered, so text used thin glyphs even when bold was requested.
 * </p>
 */
public class VariableWeightInstancesTest {

	@TempDir
	Path tempDir;

	private static final File FONT = new File("src/test/resources/mulish/Mulish-VariableFont_wght.ttf");

	private static List<FontSource> scan() throws Exception {
		final List<FontSource> list = new ArrayList<>();
		final FontFace face = new FontFace();
		FontLoader.readTTF(list, face, FontLoader.Type.CID_IDENTITY, FONT, 0, new HashMap<>(), true);
		FontLoader.readTTF(list, face, FontLoader.Type.EMBEDDED, FONT, 0, new HashMap<>(), true);
		FontLoader.addWeightInstances(list, FONT);
		return list;
	}

	private static OpenTypeFontSource find(final List<FontSource> list, final Weight weight) {
		for (final FontSource source : list) {
			if (source.getWeight() == weight && source.getDirection() == Direction.LTR
					&& source instanceof net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource) {
				return (OpenTypeFontSource) source;
			}
		}
		return null;
	}

	private static int advance(final OpenTypeFontSource source, final char c) {
		final int gid = source.getCmapFormat().mapCharCode(c);
		return ((XmtxTable) source.getOpenTypeFont().getTable(Table.HMTX)).getAdvanceWidth(gid);
	}

	@Test
	public void weightsWithinTheAxisAreRegistered() throws Exception {
		final List<FontSource> list = scan();
		final TreeSet<Integer> weights = new TreeSet<>();
		for (final FontSource source : list) {
			weights.add((int) source.getWeight().w);
		}
		// Default instance (200) plus 300–900 within the axis range. 100 is outside the axis range
		assertEquals(new TreeSet<>(List.of(200, 300, 400, 500, 600, 700, 800, 900)), weights);

		final OpenTypeFontSource light = find(list, Weight.W_200);
		final OpenTypeFontSource bold = find(list, Weight.W_700);
		assertNull(light.getVariation());
		assertEquals(Map.of("wght", 700.0), bold.getVariation());
		// Copies generate static fonts when used. Heavier weights have wider glyphs
		assertNotEquals(FONT, bold.getFile());
		assertTrue(advance(bold, 'm') > advance(light, 'm'), advance(bold, 'm') + " > " + advance(light, 'm'));
		assertEquals(bold.getFile(), bold.getFile(), "実体化は 1 度だけ");
	}

	@Test
	public void indexRestoresTheWeightInstances() throws Exception {
		final List<FontSource> parsed = scan();
		final File dbFile = Files.createTempFile(this.tempDir, "fonts", ".db").toFile();
		final FontIndex writeIndex = new FontIndex(dbFile);
		writeIndex.put(FONT, "key", 1, parsed);
		writeIndex.save();

		final List<FontSource> restored = new FontIndex(dbFile).lookup(FONT, "key");
		assertNotNull(restored);
		assertEquals(parsed.size(), restored.size());
		for (int i = 0; i < parsed.size(); ++i) {
			final OpenTypeFontSource a = (OpenTypeFontSource) parsed.get(i);
			final OpenTypeFontSource b = (OpenTypeFontSource) restored.get(i);
			assertEquals(a.getWeight(), b.getWeight());
			assertEquals(a.getVariation(), b.getVariation());
			assertEquals(a.getDirection(), b.getDirection());
		}
		assertEquals(advance(find(parsed, Weight.W_700), 'm'), advance(find(restored, Weight.W_700), 'm'));
	}
}
