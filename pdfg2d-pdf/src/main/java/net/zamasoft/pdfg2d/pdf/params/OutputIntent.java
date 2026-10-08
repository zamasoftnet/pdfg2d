package net.zamasoft.pdfg2d.pdf.params;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;

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

	/**
	 * What disqualifies an ICC profile as the {@code DestOutputProfile} of an
	 * output intent, in the order {@link OutputIntent#checkProfile} tests them.
	 *
	 * @since 1.3
	 */
	public enum ProfileProblem {
		/** Not an output (class {@code prtr}) profile. */
		PROFILE_CLASS,
		/** PDF/X only: not a CMYK profile. */
		COLOR_SPACE,
		/**
		 * Neither 1, 3 nor 4 components; for PDF/X, the profile or the
		 * declared count is not 4.
		 */
		COMPONENT_COUNT,
		/**
		 * PDF/X-1a and PDF/X-3, which are based on PDF 1.4: an ICC version 4
		 * profile (it needs PDF 1.5).
		 */
		ICC_VERSION
	}

	/**
	 * Checks an ICC profile as the {@code DestOutputProfile} of an output
	 * intent written to {@code version}. The writer refuses a PDF/X output
	 * intent with any problem; foliojet4 answers each one with its own error
	 * (PDF/X) or warning (other versions) before writing.
	 * <p>
	 * The profile's getters read its header and may throw for a profile the
	 * color management module cannot read; the caller decides how to answer
	 * that.
	 * </p>
	 *
	 * @param profile            the parsed profile
	 * @param declaredComponents the number of color components the output
	 *                           intent declares
	 * @param version            the PDF version the output intent is written to
	 * @return the first problem, or {@code null} if the profile is acceptable
	 * @since 1.3
	 */
	public static ProfileProblem checkProfile(final ICC_Profile profile, final int declaredComponents,
			final PDFParams.Version version) {
		final boolean pdfX = version.isPdfX();
		if (profile.getProfileClass() != ICC_Profile.CLASS_OUTPUT) {
			return ProfileProblem.PROFILE_CLASS;
		}
		if (pdfX && profile.getColorSpaceType() != ColorSpace.TYPE_CMYK) {
			return ProfileProblem.COLOR_SPACE;
		}
		final int components = profile.getNumComponents();
		if ((components != 1 && components != 3 && components != 4)
				|| (pdfX && (components != 4 || declaredComponents != 4))) {
			return ProfileProblem.COMPONENT_COUNT;
		}
		if (version.isPdfXOnPdf14() && profile.getMajorVersion() >= 4) {
			return ProfileProblem.ICC_VERSION;
		}
		return null;
	}

	/**
	 * Whether a name is printable ASCII, as PDF/X requires of the output
	 * condition identifier and the registry name (ICC registry names are all
	 * ASCII; a RIP cannot match anything else).
	 *
	 * @param name the identifier or registry name
	 * @return {@code true} if every character is in U+0020 to U+007E
	 * @since 1.3
	 */
	public static boolean isPrintableAscii(final String name) {
		return name.chars().allMatch(c -> c >= 0x20 && c <= 0x7E);
	}

	public OutputIntent {
		if (outputConditionIdentifier == null || outputConditionIdentifier.isEmpty()) {
			throw new IllegalArgumentException("outputConditionIdentifier is required.");
		}
		if (iccProfile != null && colorComponents != 3 && colorComponents != 4 && colorComponents != 1) {
			throw new IllegalArgumentException("colorComponents must be 1, 3 or 4.");
		}
	}
}
