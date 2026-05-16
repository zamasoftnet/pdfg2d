package net.zamasoft.pdfg2d.pdf.gc;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Represents a PDF image resource.
 * 
 * @param name   The PDF resource name.
 * @param width  The image width.
 * @param height The image height.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record PDFImage(String name, double width, double height) implements Image {
	/** {@inheritDoc} */
	@Override
	public double getWidth() {
		return this.width;
	}

	/** {@inheritDoc} */
	@Override
	public double getHeight() {
		return this.height;
	}

	/**
	 * {@inheritDoc}
	 * Draws this PDF image resource into the given graphics context.
	 * Only supported when the target is a {@link PDFGC}.
	 */
	@Override
	public void drawTo(final GC gc) throws GraphicsException {
		if (gc instanceof PDFGC pdfgc) {
			pdfgc.drawPDFImage(this.name, this.width, this.height);
		}
	}

	/**
	 * Returns {@code null} as PDF image resources do not carry alt text.
	 *
	 * @return {@code null}
	 */
	@Override
	public String getAltString() {
		return null;
	}

	/**
	 * Returns the PDF resource name of this image.
	 *
	 * @return the resource name string
	 */
	@Override
	public String toString() {
		return this.name;
	}

	/**
	 * Returns the PDF resource name of this image.
	 * This is a convenience accessor that delegates to the record component.
	 *
	 * @return the resource name
	 */
	public String getName() {
		return this.name();
	}
}
