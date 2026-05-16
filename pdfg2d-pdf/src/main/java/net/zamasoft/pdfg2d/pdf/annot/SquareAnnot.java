package net.zamasoft.pdfg2d.pdf.annot;

import java.io.IOException;

import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;

/**
 * A PDF square (rectangle) annotation that draws a rectangle on the page
 * (PDF spec section 12.5.6.8).
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class SquareAnnot extends Annot {
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void writeTo(final PDFOutput out, final PDFPageOutput pageOut) throws IOException {
		super.writeTo(out, pageOut);

		out.writeName("Subtype");
		out.writeName("Square");
		out.breakBefore();
	}
}
