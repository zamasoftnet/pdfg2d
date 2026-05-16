package net.zamasoft.pdfg2d.pdf.impl;

import net.zamasoft.pdfg2d.pdf.Attachment;
import net.zamasoft.pdfg2d.pdf.ObjectRef;

/**
 * Represents a PDF file specification (filespec) dictionary entry used for
 * embedded file attachments.
 * <p>
 * A filespec bundles the raw {@link Attachment} data together with the display
 * name of the file and the indirect object reference that will be written to
 * the PDF cross-reference table.  Instances are created by the PDF writer when
 * an attachment is registered and are later serialized into the
 * {@code EmbeddedFiles} name tree.
 * </p>
 *
 * @param attachment the attachment metadata and content to embed
 * @param name       the file name as it should appear inside the PDF
 * @param ref        the indirect object reference allocated for this filespec
 *                   dictionary
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
record Filespec(Attachment attachment, String name, ObjectRef ref) {
}
