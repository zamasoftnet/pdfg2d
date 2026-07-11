package net.zamasoft.pdfg2d.pdf.params;

/**
 * Enables Tagged PDF output: content is wrapped in marked-content sequences
 * and a logical structure tree ({@code /StructTreeRoot}) is generated.
 * <p>
 * Without explicit structure calls (see
 * {@link net.zamasoft.pdfg2d.pdf.PDFPageOutput#beginStructElement(String)}),
 * each text run is tagged as a paragraph ({@code P}) and each image as a
 * {@code Figure}. Callers that know the document's logical structure — such
 * as an HTML layout engine — should group content explicitly to produce
 * meaningful structure.
 * </p>
 *
 * @param lang  the document language (BCP 47, e.g. {@code "ja"} or
 *              {@code "en-US"}) written to the catalog {@code /Lang}, or
 *              {@code null} to omit; required when {@code pdfua} is set
 * @param pdfua whether to declare and enforce PDF/UA-1 (ISO 14289-1)
 *              conformance: adds the {@code pdfuaid} XMP schema, forces
 *              {@code DisplayDocTitle}, and requires a document title and
 *              language
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public record TaggedParams(String lang, boolean pdfua) {

	/** Tagged output without PDF/UA declaration and without a language. */
	public static final TaggedParams TAGGED = new TaggedParams(null, false);

	public TaggedParams {
		if (pdfua && (lang == null || lang.isEmpty())) {
			throw new IllegalArgumentException("PDF/UA requires a document language.");
		}
	}

	/**
	 * Creates a PDF/UA-1 configuration with the given language.
	 *
	 * @param lang the document language (BCP 47)
	 * @return a PDF/UA tagged configuration
	 */
	public static TaggedParams pdfua(final String lang) {
		return new TaggedParams(lang, true);
	}
}
