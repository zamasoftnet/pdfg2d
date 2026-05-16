package net.zamasoft.pdfg2d.resolver.protocol.http;

import net.zamasoft.pdfg2d.resolver.SourceValidity;

/**
 * {@link SourceValidity} implementation for HTTP/HTTPS sources.
 * <p>
 * Validity can only be checked when two {@code HTTPSourceValidity} instances
 * are compared.  If the server did not report a {@code Last-Modified} header
 * ({@code lastModified == -1}), the result is always
 * {@link net.zamasoft.pdfg2d.resolver.SourceValidity.Validity#UNKNOWN}.
 * </p>
 *
 * @param lastModified the {@code Last-Modified} timestamp in milliseconds
 *                     since the Unix epoch, or {@code -1} if not available
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
record HTTPSourceValidity(long lastModified) implements SourceValidity {
	@Override
	public Validity getValid() {
		return Validity.UNKNOWN;
	}

	@Override
	public Validity getValid(final SourceValidity validity) {
		if (this.lastModified == -1) {
			return Validity.UNKNOWN;
		}
		if (validity instanceof HTTPSourceValidity other) {
			return this.lastModified == other.lastModified ? Validity.VALID : Validity.INVALID;
		}
		return Validity.UNKNOWN;
	}
}
