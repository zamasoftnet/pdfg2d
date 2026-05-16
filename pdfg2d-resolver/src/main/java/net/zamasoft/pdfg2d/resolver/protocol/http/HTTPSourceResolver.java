package net.zamasoft.pdfg2d.resolver.protocol.http;

import java.io.IOException;
import java.net.URI;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import net.zamasoft.pdfg2d.resolver.Source;
import net.zamasoft.pdfg2d.resolver.SourceResolver;

/**
 * A {@link SourceResolver} implementation that handles {@code http:} and
 * {@code https:} URIs by retrieving data via Apache HttpClient.
 *
 * <p>A single pooled {@link CloseableHttpClient} is created lazily and shared
 * across all {@link HTTPSource} instances produced by this resolver.  The pool
 * allows a maximum of 20 total connections and 2 connections per route.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class HTTPSourceResolver implements SourceResolver {
	private CloseableHttpClient sharedClient;

	/**
	 * Returns the shared {@link CloseableHttpClient}, creating it on the first
	 * call.  The client uses a {@link PoolingHttpClientConnectionManager} with a
	 * maximum of 20 total connections and 2 per route.
	 *
	 * @return the shared HTTP client; never {@code null}.
	 */
	protected synchronized CloseableHttpClient getSharedClient() {
		if (this.sharedClient == null) {
			final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
			cm.setMaxTotal(20);
			cm.setDefaultMaxPerRoute(2);
			this.sharedClient = HttpClientBuilder.create().setConnectionManager(cm).build();
		}
		return this.sharedClient;
	}

	/**
	 * Resolves the given HTTP or HTTPS URI by creating an {@link HTTPSource} that
	 * uses the shared HTTP client.  The client is not closed when the source is
	 * released; it remains available for subsequent requests.
	 *
	 * @param uri an {@code http:} or {@code https:} URI; must not be {@code null}.
	 * @return a new {@link HTTPSource} for the resource.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public Source resolve(final URI uri) throws IOException {
		return new HTTPSource(uri, this.getSharedClient(), false);
	}

	/**
	 * Releases the given source by closing its connection.  The shared HTTP client
	 * itself is not affected.
	 *
	 * @param source the source to release; must be an {@link HTTPSource} obtained
	 *               from this resolver.
	 */
	@Override
	public void release(final Source source) {
		if (source instanceof final HTTPSource httpSource) {
			httpSource.close();
		}
	}
}
