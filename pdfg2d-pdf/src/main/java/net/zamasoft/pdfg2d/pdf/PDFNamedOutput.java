package net.zamasoft.pdfg2d.pdf;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Abstract base for a PDF output stream that has an associated resource name.
 * Subclasses produce named PDF resources such as Form XObjects.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class PDFNamedOutput extends PDFOutput {
	/**
	 * Constructs a PDFNamedOutput.
	 *
	 * @param out          the underlying output stream
	 * @param nameEncoding the encoding used for PDF name objects
	 * @throws IOException if an I/O error occurs
	 */
	public PDFNamedOutput(final OutputStream out, final String nameEncoding) throws IOException {
		super(out, nameEncoding);
	}

	/**
	 * Returns the PDF resource name assigned to this output stream.
	 *
	 * @return the resource name
	 */
	public abstract String getName();
}