package net.zamasoft.pdfg2d.resolver.protocol.stream;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Objects;
import net.zamasoft.pdfg2d.resolver.SourceValidity;
import net.zamasoft.pdfg2d.resolver.util.AbstractSource;
import net.zamasoft.pdfg2d.resolver.util.UnknownSourceValidity;

/**
 * A {@link net.zamasoft.pdfg2d.resolver.Source Source} implementation that
 * wraps an existing {@link InputStream} or {@link Reader}.
 *
 * <p>The underlying stream or reader is wrapped in a buffered version that
 * supports {@link java.io.InputStream#mark(int) mark}/{@link java.io.InputStream#reset() reset}
 * up to {@value #MARK_LIMIT} bytes, so that {@link #getInputStream()} and
 * {@link #getReader()} can be called multiple times on the same source.
 *
 * <p>The source never closes the underlying stream — close operations on the
 * internal buffered wrapper are silently ignored.  Validity is always
 * {@link net.zamasoft.pdfg2d.resolver.util.UnknownSourceValidity#SHARED_INSTANCE UNKNOWN}
 * because a stream has no stable modification-time concept.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class StreamSource extends AbstractSource {
	private static final int MARK_LIMIT = 8192;

	private final String mimeType;
	private final String encoding;
	private final BufferedInputStream in;
	private final BufferedReader reader;
	private final long length;

	/**
	 * Constructs a {@code StreamSource} backed by an {@link InputStream} with all
	 * attributes specified explicitly.
	 *
	 * @param uri      the URI to associate with this source; must not be
	 *                 {@code null}.
	 * @param in       the input stream to wrap; must not be {@code null}.
	 * @param mimeType the MIME type of the content, or {@code null} if unknown.
	 * @param encoding the character encoding, or {@code null} if binary.
	 * @param length   the content length in bytes, or {@code -1} if unknown.
	 * @throws UnsupportedEncodingException if the encoding is not supported.
	 */
	public StreamSource(final URI uri, final InputStream in, final String mimeType, final String encoding,
			final long length)
			throws UnsupportedEncodingException {
		super(uri);
		Objects.requireNonNull(in, "InputStream must not be null");
		this.mimeType = mimeType;
		this.encoding = encoding;
		this.in = new BufferedInputStream(in) {
			@Override
			public void close() throws IOException {
				// Ignore close to prevent closing the underlying stream unexpectedly
			}
		};
		this.in.mark(MARK_LIMIT);
		this.reader = null;
		this.length = length;
	}

	/**
	 * Constructs a {@code StreamSource} backed by an {@link InputStream} without
	 * a character encoding.
	 *
	 * @param uri      the URI to associate with this source; must not be
	 *                 {@code null}.
	 * @param in       the input stream to wrap; must not be {@code null}.
	 * @param mimeType the MIME type of the content, or {@code null} if unknown.
	 * @param length   the content length in bytes, or {@code -1} if unknown.
	 */
	public StreamSource(final URI uri, final InputStream in, final String mimeType, final long length) {
		super(uri);
		Objects.requireNonNull(in, "InputStream must not be null");
		this.mimeType = mimeType;
		this.in = new BufferedInputStream(in) {
			@Override
			public void close() throws IOException {
				// ignore
			}
		};
		this.in.mark(MARK_LIMIT);
		this.encoding = null;
		this.reader = null;
		this.length = length;
	}

	/**
	 * Constructs a {@code StreamSource} backed by a {@link Reader} with all
	 * attributes specified explicitly.
	 *
	 * @param uri      the URI to associate with this source; must not be
	 *                 {@code null}.
	 * @param reader   the character reader to wrap; must not be {@code null}.
	 * @param mimeType the MIME type of the content, or {@code null} if unknown.
	 * @param encoding the declared character encoding, or {@code null} if unknown.
	 * @param length   the content length in bytes, or {@code -1} if unknown.
	 * @throws IOException if an I/O error occurs while marking the reader.
	 */
	public StreamSource(final URI uri, final Reader reader, final String mimeType, final String encoding,
			final long length) throws IOException {
		super(uri);
		Objects.requireNonNull(reader, "Reader must not be null");
		this.mimeType = mimeType;
		this.in = null;
		this.encoding = encoding;
		this.reader = new BufferedReader(reader) {
			@Override
			public void close() throws IOException {
				// ignore
			}
		};
		this.reader.mark(MARK_LIMIT);
		this.length = length;
	}

	/**
	 * Constructs a {@code StreamSource} backed by an {@link InputStream} with an
	 * unknown content length.
	 *
	 * @param uri      the URI to associate with this source; must not be
	 *                 {@code null}.
	 * @param in       the input stream to wrap; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} if unknown.
	 * @param encoding the character encoding, or {@code null} if binary.
	 * @throws UnsupportedEncodingException if the encoding is not supported.
	 */
	public StreamSource(final URI uri, final InputStream in, final String mimeType, final String encoding)
			throws UnsupportedEncodingException {
		this(uri, in, mimeType, encoding, -1L);
	}

	/**
	 * Constructs a {@code StreamSource} backed by an {@link InputStream} with a
	 * known MIME type but no character encoding and unknown length.
	 *
	 * @param uri      the URI to associate with this source; must not be
	 *                 {@code null}.
	 * @param in       the input stream to wrap; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} if unknown.
	 */
	public StreamSource(final URI uri, final InputStream in, final String mimeType) {
		this(uri, in, mimeType, -1L);
	}

	/**
	 * Constructs a {@code StreamSource} backed by an {@link InputStream} with no
	 * metadata.
	 *
	 * @param uri the URI to associate with this source; must not be {@code null}.
	 * @param in  the input stream to wrap; must not be {@code null}.
	 */
	public StreamSource(final URI uri, final InputStream in) {
		this(uri, in, null, -1L);
	}

	/**
	 * Constructs a {@code StreamSource} backed by a {@link Reader} with an unknown
	 * content length.
	 *
	 * @param uri      the URI to associate with this source; must not be
	 *                 {@code null}.
	 * @param reader   the character reader to wrap; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} if unknown.
	 * @param encoding the declared character encoding, or {@code null} if unknown.
	 * @throws IOException if an I/O error occurs while marking the reader.
	 */
	public StreamSource(final URI uri, final Reader reader, final String mimeType, final String encoding)
			throws IOException {
		this(uri, reader, mimeType, encoding, -1L);
	}

	/**
	 * Constructs a {@code StreamSource} backed by a {@link Reader} with a known
	 * MIME type and unknown length.
	 *
	 * @param uri      the URI to associate with this source; must not be
	 *                 {@code null}.
	 * @param reader   the character reader to wrap; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} if unknown.
	 * @throws IOException if an I/O error occurs while marking the reader.
	 */
	public StreamSource(final URI uri, final Reader reader, final String mimeType) throws IOException {
		this(uri, reader, mimeType, null, -1L);
	}

	/**
	 * Constructs a {@code StreamSource} backed by a {@link Reader} with no
	 * metadata.
	 *
	 * @param uri    the URI to associate with this source; must not be
	 *               {@code null}.
	 * @param reader the character reader to wrap; must not be {@code null}.
	 * @throws IOException if an I/O error occurs while marking the reader.
	 */
	public StreamSource(final URI uri, final Reader reader) throws IOException {
		this(uri, reader, null, null, -1L);
	}

	/**
	 * Returns the URI associated with this source.
	 *
	 * @return the URI; never {@code null}.
	 */
	@Override
	public URI getURI() {
		return this.uri;
	}

	/**
	 * Returns the MIME type supplied at construction time.
	 *
	 * @return the MIME type string, or {@code null} if unknown.
	 */
	@Override
	public String getMimeType() {
		return this.mimeType;
	}

	/**
	 * Always returns {@code true} — a stream-based source always has content.
	 *
	 * @return {@code true}.
	 */
	@Override
	public boolean exists() {
		return true;
	}

	/**
	 * Returns the character encoding supplied at construction time.
	 *
	 * @return the encoding name, or {@code null} if unknown or binary.
	 */
	@Override
	public String getEncoding() {
		return this.encoding;
	}

	/**
	 * Returns {@code true} if this source was constructed with an
	 * {@link InputStream}.
	 *
	 * @return {@code true} if a binary stream is available.
	 */
	@Override
	public boolean isInputStream() {
		return this.in != null;
	}

	/**
	 * Returns {@code true} if this source was constructed with a {@link Reader}
	 * or if a character encoding is known (so a reader can be derived from the
	 * input stream).
	 *
	 * @return {@code true} if a character reader is available.
	 */
	@Override
	public boolean isReader() {
		return this.reader != null || this.encoding != null;
	}

	/**
	 * Returns the buffered {@link InputStream} for this source, reset to the
	 * beginning.  The stream supports repeated reads up to {@value #MARK_LIMIT}
	 * bytes from its initial position.
	 *
	 * @return the reusable input stream; never {@code null}.
	 * @throws UnsupportedOperationException if this source was constructed with a
	 *                                        {@link Reader} rather than a stream.
	 * @throws IOException if an I/O error occurs while resetting the stream.
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		if (this.in == null) {
			throw new UnsupportedOperationException();
		}
		this.in.reset();
		this.in.mark(MARK_LIMIT);
		return this.in;
	}

	/**
	 * Returns a character {@link Reader} for this source.  If this source was
	 * constructed with a {@link Reader}, that reader is reset and returned;
	 * otherwise the buffered stream is wrapped with the known encoding.
	 *
	 * @return a character reader positioned at the beginning of the content.
	 * @throws UnsupportedOperationException if no {@link Reader} was provided and
	 *                                        no character encoding is known.
	 * @throws IOException if an I/O error occurs while resetting the reader.
	 */
	@Override
	public Reader getReader() throws IOException {
		if (this.reader == null) {
			if (this.encoding == null) {
				throw new UnsupportedOperationException();
			}
			return new InputStreamReader(this.getInputStream(), this.encoding);
		}
		this.reader.reset();
		this.reader.mark(MARK_LIMIT);
		return this.reader;
	}

	/**
	 * Always throws {@link UnsupportedOperationException} — stream-based sources
	 * are not backed by a local file.
	 *
	 * @throws UnsupportedOperationException always.
	 */
	@Override
	public File getFile() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns the content length supplied at construction time.
	 *
	 * @return the content length in bytes, or {@code -1} if unknown.
	 * @throws IOException never thrown.
	 */
	@Override
	public long getLength() throws IOException {
		return this.length;
	}

	/**
	 * Returns {@link UnknownSourceValidity#SHARED_INSTANCE} because a stream has
	 * no stable modification-time concept.
	 *
	 * @return the shared unknown-validity instance; never {@code null}.
	 */
	@Override
	public SourceValidity getValidity() {
		return UnknownSourceValidity.SHARED_INSTANCE;
	}
}
