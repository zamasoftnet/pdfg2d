package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;

import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.ViewerPreferences;

/**
 * Writes the {@code /ViewerPreferences} dictionary into the catalog.
 * <p>
 * Each entry is version-gated: preferences introduced by a later PDF revision
 * than the target version cause an {@link UnsupportedOperationException}
 * rather than emitting a dictionary the target readers may reject.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
final class ViewerPreferencesWriter {

	private ViewerPreferencesWriter() {
		// static use only
	}

	/**
	 * Writes the viewer preferences from {@code params} to the catalog flow.
	 * Does nothing when no preferences are configured.
	 *
	 * @param catalogFlow the catalog dictionary flow
	 * @param params      the PDF generation parameters
	 * @throws IOException if an I/O error occurs
	 */
	static void write(final PDFFragmentOutputImpl catalogFlow, final PDFParams params) throws IOException {
		final ViewerPreferences vp = params.viewerPreferences();
		if (vp == null) {
			return;
		}
		catalogFlow.writeName("ViewerPreferences");
		catalogFlow.startHash();

		if (vp.isHideToolbar()) {
			catalogFlow.writeName("HideToolbar");
			catalogFlow.writeBoolean(true);
			catalogFlow.lineBreak();
		}

		if (vp.isHideMenubar()) {
			catalogFlow.writeName("HideMenubar");
			catalogFlow.writeBoolean(true);
			catalogFlow.lineBreak();
		}

		if (vp.isHideWindowUI()) {
			catalogFlow.writeName("HideWindowUI");
			catalogFlow.writeBoolean(true);
			catalogFlow.lineBreak();
		}

		if (vp.isFitWindow()) {
			catalogFlow.writeName("FitWindow");
			catalogFlow.writeBoolean(true);
			catalogFlow.lineBreak();
		}

		if (vp.isCenterWindow()) {
			catalogFlow.writeName("CenterWindow");
			catalogFlow.writeBoolean(true);
			catalogFlow.lineBreak();
		}

		if (vp.isDisplayDocTitle()) {
			if (params.version().v < PDFParams.Version.V_1_4.v) {
				throw new UnsupportedOperationException("ViewerPreference DisplayDocTitle requires PDF 1.4 or later.");
			}
			catalogFlow.writeName("DisplayDocTitle");
			catalogFlow.writeBoolean(true);
			catalogFlow.lineBreak();
		}

		if (vp.getNonFullScreenPageMode() != ViewerPreferences.NonFullScreenPageMode.NONE) {
			catalogFlow.writeName("NonFullScreenPageMode");
			switch (vp.getNonFullScreenPageMode()) {
				case OUTLINES -> catalogFlow.writeName("UseOutlines");
				case THUMBS -> catalogFlow.writeName("UseThumbs");
				case OC -> catalogFlow.writeName("UseOC");
				case NONE -> catalogFlow.writeName("UseNone");
			}
			catalogFlow.lineBreak();
		}

		if (vp.getDirection() != ViewerPreferences.Direction.L2R) {
			if (params.version().v < PDFParams.Version.V_1_3.v) {
				throw new UnsupportedOperationException("ViewerPreference Direction requires PDF 1.3 or later.");
			}
			catalogFlow.writeName("Direction");
			if (vp.getDirection() == ViewerPreferences.Direction.R2L) {
				catalogFlow.writeName("R2L");
			} else {
				catalogFlow.writeName("L2R");
			}
			catalogFlow.lineBreak();
		}

		if (vp.getViewArea() != ViewerPreferences.AreaBox.CROP) {
			if (params.version().v < PDFParams.Version.V_1_4.v) {
				throw new UnsupportedOperationException("ViewerPreference ViewArea requires PDF 1.4 or later.");
			}
			catalogFlow.writeName("ViewArea");
			writeArea(catalogFlow, vp.getViewArea());
			catalogFlow.lineBreak();
		}

		if (vp.getViewClip() != ViewerPreferences.AreaBox.CROP) {
			if (params.version().v < PDFParams.Version.V_1_4.v) {
				throw new UnsupportedOperationException("ViewerPreference ViewClip requires PDF 1.4 or later.");
			}
			catalogFlow.writeName("ViewClip");
			writeArea(catalogFlow, vp.getViewClip());
			catalogFlow.lineBreak();
		}

		if (vp.getPrintArea() != ViewerPreferences.AreaBox.CROP) {
			if (params.version().v < PDFParams.Version.V_1_4.v) {
				throw new UnsupportedOperationException("ViewerPreference PrintArea requires PDF 1.4 or later.");
			}
			catalogFlow.writeName("PrintArea");
			writeArea(catalogFlow, vp.getPrintArea());
			catalogFlow.lineBreak();
		}

		if (vp.getPrintClip() != ViewerPreferences.AreaBox.CROP) {
			if (params.version().v < PDFParams.Version.V_1_4.v) {
				throw new UnsupportedOperationException("ViewerPreference PrintClip requires PDF 1.4 or later.");
			}
			catalogFlow.writeName("PrintClip");
			writeArea(catalogFlow, vp.getPrintClip());
			catalogFlow.lineBreak();
		}

		if (vp.getPrintScaling() != ViewerPreferences.PrintScaling.APP_DEFAULT) {
			if (params.version().v < PDFParams.Version.V_1_6.v) {
				throw new UnsupportedOperationException("ViewerPreference PrintScaling requires PDF 1.6 or later.");
			}
			catalogFlow.writeName("PrintScaling");
			catalogFlow.writeName(vp.getPrintScaling() == ViewerPreferences.PrintScaling.NONE ? "None" : "AppDefault");
			catalogFlow.lineBreak();
		}

		if (vp.getDuplex() != ViewerPreferences.Duplex.NONE) {
			if (params.version().v < PDFParams.Version.V_1_7.v) {
				throw new UnsupportedOperationException("ViewerPreference Duplex requires PDF 1.7 or later.");
			}
			catalogFlow.writeName("Duplex");
			switch (vp.getDuplex()) {
				case SIMPLEX -> catalogFlow.writeName("Simplex");
				case FLIP_SHORT_EDGE -> catalogFlow.writeName("DuplexFlipShortEdge");
				case FLIP_LONG_EDGE -> catalogFlow.writeName("DuplexFlipLongEdge");
				default -> {
				}
			}
			catalogFlow.lineBreak();
		}

		if (vp.getPickTrayByPDFSize()) {
			if (params.version().v < PDFParams.Version.V_1_7.v) {
				throw new UnsupportedOperationException(
						"ViewerPreference PickTrayByPDFSize requires PDF 1.7 or later.");
			}
			catalogFlow.writeName("PickTrayByPDFSize");
			catalogFlow.writeBoolean(true);
			catalogFlow.lineBreak();
		}

		final var printPageRange = vp.getPrintPageRange();
		if (printPageRange != null) {
			if (params.version().v < PDFParams.Version.V_1_7.v) {
				throw new UnsupportedOperationException("ViewerPreference PrintPageRange requires PDF 1.7 or later.");
			}
			catalogFlow.writeName("PrintPageRange");
			catalogFlow.startArray();
			for (final var range : printPageRange) {
				catalogFlow.writeInt(range);
			}
			catalogFlow.endArray();
			catalogFlow.lineBreak();
		}

		final int numCopies = vp.getNumCopies();
		if (numCopies > 0) {
			if (params.version().v < PDFParams.Version.V_1_7.v) {
				throw new UnsupportedOperationException("ViewerPreference NumCopies requires PDF 1.7 or later.");
			}
			catalogFlow.writeName("NumCopies");
			catalogFlow.writeInt(numCopies);
			catalogFlow.lineBreak();
		}

		catalogFlow.endHash();

		catalogFlow.lineBreak();
	}

	private static void writeArea(final PDFFragmentOutputImpl catalogFlow, final ViewerPreferences.AreaBox area)
			throws IOException {
		switch (area) {
			case MEDIA -> catalogFlow.writeName("MediaBox");
			case BLEED -> catalogFlow.writeName("BleedBox");
			case TRIM -> catalogFlow.writeName("TrimBox");
			case ART -> catalogFlow.writeName("ArtBox");
			case CROP -> {
			}
		}
	}
}
