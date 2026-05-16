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
		boolean linearized) {

	/**
	 * Represents the PDF version.
	 */
	public enum Version {
		V_1_2(1200), V_1_3(1300), V_1_4(1400), V_PDFA1B(1412), V_PDFX1A(1421), V_1_5(1500), V_1_6(1600), V_1_7(1700);

		public final int v;

		Version(int v) {
			this.v = v;
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
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
				maxImageHeight, precision, fileId, metaInfo, viewerPreferences, openAction, linearized);
	}
}
