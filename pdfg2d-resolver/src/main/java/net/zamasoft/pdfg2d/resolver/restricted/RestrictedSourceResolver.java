package net.zamasoft.pdfg2d.resolver.restricted;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.zamasoft.pdfg2d.resolver.Source;
import net.zamasoft.pdfg2d.resolver.SourceResolver;

/**
 * A {@link SourceResolver} decorator that enforces URI-based access control
 * using wildcard patterns.
 *
 * <p>Patterns are evaluated in the order they were added via {@link #include}
 * and {@link #exclude}.  The first pattern that matches the request URI
 * determines whether access is granted or denied.  If no pattern matches, and
 * the scheme is not {@code data:}, access is denied by default.
 *
 * <p>{@code data:} URIs are always permitted regardless of the ACL because
 * they carry their content inline and cannot be used for server-side request
 * forgery.
 *
 * <p>The URI is normalised and {@code %xx} sequences in HTTP(S) URIs are
 * partially decoded before pattern matching so that the same resource cannot
 * be addressed by two differently-encoded forms.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class RestrictedSourceResolver implements SourceResolver {
	private SourceResolver enclosedSourceResolver;
	private final List<Pattern> acl = new ArrayList<>();

	/**
	 * Internal record pairing an access-control decision with the compiled
	 * wildcard pattern it applies to.
	 *
	 * @param permit  {@code true} for an include/allow rule; {@code false} for
	 *                an exclude/deny rule.
	 * @param pattern the compiled wildcard pattern produced by
	 *                {@link WildcardHelper#compilePattern(String)}.
	 */
	protected record Pattern(boolean permit, int[] pattern) {
	}

	/**
	 * Constructs a {@code RestrictedSourceResolver} wrapping the given delegate.
	 *
	 * @param enclosedSourceResolver the delegate resolver to forward permitted
	 *                               requests to, or {@code null} if no delegate
	 *                               has been set yet.
	 */
	public RestrictedSourceResolver(SourceResolver enclosedSourceResolver) {
		this.enclosedSourceResolver = enclosedSourceResolver;
	}

	/**
	 * Constructs a {@code RestrictedSourceResolver} with no delegate.  The
	 * delegate must be set via
	 * {@link #setEnclosedSourceResolver(SourceResolver)} before use.
	 */
	public RestrictedSourceResolver() {
		this(null);
	}

	private static char convertHexDigit(char b) {
		if ((b >= '0') && (b <= '9'))
			return (char) (b - '0');
		if ((b >= 'a') && (b <= 'f'))
			return (char) (b - 'a' + 10);
		if ((b >= 'A') && (b <= 'F'))
			return (char) (b - 'A' + 10);
		return 0;
	}

	/**
	 * Converts the given URI to a normalised string key used for pattern matching.
	 * For {@code http:} and {@code https:} URIs, safe percent-encoded characters
	 * are decoded in place (except for {@code *}, {@code ?}, {@code #} and
	 * delimiter characters in the query string) to prevent encoding-based bypass
	 * attempts.
	 *
	 * @param uri the URI to convert; must not be {@code null}.
	 * @return the normalised key string; never {@code null}.
	 */
	public static String toKey(URI uri) {
		String key = uri.toString();
		String scheme = uri.getScheme();
		if (scheme != null && (scheme.equals("http") || scheme.equals("https"))) {
			String exclude = "*?#";
			char[] ch = key.toCharArray();
			int len = ch.length;
			int ix = 0;
			int ox = 0;
			while (ix < len) {
				char b = ch[ix++];
				if (b == '?') {
					exclude = "*&=#";
				} else if (b == '+') {
					b = ' ';
				} else if (b == '%') {
					if (ix + 1 < len) {
						char c = (char) ((convertHexDigit(ch[ix]) << 4) + convertHexDigit(ch[ix + 1]));
						if (exclude.indexOf(c) == -1) {
							b = c;
							ix += 2;
						} else {
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
	 * Adds a permit (allow) rule for URIs matching the given wildcard pattern.
	 * The pattern is normalised before compilation, and {@code *} is interpreted
	 * as a single-segment wildcard.
	 *
	 * @param uriPattern the URI pattern to allow; must not be {@code null}.
	 */
	public void include(URI uriPattern) {
		String key = toKey(uriPattern.normalize());
		this.acl.add(new Pattern(true, WildcardHelper.compilePattern(key)));
	}

	/**
	 * Adds a deny rule for URIs matching the given wildcard pattern.
	 *
	 * @param uriPattern the URI pattern to deny; must not be {@code null}.
	 */
	public void exclude(URI uriPattern) {
		String key = toKey(uriPattern.normalize());
		this.acl.add(new Pattern(false, WildcardHelper.compilePattern(key)));
	}

	/**
	 * Resolves the given URI after checking the ACL.  Equivalent to calling
	 * {@link #resolve(URI, boolean) resolve(uri, false)}.
	 *
	 * @param uri the URI to resolve; must not be {@code null}.
	 * @return a {@link Source} for the resource.
	 * @throws SecurityException     if the URI is denied by the ACL.
	 * @throws FileNotFoundException if no delegate resolver is configured.
	 * @throws IOException           if an I/O error occurs.
	 */
	@Override
	public Source resolve(URI uri) throws IOException {
		return this.resolve(uri, false);
	}

	/**
	 * Resolves the given URI, optionally bypassing the ACL check.
	 *
	 * @param uri   the URI to resolve; must not be {@code null}.
	 * @param force {@code true} to skip ACL evaluation and delegate directly.
	 * @return a {@link Source} for the resource.
	 * @throws SecurityException     if {@code force} is {@code false} and the URI
	 *                               is denied by the ACL.
	 * @throws FileNotFoundException if no delegate resolver is configured.
	 * @throws IOException           if an I/O error occurs.
	 */
	public Source resolve(URI uri, boolean force) throws IOException {
		uri = uri.normalize();
		String key = toKey(uri);
		key = key.replaceAll("\\*", "%2A");
		if (force) {
			if (this.enclosedSourceResolver == null) {
				throw new FileNotFoundException(key);
			}
			return this.enclosedSourceResolver.resolve(uri);
		}
		for (Pattern pattern : this.acl) {
			if (WildcardHelper.match(key, pattern.pattern)) {
				if (pattern.permit) {
					if (this.enclosedSourceResolver == null) {
						throw new FileNotFoundException(key);
					}
					return this.enclosedSourceResolver.resolve(uri);
				}
				throw new SecurityException("Access denied: " + key);
			}
		}
		if ("data".equals(uri.getScheme())) {
			if (this.enclosedSourceResolver == null) {
				throw new FileNotFoundException(key);
			}
			return this.enclosedSourceResolver.resolve(uri);
		}
		throw new SecurityException("Access denied: " + key);
	}

	/**
	 * Delegates release of the source to the enclosed resolver, if one is set.
	 *
	 * @param source the source to release; must not be {@code null}.
	 */
	@Override
	public void release(Source source) {
		if (this.enclosedSourceResolver != null) {
			this.enclosedSourceResolver.release(source);
		}
	}

	/**
	 * Returns the delegate {@link SourceResolver} to which permitted requests are
	 * forwarded.
	 *
	 * @return the enclosed resolver, or {@code null} if none has been set.
	 */
	public SourceResolver getEnclosedSourceResolver() {
		return this.enclosedSourceResolver;
	}

	/**
	 * Sets the delegate {@link SourceResolver} to which permitted requests are
	 * forwarded.
	 *
	 * @param enclosedSourceResolver the delegate resolver, or {@code null} to
	 *                               clear it.
	 */
	public void setEnclosedSourceResolver(SourceResolver enclosedSourceResolver) {
		this.enclosedSourceResolver = enclosedSourceResolver;
	}

	/**
	 * Clears both the enclosed resolver and all ACL patterns, restoring this
	 * instance to its initial state.
	 */
	public void reset() {
		this.enclosedSourceResolver = null;
		this.acl.clear();
	}
}

