package net.zamasoft.pdfg2d.svg;

import java.awt.Graphics2D;
import java.io.IOException;

import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.filter.GraphicsNodeRable8Bit;

import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.pdf.PDFGraphicsOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * A filter for rendering graphics nodes with transparency in PDF output.
 * 
 * <p>
 * This class wraps a graphics node and handles its rendering through a PDF
 * transparency group when the output supports it (PDF 1.4+). For unsupported
 * PDF versions or non-PDF outputs, it falls back to Batik's default
 * rasterization.
 * 
 * <p>
 * Transparency groups are required for correct rendering of partially
 * transparent elements in PDF documents.
 * 
 * @since 1.0
 */
class PDFTransparencyRable extends GraphicsNodeRable8Bit {

	/** Whether to force vector output regardless of PDF version. */
	private final boolean forceVector;

	/**
	 * Creates a new PDF graphics node filter.
	 *
	 * @param node        the graphics node to wrap
	 * @param forceVector true to force vector output (bypass version checks)
	 */
	PDFTransparencyRable(final GraphicsNode node, final boolean forceVector) {
		super(node);
		this.forceVector = forceVector;
	}

	/**
	 * Paints this node to the graphics context.
	 * 
	 * <p>
	 * If the graphics context is a PDF context with transparency support
	 * (PDF 1.4+, excluding PDF/A-1b and PDF/X-1a), the node is rendered
	 * through a transparency group. Otherwise, falls back to default rendering.
	 *
	 * @param g2d the graphics context to paint to
	 * @return true if painting was handled, false to use default rendering
	 */
	@Override
	public boolean paintRable(final Graphics2D g2d) {
		// Check if we're rendering to a PDF-capable context
		if (!(g2d instanceof final BridgeGraphics2D bridgeG2d)) {
			return super.paintRable(g2d);
		}

		final GC gc = bridgeG2d.getGC();
		if (!(gc instanceof final PDFGC pdfGc)) {
			return super.paintRable(g2d);
		}

		try {
			final PDFGraphicsOutput pdfOutput = pdfGc.getPDFGraphicsOutput();

			// Check PDF version for transparency support
			if (!this.forceVector) {
				final var pdfVersion = pdfOutput.getPdfWriter().getParams().version();
				if (!supportsTransparency(pdfVersion)) {
					// Fall back to rasterization for unsupported versions
					return super.paintRable(g2d);
				}
			}

			// Render through a transparency group
			renderWithTransparencyGroup(g2d, pdfOutput, gc);

		} catch (final IOException e) {
			throw new RuntimeException("Failed to render PDF transparency group", e);
		}

		return true;
	}

	/**
	 * Checks if the PDF version supports transparency groups.
	 *
	 * @param version the PDF version
	 * @return true if transparency is supported
	 */
	private boolean supportsTransparency(final PDFParams.Version version) {
		// PDF 1.4+ supports transparency, but PDF/A-1b and PDF/X-1a do not
		return version.v >= PDFParams.Version.V_1_4.v
				&& version.v != PDFParams.Version.V_PDFA1B.v
				&& version.v != PDFParams.Version.V_PDFX1A.v;
	}

	/**
	 * Renders the graphics node through a PDF transparency group.
	 *
	 * @param g2d       the original graphics context
	 * @param pdfOutput the PDF output
	 * @param gc        the graphics context
	 * @throws IOException if an I/O error occurs
	 */
	private void renderWithTransparencyGroup(final Graphics2D g2d,
			final PDFGraphicsOutput pdfOutput, final GC gc) throws IOException {
		try (final PDFGroupImage groupImage = pdfOutput.getPdfWriter()
				.createGroupImage(pdfOutput.getWidth(), pdfOutput.getHeight())) {

			final var gc2 = new PDFGC(groupImage);
			try (final var gcState = gc2.begin()) {

				final Graphics2D g2d2 = new BridgeGraphics2D(gc2, g2d.getDeviceConfiguration());
				final var gn = getGraphicsNode();

				if (getUsePrimitivePaint()) {
					gn.primitivePaint(g2d2);
				} else {
					gn.paint(g2d2);
				}

			}
			gc.drawImage(groupImage);
		}
	}
}
