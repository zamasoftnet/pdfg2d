package net.zamasoft.pdfg2d.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.zamasoft.pdfg2d.resolver.composite.CompositeSourceResolver;
import net.zamasoft.pdfg2d.resolver.protocol.data.DataSource;
import net.zamasoft.pdfg2d.resolver.protocol.data.DataSourceResolver;
import net.zamasoft.pdfg2d.resolver.protocol.file.FileSource;
import net.zamasoft.pdfg2d.resolver.protocol.file.FileSourceResolver;
import net.zamasoft.pdfg2d.resolver.cache.CachedSource;
import net.zamasoft.pdfg2d.resolver.cache.CachedSourceResolver;
import net.zamasoft.pdfg2d.resolver.restricted.RestrictedSourceResolver;
import net.zamasoft.pdfg2d.resolver.protocol.stream.StreamSource;
import net.zamasoft.pdfg2d.resolver.util.SourceWrapper;
import net.zamasoft.pdfg2d.resolver.util.SimpleSourceMetadata;
import net.zamasoft.pdfg2d.resolver.util.UnknownSourceValidity;
import net.zamasoft.pdfg2d.resolver.util.ValidSourceValidity;
import net.zamasoft.pdfg2d.resolver.protocol.url.URLSource;

class ResolverApiTest {
    @TempDir
    File tempDir;

    @Test
    void testDataSourceResolverDecodesTextAndBase64Uris() throws Exception {
        final var resolver = new DataSourceResolver();

        try (final var textSource = resolver.resolve(new java.net.URI("data:text/plain;charset=UTF-8,Hello%20World"))) {
            assertTrue(textSource instanceof DataSource);
            assertTrue(textSource.exists());
            assertFalse(textSource.isFile());
            assertTrue(textSource.isInputStream());
            assertTrue(textSource.isReader());
            assertEquals("text/plain", textSource.getMimeType());
            assertEquals("UTF-8", textSource.getEncoding());
            assertEquals("Hello World", readString(textSource.getInputStream()));
            assertEquals("Hello World", readAll(textSource.getReader()));
        }

        try (final var base64Source = resolver.resolve(new java.net.URI("data:text/plain;base64,SGVsbG8rV29ybGQ="))) {
            assertEquals("Hello+World", readString(base64Source.getInputStream()));
            assertEquals(11L, base64Source.getLength());
        }
    }

    @Test
    void testRestrictedSourceResolverHonorsAclAndAllowsDataUris() throws Exception {
        final var delegate = new DataSourceResolver();
        final var resolver = new RestrictedSourceResolver(delegate);
        resolver.exclude(new java.net.URI("https://example.com/private/*"));
        resolver.include(new java.net.URI("https://example.com/*"));

        try (final var source = resolver.resolve(new java.net.URI("data:text/plain,inline"))) {
            assertEquals("inline", readString(source.getInputStream()));
        }

        assertThrows(SecurityException.class,
                () -> resolver.resolve(new java.net.URI("https://example.com/private/file.txt")));
        assertThrows(SecurityException.class,
                () -> resolver.resolve(new java.net.URI("https://other.example.com/open.txt")));
    }

    @Test
    void testRestrictedSourceResolverToKeyNormalizesHttpEncoding() throws Exception {
        final var uri = new java.net.URI("https://example.com/a%20b?q=x+y%2Bz");
        final var key = RestrictedSourceResolver.toKey(uri);

        assertEquals("https://example.com/a b?q=x y+z", key);
    }

