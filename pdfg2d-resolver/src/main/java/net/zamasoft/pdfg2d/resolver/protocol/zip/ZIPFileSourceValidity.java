package net.zamasoft.pdfg2d.resolver.protocol.zip;

import java.io.File;
import net.zamasoft.pdfg2d.resolver.SourceValidity;

/**
 * {@link SourceValidity} implementation for sources residing inside a ZIP
 * archive.
 * <p>
 * Validity is determined by comparing the ZIP file's last-modified timestamp
 * recorded at resolution time ({@code timestamp}) with its current
 * last-modified value.  Any change to the archive file invalidates the entry.
 * </p>
 *
 * @param timestamp the last-modified time of the ZIP file when the source was
 *                  resolved
 * @param file      the ZIP archive file
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
record ZIPFileSourceValidity(long timestamp, File file) implements SourceValidity {
	@Override
	public Validity getValid() {
		return checkValidity(this.file);
	}

	@Override
	public Validity getValid(SourceValidity validity) {
		if (validity instanceof ZIPFileSourceValidity other) {
			return checkValidity(other.file);
		}
		return Validity.UNKNOWN;
	}

	private Validity checkValidity(File f) {
		if (f.lastModified() != this.timestamp) {
			return Validity.INVALID;
		}
		return Validity.VALID;
	}
}
