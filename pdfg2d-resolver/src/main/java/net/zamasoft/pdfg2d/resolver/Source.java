package net.zamasoft.pdfg2d.resolver;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * Represents a source of data such as a file, HTTP resource, ZIP entry, or
 * inline data URI. A {@code Source} extends {@link SourceMetadata} to expose
 * descriptive attributes and {@link Closeable} to allow releasing any
 * underlying connections or streams when the caller is finished.
 *
 * <p>Implementations are expected to be lazily connected — that is, network
 * or file handles should not be opened until one of the accessor methods
 * ({@link #getInputStream()}, {@link #getReader()}, etc.) is first called.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface Source extends SourceMetadata, Closeable {
	/**
	 * Returns {@code true} if the resource denoted by this source actually exists.
	 * For file-based sources this checks physical existence; for HTTP sources the
	 * response status code is consulted.
	 *
	 * @return {@code true} if the resource exists; {@code false} otherwise.
	 * @throws IOException if an I/O error occurs while checking for existence.
	 */
	boolean exists() throws IOException;

	/**
	 * Returns {@code true} if the content of this source can be read as a binary
	 * {@link InputStream}.
	 *
	 * @return {@code true} if a binary stream is available.
	 * @throws IOException if an I/O error occurs.
	 */
	boolean isInputStream() throws IOException;

	/**
	 * Opens and returns a binary input stream for the content of this source.
	 * Callers are responsible for closing the returned stream. If a stream was
	 * previously opened it will be closed and replaced by a fresh one.
	 *
	 * @return a new {@link InputStream} positioned at the beginning of the content.
	 * @throws IOException if an I/O error occurs while opening the stream.
	 */
	InputStream getInputStream() throws IOException;

	/**
	 * Returns {@code true} if the content of this source can be read as a character
	 * {@link Reader}. This is typically only possible when the encoding is known.
	 *
	 * @return {@code true} if a character reader is available.
	 * @throws IOException if an I/O error occurs.
	 */
	boolean isReader() throws IOException;

	/**
	 * Opens and returns a character reader for the content of this source decoded
	 * with the character encoding reported by {@link SourceMetadata#getEncoding()}.
	 *
	 * @return a {@link Reader} positioned at the beginning of the content.
	 * @throws UnsupportedOperationException if no character encoding is available
	 *                                        for this source.
	 * @throws IOException                    if an I/O error occurs while opening
	 *                                        the reader.
	 */
	Reader getReader() throws IOException;

	/**
	 * Returns {@code true} if this source is backed by a local {@link File}.
	 *
	 * @return {@code true} if {@link #getFile()} will succeed.
	 * @throws IOException if an I/O error occurs.
	 */
	boolean isFile() throws IOException;

	/**
	 * Returns the local {@link File} that backs this source.
	 *
	 * @return the backing file.
	 * @throws UnsupportedOperationException if this source is not file-backed.
	 */
	File getFile();

	/**
	 * Returns validity information that can be used to determine whether the
	 * content of this source has changed since it was last accessed.
	 *
	 * @return a {@link SourceValidity} instance; never {@code null}.
	 * @throws IOException if an I/O error occurs while determining validity.
	 */
	SourceValidity getValidity() throws IOException;
}
