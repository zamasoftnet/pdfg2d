package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.demo.DemoUtils;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.pipeline.BreakNode;
import net.zamasoft.pdfg2d.gc.text.pipeline.Itemizer;
import net.zamasoft.pdfg2d.gc.text.pipeline.LineBreaker;
import net.zamasoft.pdfg2d.gc.text.pipeline.Paragraph;
import net.zamasoft.pdfg2d.gc.text.pipeline.Paragraph.StyleSpan;
import net.zamasoft.pdfg2d.gc.text.pipeline.ParagraphLayout;
import net.zamasoft.pdfg2d.gc.text.pipeline.PipelineTextDrawer;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

/**
 * Tests for the paragraph text pipeline (the §C redesign foundation): bidi
 * reordering (UAX #9 L2), greedy line breaking over box/glue/penalty nodes,
 * and end-to-end layout that renders through the existing GC.drawText path.
 */
public class TextPipelineTest {

	@Test
	public void testVisualReorderPureRtlAndMixed() {
		// All LTR: order unchanged.
		assertArrayEquals(new int[] { 0, 1, 2 },
				Itemizer.reorderVisual(new byte[] { 0, 0, 0 }));
		// All RTL (level 1): fully reversed.
		assertArrayEquals(new int[] { 2, 1, 0 },
				Itemizer.reorderVisual(new byte[] { 1, 1, 1 }));
		// "abc" (0) + Hebrew "XYZ" (1): the RTL run reverses within the line.
		// logical a b c X Y Z -> visual a b c Z Y X
		assertArrayEquals(new int[] { 0, 1, 2, 5, 4, 3 },
				Itemizer.reorderVisual(new byte[] { 0, 0, 0, 1, 1, 1 }));
		// Number (level 2) inside RTL (level 1): 1 2 remain in logical order
		// but the whole RTL span reverses around them.
		// logical: R(1) d(2) d(2) R(1) -> visual: R d d R reversed => R d d R
		assertArrayEquals(new int[] { 3, 1, 2, 0 },
				Itemizer.reorderVisual(new byte[] { 1, 2, 2, 1 }));
	}

	@Test
	public void testGreedyBreakingWrapsAtGlue() {
		// Four 30-wide boxes separated by 10-wide glue; measure 80 fits two
		// boxes + one glue (70) then wraps.
		final var nodes = List.<BreakNode>of(
				new BreakNode.Box(30, null, 0, 0),
				new BreakNode.Glue(10, 5, 3),
				new BreakNode.Box(30, null, 0, 0),
				new BreakNode.Glue(10, 5, 3),
				new BreakNode.Box(30, null, 0, 0),
				new BreakNode.Glue(10, 5, 3),
				new BreakNode.Box(30, null, 0, 0));
		final var lines = LineBreaker.greedy(nodes, 80);
		assertEquals(2, lines.size(), "Four words at measure 80 must wrap to two lines");
	}

	@Test
	public void testForcedBreakSplitsLines() {
		final var nodes = List.<BreakNode>of(
				new BreakNode.Box(10, null, 0, 0),
				BreakNode.Penalty.forced(),
				new BreakNode.Box(10, null, 0, 0));
		final var lines = LineBreaker.greedy(nodes, 1000);
		assertEquals(2, lines.size(), "A forced penalty must split the line");
	}

	private static PDFFontSourceManager latinFont() throws Exception {
		final var fsm = new PDFFontSourceManager();
		final var face = new FontFace();
		face.src = new FileSource(DemoUtils.getResourceFile("ipaexm.ttf"));
		face.fontFamily = FontFamilyList.create("Pipeline");
		fsm.addFontFace(face);
		return fsm;
	}

	private static FontStyleImpl style(final double size, final Direction dir) {
		return new FontStyleImpl(FontFamilyList.create("Pipeline"), size, Style.NORMAL, Weight.W_400,
				dir, TextLayoutHandler.DEFAULT_FONT_POLICY);
	}

	@Test
	public void testEndToEndLayoutWrapsAndRenders() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "pipeline_wrap.pdf");
		final var fsm = latinFont();
		final var text = "The quick brown fox jumps over the lazy dog again and again".toCharArray();
		final var para = new Paragraph(text, List.of(new StyleSpan(0, text.length, style(12, Direction.LTR))));

		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault().withFontSourceManager(fsm));
			final var page = pdf.nextPage(300, 300);
			final var layout = new ParagraphLayout(pdf.getFontManager());
			final var lines = layout.layout(para, 200);
			// This text is ~340pt wide, so at measure 200 it wraps to 2 lines.
			assertTrue(lines.size() >= 2, "Expected multiple wrapped lines, got " + lines.size());
			try (final var gc = new PDFGC(page)) {
				var y = 40.0;
				for (final var line : lines) {
					assertTrue(line.width() <= 200 + 1e-6, "Line must fit the measure: " + line.width());
					PipelineTextDrawer.draw(gc, line, 50, y);
					y += 16;
				}
			}
			pdf.close();
			builder.close();
		}
		assertTrue(file.length() > 0);
	}

	@Test
	public void testBidiLayoutReordersVisually() throws Exception {
		final var fsm = latinFont();
		// "abc" + Hebrew aleph-bet-gimel: logical a b c א ב ג
		final var text = "abcאבג".toCharArray();
		final var para = new Paragraph(text, List.of(new StyleSpan(0, text.length, style(12, Direction.LTR))));

		final var fsmMgr = new PDFWriterImpl(
				new StreamFragmentedOutput(new java.io.ByteArrayOutputStream()),
				PDFParams.createDefault().withFontSourceManager(fsm)).getFontManager();
		final var lines = new ParagraphLayout(fsmMgr).layout(para, 1000);
		assertEquals(1, lines.size());
		final var runs = lines.get(0).runs();
		// Visual order of clusters must be a b c then gimel bet aleph (5,4,3).
		final var clusters = runs.stream()
				.map(r -> r.run().clusters[r.glyphBegin()])
				.toList();
		assertEquals(List.of(0, 1, 2, 5, 4, 3), clusters,
				"RTL Hebrew run must be visually reversed after the LTR run");
	}
}
