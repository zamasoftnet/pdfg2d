package net.zamasoft.pdfg2d.resolver.protocol.zip;

import java.io.IOException;
import java.net.URI;
import java.util.zip.ZipFile;
import net.zamasoft.pdfg2d.resolver.Source;
import net.zamasoft.pdfg2d.resolver.SourceResolver;

/**
 * A {@link SourceResolver} implementation that resolves URIs to entries within
 * a single ZIP archive.
 *
 * <p>The ZIP archive is supplied at construction time and is shared across all
 * {@link ZIPFileSource} instances produced by this resolver.  The archive is
 * not closed by this resolver; callers are responsible for closing it when it
 * is no longer needed.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class ZIPFileSourceResolver implements SourceResolver {
	/** The shared ZIP archive used by all sources produced by this resolver. */
	protected final ZipFile zip;

	/**
	 * Constructs a {@code ZIPFileSourceResolver} backed by the given ZIP archive.
	 *
	 * @param zip the open ZIP archive to read from; must not be {@code null}.
	 */
	public ZIPFileSourceResolver(final ZipFile zip) {
		this.zip = zip;
	}

	/**
	 * Resolves the given URI to an entry in the ZIP archive.  The entry path is
	 * derived from the URI's scheme-specific part.
	 *
	 * @param uri the URI whose path identifies a ZIP entry; must not be
	 *            {@code null}.
	 * @return a new {@link ZIPFileSource} for the entry.
	 * @throws IOException if an I/O error occurs during construction.
	 */
	@Override
	public Source resolve(final URI uri) throws IOException {
		return new ZIPFileSource(this.zip, uri);
	}

	/**
	 * Releases the given source by closing it.  Any {@link IOException} thrown
	 * during close is silently ignored.
	 *
	 * @param source the source to release; must have been obtained from this
	 *               resolver.
	 */
	@Override
	public void release(final Source source) {
		try {
			source.close();
		} catch (IOException e) {
			// ignore
		}
	}
}
