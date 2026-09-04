package net.zamasoft.pdfg2d.pdf;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.resolver.Source;

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
	 * Adds an in-memory image generated as part of PDF rendering.
	 *
	 * <p>
	 * Generated images are embedded losslessly with Flate compression and are
	 * never resized, regardless of the normal image compression and maximum-size
	 * settings. Alpha is emitted through the usual soft-mask path.
	 * </p>
	 *
	 * @param image the generated buffered image to add
	 * @return the PDF image representation
	 * @throws IOException if an I/O error occurs
	 */
	Image addGeneratedImage(final BufferedImage image) throws IOException;

	/**
	 * Returns the shared ICC profile stream used to identify renderer-generated
	 * RGB image samples as sRGB, creating the indirect stream object on first use.
	 *
	 * <p>
	 * This is the stream reference itself, rather than a named page resource:
	 * image dictionaries embed it directly as
	 * {@code /ColorSpace [/ICCBased profile-ref]}.
	 * </p>
	 *
	 * @return the shared sRGB ICC profile stream reference
	 * @throws IOException if the profile stream cannot be written
	 */
	ObjectRef useGeneratedImageRGBProfile() throws IOException;

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
	 * Creates a Type 4 free-form Gouraud mesh shading and its PatternType 2
	 * wrapper. The supplied vertex data is already bit-packed according to the
	 * fixed 8-bit flags, 32-bit coordinates and 16-bit components used by this
	 * API.
	 *
	 * @param pageHeight page or form height used by the PDF coordinate flip
	 * @param matrix     gradient-local to current user-space transform
	 * @param colorType  process color type for the packed components
	 * @param decode     coordinate and component decode ranges
	 * @param vertexData complete packed Type 4 vertex stream
	 * @return the Pattern resource name
	 * @throws IOException if the pattern cannot be written
	 */
	String createType4ShadingPattern(double pageHeight, AffineTransform matrix, Color.Type colorType,
			double[] decode, byte[] vertexData) throws IOException;

	/**
	 * Creates a luminosity soft mask from an existing (grayscale) shading
	 * pattern: a Form XObject filling the page with the pattern, wrapped in
	 * an ExtGState whose {@code /SMask} references it. Used to reproduce
	 * gradients with per-stop alpha.
	 *
	 * @param shadingPatternName the resource name of the grayscale shading
	 *                           pattern encoding the alpha ramp
	 * @param width              the mask extent (page width)
	 * @param height             the mask extent (page height)
	 * @return the resource name of the created ExtGState
	 * @throws IOException                   if an I/O error occurs
	 * @throws UnsupportedOperationException if the implementation does not
	 *                                       support soft masks
	 */
	default String createLuminositySoftMask(String shadingPatternName, double width, double height)
			throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Registers a {@code /Separation} color space for the given spot
	 * colorant, creating it on first use; the same colorant name always
	 * resolves to the same resource within the document. The tint transform
	 * maps tint 1 to the (color-mode adjusted) alternate color and tint 0 to
	 * white.
	 *
	 * @param colorantName the colorant name (e.g. {@code "PANTONE 185 C"})
	 * @param alternate    the alternate process color at full tint
	 * @return the color space resource name
	 * @throws IOException if an I/O error occurs
	 */
	default String useSeparation(String colorantName, net.zamasoft.pdfg2d.gc.paint.Color alternate)
			throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Registers a {@code /DeviceN} color space for the given colorant set,
	 * creating it on first use; the same set (by name, in order) always
	 * resolves to the same resource within the document. The tint transform
	 * (a PostScript calculator function) combines the colorants' alternate
	 * colors in the document's process color space.
	 *
	 * @param colorants the colorants (2..32, distinct names)
	 * @return the color space resource name
	 * @throws IOException if an I/O error occurs
	 */
	default String useDeviceN(net.zamasoft.pdfg2d.gc.paint.SpotColor[] colorants) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Registers the ICCBased color space for RGB content when an RGB profile
	 * is configured, creating it on first use.
	 *
	 * @return the color space resource name, or {@code null} when no RGB
	 *         profile is configured
	 * @throws IOException if an I/O error occurs
	 */
	default String useICCBasedRGB() throws IOException {
		return null;
	}

	/**
	 * Creates a named optional content group (layer). Requires PDF 1.5+.
	 *
	 * @param name        the layer name shown in viewers
	 * @param viewable    whether the layer is shown on screen
	 *                    ({@code /Usage /View})
	 * @param printable   whether the layer is printed ({@code /Usage /Print})
	 * @param initiallyOn whether the layer starts enabled in the default
	 *                    configuration
	 * @param locked      whether viewers must not let the user toggle it
	 * @return the layer handle
	 * @throws IOException if an I/O error occurs
	 */
	default PDFOptionalContentGroup createOptionalContentGroup(String name, boolean viewable, boolean printable,
			boolean initiallyOn, boolean locked) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Starts a new document part (PDF/VT): pages created afterwards belong
	 * to a new {@code DPart} leaf, typically one per recipient record in
	 * variable-data printing.
	 *
	 * @param metadata document part metadata written as the {@code /DPM}
	 *                 dictionary (string values), or {@code null}
	 * @throws IOException                   if an I/O error occurs
	 * @throws UnsupportedOperationException when the document is not PDF/VT
	 */
	default void nextDocumentPart(java.util.Map<String, String> metadata) throws IOException {
		throw new UnsupportedOperationException();
	}

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
