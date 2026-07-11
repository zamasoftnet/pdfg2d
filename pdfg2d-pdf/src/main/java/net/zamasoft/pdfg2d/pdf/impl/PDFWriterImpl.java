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

import net.zamasoft.pdfg2d.pdf.PDFOutput.Destination;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.action.Action;
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;
import net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage;
import net.zamasoft.pdfg2d.pdf.params.EncryptionParams;
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

	private static final byte[] PDF12 = { '1', '.', '2' };

	private static final byte[] PDF13 = { '1', '.', '3' };

	private static final byte[] PDF14 = { '1', '.', '4' };

	private static final byte[] PDF15 = { '1', '.', '5' };

	private static final byte[] PDF16 = { '1', '.', '6' };

	private static final byte[] PDF17 = { '1', '.', '7' };

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

	private List<ObjectRef> ocgs = null;

	private ObjectRef linDictRef;
	private PDFFragmentOutputImpl linDictFlow;

	private final ObjectRef rootPageRef;

	public PDFWriterImpl(final FragmentedOutput builder, final PDFParams params) throws IOException {
		this.params = (params != null) ? params : PDFParams.createDefault();
		this.builder = builder.supportsPositionInfo() ? builder : new PositionTrackingOutput(builder);

		final var id = this.nextId();
		this.builder.addFragment();
		final var out = new FragmentOutputAdapter(this.builder, id);
		this.mainFlow = new PDFFragmentOutputImpl(out, this, id, -1, null);

		// Header
		final var pdfVersion = this.params.version();
		this.mainFlow.write(HEADER);
		switch (pdfVersion) {
			case V_1_2 -> this.mainFlow.write(PDF12);
			case V_1_3 -> this.mainFlow.write(PDF13);
			case V_1_4, V_PDFX1A -> this.mainFlow.write(PDF14);
			case V_PDFA1B -> {
				this.mainFlow.write(PDF14);
				this.mainFlow.lineBreak();
				// PDF/A-1 binary identification
				this.mainFlow.write('%');
				for (var i = 0; i < 4; ++i) {
					this.mainFlow.write(RND.nextInt(128) + 127);
				}
			}
			case V_1_5 -> this.mainFlow.write(PDF15);
			case V_1_6 -> this.mainFlow.write(PDF16);
			case V_1_7 -> this.mainFlow.write(PDF17);
		}
		this.mainFlow.lineBreak();

		if (this.params.linearized()) {
			this.linDictFlow = this.mainFlow.forkFragment();
		}

		// Start root element (Catalog)
		this.xref = new XRefImpl(this.mainFlow);

		if (this.params.linearized()) {
			this.linDictRef = this.xref.nextObjectRef();
			// We will fill this later in closeLinearized
		}

		this.mainFlow.startHash();

		this.mainFlow.writeName("Type");
		this.mainFlow.writeName("Catalog");
		this.mainFlow.lineBreak();

		// Version
		if (pdfVersion.v >= PDFParams.Version.V_1_4.v) {
			this.mainFlow.writeName("Version");
			switch (pdfVersion) {
				case V_1_4:
				case V_PDFA1B:
				case V_PDFX1A:
					this.mainFlow.writeName("1.4");
					break;

				case V_1_5:
					this.mainFlow.writeName("1.5");
					break;

				case V_1_6:
					this.mainFlow.writeName("1.6");
					break;

				case V_1_7:
					this.mainFlow.writeName("1.7");
					break;
				default:
					throw new IllegalStateException();
			}
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
			if (pdfVersion == PDFParams.Version.V_PDFA1B) {
				throw new IllegalArgumentException("Encryption cannot be used in PDF/A-1.");
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
			this.mainFlow.writeName(pdfVersion == PDFParams.Version.V_PDFA1B ? "GTS_PDFA1" : "GTS_PDFX");
			this.mainFlow.lineBreak();

			String iccName, iccFile;
			int colors;
			if (pdfVersion == PDFParams.Version.V_PDFX1A) {
				iccName = "Probe Profile";
				iccFile = "Probev1_ICCv2.icc";
				colors = 4;
			} else {
				iccName = "sRGB IEC61966-2.1";
				iccFile = "sRGB_IEC61966-2-1_no_black_scaling.icc";
				colors = 3;
			}

			this.mainFlow.writeName("OutputConditionIdentifier");
			this.mainFlow.writeString(iccName);
			this.mainFlow.lineBreak();

			final var profRef = this.xref.nextObjectRef();
			this.mainFlow.writeName("DestOutputProfile");
			this.mainFlow.writeObjectRef(profRef);
			this.mainFlow.lineBreak();

			this.mainFlow.endHash();
			this.mainFlow.endObject();

			this.mainFlow.startObject(profRef);
			this.mainFlow.startHash();

			this.mainFlow.writeName("N");
			this.mainFlow.writeInt(colors);
			this.mainFlow.lineBreak();

			try (final var pout = this.mainFlow.startStreamFromHash(PDFFragmentOutput.Mode.BINARY);
					final var in = PDFWriterImpl.class.getResourceAsStream(iccFile)) {
				final var buff = this.mainFlow.getBuffer();
				for (int len = in.read(buff); len != -1; len = in.read(buff)) {
					pout.write(buff, 0, len);
				}
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
		if (pdfVersion.v >= PDFParams.Version.V_1_4.v && pdfVersion.v != PDFParams.Version.V_PDFA1B.v) {
			this.embeddedFiles = new NameTreeFlow(this, "EmbeddedFiles") {
				@Override
				protected void writeEntry(final Object entry) throws IOException {
					this.out.startHash();
					this.out.writeName("Type");
					this.out.writeName("Filespec");
					this.out.lineBreak();

					final var spec = (Filespec) entry;
					final var att = spec.attachment();

					this.out.writeName("F");
					this.out.writeFileName(new String[] { spec.name() },
							PDFWriterImpl.this.params.platformEncoding());
					this.out.lineBreak();

					if (pdfVersion.v >= PDFParams.Version.V_1_7.v && att.description() != null) {
						this.out.writeName("UF");
						this.out.writeUTF16(att.description());
						this.out.lineBreak();
					}

					this.out.writeName("EF");
					this.out.startHash();
					this.out.writeName("F");
					this.out.writeObjectRef(spec.ref());
					this.out.endHash();

					this.out.endHash();
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
	 * Returns the next unique fragment sequence number.
	 *
	 * @return monotonically increasing sequence number
	 */
	protected int nextId() {
		return this.sequence++;
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

	/**
	 * Allocates the next PDF object reference for an Optional Content Group (OCG)
	 * and registers it in the OCG list for the {@code OCProperties} catalog entry.
	 *
	 * @return the newly allocated object reference
	 */
	protected ObjectRef nextOCG() {
		final var ocgRef = this.xref.nextObjectRef();
		if (this.ocgs == null) {
			this.ocgs = new ArrayList<>();
		}
		this.ocgs.add(ocgRef);
		return ocgRef;
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

		objectsFlow.writeName("Group");
		objectsFlow.startHash();
		objectsFlow.writeName("Type");
		objectsFlow.writeName("Group");
		objectsFlow.writeName("S");
		objectsFlow.writeName("Transparency");
		objectsFlow.endHash();

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

	public OutputStream addAttachment(final String filename, final Attachment attachment) throws IOException {
		if (attachment.description() == null && filename == null) {
			throw new NullPointerException("Both description and filename cannot be null.");
		}
		if (this.params.version().v < PDFParams.Version.V_1_4.v) {
			throw new UnsupportedOperationException("File attachment requires PDF 1.4 or later.");
		}
		if (this.params.version() == PDFParams.Version.V_PDFA1B) {
			throw new UnsupportedOperationException("File attachment cannot be used in PDF/A.");
		}
		if (this.params.version() == PDFParams.Version.V_PDFX1A) {
			throw new UnsupportedOperationException("File attachment cannot be used in PDF/X.");
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

		if (attachment.mimeType() != null) {
			objectsFlow.writeName("Subtype");
			objectsFlow.writeName(attachment.mimeType());
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
			final var zone = TimeZone.getDefault();
			var create = info.getCreationDate();
			if (create == -1L) {
				create = System.currentTimeMillis();
			}
			var modify = info.getModDate();

			final var infoRef = this.xref.nextObjectRef();
			this.objectsFlow.startObject(infoRef);
			this.objectsFlow.startHash();

			if (this.params.version() == PDFParams.Version.V_PDFX1A) {
				if (title == null || title.isEmpty()) {
					title = "Untitled";
				}
				this.objectsFlow.writeName("GTS_PDFXVersion");
				this.objectsFlow.writeText("PDF/X-1a:2003");
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

			if (modify == -1L) {
				modify = create;
			}
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

			// XML Metadata
			if (this.xmpmetaFlow != null) {
				XMPMetadataWriter.write(this.xmpmetaFlow, this.params.version(), author, creator, producer, title,
						keywords, create, modify);
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

			// OCGs
			this.writeOCProperties();

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

			// Resources
			this.fonts.close();
			if (this.pageResourceFlow != null) {
				this.pageResourceFlow.close();
			}
			this.objectsFlow.close();

			if (this.params.linearized()) {
				new LinearizedPDFAssembler(this, this.linDictRef, this.linDictFlow, this.rootPageRef, this.fileid)
						.assemble(infoRef);
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

		this.objectsFlow.startObject(ref);
		this.objectsFlow.startHash();
		this.objectsFlow.writeName("OCGs");
		this.objectsFlow.startArray();
		for (final var ocgRef : this.ocgs) {
			this.objectsFlow.writeObjectRef(ocgRef);
		}
		this.objectsFlow.endArray();

		this.objectsFlow.writeName("D");
		this.objectsFlow.startHash();
		this.objectsFlow.writeName("ON");
		this.objectsFlow.startArray();
		for (final var ocgRef : this.ocgs) {
			this.objectsFlow.writeObjectRef(ocgRef);
		}
		this.objectsFlow.endArray();
		this.objectsFlow.writeName("AS");
		this.objectsFlow.startArray();

		this.objectsFlow.startHash();
		this.objectsFlow.writeName("Event");
		this.objectsFlow.writeName("View");
		this.objectsFlow.writeName("OCGs");
		this.objectsFlow.startArray();
		for (final var ocgRef : this.ocgs) {
			this.objectsFlow.writeObjectRef(ocgRef);
		}
		this.objectsFlow.endArray();
		this.objectsFlow.writeName("Category");
		this.objectsFlow.startArray();
		this.objectsFlow.writeName("View");
		this.objectsFlow.endArray();
		this.objectsFlow.endHash();

		this.objectsFlow.startHash();
		this.objectsFlow.writeName("Event");
		this.objectsFlow.writeName("Print");
		this.objectsFlow.writeName("OCGs");
		this.objectsFlow.startArray();
		for (final var ocgRef : this.ocgs) {
			this.objectsFlow.writeObjectRef(ocgRef);
		}
		this.objectsFlow.endArray();
		this.objectsFlow.writeName("Category");
		this.objectsFlow.startArray();
		this.objectsFlow.writeName("Print");
		this.objectsFlow.endArray();
		this.objectsFlow.endHash();

		this.objectsFlow.endArray();
		this.objectsFlow.endHash();

		this.objectsFlow.endHash();
		this.objectsFlow.endObject();
	}
}
