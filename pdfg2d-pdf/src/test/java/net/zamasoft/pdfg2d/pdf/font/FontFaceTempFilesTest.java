package net.zamasoft.pdfg2d.pdf.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.zstream.resolver.protocol.stream.StreamSource;

/**
 * Ensures that closing the manager leaves no temporary files created for {@code @font-face} (2026-10-05).
 *
 * <p>
 * Only the copy of the fetched data was deleted. Variable font copies (nine weights) and the fonts held
 * by the cache that opened them remained until the process exited. In a persistent server, they
 * accumulated with each conversion.
 * </p>
 */
public class FontFaceTempFilesTest {
	private static final File FONT = new File("src/test/resources/mulish/Mulish-VariableFont_wght.ttf");

	private static final String[] PREFIXES = { "copper-font-face", "copper-vf-instance", "pdfg2d-font-",
			"pdfg2d-woff2-" };

	private static Set<String> tempFiles() {
		final Set<String> names = new TreeSet<>();
		final File[] files = new File(System.getProperty("java.io.tmpdir")).listFiles();
		if (files != null) {
			for (final File file : files) {
				for (final String prefix : PREFIXES) {
					if (file.getName().startsWith(prefix)) {
						names.add(file.getName());
					}
				}
			}
		}
		return names;
	}

	@Test
	public void closingTheManagerRemovesTheFilesItMade() throws Exception {
		final Set<String> before = tempFiles();
		final PDFFontSourceManager manager = new PDFFontSourceManager(true);
		try (final InputStream in = new FileInputStream(FONT)) {
			final FontFace face = new FontFace();
			face.src = new StreamSource(URI.create("http://example.com/mulish.ttf"), in, "font/ttf", FONT.length());
			manager.addFontFace(face);
		}
		final Set<String> made = tempFiles();
		made.removeAll(before);
		// One copy of the fetched data, plus copies for the weights
		assertTrue(made.size() > 1, made.toString());

		manager.close();
		final Set<String> left = tempFiles();
		left.removeAll(before);
		assertEquals(Set.of(), left);
	}
}
