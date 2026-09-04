package net.zamasoft.pdfg2d.pdf.impl;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.PDFOutput;

import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.annot.Annot;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Implementation of a PDF page output. This class manages the page content
 * stream,
 * annotations, and page-level metadata like boxes (MediaBox, CropBox, etc.).
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class PDFPageOutputImpl extends PDFPageOutput {
	private final PDFFragmentOutputImpl pageFlow;

	/** Current page object. */
	private final ObjectRef pageRef;

	private final ObjectRef contentsRef;

	public ObjectRef getPageRef() {
		return this.pageRef;
	}

	public ObjectRef getContentsRef() {
		return this.contentsRef;
	}

	/** Parameters flow for current page. */
	private final PDFFragmentOutputImpl paramsFlow;

	/** Annotations flow for current page. */
	private final PDFFragmentOutputImpl annotsFlow;

	/** Does current page have annotations? */
	private boolean hasAnnots = false;

	private Rectangle2D mediaBox, cropBox, bleedBox, trimBox, artBox;

	/** The page's parent tree key ({@code /StructParents}), or {@code -1}. */
	private int structParents = -1;

	/** The PDF/VT document part this page belongs to, or {@code null}. */
	private final ObjectRef dpartRef;

	public PDFPageOutputImpl(final PDFWriterImpl pdfWriter, final ObjectRef rootPageRef,
			final PDFFragmentOutputImpl pagesKidsFlow, final double width, final double height) throws IOException {
		super(pdfWriter, null, width, height);
		if (width < PDFWriter.MIN_PAGE_WIDTH || height < PDFWriter.MIN_PAGE_HEIGHT) {
			throw new IllegalArgumentException("Page size is too small: " + width + "x" + height);
		}
		if (width > PDFWriter.MAX_PAGE_WIDTH || height > PDFWriter.MAX_PAGE_HEIGHT) {
			throw new IllegalArgumentException("Page size exceeds limits: " + width + "x" + height);
		}

		final var mainFlow = pdfWriter.mainFlow;
		final var xref = pdfWriter.xref;
		this.dpartRef = pdfWriter.dpartForNewPage();

		this.pageRef = xref.nextObjectRef();
		mainFlow.startObject(this.pageRef);
		pagesKidsFlow.writeObjectRef(this.pageRef);
		mainFlow.startHash();

		mainFlow.writeName("Type");
		mainFlow.writeName("Page");
		mainFlow.lineBreak();

		this.paramsFlow = mainFlow.forkFragment();
		this.mediaBox = new Rectangle2D.Double(0, 0, width, height);

		mainFlow.writeName("Parent");
		mainFlow.writeObjectRef(rootPageRef);
		mainFlow.lineBreak();

		mainFlow.writeName("Resources");
		final var pageResourceRef = pdfWriter.ensurePageResourceRef();
		mainFlow.writeObjectRef(pageResourceRef);
		mainFlow.lineBreak();

		mainFlow.writeName("Contents");
		this.contentsRef = xref.nextObjectRef();
		mainFlow.writeObjectRef(this.contentsRef);
		mainFlow.lineBreak();

		this.annotsFlow = mainFlow.forkFragment();
		mainFlow.lineBreak();

		mainFlow.endHash();
		mainFlow.endObject();
		pdfWriter.ensurePageResourceFlow();

		this.pageFlow = mainFlow.forkFragment();
		this.pageFlow.startObject(contentsRef);

		// Always use ASCII/Flate compression for page contents
		this.out = this.pageFlow.startStream(PDFFragmentOutput.Mode.ASCII);

		pdfWriter.pageOutputs.add(this);
	}

	private PDFWriterImpl getPDFWriterImpl() {
		return (PDFWriterImpl) this.pdfWriter;
	}

	/** Records the parent tree key assigned by the structure tree builder. */
	void setStructParents(final int key) {
		this.structParents = key;
	}

	@Override
	public int beginMark(final String role, final String alt) throws IOException {
		final var structure = this.getPDFWriterImpl().structure;
		if (structure == null) {
			return -1;
		}
		final var mark = structure.mark(this, role, alt);
		this.writeName(mark.tag());
		this.startHash();
		this.writeName("MCID");
		this.writeInt(mark.mcid());
		this.endHash();
		this.writeOperator("BDC");
		return mark.mcid();
	}

	@Override
	public boolean beginArtifact() throws IOException {
		if (this.getPDFWriterImpl().structure == null) {
			return false;
		}
		this.writeName("Artifact");
		this.writeOperator("BMC");
		return true;
	}

	@Override
	public void endMark() throws IOException {
		this.writeOperator("EMC");
	}

	@Override
	public void beginStructElement(final String role) {
		final var structure = this.getPDFWriterImpl().structure;
		if (structure != null) {
			structure.begin(role);
		}
	}

	@Override
	public void beginStructElement(final String role, final String scope) {
		final var structure = this.getPDFWriterImpl().structure;
		if (structure != null) {
			structure.begin(role, scope);
		}
	}

	@Override
	public void endStructElement() {
		final var structure = this.getPDFWriterImpl().structure;
		if (structure != null) {
			structure.end();
		}
	}

	@Override
	public net.zamasoft.pdfg2d.pdf.StructureRef declareStructElement(
			final net.zamasoft.pdfg2d.pdf.StructureRef parent, final String role, final String scope) {
		final var structure = this.getPDFWriterImpl().structure;
		return structure != null ? structure.declare(parent, role, scope) : null;
	}

	@Override
	public void beginStructContent(final net.zamasoft.pdfg2d.pdf.StructureRef target) {
		this.beginStructContent(target, null);
	}

	@Override
	public void beginStructContent(final net.zamasoft.pdfg2d.pdf.StructureRef target,
			final net.zamasoft.pdfg2d.pdf.StructureOrder order) {
		final var structure = this.getPDFWriterImpl().structure;
		if (structure != null) {
			// A null or foreign target leaves routing/order untouched but still
			// pushes a frame, so callers can bracket without a conditional.
			structure.beginContent(target, order);
		}
	}

	@Override
	public void endStructContent() {
		final var structure = this.getPDFWriterImpl().structure;
		if (structure != null) {
			structure.endContent();
		}
	}

	/**
	 * Ensures that the named resource is declared in the page resource dictionary.
	 * <p>
	 * If the resource has already been added, this method is a no-op.
	 * </p>
	 *
	 * @param type PDF resource type (e.g. {@code "Font"}, {@code "XObject"})
	 * @param name resource name as assigned by
	 *             {@link PDFWriterImpl#addResource(String, String, ObjectRef)}
	 * @throws IOException if an I/O error occurs
	 */
	public void useResource(final String type, final String name) throws IOException {
		final var pdfWriter = this.getPDFWriterImpl();
		pdfWriter.ensurePageResourceFlow();
		final var resourceFlow = pdfWriter.pageResourceFlow;
		if (resourceFlow.contains(name)) {
			return;
		}
		final var nameToResourceRef = pdfWriter.nameToResourceRef;
		final var objectRef = nameToResourceRef.get(name);
		resourceFlow.put(type, name, objectRef);
	}

	private final List<ObjectRef> annotRefs = new ArrayList<>();

	/** Bounds of annotations to validate against the PDF/X boxes on close. */
	private final List<Rectangle2D> pdfxAnnotBounds = new ArrayList<>();

	public List<ObjectRef> getAnnotRefs() {
		return this.annotRefs;
	}

	/**
	 * Adds an annotation to this page.
	 * 
	 * @param annot The annotation to add
	 * @throws IOException If an I/O error occurs
	 */
	public void addAnnotation(final Annot annot) throws IOException {
		final var pdfWriterImpl = this.getPDFWriterImpl();
		final var params = pdfWriterImpl.getParams();
		if (params.version().isPdfX()) {
			// ISO 15930 permits annotations only when they lie entirely
			// outside the bleed area (or the finished page when there is no
			// BleedBox). The boxes may be set after this call, so the bounds
			// are validated when the page closes.
			this.pdfxAnnotBounds.add(annot.getShape().getBounds2D());
		}

		if (!this.hasAnnots) {
			this.annotsFlow.writeName("Annots");
			this.annotsFlow.startArray();
			this.hasAnnots = true;
		}

		final var annotRef = pdfWriterImpl.xref.nextObjectRef();
		this.annotRefs.add(annotRef);
		this.annotsFlow.writeObjectRef(annotRef);

		// When a structure element is open, associate the annotation with it
		// (OBJR child + /StructParent) so assistive technology reads the link
		// in document order — a PDF/UA requirement for link annotations.
		final var structure = pdfWriterImpl.structure;
		final var structParent = (structure != null) ? structure.associateAnnotation(this, annotRef) : -1;

		// Write annotation object to a separate fragment
		try (final var objectsFlow = pdfWriterImpl.objectsFlow.forkFragment()) {
			objectsFlow.startObject(annotRef);
			objectsFlow.startHash();
			annot.writeTo(objectsFlow, this);

			// Required flags for PDF/A or PDF/X
			if (params.version().isPdfA() || params.version().isPdfX()) {
				objectsFlow.writeName("F");
				objectsFlow.writeInt(0x04); // Print flag
				objectsFlow.lineBreak();
			}

			if (structParent >= 0) {
				objectsFlow.writeName("StructParent");
				objectsFlow.writeInt(structParent);
				objectsFlow.lineBreak();
			}

			objectsFlow.endHash();
			objectsFlow.endObject();
		}
	}

	@Override
	public void addFormField(final net.zamasoft.pdfg2d.pdf.form.FormField field) throws IOException {
		final var writer = this.getPDFWriterImpl();
		if (writer.getParams().version().isPdfX()) {
			throw new UnsupportedOperationException("Form fields are not allowed in PDF/X.");
		}
		final var rect = field.rect();
		final double llx = rect.getX();
		final double lly = this.height - (rect.getY() + rect.getHeight());
		final double urx = rect.getX() + rect.getWidth();
		final double ury = this.height - rect.getY();
		final double w = rect.getWidth();
		final double h = rect.getHeight();

		if (!this.hasAnnots) {
			this.annotsFlow.writeName("Annots");
			this.annotsFlow.startArray();
			this.hasAnnots = true;
		}
		final var fieldRef = writer.xref.nextObjectRef();
		this.annotRefs.add(fieldRef);
		this.annotsFlow.writeObjectRef(fieldRef);

		// Associate with a Form structure element for accessible tagging.
		final var structure = writer.structure;
		final var structParent = (structure != null) ? structure.associateAnnotation(this, fieldRef) : -1;

		// Checkbox appearance streams are written before the field object.
		net.zamasoft.pdfg2d.pdf.ObjectRef onRef = null, offRef = null;
		if (field instanceof net.zamasoft.pdfg2d.pdf.form.CheckBoxField) {
			offRef = writer.writeAppearanceStream(w, h, "q 1 1 " + fmt(w - 2) + " " + fmt(h - 2) + " re S Q");
			final double i = 3;
			onRef = writer.writeAppearanceStream(w, h, "q 1 1 " + fmt(w - 2) + " " + fmt(h - 2) + " re S "
					+ fmt(i) + " " + fmt(i) + " m " + fmt(w - i) + " " + fmt(h - i) + " l "
					+ fmt(i) + " " + fmt(h - i) + " m " + fmt(w - i) + " " + fmt(i) + " l S Q");
		}
		final boolean needsAppearances = field instanceof net.zamasoft.pdfg2d.pdf.form.TextField
				|| field instanceof net.zamasoft.pdfg2d.pdf.form.ChoiceField;
		if (needsAppearances) {
			writer.helvFontRef(); // ensure /Helv is in /DR
		}
		writer.addAcroFormField(fieldRef, needsAppearances);

		try (final var flow = writer.objectsFlow.forkFragment()) {
			flow.startObject(fieldRef);
			flow.startHash();
			flow.writeName("Type");
			flow.writeName("Annot");
			flow.writeName("Subtype");
			flow.writeName("Widget");
			flow.writeName("Rect");
			flow.startArray();
			flow.writeReal(llx);
			flow.writeReal(lly);
			flow.writeReal(urx);
			flow.writeReal(ury);
			flow.endArray();
			flow.lineBreak();
			flow.writeName("F");
			flow.writeInt(0x04); // Print
			flow.lineBreak();
			flow.writeName("T");
			flow.writeText(field.name());
			flow.lineBreak();
			if (field.tooltip() != null) {
				flow.writeName("TU");
				flow.writeUTF16(field.tooltip());
				flow.lineBreak();
			}
			if (structParent >= 0) {
				flow.writeName("StructParent");
				flow.writeInt(structParent);
				flow.lineBreak();
			}
			this.writeFieldBody(flow, field, w, h, onRef, offRef);
			flow.endHash();
			flow.endObject();
		}
	}

	@Override
	public void addRadioGroup(final net.zamasoft.pdfg2d.pdf.form.RadioGroup group) throws IOException {
		final var writer = this.getPDFWriterImpl();
		if (writer.getParams().version().isPdfX()) {
			throw new UnsupportedOperationException("Form fields are not allowed in PDF/X.");
		}
		if (group.buttons().isEmpty()) {
			return;
		}
		if (!this.hasAnnots) {
			this.annotsFlow.writeName("Annots");
			this.annotsFlow.startArray();
			this.hasAnnots = true;
		}
		final var structure = writer.structure;
		final var parentRef = writer.xref.nextObjectRef();
		final var kidRefs = new java.util.ArrayList<ObjectRef>(group.buttons().size());

		for (final var button : group.buttons()) {
			final var rect = button.rect();
			final double llx = rect.getX();
			final double lly = this.height - (rect.getY() + rect.getHeight());
			final double urx = rect.getX() + rect.getWidth();
			final double ury = this.height - rect.getY();
			final double w = rect.getWidth();
			final double h = rect.getHeight();
			final var on = (button.onValue() != null && !button.onValue().isEmpty()) ? button.onValue() : "On";

			// Off: an empty ring; On: the ring with a filled centre.
			final var offRef = writer.writeAppearanceStream(w, h, "q 1 1 " + fmt(w - 2) + " " + fmt(h - 2) + " re S Q");
			final double i = 3;
			final var onRef = writer.writeAppearanceStream(w, h, "q 1 1 " + fmt(w - 2) + " " + fmt(h - 2) + " re S "
					+ fmt(i) + " " + fmt(i) + " " + fmt(w - 2 * i) + " " + fmt(h - 2 * i) + " re f Q");

			final var kidRef = writer.xref.nextObjectRef();
			kidRefs.add(kidRef);
			this.annotRefs.add(kidRef);
			this.annotsFlow.writeObjectRef(kidRef);
			final var structParent = (structure != null) ? structure.associateAnnotation(this, kidRef) : -1;

			final boolean selected = on.equals(group.selectedValue());
			try (final var flow = writer.objectsFlow.forkFragment()) {
				flow.startObject(kidRef);
				flow.startHash();
				flow.writeName("Type");
				flow.writeName("Annot");
				flow.writeName("Subtype");
				flow.writeName("Widget");
				flow.writeName("Parent");
				flow.writeObjectRef(parentRef);
				flow.writeName("Rect");
				flow.startArray();
				flow.writeReal(llx);
				flow.writeReal(lly);
				flow.writeReal(urx);
				flow.writeReal(ury);
				flow.endArray();
				flow.lineBreak();
				flow.writeName("F");
				flow.writeInt(0x04); // Print
				flow.lineBreak();
				flow.writeName("AS");
				flow.writeName(selected ? on : "Off");
				flow.writeName("AP");
				flow.startHash();
				flow.writeName("N");
				flow.startHash();
				flow.writeName(on);
				flow.writeObjectRef(onRef);
				flow.writeName("Off");
				flow.writeObjectRef(offRef);
				flow.endHash();
				flow.endHash();
				flow.lineBreak();
				if (structParent >= 0) {
					flow.writeName("StructParent");
					flow.writeInt(structParent);
					flow.lineBreak();
				}
				flow.endHash();
				flow.endObject();
			}
		}

		writer.addAcroFormField(parentRef, false);
		try (final var flow = writer.objectsFlow.forkFragment()) {
			flow.startObject(parentRef);
			flow.startHash();
			flow.writeName("FT");
			flow.writeName("Btn");
			flow.writeName("Ff");
			flow.writeInt(FF_RADIO | (group.readOnly() ? FF_READONLY : 0) | (group.required() ? FF_REQUIRED : 0));
			flow.lineBreak();
			flow.writeName("T");
			flow.writeText(group.name());
			flow.lineBreak();
			if (group.tooltip() != null) {
				flow.writeName("TU");
				flow.writeUTF16(group.tooltip());
				flow.lineBreak();
			}
			flow.writeName("V");
			flow.writeName(group.selectedValue() != null ? group.selectedValue() : "Off");
			flow.writeName("Kids");
			flow.startArray();
			for (final var kidRef : kidRefs) {
				flow.writeObjectRef(kidRef);
			}
			flow.endArray();
			flow.lineBreak();
			flow.endHash();
			flow.endObject();
		}
	}

	private static String fmt(final double v) {
		return String.format(java.util.Locale.US, "%.2f", v);
	}

	/** Field flags (ISO 32000 table 227/228/230). */
	private static final int FF_READONLY = 1 << 0, FF_REQUIRED = 1 << 1, FF_RADIO = 1 << 15, FF_PUSHBUTTON = 1 << 16,
			FF_MULTILINE = 1 << 12, FF_COMBO = 1 << 17;

	private void writeFieldBody(final PDFOutput flow, final net.zamasoft.pdfg2d.pdf.form.FormField field,
			final double w, final double h, final ObjectRef onRef, final ObjectRef offRef) throws IOException {
		int ff = 0;
		if (field.readOnly()) {
			ff |= FF_READONLY;
		}
		if (field.required()) {
			ff |= FF_REQUIRED;
		}
		switch (field) {
			case net.zamasoft.pdfg2d.pdf.form.TextField tf -> {
				flow.writeName("FT");
				flow.writeName("Tx");
				if (tf.multiline()) {
					ff |= FF_MULTILINE;
				}
				flow.writeName("DA");
				flow.writeText("/Helv " + fmt(tf.fontSize()) + " Tf 0 g");
				if (tf.value() != null) {
					flow.writeName("V");
					flow.writeUTF16(tf.value());
					flow.lineBreak();
				}
				if (tf.maxLength() > 0) {
					flow.writeName("MaxLen");
					flow.writeInt(tf.maxLength());
					flow.lineBreak();
				}
			}
			case net.zamasoft.pdfg2d.pdf.form.CheckBoxField cb -> {
				flow.writeName("FT");
				flow.writeName("Btn");
				if (cb.radio()) {
					ff |= FF_RADIO;
				}
				final var on = (cb.onValue() != null && !cb.onValue().isEmpty()) ? cb.onValue() : "On";
				flow.writeName("V");
				flow.writeName(cb.checked() ? on : "Off");
				flow.writeName("AS");
				flow.writeName(cb.checked() ? on : "Off");
				flow.writeName("AP");
				flow.startHash();
				flow.writeName("N");
				flow.startHash();
				flow.writeName(on);
				flow.writeObjectRef(onRef);
				flow.writeName("Off");
				flow.writeObjectRef(offRef);
				flow.endHash();
				flow.endHash();
				flow.lineBreak();
			}
			case net.zamasoft.pdfg2d.pdf.form.ChoiceField ch -> {
				flow.writeName("FT");
				flow.writeName("Ch");
				if (ch.combo()) {
					ff |= FF_COMBO;
				}
				flow.writeName("DA");
				flow.writeText("/Helv " + fmt(ch.fontSize()) + " Tf 0 g");
				flow.writeName("Opt");
				flow.startArray();
				for (final var opt : ch.options()) {
					flow.writeUTF16(opt);
				}
				flow.endArray();
				flow.lineBreak();
				if (ch.selected() != null) {
					flow.writeName("V");
					flow.writeUTF16(ch.selected());
					flow.lineBreak();
				}
			}
			case net.zamasoft.pdfg2d.pdf.form.PushButtonField pb -> {
				flow.writeName("FT");
				flow.writeName("Btn");
				ff |= FF_PUSHBUTTON;
				flow.writeName("MK");
				flow.startHash();
				flow.writeName("CA");
				flow.writeText(pb.caption() != null ? pb.caption() : "");
				flow.endHash();
				flow.lineBreak();
			}
		}
		if (ff != 0) {
			flow.writeName("Ff");
			flow.writeInt(ff);
			flow.lineBreak();
		}
	}

	@Override
	@SuppressWarnings("resource")
	public void addFragment(final String id, final Point2D location) throws IOException {
		final Destination dest = new Destination(this.pageRef, location.getX(), this.height - location.getY(), 0);
		this.getPDFWriterImpl().fragments.addEntry(id, dest);
	}

	/**
	 * Starts bookmark hierarchy.
	 * <p>
	 * The number of endBookmark calls does not need to match startBookmark.
	 * Unclosed hierarchies are automatically closed when document construction is
	 * complete.
	 * </p>
	 * 
	 * @param title    the title
	 * @param location the location
	 * @throws IOException in case of I/O error
	 */
	@Override
	@SuppressWarnings("resource")
	public void startBookmark(final String title, final Point2D location) throws IOException {
		if (this.getPDFWriterImpl().outline != null) {
			this.getPDFWriterImpl().outline.startBookmark(this.pageRef, title, this.height, location.getX(),
					location.getY());
		}
	}

	/**
	 * Ends the current bookmark hierarchy level.
	 * 
	 * @throws IOException If an I/O error occurs
	 */
	public void endBookmark() throws IOException {
		final var outline = this.getPDFWriterImpl().outline;
		if (outline != null) {
			outline.endBookmark();
		}
	}

	/**
	 * Writes a PDF rectangle array for a page box, converting from the top-left
	 * coordinate system used by this API to the bottom-left PDF coordinate system.
	 *
	 * @param r rectangle in top-left coordinates
	 * @throws IOException if an I/O error occurs
	 */
	private void paramRect(final Rectangle2D r) throws IOException {
		this.paramsFlow.startArray();
		this.paramsFlow.writeReal(r.getMinX());
		this.paramsFlow.writeReal(this.height - r.getMaxY());
		this.paramsFlow.writeReal(r.getMaxX());
		this.paramsFlow.writeReal(this.height - r.getMinY());
		this.paramsFlow.endArray();
		this.paramsFlow.lineBreak();
	}

	public void setMediaBox(final Rectangle2D mediaBox) {
		if (mediaBox == null) {
			throw new NullPointerException("MediaBox cannot be null");
		}
		this.mediaBox = mediaBox;
	}

	public void setCropBox(final Rectangle2D cropBox) {
		this.cropBox = cropBox;
	}

	public void setBleedBox(final Rectangle2D bleedBox) {
		if (bleedBox != null && this.pdfWriter.getParams().version().v < PDFParams.Version.V_1_3.v) {
			throw new UnsupportedOperationException("BleedBox requires PDF 1.4+.");
		}
		this.bleedBox = bleedBox;
	}

	public void setTrimBox(final Rectangle2D trimBox) {
		if (trimBox != null && this.pdfWriter.getParams().version().v < PDFParams.Version.V_1_3.v) {
			throw new UnsupportedOperationException("TrimBox requires PDF 1.4+.");
		}
		this.trimBox = trimBox;
	}

	public void setArtBox(final Rectangle2D artBox) {
		if (artBox != null && this.pdfWriter.getParams().version().v < PDFParams.Version.V_1_3.v) {
			throw new UnsupportedOperationException("ArtBox requires PDF 1.4+.");
		}
		this.artBox = artBox;
	}

	/**
	 * Returns whether {@code outer} contains {@code inner}, with a small
	 * tolerance for floating-point rounding.
	 */
	private static boolean containsBox(final Rectangle2D outer, final Rectangle2D inner) {
		final double e = 0.01;
		return inner.getMinX() >= outer.getMinX() - e && inner.getMinY() >= outer.getMinY() - e
				&& inner.getMaxX() <= outer.getMaxX() + e && inner.getMaxY() <= outer.getMaxY() + e;
	}

	/**
	 * Enforces the PDF/X-1a page box rules (ISO 15930): every page carries
	 * exactly one of TrimBox or ArtBox (a full-page TrimBox is supplied when
	 * neither was set), and the finished-size box must lie inside the
	 * BleedBox (when present), which in turn must lie inside the MediaBox.
	 */
	private void validatePdfxBoxes() {
		if (this.trimBox != null && this.artBox != null) {
			throw new IllegalStateException(
					"PDF/X requires exactly one of TrimBox or ArtBox per page, not both.");
		}
		if (this.trimBox == null && this.artBox == null) {
			this.trimBox = new Rectangle2D.Double(0, 0, this.width, this.height);
		}
		final var finished = (this.trimBox != null) ? this.trimBox : this.artBox;
		if (this.bleedBox != null) {
			if (!containsBox(this.mediaBox, this.bleedBox)) {
				throw new IllegalStateException("BleedBox must lie within the MediaBox.");
			}
			if (!containsBox(this.bleedBox, finished)) {
				throw new IllegalStateException(
						(this.trimBox != null ? "TrimBox" : "ArtBox") + " must lie within the BleedBox.");
			}
		} else if (!containsBox(this.mediaBox, finished)) {
			throw new IllegalStateException(
					(this.trimBox != null ? "TrimBox" : "ArtBox") + " must lie within the MediaBox.");
		}

		// ISO 15930: annotations must lie entirely outside the bleed area
		// (the BleedBox, or the finished-size box when no bleed is defined).
		// This allows proofing notes in the slug/marks area while keeping
		// the printed area clean.
		final var keepOut = (this.bleedBox != null) ? this.bleedBox : finished;
		for (final var bounds : this.pdfxAnnotBounds) {
			if (bounds.intersects(keepOut)) {
				throw new IllegalStateException(
						"PDF/X allows annotations only entirely outside the bleed/finished area; "
								+ "annotation " + bounds + " intersects " + keepOut + ".");
			}
		}
	}

	public void close() throws IOException {
		super.close();

		if (this.mediaBox == null) {
			throw new IllegalStateException();
		}

		if (this.pdfWriter.getParams().version().isPdfX()) {
			this.validatePdfxBoxes();
		}

		if (this.structParents >= 0) {
			this.paramsFlow.writeName("StructParents");
			this.paramsFlow.writeInt(this.structParents);
			this.paramsFlow.lineBreak();
		}

		// PDF/UA (ISO 14289-1 7.18.3): a page carrying annotations must set a
		// tab order; /S follows the structure order.
		if (this.hasAnnots) {
			this.paramsFlow.writeName("Tabs");
			this.paramsFlow.writeName("S");
			this.paramsFlow.lineBreak();
		}

		// PDF/VT: every page belongs to a document part
		if (this.dpartRef != null) {
			this.paramsFlow.writeName("DPart");
			this.paramsFlow.writeObjectRef(this.dpartRef);
			this.paramsFlow.lineBreak();
		}
		this.paramsFlow.writeName("MediaBox");
		this.paramRect(this.mediaBox);

		if (this.cropBox != null) {
			this.paramsFlow.writeName("CropBox");
			this.paramRect(this.cropBox);
		}

		if (this.bleedBox != null) {
			this.paramsFlow.writeName("BleedBox");
			this.paramRect(this.bleedBox);
		}

		if (this.trimBox != null) {
			this.paramsFlow.writeName("TrimBox");
			this.paramRect(this.trimBox);
		}

		if (this.artBox != null) {
			this.paramsFlow.writeName("ArtBox");
			this.paramRect(this.artBox);
		}

		this.paramsFlow.close();

		if (this.hasAnnots) {
			this.annotsFlow.endArray();
			this.hasAnnots = false;
		}
		this.annotsFlow.close();
		this.pageFlow.endObject();
		this.pageFlow.close();
	}
}
