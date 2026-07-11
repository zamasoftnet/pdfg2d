package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFOutput;

/**
 * Destination for writing indirect objects that carry no streams: either
 * directly into the document body or packed into object streams when
 * enabled. Callers write the object body (dictionary/array tokens only)
 * between {@link #startObject} and {@link #endObject}.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
interface PDFObjectSink {

	/**
	 * Opens the given object and returns the token output to write its body.
	 *
	 * @param ref the object reference
	 * @return the output for the object body
	 * @throws IOException if an I/O error occurs
	 */
	PDFOutput startObject(ObjectRef ref) throws IOException;

	/**
	 * Closes the object opened by {@link #startObject}.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void endObject() throws IOException;
}
