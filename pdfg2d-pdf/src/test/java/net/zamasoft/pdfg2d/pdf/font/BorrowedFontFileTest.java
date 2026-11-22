package net.zamasoft.pdfg2d.pdf.font;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.zstream.resolver.cache.CachedSource;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

/**
 * A font whose source is a file that only stores a resource received from elsewhere (an uploaded {@code @font-face}
 * font in the temporary file of the resolver that received it) is not opened from that file. The parsed-font cache
 * is shared by the process, keyed by path, and keeps the font open: the file's owner deleted the file later, and the
 * path, and on some systems the open file, stayed in the cache.
 */
public class BorrowedFontFileTest {
	private static final File FONT = new File("src/test/resources/ipaexm-novert2500.ttf");

	/** Whether the parsed-font cache has an entry for {@code file}. */
	private static boolean parsedFontCached(final File file) throws Exception {
		final Field field = OpenTypeFontSource.class.getDeclaredField("fileToFont");
		field.setAccessible(true);
		final Map<?, ?> cache = (Map<?, ?>) field.get(null);
		synchronized (cache) {
			return cache.containsKey(file.getPath());
		}
	}

	private static FontFace face(final net.zamasoft.zstream.resolver.Source src) {
		final FontFace face = new FontFace();
		face.fontFamily = FontFamilyList.create("Borrowed");
		face.src = src;
		return face;
	}

	@Test
	public void aFontInTemporaryStorageIsNotCachedByThatPath() throws Exception {
		final File upload = File.createTempFile("pdfg2d-test-upload-", ".dat");
		try {
			Files.copy(FONT.toPath(), upload.toPath(), StandardCopyOption.REPLACE_EXISTING);
			final PDFFontSourceManager manager = new PDFFontSourceManager(true);
			try {
				manager.addFontFace(
						face(new CachedSource(URI.create("copper-test:/font.ttf"), "font/ttf", null, upload)));
				assertTrue(manager.lookup(null).length > 0, "the font was not read");
				assertFalse(parsedFontCached(upload), "the font was opened from the temporary file");
			} finally {
				manager.close();
			}
			assertFalse(parsedFontCached(upload), "the temporary file stayed in the parsed-font cache");
			assertTrue(upload.delete(), "the temporary file is still open");
		} finally {
			// Only when the test fails: do not leave the font open in the cache of the test JVM
			OpenTypeFontSource.release(upload);
			upload.delete();
		}
	}

	/** A local font file named by its own {@code file:} URI is still opened in place, without a copy. */
	@Test
	public void aLocalFontFileIsOpenedInPlace() throws Exception {
		final File local = File.createTempFile("pdfg2d-test-local-", ".ttf");
		try {
			Files.copy(FONT.toPath(), local.toPath(), StandardCopyOption.REPLACE_EXISTING);
			final PDFFontSourceManager manager = new PDFFontSourceManager(true);
			try {
				manager.addFontFace(face(new FileSource(local)));
				assertTrue(manager.lookup(null).length > 0, "the font was not read");
				assertTrue(manager.uriToFile.isEmpty(), "a local font file was copied");
				assertTrue(parsedFontCached(local), "the local font file was not opened in place");
			} finally {
				manager.close();
				OpenTypeFontSource.release(local);
			}
		} finally {
			local.delete();
		}
	}
}
