package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

/**
 * Integration tests for gradient painting: axial (ShadingType 2) and radial
 * (ShadingType 3) shadings, including multi-stop gradients that require a
 * stitching function. Exercises the pattern/shading resource generation and
 * its per-document caching.
 */
public class GradientShadingTest {

	@Test
	public void testLinearGradientProducesAxialShading() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "linear_gradient.pdf");
		try (final var g2d = new PDFGraphics2D(file, 400, 400)) {
			g2d.setPaint(new GradientPaint(0, 0, Color.RED, 400, 400, Color.BLUE));
			g2d.fill(new Rectangle2D.Double(0, 0, 400, 400));
		}

		try (final var doc = Loader.loadPDF(file)) {
			assertTrue(doc.getNumberOfPages() == 1);
		}
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/ShadingType 2"), "Linear gradient must emit an axial shading");
		assertTrue(raw.contains("/PatternType 2"), "Shading must be wrapped in a shading pattern");
	}

	@Test
	public void testRadialGradientProducesRadialShading() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "radial_gradient.pdf");
		try (final var g2d = new PDFGraphics2D(file, 400, 400)) {
			g2d.setPaint(new RadialGradientPaint(new Point2D.Double(200, 200), 150,
					new float[] { 0f, 1f }, new Color[] { Color.WHITE, Color.BLACK }));
			g2d.fill(new Rectangle2D.Double(0, 0, 400, 400));
		}

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/ShadingType 3"), "Radial gradient must emit a radial shading");
	}

	@Test
	public void testMultiStopGradientUsesStitchingFunction() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "multi_stop_gradient.pdf");
		try (final var g2d = new PDFGraphics2D(file, 400, 400)) {
			// Stops that do not span [0,1] force lead-in/tail constant segments
			g2d.setPaint(new LinearGradientPaint(new Point2D.Double(0, 0), new Point2D.Double(400, 0),
					new float[] { 0.2f, 0.5f, 0.9f },
					new Color[] { Color.RED, Color.GREEN, Color.BLUE }));
			g2d.fill(new Rectangle2D.Double(0, 0, 400, 400));
		}

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/FunctionType 3"), "Multi-stop gradient must use a stitching function");
		assertTrue(raw.contains("/Bounds"), "Stitching function must carry stop bounds");
	}

	@Test
	public void testIdenticalGradientsShareOneShadingResource() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "cached_gradient.pdf");
		try (final var g2d = new PDFGraphics2D(file, 400, 400)) {
			final var paint = new GradientPaint(0, 0, Color.RED, 400, 400, Color.BLUE);
			g2d.setPaint(paint);
			g2d.fill(new Rectangle2D.Double(0, 0, 100, 100));
			g2d.setPaint(Color.BLACK);
			g2d.fill(new Rectangle2D.Double(100, 0, 10, 10));
			g2d.setPaint(paint);
			g2d.fill(new Rectangle2D.Double(200, 200, 100, 100));
		}

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		// The same gradient used twice with the same transform must not
		// produce a second shading dictionary.
		final var count = raw.split("/ShadingType 2", -1).length - 1;
		assertTrue(count == 1, "Expected exactly one shading resource, found " + count);
	}
}
