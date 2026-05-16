package net.zamasoft.pdfg2d.resolver.protocol.url;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.zamasoft.pdfg2d.resolver.SourceValidity;
import net.zamasoft.pdfg2d.resolver.util.AbstractSource;

/**
 * A {@link net.zamasoft.pdfg2d.resolver.Source Source} implementation that
 * retrieves data from a {@link java.net.URL}.
 *
 * <p>If the URL uses the {@code file} scheme the content is read directly from
 * the file system; for all other schemes a {@link java.net.URLConnection} is
 * opened lazily on the first content or metadata access.
 *
 * <p>The connection is re-established automatically each time
 * {@link #getInputStream()} is called so that the stream is always positioned
 * at the beginning of the content.  Validity is based on the connection's
 * {@code Last-Modified} timestamp.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class URLSource extends AbstractSource {
	private static final Logger LOG = Logger.getLogger(URLSource.class.getName());

	private final URL url;
	private final String encoding;
	private String mimeType = null;

	// Transient in case of serialization, though this class doesn't explicitly
	// implement Serializable.
	private transient URLConnection conn = null;
	private transient InputStream in = null;
	private long timestamp = -1L;

	/**
	 * Constructs a {@code URLSource} with all attributes specified explicitly.
	 *
	 * @param uri      the URI to associate with this source; must not be
	 *                 {@code null}.
	 * @param url      the backing URL; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 * @param encoding the character encoding, or {@code null} if unknown.
	 */
	public URLSource(final URI uri, final URL url, final String mimeType, final String encoding) {
		super(uri);
		this.url = Objects.requireNonNull(url, "URL must not be null");
		this.mimeType = mimeType;
		this.encoding = encoding;
	}

	/**
	 * Constructs a {@code URLSource} from a {@link URL} with an explicit MIME type
	 * and character encoding.  The URI is derived from the URL.
	 *
	 * @param url      the backing URL; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 * @param encoding the character encoding, or {@code null} if unknown.
	 * @throws URISyntaxException if the URL cannot be converted to a URI.
	 */
	public URLSource(final URL url, final String mimeType, final String encoding) throws URISyntaxException {
		this(url.toURI(), url, mimeType, encoding);
	}

	/**
	 * Constructs a {@code URLSource} from a {@link URL} with an explicit MIME type.
	 * The character encoding is left unknown.
	 *
	 * @param url      the backing URL; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 * @throws URISyntaxException if the URL cannot be converted to a URI.
	 */
	public URLSource(final URL url, final String mimeType) throws URISyntaxException {
		this(url, mimeType, null);
	}

	/**
	 * Constructs a {@code URLSource} from a {@link URL}.  Both MIME type and
	 * character encoding are determined automatically.
	 *
	 * @param url the backing URL; must not be {@code null}.
	 * @throws URISyntaxException if the URL cannot be converted to a URI.
	 */
	public URLSource(final URL url) throws URISyntaxException {
		this(url, null);
	}

	/**
	 * Constructs a {@code URLSource} from a {@link URI} with an explicit MIME type
	 * and character encoding.  The URL is derived from the URI.
	 *
	 * @param uri      the URI; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 * @param encoding the character encoding, or {@code null} if unknown.
	 * @throws MalformedURLException if the URI cannot be converted to a URL.
	 */
	public URLSource(final URI uri, final String mimeType, final String encoding) throws MalformedURLException {
		this(uri, uri.toURL(), mimeType, encoding);
	}

	/**
	 * Constructs a {@code URLSource} from a {@link URI} with an explicit MIME type.
	 * The character encoding is left unknown.
	 *
	 * @param uri      the URI; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 * @throws MalformedURLException if the URI cannot be converted to a URL.
	 */
	public URLSource(final URI uri, final String mimeType) throws MalformedURLException {
		this(uri, uri.toURL(), mimeType, null);
	}

	/**
	 * Constructs a {@code URLSource} from a {@link URI}.  Both MIME type and
	 * character encoding are determined automatically.
	 *
	 * @param uri the URI; must not be {@code null}.
	 * @throws MalformedURLException if the URI cannot be converted to a URL.
	 */
	public URLSource(final URI uri) throws MalformedURLException {
		this(uri, uri.toURL(), null, null);
	}

	/**
	 * Returns the MIME type of the resource.  For {@code file:} URIs the type is
	 * inferred from the file extension; for all other URIs it is retrieved from the
	 * connection's {@code Content-Type} header.
	 *
	 * @return the MIME type string, or {@code null} if it cannot be determined.
	 * @throws IOException if an I/O error occurs while connecting.
	 */
	@Override
	public String getMimeType() throws IOException {
		if (this.mimeType == null) {
			if (this.isFile()) {
				final String filename = this.getFile().getName();
				final int dot = filename.lastIndexOf('.');
				if (dot != -1) {
					final String suffix = filename.substring(dot).toLowerCase();
					this.mimeType = switch (suffix) {
						case ".html", ".htm" -> "text/html";
						case ".xml", ".xhtml", ".xht" -> "text/xml";
						default -> null;
					};
					if (this.mimeType != null) {
						return this.mimeType;
					}
				}
			}
			try {
				if (this.conn == null) {
					this.connect();
				}
				this.mimeType = this.conn.getContentType();
			} catch (IOException e) {
				this.conn = null;
			}
		}
		return this.mimeType;
	}

	/**
	 * Returns the character encoding supplied at construction time.
	 *
	 * @return the encoding name, or {@code null} if unknown.
	 */
	@Override
	public String getEncoding() {
		return this.encoding;
	}

	/**
	 * Returns {@code true} if the resource exists.  For {@code file:} URIs the
	 * file system is checked; for all other URIs {@code true} is returned
	 * unconditionally (a connection is not opened just to check existence).
	 *
	 * @return {@code true} if the resource is expected to exist.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public boolean exists() throws IOException {
		if (this.isFile()) {
			return this.getFile().exists();
		}
		return true;
	}

	/**
	 * Returns {@code true} if the URI uses the {@code file} scheme.
	 *
	 * @return {@code true} if this source is backed by the local file system.
	 * @throws IOException never thrown.
	 */
	@Override
	public boolean isFile() throws IOException {
		return "file".equals(this.uri.getScheme());
	}

	/**
	 * Always returns {@code true} — URL content is always accessible as a binary
	 * stream.
	 *
	 * @return {@code true}.
	 * @throws IOException never thrown.
	 */
	@Override
	public boolean isInputStream() throws IOException {
		return true;
	}

	/**
	 * Returns {@code true} if a character encoding was supplied at construction
	 * time, meaning the content can be read as a character stream.
	 *
	 * @return {@code true} if a character encoding is known.
	 * @throws IOException never thrown.
	 */
	@Override
	public boolean isReader() throws IOException {
		return this.encoding != null;
	}

	/**
	 * Opens and returns a binary {@link InputStream} for the URL content.  Any
	 * previously opened connection is discarded first.  For {@code file:} URIs a
	 * {@link java.io.FileInputStream} is returned directly; for all other URIs a
	 * {@link java.net.URLConnection} is opened.
	 *
	 * @return a new {@link InputStream} positioned at the beginning of the content.
	 * @throws IOException if an I/O error occurs while opening the connection.
	 */
	@Override
	public synchronized InputStream getInputStream() throws IOException {
		if (this.in != null) {
			this.in = null;
			this.conn = null;
			this.timestamp = -1L;
		}
		if (this.isFile()) {
			this.in = new FileInputStream(this.getFile());
			return this.in;
		}
		if (this.conn == null) {
			this.connect();
		}
		this.in = this.conn.getInputStream();
		return this.in;
	}

	/**
	 * Returns a character {@link Reader} for the URL content decoded with the
	 * encoding supplied at construction time.
	 *
	 * @return a {@link Reader} positioned at the beginning of the content.
	 * @throws UnsupportedOperationException if no character encoding is known.
	 * @throws IOException if an I/O error occurs while opening the connection.
	 */
	@Override
	public Reader getReader() throws IOException {
		if (this.encoding == null) {
			throw new UnsupportedOperationException("Encoding not set");
		}
		return new InputStreamReader(this.getInputStream(), this.encoding);
	}

	/**
	 * Closes the currently open connection and input stream, if any.  After this
	 * call the source may be re-opened by calling {@link #getInputStream()} again.
	 */
	@Override
	public synchronized void close() {
		if (this.in != null) {
			try {
				this.in.close();
			} catch (Exception e) {
				LOG.log(Level.FINE, "Exception while closing URL connection", e);
			} finally {
				this.in = null;
				this.conn = null;
				this.timestamp = -1L;
			}
		}
	}

	private void connect() throws IOException {
		this.conn = this.url.openConnection();
		this.timestamp = this.conn.getLastModified();
	}

	/**
	 * Returns the local {@link File} corresponding to this URL.  When the URI
	 * scheme is {@code file} the file is derived directly from the URI; otherwise
	 * the URI path component is used as a best-effort fallback.
	 *
	 * @return a {@link File} for this source; never {@code null}.
	 */
	@Override
	public File getFile() {
		if ("file".equals(this.uri.getScheme())) {
			return new File(this.uri);
		}
		// Fallback for non-file UI but getFile called?
		String path = this.uri.getPath();
		if (path == null) {
			path = this.uri.getSchemeSpecificPart();
		}
		return new File(path);
	}

	/**
	 * Returns the byte length of the content.  For {@code file:} URIs the file
	 * size is returned directly; for all other URIs the connection's
	 * {@code Content-Length} is used, opening a connection if necessary.
	 *
	 * @return the content length in bytes, or {@code -1} if unknown.
	 * @throws IOException if an I/O error occurs while connecting.
	 */
	@Override
	public long getLength() throws IOException {
		if (this.isFile()) {
			return this.getFile().length();
		}
		if (this.conn == null) {
			this.connect();
		}
		return this.conn.getContentLengthLong();
	}

	/**
	 * Returns a {@link SourceValidity} based on the connection's
	 * {@code Last-Modified} timestamp.  A new connection is opened if necessary.
	 *
	 * @return a validity instance; never {@code null}.
	 * @throws IOException if an I/O error occurs while connecting.
	 */
	@Override
	public SourceValidity getValidity() throws IOException {
		this.connect();
		return new URLSourceValidity(this.timestamp, this.url);
	}
}
