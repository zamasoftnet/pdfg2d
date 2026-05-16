package net.zamasoft.pdfg2d.pdf;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.resolver.Source;

/**
 * Interface for writing PDF documents.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface PDFWriter extends Closeable {
	/**
	 * Minimum page width.
	 */
	double MIN_PAGE_WIDTH = 3;

	/**
	 * Minimum page height.
	 */
	double MIN_PAGE_HEIGHT = 3;

	/**
	 * Maximum page width.
	 * <p>
	 * Limitation due to PDF implementation limits; larger pages may appear blank in
	 * Adobe Reader.
	 * </p>
	 */
	double MAX_PAGE_WIDTH = 14400;

	/**
	 * Maximum page height.
	 * <p>
	 * Limitation due to PDF implementation limits; larger pages may appear blank in
	 * Adobe Reader.
	 * </p>
	 */
	double MAX_PAGE_HEIGHT = 14400;

	/**
	 * Returns the parameters used to configure this PDF writer.
	 *
	 * @return PDF generation parameters
	 */
	PDFParams getParams();

	/**
	 * Returns the font manager for text drawing context.
	 * 
	 * @return the font manager
	 */
	FontManager getFontManager();

	/**
	 * Loads an image from the specified source.
	 * 
	 * @param source The image source
	 * @return The PDF image representation
	 * @throws IOException If an I/O error occurs
	 */
	Image loadImage(final Source source) throws IOException;

	/**
	 * Adds an image from a BufferedImage.
	 * 
	 * @param image The buffered image to add
	 * @return The PDF image representation
	 * @throws IOException If an I/O error occurs
	 */
	Image addImage(final BufferedImage image) throws IOException;

	/**
	 * Adds an attachment file.
	 * 
	 * @param name       The attachment name
	 * @param attachment The attachment metadata
	 * @return Output stream to write the attachment content
	 * @throws IOException If an I/O error occurs
	 */
	OutputStream addAttachment(final String name, final Attachment attachment) throws IOException;

	/**
	 * Creates a special extended graphics state.
	 * 
	 * @return the output context for the graphics state
	 * @throws IOException in case of I/O error
	 */
	PDFNamedOutput createSpecialGraphicsState() throws IOException;

	/**
	 * Creates a group image, used for transparent images, annotations, etc.
	 * 
	 * @param width  the width
	 * @param height the height
	 * @return the group image
	 * @throws IOException in case of I/O error
	 */
	PDFGroupImage createGroupImage(double width, double height) throws IOException;

	/**
	 * Creates a tiling pattern.
	 * <p>
	 * The returned PDFNamedGraphicsOutput must be closed after writing the pattern.
	 * </p>
	 * 
	 * @param width      pattern width
	 * @param height     pattern height
	 * @param pageHeight page height
	 * @param at         transformation matrix
	 * @return the pattern output context; the name can be used for referencing
	 * @throws IOException in case of I/O error
	 */
	PDFNamedGraphicsOutput createTilingPattern(double width, double height, double pageHeight, AffineTransform at)
			throws IOException;

	/**
	 * Creates a shading pattern.
	 * <p>
	 * The returned PDFNamedOutput must be closed after writing the pattern.
	 * </p>
	 * 
	 * @param pageHeight page height
	 * @param at         transformation matrix
	 * @return the pattern output context; the name can be used for referencing
	 * @throws IOException in case of I/O error
	 */
	PDFNamedOutput createShadingPattern(double pageHeight, AffineTransform at) throws IOException;

	/**
	 * Creates a new page.
	 * 
	 * @param width  Page width
	 * @param height Page height
	 * @return The page output context
	 * @throws IOException If an I/O error occurs
	 */
	PDFPageOutput nextPage(final double width, final double height) throws IOException;

	/**
	 * Returns the value associated with the given key in this writer's attribute
	 * map, or {@code null} if no mapping exists.
	 *
	 * @param key attribute key
	 * @return associated value, or {@code null}
	 */
	Object getAttribute(Object key);

	/**
	 * Stores a key/value pair in this writer's attribute map.
	 * <p>
	 * The attribute map is a general-purpose store for cooperating components that
	 * need to share state through the writer without coupling to each other
	 * directly.
	 * </p>
	 *
	 * @param key   attribute key
	 * @param value attribute value
	 */
	void putAttribute(Object key, Object value);

	/**
	 * Finishes building the PDF.
	 * 
	 * @throws IOException in case of I/O error
	 */
	@Override
	void close() throws IOException;
}
