package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.params.OutputIntent;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Writes the OutputIntent dictionary and ICC profile stream for {@code /OutputIntents}
 * (2026-08-01, extracted from the PDFWriterImpl constructor,
 * following the same pattern as ViewerPreferencesWriter/XMPMetadataWriter).
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
final class OutputIntentWriter {
	private static final String ECI_PROFILE_RESOURCE = "ISOcoated_v2_300_eci.icc";
	private static final String SRGB_PROFILE_RESOURCE = "sRGB_IEC61966-2-1_no_black_scaling.icc";

	private static volatile OutputIntent defaultCmykIntent;
	private static volatile OutputIntent defaultRgbIntent;

	private OutputIntentWriter() {
		// static use only
	}

	/** Loads the bundled ICC profile once and returns the default CMYK output intent. */
	static OutputIntent defaultCmykIntent() throws IOException {
		var intent = defaultCmykIntent;
		if (intent == null) {
			synchronized (OutputIntentWriter.class) {
				intent = defaultCmykIntent;
				if (intent == null) {
					intent = new OutputIntent("FOGRA39", "ISO Coated v2 300% (ECI)",
							OutputIntent.ICC_REGISTRY,
							"Offset printing, ISO 12647-2:2004/Amd 1, paper type 1/2 (coated), TAC 300%",
							PDFWriterImpl.loadResource(ECI_PROFILE_RESOURCE), 4);
					defaultCmykIntent = intent;
				}
			}
		}
		return intent;
	}

	/** Returns the explicit or default output intent ICC profile used for CMYK conversion. */
	static byte[] cmykProfile(final PDFParams params) throws IOException {
		final var intent = params.outputIntent();
		if (intent != null && intent.colorComponents() == 4 && intent.iccProfile() != null) {
			return intent.iccProfile();
		}
		return defaultCmykIntent().iccProfile();
	}

	/** Loads the bundled ICC profile once and returns the default RGB output intent. */
	private static OutputIntent defaultRgbIntent() throws IOException {
		var intent = defaultRgbIntent;
		if (intent == null) {
			synchronized (OutputIntentWriter.class) {
				intent = defaultRgbIntent;
				if (intent == null) {
					intent = new OutputIntent("sRGB IEC61966-2.1", null, null, null,
							PDFWriterImpl.loadResource(SRGB_PROFILE_RESOURCE), 3);
					defaultRgbIntent = intent;
				}
			}
		}
		return intent;
	}

	/**
	 * Writes the OutputIntent object.
	 *
	 * @param mainFlow output destination
	 * @param xref     cross-reference (allocates the indirect reference for the ICC profile)
	 * @param params   PDF generation parameters
	 * @param ref      indirect reference already allocated for the OutputIntent dictionary
	 * @throws IOException if an I/O error occurs
	 */
	static void write(final PDFFragmentOutputImpl mainFlow, final XRefImpl xref, final PDFParams params,
			final ObjectRef ref) throws IOException {
		final var pdfVersion = params.version();
		mainFlow.startObject(ref);
		mainFlow.startHash();
		mainFlow.writeName("Type");
		mainFlow.writeName("OutputIntent");
		mainFlow.lineBreak();

		mainFlow.writeName("S");
		mainFlow.writeName(pdfVersion.isPdfX() ? "GTS_PDFX" : "GTS_PDFA1");
		mainFlow.lineBreak();

		// Always prefer an explicit setting. PDF/X requires a CMYK output intent describing
		// the printing conditions, regardless of the content's color mode.
		var intent = params.outputIntent();
		if (intent == null) {
			if (pdfVersion.isPdfX()
					|| params.effectiveColorMode() == PDFParams.ColorMode.CMYK) {
				intent = defaultCmykIntent();
			} else {
				intent = defaultRgbIntent();
			}
		}

		mainFlow.writeName("OutputConditionIdentifier");
		writeByteString(mainFlow, intent.outputConditionIdentifier());
		mainFlow.lineBreak();

		if (intent.outputCondition() != null) {
			mainFlow.writeName("OutputCondition");
			mainFlow.writeText(intent.outputCondition());
			mainFlow.lineBreak();
		}

		if (intent.registryName() != null) {
			mainFlow.writeName("RegistryName");
			writeByteString(mainFlow, intent.registryName());
			mainFlow.lineBreak();
		}

		// Info is required unless the identifier names a registered standard
		// condition (ISO 32000-1 table 365). Whether a name is registered cannot
		// be decided here, so PDF/X always gets one: the condition, else the
		// identifier (2026-10-07; a custom intent without Info was not PDF/X).
		final String info = intent.info() != null ? intent.info()
				: pdfVersion.isPdfX() ? (intent.outputCondition() != null ? intent.outputCondition()
						: intent.outputConditionIdentifier())
						: null;
		if (info != null) {
			mainFlow.writeName("Info");
			mainFlow.writeText(info);
			mainFlow.lineBreak();
		}

		final var profileData = intent.iccProfile();
		if (profileData != null) {
			final var profRef = xref.nextObjectRef();
			mainFlow.writeName("DestOutputProfile");
			mainFlow.writeObjectRef(profRef);
			mainFlow.lineBreak();

			mainFlow.endHash();
			mainFlow.endObject();

			mainFlow.startObject(profRef);
			mainFlow.startHash();

			mainFlow.writeName("N");
			mainFlow.writeInt(intent.colorComponents());
			mainFlow.lineBreak();

			try (final var pout = mainFlow.startStreamFromHash(PDFFragmentOutput.Mode.BINARY)) {
				pout.write(profileData);
			}
		} else {
			mainFlow.endHash();
		}
		mainFlow.endObject();
	}

	/**
	 * Writes an identifier string. ASCII goes out byte for byte; anything else
	 * as a UTF-16 text string, since the byte writer keeps only the low 8 bits
	 * and silently garbled non-ASCII names (2026-10-07). PDF/X refuses
	 * non-ASCII identifiers before this point.
	 */
	private static void writeByteString(final PDFFragmentOutputImpl out, final String s) throws IOException {
		if (s.chars().allMatch(c -> c < 0x80)) {
			out.writeString(s);
		} else {
			out.writeText(s);
		}
	}
}
