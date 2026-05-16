package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.PDFNamedGraphicsOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;

/**
 * Implementation of a named graphics output (PDF Pattern or Form XObject with
 * a registered name).
 * <p>
 * This class owns a dedicated {@link PDFFragmentOutput} for the pattern/form
 * dictionary entries and a {@link ResourceFlow} for the resources referenced
 * inside its content stream.  It looks up global resource references from the
 * writer's shared name-to-reference map and promotes them into the local
 * resource dictionary on demand via {@link #useResource(String, String)}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class PDFNamedGraphicsOutputImpl extends PDFNamedGraphicsOutput {
	/** Fragment output for the pattern/form dictionary entries. */
	private final PDFFragmentOutput patternFlow;

	/** Resource dictionary for this named graphics output. */
	private final ResourceFlow resourceFlow;

	/** The PDF resource name used to reference this object (e.g. {@code "P0"}). */
	private final String name;

	/**
	 * Constructs a new PDFNamedGraphicsOutputImpl.
	 *
	 * @param pdfWriter    the owning PDF writer
	 * @param out          the raw output stream for this object's content stream
	 * @param patternFlow  the fragment output for the pattern/form dictionary
	 * @param resourceFlow the resource dictionary for this object
	 * @param width        the width of the graphics area in user units
	 * @param height       the height of the graphics area in user units
	 * @param name         the PDF resource name for this object
	 * @throws IOException if an I/O error occurs during initialization
	 */
	public PDFNamedGraphicsOutputImpl(final PDFWriter pdfWriter, final OutputStream out,
			final PDFFragmentOutput patternFlow, final ResourceFlow resourceFlow, final double width,
			final double height, final String name) throws IOException {
		super(pdfWriter, out, width, height);
		this.patternFlow = patternFlow;
		this.resourceFlow = resourceFlow;
		this.name = name;
	}

	/**
	 * Returns the owning writer cast to its concrete type.
	 *
	 * @return the {@link PDFWriterImpl} that created this named graphics output
	 */
	private PDFWriterImpl getPDFWriterImpl() {
		return (PDFWriterImpl) this.pdfWriter;
	}

	/**
	 * Ensures that the named resource is registered in this object's local resource
	 * dictionary.
	 * <p>
	 * If the resource is already present, this method is a no-op.  Otherwise the
	 * object reference is resolved from the writer's global name map and inserted
	 * under the given type category.
	 * </p>
	 *
	 * @param type the resource type (e.g. {@code "Font"}, {@code "XObject"})
	 * @param name the resource name (e.g. {@code "F0"}, {@code "I1"})
	 * @throws IOException if an I/O error occurs while writing to the resource
	 *                     dictionary
	 */
	public void useResource(final String type, final String name) throws IOException {
		if (this.resourceFlow.contains(name)) {
			return;
		}
		final var nameToResourceRef = this.getPDFWriterImpl().nameToResourceRef;
		final var objectRef = nameToResourceRef.get(name);
		this.resourceFlow.put(type, name, objectRef);
	}

	/**
	 * Returns the PDF resource name for this named graphics output.
	 *
	 * @return the resource name (e.g. {@code "P0"})
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Finalizes this named graphics output, then closes the pattern flow and
	 * resource flow.
	 *
	 * @throws IOException if an I/O error occurs during finalization
	 */
	public void close() throws IOException {
		super.close();
		this.patternFlow.close();
		this.resourceFlow.close();
	}
}