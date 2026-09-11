package net.zamasoft.pdfg2d.pdf.font.cid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.PDFOutput;

/** Exact PDF syntax and sparse/default metric contracts. */
class CIDUtilsVerticalMetricsTest {
	private static final short UNUSED = Short.MIN_VALUE;

	private static String write(final short[] widths, final short[] advances, final short[] origins) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		try (final var out = new PDFOutput(bytes, "ISO-8859-1")) {
			CIDUtils.writeW2(out, widths, advances, origins);
		}
		return bytes.toString(StandardCharsets.ISO_8859_1).replaceAll("\\s+", " ")
				.replace("[ ", "[").replace(" ]", "]").trim();
	}

	@Test
	void oddHorizontalWidthIsNotRoundedAndDefaultCidsAreOmitted() throws Exception {
		assertEquals("/DW2 [880 -1000] /W2 [1 [-1000 318.5 829]]",
				write(new short[] { 1000, 637, 1000 }, new short[] { 1000, 1000, 1000 },
						new short[] { 880, 829, 880 }));
	}

	@Test
	void allDefaultMetricsNeedNoExplicitEntries() throws Exception {
		assertEquals("/DW2 [879 -1000] /W2 []",
				write(new short[] { 637, 1000 }, new short[] { 1000, 1000 }, new short[] { 879, 879 }));
	}

	@Test
	void nullAndEmptyAdvancesWriteNothing() throws Exception {
		assertEquals("", write(null, null, null));
		assertEquals("", write(null, new short[0], new short[0]));
	}

	@Test
	void allUnusedCidsProduceNoExplicitMetrics() throws Exception {
		assertEquals("/DW2 [880 0] /W2 []",
				write(null, new short[] { UNUSED, UNUSED }, new short[] { UNUSED, UNUSED }));
	}

	@Test
	void nullOriginsUse880() throws Exception {
		assertEquals("/DW2 [880 -1000] /W2 [1 [-500 318.5 880]]",
				write(new short[] { 1000, 637, 1000 }, new short[] { 1000, 500, 1000 }, null));
	}

	@Test
	void sparseAdvancesKeepLegacyFrequencyAndSkipUnusedCids() throws Exception {
		assertEquals("/DW2 [880 -1000] /W2 [3 4 -500 500 880]",
				write(new short[] { 1000, UNUSED, UNUSED, 1000, 1000 },
						new short[] { 1000, UNUSED, UNUSED, 500, 500 },
						new short[] { 880, UNUSED, UNUSED, 880, 880 }));
	}

	@Test
	void repeatedTriplesUseRangesAndDifferentTriplesUseArrays() throws Exception {
		assertEquals("/DW2 [880 -1000] /W2 [1 3 -500 500 880 5 [-600 318.5 880 -700 500 880]]",
				write(new short[] { 1000, 1000, 1000, 1000, 1000, 637, 1000, 1000, 1000, 1000 },
						new short[] { 1000, 500, 500, 500, 1000, 600, 700, 1000, 1000, 1000 }, null));
	}

	@Test
	void defaultTripleAlwaysBreaksAnArray() throws Exception {
		assertEquals("/DW2 [880 -1000] /W2 [0 [-500 500 880] 2 [-600 500 880]]",
				write(new short[] { 1000, 1000, 1000, 1000 }, new short[] { 500, 1000, 600, 1000 }, null));
	}

	@Test
	void originFrequencyCountsOnlyUsedCidsAndBreaksTiesBySmallerValue() throws Exception {
		assertEquals("/DW2 [829 -1000] /W2 [1 [-1000 500 880]]",
				write(new short[] { 637, 1000, UNUSED }, new short[] { 1000, 1000, UNUSED },
						new short[] { 829, 880, 880 }));
	}

	@Test
	void incompleteOriginArraysSkipMissingEntries() throws Exception {
		for (final var origins : new short[][] { { 829 }, { 829, UNUSED }, { 829, UNUSED, 700 } }) {
			assertEquals("/DW2 [829 -1000] /W2 []",
					write(new short[] { 637, 1000 }, new short[] { 1000, 1000 }, origins));
		}
		assertEquals("/DW2 [880 -1000] /W2 []",
				write(new short[] { 1000 }, new short[] { 1000 }, new short[0]));
	}

	@Test
	void missingHorizontalWidthIsRejectedBeforeWriting() {
		for (final var widths : new short[][] { null, {}, { UNUSED } }) {
			assertThrows(IllegalArgumentException.class, () -> write(widths, new short[] { 1000 }, null));
		}
		assertThrows(IllegalArgumentException.class,
				() -> write(new short[] { 1000 }, new short[] { 1000, 1000 }, new short[] { 880 }));
	}
}
