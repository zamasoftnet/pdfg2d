package net.zamasoft.pdfg2d.resolver.protocol.url;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import net.zamasoft.pdfg2d.resolver.SourceValidity;

/**
 * {@link SourceValidity} implementation for generic URL sources.
 * <p>
 * Each validity check opens a new connection to the URL and compares the
 * server-reported {@code Last-Modified} time against the cached
 * {@code timestamp}.  If the connection fails, the result is
 * {@link net.zamasoft.pdfg2d.resolver.SourceValidity.Validity#UNKNOWN}.
 * </p>
 *
 * @param timestamp the last-modified time recorded when the source was resolved
 * @param url       the URL whose freshness is being tracked
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
record URLSourceValidity(long timestamp, URL url) implements SourceValidity {
	@Override
	public Validity getValid() {
		return checkValidity(this.url);
	}

	@Override
	public Validity getValid(SourceValidity validity) {
		if (validity instanceof URLSourceValidity other) {
			return checkValidity(other.url);
		}
		return Validity.UNKNOWN;
	}

	private Validity checkValidity(final URL u) {
		try {
			if ("file".equals(u.getProtocol())) {
				if (new java.io.File(u.toURI()).lastModified() != this.timestamp) {
					return Validity.INVALID;
				}
				return Validity.VALID;
			}
			final URLConnection conn = u.openConnection();
			if (conn.getLastModified() != this.timestamp) {
				return Validity.INVALID;
			}
			return Validity.VALID;
		} catch (IOException | java.net.URISyntaxException | IllegalArgumentException e) {
			return Validity.UNKNOWN;
		}
	}
}
