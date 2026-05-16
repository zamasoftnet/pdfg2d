package net.zamasoft.pdfg2d.pdf.impl;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.helpers.AttributesImpl;

import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontStore;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.io.FragmentedOutput;
import net.zamasoft.pdfg2d.io.util.FragmentOutputAdapter;
import net.zamasoft.pdfg2d.io.util.PositionTrackingOutput;
import net.zamasoft.pdfg2d.pdf.Attachment;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.PDFNamedGraphicsOutput;
import net.zamasoft.pdfg2d.pdf.PDFOutput;
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
import net.zamasoft.pdfg2d.resolver.Source;

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

	protected static final Random RND = new Random();

	protected static final int BUFFER_SIZE = 8192;

	private static final byte[] HEADER = { '%', 'P', 'D', 'F', '-' };

	private static final byte[] PDF12 = { '1', '.', '2' };

	private static final byte[] PDF13 = { '1', '.', '3' };

	private static final byte[] PDF14 = { '1', '.', '4' };

	private static final byte[] PDF15 = { '1', '.', '5' };

	private static final byte[] PDF16 = { '1', '.', '6' };

	private static final byte[] PDF17 = { '1', '.', '7' };

	private static final byte[] XMP_PADDING;

	static {
		XMP_PADDING = new byte[80];
		java.util.Arrays.fill(XMP_PADDING, (byte) ' ');
		XMP_PADDING[79] = '\n';
	}

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
		// Adjust Y position for tiling
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
				this.xmpmetaFlow.startHash();

				this.xmpmetaFlow.writeName("Type");
				this.xmpmetaFlow.writeName("Metadata");
				this.xmpmetaFlow.lineBreak();

				this.xmpmetaFlow.writeName("Subtype");
				this.xmpmetaFlow.writeName("XML");
				this.xmpmetaFlow.lineBreak();

				try (final var xout = this.xmpmetaFlow.startStreamFromHash(PDFFragmentOutput.Mode.RAW)) {
					xout.write("<?xpacket begin='".getBytes(java.nio.charset.StandardCharsets.UTF_8));
					xout.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
					xout.write("' id='W5M0MpCehiHzreSzNTczkc9d'?>\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
					final var handler = ((SAXTransformerFactory) SAXTransformerFactory.newInstance())
							.newTransformerHandler();
					handler.setResult(new StreamResult(xout));
					final var t = handler.getTransformer();
					t.setOutputProperty(OutputKeys.METHOD, "xml");
					t.setOutputProperty(OutputKeys.INDENT, "yes");
					t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
					t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

					final var attsi = new AttributesImpl();
					final var xURI = "adobe:ns:meta/";
					final var rdfURI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
					final var pdfaidURI = "http://www.aiim.org/pdfa/ns/id/";
					final var pdfURI = "http://ns.adobe.com/pdf/1.3/";
					final var dcURI = "http://purl.org/dc/elements/1.1/";
					final var xmpURI = "http://ns.adobe.com/xap/1.0/";

					handler.startDocument();
					attsi.addAttribute("", "x", "xmlns:x", "CDATA", xURI);

					handler.startElement(xURI, "xmpmeta", "x:xmpmeta", attsi);
					attsi.clear();
					attsi.addAttribute("", "rdf", "xmlns:rdf", "CDATA", rdfURI);
					handler.startElement(rdfURI, "RDF", "rdf:RDF", attsi);
					attsi.clear();

					// PDF/A ID
					if (this.params.version() == PDFParams.Version.V_PDFA1B) {
						attsi.addAttribute("", "pdfaid", "xmlns:pdfaid", "CDATA", pdfaidURI);
						attsi.addAttribute(rdfURI, "about", "rdf:about", "CDATA", "");
						handler.startElement(rdfURI, "Description", "rdf:Description", attsi);
						attsi.clear();
						handler.startElement(pdfaidURI, "part", "pdfaid:part", attsi);
						handler.characters("1".toCharArray(), 0, 1);
						handler.endElement(pdfaidURI, "part", "pdfaid:part");
						handler.startElement(pdfaidURI, "conformance", "pdfaid:conformance", attsi);
						handler.characters("A".toCharArray(), 0, 1);
						handler.endElement(pdfaidURI, "conformance", "pdfaid:conformance");
						handler.endElement(rdfURI, "Description", "rdf:Description");
					}

					// PDF
					attsi.addAttribute("", "pdf", "xmlns:pdf", "CDATA", pdfURI);
					attsi.addAttribute(rdfURI, "about", "rdf:about", "CDATA", "");
					handler.startElement(rdfURI, "Description", "rdf:Description", attsi);
					attsi.clear();
					if (keywords != null) {
						handler.startElement(pdfURI, "Keywords", "pdf:Keywords", attsi);
						handler.characters(keywords.toCharArray(), 0, keywords.length());
						handler.endElement(pdfURI, "Keywords", "pdf:Keywords");
					}
					if (producer != null) {
						handler.startElement(pdfURI, "Producer", "pdf:Producer", attsi);
						handler.characters(producer.toCharArray(), 0, producer.length());
						handler.endElement(pdfURI, "Producer", "pdf:Producer");
					}
					handler.endElement(rdfURI, "Description", "rdf:Description");

					// DC
					attsi.addAttribute(rdfURI, "about", "rdf:about", "CDATA", "");
					attsi.addAttribute("", "dc", "xmlns:dc", "CDATA", dcURI);
					handler.startElement(rdfURI, "Description", "rdf:Description", attsi);
					attsi.clear();

					final String format = "application/pdf";
					handler.startElement(dcURI, "format", "dc:format", attsi);
					handler.characters(format.toCharArray(), 0, format.length());
					handler.endElement(dcURI, "format", "dc:format");

					if (title != null) {
						handler.startElement(dcURI, "title", "dc:title", attsi);
						handler.startElement(rdfURI, "Alt", "rdf:Alt", attsi);
						attsi.addAttribute("", "lang", "xml:lang", "CDATA", "x-default");
						handler.startElement(rdfURI, "li", "rdf:li", attsi);
						attsi.clear();
						handler.characters(title.toCharArray(), 0, title.length());
						handler.endElement(rdfURI, "li", "rdf:li");
						handler.endElement(rdfURI, "Alt", "rdf:Alt");
						handler.endElement(dcURI, "title", "dc:title");
					}

					if (author != null) {
						handler.startElement(dcURI, "creator", "dc:creator", attsi);
						handler.startElement(rdfURI, "Seq", "rdf:Seq", attsi);
						handler.startElement(rdfURI, "li", "rdf:li", attsi);
						handler.characters(author.toCharArray(), 0, author.length());
						handler.endElement(rdfURI, "li", "rdf:li");
						handler.endElement(rdfURI, "Seq", "rdf:Seq");
						handler.endElement(dcURI, "creator", "dc:creator");
					}
					attsi.clear();
					handler.endElement(rdfURI, "Description", "rdf:Description");

					// XMP
					attsi.addAttribute("", "xmp", "xmlns:xmp", "CDATA", xmpURI);
					attsi.addAttribute(rdfURI, "about", "rdf:about", "CDATA", "");
					handler.startElement(rdfURI, "Description", "rdf:Description", attsi);
					attsi.clear();
					if (creator != null) {
						handler.startElement(xmpURI, "CreatorTool", "xmp:CreatorTool", attsi);
						handler.characters(creator.toCharArray(), 0, creator.length());
						handler.endElement(xmpURI, "CreatorTool", "xmp:CreatorTool");
					}
					final var dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
					handler.startElement(xmpURI, "CreateDate", "xmp:CreateDate", attsi);
					final var createStr = dateFormat.format(new Date(create));
					handler.characters(createStr.toCharArray(), 0, createStr.length());
					handler.endElement(xmpURI, "CreateDate", "xmp:CreateDate");
					if (modify != -1L) {
						handler.startElement(xmpURI, "ModifyDate", "xmp:ModifyDate", attsi);
						final var modifyStr = dateFormat.format(new Date(modify));
						handler.characters(modifyStr.toCharArray(), 0, modifyStr.length());
						handler.endElement(xmpURI, "ModifyDate", "xmp:ModifyDate");
					}
					handler.endElement(rdfURI, "Description", "rdf:Description");

					handler.endElement(rdfURI, "RDF", "rdf:RDF");
					handler.endElement(xURI, "xmpmeta", "x:xmpmeta");

					handler.endDocument();
					// XMP (p33) 2-4KB padding
					for (var i = 0; i < 26; ++i) {
						xout.write(XMP_PADDING);
					}
					xout.write("<?xpacket end='w'?>\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				this.xmpmetaFlow.endObject();
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
			if (this.ocgs != null) {
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

			// ViewerPreferences
			final ViewerPreferences vp = this.params.viewerPreferences();
			if (vp != null) {
				this.catalogFlow.writeName("ViewerPreferences");
				this.catalogFlow.startHash();

				if (vp.isHideToolbar()) {
					this.catalogFlow.writeName("HideToolbar");
					this.catalogFlow.writeBoolean(true);
					this.catalogFlow.lineBreak();
				}

				if (vp.isHideMenubar()) {
					this.catalogFlow.writeName("HideMenubar");
					this.catalogFlow.writeBoolean(true);
					this.catalogFlow.lineBreak();
				}

				if (vp.isHideWindowUI()) {
					this.catalogFlow.writeName("HideWindowUI");
					this.catalogFlow.writeBoolean(true);
					this.catalogFlow.lineBreak();
				}

				if (vp.isFitWindow()) {
					this.catalogFlow.writeName("FitWindow");
					this.catalogFlow.writeBoolean(true);
					this.catalogFlow.lineBreak();
				}

				if (vp.isCenterWindow()) {
					this.catalogFlow.writeName("CenterWindow");
					this.catalogFlow.writeBoolean(true);
					this.catalogFlow.lineBreak();
				}

				if (vp.isDisplayDocTitle()) {
					if (this.params.version().v < PDFParams.Version.V_1_4.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference DisplayDocTitle requires PDF 1.4 or later.");
					}
					this.catalogFlow.writeName("DisplayDocTitle");
					this.catalogFlow.writeBoolean(true);
					this.catalogFlow.lineBreak();
				}

				if (vp.getNonFullScreenPageMode() != ViewerPreferences.NonFullScreenPageMode.NONE) {
					this.catalogFlow.writeName("NonFullScreenPageMode");
					switch (vp.getNonFullScreenPageMode()) {
						case OUTLINES -> this.catalogFlow.writeName("UseOutlines");
						case THUMBS -> this.catalogFlow.writeName("UseThumbs");
						case OC -> this.catalogFlow.writeName("UseOC");
						case NONE -> this.catalogFlow.writeName("UseNone");
					}
					this.catalogFlow.lineBreak();
				}

				if (vp.getDirection() != ViewerPreferences.Direction.L2R) {
					if (this.params.version().v < PDFParams.Version.V_1_3.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference Direction requires PDF 1.3 or later.");
					}
					this.catalogFlow.writeName("Direction");
					if (vp.getDirection() == ViewerPreferences.Direction.R2L) {
						this.catalogFlow.writeName("R2L");
					} else {
						this.catalogFlow.writeName("L2R");
					}
					this.catalogFlow.lineBreak();
				}

				if (vp.getViewArea() != ViewerPreferences.AreaBox.CROP) {
					if (this.params.version().v < PDFParams.Version.V_1_4.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference ViewArea requires PDF 1.4 or later.");
					}
					this.catalogFlow.writeName("ViewArea");
					this.writeArea(vp.getViewArea());
					this.catalogFlow.lineBreak();
				}

				if (vp.getViewClip() != ViewerPreferences.AreaBox.CROP) {
					if (this.params.version().v < PDFParams.Version.V_1_4.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference ViewClip requires PDF 1.4 or later.");
					}
					this.catalogFlow.writeName("ViewClip");
					this.writeArea(vp.getViewClip());
					this.catalogFlow.lineBreak();
				}

				if (vp.getPrintArea() != ViewerPreferences.AreaBox.CROP) {
					if (this.params.version().v < PDFParams.Version.V_1_4.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference PrintArea requires PDF 1.4 or later.");
					}
					this.catalogFlow.writeName("PrintArea");
					this.writeArea(vp.getPrintArea());
					this.catalogFlow.lineBreak();
				}

				if (vp.getPrintClip() != ViewerPreferences.AreaBox.CROP) {
					if (this.params.version().v < PDFParams.Version.V_1_4.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference PrintClip requires PDF 1.4 or later.");
					}
					this.catalogFlow.writeName("PrintClip");
					this.writeArea(vp.getPrintClip());
					this.catalogFlow.lineBreak();
				}

				if (vp.getPrintScaling() != ViewerPreferences.PrintScaling.APP_DEFAULT) {
					if (this.params.version().v < PDFParams.Version.V_1_6.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference PrintScaling requires PDF 1.6 or later.");
					}
					this.catalogFlow.writeName("PrintScaling");
					this.catalogFlow.writeName(
							vp.getPrintScaling() == ViewerPreferences.PrintScaling.NONE ? "None" : "AppDefault");
					this.catalogFlow.lineBreak();
				}

				if (vp.getDuplex() != ViewerPreferences.Duplex.NONE) {
					if (this.params.version().v < PDFParams.Version.V_1_7.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference Duplex requires PDF 1.7 or later.");
					}
					this.catalogFlow.writeName("Duplex");
					switch (vp.getDuplex()) {
						case SIMPLEX -> this.catalogFlow.writeName("Simplex");
						case FLIP_SHORT_EDGE -> this.catalogFlow.writeName("DuplexFlipShortEdge");
						case FLIP_LONG_EDGE -> this.catalogFlow.writeName("DuplexFlipLongEdge");
						default -> {
						}
					}
					this.catalogFlow.lineBreak();
				}

				if (vp.getPickTrayByPDFSize()) {
					if (this.params.version().v < PDFParams.Version.V_1_7.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference PickTrayByPDFSize requires PDF 1.7 or later.");
					}
					this.catalogFlow.writeName("PickTrayByPDFSize");
					this.catalogFlow.writeBoolean(true);
					this.catalogFlow.lineBreak();
				}

				final var printPageRange = vp.getPrintPageRange();
				if (printPageRange != null) {
					if (this.params.version().v < PDFParams.Version.V_1_7.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference PrintPageRange requires PDF 1.7 or later.");
					}
					this.catalogFlow.writeName("PrintPageRange");
					this.catalogFlow.startArray();
					for (final var range : printPageRange) {
						this.catalogFlow.writeInt(range);
					}
					this.catalogFlow.endArray();
					this.catalogFlow.lineBreak();
				}

				final int numCopies = vp.getNumCopies();
				if (numCopies > 0) {
					if (this.params.version().v < PDFParams.Version.V_1_7.v) {
						throw new UnsupportedOperationException(
								"ViewerPreference NumCopies requires PDF 1.7 or later.");
					}
					this.catalogFlow.writeName("NumCopies");
					this.catalogFlow.writeInt(numCopies);
					this.catalogFlow.lineBreak();
				}

				this.catalogFlow.endHash();

				this.catalogFlow.lineBreak();
			}

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
				this.closeLinearized(infoRef);
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

	private static final byte[] PLACEHOLDER = "0000000000".getBytes();

	private void writePlaceholder(PDFFragmentOutputImpl flow) throws IOException {
		flow.write(PLACEHOLDER);
	}

	private byte[] format(long value) {
		String s = String.valueOf(value);
		if (s.length() > 10) {
			s = s.substring(s.length() - 10);
		} else {
			while (s.length() < 10) {
				s = " " + s;
			}
		}
		return s.getBytes();
	}

	private void closeLinearized(ObjectRef infoRef) throws IOException {
		this.linDictFlow.flush();
		this.mainFlow.flush();

		if (!(this.builder instanceof net.zamasoft.pdfg2d.io.impl.AbstractTempFileOutput tempBuilder)) {
			throw new IOException("Linearized output requires AbstractTempFileOutput-backed storage.");
		}

		final var posInfo = this.builder.getPositionInfo();
		final var snapshot = tempBuilder.snapshotBytes();
		final var hintRef = this.xref.nextObjectRef();
		final var allObjects = this.xref.getObjects();
		final var linearizedBytes = this.assembleLinearizedPdf(snapshot, posInfo, allObjects, infoRef, hintRef);
		tempBuilder.replaceBytes(linearizedBytes);
	}

	private byte[] assembleLinearizedPdf(
			final byte[] snapshot,
			final net.zamasoft.pdfg2d.io.FragmentedOutput.PositionInfo posInfo,
			final List<ObjectRef> allObjects,
			final ObjectRef infoRef,
			final ObjectRef hintRef) throws IOException {
		final var sourceObjects = new ArrayList<ObjectRef>();
		long firstObjectOffset = Long.MAX_VALUE;
		for (final var ref : allObjects) {
			if (ref == this.linDictRef || ref == hintRef) {
				continue;
			}
			final var impl = (ObjectRefImpl) ref;
			sourceObjects.add(ref);
			firstObjectOffset = Math.min(firstObjectOffset, impl.getPosition(posInfo));
		}
		sourceObjects.sort((left, right) -> Long.compare(
				((ObjectRefImpl) left).getPosition(posInfo),
				((ObjectRefImpl) right).getPosition(posInfo)));

		final var headerBytes = Arrays.copyOf(snapshot, (int) firstObjectOffset);
		final var objectBytes = new HashMap<ObjectRef, byte[]>();
		for (int i = 0; i < sourceObjects.size(); ++i) {
			final var ref = sourceObjects.get(i);
			final int start = (int) ((ObjectRefImpl) ref).getPosition(posInfo);
			final int end;
			if (i + 1 < sourceObjects.size()) {
				end = (int) ((ObjectRefImpl) sourceObjects.get(i + 1)).getPosition(posInfo);
			} else {
				end = snapshot.length;
			}
			objectBytes.put(ref, Arrays.copyOfRange(snapshot, start, end));
		}

		final var reachableByPage = new HashMap<PDFPageOutputImpl, LinkedHashSet<ObjectRef>>();
		final var usageCount = new HashMap<ObjectRef, Integer>();
		for (final var page : this.pageOutputs) {
			final var reachable = new LinkedHashSet<ObjectRef>();
			this.collectLinearizedPageObjects(page.getPageRef(), reachable, new LinkedHashSet<>());
			reachableByPage.put(page, reachable);
			for (final var ref : reachable) {
				usageCount.merge(ref, 1, Integer::sum);
			}
		}

		final var sharedObjects = new ArrayList<ObjectRef>();
		for (final var ref : sourceObjects) {
			if (usageCount.getOrDefault(ref, 0) > 1) {
				sharedObjects.add(ref);
			}
		}
		final var sharedSet = new LinkedHashSet<>(sharedObjects);

		final var firstPage = this.pageOutputs.get(0);
		final var firstPageSectionObjects = new ArrayList<ObjectRef>();
		for (final var ref : sourceObjects) {
			if (reachableByPage.get(firstPage).contains(ref)) {
				firstPageSectionObjects.add(ref);
			}
		}
		final var firstPageSectionSet = new LinkedHashSet<>(firstPageSectionObjects);
		final var sharedSectionObjects = new ArrayList<ObjectRef>();
		for (final var ref : sharedObjects) {
			if (!firstPageSectionSet.contains(ref)) {
				sharedSectionObjects.add(ref);
			}
		}

		final var bodyOrder = new ArrayList<ObjectRef>(firstPageSectionObjects);
		for (final var ref : sourceObjects) {
			if (!firstPageSectionSet.contains(ref)) {
				bodyOrder.add(ref);
			}
		}

		final var bodyOffsets = new HashMap<ObjectRef, Integer>();
		int bodyLength = 0;
		for (final var ref : bodyOrder) {
			bodyOffsets.put(ref, bodyLength);
			bodyLength += objectBytes.get(ref).length;
		}

		final var bodyIndex = new HashMap<ObjectRef, Integer>();
		for (int i = 0; i < bodyOrder.size(); ++i) {
			bodyIndex.put(bodyOrder.get(i), i);
		}
		final var sharedHintObjects = new ArrayList<ObjectRef>(firstPageSectionObjects);
		sharedHintObjects.addAll(sharedSectionObjects);
		final var sharedObjectIndex = new HashMap<ObjectRef, Integer>();
		for (int i = 0; i < sharedHintObjects.size(); ++i) {
			sharedObjectIndex.put(sharedHintObjects.get(i), i);
		}

		final var pageEntries = new ArrayList<HintTableBuilder.PageEntry>();
		long firstPageEndInBody = 0;
		for (int pageIndex = 0; pageIndex < this.pageOutputs.size(); ++pageIndex) {
			final var page = this.pageOutputs.get(pageIndex);
			final boolean isFirstPage = page == firstPage;
			final boolean isLastPage = pageIndex == this.pageOutputs.size() - 1;
			final var sectionObjects = new ArrayList<ObjectRef>();
			if (isFirstPage) {
				sectionObjects.addAll(firstPageSectionObjects);
			} else {
				for (final var ref : sourceObjects) {
					if (reachableByPage.get(page).contains(ref) && !sharedSet.contains(ref)) {
						sectionObjects.add(ref);
					}
				}
			}

			final long sectionStart = this.computeLinearizedPageStart(page, bodyOffsets);
			final long sectionEnd = this.computeLinearizedPageEndFromBody(
					isLastPage, sectionObjects, bodyOrder, bodyIndex, bodyOffsets, objectBytes);
			final var entry = new HintTableBuilder.PageEntry();
			entry.objectsPerPage = sectionObjects.size();
			entry.pageLength = sectionEnd - sectionStart;
			entry.contentStreamStart = 0;
			entry.contentStreamLength = entry.pageLength;
			if (!isFirstPage) {
				for (final var ref : sharedHintObjects) {
					if (usageCount.getOrDefault(ref, 0) > 1 && reachableByPage.get(page).contains(ref)) {
						entry.sharedObjectIndices.add(sharedObjectIndex.get(ref));
					}
				}
			}
			pageEntries.add(entry);
			if (isFirstPage) {
				firstPageEndInBody = sectionEnd;
			}
		}

		final int xrefSize = allObjects.size() + 1;
		final int linDictObjectNumber = this.linDictRef.objectNumber();
		final int hintObjectNumber = hintRef.objectNumber();

		byte[] primaryXrefBytes = new byte[0];
		byte[] hintCompressedBytes = new byte[0];
		byte[] hintObjectBytes = renderLinearizedHintObject(hintRef, 0, hintCompressedBytes);
		int hintSharedTableOffset = 0;

		for (int i = 0; i < 8; ++i) {
			final int linDictLength = renderLinearizedDictionaryBytes(
					0, 0, 0, firstPage.getPageRef().objectNumber(), 0, this.pageOutputs.size(), 0).length;
			final int primaryXrefOffset = headerBytes.length + linDictLength;
			final int hintObjectOffset = primaryXrefOffset + primaryXrefBytes.length;
			final int bodyStartOffset = hintObjectOffset + hintObjectBytes.length;
			final int mainXrefOffset = bodyStartOffset + bodyLength;

			final var offsets = new HashMap<ObjectRef, Long>();
			offsets.put(this.linDictRef, (long) headerBytes.length);
			offsets.put(hintRef, (long) hintObjectOffset);
			for (final var ref : bodyOrder) {
				offsets.put(ref, (long) bodyStartOffset + bodyOffsets.get(ref));
			}

			final var hintBuild = this.buildLinearizedHintBytes(
					hintObjectOffset,
					pageEntries,
					firstPageSectionObjects,
					sharedSectionObjects,
					bodyOffsets,
					objectBytes);
			hintCompressedBytes = hintBuild.compressedBytes;
			hintSharedTableOffset = hintBuild.sharedObjectTableOffset;
			final var newHintObjectBytes = renderLinearizedHintObject(hintRef, hintSharedTableOffset, hintCompressedBytes);
			final var newPrimaryXrefBytes = this.renderLinearizedXrefBytes(
					allObjects, offsets, infoRef, mainXrefOffset, false, -1);
			if (newHintObjectBytes.length == hintObjectBytes.length
					&& newPrimaryXrefBytes.length == primaryXrefBytes.length) {
				hintObjectBytes = newHintObjectBytes;
				primaryXrefBytes = newPrimaryXrefBytes;
				break;
			}
			hintObjectBytes = newHintObjectBytes;
			primaryXrefBytes = newPrimaryXrefBytes;
		}

		final int linDictLength = renderLinearizedDictionaryBytes(
				0, 0, 0, firstPage.getPageRef().objectNumber(), 0, this.pageOutputs.size(), 0).length;
		final var offsets = new HashMap<ObjectRef, Long>();
		for (int i = 0; i < 8; ++i) {
			final int primaryXrefOffset = headerBytes.length + linDictLength;
			final int hintObjectOffset = primaryXrefOffset + primaryXrefBytes.length;
			final int bodyStartOffset = hintObjectOffset + hintObjectBytes.length;
			final int mainXrefOffset = bodyStartOffset + bodyLength;

			offsets.clear();
			offsets.put(this.linDictRef, (long) headerBytes.length);
			offsets.put(hintRef, (long) hintObjectOffset);
			for (final var ref : bodyOrder) {
				offsets.put(ref, (long) bodyStartOffset + bodyOffsets.get(ref));
			}

			final var nextHintBuild = this.buildLinearizedHintBytes(
					hintObjectOffset,
					pageEntries,
					firstPageSectionObjects,
					sharedSectionObjects,
					bodyOffsets,
					objectBytes);
			final var nextHintBytes = renderLinearizedHintObject(
					hintRef, nextHintBuild.sharedObjectTableOffset, nextHintBuild.compressedBytes);
			final var nextPrimaryXref = this.renderLinearizedXrefBytes(
					allObjects, offsets, infoRef, mainXrefOffset, false, -1);
			if (nextHintBytes.length == hintObjectBytes.length && nextPrimaryXref.length == primaryXrefBytes.length) {
				hintCompressedBytes = nextHintBuild.compressedBytes;
				hintSharedTableOffset = nextHintBuild.sharedObjectTableOffset;
				hintObjectBytes = nextHintBytes;
				primaryXrefBytes = nextPrimaryXref;
				break;
			}
			hintCompressedBytes = nextHintBuild.compressedBytes;
			hintSharedTableOffset = nextHintBuild.sharedObjectTableOffset;
			hintObjectBytes = nextHintBytes;
			primaryXrefBytes = nextPrimaryXref;
		}

		final int finalBodyStartOffset = headerBytes.length + linDictLength + primaryXrefBytes.length + hintObjectBytes.length;
		offsets.clear();
		offsets.put(this.linDictRef, (long) headerBytes.length);
		offsets.put(hintRef, (long) (headerBytes.length + linDictLength + primaryXrefBytes.length));
		for (final var ref : bodyOrder) {
			offsets.put(ref, (long) finalBodyStartOffset + bodyOffsets.get(ref));
		}

		final int finalMainXrefOffset = finalBodyStartOffset + bodyLength;
		primaryXrefBytes = this.renderLinearizedXrefBytes(allObjects, offsets, infoRef, finalMainXrefOffset, false, -1);
		final var mainXrefBytes = this.renderLinearizedXrefBytes(allObjects, offsets, infoRef, -1, true, finalMainXrefOffset);
		final int finalFileLength = finalMainXrefOffset + mainXrefBytes.length;
		final int firstPageEnd = finalBodyStartOffset + (int) firstPageEndInBody;
		final int tOffset = (int) linearizedXrefFirstItemOffset(finalMainXrefOffset, xrefSize);
		final var linDictBytes = renderLinearizedDictionaryBytes(
				finalFileLength,
				headerBytes.length + linDictLength + primaryXrefBytes.length,
				hintObjectBytes.length,
				firstPage.getPageRef().objectNumber(),
				firstPageEnd,
				this.pageOutputs.size(),
				tOffset);

		final var out = new ByteArrayOutputStream(finalFileLength);
		out.write(headerBytes);
		out.write(linDictBytes);
		out.write(primaryXrefBytes);
		out.write(hintObjectBytes);
		for (final var ref : bodyOrder) {
			out.write(objectBytes.get(ref));
		}
		out.write(mainXrefBytes);
		return out.toByteArray();
	}

	private long computeLinearizedPageStart(final PDFPageOutputImpl page, final Map<ObjectRef, Integer> bodyOffsets) {
		long start = bodyOffsets.get(page.getPageRef());
		start = Math.min(start, bodyOffsets.get(page.getContentsRef()));
		for (final var annotRef : page.getAnnotRefs()) {
			start = Math.min(start, bodyOffsets.get(annotRef));
		}
		return start;
	}

	private long computeLinearizedPageEndFromBody(
			final boolean isLastPage,
			final List<ObjectRef> sectionObjects,
			final List<ObjectRef> bodyOrder,
			final Map<ObjectRef, Integer> bodyIndex,
			final Map<ObjectRef, Integer> bodyOffsets,
			final Map<ObjectRef, byte[]> objectBytes) {
		long end = 0;
		int lastIndex = -1;
		for (final var ref : sectionObjects) {
			end = Math.max(end, bodyOffsets.get(ref) + objectBytes.get(ref).length);
			lastIndex = Math.max(lastIndex, bodyIndex.get(ref));
		}
		if (!isLastPage && lastIndex >= 0 && lastIndex + 1 < bodyOrder.size()) {
			return bodyOffsets.get(bodyOrder.get(lastIndex + 1));
		}
		return end;
	}

	private LinearizedHintBuild buildLinearizedHintBytes(
			final int firstPageLocation,
			final List<HintTableBuilder.PageEntry> pageEntries,
			final List<ObjectRef> firstPageSectionObjects,
			final List<ObjectRef> sharedSectionObjects,
			final Map<ObjectRef, Integer> bodyOffsets,
			final Map<ObjectRef, byte[]> objectBytes) throws IOException {
		final var hintBuilder = new HintTableBuilder();
		hintBuilder.setFirstPageLocation(firstPageLocation);
		for (final var ref : firstPageSectionObjects) {
			hintBuilder.addFirstPageObject(ref, firstPageLocation + bodyOffsets.get(ref), objectBytes.get(ref).length);
		}
		for (final var ref : sharedSectionObjects) {
			hintBuilder.addSharedObject(ref, firstPageLocation + bodyOffsets.get(ref), objectBytes.get(ref).length);
		}
		for (final var entry : pageEntries) {
			hintBuilder.addPage(entry);
		}
		final var rawHints = new ByteArrayOutputStream();
		hintBuilder.build(rawHints);
		final var compressed = new ByteArrayOutputStream();
		try (final var deflater = new java.util.zip.DeflaterOutputStream(compressed)) {
			deflater.write(rawHints.toByteArray());
		}
		return new LinearizedHintBuild(compressed.toByteArray(), hintBuilder.getSharedObjectTableOffset(), 0);
	}

	private byte[] renderLinearizedDictionaryBytes(
			final int fileLength,
			final int hintOffset,
			final int hintLength,
			final int firstPageObjectNumber,
			final int firstPageEnd,
			final int pageCount,
			final int mainXrefOffset) throws IOException {
		final var out = new ByteArrayOutputStream();
		try (final var pdf = new PDFOutput(out, "ISO-8859-1")) {
			pdf.writeInt(this.linDictRef.objectNumber());
			pdf.writeInt(this.linDictRef.generationNumber());
			pdf.writeOperator("obj");
			pdf.lineBreak();
			pdf.startHash();
			pdf.writeName("Linearized");
			pdf.writeReal(1.0);
			pdf.lineBreak();
			pdf.writeName("L");
			pdf.spaceBefore();
			writeFixedNumber(pdf, fileLength, 10);
			pdf.lineBreak();
			pdf.writeName("H");
			pdf.startArray();
			writeFixedNumber(pdf, hintOffset, 10);
			pdf.spaceBefore();
			writeFixedNumber(pdf, hintLength, 10);
			pdf.endArray();
			pdf.lineBreak();
			pdf.writeName("O");
			pdf.spaceBefore();
			writeFixedNumber(pdf, firstPageObjectNumber, 10);
			pdf.lineBreak();
			pdf.writeName("E");
			pdf.spaceBefore();
			writeFixedNumber(pdf, firstPageEnd, 10);
			pdf.lineBreak();
			pdf.writeName("N");
			pdf.spaceBefore();
			writeFixedNumber(pdf, pageCount, 10);
			pdf.lineBreak();
			pdf.writeName("T");
			pdf.spaceBefore();
			writeFixedNumber(pdf, mainXrefOffset, 10);
			pdf.lineBreak();
			pdf.endHash();
			pdf.lineBreak();
			pdf.writeOperator("endobj");
			pdf.lineBreak();
		}
		return out.toByteArray();
	}

	private byte[] renderLinearizedXrefBytes(
			final List<ObjectRef> allObjects,
			final Map<ObjectRef, Long> offsets,
			final ObjectRef infoRef,
			final long prevOffset,
			final boolean withStartxref,
			final long startxrefOffset) throws IOException {
		final var out = new ByteArrayOutputStream();
		try (final var pdf = new PDFOutput(out, "ISO-8859-1")) {
			pdf.writeOperator("xref");
			pdf.lineBreak();
			pdf.writeInt(0);
			pdf.writeInt(allObjects.size() + 1);
			pdf.lineBreak();
			writeLinearizedXrefEntry(pdf, 0, 65535, false);
			for (final var ref : allObjects) {
				writeLinearizedXrefEntry(pdf, offsets.get(ref), ref.generationNumber(), true);
			}
			pdf.writeOperator("trailer");
			pdf.lineBreak();
			pdf.startHash();
			pdf.writeName("Size");
			pdf.writeInt(allObjects.size() + 1);
			pdf.lineBreak();
			if (prevOffset >= 0) {
				pdf.writeName("Prev");
				pdf.writeInt((int) prevOffset);
				pdf.lineBreak();
			}
			pdf.writeName("Root");
			pdf.writeObjectRef(this.xref.getRootRef());
			pdf.lineBreak();
			if (infoRef != null) {
				pdf.writeName("Info");
				pdf.writeObjectRef(infoRef);
				pdf.lineBreak();
			}
			if (this.fileid != null) {
				pdf.writeName("ID");
				pdf.startArray();
				pdf.writeBytes8(this.fileid[0], 0, this.fileid[0].length);
				pdf.writeBytes8(this.fileid[1], 0, this.fileid[1].length);
				pdf.endArray();
				pdf.lineBreak();
			}
			if (this.encryption != null) {
				pdf.writeName("Encrypt");
				pdf.writeObjectRef(this.encryption.getObjectRef());
				pdf.lineBreak();
			}
			pdf.endHash();
			if (withStartxref) {
				pdf.writeOperator("startxref");
				pdf.lineBreak();
				writeFixedNumber(pdf, startxrefOffset, 10);
				pdf.lineBreak();
				pdf.writeLine("%%EOF");
			}
		}
		return out.toByteArray();
	}

	private LinearizedHintBuild buildLinearizedHintData(
			final net.zamasoft.pdfg2d.io.FragmentedOutput.PositionInfo posInfo,
			final PDFPageOutputImpl firstPage,
			final List<SharedObjectGroup> sharedGroups,
			final Set<ObjectRef> sharedRootSet,
			final Map<ObjectRef, Integer> sharedIndexByRoot,
			final long shift) throws IOException {
		final var hintBuilder = new HintTableBuilder();
		final var layout = this.computeLinearizedLayout(posInfo, shift, firstPage);
		for (final var objectRef : layout.sharedObjects) {
			hintBuilder.addSharedObject(
					objectRef,
					((ObjectRefImpl) objectRef).getPosition(posInfo) + shift,
					((ObjectRefImpl) objectRef).getLength());
		}
		final long firstPageObjPos = ((ObjectRefImpl) firstPage.getPageRef()).getPosition(posInfo) + shift;
		hintBuilder.setFirstPageLocation(firstPageObjPos);
		long endOfFirstPage = layout.firstPageSectionEnd;
		for (PDFPageOutputImpl page : this.pageOutputs) {
			final var pageObjects = layout.pageObjects.get(page);
			final var entry = new HintTableBuilder.PageEntry();
			entry.objectsPerPage = pageObjects.sectionObjects.size();
			long start = ((ObjectRefImpl) page.getPageRef()).getPosition(posInfo) + shift;
			long cPos = ((ObjectRefImpl) page.getContentsRef()).getPosition(posInfo) + shift;
			long cLen = ((ObjectRefImpl) page.getContentsRef()).getLength();
			long s = Math.min(start, cPos);
			entry.contentStreamStart = cPos - s;
			entry.contentStreamLength = cLen;
			for (ObjectRef aRef : page.getAnnotRefs()) {
				long aPos = ((ObjectRefImpl) aRef).getPosition(posInfo) + shift;
				s = Math.min(s, aPos);
			}
			entry.pageLength = pageObjects.pageEnd - s;
			if (page == firstPage) {
				endOfFirstPage = pageObjects.pageEnd;
			} else {
				entry.sharedObjectIndices.addAll(pageObjects.sharedIndices);
			}
			hintBuilder.addPage(entry);
		}

		final ByteArrayOutputStream rawHints = new ByteArrayOutputStream();
		hintBuilder.build(rawHints);
		final ByteArrayOutputStream compHints = new ByteArrayOutputStream();
		try (java.util.zip.DeflaterOutputStream def = new java.util.zip.DeflaterOutputStream(compHints)) {
			def.write(rawHints.toByteArray());
		}
		return new LinearizedHintBuild(compHints.toByteArray(), hintBuilder.getSharedObjectTableOffset(), endOfFirstPage);
	}

	private LinearizedLayout computeLinearizedLayout(
			final net.zamasoft.pdfg2d.io.FragmentedOutput.PositionInfo posInfo,
			final long shift,
			final PDFPageOutputImpl firstPage) {
		final var orderedBodyObjects = new ArrayList<ObjectRef>();
		for (final var ref : this.xref.getObjects()) {
			if (ref == this.linDictRef) {
				continue;
			}
			final var impl = (ObjectRefImpl) ref;
			if (impl.getLength() <= 0) {
				continue;
			}
			orderedBodyObjects.add(ref);
		}
		orderedBodyObjects.sort((left, right) -> Long.compare(
				((ObjectRefImpl) left).getPosition(posInfo),
				((ObjectRefImpl) right).getPosition(posInfo)));

		final var bodyOrder = new HashMap<ObjectRef, Integer>();
		for (int i = 0; i < orderedBodyObjects.size(); ++i) {
			bodyOrder.put(orderedBodyObjects.get(i), i);
		}

		final var reachableByPage = new HashMap<PDFPageOutputImpl, LinkedHashSet<ObjectRef>>();
		final var usageCount = new HashMap<ObjectRef, Integer>();
		for (final var page : this.pageOutputs) {
			final var reachable = new LinkedHashSet<ObjectRef>();
			this.collectLinearizedPageObjects(page.getPageRef(), reachable, new LinkedHashSet<>());
			reachableByPage.put(page, reachable);
			for (final var ref : reachable) {
				usageCount.merge(ref, 1, Integer::sum);
			}
		}

		final var sharedObjects = new ArrayList<ObjectRef>();
		for (final var entry : usageCount.entrySet()) {
			if (entry.getValue() > 1) {
				sharedObjects.add(entry.getKey());
			}
		}
		sharedObjects.sort((left, right) -> Integer.compare(
				bodyOrder.getOrDefault(left, Integer.MAX_VALUE),
				bodyOrder.getOrDefault(right, Integer.MAX_VALUE)));

		final var sharedIndexByObject = new HashMap<ObjectRef, Integer>();
		for (int i = 0; i < sharedObjects.size(); ++i) {
			sharedIndexByObject.put(sharedObjects.get(i), i);
		}

		final var pageObjects = new HashMap<PDFPageOutputImpl, LinearizedPageObjects>();
		for (int pageIndex = 0; pageIndex < this.pageOutputs.size(); ++pageIndex) {
			final var page = this.pageOutputs.get(pageIndex);
			final var reachable = reachableByPage.get(page);
			final var sectionObjects = new ArrayList<ObjectRef>();
			final var sharedIndices = new ArrayList<Integer>();
			for (final var ref : reachable) {
				final boolean shared = sharedIndexByObject.containsKey(ref);
				if (page == firstPage || !shared) {
					sectionObjects.add(ref);
				} else {
					sharedIndices.add(sharedIndexByObject.get(ref));
				}
			}
			sectionObjects.sort((left, right) -> Integer.compare(
					bodyOrder.getOrDefault(left, Integer.MAX_VALUE),
					bodyOrder.getOrDefault(right, Integer.MAX_VALUE)));
			Collections.sort(sharedIndices);
			final long pageEnd = this.computeLinearizedPageEnd(
					page,
					pageIndex == this.pageOutputs.size() - 1,
					sectionObjects,
					orderedBodyObjects,
					bodyOrder,
					posInfo,
					shift);
			pageObjects.put(page, new LinearizedPageObjects(sectionObjects, sharedIndices, pageEnd));
		}

		return new LinearizedLayout(sharedObjects, pageObjects, pageObjects.get(firstPage).pageEnd);
	}

	private void collectLinearizedPageObjects(final ObjectRef current, final Set<ObjectRef> collected,
			final Set<ObjectRef> visited) {
		if (current == null || current == this.rootPageRef || !visited.add(current)) {
			return;
		}
		collected.add(current);
		for (final var dependency : this.xref.getDependencies(current)) {
			if (dependency == this.rootPageRef) {
				continue;
			}
			this.collectLinearizedPageObjects(dependency, collected, visited);
		}
	}

	private long computeLinearizedPageEnd(
			final PDFPageOutputImpl page,
			final boolean isLastPage,
			final List<ObjectRef> sectionObjects,
			final List<ObjectRef> orderedBodyObjects,
			final Map<ObjectRef, Integer> bodyOrder,
			final net.zamasoft.pdfg2d.io.FragmentedOutput.PositionInfo posInfo,
			final long shift) {
		long pageEnd = 0;
		int lastBodyIndex = -1;
		for (final var ref : sectionObjects) {
			final var impl = (ObjectRefImpl) ref;
			pageEnd = Math.max(pageEnd, impl.getPosition(posInfo) + shift + impl.getLength());
			lastBodyIndex = Math.max(lastBodyIndex, bodyOrder.getOrDefault(ref, -1));
		}
		if (!isLastPage && lastBodyIndex >= 0 && lastBodyIndex + 1 < orderedBodyObjects.size()) {
			final var nextObject = (ObjectRefImpl) orderedBodyObjects.get(lastBodyIndex + 1);
			pageEnd = nextObject.getPosition(posInfo) + shift;
		}
		return pageEnd;
	}

	private byte[] renderLinearizedPrimaryXref(
			final List<ObjectRef> allObjects,
			final net.zamasoft.pdfg2d.io.FragmentedOutput.PositionInfo posInfo,
			final ObjectRef infoRef,
			final ObjectRef hintRef,
			final long linDictOffset,
			final long hintObjectOffset,
			final long shift,
			final long prevOffset) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (final PDFOutput pdf = new PDFOutput(out, "ISO-8859-1")) {
			pdf.writeOperator("xref");
			pdf.lineBreak();
			pdf.writeInt(0);
			pdf.writeInt(allObjects.size() + 1);
			pdf.lineBreak();
			writeLinearizedXrefEntry(pdf, 0, 65535, false);
			for (final ObjectRef ref : allObjects) {
				final long offset;
				if (ref == this.linDictRef) {
					offset = linDictOffset;
				} else if (ref == hintRef) {
					offset = hintObjectOffset;
				} else {
					offset = ((ObjectRefImpl) ref).getPosition(posInfo) + shift;
				}
				writeLinearizedXrefEntry(pdf, offset, ref.generationNumber(), true);
			}
			pdf.writeOperator("trailer");
			pdf.lineBreak();
			pdf.startHash();
			pdf.writeName("Size");
			pdf.writeInt(allObjects.size() + 1);
			pdf.lineBreak();
			pdf.writeName("Prev");
			pdf.writeInt((int) prevOffset);
			pdf.lineBreak();
			pdf.writeName("Root");
			pdf.writeObjectRef(this.xref.getRootRef());
			pdf.lineBreak();
			if (infoRef != null) {
				pdf.writeName("Info");
				pdf.writeObjectRef(infoRef);
				pdf.lineBreak();
			}
			if (this.encryption != null) {
				pdf.writeName("Encrypt");
				pdf.writeObjectRef(this.encryption.getObjectRef());
				pdf.lineBreak();
			}
			pdf.endHash();
		}
		return out.toByteArray();
	}

	private static void writeLinearizedXrefEntry(final PDFOutput out, final long byteOffset, final int generationNum,
			final boolean inUse) throws IOException {
		writeFixedNumber(out, byteOffset, 10);
		out.write(' ');
		writeFixedNumber(out, generationNum, 5);
		out.write(' ');
		out.write(inUse ? 'n' : 'f');
		out.lineBreak();
	}

	private static void writeFixedNumber(final PDFOutput out, long value, final int width) throws IOException {
		final byte[] digits = new byte[width];
		for (int i = width - 1; i >= 0; --i) {
			digits[i] = (byte) ('0' + (value % 10));
			value /= 10;
		}
		out.write(digits);
	}

	private byte[] renderLinearizedHintObject(
			final ObjectRef hintRef,
			final int sharedObjectTableOffset,
			final byte[] hintBytesCompressed) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (final PDFOutput pdf = new PDFOutput(out, "ISO-8859-1")) {
			pdf.writeInt(hintRef.objectNumber());
			pdf.writeInt(hintRef.generationNumber());
			pdf.writeOperator("obj");
			pdf.lineBreak();
			pdf.startHash();
			pdf.writeName("Filter");
			pdf.writeName("FlateDecode");
			pdf.writeName("S");
			pdf.writeInt(sharedObjectTableOffset);
			pdf.writeName("Length");
			pdf.writeInt(hintBytesCompressed.length);
			pdf.lineBreak();
			pdf.endHash();
			pdf.writeOperator("stream");
			pdf.lineBreak();
			pdf.write(hintBytesCompressed);
			pdf.lineBreak();
			pdf.writeOperator("endstream");
			pdf.lineBreak();
			pdf.writeOperator("endobj");
			pdf.lineBreak();
		}
		return out.toByteArray();
	}

	private static long linearizedXrefFirstItemOffset(final long xrefOffset, final int objectCount) {
		return xrefOffset + ("xref\r\n0 " + objectCount + "\r\n").length();
	}

	private static final class LinearizedHintBuild {
		final byte[] compressedBytes;
		final int sharedObjectTableOffset;
		final long endOfFirstPage;

		LinearizedHintBuild(final byte[] compressedBytes, final int sharedObjectTableOffset, final long endOfFirstPage) {
			this.compressedBytes = compressedBytes;
			this.sharedObjectTableOffset = sharedObjectTableOffset;
			this.endOfFirstPage = endOfFirstPage;
		}
	}

	private static final class LinearizedLayout {
		final List<ObjectRef> sharedObjects;
		final Map<PDFPageOutputImpl, LinearizedPageObjects> pageObjects;
		final long firstPageSectionEnd;

		LinearizedLayout(final List<ObjectRef> sharedObjects,
				final Map<PDFPageOutputImpl, LinearizedPageObjects> pageObjects,
				final long firstPageSectionEnd) {
			this.sharedObjects = sharedObjects;
			this.pageObjects = pageObjects;
			this.firstPageSectionEnd = firstPageSectionEnd;
		}
	}

	private static final class LinearizedPageObjects {
		final List<ObjectRef> sectionObjects;
		final List<Integer> sharedIndices;
		final long pageEnd;

		LinearizedPageObjects(final List<ObjectRef> sectionObjects, final List<Integer> sharedIndices,
				final long pageEnd) {
			this.sectionObjects = sectionObjects;
			this.sharedIndices = sharedIndices;
			this.pageEnd = pageEnd;
		}
	}

	private static final class SharedObjectGroup {
		final ObjectRef root;
		final List<ObjectRef> members;
		final int length;

		SharedObjectGroup(final ObjectRef root, final List<ObjectRef> members, final int length) {
			this.root = root;
			this.members = members;
			this.length = length;
		}
	}

	private List<SharedObjectGroup> buildSharedObjectGroups(final List<ObjectRef> roots) {
		final var rootSet = new LinkedHashSet<>(roots);
		final var groups = new ArrayList<SharedObjectGroup>(roots.size());
		for (final var root : roots) {
			final var members = new ArrayList<ObjectRef>();
			this.collectSharedGroupMembers(root, root, rootSet, new LinkedHashSet<>(), members);
			var length = 0;
			for (final var member : members) {
				length += Math.max(0, ((ObjectRefImpl) member).getLength());
			}
			groups.add(new SharedObjectGroup(root, members, length));
		}
		return groups;
	}

	private void collectSharedGroupMembers(final ObjectRef current, final ObjectRef root, final Set<ObjectRef> rootSet,
			final Set<ObjectRef> visited, final List<ObjectRef> members) {
		if (!visited.add(current)) {
			return;
		}
		members.add(current);
		for (final var dependency : this.xref.getDependencies(current)) {
			if (dependency == root) {
				continue;
			}
			if (rootSet.contains(dependency)) {
				continue;
			}
			this.collectSharedGroupMembers(dependency, root, rootSet, visited, members);
		}
	}

	private List<Integer> sharedGroupIndicesForPage(final PDFPageOutputImpl page, final Set<ObjectRef> sharedRoots,
			final Map<ObjectRef, Integer> sharedIndexByRoot) {
		final var reachableRoots = new LinkedHashSet<ObjectRef>();
		this.collectReachableSharedRoots(page.getPageRef(), sharedRoots, new LinkedHashSet<>(), reachableRoots);
		reachableRoots.remove(page.getPageRef());
		final var indices = new ArrayList<Integer>(reachableRoots.size());
		for (final var root : reachableRoots) {
			final var index = sharedIndexByRoot.get(root);
			if (index != null) {
				indices.add(index);
			}
		}
		Collections.sort(indices);
		return indices;
	}

	private void collectReachableSharedRoots(final ObjectRef current, final Set<ObjectRef> sharedRoots,
			final Set<ObjectRef> visited, final Set<ObjectRef> reachableRoots) {
		if (!visited.add(current)) {
			return;
		}
		for (final var dependency : this.xref.getDependencies(current)) {
			if (sharedRoots.contains(dependency)) {
				reachableRoots.add(dependency);
			}
			this.collectReachableSharedRoots(dependency, sharedRoots, visited, reachableRoots);
		}
	}

	private long computeSharedSectionEnd(final net.zamasoft.pdfg2d.io.FragmentedOutput.PositionInfo posInfo,
			final long shift, final List<SharedObjectGroup> sharedGroups) {
		long end = 0;
		for (final var group : sharedGroups) {
			for (final var member : group.members) {
				final var impl = (ObjectRefImpl) member;
				end = Math.max(end, impl.getPosition(posInfo) + shift + impl.getLength());
			}
		}
		return end;
	}

	private void writeArea(final ViewerPreferences.AreaBox area) throws IOException {
		switch (area) {
			case MEDIA -> this.catalogFlow.writeName("MediaBox");
			case BLEED -> this.catalogFlow.writeName("BleedBox");
			case TRIM -> this.catalogFlow.writeName("TrimBox");
			case ART -> this.catalogFlow.writeName("ArtBox");
			case CROP -> {
			}
		}
	}
}
