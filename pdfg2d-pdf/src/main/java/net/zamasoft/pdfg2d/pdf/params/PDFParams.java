package net.zamasoft.pdfg2d.pdf.params;

import net.zamasoft.pdfg2d.font.FontSourceManager;
import net.zamasoft.pdfg2d.pdf.PDFMetaInfo;
import net.zamasoft.pdfg2d.pdf.action.Action;
import net.zamasoft.pdfg2d.pdf.font.ConfigurablePDFFontSourceManager;

/**
 * Parameters for generating PDF documents.
 * 
 * @param fontSourceManager        Manager for font sources
 * @param version                  PDF version to generate
 * @param compression              Compression mode for content streams
 * @param jpegImage                Handling mode for JPEG images
 * @param imageCompression         Compression algorithm for images
 * @param imageCompressionLossless Threshold size for lossless image compression
 * @param platformEncoding         Encoding for text strings
 * @param bookmarks                Whether to generate bookmarks
 * @param encryption               Encryption settings
 * @param colorMode                Color mode (e.g., RGB, CMYK, Gray)
 * @param maxImageWidth            Maximum width for images (0 for no limit)
 * @param maxImageHeight           Maximum height for images (0 for no limit)
 * @param precision                Decimal precision for coordinates
 * @param fileId                   File ID (16 bytes)
 * @param metaInfo                 Metadata information
 * @param viewerPreferences        Viewer preferences
 * @param openAction               Action to perform when document opens
 * @param linearized               Whether to produce a linearized (Fast Web View) file
 * @param outputIntent             Output intent (characterized printing condition), or {@code null} for defaults
 * @param tagged                   Tagged PDF configuration, or {@code null} for untagged output
 */
