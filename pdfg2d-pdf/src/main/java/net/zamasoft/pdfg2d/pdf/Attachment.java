package net.zamasoft.pdfg2d.pdf;

/**
 * Represents an attachment in a PDF document.
 * <p>
 * The {@code afRelationship} controls the {@code /AFRelationship} of the
 * associated-file entry (PDF/A-3, PDF 2.0). E-invoices (Factur-X/ZUGFeRD)
 * require the invoice XML to be attached as {@code "Alternative"}.
 * </p>
 *
 * @param description    The description of the attachment.
 * @param mimeType       The MIME type of the attachment.
 * @param afRelationship The AFRelationship name (e.g. {@code "Alternative"},
 *                       {@code "Data"}, {@code "Source"}, {@code "Supplement"},
 *                       {@code "Unspecified"}), or null for {@code "Unspecified"}.
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record Attachment(String description, String mimeType, String afRelationship) {
    /**
     * Creates an attachment with no description or MIME type.
     */
    public Attachment() {
        this(null, null, null);
    }

    /**
     * Creates an attachment with an unspecified AFRelationship.
     *
     * @param description The description of the attachment.
     * @param mimeType    The MIME type of the attachment.
     */
    public Attachment(final String description, final String mimeType) {
        this(description, mimeType, null);
    }

    /**
     * Returns the AFRelationship, defaulting to {@code "Unspecified"}.
     *
     * @return the AFRelationship name
     */
    public String afRelationshipOrDefault() {
        return (this.afRelationship != null) ? this.afRelationship : "Unspecified";
    }
}
