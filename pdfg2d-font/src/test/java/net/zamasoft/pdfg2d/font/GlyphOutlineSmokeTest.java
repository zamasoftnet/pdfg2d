package net.zamasoft.pdfg2d.font;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Decodes the outline of every glyph in each bundled test font.
 * <p>
 * This is a regression net for glyph-decoding crashes (such as reading past
 * the end of a contour): binary font parsers tend to fail on boundary glyphs
 * — the first, the last, empty glyphs, and composite glyphs — so instead of
 * sampling a few known code points we sweep the full glyph range.
 * </p>
 */
public class GlyphOutlineSmokeTest {

	@ParameterizedTest
	@ValueSource(strings = { "test.otf", "test.ttf", "test.woff" })
	public void testDecodeAllGlyphs(final String resourceName) throws Exception {
		final var file = new File("src/test/resources/data/" + resourceName);
		assertTrue(file.isFile(), "Test font should exist: " + resourceName);

		final var fontFile = new FontFile(file);
		final var font = fontFile.getFont();
		assertNotNull(font);

		final var numGlyphs = font.getNumGlyphs();
		assertTrue(numGlyphs > 0, "Font must contain glyphs");

		var decoded = 0;
		for (var gid = 0; gid < numGlyphs; ++gid) {
			// Must never throw, even for empty or malformed boundary glyphs.
			final var glyph = font.getGlyph(gid);
			if (glyph != null && (glyph.path() != null || glyph.charString() != null)) {
				++decoded;
			}
		}
		assertTrue(decoded > 0, "At least one glyph should carry outline data in " + resourceName);
	}
}
