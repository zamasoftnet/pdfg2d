package net.zamasoft.pdfg2d.pdf.font;

import java.io.IOException;
import java.io.Serializable;

import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.XRef;

/**
 * Represents a font that can be embedded in or referenced from a PDF document.
 * Extends the base {@link Font} interface with PDF-specific operations such as
 * writing font dictionary objects to the PDF output stream.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface PDFFont extends Font, Serializable {
	/**
	 * Returns the font name used for identification within the PDF document.
	 * 
	 * @return the font name
	 */
	public String getName();

	/**
	 * Writes font information as a PDF object.
	 * 
	 * @param out  the PDF fragment output stream
	 * @param xref the cross-reference table
	 * @throws IOException if an I/O error occurs
	 */
	public void writeTo(PDFFragmentOutput out, XRef xref) throws IOException;
}
