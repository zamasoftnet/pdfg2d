package net.zamasoft.pdfg2d.pdf;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Abstract base for a PDF graphics output stream that has an associated
 * resource name. Instances correspond to named PDF resources such as Form
 * XObjects that are also graphics content streams.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class PDFNamedGraphicsOutput extends PDFGraphicsOutput {
	/**
	 * Constructs a PDFNamedGraphicsOutput.
	 *
	 * @param pdfWriter the PDF writer that owns this output
	 * @param out       the underlying output stream
	 * @param width     the width of the graphics area in points
	 * @param height    the height of the graphics area in points
	 * @throws IOException if an I/O error occurs
	 */
	protected PDFNamedGraphicsOutput(final PDFWriter pdfWriter, final OutputStream out, final double width,
			final double height) throws IOException {
		super(pdfWriter, out, width, height);
	}

	/**
	 * Returns the name of the graphics.
	 * 
	 * @return the name
	 */
	public abstract String getName();
}