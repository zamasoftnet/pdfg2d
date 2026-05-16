package net.zamasoft.pdfg2d.resolver.protocol.data;

import java.io.IOException;
import java.net.URI;
import net.zamasoft.pdfg2d.resolver.Source;
import net.zamasoft.pdfg2d.resolver.SourceResolver;

/**
 * A {@link SourceResolver} implementation that handles
 * <a href="https://datatracker.ietf.org/doc/html/rfc2397">RFC 2397</a>
 * {@code data:} URIs by constructing a {@link DataSource} for each resolved
 * URI.
 *
 * <p>Because {@code data:} URIs are self-contained, this resolver has no
 * external dependencies and no shared state.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class DataSourceResolver implements SourceResolver {
	/**
	 * Resolves the given {@code data:} URI by wrapping it in a {@link DataSource}.
	 *
	 * @param uri a {@code data:} scheme URI; must not be {@code null}.
	 * @return a new {@link DataSource} for the URI.
	 * @throws IOException if an I/O error occurs (in practice this should not
	 *                     happen as parsing is deferred to the first access).
	 */
	@Override
	public Source resolve(URI uri) throws IOException {
		return new DataSource(uri);
	}

	/**
	 * Releases the given source by closing it.  Any {@link IOException} thrown
	 * during close is silently ignored.
	 *
	 * @param source the source to release; must be a {@link DataSource} obtained
	 *               from this resolver.
	 */
	@Override
	public void release(Source source) {
		if (source instanceof DataSource dataSource) {
			try {
				dataSource.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}
}
