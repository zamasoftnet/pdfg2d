package net.zamasoft.pdfg2d;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import net.zamasoft.pdfg2d.io.impl.FileFragmentedOutput;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.util.PDFUtils;

/**
 * Convenience {@link java.awt.Graphics2D} facade that writes directly to a PDF
 * file.
 * <p>
 * This class bridges the AWT {@code Graphics2D} API to the pdfg2d PDF output
 * pipeline.  It combines a {@link PDFWriterImpl}, a {@link PDFGC} graphics
 * context, and a {@link BridgeGraphics2D} adaptor so that any code written
 * against the standard Java 2D API can produce PDF output with no changes.
 * </p>
 * <p>
 * When constructed from a {@link File}, the writer owns the underlying output
 * and will close it when {@link #close()} is called.  When constructed from a
 * {@link PDFPageOutput} the caller retains ownership of the writer lifecycle.
 * </p>
 * <p>
 * Coordinate units follow the PDF convention: one point = 1/72 inch.  Use
 * {@link net.zamasoft.pdfg2d.pdf.util.PDFUtils#mmToPt} to convert millimetres.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PDFGraphics2D extends BridgeGraphics2D implements Closeable {
	/** {@code true} when this instance owns the underlying PDF writer and must close it. */
	private final boolean fileOut;

	/**
	 * Creates a single-page PDF at the given file path with explicit dimensions
	 * and parameters.
	 *
	 * @param file   the output file (created or overwritten)
	 * @param width  page width in points (1 pt = 1/72 in)
	 * @param height page height in points
	 * @param params PDF generation parameters
	 * @throws IOException if the file cannot be created or written
	 */
	@SuppressWarnings("resource")
	public PDFGraphics2D(final File file, final double width, final double height, final PDFParams params)
			throws IOException {
		super(new PDFGC(new PDFWriterImpl(new FileFragmentedOutput(file), params).nextPage(width, height)));
		this.fileOut = true;
	}

	/**
	 * Creates a single-page PDF at the given file path with explicit dimensions
	 * and default PDF parameters.
	 *
	 * @param file   the output file
	 * @param width  page width in points
	 * @param height page height in points
	 * @throws IOException if the file cannot be created or written
	 */
	public PDFGraphics2D(final File file, final double width, final double height) throws IOException {
		this(file, width, height, PDFParams.createDefault());
	}

	/**
	 * Creates a single-page A4-sized PDF at the given file path with default
	 * parameters.
	 *
	 * @param file the output file
	 * @throws IOException if the file cannot be created or written
	 */
	public PDFGraphics2D(final File file) throws IOException {
		this(file, PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM), PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM));
	}

	/**
	 * Creates a graphics context that draws onto an existing {@link PDFPageOutput}.
	 * The caller retains ownership of the page output and the enclosing writer.
	 *
	 * @param pageOut the page output to draw onto
	 * @throws IOException if an I/O error occurs during initialisation
	 */
	public PDFGraphics2D(final PDFPageOutput pageOut) throws IOException {
		super(new PDFGC(pageOut));
		this.fileOut = false;
	}

	/**
	 * Closes the graphics context and, if this instance owns the underlying PDF
	 * writer (i.e. was constructed from a {@link File}), also finalises and closes
	 * the writer.
	 *
	 * @throws IOException if an I/O error occurs while flushing or closing
	 */
	@Override
	public void close() throws IOException {
		final var pdfGc = (PDFGC) this.gc;
		pdfGc.getPDFGraphicsOutput().close();
		if (!this.fileOut) {
			return;
		}
		pdfGc.getPdfWriter().close();
	}
}
