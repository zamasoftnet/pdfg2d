package net.zamasoft.pdfg2d.pdf.font.cid.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;

/** Glyph-ledger regressions below the PDF object writer. */
public class OpenTypeEmbeddedCIDFontSubsetTest {
	private static final File FONT = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");

	private record Pair(OpenTypeEmbeddedCIDFont horizontal, OpenTypeEmbeddedCIDFont vertical,
			OpenTypeEmbeddedCIDFontSubset subset) {
	}

	private static Pair pair() throws Exception {
		final var horizontalSource = new OpenTypeEmbeddedCIDFontSource(FONT, 0, Direction.LTR);
		final var verticalSource = new OpenTypeEmbeddedCIDFontSource(FONT, 0, Direction.TB);
		final var subset = horizontalSource.createSubset();
		final var horizontal = (OpenTypeEmbeddedCIDFont) horizontalSource.createFont("H", null, subset);
		final var vertical = (OpenTypeEmbeddedCIDFont) verticalSource.createFont("V", null, subset);
		return new Pair(horizontal, vertical, subset);
	}

	private static int[] directionMappings(final boolean verticalFirst) throws Exception {
		final var pair = pair();
		final int sourceGid = ((OpenTypeEmbeddedCIDFontSource) pair.horizontal.getFontSource())
				.getCmapFormat().mapCharCode('A');
		final int horizontalCid;
		final int verticalCid;
		if (verticalFirst) {
			verticalCid = pair.vertical.addGID('B', sourceGid);
			horizontalCid = pair.horizontal.addGID('A', sourceGid);
		} else {
			horizontalCid = pair.horizontal.addGID('A', sourceGid);
			verticalCid = pair.vertical.addGID('B', sourceGid);
		}
		return new int[] { horizontalCid, verticalCid, pair.horizontal.toChar(horizontalCid),
				pair.vertical.toChar(verticalCid) };
	}

	@Test
	public void sharedOutlineKeepsDirectionLocalUnicodeInEitherOrder() throws Exception {
		assertEquals(java.util.List.of(1, 1, (int) 'A', (int) 'B'),
				java.util.Arrays.stream(directionMappings(false)).boxed().toList());
		assertEquals(java.util.List.of(1, 1, (int) 'A', (int) 'B'),
				java.util.Arrays.stream(directionMappings(true)).boxed().toList());
	}

	@Test
	public void verticalAlternateGetsASeparateCidAndLeavesHorizontalGapUnmapped() throws Exception {
		final var pair = pair();
		final int sourceGid = ((OpenTypeEmbeddedCIDFontSource) pair.horizontal.getFontSource())
				.getCmapFormat().mapCharCode(0x3001);
		final int horizontalCid = pair.horizontal.addGID(0x3001, sourceGid);
		final int verticalCid = pair.vertical.addGID(0x3001, sourceGid);
		assertNotEquals(horizontalCid, verticalCid, "vrt2/vert outline must remain a distinct CID");
		assertNotEquals(pair.subset.sourceGid(horizontalCid), pair.subset.sourceGid(verticalCid));
		assertEquals(-1, pair.horizontal.toChar(verticalCid),
				"the other direction's CID must not receive an inferred Unicode mapping");
	}

	@Test
	public void manualVerticalRotationIsPartOfThePhysicalGlyphIdentity() throws Exception {
		final var pair = pair();
		final int sourceGid = ((OpenTypeEmbeddedCIDFontSource) pair.horizontal.getFontSource())
				.getCmapFormat().mapCharCode('A');
		final int horizontalCid = pair.horizontal.addGID(0xFF0D, sourceGid);
		final int verticalCid = pair.vertical.addGID(0xFF0D, sourceGid);
		assertNotEquals(horizontalCid, verticalCid);
		assertEquals(0, pair.subset.shapeFlags(horizontalCid));
		assertTrue(pair.subset.shapeFlags(verticalCid) != 0);
		final var horizontalBounds = pair.horizontal.getShape(horizontalCid).getBounds2D();
		final var verticalBounds = pair.vertical.getShape(verticalCid).getBounds2D();
		assertEquals(horizontalBounds.getWidth(), verticalBounds.getHeight(), 1.0);
		assertEquals(horizontalBounds.getHeight(), verticalBounds.getWidth(), 1.0);
	}
}
