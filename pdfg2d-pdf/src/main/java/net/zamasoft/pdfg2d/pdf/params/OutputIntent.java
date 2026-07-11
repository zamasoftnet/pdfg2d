package net.zamasoft.pdfg2d.pdf.params;

/**
 * Configuration for the {@code /OutputIntents} entry of the catalog: the
 * characterized printing condition (or display condition) the document's
 * device-dependent colors are intended for.
 * <p>
 * For PDF/X, set {@code outputConditionIdentifier} to a characterization
 * registered at the ICC registry (e.g. {@code "FOGRA39"} or
 * {@code "JC200103"} for Japan Color 2001 Coated) together with
 * {@code registryName} {@code "http://www.color.org"}; for conditions that
 * are not registered, supply a human-readable {@code info} and an embedded
 * ICC profile instead.
 * </p>
 *
 * @param outputConditionIdentifier identifier of the intended output
 *                                  condition (required)
 * @param outputCondition           human-readable name of the condition, or
 *                                  {@code null}
 * @param registryName              registry holding the identifier (usually
 *                                  {@code "http://www.color.org"}), or
 *                                  {@code null} for unregistered conditions
 * @param info                      additional human-readable description, or
 *                                  {@code null}; recommended for PDF/X when
 *                                  the condition is not registered
 * @param iccProfile                ICC profile to embed as
 *                                  {@code DestOutputProfile}, or {@code null}
 *                                  when the identifier alone suffices
 * @param colorComponents           number of color components of the profile
 *                                  (3 for RGB, 4 for CMYK)
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public record OutputIntent(
		String outputConditionIdentifier,
		String outputCondition,
		String registryName,
		String info,
		byte[] iccProfile,
		int colorComponents) {

	/** Registry name of the ICC characterization registry. */
	public static final String ICC_REGISTRY = "http://www.color.org";

	public OutputIntent {
		if (outputConditionIdentifier == null || outputConditionIdentifier.isEmpty()) {
			throw new IllegalArgumentException("outputConditionIdentifier is required.");
		}
		if (iccProfile != null && colorComponents != 3 && colorComponents != 4 && colorComponents != 1) {
			throw new IllegalArgumentException("colorComponents must be 1, 3 or 4.");
		}
	}
}
