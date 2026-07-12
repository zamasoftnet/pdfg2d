package net.zamasoft.pdfg2d.pdf.impl;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;



import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontStore;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.util.FragmentOutputAdapter;
import net.zamasoft.zstream.io.util.PositionTrackingOutput;
import net.zamasoft.pdfg2d.pdf.Attachment;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.PDFNamedGraphicsOutput;
import net.zamasoft.pdfg2d.pdf.PDFNamedOutput;
import net.zamasoft.pdfg2d.pdf.PDFOptionalContentGroup;

import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.PDFOutput.Destination;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.action.Action;
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;
import net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage;
import net.zamasoft.pdfg2d.pdf.params.EncryptionParams;
import net.zamasoft.pdfg2d.pdf.params.OutputIntent;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.V4EncryptionParams;
import net.zamasoft.pdfg2d.pdf.params.ViewerPreferences;
import net.zamasoft.pdfg2d.pdf.util.encryption.Encryption;
import net.zamasoft.zstream.resolver.Source;

/**
 * Core implementation of {@link PDFWriter} that handles the assembly of PDF
 * document structure.
 * This class manages the PDF Catalog, Page Tree, XRef table, encryption, and
 * various
 * resource flows (fonts, images, etc.).
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PDFWriterImpl implements PDFWriter, FontStore {

	/**
	 * Randomness source for the file ID and the PDF/A binary marker. The file
	 * ID participates in encryption key derivation, so a cryptographically
	 * strong generator is used.
	 */
	protected static final Random RND = new SecureRandom();

	protected static final int BUFFER_SIZE = 8192;

	private static final byte[] HEADER = { '%', 'P', 'D', 'F', '-' };

	final FragmentedOutput builder;

	final PDFParams params;

	private FontManagerImpl fontManager = null;

	/** XRef Table. */
	protected final XRefImpl xref;

	/** For generating unique fragment IDs. */
	private int sequence = 0;

	/** Encryption. */
	Encryption encryption = null;

	/** File ID. */
	private final byte[][] fileid;

	/** Main flow. */
	final PDFFragmentOutputImpl mainFlow;

	/** Catalog dictionary flow. */
	final PDFFragmentOutputImpl catalogFlow;

	/** XMP metadata flow. */
	final PDFFragmentOutputImpl xmpmetaFlow;

	/**
	 * Object flows.
	 */
	final PDFFragmentOutputImpl objectsFlow;

	final NameDictionaryFlow nameDict;

	/**
	 * Resources referenced from pages.
	 */
	ResourceFlow pageResourceFlow;

	ObjectRef pageResourceRef;

	/**
	 * Common resources for pages and XObjects.
	 */
	final Map<String, ObjectRef> nameToResourceRef = new HashMap<>();

	/**
	 * Resource type and count.
	 */
	private final Map<String, Integer> typeToCount = new HashMap<>();

	private final Map<Object, Object> keyToValue = new HashMap<>();

	/** Pages. */
	private final PagesFlow pages;

	protected final List<PDFPageOutputImpl> pageOutputs = new ArrayList<>();

	/** Outline. */
	final OutlineFlow outline;

	/** Anchor. */
	final NameTreeFlow fragments;

	/** Attachments. */
	private final NameTreeFlow embeddedFiles;

	/** Images. */
	private final ImageFlow images;

	private final FontFlow fonts;

	/** Registered optional content groups with their configuration state. */
	record OCGEntry(ObjectRef ref, boolean initiallyOn, boolean locked) {
	}

	private List<OCGEntry> ocgs = null;

	/** Filespec references for the PDF/A-3 catalog /AF (associated files) array. */
	private List<ObjectRef> afRefs = null;

	/** Logical structure collector for tagged PDF output, or {@code null}. */
	final StructureTreeBuilder structure;

	/** Object stream packer when object streams are enabled, or {@code null}. */
	private ObjectStreamWriter objStm = null;

	/** PDF/VT document part hierarchy references, or {@code null}. */
	ObjectRef dpartRootRef, dpartNodeRef;

	/** One PDF/VT document part (record): its pages span startPage..end. */
	static final class DPartInfo {
		final ObjectRef ref;
		final int startPage;
		final Map<String, String> metadata;

		DPartInfo(final ObjectRef ref, final int startPage, final Map<String, String> metadata) {
			this.ref = ref;
			this.startPage = startPage;
			this.metadata = metadata;
		}
	}

	/** PDF/VT document parts in order, or {@code null} when not PDF/VT. */
	private List<DPartInfo> dparts = null;

	/** AcroForm field object references, collected as fields are added. */
	private List<ObjectRef> acroFormFields = null;

	/** Whether a text or choice field needs viewer-generated appearances. */
	private boolean acroFormNeedsAppearances = false;

	/** Shared standard-font references for form default resources (/DR). */
	private ObjectRef helvFontRef, zadbFontRef;

	/**
	 * Registers an AcroForm field object reference and notes whether the form
	 * needs viewer-generated appearances (text/choice fields).
	 *
	 * @param fieldRef        the field object reference
	 * @param needAppearances whether this field relies on {@code NeedAppearances}
	 */
	void addAcroFormField(final ObjectRef fieldRef, final boolean needAppearances) {
		if (this.acroFormFields == null) {
			this.acroFormFields = new ArrayList<>();
		}
		this.acroFormFields.add(fieldRef);
		this.acroFormNeedsAppearances |= needAppearances;
	}

	/**
	 * Returns the shared Helvetica font reference for form fields, allocating
	 * and writing the standard Type1 font dictionary on first use.
	 */
	ObjectRef helvFontRef() throws IOException {
		if (this.helvFontRef == null) {
			this.helvFontRef = this.writeStandardFont("Helvetica");
		}
		return this.helvFontRef;
	}

	/** Returns the shared ZapfDingbats font reference (checkbox marks). */
	ObjectRef zadbFontRef() throws IOException {
		if (this.zadbFontRef == null) {
			this.zadbFontRef = this.writeStandardFont("ZapfDingbats");
		}
		return this.zadbFontRef;
	}

	private ObjectRef writeStandardFont(final String baseFont) throws IOException {
		final var ref = this.xref.nextObjectRef();
		final var flow = this.objectsFlow;
		flow.startObject(ref);
		flow.startHash();
		flow.writeName("Type");
		flow.writeName("Font");
		flow.writeName("Subtype");
		flow.writeName("Type1");
		flow.writeName("BaseFont");
		flow.writeName(baseFont);
		flow.endHash();
		flow.endObject();
		return ref;
	}

	/**
	 * Writes a widget appearance as a Form XObject and returns its reference.
	 *
	 * @param width   the BBox width
	 * @param height  the BBox height
	 * @param content the content-stream operators
	 * @return the appearance stream reference
	 * @throws IOException if writing fails
	 */
	ObjectRef writeAppearanceStream(final double width, final double height, final String content) throws IOException {
		final var ref = this.xref.nextObjectRef();
		final var bytes = content.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		final var flow = this.objectsFlow;
		flow.startObject(ref);
		flow.startHash();
		flow.writeName("Type");
		flow.writeName("XObject");
		flow.writeName("Subtype");
		flow.writeName("Form");
		flow.writeName("BBox");
		flow.startArray();
		flow.writeReal(0);
		flow.writeReal(0);
		flow.writeReal(width);
		flow.writeReal(height);
		flow.endArray();
		flow.lineBreak();
		try (final var sout = flow.startStreamFromHash(PDFFragmentOutput.Mode.RAW)) {
			sout.write(bytes);
		}
		flow.endObject();
		return ref;
	}

	private ObjectRef linDictRef;
	private PDFFragmentOutputImpl linDictFlow;

	private final ObjectRef rootPageRef;

	public PDFWriterImpl(final FragmentedOutput builder, final PDFParams params) throws IOException {
		this.params = (params != null) ? params : PDFParams.createDefault();
		this.builder = builder.supportsPositionInfo() ? builder : new PositionTrackingOutput(builder);

		// PDF/A-1 forbids JavaScript actions and PDF/X forbids actions
		// entirely; the only supported open action is JavaScript, so reject
		// the combination up front rather than emitting a non-conformant file.
		if (this.params.openAction() != null
				&& (this.params.version().isPdfA() || this.params.version().isPdfX())) {
			throw new IllegalArgumentException("OpenAction is not allowed in PDF/A or PDF/X.");
		}

		final var tagged = this.params.tagged();
		this.structure = (tagged != null) ? new StructureTreeBuilder() : null;
		if (tagged != null && tagged.pdfua()) {
			// PDF/UA requires the window title to come from the document
			// title rather than the file name.
			this.params.viewerPreferences().setDisplayDocTitle(true);
		}

		final var id = this.nextId();
		this.builder.addFragment();
		final var out = new FragmentOutputAdapter(this.builder, id);
		this.mainFlow = new PDFFragmentOutputImpl(out, this, id, -1, null);

		// Header
		final var pdfVersion = this.params.version();
		this.mainFlow.write(HEADER);
		this.mainFlow.write(pdfVersion.baseVersion().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		this.mainFlow.lineBreak();
		// Binary identification comment: four bytes with values above 127
		// mark the file as binary for transfer tools. Required in practice by
		// PDF/A and PDF/X validators and recommended for every PDF.
		// (128..255: the spec demands strictly greater than 127.)
		this.mainFlow.write('%');
		for (var i = 0; i < 4; ++i) {
			this.mainFlow.write(RND.nextInt(128) + 128);
		}
		this.mainFlow.lineBreak();

		if (this.params.linearized()) {
			this.linDictFlow = this.mainFlow.forkFragment();
		}

		// Start root element (Catalog)
		this.xref = new XRefImpl(this.mainFlow);

		// PDF/VT: pages reference their document part; the first part is
		// created lazily with the first page, further parts via
		// nextDocumentPart().
		if (pdfVersion.isPdfVT()) {
			this.dpartRootRef = this.xref.nextObjectRef();
			this.dpartNodeRef = this.xref.nextObjectRef();
			this.dparts = new ArrayList<>();
		}

		if (this.params.linearized()) {
			this.linDictRef = this.xref.nextObjectRef();
			// We will fill this later in closeLinearized
		}

		this.mainFlow.startHash();

		this.mainFlow.writeName("Type");
		this.mainFlow.writeName("Catalog");
		this.mainFlow.lineBreak();

		// Version (deprecated in PDF 2.0, where the header is authoritative)
		if (pdfVersion.v >= PDFParams.Version.V_1_4.v && pdfVersion.v < PDFParams.Version.V_2_0.v) {
			this.mainFlow.writeName("Version");
			this.mainFlow.writeName(pdfVersion.baseVersion());
			this.mainFlow.lineBreak();
		}

		// Page Tree
		this.mainFlow.writeName("Pages");
		this.rootPageRef = this.xref.nextObjectRef();
		this.mainFlow.writeObjectRef(this.rootPageRef);
		this.mainFlow.lineBreak();

		// XMP Metadata
		var xmpmetaRef = (ObjectRef) null;
		if (this.params.version().v >= PDFParams.Version.V_1_4.v) {
			xmpmetaRef = this.xref.nextObjectRef();
			this.mainFlow.writeName("Metadata");
			this.mainFlow.writeObjectRef(xmpmetaRef);
			this.mainFlow.lineBreak();
		}

		// OutputIntents
		var outputIntentRef = (ObjectRef) null;
		if (this.params.version().v >= PDFParams.Version.V_1_4.v) {
			outputIntentRef = this.xref.nextObjectRef();
			this.mainFlow.writeName("OutputIntents");
			this.mainFlow.startArray();
			this.mainFlow.writeObjectRef(outputIntentRef);
			this.mainFlow.endArray();
			this.mainFlow.lineBreak();
		}

		// Inside Catalog
		this.catalogFlow = this.mainFlow.forkFragment();

		// File ID
		var fileId = this.params.fileId();
		if (fileId == null) {
			fileId = new byte[16];
			RND.nextBytes(fileId);
		}
		this.fileid = new byte[][] { fileId, fileId };

		// End Catalog
		this.mainFlow.endHash();
		this.mainFlow.endObject();

		// Encryption
		final var encryptionParams = this.params.encryption();
		if (encryptionParams != null) {
			if (pdfVersion.isPdfA()) {
				throw new IllegalArgumentException("Encryption cannot be used in PDF/A.");
			}
			if (pdfVersion.isPdfX()) {
				throw new IllegalArgumentException("Encryption cannot be used in PDF/X.");
			}
			final var encType = encryptionParams.getType();
			if (encType == EncryptionParams.Type.V2 && pdfVersion.v < PDFParams.Version.V_1_3.v) {
				throw new IllegalArgumentException("V2 encryption requires PDF 1.3 or later.");
			}
			if (encryptionParams instanceof final V4EncryptionParams v4Params) {
				if (pdfVersion.v < PDFParams.Version.V_1_5.v) {
					throw new IllegalArgumentException("V4 encryption requires PDF 1.5 or later.");
				}
				if (v4Params.getCFM() == V4EncryptionParams.CFM.AESV2 && pdfVersion.v < PDFParams.Version.V_1_6.v) {
					throw new IllegalArgumentException("AESV2 encryption requires PDF 1.6 or later.");
				}
			}
			if (encType == EncryptionParams.Type.V5 && pdfVersion.v < PDFParams.Version.V_1_7.v) {
				// AES-256 (R6) is standard in PDF 2.0 and accepted by PDF 1.7
				// extension level 8 viewers.
				throw new IllegalArgumentException("V5 (AES-256) encryption requires PDF 1.7 or later.");
			}

			this.encryption = new Encryption(this.mainFlow, this.xref, this.fileid, encryptionParams);
		}

		// Page Tree
		this.pages = new PagesFlow(this, this.rootPageRef);

		// XMP Metadata
		if (xmpmetaRef != null) {
			this.xmpmetaFlow = this.mainFlow.forkFragment();
			this.xmpmetaFlow.startObject(xmpmetaRef);
		} else {
			this.xmpmetaFlow = null;
		}

		// OutputIntents
		if (outputIntentRef != null) {
			this.mainFlow.startObject(outputIntentRef);
			this.mainFlow.startHash();
			this.mainFlow.writeName("Type");
			this.mainFlow.writeName("OutputIntent");
			this.mainFlow.lineBreak();

			this.mainFlow.writeName("S");
			this.mainFlow.writeName(pdfVersion.isPdfX() ? "GTS_PDFX" : "GTS_PDFA1");
			this.mainFlow.lineBreak();

			// Resolve the intent: explicit configuration wins; otherwise a
			// built-in profile is chosen so that the intent's color space
			// matches the device color space actually emitted (PDF/A-1
			// requires DeviceRGB/DeviceCMYK content to be backed by an
			// output intent of the same type).
			var intent = this.params.outputIntent();
			if (intent == null) {
				if (pdfVersion == PDFParams.Version.V_PDFX1A
						|| this.params.effectiveColorMode() == PDFParams.ColorMode.CMYK) {
					intent = new OutputIntent("Probe Profile", null, null, "Probe CMYK profile",
							loadResource("Probev1_ICCv2.icc"), 4);
				} else {
					intent = new OutputIntent("sRGB IEC61966-2.1", null, null, null,
							loadResource("sRGB_IEC61966-2-1_no_black_scaling.icc"), 3);
				}
			}

			this.mainFlow.writeName("OutputConditionIdentifier");
			this.mainFlow.writeString(intent.outputConditionIdentifier());
			this.mainFlow.lineBreak();

			if (intent.outputCondition() != null) {
				this.mainFlow.writeName("OutputCondition");
				this.mainFlow.writeText(intent.outputCondition());
				this.mainFlow.lineBreak();
			}

			if (intent.registryName() != null) {
				this.mainFlow.writeName("RegistryName");
				this.mainFlow.writeString(intent.registryName());
				this.mainFlow.lineBreak();
			}

			if (intent.info() != null) {
				this.mainFlow.writeName("Info");
				this.mainFlow.writeText(intent.info());
				this.mainFlow.lineBreak();
			}

			final var profileData = intent.iccProfile();
			if (profileData != null) {
				final var profRef = this.xref.nextObjectRef();
				this.mainFlow.writeName("DestOutputProfile");
				this.mainFlow.writeObjectRef(profRef);
				this.mainFlow.lineBreak();

				this.mainFlow.endHash();
				this.mainFlow.endObject();

				this.mainFlow.startObject(profRef);
				this.mainFlow.startHash();

				this.mainFlow.writeName("N");
				this.mainFlow.writeInt(intent.colorComponents());
				this.mainFlow.lineBreak();

				try (final var pout = this.mainFlow.startStreamFromHash(PDFFragmentOutput.Mode.BINARY)) {
					pout.write(profileData);
				}
			} else {
				this.mainFlow.endHash();
			}
			this.mainFlow.endObject();
		}

		// Outline Info
		if (this.params.bookmarks()) {
			this.outline = new OutlineFlow(this);
		} else {
			this.outline = null;
		}

		// Name Dictionary
		this.nameDict = new NameDictionaryFlow(this);

		// Fragments
		this.fragments = new NameTreeFlow(this, "Dests") {
			protected void writeEntry(final Object entry) throws IOException {
				this.out.writeDestination((Destination) entry);
			}
		};

		// Attachments
		// Attachment permission depends on the profile: forbidden in PDF/A-1,
		// PDF/A-2 and PDF/X; allowed with AFRelationship in PDF/A-3 and A-4f.
		if (pdfVersion.allowsAttachments()) {
			this.embeddedFiles = new NameTreeFlow(this, "EmbeddedFiles") {
				@Override
				protected void writeEntry(final Object entry) throws IOException {
					// The filespec is written as an indirect object so that the
					// PDF/A-3 catalog /AF array can reference the same object,
					// making the attachment an "associated file".
					final var specRef = PDFWriterImpl.this.xref.nextObjectRef();
					if (PDFWriterImpl.this.afRefs == null) {
						PDFWriterImpl.this.afRefs = new ArrayList<>();
					}
					PDFWriterImpl.this.afRefs.add(specRef);
					this.out.writeObjectRef(specRef);

					final var sink = PDFWriterImpl.this.objectSink();
					final var flow = sink.startObject(specRef);
					flow.startHash();
					flow.writeName("Type");
					flow.writeName("Filespec");
					flow.lineBreak();

					final var spec = (Filespec) entry;
					final var att = spec.attachment();

					flow.writeName("F");
					flow.writeFileName(new String[] { spec.name() },
							PDFWriterImpl.this.params.platformEncoding());
					flow.lineBreak();

					if (pdfVersion.v >= PDFParams.Version.V_1_7.v
							&& (att.description() != null || pdfVersion.isPdfA())) {
						flow.writeName("UF");
						flow.writeUTF16(att.description() != null ? att.description() : spec.name());
						flow.lineBreak();
					}

					if (pdfVersion.isPdfA()) {
						// ISO 19005-3 requires every embedded file to declare its
						// relationship to the document content. E-invoices
						// (Factur-X/ZUGFeRD) attach the XML as "Alternative".
						flow.writeName("AFRelationship");
						flow.writeName(att.afRelationshipOrDefault());
						flow.lineBreak();
					}

					flow.writeName("EF");
					flow.startHash();
					flow.writeName("F");
					flow.writeObjectRef(spec.ref());
					flow.endHash();

					flow.endHash();
					sink.endObject();
				}
			};
		} else {
			this.embeddedFiles = null;
		}

		// Page Resources
		this.pageResourceRef = null;
		this.pageResourceFlow = null;

		// Objects
		this.objectsFlow = this.mainFlow.forkFragment();
		this.fonts = new FontFlow(this.nameToResourceRef, this.objectsFlow, this.xref);
		this.images = new ImageFlow(this.nameToResourceRef, this.objectsFlow, this.xref, this.params);
	}

	public PDFWriterImpl(final FragmentedOutput builder) throws IOException {
		this(builder, PDFParams.createDefault());
	}

	@Override
	public PDFParams getParams() {
		return this.params;
	}

	/**
	 * Returns the underlying {@link FragmentedOutput} used to assemble the PDF
	 * byte stream.
	 *
	 * @return the fragmented output builder
	 */
	public FragmentedOutput getBuilder() {
		return this.builder;
	}

	@Override
	public Object getAttribute(final Object key) {
		return this.keyToValue.get(key);
	}

	@Override
	public void putAttribute(final Object key, final Object value) {
		this.keyToValue.put(key, value);
	}

	@Override
	public FontManager getFontManager() {
		if (this.fontManager == null) {
			this.fontManager = new FontManagerImpl(this.params.fontSourceManager(), this);
		}
		return this.fontManager;
	}

	/**
	 * Returns the document part reference for a page about to be created,
	 * starting the first part lazily. {@code null} when not PDF/VT.
	 */
	ObjectRef dpartForNewPage() {
		if (this.dparts == null) {
			return null;
		}
		if (this.dparts.isEmpty()) {
			this.dparts.add(new DPartInfo(this.xref.nextObjectRef(), this.pageOutputs.size(), null));
		}
		return this.dparts.get(this.dparts.size() - 1).ref;
	}

	@Override
	public void nextDocumentPart(final Map<String, String> metadata) throws IOException {
		if (this.dparts == null) {
			throw new UnsupportedOperationException("Document parts require a PDF/VT version.");
		}
		this.dparts.add(new DPartInfo(this.xref.nextObjectRef(), this.pageOutputs.size(), metadata));
	}

	/**
	 * Returns the destination for stream-less indirect objects: an object
	 * stream packer when object streams are enabled, the plain object flow
	 * otherwise.
	 *
	 * @return the object sink
	 */
	PDFObjectSink objectSink() {
		if (this.params.objectStreams()) {
			if (this.objStm == null) {
				this.objStm = new ObjectStreamWriter(this);
			}
			return this.objStm;
		}
		return new PDFObjectSink() {
			@Override
			public net.zamasoft.pdfg2d.pdf.PDFOutput startObject(final ObjectRef ref) throws IOException {
				PDFWriterImpl.this.objectsFlow.startObject(ref);
				return PDFWriterImpl.this.objectsFlow;
			}

			@Override
			public void endObject() throws IOException {
				PDFWriterImpl.this.objectsFlow.endObject();
			}
		};
	}

	/**
	 * Returns the next unique fragment sequence number.
	 *
	 * @return monotonically increasing sequence number
	 */
	protected int nextId() {
		return this.sequence++;
	}

	/**
	 * Loads a classpath resource bundled with this library (e.g. a built-in
	 * ICC profile) into a byte array.
	 *
	 * @param name the resource name relative to this class
	 * @return the resource contents
	 * @throws IOException if the resource is missing or cannot be read
	 */
	private static byte[] loadResource(final String name) throws IOException {
		try (final var in = PDFWriterImpl.class.getResourceAsStream(name)) {
			if (in == null) {
				throw new IOException("Missing bundled resource: " + name);
			}
			return in.readAllBytes();
		}
	}

	ObjectRef ensurePageResourceRef() {
		if (this.pageResourceRef == null) {
			this.pageResourceRef = this.xref.nextObjectRef();
		}
		return this.pageResourceRef;
	}

	void ensurePageResourceFlow() throws IOException {
		if (this.pageResourceFlow != null) {
			return;
		}
		if (this.pageResourceRef == null) {
			throw new IllegalStateException("Page resource reference must be allocated before writing the resource object.");
		}
		final var resourceFlow = this.mainFlow.forkFragment();
		resourceFlow.startObject(this.pageResourceRef);
		this.pageResourceFlow = new ResourceFlow(resourceFlow);
		resourceFlow.endObject();
	}

	@Override
	public PDFOptionalContentGroup createOptionalContentGroup(final String name, final boolean viewable,
			final boolean printable, final boolean initiallyOn, final boolean locked) throws IOException {
		if (this.params.version().v < PDFParams.Version.V_1_5.v) {
			throw new UnsupportedOperationException("Optional content requires PDF 1.5 or later.");
		}
		final var ocgRef = this.xref.nextObjectRef();
		if (this.ocgs == null) {
			this.ocgs = new ArrayList<>();
		}
		this.ocgs.add(new OCGEntry(ocgRef, initiallyOn, locked));
		final var resourceName = this.addResource("Properties", "MC", ocgRef);

		final var sink = this.objectSink();
		final var flow = sink.startObject(ocgRef);
		flow.startHash();
		flow.writeName("Type");
		flow.writeName("OCG");
		flow.writeName("Name");
		flow.writeText(name);
		flow.lineBreak();
		flow.writeName("Usage");
		flow.startHash();
		flow.writeName("View");
		flow.startHash();
		flow.writeName("ViewState");
		flow.writeName(viewable ? "ON" : "OFF");
		flow.endHash();
		flow.writeName("Print");
		flow.startHash();
		flow.writeName("PrintState");
		flow.writeName(printable ? "ON" : "OFF");
		flow.endHash();
		flow.endHash();
		flow.endHash();
		sink.endObject();
		return new PDFOptionalContentGroup(ocgRef, resourceName, name);
	}

	@Override
	public Font useFont(final FontSource source) throws IOException {
		return this.fonts.useFont(source);
	}

	@Override
	public Image loadImage(final Source source) throws IOException {
		return this.images.loadImage(source);
	}

	@Override
	public Image addImage(final BufferedImage image) throws IOException {
		return this.images.addImage(image);
	}

	/**
	 * Registers a named PDF resource (font, image, pattern, etc.) and returns the
	 * unique name assigned to it.
	 * <p>
	 * The name is formed by concatenating {@code prefix} with a zero-based counter
	 * that is incremented each time a resource of the same {@code type} is added.
	 * </p>
	 *
	 * @param type        PDF resource type key (e.g. {@code "Font"}, {@code "XObject"})
	 * @param prefix      name prefix (e.g. {@code "F"} for fonts, {@code "T"} for images)
	 * @param resourceRef object reference for the resource dictionary entry
	 * @return the assigned resource name (e.g. {@code "F0"}, {@code "T3"})
	 * @throws IOException if an I/O error occurs
	 */
	protected String addResource(final String type, final String prefix, final ObjectRef resourceRef)
			throws IOException {
		final var num = this.typeToCount.getOrDefault(type, 0);
		this.typeToCount.put(type, num + 1);
		final var name = prefix + num;
		this.nameToResourceRef.put(name, resourceRef);
		return name;
	}

	public PDFNamedOutput createSpecialGraphicsState() throws IOException {
		final var gsRef = this.xref.nextObjectRef();
		final var name = this.addResource("ExtGState", "G", gsRef);
		final var gsOut = this.objectsFlow;
		gsOut.startObject(gsRef);
		gsOut.startHash();

		gsOut.writeName("Type");
		gsOut.writeName("ExtGState");
		gsOut.lineBreak();

		return new PDFNamedOutput(gsOut, this.params.platformEncoding()) {
			{
				this.setPrecision(PDFWriterImpl.this.params.precision());
			}

			@Override
			public String getName() {
				return name;
			}

			@Override
			public void close() throws IOException {
				this.flush();
				gsOut.endHash();
				gsOut.endObject();
			}
		};
	}

	public PDFGroupImage createGroupImage(final double width, final double height) throws IOException {
		if (this.params.version().v < PDFParams.Version.V_1_4.v) {
			throw new UnsupportedOperationException("Form Type 1 Group feature requires PDF >= 1.4.");
		}
		final var imageRef = this.xref.nextObjectRef();
		final var name = this.addResource("XObject", "T", imageRef);

		final var objectsFlow = this.objectsFlow;

		objectsFlow.startObject(imageRef);
		objectsFlow.startHash();

		objectsFlow.writeName("Type");
		objectsFlow.writeName("XObject");
		objectsFlow.lineBreak();
		objectsFlow.writeName("Subtype");
		objectsFlow.writeName("Form");
		objectsFlow.lineBreak();
		objectsFlow.writeName("FormType");
		objectsFlow.writeInt(1);
		objectsFlow.lineBreak();

		// PDF/A-1 and PDF/X-1a forbid transparency, including transparency
		// group XObjects; emit a plain Form XObject there instead. Alpha and
		// soft masks are suppressed for these targets elsewhere, so the group
		// semantics are not needed.
		if (this.params.version().allowsTransparency()) {
			objectsFlow.writeName("Group");
			objectsFlow.startHash();
			objectsFlow.writeName("Type");
			objectsFlow.writeName("Group");
			objectsFlow.writeName("S");
			objectsFlow.writeName("Transparency");
			objectsFlow.endHash();
		}

		objectsFlow.writeName("Resources");
		final var newResourceFlow = new ResourceFlow(objectsFlow);
		objectsFlow.lineBreak();

		// Convert coordinate system from PDF default (bottom-left) to user default
		// (top-left)
		objectsFlow.writeName("Matrix");
		objectsFlow.startArray();
		objectsFlow.writeReal(1.0 / width);
		objectsFlow.writeReal(0);
		objectsFlow.writeReal(0);
		objectsFlow.writeReal(1.0 / height);
		objectsFlow.writeReal(0);
		objectsFlow.writeReal(0);
		objectsFlow.endArray();
		objectsFlow.lineBreak();

		objectsFlow.writeName("BBox");
		objectsFlow.startArray();
		objectsFlow.writeInt(0);
		objectsFlow.writeReal(0);
		objectsFlow.writeReal(width);
		objectsFlow.writeReal(height);
		objectsFlow.endArray();
		objectsFlow.lineBreak();

		final var formFlow = objectsFlow.forkFragment();
		final var groupFlow = objectsFlow.forkFragment();
		final var groupOut = groupFlow.startStreamFromHash(PDFFragmentOutput.Mode.ASCII);
		objectsFlow.endObject();

		return new PDFGroupImageImpl(this, groupOut, groupFlow, newResourceFlow, width, height, name, imageRef,
				formFlow);
	}

	public PDFNamedGraphicsOutput createTilingPattern(final double width, final double height, final double pageHeight,
			final AffineTransform at) throws IOException {
		// Pattern Object
		final var patternRef = this.xref.nextObjectRef();
		final var name = this.addResource("Pattern", "P", patternRef);

		final var objectsFlow = this.objectsFlow;
		objectsFlow.startObject(patternRef);
		objectsFlow.startHash();

		objectsFlow.writeName("Type");
		objectsFlow.writeName("Pattern");
		objectsFlow.lineBreak();

		objectsFlow.writeName("PatternType");
		objectsFlow.writeInt(1);
		objectsFlow.lineBreak();

		objectsFlow.writeName("PaintType");
		objectsFlow.writeInt(1);
		objectsFlow.lineBreak();

		objectsFlow.writeName("Resources");
		final var newResourceFlow = new ResourceFlow(objectsFlow);
		objectsFlow.lineBreak();

		objectsFlow.writeName("TilingType");
		objectsFlow.writeInt(1);
		objectsFlow.lineBreak();

		// Convert coordinate system from PDF default (bottom-left) to user default
		// (top-left)
		objectsFlow.writeName("Matrix");
		objectsFlow.startArray();

		final var flatMatrix = new double[6];
		if (at != null) {
			at.getMatrix(flatMatrix);
		} else {
			flatMatrix[0] = 1.0; // scx
			flatMatrix[3] = 1.0; // scy
		}
		objectsFlow.writeReal(flatMatrix[0]); // scx
		objectsFlow.writeReal(flatMatrix[1]); // shy
		objectsFlow.writeReal(flatMatrix[2]); // shx
		objectsFlow.writeReal(flatMatrix[3]); // scy
		objectsFlow.writeReal(flatMatrix[4]); // tx
		// ty: pattern space is anchored at the page's bottom-left while user
		// space is top-left, so the Y translation is mirrored. The extra
		// (pageHeight mod tile height) keeps the tile grid phase-aligned with
		// the top edge, so the first visible tile row is not clipped.
		objectsFlow.writeReal(-flatMatrix[5] + pageHeight % (height * flatMatrix[3]));
		objectsFlow.endArray();
		objectsFlow.lineBreak();

		objectsFlow.writeName("BBox");
		objectsFlow.startArray();
		objectsFlow.writeInt(0);
		objectsFlow.writeReal(0);
		objectsFlow.writeReal(width);
		objectsFlow.writeReal(height);
		objectsFlow.endArray();
		objectsFlow.lineBreak();

		objectsFlow.writeName("XStep");
		objectsFlow.writeReal(width);
		objectsFlow.lineBreak();

		objectsFlow.writeName("YStep");
		objectsFlow.writeReal(height);
		objectsFlow.lineBreak();

		final var patternFlow = objectsFlow.forkFragment();
		final var patternOut = patternFlow.startStreamFromHash(PDFFragmentOutput.Mode.ASCII);
		objectsFlow.endObject();

		return new PDFNamedGraphicsOutputImpl(this, patternOut, patternFlow, newResourceFlow, width, height, name);
	}

	public PDFNamedOutput createShadingPattern(final double pageHeight, final AffineTransform at) throws IOException {
		final var patternRef = this.xref.nextObjectRef();
		final var name = this.addResource("Pattern", "P", patternRef);

		final var objectsFlow = this.objectsFlow;
		objectsFlow.startObject(patternRef);
		objectsFlow.startHash();

		objectsFlow.writeName("Type");
		objectsFlow.writeName("Pattern");
		objectsFlow.lineBreak();

		objectsFlow.writeName("PatternType");
		objectsFlow.writeInt(2);
		objectsFlow.lineBreak();

		objectsFlow.writeName("Matrix");
		objectsFlow.startArray();
		final var transform = (at != null) ? new AffineTransform(at) : new AffineTransform();
		transform.preConcatenate(new AffineTransform(1, 0, 0, -1, 0, pageHeight));
		final var flatMatrix = new double[6];
		transform.getMatrix(flatMatrix);
		objectsFlow.writeReal(flatMatrix[0]); // scx
		objectsFlow.writeReal(flatMatrix[1]); // shy
		objectsFlow.writeReal(flatMatrix[2]); // shx
		objectsFlow.writeReal(flatMatrix[3]); // scy
		objectsFlow.writeReal(flatMatrix[4]); // tx
		objectsFlow.writeReal(flatMatrix[5]); // ty
		objectsFlow.endArray();
		objectsFlow.lineBreak();

		final var shadingRef = this.xref.nextObjectRef();
		objectsFlow.writeName("Shading");
		objectsFlow.writeObjectRef(shadingRef);
		objectsFlow.lineBreak();

		objectsFlow.endHash();
		objectsFlow.endObject();

		objectsFlow.startObject(shadingRef);
		objectsFlow.startHash();
		return new PDFNamedOutput(objectsFlow, this.params.platformEncoding()) {
			{
				this.setPrecision(PDFWriterImpl.this.params.precision());
			}

			@Override
			public String getName() {
				return name;
			}

			@Override
			public void close() throws IOException {
				this.flush();
				objectsFlow.endHash();
				objectsFlow.endObject();
			}
		};
	}

	/** Separation color space resource names by colorant name. */
	private Map<String, String> separationNames = null;

	/** Separation colorant names to their color space object references. */
	private Map<String, ObjectRef> separationRefs = null;

	/** DeviceN colorant-set keys to color space resource names. */
	private Map<String, String> deviceNNames = null;

	/** ICCBased RGB color space resource name, or {@code null}. */
	private String iccBasedRGBName = null;

	@Override
	public String useICCBasedRGB() throws IOException {
		final var profile = this.params.rgbProfile();
		if (profile == null) {
			return null;
		}
		if (this.iccBasedRGBName != null) {
			return this.iccBasedRGBName;
		}
		final var flow = this.objectsFlow;

		// The ICC profile stream
		final var profileRef = this.xref.nextObjectRef();
		flow.startObject(profileRef);
		flow.startHash();
		flow.writeName("N");
		flow.writeInt(3);
		flow.writeName("Alternate");
		flow.writeName("DeviceRGB");
		flow.lineBreak();
		try (final var out = flow.startStreamFromHash(PDFFragmentOutput.Mode.BINARY)) {
			out.write(profile);
		}
		flow.endObject();

		// The color space array [/ICCBased stream]
		final var csRef = this.xref.nextObjectRef();
		final var name = this.addResource("ColorSpace", "CS", csRef);
		flow.startObject(csRef);
		flow.startArray();
		flow.writeName("ICCBased");
		flow.writeObjectRef(profileRef);
		flow.endArray();
		flow.endObject();

		this.iccBasedRGBName = name;
		return name;
	}

	/**
	 * Aligns a named color's alternate with the document's process color
	 * space: the effective color mode first (PDF/X CMYK enforcement), then
	 * the PDF/A OutputIntent color space, which device color spaces (and
	 * therefore Separation/DeviceN alternates) must match.
	 *
	 * @param alternate the alternate color as given by the caller
	 * @return the aligned alternate
	 */
	private net.zamasoft.pdfg2d.gc.paint.Color alignAlternate(final net.zamasoft.pdfg2d.gc.paint.Color alternate) {
		var alt = switch (this.params.effectiveColorMode()) {
			case GRAY -> net.zamasoft.pdfg2d.util.ColorUtils.toGray(alternate);
			case CMYK -> net.zamasoft.pdfg2d.util.ColorUtils.toCMYK(alternate);
			default -> alternate;
		};
		if (this.params.version().isPdfA()) {
			final var intent = this.params.outputIntent();
			final var components = (intent != null) ? intent.colorComponents()
					: (this.params.effectiveColorMode() == PDFParams.ColorMode.CMYK ? 4 : 3);
			alt = switch (components) {
				case 4 -> net.zamasoft.pdfg2d.util.ColorUtils.toCMYK(alt);
				case 1 -> net.zamasoft.pdfg2d.util.ColorUtils.toGray(alt);
				default -> net.zamasoft.pdfg2d.gc.paint.RGBColor.create(alt.getRed(), alt.getGreen(),
						alt.getBlue());
			};
		}
		return alt;
	}

	@Override
	public String useSeparation(final String colorantName, final net.zamasoft.pdfg2d.gc.paint.Color alternate)
			throws IOException {
		if (this.separationNames == null) {
			this.separationNames = new HashMap<>();
		}
		var name = this.separationNames.get(colorantName);
		if (name != null) {
			return name;
		}

		final var alt = this.alignAlternate(alternate);

		final String altSpace;
		final float[] white;
		final float[] full;
		switch (alt.getColorType()) {
			case GRAY -> {
				altSpace = "DeviceGray";
				white = new float[] { 1 };
				full = new float[] { alt.getComponent(0) };
			}
			case CMYK -> {
				altSpace = "DeviceCMYK";
				white = new float[] { 0, 0, 0, 0 };
				full = new float[] { alt.getComponent(0), alt.getComponent(1), alt.getComponent(2),
						alt.getComponent(3) };
			}
			default -> {
				altSpace = "DeviceRGB";
				white = new float[] { 1, 1, 1 };
				full = new float[] { alt.getRed(), alt.getGreen(), alt.getBlue() };
			}
		}

		final var ref = this.xref.nextObjectRef();
		name = this.addResource("ColorSpace", "CS", ref);
		final var flow = this.objectsFlow;
		flow.startObject(ref);
		flow.startArray();
		flow.writeName("Separation");
		flow.writeName(colorantName);
		flow.writeName(altSpace);
		// Tint transform: linear from white (tint 0) to the alternate (tint 1)
		flow.startHash();
		flow.writeName("FunctionType");
		flow.writeInt(2);
		flow.writeName("Domain");
		flow.startArray();
		flow.writeInt(0);
		flow.writeInt(1);
		flow.endArray();
		flow.writeName("N");
		flow.writeInt(1);
		flow.writeName("C0");
		flow.startArray();
		for (final var c : white) {
			flow.writeReal(c);
		}
		flow.endArray();
		flow.writeName("C1");
		flow.startArray();
		for (final var c : full) {
			flow.writeReal(c);
		}
		flow.endArray();
		flow.endHash();
		flow.endArray();
		flow.endObject();

		this.separationNames.put(colorantName, name);
		if (this.separationRefs == null) {
			this.separationRefs = new HashMap<>();
		}
		this.separationRefs.put(colorantName, ref);
		return name;
	}

	@Override
	public String useDeviceN(final net.zamasoft.pdfg2d.gc.paint.SpotColor[] colorants) throws IOException {
		final var keyBuilder = new StringBuilder();
		for (final var colorant : colorants) {
			keyBuilder.append(colorant.name()).append((char) 0);
		}
		final var key = keyBuilder.toString();
		if (this.deviceNNames == null) {
			this.deviceNNames = new HashMap<>();
		}
		var name = this.deviceNNames.get(key);
		if (name != null) {
			return name;
		}

		// Align each alternate with the process space, then bring all of them
		// to one common space (CMYK when mixed) for the tint transform range.
		final var alts = new net.zamasoft.pdfg2d.gc.paint.Color[colorants.length];
		var mixed = false;
		for (var i = 0; i < colorants.length; ++i) {
			alts[i] = this.alignAlternate(colorants[i].alternate());
			mixed |= alts[i].getColorType() != alts[0].getColorType();
		}
		if (mixed) {
			for (var i = 0; i < alts.length; ++i) {
				alts[i] = net.zamasoft.pdfg2d.util.ColorUtils.toCMYK(alts[i]);
			}
		}

		// The output channels of the tint transform. CMYK channels are ink
		// amounts and combine additively (clamped); RGB/Gray channels combine
		// on their complements (ink absorption).
		final String altSpace;
		final int outComponents;
		final boolean complement;
		switch (alts[0].getColorType()) {
			case GRAY -> {
				altSpace = "DeviceGray";
				outComponents = 1;
				complement = true;
			}
			case CMYK -> {
				altSpace = "DeviceCMYK";
				outComponents = 4;
				complement = false;
			}
			default -> {
				altSpace = "DeviceRGB";
				outComponents = 3;
				complement = true;
			}
		}

		// PostScript calculator (FunctionType 4) program. Inputs t1..tN are on
		// the stack (tN topmost). For each output channel the weighted sum of
		// the inputs is accumulated and clamped to 1; complement-space
		// channels weight by (1 - component) and finish with 1 - sum.
		final var n = colorants.length;
		final var ps = new StringBuilder("{\n");
		for (var j = 0; j < outComponents; ++j) {
			ps.append("0.0");
			for (var i = 0; i < n; ++i) {
				final var component = alts[i].getComponent(j);
				final var coefficient = complement
						? 1 - (j == 0 && alts[i].getColorType() == net.zamasoft.pdfg2d.gc.paint.Color.Type.GRAY
								? alts[i].getComponent(0)
								: switch (j) {
									case 0 -> alts[i].getRed();
									case 1 -> alts[i].getGreen();
									default -> alts[i].getBlue();
								})
						: component;
				if (coefficient != 0) {
					// Copy input t_i: it sits below the accumulator and the
					// j outputs computed so far.
					ps.append(' ').append(n - 1 - i + j + 1).append(" index ")
							.append(coefficient).append(" mul add");
				}
			}
			ps.append(" dup 1.0 gt {pop 1.0} if");
			if (complement) {
				ps.append(" neg 1.0 add");
			}
			ps.append('\n');
		}
		// Drop the inputs, keeping the outputs in order.
		ps.append(n + outComponents).append(' ').append(outComponents).append(" roll\n");
		for (var i = 0; i < n; ++i) {
			ps.append("pop ");
		}
		ps.append("\n}");
		final var program = ps.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);

		final var funcRef = this.xref.nextObjectRef();
		final var flow = this.objectsFlow;
		flow.startObject(funcRef);
		flow.startHash();
		flow.writeName("FunctionType");
		flow.writeInt(4);
		flow.writeName("Domain");
		flow.startArray();
		for (var i = 0; i < n; ++i) {
			flow.writeInt(0);
			flow.writeInt(1);
		}
		flow.endArray();
		flow.writeName("Range");
		flow.startArray();
		for (var j = 0; j < outComponents; ++j) {
			flow.writeInt(0);
			flow.writeInt(1);
		}
		flow.endArray();
		flow.lineBreak();
		try (final var sout = flow.startStreamFromHash(PDFFragmentOutput.Mode.RAW)) {
			sout.write(program);
		}
		flow.endObject();

		// PDF/A-2 (6.2.4.4) requires a Colorants dictionary entry for every
		// spot colorant of a DeviceN space, each one a Separation color
		// space; reuse the document-wide Separation resources.
		final var sepRefs = new ObjectRef[n];
		for (var i = 0; i < n; ++i) {
			this.useSeparation(colorants[i].name(), colorants[i].alternate());
			sepRefs[i] = this.separationRefs.get(colorants[i].name());
		}

		final var ref = this.xref.nextObjectRef();
		name = this.addResource("ColorSpace", "CS", ref);
		final var sink = this.objectSink();
		final var csFlow = sink.startObject(ref);
		csFlow.startArray();
		csFlow.writeName("DeviceN");
		csFlow.startArray();
		for (final var colorant : colorants) {
			csFlow.writeName(colorant.name());
		}
		csFlow.endArray();
		csFlow.writeName(altSpace);
		csFlow.writeObjectRef(funcRef);
		csFlow.startHash();
		csFlow.writeName("Colorants");
		csFlow.startHash();
		for (var i = 0; i < n; ++i) {
			csFlow.writeName(colorants[i].name());
			csFlow.writeObjectRef(sepRefs[i]);
		}
		csFlow.endHash();
		csFlow.endHash();
		csFlow.endArray();
		sink.endObject();

		this.deviceNNames.put(key, name);
		return name;
	}

	@Override
	public String createLuminositySoftMask(final String shadingPatternName, final double width, final double height)
			throws IOException {
		if (!this.params.version().allowsTransparency()) {
			throw new UnsupportedOperationException(
					"Soft masks require transparency support (PDF 1.4+, not PDF/A-1 or PDF/X-1a).");
		}
		final var patternRef = this.nameToResourceRef.get(shadingPatternName);
		if (patternRef == null) {
			throw new IllegalArgumentException("Unknown shading pattern: " + shadingPatternName);
		}

		// The mask source: a Form XObject with a DeviceGray transparency
		// group whose content fills the whole page area with the grayscale
		// (alpha-ramp) shading pattern. Both the form and the page share the
		// default coordinate space, so the mask aligns with painted content.
		final var formRef = this.xref.nextObjectRef();
		final var objectsFlow = this.objectsFlow;
		objectsFlow.startObject(formRef);
		objectsFlow.startHash();
		objectsFlow.writeName("Type");
		objectsFlow.writeName("XObject");
		objectsFlow.writeName("Subtype");
		objectsFlow.writeName("Form");
		objectsFlow.writeName("FormType");
		objectsFlow.writeInt(1);
		objectsFlow.lineBreak();
		objectsFlow.writeName("BBox");
		objectsFlow.startArray();
		objectsFlow.writeInt(0);
		objectsFlow.writeInt(0);
		objectsFlow.writeReal(width);
		objectsFlow.writeReal(height);
		objectsFlow.endArray();
		objectsFlow.lineBreak();
		objectsFlow.writeName("Group");
		objectsFlow.startHash();
		objectsFlow.writeName("Type");
		objectsFlow.writeName("Group");
		objectsFlow.writeName("S");
		objectsFlow.writeName("Transparency");
		objectsFlow.writeName("CS");
		objectsFlow.writeName("DeviceGray");
		objectsFlow.endHash();
		objectsFlow.lineBreak();
		objectsFlow.writeName("Resources");
		objectsFlow.startHash();
		objectsFlow.writeName("Pattern");
		objectsFlow.startHash();
		objectsFlow.writeName(shadingPatternName);
		objectsFlow.writeObjectRef(patternRef);
		objectsFlow.endHash();
		objectsFlow.endHash();
		objectsFlow.lineBreak();
		try (final var out = objectsFlow.startStreamFromHash(PDFFragmentOutput.Mode.ASCII)) {
			final var content = "/Pattern cs /" + shadingPatternName + " scn\n0 0 " + (float) width + " "
					+ (float) height + " re f\n";
			out.write(content.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		}
		objectsFlow.endObject();

		// The ExtGState applying the mask
		final var gsRef = this.xref.nextObjectRef();
		final var name = this.addResource("ExtGState", "G", gsRef);
		objectsFlow.startObject(gsRef);
		objectsFlow.startHash();
		objectsFlow.writeName("Type");
		objectsFlow.writeName("ExtGState");
		objectsFlow.lineBreak();
		objectsFlow.writeName("SMask");
		objectsFlow.startHash();
		objectsFlow.writeName("Type");
		objectsFlow.writeName("Mask");
		objectsFlow.writeName("S");
		objectsFlow.writeName("Luminosity");
		objectsFlow.writeName("G");
		objectsFlow.writeObjectRef(formRef);
		objectsFlow.endHash();
		objectsFlow.lineBreak();
		objectsFlow.writeName("AIS");
		objectsFlow.writeBoolean(false);
		objectsFlow.lineBreak();
		objectsFlow.endHash();
		objectsFlow.endObject();
		return name;
	}

	public OutputStream addAttachment(final String filename, final Attachment attachment) throws IOException {
		if (attachment.description() == null && filename == null) {
			throw new NullPointerException("Both description and filename cannot be null.");
		}
		if (this.params.version().v < PDFParams.Version.V_1_4.v) {
			throw new UnsupportedOperationException("File attachment requires PDF 1.4 or later.");
		}
		if (!this.params.version().allowsAttachments()) {
			throw new UnsupportedOperationException(
					"File attachments are not allowed in " + this.params.version() + ".");
		}

		var desc = attachment.description();
		var name = filename;
		if (desc == null) {
			desc = name;
		} else if (name == null) {
			name = desc;
		}

		final var fileRef = this.xref.nextObjectRef();
		final var objectsFlow = this.objectsFlow;
		objectsFlow.startObject(fileRef);
		objectsFlow.startHash();

		objectsFlow.writeName("Type");
		objectsFlow.writeName("EmbeddedFile");
		objectsFlow.lineBreak();

		// PDF/A-3 requires embedded file streams to declare a MIME subtype.
		final var mimeType = (attachment.mimeType() != null) ? attachment.mimeType()
				: (this.params.version().isPdfA() ? "application/octet-stream" : null);
		if (mimeType != null) {
			objectsFlow.writeName("Subtype");
			objectsFlow.writeName(mimeType);
			objectsFlow.lineBreak();
		}

		final var filespec = new Filespec(attachment, name, fileRef);
		this.embeddedFiles.addEntry(desc, filespec);

		final var paramsFlow = objectsFlow.forkFragment();
		try {
			final var md5 = MessageDigest.getInstance("MD5");
			final var out = objectsFlow.startStreamFromHash(PDFFragmentOutput.Mode.BINARY);
			return new FilterOutputStream(out) {
				private int size = 0;

				public void write(byte[] buff, int off, int len) throws IOException {
					this.out.write(buff, off, len);
					md5.update(buff, off, len);
					this.size += len;
				}

				public void write(byte[] buff) throws IOException {
					this.out.write(buff);
					md5.update(buff);
					this.size += buff.length;
				}

				public void write(int b) throws IOException {
					this.out.write(b);
					md5.update((byte) b);
					++this.size;
				}

				public void close() throws IOException {
					this.out.close();
					objectsFlow.endObject();

					paramsFlow.writeName("Params");
					paramsFlow.startHash();

					paramsFlow.writeName("Size");
					paramsFlow.writeInt(this.size);
					paramsFlow.lineBreak();

					paramsFlow.writeName("CheckSum");
					byte[] hash = md5.digest();
					paramsFlow.writeBytes8(hash, 0, hash.length);
					paramsFlow.lineBreak();

					paramsFlow.endHash();
					paramsFlow.close();
				};
			};
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public PDFPageOutput nextPage(final double width, final double height) throws IOException {
		return this.pages.createPage(width, height);
	}

	/**
	 * Writes the document information dictionary and returns its reference.
	 * For PDF/X profiles the {@code GTS_PDFXVersion} identification is
	 * included as required by ISO 15930.
	 *
	 * @return the reference of the written Info dictionary
	 * @throws IOException if an I/O error occurs
	 */
	private ObjectRef writeDocumentInfo(final String author, final String creator, final String producer,
			final String title, final String subject, final String keywords, final long create, final long modify)
			throws IOException {
		final var zone = TimeZone.getDefault();
		final var infoRef = this.xref.nextObjectRef();
		this.objectsFlow.startObject(infoRef);
		this.objectsFlow.startHash();

		final var pdfxVersion = this.params.version().pdfxVersion();
		if (pdfxVersion != null) {
			this.objectsFlow.writeName("GTS_PDFXVersion");
			this.objectsFlow.writeText(pdfxVersion);
			this.objectsFlow.lineBreak();
		}
		if (this.params.version().isPdfVT()) {
			this.objectsFlow.writeName("GTS_PDFVTVersion");
			this.objectsFlow.writeText("PDF/VT-1");
			this.objectsFlow.lineBreak();
		}

		if (author != null) {
			this.objectsFlow.writeName("Author");
			this.objectsFlow.writeText(author);
			this.objectsFlow.lineBreak();
		}

		this.objectsFlow.writeName("CreationDate");
		this.objectsFlow.writeDate(create, zone);
		this.objectsFlow.lineBreak();

		this.objectsFlow.writeName("ModDate");
		this.objectsFlow.writeDate(modify, zone);
		this.objectsFlow.lineBreak();

		if (creator != null) {
			this.objectsFlow.writeName("Creator");
			this.objectsFlow.writeText(creator);
			this.objectsFlow.lineBreak();
		}

		if (producer != null) {
			this.objectsFlow.writeName("Producer");
			this.objectsFlow.writeText(producer);
			this.objectsFlow.lineBreak();
		}

		if (title != null) {
			this.objectsFlow.writeName("Title");
			this.objectsFlow.writeText(title);
			this.objectsFlow.lineBreak();
		}

		if (subject != null) {
			this.objectsFlow.writeName("Subject");
			this.objectsFlow.writeText(subject);
			this.objectsFlow.lineBreak();
		}

		if (keywords != null) {
			this.objectsFlow.writeName("Keywords");
			this.objectsFlow.writeText(keywords);
			this.objectsFlow.lineBreak();
		}

		this.objectsFlow.writeName("Trapped");
		this.objectsFlow.writeName("False");
		this.objectsFlow.lineBreak();

		this.objectsFlow.endHash();
		this.objectsFlow.endObject();
		return infoRef;
	}

	public void close() throws IOException {
		try {
			// Meta Info
			final var info = this.params.metaInfo();

			final var author = info.getAuthor();
			final var creator = info.getCreator();
			final var producer = info.getProducer();
			var title = info.getTitle();
			final var subject = info.getSubject();
			final var keywords = info.getKeywords();
			var create = info.getCreationDate();
			if (create == -1L) {
				create = System.currentTimeMillis();
			}
			var modify = info.getModDate();
			if (modify == -1L) {
				modify = create;
			}

			final var taggedParams = this.params.tagged();
			if ((this.params.version().isPdfX() || (taggedParams != null && taggedParams.pdfua()))
					&& (title == null || title.isEmpty())) {
				title = "Untitled";
			}

			// PDF/A-4 (PDF 2.0 based) forbids the document information
			// dictionary; all metadata lives in the XMP packet instead.
			final ObjectRef infoRef;
			if (this.params.version().pdfaPart() == 4) {
				infoRef = null;
			} else {
				infoRef = this.writeDocumentInfo(author, creator, producer, title, subject, keywords, create, modify);
			}

			// XML Metadata
			if (this.xmpmetaFlow != null) {
				XMPMetadataWriter.write(this.xmpmetaFlow, this.params.version(),
						taggedParams != null && taggedParams.pdfua(), author, creator, producer, title,
						keywords, create, modify, info.getFacturX());
			}
			// Catalog - Page Info
			this.pages.close();

			// Outline
			if (this.outline != null) {
				this.outline.close();
			}

			// Anchor
			this.fragments.close();

			// Attachments
			if (this.embeddedFiles != null) {
				this.embeddedFiles.close();
			}

			// Name Dictionary
			this.nameDict.close();

			// Associated files (PDF/A-3): every embedded file must be linked
			// from the catalog /AF array to qualify as an associated file.
			if (this.params.version().isPdfA() && this.afRefs != null) {
				this.catalogFlow.writeName("AF");
				this.catalogFlow.startArray();
				for (final var afRef : this.afRefs) {
					this.catalogFlow.writeObjectRef(afRef);
				}
				this.catalogFlow.endArray();
				this.catalogFlow.lineBreak();
			}

			// OCGs
			this.writeOCProperties();

			// PDF/VT document part hierarchy: one DPart leaf per document
			// part (record). Parts are opened with nextDocumentPart(); a
			// single implicit part covers documents that never call it.
			if (this.dpartRootRef != null && this.dparts != null && !this.dparts.isEmpty()
					&& !this.pageOutputs.isEmpty()) {
				this.catalogFlow.writeName("DPartRoot");
				this.catalogFlow.writeObjectRef(this.dpartRootRef);
				this.catalogFlow.lineBreak();

				final var dpartSink = this.objectSink();
				var flow = dpartSink.startObject(this.dpartRootRef);
				flow.startHash();
				flow.writeName("Type");
				flow.writeName("DPartRoot");
				flow.writeName("DPartRootNode");
				flow.writeObjectRef(this.dpartNodeRef);
				flow.endHash();
				dpartSink.endObject();

				flow = dpartSink.startObject(this.dpartNodeRef);
				flow.startHash();
				flow.writeName("Type");
				flow.writeName("DPart");
				flow.writeName("Parent");
				flow.writeObjectRef(this.dpartRootRef);
				flow.writeName("DParts");
				flow.startArray();
				flow.startArray();
				for (final var part : this.dparts) {
					flow.writeObjectRef(part.ref);
				}
				flow.endArray();
				flow.endArray();
				flow.endHash();
				dpartSink.endObject();

				for (var i = 0; i < this.dparts.size(); ++i) {
					final var part = this.dparts.get(i);
					final var endPage = (i + 1 < this.dparts.size())
							? this.dparts.get(i + 1).startPage - 1
							: this.pageOutputs.size() - 1;
					if (part.startPage > endPage) {
						// A part without pages (nextDocumentPart called but
						// no page created); still emitted for structure.
					}
					flow = dpartSink.startObject(part.ref);
					flow.startHash();
					flow.writeName("Type");
					flow.writeName("DPart");
					flow.writeName("Parent");
					flow.writeObjectRef(this.dpartNodeRef);
					if (part.startPage <= endPage) {
						flow.writeName("Start");
						flow.writeObjectRef(this.pageOutputs.get(part.startPage).getPageRef());
						if (endPage > part.startPage) {
							flow.writeName("End");
							flow.writeObjectRef(this.pageOutputs.get(endPage).getPageRef());
						}
					}
					if (part.metadata != null && !part.metadata.isEmpty()) {
						flow.writeName("DPM");
						flow.startHash();
						for (final var e : part.metadata.entrySet()) {
							flow.writeName(e.getKey());
							flow.writeText(e.getValue());
						}
						flow.endHash();
					}
					flow.endHash();
					dpartSink.endObject();
				}
			}

			// Logical structure (tagged PDF)
			if (this.structure != null) {
				final var taggedForStruct = this.params.tagged();
				if (taggedForStruct != null && taggedForStruct.pdfua() && this.structure.hasHeadingSkip()) {
					// PDF/UA (Matterhorn 14-002): heading levels must not be
					// skipped. Fail fast rather than emit a non-conforming file.
					throw new IllegalStateException(
							"PDF/UA forbids skipping heading levels (e.g. H1 followed by H3).");
				}
				final var structRootRef = this.structure.writeTo(this.objectSink(), this.xref);
				this.catalogFlow.writeName("StructTreeRoot");
				this.catalogFlow.writeObjectRef(structRootRef);
				this.catalogFlow.lineBreak();
				this.catalogFlow.writeName("MarkInfo");
				this.catalogFlow.startHash();
				this.catalogFlow.writeName("Marked");
				this.catalogFlow.writeBoolean(true);
				this.catalogFlow.endHash();
				this.catalogFlow.lineBreak();
				final var lang = this.params.tagged().lang();
				if (lang != null) {
					this.catalogFlow.writeName("Lang");
					this.catalogFlow.writeString(lang);
					this.catalogFlow.lineBreak();
				}
			}

			// AcroForm (interactive form fields)
			if (this.acroFormFields != null && !this.acroFormFields.isEmpty()) {
				this.catalogFlow.writeName("AcroForm");
				this.catalogFlow.startHash();
				this.catalogFlow.writeName("Fields");
				this.catalogFlow.startArray();
				for (final var fieldRef : this.acroFormFields) {
					this.catalogFlow.writeObjectRef(fieldRef);
				}
				this.catalogFlow.endArray();
				this.catalogFlow.lineBreak();
				// Default resources and appearance for viewer-drawn fields.
				this.catalogFlow.writeName("DR");
				this.catalogFlow.startHash();
				this.catalogFlow.writeName("Font");
				this.catalogFlow.startHash();
				if (this.helvFontRef != null) {
					this.catalogFlow.writeName("Helv");
					this.catalogFlow.writeObjectRef(this.helvFontRef);
				}
				if (this.zadbFontRef != null) {
					this.catalogFlow.writeName("ZaDb");
					this.catalogFlow.writeObjectRef(this.zadbFontRef);
				}
				this.catalogFlow.endHash();
				this.catalogFlow.endHash();
				this.catalogFlow.writeName("DA");
				this.catalogFlow.writeText("/Helv 0 Tf 0 g");
				if (this.acroFormNeedsAppearances) {
					this.catalogFlow.writeName("NeedAppearances");
					this.catalogFlow.writeBoolean(true);
				}
				this.catalogFlow.endHash();
				this.catalogFlow.lineBreak();
			}

			// ViewerPreferences
			ViewerPreferencesWriter.write(this.catalogFlow, this.params);

			// Open Action
			final Action action = this.params.openAction();
			if (action != null) {
				this.catalogFlow.writeName("OpenAction");
				this.catalogFlow.startHash();
				action.writeTo(this.catalogFlow, this.params);
				this.catalogFlow.endHash();
				this.catalogFlow.lineBreak();
			}

			// Catalog
			this.catalogFlow.close();

			// Flush packed objects before the object flow closes
			if (this.objStm != null) {
				this.objStm.close();
			}

			// Resources
			this.fonts.close();
			if (this.pageResourceFlow != null) {
				this.pageResourceFlow.close();
			}
			this.objectsFlow.close();

			if (this.params.linearized()) {
				new LinearizedPDFAssembler(this, this.linDictRef, this.linDictFlow, this.rootPageRef, this.fileid)
						.assemble(infoRef);
			} else if (this.params.objectStreams()) {
				// Cross-reference stream with type-2 entries for packed objects
				this.xref.closeWithXrefStream(this.builder.getPositionInfo(), infoRef, this.fileid);
			} else {
				// XRef
				this.xref.close(this.builder.getPositionInfo(), infoRef, this.fileid, this.encryption);
			}

			this.mainFlow.close();
		} finally {
			this.builder.close();
		}
		if (this.fontManager != null) {
			this.fontManager.close();
		}
	}


	/**
	 * Writes the {@code /OCProperties} entry of the catalog and its Optional
	 * Content Group configuration object.
	 * <p>
	 * All registered OCGs are turned ON in the default configuration; their
	 * actual visibility per medium (screen/print) is controlled by the
	 * {@code Usage} dictionary written with each OCG, activated through the
	 * usage application dictionaries ({@code AS}) emitted here.
	 * </p>
	 *
	 * @throws IOException if an I/O error occurs
	 */
	private void writeOCProperties() throws IOException {
		if (this.ocgs == null) {
			return;
		}
		final ObjectRef ref = this.xref.nextObjectRef();
		this.catalogFlow.writeName("OCProperties");
		this.catalogFlow.writeObjectRef(ref);

		final var sink = this.objectSink();
		final var flow = sink.startObject(ref);
		flow.startHash();
		flow.writeName("OCGs");
		flow.startArray();
		for (final var entry : this.ocgs) {
			flow.writeObjectRef(entry.ref());
		}
		flow.endArray();

		flow.writeName("D");
		flow.startHash();
		flow.writeName("Name");
		flow.writeText("Default");
		flow.writeName("ON");
		flow.startArray();
		for (final var entry : this.ocgs) {
			if (entry.initiallyOn()) {
				flow.writeObjectRef(entry.ref());
			}
		}
		flow.endArray();
		flow.writeName("OFF");
		flow.startArray();
		for (final var entry : this.ocgs) {
			if (!entry.initiallyOn()) {
				flow.writeObjectRef(entry.ref());
			}
		}
		flow.endArray();
		flow.writeName("Locked");
		flow.startArray();
		for (final var entry : this.ocgs) {
			if (entry.locked()) {
				flow.writeObjectRef(entry.ref());
			}
		}
		flow.endArray();
		// PDF/A forbids the usage application dictionaries (/AS); without
		// them the per-OCG /Usage states are informational only.
		if (!this.params.version().isPdfA()) {
			this.writeOCUsageApplications(flow);
		}
		flow.endHash();

		flow.endHash();
		sink.endObject();
	}

	/**
	 * Writes the usage application dictionaries ({@code /AS}) that make
	 * viewers apply each OCG's {@code /Usage} states for the View and Print
	 * events automatically.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	private void writeOCUsageApplications(final PDFOutput flow) throws IOException {
		flow.writeName("AS");
		flow.startArray();

		flow.startHash();
		flow.writeName("Event");
		flow.writeName("View");
		flow.writeName("OCGs");
		flow.startArray();
		for (final var entry : this.ocgs) {
			flow.writeObjectRef(entry.ref());
		}
		flow.endArray();
		flow.writeName("Category");
		flow.startArray();
		flow.writeName("View");
		flow.endArray();
		flow.endHash();

		flow.startHash();
		flow.writeName("Event");
		flow.writeName("Print");
		flow.writeName("OCGs");
		flow.startArray();
		for (final var entry : this.ocgs) {
			flow.writeObjectRef(entry.ref());
		}
		flow.endArray();
		flow.writeName("Category");
		flow.startArray();
		flow.writeName("Print");
		flow.endArray();
		flow.endHash();

		flow.endArray();
	}
}
