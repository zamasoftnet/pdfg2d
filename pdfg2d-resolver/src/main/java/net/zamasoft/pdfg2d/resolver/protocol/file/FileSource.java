package net.zamasoft.pdfg2d.resolver.protocol.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.Objects;
import net.zamasoft.pdfg2d.resolver.SourceValidity;
import net.zamasoft.pdfg2d.resolver.util.AbstractSource;
import net.zamasoft.pdfg2d.resolver.util.URIHelper;

/**
 * A {@link net.zamasoft.pdfg2d.resolver.Source Source} implementation that
 * reads content from a local file on the file system.
 *
 * <p>MIME type detection is performed lazily from the file extension when not
 * supplied explicitly.  Validity is based on the file's last-modified timestamp.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class FileSource extends AbstractSource {
	private final File file;
	private final String encoding;
	private String mimeType = null;

	/**
	 * Constructs a {@code FileSource} with all attributes specified explicitly.
	 *
	 * @param file     the backing file; must not be {@code null}.
	 * @param uri      the URI to associate with this source; must not be
	 *                 {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 * @param encoding the character encoding, or {@code null} if unknown.
	 */
	public FileSource(final File file, final URI uri, final String mimeType, final String encoding) {
		super(uri);
		this.file = Objects.requireNonNull(file);
		this.mimeType = mimeType;
		this.encoding = encoding;
	}

	/**
	 * Constructs a {@code FileSource} from a {@code file:} URI by decoding the
	 * path component.
	 *
	 * @param uri a {@code file:} scheme URI; must not be {@code null}.
	 * @throws IOException if the URI path cannot be decoded.
	 */
	public FileSource(final URI uri) throws IOException {
		super(uri);
		String path = uri.getSchemeSpecificPart();
		path = URIHelper.decode(path);
		this.file = new File(path);
		this.mimeType = null;
		this.encoding = null;
	}

	/**
	 * Constructs a {@code FileSource} from a {@link File} with an explicit MIME
	 * type and character encoding.  The URI is derived from the file path.
	 *
	 * @param file     the backing file; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 * @param encoding the character encoding, or {@code null} if unknown.
	 */
	public FileSource(final File file, final String mimeType, final String encoding) {
		this(file, file.toURI(), mimeType, encoding);
	}

	/**
	 * Constructs a {@code FileSource} from a {@link File} with an explicit MIME
	 * type.  The character encoding is left unknown.
	 *
	 * @param file     the backing file; must not be {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 */
	public FileSource(final File file, final String mimeType) {
		this(file, mimeType, null);
	}

	/**
	 * Constructs a {@code FileSource} from a {@link File}.  Both MIME type and
	 * character encoding are determined automatically.
	 *
	 * @param file the backing file; must not be {@code null}.
	 */
	public FileSource(final File file) {
		this(file, null);
	}

	/**
	 * Returns the MIME type of the file, auto-detected from the file extension
	 * when not supplied at construction time.  Recognises {@code .html},
	 * {@code .htm}, {@code .xml}, {@code .xhtml}, and {@code .xht}; all other
	 * extensions yield {@code null}.
	 *
	 * @return the MIME type string, or {@code null} if it cannot be determined.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public String getMimeType() throws IOException {
		if (this.mimeType == null) {
			final String filename = this.file.getName();
			final int dot = filename.lastIndexOf('.');
			if (dot != -1) {
				final String suffix = filename.substring(dot).toLowerCase();
				this.mimeType = switch (suffix) {
					case ".html", ".htm" -> "text/html";
					case ".xml", ".xhtml", ".xht" -> "text/xml";
					default -> null;
				};
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
	 * Returns {@code true} if the backing file exists on the file system.
	 *
	 * @return {@code true} if the file exists; {@code false} otherwise.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public boolean exists() throws IOException {
		return this.file.exists();
	}

	/**
	 * Always returns {@code true} — this source is always backed by a local file.
	 *
	 * @return {@code true}.
	 * @throws IOException never thrown.
	 */
	@Override
	public boolean isFile() throws IOException {
		return true;
	}

	/**
	 * Always returns {@code true} — file content is always readable as a binary
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
	 * Opens and returns a {@link java.io.FileInputStream} for the backing file.
	 *
	 * @return a new {@link InputStream} positioned at the beginning of the file.
	 * @throws IOException if the file cannot be opened.
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return new FileInputStream(this.file);
	}

	/**
	 * Opens and returns a character {@link Reader} for the file using the encoding
	 * supplied at construction time.
	 *
	 * @return a {@link Reader} positioned at the beginning of the file.
	 * @throws UnsupportedOperationException if no character encoding is known.
	 * @throws IOException if the file cannot be opened.
	 */
	@Override
	public Reader getReader() throws IOException {
		if (!this.isReader()) {
			throw new UnsupportedOperationException();
		}
		return new InputStreamReader(this.getInputStream(), this.encoding);
	}

	/**
	 * Returns the local {@link File} that backs this source.
	 *
	 * @return the backing file; never {@code null}.
	 */
	@Override
	public File getFile() {
		return this.file;
	}

	/**
	 * Returns the byte length of the backing file.
	 *
	 * @return the number of bytes, or {@code 0} if the file does not exist.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public long getLength() throws IOException {
		return this.file.length();
	}

	/**
	 * Returns a {@link SourceValidity} based on the file's last-modified timestamp.
	 *
	 * @return a validity instance; never {@code null}.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public SourceValidity getValidity() throws IOException {
		final long timestamp = this.file.lastModified();
		return new FileSourceValidity(timestamp, this.file);
	}
}
