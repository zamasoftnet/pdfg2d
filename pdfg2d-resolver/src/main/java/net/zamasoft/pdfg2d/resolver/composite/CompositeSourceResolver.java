package net.zamasoft.pdfg2d.resolver.composite;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.zamasoft.pdfg2d.resolver.Source;
import net.zamasoft.pdfg2d.resolver.SourceResolver;
import net.zamasoft.pdfg2d.resolver.protocol.data.DataSourceResolver;
import net.zamasoft.pdfg2d.resolver.protocol.file.FileSourceResolver;
import net.zamasoft.pdfg2d.resolver.protocol.http.HTTPSourceResolver;
import net.zamasoft.pdfg2d.resolver.protocol.url.URLSourceResolver;

/**
 * A {@link SourceResolver} that aggregates multiple delegate resolvers, each
 * responsible for one or more URI schemes.  When {@link #resolve(URI)} is
 * called the scheme of the supplied URI is used to select the appropriate
 * delegate.  If no delegate is registered for the scheme the
 * {@link #getDefaultSourceResolver() default resolver} is used instead.
 *
 * <p>Scheme look-up is case-insensitive.  URIs without a scheme are treated as
 * belonging to the {@link #getDefaultSchema() default scheme}.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CompositeSourceResolver implements SourceResolver {
	private final Map<String, SourceResolver> schemeToResolver = new HashMap<>();
	private SourceResolver defaultResolver = new URLSourceResolver();
	private String defaultScheme = "file";

	/**
	 * Creates a pre-configured {@code CompositeSourceResolver} that handles the
	 * most common URI schemes:
	 * <ul>
	 *   <li>{@code file} — local file system via {@link FileSourceResolver}</li>
	 *   <li>{@code http} / {@code https} — HTTP(S) via {@link HTTPSourceResolver}
	 *       (silently skipped if the HTTP client library is unavailable)</li>
	 *   <li>{@code data} — inline RFC 2397 data URIs via
	 *       {@link DataSourceResolver}</li>
	 * </ul>
	 *
	 * @return a new {@code CompositeSourceResolver} with the default scheme
	 *         registrations.
	 */
	public static CompositeSourceResolver createGenericCompositeSourceResolver() {
		CompositeSourceResolver resolver = new CompositeSourceResolver();
		resolver.addSourceResolver("file", new FileSourceResolver());
		try {
			HTTPSourceResolver httpSourceResolver = new HTTPSourceResolver();
			resolver.addSourceResolver("http", httpSourceResolver);
			resolver.addSourceResolver("https", httpSourceResolver);
		} catch (Throwable e) {
			// ignore
		}
		resolver.addSourceResolver("data", new DataSourceResolver());
		return resolver;
	}

	/**
	 * Registers a {@link SourceResolver} for the given URI scheme.  Any existing
	 * registration for the same scheme (compared case-insensitively) is replaced.
	 *
	 * @param scheme   the URI scheme to register (e.g. {@code "file"},
	 *                 {@code "http"}); leading/trailing whitespace is ignored.
	 * @param resolver the resolver to associate with the scheme; must not be
	 *                 {@code null}.
	 */
	public void addSourceResolver(String scheme, SourceResolver resolver) {
		this.schemeToResolver.put(scheme.trim().toLowerCase(), resolver);
	}

	/**
	 * Removes the {@link SourceResolver} previously registered for the given
	 * scheme.  If no resolver is registered for the scheme this method is a no-op.
	 *
	 * @param scheme the URI scheme to deregister; leading/trailing whitespace is
	 *               ignored.
	 */
	public void removeSourceResolver(String scheme) {
		this.schemeToResolver.remove(scheme.trim().toLowerCase());
	}

	/**
	 * Returns the {@link SourceResolver} registered for the given scheme, or
	 * {@code null} if none is registered.
	 *
	 * @param scheme the URI scheme to look up; leading/trailing whitespace is
	 *               ignored.
	 * @return the registered resolver, or {@code null}.
	 */
	public SourceResolver getSourceResolver(String scheme) {
		return this.schemeToResolver.get(scheme.trim().toLowerCase());
	}

	/**
	 * Returns an unmodifiable view of the set of URI schemes currently registered
	 * with this resolver.
	 *
	 * @return the set of registered scheme strings.
	 */
	public Collection<String> getSchemata() {
		return this.schemeToResolver.keySet();
	}

	/**
	 * Sets the fallback {@link SourceResolver} used when no scheme-specific
	 * resolver is registered for a URI.
	 *
	 * @param defaultResolver the fallback resolver; may be {@code null} to disable
	 *                        fallback resolution.
	 */
	public void setDefaultSourceResolver(SourceResolver defaultResolver) {
		this.defaultResolver = defaultResolver;
	}

	/**
	 * Returns the fallback {@link SourceResolver} used when no scheme-specific
	 * resolver is registered for a URI.
	 *
	 * @return the fallback resolver, or {@code null} if none is set.
	 */
	public SourceResolver getDefaultSourceResolver() {
		return this.defaultResolver;
	}

	/**
	 * Sets the scheme assumed for URIs that carry no explicit scheme component.
	 * Defaults to {@code "file"}.
	 *
	 * @param defaultScheme the default scheme string; must not be {@code null}.
	 */
	public void setDefaultScheme(String defaultScheme) {
		this.defaultScheme = defaultScheme;
	}

	/**
	 * Returns the scheme assumed for URIs that carry no explicit scheme component.
	 *
	 * @return the default scheme string; never {@code null}.
	 */
	public String getDefaultSchema() {
		return this.defaultScheme;
	}

	/**
	 * Selects the appropriate delegate {@link SourceResolver} for the given URI by
	 * examining its scheme.  Falls back to the default resolver when no
	 * scheme-specific delegate is found.
	 *
	 * @param uri the URI whose scheme is used for look-up; must not be
	 *            {@code null}.
	 * @return the selected resolver; never {@code null}.
	 */
	protected SourceResolver getSourceResolver(URI uri) {
		String scheme = uri.getScheme();
		if (scheme == null) {
			scheme = this.defaultScheme;
		}
		SourceResolver resolver = this.getSourceResolver(scheme);
		if (resolver == null) {
			return this.defaultResolver;
		}
		return resolver;
	}

	@Override
	public Source resolve(URI uri) throws IOException {
		SourceResolver resolver = this.getSourceResolver(uri);
		return resolver.resolve(uri);
	}

	@Override
	public void release(Source source) {
		SourceResolver resolver = this.getSourceResolver(source.getURI());
		if (resolver != null) {
			resolver.release(source);
		}
	}

	@Override
	public String toString() {
		return super.toString() + "[defaultSchema=" + this.defaultScheme
				+ ",defaultResolver=" + this.defaultResolver + ",map="
				+ this.schemeToResolver + "]";
	}
}
