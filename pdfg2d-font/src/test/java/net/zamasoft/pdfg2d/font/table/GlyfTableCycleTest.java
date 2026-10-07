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
 * Ensures that reading glyphs terminates for fonts with self-referencing composite glyphs.
 *
 * <p>
 * On 2026-09-01, the production font list returned HTTP 500 due to {@code StackOverflowError}.
 * After adding {@code scripts} identification by looking up representative code point glyphs for each font,
 * a font in the font pack had cyclic composite glyph components,
 * causing infinite recursion from {@code GlyfCompositeDescript.read} to {@code GlyfTable.getDescription}.
 * A nesting limit rejects this.
 * </p>
 */
public class GlyfTableCycleTest {

	/** Composite glyph with a single component referencing itself. */
	private static byte[] selfReferencingComposite() {
		return new byte[] { //
				(byte) 0xFF, (byte) 0xFF, // numberOfContours = -1 (composite).
				0, 0, 0, 0, 0, 0, 0, 0, // xMin, yMin, xMax, yMax
				0, 0x02, // flags = ARGS_ARE_XY_VALUES only (no MORE_COMPONENTS).
				0, 0, // glyphIndex = 0 <- itself.
				0, 0 // argument1, argument2 (one byte each).
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

				// Without breaking the cycle, this throws StackOverflowError or never returns.
				assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
					final var desc = glyfTable.getDescription(0);
					assertNotNull(desc);
					// The sole component that leads back to itself is dropped, leaving no points or contours.
					assertEquals(0, desc.getPointCount());
					assertEquals(0, desc.getContourCount());
				});
			}
		} finally {
			Files.deleteIfExists(file);
		}
	}
}
