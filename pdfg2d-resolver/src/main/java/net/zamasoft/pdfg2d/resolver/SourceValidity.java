package net.zamasoft.pdfg2d.resolver;

import java.io.Serializable;

/**
 * Carries information about whether the content of a {@link Source} has
 * changed since it was last accessed, enabling caching strategies.
 *
 * <p>A {@code SourceValidity} is obtained via {@link Source#getValidity()} at
 * the time a resource is first fetched.  On subsequent requests the stored
 * validity can be compared against a freshly obtained one to decide whether the
 * cached copy is still usable.
 *
 * <p>Instances must be {@link Serializable} so that they can be persisted
 * together with a cached response.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface SourceValidity extends Serializable {
	/**
	 * Enumeration of possible cache-validity states.
	 */
	enum Validity {
		/** The resource has changed — the cached copy is no longer valid. */
		INVALID,
		/** It cannot be determined whether the resource has changed. */
		UNKNOWN,
		/** The resource has not changed — the cached copy is still valid. */
		VALID;
	}

	/**
	 * Checks whether the resource is still valid without comparing against another
	 * validity object.  Some implementations (e.g. HTTP without a
	 * {@code Last-Modified} header) can only return {@link Validity#UNKNOWN} here
	 * and require a second validity object for a meaningful comparison.
	 *
	 * @return {@link Validity#VALID} if the resource is unchanged,
	 *         {@link Validity#INVALID} if it has changed, or
	 *         {@link Validity#UNKNOWN} if the state cannot be determined.
	 */
	Validity getValid();

	/**
	 * Compares this validity against a second {@link SourceValidity} obtained from
	 * a fresh resolution of the same URI.  Implementations typically compare
	 * timestamps or ETags stored in both objects.
	 *
	 * @param validity a freshly obtained {@link SourceValidity} for the same
	 *                 resource; must not be {@code null}.
	 * @return {@link Validity#VALID} if the resource is unchanged,
	 *         {@link Validity#INVALID} if it has changed, or
	 *         {@link Validity#UNKNOWN} if the comparison is inconclusive (e.g.
	 *         the two objects are of incompatible types).
	 */
	Validity getValid(SourceValidity validity);
}

