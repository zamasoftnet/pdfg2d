package net.zamasoft.pdfg2d.pdf;

/**
 * Handle for an Optional Content Group (layer): named content whose
 * visibility can differ between screen and print and be toggled by the
 * viewer. Created through
 * {@link PDFWriter#createOptionalContentGroup(String, boolean, boolean, boolean, boolean)}
 * and applied either to whole Form XObjects
 * ({@link net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage#setOCG}) or to content
 * runs via {@code PDFGC.beginLayer}/{@code endLayer}.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public final class PDFOptionalContentGroup {

	private final ObjectRef ref;

	private final String resourceName;

	private final String name;

	/**
	 * Creates the handle; called by the writer implementation.
	 *
	 * @param ref          the OCG object reference
	 * @param resourceName the {@code /Properties} resource name
	 * @param name         the human-readable layer name
	 */
	public PDFOptionalContentGroup(final ObjectRef ref, final String resourceName, final String name) {
		this.ref = ref;
		this.resourceName = resourceName;
		this.name = name;
	}

	/**
	 * Returns the OCG object reference.
	 *
	 * @return the object reference
	 */
	public ObjectRef getRef() {
		return this.ref;
	}

	/**
	 * Returns the {@code /Properties} resource name used by marked-content
	 * operators.
	 *
	 * @return the resource name
	 */
	public String getResourceName() {
		return this.resourceName;
	}

	/**
	 * Returns the layer name shown in viewers.
	 *
	 * @return the layer name
	 */
	public String getName() {
		return this.name;
	}

	@Override
	public String toString() {
		return this.name;
	}
}
