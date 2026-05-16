package net.zamasoft.pdfg2d.resolver.cache;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import net.zamasoft.pdfg2d.resolver.SourceMetadata;
import net.zamasoft.pdfg2d.resolver.Source;
import net.zamasoft.pdfg2d.resolver.SourceResolver;

/**
 * A {@link SourceResolver} that stores remote or computed resources as
 * temporary files so that they can be resolved repeatedly without re-fetching.
 *
 * <p>Each cached resource is keyed by a normalised URI string.  Callers first
 * populate the cache via {@link #putFile(SourceMetadata)} or
 * {@link #putSource(Source)}, after which {@link #resolve(URI)} will return a
 * {@link CachedSource} wrapping the corresponding temporary file.
 *
 * <p>Call {@link #reset()} to delete all cached files and clear the index, or
 * {@link #dispose()} (which delegates to {@link #reset()}) when the resolver is
 * no longer needed.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CachedSourceResolver implements SourceResolver {
	/**
	 * Internal record that associates a canonical URI with its cached file and
	 * content-type metadata.
	 *
	 * @param uri      the normalised URI of the original resource.
	 * @param mimeType the MIME type of the cached content, or {@code null}.
	 * @param encoding the character encoding of the cached content, or {@code null}.
	 * @param file     the temporary file holding the cached bytes.
	 */
	protected record CachedSourceInfo(URI uri, String mimeType, String encoding, File file) {
	}

	private final Map<String, CachedSourceInfo> uriToSource = new HashMap<>();
	private final File tmpDir;

	/**
	 * Constructs a resolver that writes temporary files to the given directory.
	 *
	 * @param tmpDir the directory for temporary cache files, or {@code null} to
	 *               use the JVM default temporary directory.
	 */
	public CachedSourceResolver(final File tmpDir) {
		this.tmpDir = tmpDir;
	}

	/**
	 * Constructs a resolver that writes temporary files to the JVM default
	 * temporary directory.
	 */
	public CachedSourceResolver() {
		this(null);
	}

	private static char convertHexDigit(final char b) {
		if ((b >= '0') && (b <= '9')) {
			return (char) (b - '0');
		}
		if ((b >= 'a') && (b <= 'f')) {
			return (char) (b - 'a' + 10);
		}
		if ((b >= 'A') && (b <= 'F')) {
			return (char) (b - 'A' + 10);
		}
		return 0;
	}

	/**
	 * Converts a URI to a normalised cache key string, selectively URL-decoding
	 * characters that are safe to compare literally for HTTP(S) URIs.
	 *
	 * @param uri the URI to convert; must not be {@code null}.
	 * @return a string key suitable for use as a cache map key.
	 */
	public static String toKey(final URI uri) {
		var key = uri.toString();
		final var scheme = uri.getScheme();
		if (scheme == null) {
			return key;
		}
		if (scheme.equals("http") || scheme.equals("https")) {
			// Decode except ?&=#
			var exclude = "?#";
			final var ch = key.toCharArray();
			final int len = ch.length;
			var ix = 0;
			var ox = 0;
			while (ix < len) {
				var b = ch[ix++];
				if (b == '?') {
					exclude = "&=#";
				} else if (b == '+') {
					b = ' ';
				} else if (b == '%') {
					if (ix + 1 < len) {
						final var c = (char) ((convertHexDigit(ch[ix]) << 4) + convertHexDigit(ch[ix + 1]));
						if (exclude.indexOf(c) == -1) {
							b = c;
							ix += 2;
						} else {
							// Normalize uppercase
							ch[ix] = Character.toUpperCase(ch[ix]);
							ch[ix + 1] = Character.toUpperCase(ch[ix + 1]);
						}
					}
				}
				ch[ox++] = b;
			}
			key = new String(ch, 0, ox);
		}
		return key;
	}

	/**
	 * Registers the given {@link SourceMetadata} in the cache index and returns a
	 * newly created empty temporary file that the caller should populate with the
	 * resource bytes. If a previous entry exists for the same URI its file is
	 * deleted before the new one is created.
	 *
	 * @param sourceMetadata metadata of the resource to cache; must not be
	 *                        {@code null}.
	 * @return a newly created, empty temporary file ready to receive the content.
	 * @throws IOException if a temporary file cannot be created, or if reading the
	 *                     metadata causes an I/O error.
	 */
	public File putFile(final SourceMetadata sourceMetadata) throws IOException {
		final var uri = sourceMetadata.getURI().normalize();
		final var key = toKey(uri);
		var info = this.uriToSource.get(key);
		if (info != null) {
			if (!info.file.delete()) {
				// log warning?
			}
		}

		final var mimeType = sourceMetadata.getMimeType();
		final var encoding = sourceMetadata.getEncoding();
		final var file = File.createTempFile("cssj-cache-", ".dat", this.tmpDir);
		file.deleteOnExit();
		info = new CachedSourceInfo(uri, mimeType, encoding, file);
		this.uriToSource.put(key, info);
		return file;
	}

	/**
	 * Copies the content of {@code source} into a temporary cache file and
	 * registers it in the index.  The source's input stream is consumed exactly
	 * once; the source itself is not closed by this method.
	 *
	 * @param source the source whose content should be cached; must not be
	 *               {@code null} and must support {@link Source#getInputStream()}.
	 * @throws IOException if reading from the source or writing to the cache file
	 *                     fails.
	 */
	public void putSource(final Source source) throws IOException {
		final var file = this.putFile(source);
		try (final var in = source.getInputStream(); final var out = new FileOutputStream(file)) {
			IOUtils.copy(in, out);
		}
	}

	@Override
	public Source resolve(URI uri) throws IOException {
		uri = uri.normalize();
		String key = toKey(uri);
		CachedSourceInfo info = this.uriToSource.get(key);
		if (info != null) {
			return new CachedSource(info.uri, info.mimeType, info.encoding, info.file);
		}
		throw new FileNotFoundException(uri.toString());
	}

	@Override
	public void release(Source source) {
		if (source instanceof CachedSource cachedSource) {
			cachedSource.close();
		}
	}

	/**
	 * Deletes all temporary cache files and clears the URI index.
	 */
	public void reset() {
		for (CachedSourceInfo info : this.uriToSource.values()) {
			if (!info.file.delete()) {
				// ignore
			}
		}
		this.uriToSource.clear();
	}

	/**
	 * Disposes of this resolver by clearing the cache.  Equivalent to calling
	 * {@link #reset()}.
	 */
	public void dispose() {
		this.reset();
	}
}
