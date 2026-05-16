package net.zamasoft.pdfg2d.resolver.protocol.url;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import net.zamasoft.pdfg2d.resolver.Source;
import net.zamasoft.pdfg2d.resolver.SourceResolver;

/**
 * A {@link SourceResolver} implementation that resolves any URI that can be
 * converted to a {@link java.net.URL} using the standard Java URL handling
 * mechanism.
 *
 * <p>This resolver acts as a generic fallback and delegates connection
 * management to the JVM's built-in URL infrastructure ({@link java.net.URLConnection}).
 * It is typically used as the {@link CompositeSourceResolver#getDefaultSourceResolver()
 * default resolver} in a composite configuration.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class URLSourceResolver implements SourceResolver {
	/**
	 * Resolves the given URI by converting it to a {@link java.net.URL} and
	 * wrapping it in a {@link URLSource}.
	 *
	 * @param uri the URI to resolve; must not be {@code null} and must be
	 *            convertible to a URL.
	 * @return a new {@link URLSource} for the resource.
	 * @throws IOException if the URI cannot be converted to a URL or an I/O error
	 *                     occurs.
	 */
	@Override
	public Source resolve(final URI uri) throws IOException {
		try {
			final URL url = uri.toURL();
			try {
				return new URLSource(url, null, null);
			} catch (URISyntaxException e) {
				throw new IOException(e);
			}
		} catch (IllegalArgumentException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Releases the given source by closing its underlying URL connection.
	 *
	 * @param source the source to release; must be a {@link URLSource} obtained
	 *               from this resolver.
	 */
	@Override
	public void release(final Source source) {
		if (source instanceof final URLSource urlSource) {
			urlSource.close();
		}
	}
}
