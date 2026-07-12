package net.zamasoft.pdfg2d.pdf;

/**
 * Describes the electronic-invoice (Factur-X / ZUGFeRD / XRechnung) XML that is
 * embedded in a hybrid PDF/A-3 invoice. Setting this on the
 * {@link PDFMetaInfo} makes the writer emit the {@code fx:} XMP extension schema
 * that e-invoice validators require; the invoice XML itself is supplied by the
 * caller as an {@link Attachment} with {@code afRelationship = "Alternative"}
 * and the file name {@link #documentFileName()}.
 * <p>
 * The registered XMP namespace is
 * {@code urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#} (prefix
 * {@code fx}). This class does not generate or validate the invoice XML
 * content; that is the caller's responsibility.
 * </p>
 *
 * @param documentType     the {@code fx:DocumentType}, typically
 *                         {@code "INVOICE"} or {@code "ORDER"}
 * @param documentFileName the {@code fx:DocumentFileName}; the attachment must
 *                         use this exact name (e.g. {@code "factur-x.xml"},
 *                         {@code "zugferd-invoice.xml"} or {@code "xrechnung.xml"})
 * @param version          the {@code fx:Version} of the profile, e.g.
 *                         {@code "1.0"}
 * @param conformanceLevel the {@code fx:ConformanceLevel}, e.g.
 *                         {@code "MINIMUM"}, {@code "BASIC WL"}, {@code "BASIC"},
 *                         {@code "EN 16931"} or {@code "EXTENDED"}
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record FacturX(String documentType, String documentFileName, String version, String conformanceLevel) {

	/** The standard Factur-X invoice file name. */
	public static final String FACTUR_X_FILENAME = "factur-x.xml";

	/**
	 * Creates a Factur-X descriptor for an invoice using the standard
	 * {@code factur-x.xml} file name and version {@code "1.0"}.
	 *
	 * @param conformanceLevel the Factur-X conformance level (e.g.
	 *                         {@code "EN 16931"})
	 * @return the descriptor
	 */
	public static FacturX invoice(final String conformanceLevel) {
		return new FacturX("INVOICE", FACTUR_X_FILENAME, "1.0", conformanceLevel);
	}
}
