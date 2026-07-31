package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

/**
 * Verifies that GPOS single-adjustment {@code xPlacement} (the visual glyph
 * shift that does not move the pen — e.g. {@code palt} punctuation) is carried
 * through the embedded-CID metrics chain: OpenType plan → subset glyph-id
 * translation → size scaling ({@code FontMetrics.getPlacementAdjustment}).
 * The CJK test font maps U+3001 to palt xPlacement=-19/1000em (asserted at
 * the table level by {@code OpenTypeLayoutTest}).
 */
public class FontPlacementTest {

	private static final int TAG_PALT = FontFeatureSet.packTag("palt");

	private static FontMetrics metrics(final FontFeatureSet features) throws Exception {
		final var fsm = new PDFFontSourceManager();
		final var face = new FontFace();
		face.src = new FileSource(new File("../pdfg2d-font/src/test/resources/data/test.otf"));
		face.fontFamily = FontFamilyList.create("FeaturesCJK");
		fsm.addFontFace(face);
		final var pdf = new PDFWriterImpl(new StreamFragmentedOutput(new ByteArrayOutputStream()),
				PDFParams.createDefault().withFontSourceManager(fsm));
		final var style = new FontStyleImpl(FontFamilyList.create("FeaturesCJK"), 100, Style.NORMAL, Weight.W_400,
				Direction.LTR, TextLayoutHandler.DEFAULT_FONT_POLICY, features);
		return pdf.getFontManager().getFontListMetrics(style).getFontMetrics(0);
	}

	@Test
	public void testPaltXPlacementIsCarriedToMetrics() throws Exception {
		final var palt = FontFeatureSet.of(new int[] { TAG_PALT }, new int[] { 1 });
		final var fm = metrics(palt);
		final var font = ((net.zamasoft.pdfg2d.font.FontMetricsImpl) fm).getFont();
		final int gid = font.toGID(0x3001, palt);
		assertTrue(gid > 0, "U+3001 must map to a glyph");
		// -19/1000em at 100pt = -1.9pt; the advance adjustment stays -50pt.
		assertEquals(-1.9, fm.getPlacementAdjustment(gid), 0.001, "palt xPlacement at 100pt");
		assertEquals(-50, fm.getAdvanceAdjustment(gid), 0.001, "palt xAdvance at 100pt");
	}

	@Test
	public void testNoFeaturesMeansNoPlacement() throws Exception {
		final var fm = metrics(FontFeatureSet.EMPTY);
		final var font = ((net.zamasoft.pdfg2d.font.FontMetricsImpl) fm).getFont();
		final int gid = font.toGID(0x3001, FontFeatureSet.EMPTY);
		assertEquals(0, fm.getPlacementAdjustment(gid), 0.0, "no feature, no placement");
	}
}
