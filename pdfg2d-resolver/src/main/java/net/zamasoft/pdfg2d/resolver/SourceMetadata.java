package net.zamasoft.pdfg2d.resolver;

import java.io.IOException;
import java.net.URI;

/**
 * Carries descriptive metadata about a data source without opening it.
 * Implementations may compute values lazily (e.g. by inspecting HTTP response
 * headers after connecting), hence the checked {@link IOException} on each
 * accessor.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface SourceMetadata {
	/**
	 * Returns the canonical URI that identifies this source.
	 *
	 * @return the URI of the source; never {@code null}.
	 */
	URI getURI();

	/**
	 * Returns the MIME type of the source content, such as {@code "text/html"} or
	 * {@code "application/pdf"}.
	 *
	 * @return the MIME type string, or {@code null} if it cannot be determined.
	 * @throws IOException if an I/O error occurs while determining the type.
	 */
	String getMimeType() throws IOException;

	/**
	 * Returns the character encoding (charset name) of the source content, such as
	 * {@code "UTF-8"} or {@code "ISO-8859-1"}.
	 *
	 * @return the encoding name, or {@code null} if the encoding is unknown or the
	 *         content is binary.
	 * @throws IOException if an I/O error occurs while determining the encoding.
	 */
	String getEncoding() throws IOException;

	/**
	 * Returns the byte length of the source content.
	 *
	 * @return the number of bytes, or {@code -1} if the length is unknown.
	 * @throws IOException if an I/O error occurs while determining the length.
	 */
	long getLength() throws IOException;
}
