package net.zamasoft.pdfg2d.resolver.protocol.http;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;

import net.zamasoft.pdfg2d.resolver.SourceValidity;
import net.zamasoft.pdfg2d.resolver.util.AbstractSource;

/**
 * A {@link net.zamasoft.pdfg2d.resolver.Source Source} implementation that
 * retrieves data from an HTTP or HTTPS resource using Apache HttpClient.
 *
 * <p>The HTTP connection is established lazily the first time metadata or
 * content is requested.  Once opened the response entity is consumed by
 * {@link #getInputStream()}; calling {@link #getInputStream()} a second time
 * will abort and re-issue the request so the stream is always positioned at
 * the beginning.
 *
 * <p>Validity is based on the {@code Last-Modified} response header value.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class HTTPSource extends AbstractSource {
	private final CloseableHttpClient httpClient;
	private final boolean closeClient;

	private String mimeType;
	private String encoding;

	private HttpUriRequest req;
	private HttpResponse res;
	private InputStream in;

	private boolean exists;
	private long lastModified = -1;
	private long contentLength = -1;

	/**
	 * Constructs an {@code HTTPSource} that owns and closes the given client.
	 *
	 * @param uri        the HTTP/HTTPS URI to retrieve; must not be {@code null}.
	 * @param httpClient the Apache HTTP client to use; must not be {@code null}.
	 */
	public HTTPSource(final URI uri, final CloseableHttpClient httpClient) {
		this(uri, httpClient, true);
	}

	/**
	 * Constructs an {@code HTTPSource} with explicit control over client lifetime.
	 *
	 * @param uri         the HTTP/HTTPS URI to retrieve; must not be {@code null}.
	 * @param httpClient  the Apache HTTP client to use; must not be {@code null}.
	 * @param closeClient {@code true} if the client should be closed when this
	 *                    source is {@link #close() closed}.
	 */
	public HTTPSource(final URI uri, final CloseableHttpClient httpClient, final boolean closeClient) {
		super(uri);
		this.httpClient = httpClient;
		this.closeClient = closeClient;
	}

	/**
	 * Returns the underlying Apache {@link CloseableHttpClient} used by this
	 * source.
	 *
	 * @return the HTTP client; never {@code null}.
	 */
	public CloseableHttpClient getHttpClient() {
		return this.httpClient;
	}

	/**
	 * Returns the MIME type reported by the {@code Content-Type} response header.
	 * Triggers an HTTP connection if one has not been established yet.
	 *
	 * @return the MIME type string, or {@code null} if the header is absent.
	 * @throws IOException if an I/O error occurs while connecting.
	 */
	@Override
	public String getMimeType() throws IOException {
		this.tryConnect();
		return this.mimeType;
	}

	/**
	 * Returns the character encoding reported by the {@code Content-Encoding}
	 * response header.  Triggers an HTTP connection if one has not been established
	 * yet.  {@code ISO-8859-1} and unsupported encodings are treated as absent.
	 *
	 * @return the encoding name, or {@code null}.
	 * @throws IOException if an I/O error occurs while connecting.
	 */
	@Override
	public String getEncoding() throws IOException {
		this.tryConnect();
		return this.encoding;
	}

	/**
	 * Returns {@code true} unless the HTTP response status was {@code 404 Not
	 * Found}.  Triggers an HTTP connection if one has not been established yet.
	 *
	 * @return {@code true} if the resource exists.
	 * @throws IOException if an I/O error occurs while connecting.
	 */
	@Override
	public boolean exists() throws IOException {
		this.tryConnect();
		return this.exists;
	}

	/**
	 * Always returns {@code true} — HTTP response bodies are always accessible as
	 * a binary stream.
	 *
	 * @return {@code true}.
	 * @throws IOException never thrown.
	 */
	@Override
	public boolean isInputStream() throws IOException {
		return true;
	}

	/**
	 * Returns {@code true} if a usable character encoding was reported in the
	 * response headers.  Triggers an HTTP connection if one has not been
	 * established yet.
	 *
	 * @return {@code true} if a character encoding is known.
	 * @throws IOException if an I/O error occurs while connecting.
	 */
	@Override
	public boolean isReader() throws IOException {
		this.tryConnect();
		return this.encoding != null;
	}

	/**
	 * Returns a binary {@link InputStream} for the HTTP response body.  If a
	 * previous connection is still open it is first closed and a new request is
	 * issued.
	 *
	 * @return the response body stream; never {@code null}.
	 * @throws java.io.FileNotFoundException if the response entity is empty.
	 * @throws IOException if an I/O error occurs while connecting or reading.
	 */
	@Override
	public synchronized InputStream getInputStream() throws IOException {
		if (this.in != null) {
			if (this.res != null) {
				try {
					final HttpEntity e = this.res.getEntity();
					if (e != null) {
						final InputStream is = e.getContent(); // avoid name clash with field 'in'
						if (is != null) {
							is.close();
						}
					}
				} catch (IOException e) {
					// ignore
				}
				this.res = null;
			}
			this.req = null;
			this.in = null;
		}
		this.tryConnect();
		final HttpEntity entity = this.res.getEntity();
		if (entity == null) {
			throw new FileNotFoundException();
		}
		this.in = entity.getContent();
		return this.in;
	}

	/**
	 * Establishes the HTTP connection if one has not already been established.
	 * After this method returns the response headers are available and the
	 * {@link #exists}, {@link #mimeType}, {@link #encoding}, {@link #lastModified}
	 * and {@link #contentLength} fields are populated.
	 *
	 * @throws IOException if the HTTP request fails or an I/O error occurs.
	 */
	protected void tryConnect() throws IOException {
		if (this.req != null) {
			return;
		}
		this.req = this.createHttpRequest();
		final int status;
		try {
			this.res = this.httpClient.execute(this.req);
			status = this.res.getStatusLine().getStatusCode();
		} catch (Exception e) {
			throw new IOException(e);
		}
		this.exists = status != 404;
		final HttpEntity entity = this.res.getEntity();
		this.encoding = null;
		if (entity != null) {
			final Header encodingHeader = entity.getContentEncoding();
			if (encodingHeader != null) {
				this.encoding = encodingHeader.getValue();
				try {
					if (this.encoding.equalsIgnoreCase("ISO-8859-1") || !Charset.isSupported(this.encoding)) {
						this.encoding = null;
					}
				} catch (Exception e) {
					// ignore
				}
			}
			final Header mimeTypeHeader = entity.getContentType();
			if (mimeTypeHeader != null) {
				this.mimeType = mimeTypeHeader.getValue();
			}
		}
		final Header lastModifiedHeader = this.res.getLastHeader("Last-Modified");
		if (lastModifiedHeader != null) {
			final Date date = DateUtils.parseDate(lastModifiedHeader.getValue());
			if (date != null) {
				this.lastModified = date.getTime();
			}
		}
		final Header contentLengthHeader = this.res.getLastHeader("Content-Length");
		if (contentLengthHeader != null) {
			try {
				this.contentLength = Long.parseLong(contentLengthHeader.getValue());
			} catch (NumberFormatException e) {
				// ignore
			}
		}
	}

	/**
	 * Creates the {@link HttpUriRequest} that will be used to fetch the resource.
	 * The default implementation returns a {@link HttpGet} for {@link #uri}.
	 * Subclasses may override this method to supply custom request types or add
	 * headers.
	 *
	 * @return a new HTTP request targeting this source's URI; never {@code null}.
	 */
	protected HttpUriRequest createHttpRequest() {
		return new HttpGet(this.uri);
	}

	/**
	 * Returns a character {@link Reader} for the HTTP response body decoded with
	 * the encoding reported by the response headers.
	 *
	 * @return a character reader over the response body.
	 * @throws UnsupportedOperationException if no usable character encoding was
	 *                                        reported by the server.
	 * @throws IOException                    if an I/O error occurs while
	 *                                        connecting or reading.
	 */
	@Override
	public Reader getReader() throws IOException {
		if (this.encoding == null) {
			throw new UnsupportedOperationException("Encoding not set");
		}
		return new InputStreamReader(this.getInputStream(), this.encoding);
	}

	/**
	 * Always throws {@link UnsupportedOperationException} — HTTP sources are not
	 * backed by a local file.
	 *
	 * @throws UnsupportedOperationException always.
	 */
	@Override
	public File getFile() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns the content length reported by the {@code Content-Length} response
	 * header.  Triggers an HTTP connection if one has not been established yet.
	 *
	 * @return the content length in bytes, or {@code -1} if unknown.
	 * @throws IOException if an I/O error occurs while connecting.
	 */
	@Override
	public long getLength() throws IOException {
		this.tryConnect();
		return this.contentLength;
	}

	/**
	 * Returns a {@link SourceValidity} based on the {@code Last-Modified} response
	 * header.  If the header was absent the validity timestamp is {@code -1} and
	 * comparisons will return {@link net.zamasoft.pdfg2d.resolver.SourceValidity.Validity#UNKNOWN}.
	 *
	 * @return a validity instance; never {@code null}.
	 */
	@Override
	public SourceValidity getValidity() {
		return new HTTPSourceValidity(this.lastModified);
	}

	/**
	 * Closes the HTTP response stream and, if this source was constructed with
	 * {@code closeClient = true}, also closes the underlying
	 * {@link CloseableHttpClient}.
	 */
	@Override
	public void close() {
		if (this.req != null) {
			try {
				if (this.in != null) {
					try {
						this.in.close();
					} catch (IOException e) {
						// ignore
					}
				}
			} finally {
				this.res = null;
				this.req = null;
				this.in = null;
				if (this.closeClient) {
					try {
						this.httpClient.close();
					} catch (IOException e) {
						// ignore
					}
				}
			}
		}
	}
}
