package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import net.zamasoft.pdfg2d.pdf.ObjectRef;

/**
 * Manages the resource dictionary for a PDF page or Form XObject.
 * <p>
 * On construction, the surrounding hash is opened and the {@code ProcSet} entry
 * is written immediately.  Subsequent calls to {@link #put(String, String, ObjectRef)}
 * lazily create per-type sub-dictionaries (e.g. {@code Font}, {@code XObject})
 * and insert name/reference pairs into them.  Calling {@link #close()} closes
 * all open sub-dictionaries and the forked fragment.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class ResourceFlow {
	private final PDFFragmentOutputImpl out;

	private final Map<String, PDFFragmentOutputImpl> typeToFlow = new TreeMap<>();
	private final List<PDFFragmentOutputImpl> flowList = new ArrayList<>();
	private final Map<String, ObjectRef> idToObjectRef = new HashMap<>();
	private final BiConsumer<String, String> resourceUse;

	/**
	 * Constructs a new ResourceFlow, immediately writing the opening hash and
	 * {@code ProcSet} entry to {@code flow}, then forking a fragment to receive
	 * the lazily-created sub-dictionaries.
	 *
	 * @param flow the fragment output into which the resource dictionary is written
	 * @throws IOException if an I/O error occurs while writing the initial entries
	 */
	public ResourceFlow(final PDFFragmentOutputImpl flow, final BiConsumer<String, String> resourceUse,
			final ObjectRef defaultRGBProfileRef)
			throws IOException {
		this.resourceUse = resourceUse;
		flow.startHash();
		flow.writeName("ProcSet");
		flow.startArray();
		flow.writeName("PDF");
		flow.writeName("Text");
		flow.writeName("ImageB");
		flow.writeName("ImageC");
		flow.writeName("ImageI");
		flow.endArray();
		flow.lineBreak();
		this.out = flow.forkFragment();
		if (defaultRGBProfileRef != null) {
			final var colorSpaces = this.getFlow("ColorSpace");
			colorSpaces.writeName("DefaultRGB");
			colorSpaces.startArray();
			colorSpaces.writeName("ICCBased");
			colorSpaces.writeObjectRef(defaultRGBProfileRef);
			colorSpaces.endArray();
		}
		flow.endHash();
	}

	/**
	 * Returns the fragment output for the given resource type, creating and
	 * initialising a new sub-dictionary entry the first time a particular type is
	 * requested.
	 *
	 * @param type the resource type name (e.g. {@code "Font"}, {@code "XObject"})
	 * @return the fragment output for that resource type
	 * @throws IOException if an I/O error occurs while forking a new fragment
	 */
	private PDFFragmentOutputImpl getFlow(final String type) throws IOException {
		PDFFragmentOutputImpl flow = this.typeToFlow.get(type);
		if (flow == null) {
			flow = this.out.forkFragment();
			this.typeToFlow.put(type, flow);
			this.flowList.add(flow);
			flow.writeName(type);
			flow.startHash();
		}
		return flow;
	}

	/**
	 * Returns {@code true} if a resource with the given name has already been
	 * registered in this dictionary.
	 *
	 * @param name the resource name to look up
	 * @return {@code true} if the name is already present
	 */
	public boolean contains(final String name) {
		return this.idToObjectRef.containsKey(name);
	}

	/**
	 * Adds an object.
	 * 
	 * @param type      type ("Font", "XObject", etc.)
	 * @param name      name used for reference
	 * @param objectRef object reference
	 * @throws IOException in case of I/O error
	 */
	public void put(final String type, final String name, final ObjectRef objectRef) throws IOException {
		assert !this.contains(name);
		final PDFFragmentOutputImpl flow = this.getFlow(type);
		flow.writeName(name);
		flow.writeObjectRef(objectRef);
		this.idToObjectRef.put(name, objectRef);
		this.resourceUse.accept(type, name);
	}

	/**
	 * Closes all open sub-dictionaries and the forked fragment output.
	 *
	 * @throws IOException if an I/O error occurs during finalization
	 */
	public void close() throws IOException {
		for (final PDFFragmentOutputImpl flow : this.flowList) {
			try (flow) {
				flow.endHash();
			}
		}
	}
}
