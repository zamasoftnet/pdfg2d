package net.zamasoft.pdfg2d.pdf;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Abstract base for an output stream that writes a single PDF fragment.
 * <p>
 * A PDF document is assembled from multiple fragments (indirect objects,
 * streams, dictionaries).  Each fragment is written through one instance of
 * this class and contributes its byte range to the cross-reference table.
 * </p>
 * <p>
 * Subclasses implement the actual encoding (RAW, deflate/binary, or
 * deflate/ASCII85) selected by the {@link Mode} passed to
 * {@link #startStream(Mode)} and {@link #startStreamFromHash(Mode)}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class PDFFragmentOutput extends PDFOutput {
	/**
	 * Stream encoding mode used when writing PDF stream objects.
	 */
	public enum Mode {
		/** Writes data directly to the PDF without any encoding or compression. */
		RAW,
		/** Applies Deflate compression; suitable for binary image/font data. */
		BINARY,
		/** Applies Deflate compression followed by ASCII85 encoding; suitable
		 *  for text-safe contexts (e.g. PostScript-embedded PDFs). */
		ASCII;
	}

	/**
	 * Constructs a fragment output stream.
	 *
	 * @param out          the underlying byte sink
	 * @param nameEncoding encoding used for PDF name objects
	 * @throws IOException if an I/O error occurs during construction
	 */
	protected PDFFragmentOutput(final OutputStream out, final String nameEncoding) throws IOException {
		super(out, nameEncoding);
	}

	/**
	 * Writes the start of an object.
	 * 
	 * @param ref the object reference
	 * @throws IOException in case of I/O error
	 */
	public abstract void startObject(ObjectRef ref) throws IOException;

	/**
	 * Writes the end of an object.
	 * 
	 * @throws IOException in case of I/O error
	 */
	public abstract void endObject() throws IOException;

	/**
	 * Writes the start of a stream.
	 * 
	 * @param mode the compression mode
	 * @return the output stream for the stream content
	 * @throws IOException in case of I/O error
	 */
	public abstract OutputStream startStream(Mode mode) throws IOException;

	/**
	 * Writes the start of a stream from within a dictionary (hash). This method
	 * closes the dictionary.
	 * 
	 * @param mode the compression mode
	 * @return the output stream for the stream content
	 * @throws IOException in case of I/O error
	 */
	public abstract OutputStream startStreamFromHash(Mode mode) throws IOException;
}