    @Test
    void testCompositeSourceResolverUsesDefaultSchemeAndDelegatesRelease() throws Exception {
        final var released = new AtomicBoolean(false);
        final var resolver = new CompositeSourceResolver();
        resolver.setDefaultScheme("file");
        resolver.setDefaultSourceResolver(new FileSourceResolver());
        resolver.addSourceResolver("memo", new SourceResolver() {
            @Override
            public Source resolve(final java.net.URI uri) {
                return new Source() {
                    @Override
                    public boolean exists() {
                        return true;
                    }

                    @Override
                    public boolean isInputStream() {
                        return true;
                    }

                    @Override
                    public java.io.InputStream getInputStream() {
                        return new java.io.ByteArrayInputStream("memo".getBytes(StandardCharsets.UTF_8));
                    }

                    @Override
                    public boolean isReader() {
                        return false;
                    }

                    @Override
                    public java.io.Reader getReader() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean isFile() {
                        return false;
                    }

                    @Override
                    public File getFile() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public SourceValidity getValidity() {
                        return ValidSourceValidity.SHARED_INSTANCE;
                    }

                    @Override
                    public java.net.URI getURI() {
                        return uri;
                    }

                    @Override
                    public String getMimeType() {
                        return "text/plain";
                    }

                    @Override
                    public String getEncoding() {
                        return "UTF-8";
                    }

                    @Override
                    public long getLength() {
                        return 4;
                    }

                    @Override
                    public void close() {
                        // No state.
                    }
                };
            }

            @Override
            public void release(final Source source) {
                released.set(true);
            }
        });

        final var file = new File(tempDir, "sample.xml");
        Files.writeString(file.toPath(), "<a/>", StandardCharsets.UTF_8);

        try (final var fileSource = resolver.resolve(new java.net.URI(file.getName()))) {
            assertTrue(fileSource.isFile());
            assertEquals("text/xml", fileSource.getMimeType());
        }

        final var memoSource = resolver.resolve(new java.net.URI("memo:anything"));
        resolver.release(memoSource);
        assertTrue(released.get(), "Release should be delegated to the scheme-specific resolver");
    }

    @Test
    void testFileSourceAndSourceWrapperDelegateMetadataAndContent() throws Exception {
        final var file = new File(tempDir, "hello.html");
        Files.writeString(file.toPath(), "<p>hello</p>", StandardCharsets.UTF_8);

        final var source = new FileSource(file, "text/html", "UTF-8");
        final var wrapped = new SourceWrapper(source);

        assertTrue(wrapped.exists());
        assertTrue(wrapped.isFile());
        assertTrue(wrapped.isInputStream());
        assertTrue(wrapped.isReader());
        assertEquals(file, wrapped.getFile());
        assertEquals("text/html", wrapped.getMimeType());
        assertEquals("UTF-8", wrapped.getEncoding());
        assertEquals("<p>hello</p>", readString(wrapped.getInputStream()));
        assertEquals("<p>hello</p>", readAll(wrapped.getReader()));
        assertEquals(ValidSourceValidity.SHARED_INSTANCE.getValid(), wrapped.getValidity().getValid());
        wrapped.close();

        try (final var resolved = new FileSourceResolver().resolve(file.toURI())) {
            assertEquals("text/html", resolved.getMimeType());
            assertEquals(file.length(), resolved.getLength());
        }
    }

    @Test
    void testCachedSourceResolverStoresResolvesAndResetsCachedFiles() throws Exception {
        final var cacheDir = new File(tempDir, "cache");
        assertTrue(cacheDir.mkdir());
        final var resolver = new CachedSourceResolver(cacheDir);
        final var file = new File(tempDir, "cached.txt");
        Files.writeString(file.toPath(), "cached-body", StandardCharsets.UTF_8);
        final var source = new FileSource(file, "text/plain", "UTF-8");

        resolver.putSource(source);

        final var resolved = resolver.resolve(file.toURI());
        assertTrue(resolved instanceof CachedSource);
        assertEquals("text/plain", resolved.getMimeType());
        assertEquals("UTF-8", resolved.getEncoding());
        assertEquals("cached-body", readString(resolved.getInputStream()));
        assertEquals(UnknownSourceValidity.SHARED_INSTANCE.getValid(), resolved.getValidity().getValid());

        resolver.release(resolved);
        resolver.reset();
        assertThrows(java.io.FileNotFoundException.class, () -> resolver.resolve(file.toURI()));
    }

    @Test
    void testCachedSourceResolverToKeyNormalizesHttpUris() throws Exception {
        final var uri = new java.net.URI("https://example.com/a%20b?q=x+y%2Bz");
        assertEquals("https://example.com/a b?q=x y+z", CachedSourceResolver.toKey(uri));
    }

