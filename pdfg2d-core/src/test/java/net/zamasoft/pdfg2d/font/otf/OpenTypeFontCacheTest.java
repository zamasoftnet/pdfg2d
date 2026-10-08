package net.zamasoft.pdfg2d.font.otf;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.zamasoft.pdfg2d.font.OpenTypeFont;

/**
 * The parsed-font cache reads a file's last-modified time at most once per second (2026-10-08). It used to read it
 * on every {@link OpenTypeFontSource#getOpenTypeFont(File, int)}, which runs per character and per fallback
 * candidate, and was slow on file systems with costly stat calls. A replaced file must still be picked up.
 */
public class OpenTypeFontCacheTest {
	@TempDir
	Path dir;

	private File copy(final String resource, final String name) throws Exception {
		final Path path = this.dir.resolve(name);
		try (InputStream in = OpenTypeFontCacheTest.class.getResourceAsStream("/" + resource)) {
			Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
		}
		return path.toFile();
	}

	@Test
	public void lastModifiedIsNotReadOnEveryCall() throws Exception {
		final File font = this.copy("pgothic-novert-subset.ttf", "counted.ttf");
		final AtomicInteger reads = new AtomicInteger();
		final File counted = new File(font.getPath()) {
			private static final long serialVersionUID = 1L;

			@Override
			public long lastModified() {
				reads.incrementAndGet();
				return super.lastModified();
			}
		};
		final OpenTypeFont first = OpenTypeFontSource.getOpenTypeFont(counted, 0);
		for (int i = 0; i < 10_000; ++i) {
			assertSame(first, OpenTypeFontSource.getOpenTypeFont(counted, 0));
		}
		// One read for the cache check and one by the font file when it is loaded (a slow machine may add one
		// more when a second passes); previously 10 002
		assertTrue(reads.get() <= 4, "last-modified reads: " + reads.get());
		OpenTypeFontSource.release(counted);
	}

	@Test
	public void replacedFileIsPickedUp() throws Exception {
		final File font = this.copy("pgothic-novert-subset.ttf", "replaced.ttf");
		final OpenTypeFont before = OpenTypeFontSource.getOpenTypeFont(font, 0);
		assertSame(before, OpenTypeFontSource.getOpenTypeFont(font, 0));

		final long time = font.lastModified();
		this.copy("pgothic-vert-subset.ttf", "replaced.ttf");
		assertTrue(font.setLastModified(time + 10_000));
		Thread.sleep(1_100);
		final OpenTypeFont after = OpenTypeFontSource.getOpenTypeFont(font, 0);
		assertNotSame(before, after, "a replaced font file is reloaded once its time is read again");

		// release() (owners of per-document font files call it before deleting them) forgets the time at once
		OpenTypeFontSource.release(font);
		this.copy("pgothic-novert-subset.ttf", "replaced.ttf");
		assertTrue(font.setLastModified(time + 20_000));
		final OpenTypeFont again = OpenTypeFontSource.getOpenTypeFont(font, 0);
		assertNotSame(after, again);
		OpenTypeFontSource.release(font);
	}
}
