package net.zamasoft.pdfg2d.resolver;

import java.io.IOException;
import java.net.URI;

/**
 * Strategy interface for resolving a {@link URI} into a {@link Source}.
 *
 * <p>A {@code SourceResolver} is responsible for understanding one or more URI
 * schemes and returning an appropriate {@link Source} implementation. Callers
 * must {@link #release(Source) release} every {@link Source} they obtain once
 * they are finished with it so that the resolver can clean up any resources
 * (e.g. HTTP connections, file handles).
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface SourceResolver {
	/**
	 * Resolves the given URI and returns a {@link Source} that provides access to
	 * the corresponding resource.
	 *
	 * @param uri the URI of the resource to resolve; must not be {@code null}.
	 * @return a {@link Source} for the resource; never {@code null}.
	 * @throws IOException if the resource cannot be resolved or an I/O error occurs.
	 */
	Source resolve(URI uri) throws IOException;

	/**
	 * Releases any resources held by the given {@link Source}. The source must have
	 * been obtained from {@link #resolve(URI)} on this same resolver instance.
	 * After this call the source must not be used.
	 *
	 * @param source the source to release; must not be {@code null}.
	 */
	void release(Source source);
}

