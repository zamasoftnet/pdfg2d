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
 * @param lang      the document language (BCP 47, e.g. {@code "ja"} or
 *                  {@code "en-US"}) written to the catalog {@code /Lang}, or
 *                  {@code null} to omit; required when a PDF/UA part is set
 * @param pdfuaPart the declared PDF/UA part: {@code 0} for none, {@code 1}
 *                  for PDF/UA-1 (ISO 14289-1, PDF 1.7 based) or {@code 2}
 *                  for PDF/UA-2 (ISO 14289-2:2024, PDF 2.0 based). Declaring
 *                  a part adds the {@code pdfuaid} XMP schema, forces
 *                  {@code DisplayDocTitle}, and requires a document title
 *                  and language; part 2 additionally writes the PDF 2.0
 *                  standard structure namespace
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public record TaggedParams(String lang, int pdfuaPart) {

	/** Tagged output without PDF/UA declaration and without a language. */
	public static final TaggedParams TAGGED = new TaggedParams(null, 0);

	public TaggedParams {
		if (pdfuaPart < 0 || pdfuaPart > 2) {
			throw new IllegalArgumentException("unsupported PDF/UA part: " + pdfuaPart);
		}
		if (pdfuaPart > 0 && (lang == null || lang.isEmpty())) {
			throw new IllegalArgumentException("PDF/UA requires a document language.");
		}
	}

	/**
	 * Compatibility form of the pre-part boolean constructor.
	 *
	 * @param lang  the document language, or {@code null}
	 * @param pdfua whether to declare PDF/UA-1
	 */
	public TaggedParams(final String lang, final boolean pdfua) {
		this(lang, pdfua ? 1 : 0);
	}

	/**
	 * Returns whether any PDF/UA part is declared.
	 *
	 * @return {@code true} when {@link #pdfuaPart()} is non-zero
	 */
	public boolean pdfua() {
		return this.pdfuaPart > 0;
	}

	/**
	 * Creates a PDF/UA-1 configuration with the given language.
	 *
	 * @param lang the document language (BCP 47)
	 * @return a PDF/UA-1 tagged configuration
	 */
	public static TaggedParams pdfua(final String lang) {
		return new TaggedParams(lang, 1);
	}

	/**
	 * Creates a PDF/UA-2 configuration with the given language. The document
	 * must use a PDF 2.0 base version.
	 *
	 * @param lang the document language (BCP 47)
	 * @return a PDF/UA-2 tagged configuration
	 */
	public static TaggedParams pdfua2(final String lang) {
		return new TaggedParams(lang, 2);
	}
}
