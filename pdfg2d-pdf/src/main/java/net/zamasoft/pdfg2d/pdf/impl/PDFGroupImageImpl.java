package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;
import java.io.OutputStream;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Implementation of an offscreen group image (Form XObject).
 * This class handles resource management and Optional Content Group (OCG)
 * features for group-based PDF content.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PDFGroupImageImpl extends PDFGroupImage {
	private final PDFFragmentOutput groupFlow, formFlow;
	private final ResourceFlow resourceFlow;

	/**
	 * Constructs a new PDFGroupImageImpl.
	 *
	 * @param pdfWriter    the owning PDF writer
	 * @param out          the raw output stream for this Form XObject's content stream
	 * @param groupFlow    the fragment that holds the enclosing group structure
	 * @param resourceFlow the resource dictionary for this Form XObject
	 * @param width        the width of the group image in user units
	 * @param height       the height of the group image in user units
	 * @param name         the PDF resource name used to reference this XObject
	 * @param objectRef    the indirect object reference for this Form XObject
	 * @param formFlow     the fragment that holds the Form XObject dictionary
	 *                     entries (used to append optional content references)
	 * @throws IOException if an I/O error occurs during initialization
	 */
	public PDFGroupImageImpl(final PDFWriterImpl pdfWriter, final OutputStream out, final PDFFragmentOutput groupFlow,
			final ResourceFlow resourceFlow, final double width, final double height, final String name,
			final ObjectRef objectRef, final PDFFragmentOutput formFlow) throws IOException {
		super(pdfWriter, out, width, height, name, objectRef);
		this.groupFlow = groupFlow;
		this.formFlow = formFlow;
		this.resourceFlow = resourceFlow;
	}

	/**
	 * Returns the owning writer cast to its concrete type.
	 *
	 * @return the {@link PDFWriterImpl} that created this group image
	 */
	private PDFWriterImpl getPDFWriterImpl() {
		return (PDFWriterImpl) this.pdfWriter;
	}

	/**
	 * Ensures that the named resource is registered in this Form XObject's resource
	 * dictionary.
	 * <p>
	 * If the resource has already been added, this method is a no-op.  Otherwise
	 * the object reference is looked up from the writer's global resource map and
	 * inserted under the given type category.
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
	 * Finalizes this group image, writing Optional Content Group (OCG) objects if
	 * needed, then delegating to the superclass and closing the group and resource
	 * flows.
	 * <p>
	 * If OCG flags are set, PDF 1.5 or later is required; an
	 * {@link UnsupportedOperationException} is thrown for older targets.
	 * </p>
	 *
	 * @throws IOException                   if an I/O error occurs during
	 *                                       finalization
	 * @throws UnsupportedOperationException if OCG flags are set but the target
	 *                                       PDF version is older than 1.5
	 */
	public void close() throws IOException {
		if (this.ocgFlags != 0) {
			final var pdfWriter = this.getPDFWriterImpl();
			if (pdfWriter.getParams().version().v < PDFParams.Version.V_1_5.v) {
				throw new UnsupportedOperationException("OCG feature requires PDF >= 1.5.");
			}

			// Add Optional Content reference to the Form Dictionary
			this.formFlow.writeName("OC");
			final var ocgRef = pdfWriter.nextOCG();
			this.formFlow.writeObjectRef(ocgRef);
			this.formFlow.lineBreak();
			this.formFlow.close();

			// Define the Optional Content Group object
			final var objectsFlow = pdfWriter.objectsFlow;
			objectsFlow.startObject(ocgRef);
			objectsFlow.startHash();
			objectsFlow.writeName("Type");
			objectsFlow.writeName("OCG");
			objectsFlow.writeName("Name");
			objectsFlow.writeText("WATERMARK");
			objectsFlow.writeName("Usage");
			objectsFlow.startHash();

			objectsFlow.writeName("View");
			objectsFlow.startHash();
			objectsFlow.writeName("ViewState");
			objectsFlow.writeName((this.ocgFlags & VIEW_OFF) != 0 ? "OFF" : "ON");
			objectsFlow.endHash();

			objectsFlow.writeName("Print");
			objectsFlow.startHash();
			objectsFlow.writeName("PrintState");
			objectsFlow.writeName((this.ocgFlags & PRINT_OFF) != 0 ? "OFF" : "ON");
			objectsFlow.endHash();

			objectsFlow.endHash(); // End Usage
			objectsFlow.endHash(); // End OCG
			objectsFlow.endObject();
		}

		super.close();
		this.groupFlow.close();
		this.resourceFlow.close();
	}
}