    @Test
    void testUrlSourceForFileUrisExposesDetectedMetadataAndCanReopenStreams() throws Exception {
        final var file = new File(tempDir, "page.html");
        Files.writeString(file.toPath(), "<html>hello</html>", StandardCharsets.UTF_8);
        final var source = new URLSource(file.toURI(), null, "UTF-8");

        assertTrue(source.exists());
        assertTrue(source.isFile());
        assertTrue(source.isInputStream());
        assertTrue(source.isReader());
        assertEquals(file, source.getFile());
        assertEquals("text/html", source.getMimeType());
        assertEquals("UTF-8", source.getEncoding());
        assertEquals(file.length(), source.getLength());
        assertEquals("<html>hello</html>", readString(source.getInputStream()));
        assertEquals("<html>hello</html>", readString(source.getInputStream()));
        assertEquals("<html>hello</html>", readAll(source.getReader()));
        assertEquals(SourceValidity.Validity.VALID, source.getValidity().getValid(source.getValidity()));
        source.close();
    }

    @Test
    void testUrlSourceWithoutEncodingRejectsReaderAccess() throws Exception {
        final var file = new File(tempDir, "plain.txt");
        Files.writeString(file.toPath(), "body", StandardCharsets.UTF_8);
        final var source = new URLSource(file.toURI());

        assertFalse(source.isReader());
        assertThrows(UnsupportedOperationException.class, source::getReader);
    }

    @Test
    void testStreamSourceBackedByInputStreamCanBeReadRepeatedly() throws Exception {
        final var source = new StreamSource(new java.net.URI("memo:stream"),
                new java.io.ByteArrayInputStream("stream-body".getBytes(StandardCharsets.UTF_8)), "text/plain",
                "UTF-8", 11);

        assertEquals("memo:stream", source.getURI().toString());
        assertEquals("text/plain", source.getMimeType());
        assertEquals("UTF-8", source.getEncoding());
        assertTrue(source.exists());
        assertTrue(source.isInputStream());
        assertTrue(source.isReader());
        assertEquals("stream-body", readString(source.getInputStream()));
        assertEquals("stream-body", readAll(source.getReader()));
        assertEquals(11L, source.getLength());
        assertEquals(SourceValidity.Validity.UNKNOWN, source.getValidity().getValid());
        assertThrows(UnsupportedOperationException.class, source::getFile);
    }

    @Test
    void testStreamSourceBackedByReaderCanBeResetAndDoesNotExposeInputStream() throws Exception {
        final var source = new StreamSource(new java.net.URI("memo:reader"), new StringReader("reader-body"),
                "text/plain", "UTF-8", 11);

        assertFalse(source.isInputStream());
        assertTrue(source.isReader());
        assertEquals("reader-body", readAll(source.getReader()));
        assertEquals("reader-body", readAll(source.getReader()));
        assertThrows(UnsupportedOperationException.class, source::getInputStream);
    }

    @Test
    void testSimpleSourceMetadataAndUnknownValidityExposeStableValues() throws Exception {
        final var uri = new java.net.URI("memo:meta");
        final var metadata = new SimpleSourceMetadata(uri, "text/plain", "UTF-8", 123);
        final var uriOnlyMetadata = new SimpleSourceMetadata(uri);

        assertEquals(uri, metadata.getURI());
        assertEquals("text/plain", metadata.getMimeType());
        assertEquals("UTF-8", metadata.getEncoding());
        assertEquals(123L, metadata.getLength());

        assertEquals(uri, uriOnlyMetadata.getURI());
        assertEquals(null, uriOnlyMetadata.getMimeType());
        assertEquals(null, uriOnlyMetadata.getEncoding());
        assertEquals(-1L, uriOnlyMetadata.getLength());

        assertEquals(SourceValidity.Validity.UNKNOWN, UnknownSourceValidity.SHARED_INSTANCE.getValid());
        assertEquals(SourceValidity.Validity.UNKNOWN,
                UnknownSourceValidity.SHARED_INSTANCE.getValid(ValidSourceValidity.SHARED_INSTANCE));
    }

    private static String readAll(final java.io.Reader reader) throws Exception {
        try (reader) {
            final var buffer = new StringBuilder();
            final var chars = new char[64];
            int read;
            while ((read = reader.read(chars)) != -1) {
                buffer.append(chars, 0, read);
            }
            return buffer.toString();
        }
    }

    private static String readString(final java.io.InputStream input) throws Exception {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
