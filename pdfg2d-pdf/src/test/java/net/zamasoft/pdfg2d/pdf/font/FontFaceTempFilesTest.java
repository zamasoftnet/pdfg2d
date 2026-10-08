package net.zamasoft.pdfg2d.pdf.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
 *
 * <p>
 * The test looks at the files the manager made, not at the temporary directory (2026-10-08): other processes on the
 * machine (the random sweep, the other test workers) make files with the same prefixes there at the same time, and a
 * file of theirs that was still there after {@code close()} failed the test now and then.
 * </p>
 */
public class FontFaceTempFilesTest {
	private static final File FONT = new File("src/test/resources/mulish/Mulish-VariableFont_wght.ttf");

	/** The copy of the fetched data and the variable font copies that the manager owns. */
	@SuppressWarnings("unchecked")
	private static List<File> ownedFiles(final PDFFontSourceManager manager) throws Exception {
		final List<File> files = new ArrayList<>(manager.uriToFile.values());
		final Field instances = PDFFontSourceManager.class.getDeclaredField("instanceFiles");
		instances.setAccessible(true);
		files.addAll((List<File>) instances.get(manager));
		return files;
	}

	@Test
	public void closingTheManagerRemovesTheFilesItMade() throws Exception {
		final PDFFontSourceManager manager = new PDFFontSourceManager(true);
		try (final InputStream in = new FileInputStream(FONT)) {
			final FontFace face = new FontFace();
			face.src = new StreamSource(URI.create("http://example.com/mulish.ttf"), in, "font/ttf", FONT.length());
			manager.addFontFace(face);
		}
		final List<File> made = ownedFiles(manager);
		// One copy of the fetched data, plus copies for the weights
		assertEquals(1, manager.uriToFile.size(), made.toString());
		assertTrue(made.size() > 1, made.toString());
		for (final File file : made) {
			assertTrue(file.isFile(), "not made: " + file);
		}

		manager.close();
		final List<File> left = new ArrayList<>();
		for (final File file : made) {
			if (file.exists()) {
				left.add(file);
			}
		}
		assertEquals(List.of(), left);
	}
}
