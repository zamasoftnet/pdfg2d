package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.imposition.Trims;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.imposition.GridPDFImposition;
import net.zamasoft.pdfg2d.pdf.imposition.PDFImposition;
import net.zamasoft.pdfg2d.pdf.imposition.SinglePagePDFImposition;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Imposition tests: printer's marks and page boxes for single-page
 * submission layouts, and cell placement order for the grid strategies
 * (N-up, repeat, saddle-stitch booklet).
 */
public class ImpositionTest {

	private static final double MM = 72.0 / 25.4;

	/** Runs {@code pages} logical pages through the given imposition. */
	private File generate(final String name, final PDFParams params,
			final java.util.function.Function<PDFWriter, PDFImposition> impositionFactory, final int pages)
			throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);
			final var imposition = impositionFactory.apply(pdf);
			for (var i = 0; i < pages; ++i) {
				final var gc = imposition.nextPage();
				// Distinctive content per logical page
				gc.setFillPaint(RGBColor.create((i % 3) / 2f, ((i + 1) % 3) / 2f, ((i + 2) % 3) / 2f));
				gc.fill(new Rectangle2D.Double(10, 10 + i * 5, 100, 50));
				imposition.closePage();
			}
			imposition.finish();
			pdf.close();
			builder.close();
		}
		return file;
	}

	/** Extracts the XObject placement order (T-indices) of each page. */
	private static List<List<Integer>> placementOrder(final PDDocument doc) throws Exception {
		final var result = new ArrayList<List<Integer>>();
		final var pattern = Pattern.compile("/T(\\d+)\\s+Do");
		for (final var page : doc.getPages()) {
			final var order = new ArrayList<Integer>();
			try (final var in = page.getContents()) {
				final var content = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
				final var matcher = pattern.matcher(content);
				while (matcher.find()) {
					order.add(Integer.parseInt(matcher.group(1)));
				}
			}
			result.add(order);
		}
		return result;
	}

	@Test
	public void testSinglePageMarksAndBoxes() throws Exception {
		final var file = generate("imposition_single.pdf", PDFParams.createDefault(), pdf -> {
			final var imp = new SinglePagePDFImposition(pdf);
			imp.setPageWidth(210 * MM);
			imp.setPageHeight(297 * MM);
			imp.setTrims(Trims.uniform(10 * MM, 3 * MM));
			imp.fitPaperWidth();
			imp.fitPaperHeight();
			imp.setCrop(true);
			imp.setCross(true);
			return imp;
		}, 2);

		try (final var doc = Loader.loadPDF(file)) {
			assertEquals(2, doc.getNumberOfPages());
			final var page = doc.getPage(0);
			final var media = page.getMediaBox();
			assertEquals(230 * MM, media.getWidth(), 0.5, "Paper = page + horizontal trims");
			assertEquals(317 * MM, media.getHeight(), 0.5);

			final var trim = page.getTrimBox();
			assertEquals(210 * MM, trim.getWidth(), 0.5, "TrimBox must be the finished page size");
			assertEquals(297 * MM, trim.getHeight(), 0.5);
			assertEquals(10 * MM, trim.getLowerLeftX() - media.getLowerLeftX(), 0.5);

			final var bleed = page.getBleedBox();
			assertEquals(216 * MM, bleed.getWidth(), 0.5, "BleedBox = TrimBox + cutting margin");

			// Marks: 16 corner-mark lines + 8 center-mark lines
			try (final var in = page.getContents()) {
				final var content = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
				final var strokes = content.split("\\bS\\b", -1).length - 1;
				assertTrue(strokes >= 24, "Corner and center marks must be stroked, found " + strokes);
			}
		}
	}

	@Test
	public void testSequentialGridFillsSheets() throws Exception {
		final var file = generate("imposition_2x2.pdf", PDFParams.createDefault(), pdf -> {
			final var imp = new GridPDFImposition(pdf, 2, 2, GridPDFImposition.Order.SEQUENTIAL);
			imp.setPageWidth(105 * MM);
			imp.setPageHeight(148 * MM);
			imp.setTrims(new Trims(10 * MM, 10 * MM, 10 * MM, 10 * MM, 3 * MM));
			imp.fitPaperWidth();
			imp.fitPaperHeight();
			return imp;
		}, 5);

		try (final var doc = Loader.loadPDF(file)) {
			assertEquals(2, doc.getNumberOfPages(), "5 logical pages on a 2x2 grid = 2 sheets");
			final var order = placementOrder(doc);
			assertEquals(List.of(0, 1, 2, 3), order.get(0), "First sheet holds pages 1-4 in reading order");
			assertEquals(List.of(4), order.get(1), "Second sheet holds the remaining page");
		}
	}

	@Test
	public void testRepeatGridDuplicatesEachPage() throws Exception {
		final var file = generate("imposition_repeat.pdf", PDFParams.createDefault(), pdf -> {
			final var imp = new GridPDFImposition(pdf, 2, 5, GridPDFImposition.Order.REPEAT);
			// Business card 91x55mm, 10-up
			imp.setPageWidth(91 * MM);
			imp.setPageHeight(55 * MM);
			imp.setTrims(Trims.uniform(10 * MM, 3 * MM));
			imp.fitPaperWidth();
			imp.fitPaperHeight();
			imp.setCrop(true);
			return imp;
		}, 2);

		try (final var doc = Loader.loadPDF(file)) {
			assertEquals(2, doc.getNumberOfPages(), "Each logical page becomes one full sheet");
			final var order = placementOrder(doc);
			assertEquals(List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), order.get(0),
					"All ten cells repeat the first page");
			assertEquals(List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1), order.get(1));
		}
	}

	@Test
	public void testSaddleStitchLeftBoundOrder() throws Exception {
		final var file = generate("imposition_saddle.pdf", PDFParams.createDefault(), pdf -> {
			final var imp = new GridPDFImposition(pdf, 1, 2, GridPDFImposition.Order.SADDLE_STITCH);
			imp.setPageWidth(105 * MM);
			imp.setPageHeight(148 * MM);
			imp.setTrims(Trims.NONE);
			imp.fitPaperWidth();
			imp.fitPaperHeight();
			return imp;
		}, 8);

		try (final var doc = Loader.loadPDF(file)) {
			assertEquals(4, doc.getNumberOfPages(), "8 pages saddle-stitched = 2 sheets x 2 sides");
			final var order = placementOrder(doc);
			// Left binding: [8,1] [2,7] [6,3] [4,5] as 0-based T-indices
			assertEquals(List.of(7, 0), order.get(0));
			assertEquals(List.of(1, 6), order.get(1));
			assertEquals(List.of(5, 2), order.get(2));
			assertEquals(List.of(3, 4), order.get(3));
		}
	}

	@Test
	public void testSaddleStitchRightBoundOrder() throws Exception {
		final var file = generate("imposition_saddle_right.pdf", PDFParams.createDefault(), pdf -> {
			final var imp = new GridPDFImposition(pdf, 1, 2, GridPDFImposition.Order.SADDLE_STITCH);
			imp.setBoundSide(GridPDFImposition.BoundSide.RIGHT);
			imp.setPageWidth(105 * MM);
			imp.setPageHeight(148 * MM);
			imp.setTrims(Trims.NONE);
			imp.fitPaperWidth();
			imp.fitPaperHeight();
			return imp;
		}, 8);

		try (final var doc = Loader.loadPDF(file)) {
			final var order = placementOrder(doc);
			// Right binding mirrors the cells
			assertEquals(List.of(0, 7), order.get(0));
			assertEquals(List.of(6, 1), order.get(1));
		}
	}

	@Test
	public void testSaddleStitchPadsToMultipleOfFour() throws Exception {
		final var file = generate("imposition_saddle_pad.pdf", PDFParams.createDefault(), pdf -> {
			final var imp = new GridPDFImposition(pdf, 1, 2, GridPDFImposition.Order.SADDLE_STITCH);
			imp.setPageWidth(105 * MM);
			imp.setPageHeight(148 * MM);
			imp.setTrims(Trims.NONE);
			imp.fitPaperWidth();
			imp.fitPaperHeight();
			return imp;
		}, 6);

		try (final var doc = Loader.loadPDF(file)) {
			assertEquals(4, doc.getNumberOfPages(), "6 pages pad to 8 = 4 sides");
			final var order = placementOrder(doc);
			// Pages 7 and 8 are blanks: side 1 = [blank, 1], side 2 = [2, blank]
			assertEquals(List.of(0), order.get(0));
			assertEquals(List.of(1), order.get(1));
			assertEquals(List.of(5, 2), order.get(2));
			assertEquals(List.of(3, 4), order.get(3));
		}
	}
}
