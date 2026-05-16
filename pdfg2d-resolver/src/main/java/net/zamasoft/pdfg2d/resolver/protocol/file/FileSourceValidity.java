package net.zamasoft.pdfg2d.resolver.protocol.file;

import java.io.File;
import net.zamasoft.pdfg2d.resolver.SourceValidity;

/**
 * {@link SourceValidity} implementation for local file sources.
 * <p>
 * Validity is determined by comparing the file's last-modified timestamp at
 * the time this record was created ({@code timestamp}) with the current
 * last-modified value of {@code file}.
 * </p>
 *
 * @param timestamp the last-modified time recorded when the source was resolved
 * @param file      the file whose freshness is being tracked
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
record FileSourceValidity(long timestamp, File file) implements SourceValidity {
	@Override
	public Validity getValid() {
		return this.checkValidity(this.file);
	}

	@Override
	public Validity getValid(final SourceValidity validity) {
		if (validity instanceof final FileSourceValidity other) {
			return this.checkValidity(other.file);
		}
		return Validity.UNKNOWN;
	}

	private Validity checkValidity(final File f) {
		if (f.lastModified() != this.timestamp) {
			return Validity.INVALID;
		}
		return Validity.VALID;
	}
}
