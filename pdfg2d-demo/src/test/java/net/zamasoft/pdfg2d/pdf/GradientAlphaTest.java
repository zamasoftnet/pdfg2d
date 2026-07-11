package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

/**
 * Gradient alpha support: RGBA gradient stops are reproduced through a
 * luminosity soft mask on transparency-capable targets and dropped on
 * PDF/A-1b / PDF/X-1a. Verified structurally, by rasterizing with PDFBox,
 * and with veraPDF for the PDF/A-2b case.
 */
public class GradientAlphaTest {

	@BeforeAll
	public static void initVeraPDF() {
		VeraGreenfieldFoundryProvider.initialise();
	}

	private File generate(final String name, final PDFParams params) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		try (final var g2d = new PDFGraphics2D(file, 200, 100, params)) {
			// Red fading from opaque (x=0) to fully transparent (x=200)
			g2d.setPaint(new LinearGradientPaint(new Point2D.Double(0, 0), new Point2D.Double(200, 0),
					new float[] { 0f, 1f },
					new Color[] { new Color(255, 0, 0, 255), new Color(255, 0, 0, 0) },
					MultipleGradientPaint.CycleMethod.NO_CYCLE));
			g2d.fill(new Rectangle2D.Double(0, 0, 200, 100));

			// A following solid fill must clear the soft mask again
			g2d.setPaint(Color.BLUE);
			g2d.fill(new Rectangle2D.Double(0, 90, 10, 10));
		}
		return file;
	}

	@Test
	public void testAlphaGradientEmitsLuminositySoftMask() throws Exception {
		final var file = generate("alpha_gradient.pdf", PDFParams.createDefault());
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/Luminosity"), "Alpha ramp must be applied as a luminosity soft mask");
		assertTrue(raw.contains("/SMask"), "An SMask ExtGState must be emitted");
		assertTrue(raw.contains("/SMask /None"), "The mask must be reset for the following solid fill");
		assertTrue(raw.contains("/DeviceGray"), "The mask group must be DeviceGray");
	}

	@Test
	public void testRenderedAlphaFadesToBackground() throws Exception {
		final var file = generate("alpha_gradient_render.pdf", PDFParams.createDefault());
		try (final var doc = Loader.loadPDF(file)) {
			final var image = new PDFRenderer(doc).renderImage(0);
			// Near the opaque end: strong red
			final var left = new Color(image.getRGB(10, 40));
			assertTrue(left.getRed() > 200 && left.getGreen() < 80,
					"Opaque end must be red, was " + left);
			// Near the transparent end: almost the white background
			final var right = new Color(image.getRGB(image.getWidth() - 8, 40));
			assertTrue(right.getGreen() > 200 && right.getBlue() > 200,
					"Transparent end must fade to the background, was " + right);
			// Middle: a blend, clearly lighter than the opaque end
			final var mid = new Color(image.getRGB(image.getWidth() / 2, 40));
			assertTrue(mid.getGreen() > left.getGreen() + 40,
					"Middle must be a partial blend, was " + mid + " vs " + left);
		}
	}

	@Test
	public void testRadialAlphaGradient() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "alpha_radial.pdf");
		try (final var g2d = new PDFGraphics2D(file, 200, 200, PDFParams.createDefault())) {
			g2d.setPaint(new RadialGradientPaint(new Point2D.Double(100, 100), 90,
					new float[] { 0f, 1f },
					new Color[] { new Color(0, 0, 255, 255), new Color(0, 0, 255, 0) },
					MultipleGradientPaint.CycleMethod.NO_CYCLE));
			g2d.fill(new Rectangle2D.Double(0, 0, 200, 200));
		}
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/Luminosity"));
		assertTrue(raw.contains("/ShadingType 3"), "Radial mask keeps the radial geometry");

		try (final var doc = Loader.loadPDF(file)) {
			final var image = new PDFRenderer(doc).renderImage(0);
			final var center = new Color(image.getRGB(100, 100));
			final var corner = new Color(image.getRGB(4, 4));
			assertTrue(center.getBlue() > 200 && center.getRed() < 80, "Center must be blue, was " + center);
			assertTrue(corner.getRed() > 200 && corner.getGreen() > 200,
					"Corner must fade to the background, was " + corner);
		}
	}

	@Test
	public void testPdfA2bWithAlphaGradientIsCompliant() throws Exception {
		final var file = generate("alpha_gradient_pdfa2b.pdf",
				PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFA2B));
		try (final var parser = Foundries.defaultInstance().createParser(new FileInputStream(file),
				PDFAFlavour.PDFA_2_B);
				final var validator = Foundries.defaultInstance().createValidator(PDFAFlavour.PDFA_2_B, false)) {
			final var result = validator.validate(parser);
			if (!result.isCompliant()) {
				final var failures = result.getTestAssertions().stream()
						.filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
						.map(a -> a.getRuleId() + " " + a.getMessage())
						.distinct()
						.collect(Collectors.joining("\n"));
				assertTrue(result.isCompliant(), "veraPDF PDF/A-2b failures:\n" + failures);
			}
		}
	}

	@Test
	public void testAlphaIsDroppedWherTransparencyForbidden() throws Exception {
		final var file = generate("alpha_gradient_pdfa1b.pdf",
				PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFA1B));
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertFalse(raw.contains("/Luminosity"),
				"PDF/A-1b forbids transparency; the alpha ramp must be dropped");
		assertTrue(raw.contains("/ShadingType 2"), "The color gradient itself is still painted");
	}
}
