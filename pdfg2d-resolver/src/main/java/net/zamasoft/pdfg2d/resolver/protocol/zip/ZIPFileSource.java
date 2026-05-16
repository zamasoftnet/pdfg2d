package net.zamasoft.pdfg2d.resolver.protocol.zip;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.zamasoft.pdfg2d.resolver.SourceValidity;
import net.zamasoft.pdfg2d.resolver.util.AbstractSource;
import net.zamasoft.pdfg2d.resolver.util.URIHelper;

/**
 * A {@link net.zamasoft.pdfg2d.resolver.Source Source} implementation that
 * reads an entry from a ZIP archive.
 *
 * <p>The ZIP entry is located by path at construction time.  If no matching
 * entry is found {@link #exists()} returns {@code false} and
 * {@link #getInputStream()} throws {@link java.io.FileNotFoundException}.
 *
 * <p>Validity is based on the last-modified timestamp of the underlying ZIP
 * file, not on the individual entry.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class ZIPFileSource extends AbstractSource {
	private static final Logger LOG = Logger.getLogger(ZIPFileSource.class.getName());

	private final ZipFile zip;
	private final ZipEntry entry;
	private final String encoding;
	private String mimeType = null;

	/**
	 * Constructs a {@code ZIPFileSource} with all attributes specified explicitly.
	 * If {@code path} is {@code null} the entry path is decoded from the URI's
	 * scheme-specific part.
	 *
	 * @param zip      the open ZIP archive; must not be {@code null}.
	 * @param path     the entry path within the ZIP, or {@code null} to derive it
	 *                 from the URI.
	 * @param uri      the URI associated with this source; must not be
	 *                 {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 * @param encoding the character encoding, or {@code null} if unknown.
	 */
	public ZIPFileSource(final ZipFile zip, final String path, final URI uri, final String mimeType,
			final String encoding) {
		super(uri);
		this.zip = Objects.requireNonNull(zip, "ZipFile must not be null");

		String entryPath = path;
		if (entryPath == null) {
			entryPath = uri.getSchemeSpecificPart();
			try {
				entryPath = URIHelper.decode(entryPath);
			} catch (Exception e) {
				LOG.log(Level.WARNING, "Cannot decode URI.", e);
			}
		}

		this.entry = zip.getEntry(entryPath);
		this.mimeType = mimeType;
		this.encoding = encoding;
	}

	/**
	 * Constructs a {@code ZIPFileSource} whose entry path is derived from the URI.
	 *
	 * @param zip      the open ZIP archive; must not be {@code null}.
	 * @param uri      the URI used to derive the entry path; must not be
	 *                 {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 * @param encoding the character encoding, or {@code null} if unknown.
	 */
	public ZIPFileSource(final ZipFile zip, final URI uri, final String mimeType, final String encoding) {
		this(zip, null, uri, mimeType, encoding);
	}

	/**
	 * Constructs a {@code ZIPFileSource} with a specified MIME type and no
	 * explicit character encoding.
	 *
	 * @param zip      the open ZIP archive; must not be {@code null}.
	 * @param uri      the URI used to derive the entry path; must not be
	 *                 {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 */
	public ZIPFileSource(final ZipFile zip, final URI uri, final String mimeType) {
		this(zip, uri, mimeType, null);
	}

	/**
	 * Constructs a {@code ZIPFileSource} with neither MIME type nor encoding
	 * specified explicitly.
	 *
	 * @param zip the open ZIP archive; must not be {@code null}.
	 * @param uri the URI used to derive the entry path; must not be {@code null}.
	 * @throws IOException if an I/O error occurs during construction.
	 */
	public ZIPFileSource(final ZipFile zip, final URI uri) throws IOException {
		this(zip, uri, null);
	}

	/**
	 * Constructs a {@code ZIPFileSource} with an explicit entry path and MIME
	 * type, without a character encoding.
	 *
	 * @param zip      the open ZIP archive; must not be {@code null}.
	 * @param path     the entry path within the ZIP; must not be {@code null}.
	 * @param uri      the URI associated with this source; must not be
	 *                 {@code null}.
	 * @param mimeType the MIME type, or {@code null} for auto-detection.
	 */
	public ZIPFileSource(final ZipFile zip, final String path, final URI uri, final String mimeType) {
		this(zip, path, uri, mimeType, null);
	}

	/**
	 * Constructs a {@code ZIPFileSource} with an explicit entry path but without
	 * specifying MIME type or character encoding.
	 *
	 * @param zip  the open ZIP archive; must not be {@code null}.
	 * @param path the entry path within the ZIP; must not be {@code null}.
	 * @param uri  the URI associated with this source; must not be {@code null}.
	 */
	public ZIPFileSource(final ZipFile zip, final String path, final URI uri) {
		this(zip, path, uri, null, null);
	}

	/**
	 * Returns the MIME type of the ZIP entry, auto-detected from the entry name's
	 * extension when not supplied at construction time.  Recognises {@code .html},
	 * {@code .htm}, {@code .xml}, {@code .xhtml}, and {@code .xht}; all other
	 * extensions yield {@code null}.
	 *
	 * @return the MIME type string, or {@code null} if it cannot be determined.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public String getMimeType() throws IOException {
		if (this.mimeType == null && this.entry != null) {
			final String name = this.entry.getName();
			final int dot = name.lastIndexOf('.');
			if (dot != -1) {
				final String suffix = name.substring(dot).toLowerCase();
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
	 * Returns {@code true} if a {@link ZipEntry} matching the requested path was
	 * found in the ZIP archive.
	 *
	 * @return {@code true} if the entry exists; {@code false} otherwise.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public boolean exists() throws IOException {
		return this.entry != null;
	}

	/**
	 * Always returns {@code false} — ZIP entries are not backed by a local file.
	 *
	 * @return {@code false}.
	 * @throws IOException never thrown.
	 */
	@Override
	public boolean isFile() throws IOException {
		return false;
	}

	/**
	 * Always returns {@code true} — ZIP entry content is always accessible as a
	 * binary stream.
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
	 * time, meaning the entry content can be read as a character stream.
	 *
	 * @return {@code true} if a character encoding is known.
	 * @throws IOException never thrown.
	 */
	@Override
	public boolean isReader() throws IOException {
		return this.encoding != null;
	}

	/**
	 * Opens and returns a binary {@link InputStream} for the ZIP entry content.
	 *
	 * @return the entry's input stream; never {@code null}.
	 * @throws java.io.FileNotFoundException if the entry does not exist in the
	 *                                        ZIP archive.
	 * @throws IOException if an I/O error occurs while reading the ZIP file.
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		if (this.entry == null) {
			throw new FileNotFoundException(this.uri.toString());
		}
		return this.zip.getInputStream(this.entry);
	}

	/**
	 * Returns a character {@link Reader} for the ZIP entry decoded with the
	 * encoding supplied at construction time.
	 *
	 * @return a {@link Reader} positioned at the beginning of the entry.
	 * @throws UnsupportedOperationException if no character encoding is known.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public Reader getReader() throws IOException {
		if (!this.isReader()) {
			throw new UnsupportedOperationException();
		}
		return new InputStreamReader(this.getInputStream(), this.encoding);
	}

	/**
	 * Always throws {@link UnsupportedOperationException} — ZIP entries are not
	 * backed by a local file.
	 *
	 * @throws UnsupportedOperationException always.
	 */
	@Override
	public File getFile() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns the uncompressed byte length of the ZIP entry.
	 *
	 * @return the uncompressed size in bytes, or {@code -1} if unknown, or
	 *         {@code 0} if the entry does not exist.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public long getLength() throws IOException {
		if (this.exists()) {
			final long size = this.entry.getSize();
			return size != -1 ? size : -1;
		}
		return 0;
	}

	/**
	 * Returns a {@link SourceValidity} based on the last-modified timestamp of the
	 * underlying ZIP file (not the individual entry).
	 *
	 * @return a validity instance; never {@code null}.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public SourceValidity getValidity() throws IOException {
		final File file = new File(this.zip.getName());
		final long timestamp = file.lastModified();
		return new ZIPFileSourceValidity(timestamp, file);
	}
}
