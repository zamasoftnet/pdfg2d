package net.zamasoft.pdfg2d.resolver.protocol.file;

import java.io.IOException;
import java.net.URI;
import net.zamasoft.pdfg2d.resolver.Source;
import net.zamasoft.pdfg2d.resolver.SourceResolver;

/**
 * A {@link SourceResolver} implementation that handles {@code file:} URIs by
 * constructing a {@link FileSource} backed by the local file system.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class FileSourceResolver implements SourceResolver {
	/**
	 * Resolves the given {@code file:} URI and returns a {@link FileSource} for
	 * the corresponding local file.
	 *
	 * @param uri a {@code file:} scheme URI; must not be {@code null}.
	 * @return a new {@link FileSource} for the local file.
	 * @throws IOException if the URI path cannot be decoded or an I/O error occurs.
	 */
	@Override
	public Source resolve(final URI uri) throws IOException {
		return new FileSource(uri);
	}

	/**
	 * Releases the given source.  For file-based sources no explicit cleanup is
	 * required, so this method does nothing.
	 *
	 * @param source the source to release; must have been obtained from this
	 *               resolver.
	 */
	@Override
	public void release(final Source source) {
		// ignore
	}
}
