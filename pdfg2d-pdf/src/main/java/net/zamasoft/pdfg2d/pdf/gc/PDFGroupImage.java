package net.zamasoft.pdfg2d.pdf.gc;

import java.io.IOException;
import java.io.OutputStream;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFNamedGraphicsOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;

/**
 * Represents an offscreen group image in PDF.
 * This class corresponds to a PDF Form XObject.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class PDFGroupImage extends PDFNamedGraphicsOutput implements Image {
	private final String name;

	private final ObjectRef objectRef;

	/** OCG flag: not visible on screen. */
	public static final int VIEW_OFF = 1;
	/** OCG flag: not printed. */
	public static final int PRINT_OFF = 2;

	protected int ocgFlags = 0;

	/** Optional content group applied to this Form XObject, or {@code null}. */
	protected net.zamasoft.pdfg2d.pdf.PDFOptionalContentGroup ocgLayer = null;

	/**
	 * Constructs a PDFGroupImage.
	 *
	 * @param pdfWriter the PDF writer that owns this resource
	 * @param out       the underlying output stream
	 * @param width     the width of the Form XObject in points
	 * @param height    the height of the Form XObject in points
	 * @param name      the PDF resource name for this Form XObject
	 * @param objectRef the indirect object reference for this Form XObject
	 * @throws IOException if an I/O error occurs
	 */
	protected PDFGroupImage(final PDFWriter pdfWriter, final OutputStream out, final double width, final double height,
			final String name, final ObjectRef objectRef) throws IOException {
		super(pdfWriter, out, width, height);
		this.name = name;
		this.objectRef = objectRef;
	}

	/**
	 * Sets the Optional Content Group (OCG) flags.
	 * 
	 * @param ocgFlags The flags to set.
	 */
	public void setOCG(final int ocgFlags) {
		this.ocgFlags = ocgFlags;
	}

	/**
	 * Assigns a named optional content group (layer) to this Form XObject.
	 * Takes precedence over the flag-based {@link #setOCG(int)}.
	 *
	 * @param layer the layer created by
	 *              {@link net.zamasoft.pdfg2d.pdf.PDFWriter#createOptionalContentGroup}
	 */
	public void setOCG(final net.zamasoft.pdfg2d.pdf.PDFOptionalContentGroup layer) {
		this.ocgLayer = layer;
	}

	/**
	 * {@inheritDoc}
	 * Draws this Form XObject into the given graphics context.
	 * Only supported when the target is a {@link PDFGC}.
	 */
	@Override
	public void drawTo(final GC gc) throws GraphicsException {
		if (gc instanceof PDFGC pdfgc) {
			pdfgc.drawPDFImage(this.name, this.width, this.height);
		}
	}

	@Override
	public String getName() {
		return this.name;
	}

	/**
	 * Returns the object reference for this group image.
	 * 
	 * @return The object reference.
	 */
	public ObjectRef getObjectRef() {
		return this.objectRef;
	}

	/** {@inheritDoc} */
	@Override
	public String getAltString() {
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return this.name;
	}

	/**
	 * Two {@code PDFGroupImage} instances are considered equal if they have the
	 * same resource name.
	 *
	 * @param o the object to compare
	 * @return {@code true} if the given object is a {@code PDFGroupImage} with the
	 *         same name
	 */
	@Override
	public boolean equals(final Object o) {
		if (o instanceof PDFGroupImage other) {
			return other.name.equals(this.name);
		}
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return this.name.hashCode();
	}
}
