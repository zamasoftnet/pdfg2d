package net.zamasoft.pdfg2d.resolver.util;

import java.io.IOException;
import java.net.URI;
import net.zamasoft.pdfg2d.resolver.Source;

/**
 * Abstract base class for {@link Source} implementations.
 *
 * <p>This class stores the source URI and provides default (no-op or
 * {@code false}) implementations for the capability-inquiry methods
 * ({@link #isInputStream()}, {@link #isFile()}, {@link #isReader()}) and for
 * {@link #close()}.  Concrete subclasses override the methods that are
 * relevant to their transport mechanism.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class AbstractSource implements Source {
	/** The URI associated with this source; never {@code null}. */
	protected final URI uri;

	/**
	 * Constructs an {@code AbstractSource} with the given URI.
	 *
	 * @param uri the URI to associate with this source; must not be {@code null}.
	 */
	public AbstractSource(final URI uri) {
		this.uri = uri;
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
	 * Default implementation returns {@code false}.  Subclasses that support
	 * binary stream access should override this method.
	 *
	 * @return {@code false}.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public boolean isInputStream() throws IOException {
		return false;
	}

	/**
	 * Default implementation returns {@code false}.  Subclasses that are backed
	 * by a local file should override this method.
	 *
	 * @return {@code false}.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public boolean isFile() throws IOException {
		return false;
	}

	/**
	 * Default implementation returns {@code false}.  Subclasses that support
	 * character reader access should override this method.
	 *
	 * @return {@code false}.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public boolean isReader() throws IOException {
		return false;
	}

	/**
	 * Default implementation does nothing.  Subclasses that hold open connections
	 * or streams should override this method to release those resources.
	 *
	 * @throws IOException if an I/O error occurs during close.
	 */
	@Override
	public void close() throws IOException {
		// Default implementation does nothing
	}

	/**
	 * Returns the string form of this source's URI.
	 *
	 * @return the URI as a string; never {@code null}.
	 */
	@Override
	public String toString() {
		return this.uri.toString();
	}
}
