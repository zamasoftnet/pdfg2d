package net.zamasoft.pdfg2d.resolver.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import net.zamasoft.pdfg2d.resolver.Source;
import net.zamasoft.pdfg2d.resolver.SourceValidity;

/**
 * A transparent decorator for {@link Source} that forwards every method call
 * to the wrapped delegate source.
 *
 * <p>Subclasses may override individual methods to add behaviour without
 * reimplementing the full {@link Source} interface.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class SourceWrapper implements Source {
	/** The delegate source to which all calls are forwarded. */
	protected final Source source;

	/**
	 * Constructs a {@code SourceWrapper} that delegates to the given source.
	 *
	 * @param source the source to wrap; must not be {@code null}.
	 */
	public SourceWrapper(final Source source) {
		this.source = source;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean exists() throws IOException {
		return this.source.exists();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getEncoding() throws IOException {
		return this.source.getEncoding();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public File getFile() {
		return this.source.getFile();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return this.source.getInputStream();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getLength() throws IOException {
		return this.source.getLength();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getMimeType() throws IOException {
		return this.source.getMimeType();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Reader getReader() throws IOException {
		return this.source.getReader();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public URI getURI() {
		return this.source.getURI();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SourceValidity getValidity() throws IOException {
		return this.source.getValidity();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isFile() throws IOException {
		return this.source.isFile();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isInputStream() throws IOException {
		return this.source.isInputStream();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isReader() throws IOException {
		return this.source.isReader();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() throws IOException {
		this.source.close();
	}

	/**
	 * Returns the string representation of the wrapped source.
	 *
	 * @return the delegate's {@link #toString()} result; never {@code null}.
	 */
	@Override
	public String toString() {
		return this.source.toString();
	}
}