public record PDFParams(
		FontSourceManager fontSourceManager,
		Version version,
		Compression compression,
		JPEGImage jpegImage,
		ImageCompression imageCompression,
		int imageCompressionLossless,
		String platformEncoding,
		boolean bookmarks,
		EncryptionParams encryption,
		ColorMode colorMode,
		int maxImageWidth,
		int maxImageHeight,
		int precision,
		byte[] fileId,
		PDFMetaInfo metaInfo,
		ViewerPreferences viewerPreferences,
		Action openAction,
		boolean linearized,
		OutputIntent outputIntent,
		TaggedParams tagged) {

	/**
	 * Represents the PDF version or conformance profile to generate.
	 * <p>
	 * The numeric value {@link #v} encodes the base PDF specification the
	 * profile builds on (e.g. {@code 17xx} for ISO 32000-1 based profiles),
	 * so feature gates of the form {@code version.v >= V_1_5.v} remain valid
	 * for the archival/prepress profiles. Profile-specific restrictions
	 * (encryption, transparency, color, attachments) are driven by the
	 * metadata exposed through the accessor methods instead of identity
	 * comparisons.
	 * </p>
	 */
	public enum Version {
		V_1_2(1200, "1.2", 0, null, null),
		V_1_3(1300, "1.3", 0, null, null),
		V_1_4(1400, "1.4", 0, null, null),
		/** PDF/A-1b (ISO 19005-1), based on PDF 1.4. */
		V_PDFA1B(1412, "1.4", 1, "B", null),
		/** PDF/X-1a:2003 (ISO 15930-4), based on PDF 1.4; CMYK/gray/spot only. */
		V_PDFX1A(1421, "1.4", 0, null, "PDF/X-1a:2003"),
		V_1_5(1500, "1.5", 0, null, null),
		V_1_6(1600, "1.6", 0, null, null),
		/** PDF/X-4 (ISO 15930-7), based on PDF 1.6; live transparency and layers. */
		V_PDFX4(1624, "1.6", 0, null, "PDF/X-4"),
		/**
		 * PDF/VT-1 (ISO 16612-2) for variable/transactional printing: PDF/X-4
		 * plus a document part (DPart) hierarchy.
		 */
		V_PDFVT1(1626, "1.6", 0, null, "PDF/X-4"),
		V_1_7(1700, "1.7", 0, null, null),
		/** PDF/A-2b (ISO 19005-2), based on PDF 1.7. */
		V_PDFA2B(1712, "1.7", 2, "B", null),
		/** PDF/A-3b (ISO 19005-3): PDF/A-2b plus arbitrary attachments. */
		V_PDFA3B(1713, "1.7", 3, "B", null),
		/** PDF/A-2u: level B plus guaranteed Unicode mapping of all text. */
		V_PDFA2U(1714, "1.7", 2, "U", null),
		/** PDF/A-2a: level U plus tagged logical structure. */
		V_PDFA2A(1716, "1.7", 2, "A", null),
		/** PDF/A-3a: level A with arbitrary attachments. */
		V_PDFA3A(1717, "1.7", 3, "A", null),
		/** PDF/A-3u: level U with arbitrary attachments. */
		V_PDFA3U(1715, "1.7", 3, "U", null),
		/** Plain PDF 2.0 (ISO 32000-2). */
		V_2_0(2000, "2.0", 0, null, null),
		/** PDF/A-4 (ISO 19005-4), based on PDF 2.0; no conformance levels. */
		V_PDFA4(2040, "2.0", 4, null, null),
		/** PDF/A-4f: PDF/A-4 permitting arbitrary embedded files. */
		V_PDFA4F(2041, "2.0", 4, "F", null),
		/** PDF/X-6 (ISO 15930-9), based on PDF 2.0. */
		V_PDFX6(2060, "2.0", 0, null, "PDF/X-6");

		public final int v;

		private final String baseVersion;

		private final int pdfaPart;

		private final String pdfaConformance;

		private final String pdfxVersion;

		Version(final int v, final String baseVersion, final int pdfaPart, final String pdfaConformance,
				final String pdfxVersion) {
			this.v = v;
			this.baseVersion = baseVersion;
			this.pdfaPart = pdfaPart;
			this.pdfaConformance = pdfaConformance;
			this.pdfxVersion = pdfxVersion;
		}

		/**
		 * Returns the base PDF specification version string for the file
		 * header (e.g. {@code "1.7"} or {@code "2.0"}).
		 *
		 * @return the header version string
		 */
		public String baseVersion() {
			return this.baseVersion;
		}

		/**
		 * Returns whether this version is a PDF/A archival profile.
		 *
		 * @return {@code true} for any PDF/A part
		 */
		public boolean isPdfA() {
			return this.pdfaPart > 0;
		}

		/**
		 * Returns the PDF/A part number (1-4), or {@code 0} when this is not
		 * a PDF/A profile.
		 *
		 * @return the PDF/A part number or {@code 0}
		 */
		public int pdfaPart() {
			return this.pdfaPart;
		}

		/**
		 * Returns the PDF/A conformance level identifier ({@code "B"},
		 * {@code "U"} or {@code "F"}), or {@code null} when not applicable
		 * (non-PDF/A versions and base PDF/A-4).
		 *
		 * @return the conformance level or {@code null}
		 */
		public String pdfaConformance() {
			return this.pdfaConformance;
		}

		/**
		 * Returns whether this version is a PDF/X prepress profile.
		 *
		 * @return {@code true} for PDF/X-1a, PDF/X-4 and PDF/X-6
		 */
		public boolean isPdfX() {
			return this.pdfxVersion != null;
		}

		/**
		 * Returns the {@code GTS_PDFXVersion} identification string, or
		 * {@code null} when this is not a PDF/X profile.
		 *
		 * @return the PDF/X version string or {@code null}
		 */
		public String pdfxVersion() {
			return this.pdfxVersion;
		}

		/**
		 * Returns whether this version is the PDF/VT variable-data printing
		 * profile (which is also a PDF/X-4 conforming file).
		 *
		 * @return {@code true} for PDF/VT-1
		 */
		public boolean isPdfVT() {
			return this == V_PDFVT1;
		}

		/**
		 * Returns whether attachments (embedded files) are permitted.
		 * PDF/A-1 and PDF/A-2 forbid them, base PDF/A-4 only allows PDF/A
		 * attachments (not supported here), and PDF/X forbids them entirely.
		 *
		 * @return {@code true} when embedded files may be added
		 */
		public boolean allowsAttachments() {
			if (this.isPdfX()) {
				return false;
			}
			if (this.isPdfA()) {
				return this.pdfaPart == 3 || this == V_PDFA4F;
			}
			return this.v >= V_1_4.v;
		}

		/**
		 * Returns whether transparency (soft masks, alpha, transparency
		 * groups) is permitted. Only PDF/A-1 and PDF/X-1a forbid it among
		 * the supported profiles.
		 *
		 * @return {@code true} when transparency may be emitted
		 */
		public boolean allowsTransparency() {
			return this.v >= V_1_4.v && this != V_PDFA1B && this != V_PDFX1A;
		}
	}

	/**
	 * Compression mode for content streams.
	 */
	public enum Compression {
		NONE, BINARY, ASCII
	}

	/**
	 * Handling mode for JPEG images (Pass-through or Recompress).
	 */
	public enum JPEGImage {
		RAW, RECOMPRESS
	}

	/**
	 * Compression algorithm for images.
	 */
	public enum ImageCompression {
		FLATE, JPEG, JPEG2000
	}

	/**
	 * Color mode for output (e.g., convert to Gray/CMYK or Preserve).
	 */
	public enum ColorMode {
		PRESERVE, GRAY, CMYK
	}

	public PDFParams {
		// PDF/X-1a allows only CMYK, grayscale and spot colors. PRESERVE
		// would let DeviceRGB operators through, so it is normalized to CMYK
		// conversion; an explicit GRAY mode remains valid as-is.
		// (Also X-4/X-6: those standards allow ICC-managed RGB, but this
		// writer emits DeviceRGB, so conversion to CMYK is the conservative
		// conforming choice until ICCBased color spaces are supported.)
		if (version != null && version.isPdfX() && colorMode == ColorMode.PRESERVE) {
			colorMode = ColorMode.CMYK;
		}
		// PDF/A conformance level A requires tagged logical structure.
		if (version != null && "A".equals(version.pdfaConformance()) && tagged == null) {
			tagged = TaggedParams.TAGGED;
		}
		if (fontSourceManager == null) {
			fontSourceManager = ConfigurablePDFFontSourceManager.getDefaultFontSourceManager();
		}
		if (metaInfo == null) {
			metaInfo = new PDFMetaInfo();
		}
		if (viewerPreferences == null) {
			viewerPreferences = new ViewerPreferences();
		}
		if (platformEncoding == null) {
			platformEncoding = "UTF-8";
		}
		if (fileId != null && fileId.length != 16) {
			throw new IllegalArgumentException("File ID must be a 16-byte array.");
		}
		if (openAction != null) {
			// Note: In the previous class, openAction.setParams(this) was called here.
			// Since PDFParams is now immutable, we cannot pass 'this' to a mutable Action
			// if Action expects to hold a reference to the mutable params.
			// Ideally, Action should not depend on PDFParams, or receive it when writing.
			// For now, we skip setParams call as it implies a circular dependency with
			// mutable state.
		}
	}

	/**
	 * Compatibility constructor without an output intent. Delegates to the
	 * canonical constructor with a {@code null} output intent so existing
	 * callers keep compiling and linking.
	 */
	public PDFParams(
			FontSourceManager fontSourceManager,
			Version version,
			Compression compression,
			JPEGImage jpegImage,
			ImageCompression imageCompression,
			int imageCompressionLossless,
			String platformEncoding,
			boolean bookmarks,
			EncryptionParams encryption,
			ColorMode colorMode,
			int maxImageWidth,
			int maxImageHeight,
			int precision,
			byte[] fileId,
			PDFMetaInfo metaInfo,
			ViewerPreferences viewerPreferences,
			Action openAction,
			boolean linearized) {
		this(fontSourceManager, version, compression, jpegImage, imageCompression, imageCompressionLossless,
				platformEncoding, bookmarks, encryption, colorMode, maxImageWidth, maxImageHeight, precision, fileId,
				metaInfo, viewerPreferences, openAction, linearized, null, null);
	}

	/**
	 * Compatibility constructor without tagged-output configuration.
	 */
	public PDFParams(
			FontSourceManager fontSourceManager,
			Version version,
			Compression compression,
			JPEGImage jpegImage,
			ImageCompression imageCompression,
			int imageCompressionLossless,
			String platformEncoding,
			boolean bookmarks,
			EncryptionParams encryption,
			ColorMode colorMode,
			int maxImageWidth,
			int maxImageHeight,
			int precision,
			byte[] fileId,
			PDFMetaInfo metaInfo,
			ViewerPreferences viewerPreferences,
			Action openAction,
			boolean linearized,
			OutputIntent outputIntent) {
		this(fontSourceManager, version, compression, jpegImage, imageCompression, imageCompressionLossless,
				platformEncoding, bookmarks, encryption, colorMode, maxImageWidth, maxImageHeight, precision, fileId,
				metaInfo, viewerPreferences, openAction, linearized, outputIntent, null);
	}

	/**
	 * Creates a default instance of PDFParams.
	 * 
	 * @return default PDFParams
	 */
	public static PDFParams createDefault() {
		return new PDFParams(
				null, // fontSourceManager (defaults to ConfigurablePDFFontSourceManager)
				Version.V_1_7,
				Compression.BINARY,
				JPEGImage.RAW,
				ImageCompression.FLATE,
				200, // imageCompressionLossless
				"UTF-8", // platformEncoding
				false, // bookmarks
				null, // encryption
				ColorMode.PRESERVE,
				0, // maxImageWidth
				0, // maxImageHeight
				2, // precision
				null, // fileId
				new PDFMetaInfo(),
				new ViewerPreferences(),
				null, // openAction
				false // linearized
		);
	}

	/**
	 * Returns a new instance with the specified font source manager.
	 * 
	 * @param fontSourceManager the font source manager
	 * @return new PDFParams instance
	 */
	public PDFParams withFontSourceManager(FontSourceManager fontSourceManager) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified PDF version.
	 * 
	 * @param version the PDF version
	 * @return new PDFParams instance
	 */
	public PDFParams withVersion(Version version) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified compression mode.
	 * 
	 * @param compression the compression mode
	 * @return new PDFParams instance
	 */
	public PDFParams withCompression(Compression compression) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified JPEG image handling mode.
	 * 
	 * @param jpegImage the JPEG image handling mode
	 * @return new PDFParams instance
	 */
	public PDFParams withJPEGImage(JPEGImage jpegImage) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified image compression algorithm.
	 * 
	 * @param imageCompression the image compression algorithm
	 * @return new PDFParams instance
	 */
	public PDFParams withImageCompression(ImageCompression imageCompression) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified lossless image compression
	 * threshold.
	 * 
	 * @param imageCompressionLossless the threshold size
	 * @return new PDFParams instance
	 */
	public PDFParams withImageCompressionLossless(int imageCompressionLossless) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified platform encoding.
	 * 
	 * @param platformEncoding the platform encoding
	 * @return new PDFParams instance
	 */
	public PDFParams withPlatformEncoding(String platformEncoding) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified bookmarks setting.
	 * 
	 * @param bookmarks true to generate bookmarks, false otherwise
	 * @return new PDFParams instance
	 */
	public PDFParams withBookmarks(boolean bookmarks) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified encryption settings.
	 * 
	 * @param encryption the encryption settings
	 * @return new PDFParams instance
	 */
	public PDFParams withEncryption(EncryptionParams encryption) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified color mode.
	 * 
	 * @param colorMode the color mode
	 * @return new PDFParams instance
	 */
	public PDFParams withColorMode(ColorMode colorMode) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified maximum image width.
	 * 
	 * @param maxImageWidth the maximum image width
	 * @return new PDFParams instance
	 */
	public PDFParams withMaxImageWidth(int maxImageWidth) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified maximum image height.
	 * 
	 * @param maxImageHeight the maximum image height
	 * @return new PDFParams instance
	 */
	public PDFParams withMaxImageHeight(int maxImageHeight) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified precision for coordinates.
	 * 
	 * @param precision the precision (number of decimal places)
	 * @return new PDFParams instance
	 */
	public PDFParams withPrecision(int precision) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified file ID.
	 * 
	 * @param fileId the 16-byte file ID
	 * @return new PDFParams instance
	 */
	public PDFParams withFileId(byte[] fileId) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified metadata.
	 * 
	 * @param metaInfo the metadata information
	 * @return new PDFParams instance
	 */
	public PDFParams withMetaInfo(PDFMetaInfo metaInfo) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified viewer preferences.
	 * 
	 * @param viewerPreferences the viewer preferences
	 * @return new PDFParams instance
	 */
	public PDFParams withViewerPreferences(ViewerPreferences viewerPreferences) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified open action.
	 *
	 * @param openAction the action to execute when the document is opened,
	 *                   or {@code null} for no action
	 * @return new PDFParams instance
	 */
	public PDFParams withOpenAction(Action openAction) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified linearized setting.
	 * 
	 * @param linearized true to generate linearized PDF, false otherwise
	 * @return new PDFParams instance
	 */
	public PDFParams withLinearized(boolean linearized) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified output intent.
	 *
	 * @param outputIntent the output intent configuration
	 * @return new PDFParams instance
	 */
	public PDFParams withOutputIntent(OutputIntent outputIntent) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent, tagged);
	}

	/**
	 * Returns a new instance with the specified tagged-output configuration.
	 *
	 * @param tagged the tagged PDF configuration, or {@code null} to disable
	 * @return new PDFParams instance
	 */
	public PDFParams withTagged(TaggedParams tagged) {
		return new PDFParams(fontSourceManager, version, compression, jpegImage, imageCompression,
				imageCompressionLossless, platformEncoding, bookmarks, encryption, colorMode, maxImageWidth,
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized, outputIntent,
				tagged);
	}
}
