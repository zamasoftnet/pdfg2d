package net.zamasoft.pdfg2d.pdf.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.params.OutputIntent;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Pins how the writer answers every combination of output intent ICC profile, declared component count, identifier
 * and PDF version: accepted, or the exception and its message (2026-10-08). Written from the output of the code
 * before the ICC checks of foliojet4 and pdfg2d were merged into {@link OutputIntent#checkProfile}, so that the
 * merge provably keeps the response to abnormal input.
 */
public class PdfXOutputIntentResponseTest {
	private static final PDFParams.Version[] VERSIONS = { PDFParams.Version.V_PDFX1A, PDFParams.Version.V_PDFX3,
			PDFParams.Version.V_PDFX4, PDFParams.Version.V_1_5 };

	private static final String EXPECTED = """
			cmyk-output-v2 N=4 V_PDFX1A: OK
			cmyk-output-v2 N=4 V_PDFX3: OK
			cmyk-output-v2 N=4 V_PDFX4: OK
			cmyk-output-v2 N=4 V_1_5: OK
			cmyk-output-v2 N=3 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			cmyk-output-v2 N=3 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			cmyk-output-v2 N=3 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			cmyk-output-v2 N=3 V_1_5: OK
			cmyk-output-v4 N=4 V_PDFX1A: IllegalArgumentException: PDF/X-1a:2003 output intent ICC profile must be ICC version 2, not 4.
			cmyk-output-v4 N=4 V_PDFX3: IllegalArgumentException: PDF/X-3:2003 output intent ICC profile must be ICC version 2, not 4.
			cmyk-output-v4 N=4 V_PDFX4: OK
			cmyk-output-v4 N=4 V_1_5: OK
			cmyk-output-v4 N=3 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			cmyk-output-v4 N=3 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			cmyk-output-v4 N=3 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			cmyk-output-v4 N=3 V_1_5: OK
			srgb-display N=4 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			srgb-display N=4 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			srgb-display N=4 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			srgb-display N=4 V_1_5: OK
			srgb-display N=3 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			srgb-display N=3 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			srgb-display N=3 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			srgb-display N=3 V_1_5: OK
			rgb-output N=4 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			rgb-output N=4 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			rgb-output N=4 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			rgb-output N=4 V_1_5: OK
			rgb-output N=3 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			rgb-output N=3 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			rgb-output N=3 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			rgb-output N=3 V_1_5: OK
			gray-output N=4 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			gray-output N=4 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			gray-output N=4 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			gray-output N=4 V_1_5: OK
			gray-output N=3 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			gray-output N=3 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			gray-output N=3 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			gray-output N=3 V_1_5: OK
			cmyk-display N=4 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			cmyk-display N=4 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			cmyk-display N=4 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			cmyk-display N=4 V_1_5: OK
			cmyk-display N=3 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			cmyk-display N=3 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			cmyk-display N=3 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be an output (prtr) profile.
			cmyk-display N=3 V_1_5: OK
			five-color-output N=4 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			five-color-output N=4 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			five-color-output N=4 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			five-color-output N=4 V_1_5: OK
			five-color-output N=3 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			five-color-output N=3 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			five-color-output N=3 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile must be CMYK (4 components).
			five-color-output N=3 V_1_5: OK
			broken N=4 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile cannot be parsed.
			broken N=4 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile cannot be parsed.
			broken N=4 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile cannot be parsed.
			broken N=4 V_1_5: OK
			broken N=3 V_PDFX1A: IllegalArgumentException: PDF/X output intent ICC profile cannot be parsed.
			broken N=3 V_PDFX3: IllegalArgumentException: PDF/X output intent ICC profile cannot be parsed.
			broken N=3 V_PDFX4: IllegalArgumentException: PDF/X output intent ICC profile cannot be parsed.
			broken N=3 V_1_5: OK
			none N=4 V_PDFX1A: IllegalArgumentException: PDF/X requires a DestOutputProfile: set the ICC profile of the output intent.
			none N=4 V_PDFX3: IllegalArgumentException: PDF/X requires a DestOutputProfile: set the ICC profile of the output intent.
			none N=4 V_PDFX4: IllegalArgumentException: PDF/X requires a DestOutputProfile: set the ICC profile of the output intent.
			none N=4 V_1_5: OK
			none N=3 V_PDFX1A: IllegalArgumentException: PDF/X requires a DestOutputProfile: set the ICC profile of the output intent.
			none N=3 V_PDFX3: IllegalArgumentException: PDF/X requires a DestOutputProfile: set the ICC profile of the output intent.
			none N=3 V_PDFX4: IllegalArgumentException: PDF/X requires a DestOutputProfile: set the ICC profile of the output intent.
			none N=3 V_1_5: OK
			non-ascii-identifier V_PDFX1A: IllegalArgumentException: PDF/X output intent identifier and registry name must be printable ASCII: 日本の印刷
			non-ascii-registry V_PDFX1A: IllegalArgumentException: PDF/X output intent identifier and registry name must be printable ASCII: レジストリ
			control-identifier V_PDFX1A: IllegalArgumentException: PDF/X output intent identifier and registry name must be printable ASCII: FOGRA	39
			non-ascii-identifier V_PDFX3: IllegalArgumentException: PDF/X output intent identifier and registry name must be printable ASCII: 日本の印刷
			non-ascii-registry V_PDFX3: IllegalArgumentException: PDF/X output intent identifier and registry name must be printable ASCII: レジストリ
			control-identifier V_PDFX3: IllegalArgumentException: PDF/X output intent identifier and registry name must be printable ASCII: FOGRA	39
			non-ascii-identifier V_PDFX4: IllegalArgumentException: PDF/X output intent identifier and registry name must be printable ASCII: 日本の印刷
			non-ascii-registry V_PDFX4: IllegalArgumentException: PDF/X output intent identifier and registry name must be printable ASCII: レジストリ
			control-identifier V_PDFX4: IllegalArgumentException: PDF/X output intent identifier and registry name must be printable ASCII: FOGRA	39
			non-ascii-identifier V_1_5: OK
			non-ascii-registry V_1_5: OK
			control-identifier V_1_5: OK
			""";

	@Test
	public void responses() throws Exception {
		final StringBuilder actual = new StringBuilder();
		for (final String profile : new String[] { "cmyk-output-v2", "cmyk-output-v4", "srgb-display", "rgb-output",
				"gray-output", "cmyk-display", "five-color-output", "broken", "none" }) {
			for (final int components : new int[] { 4, 3 }) {
				for (final PDFParams.Version version : VERSIONS) {
					actual.append(profile).append(" N=").append(components).append(' ').append(version).append(": ")
							.append(write(version, "FOGRA39", null, profile(profile), components)).append('\n');
				}
			}
		}
		for (final PDFParams.Version version : VERSIONS) {
			actual.append("non-ascii-identifier ").append(version).append(": ")
					.append(write(version, "日本の印刷", null, profile("cmyk-output-v2"), 4)).append('\n');
			actual.append("non-ascii-registry ").append(version).append(": ")
					.append(write(version, "FOGRA39", "レジストリ", profile("cmyk-output-v2"), 4)).append('\n');
			actual.append("control-identifier ").append(version).append(": ")
					.append(write(version, "FOGRA\t39", null, profile("cmyk-output-v2"), 4)).append('\n');
		}
		assertEquals(EXPECTED, actual.toString());
	}

	private static String write(final PDFParams.Version version, final String identifier, final String registry,
			final byte[] profile, final int components) {
		try {
			final OutputIntent intent = new OutputIntent(identifier, null, registry, null, profile, components);
			final var params = PDFParams.createDefault().withVersion(version).withOutputIntent(intent);
			try (final var builder = new StreamFragmentedOutput(new ByteArrayOutputStream());
					final var pdf = new PDFWriterImpl(builder, params)) {
				pdf.nextPage(100, 100).close();
			}
			return "OK";
		} catch (final Exception e) {
			return e.getClass().getSimpleName() + ": " + e.getMessage();
		}
	}

	private static byte[] profile(final String name) throws Exception {
		switch (name) {
		case "cmyk-output-v2":
			return cmykOutput();
		case "cmyk-output-v4": {
			final byte[] data = cmykOutput();
			data[8] = 4;
			return data;
		}
		case "srgb-display":
			return ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
		case "rgb-output":
			return relabel(ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData(), 12, "prtr");
		case "gray-output":
			return relabel(ICC_Profile.getInstance(ColorSpace.CS_GRAY).getData(), 12, "prtr");
		case "cmyk-display":
			return relabel(cmykOutput(), 12, "mntr");
		case "five-color-output":
			return relabel(cmykOutput(), 16, "5CLR");
		case "broken":
			return new byte[] { 1, 7, 3, 9, 0, 4 };
		case "none":
			return null;
		default:
			throw new IllegalArgumentException(name);
		}
	}

	/** Rewrites a four-byte header signature (12: profile class, 16: color space). */
	private static byte[] relabel(final byte[] data, final int at, final String signature) {
		final byte[] copy = data.clone();
		System.arraycopy(signature.getBytes(StandardCharsets.US_ASCII), 0, copy, at, 4);
		return copy;
	}

	private static byte[] cmykOutput() throws Exception {
		try (InputStream in = PDFWriterImpl.class.getResourceAsStream("ISOcoated_v2_300_eci.icc")) {
			assertNotNull(in);
			return in.readAllBytes();
		}
	}
}
