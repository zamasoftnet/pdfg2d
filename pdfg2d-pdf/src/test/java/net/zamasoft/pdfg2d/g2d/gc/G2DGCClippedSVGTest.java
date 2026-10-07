package net.zamasoft.pdfg2d.g2d.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.util.XMLResourceDescriptor;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.svg.PDFGVTBuilder;
import net.zamasoft.pdfg2d.svg.SVGDimension;
import net.zamasoft.pdfg2d.svg.SVGImage;
import net.zamasoft.pdfg2d.svg.SVGUserAgent;

/** Verifies that SVGs drawn through an outer clip and transform preserve child Graphics2D coordinates. */
class G2DGCClippedSVGTest {
	private static final double SCALE = 2.0 / 3.0;
	private static final Rectangle2D OUTER_CLIP = new Rectangle2D.Double(0, 0, 35, 50);
	private static final Color[] RECT_COLORS = { Color.RED, Color.GREEN, Color.BLUE };

	/**
	 * Batik uses a Graphics2D created with {@code create()} for clipped elements. Even after
	 * restoring the transform in that child, it must not apply the parent transform twice,
	 * since the saved state already includes it.
	 */
	@Test
	void clippedSvgKeepsPositionWhenDrawnThroughOuterClipAndScale() throws Exception {
		final var svg = loadSvg("""
				<svg xmlns="http://www.w3.org/2000/svg" width="96" height="40">
				  <g transform="translate(30 0)">
				    <g clip-path="url(#c)">
				      <rect x="0" y="0" width="4" height="4"
				            transform="translate(1 1)" fill="#ff0000"/>
				      <rect x="0" y="10" width="8" height="6" fill="#00ff00"/>
				    </g>
				  </g>
				  <rect x="5" y="30" width="8" height="6" fill="#0000ff"/>
				  <defs>
				    <clipPath id="c" shape-rendering="crispEdges">
				      <rect x="0" y="0" width="50" height="25"/>
				    </clipPath>
				  </defs>
				</svg>
				""");

		final var direct = render(svg, false);
		final var throughOuterClip = render(svg, true);
		final var directGreen = colorBounds(direct, Color.GREEN);
		final var scaledGreen = colorBounds(throughOuterClip, Color.GREEN);

		final var directCount = visibleColorCount(direct);
		assertEquals(RECT_COLORS.length, directCount, "直接描画した矩形数");
		assertEquals(directCount, visibleColorCount(throughOuterClip),
				"外側クリップと変換を通しても矩形数を維持する");
		assertNotNull(directGreen, "直接描画したクリップ内の矩形");
		assertNotNull(scaledGreen, "外側クリップ内に残る拡縮後の矩形");
		assertEquals(directGreen.getX() * SCALE, scaledGreen.getX(), 1.0,
				"クリップ内の矩形のx座標");
		assertEquals(directGreen.getY() * SCALE, scaledGreen.getY(), 1.0,
				"クリップ内の矩形のy座標");
	}

	private static BufferedImage render(final Image svg, final boolean outerClip) {
		final var canvas = new BufferedImage(96, 64, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = canvas.createGraphics();
		try {
			final var gc = new G2DGC(graphics, null);
			if (!outerClip) {
				gc.drawImage(svg);
				return canvas;
			}

			gc.clip(OUTER_CLIP);
			try (final GC.State state = gc.begin()) {
				gc.transform(AffineTransform.getScaleInstance(SCALE, SCALE));
				gc.drawImage(svg);
			}
			return canvas;
		} finally {
			graphics.dispose();
		}
	}

	private static Image loadSvg(final String svg) throws Exception {
		final var parser = XMLResourceDescriptor.getXMLParserClassName();
		final var factory = new SAXSVGDocumentFactory(parser);
		final Document document;
		try (final var in = new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))) {
			document = factory.createDocument("memory:clipped-transform.svg", in);
		}

		final var userAgent = new SVGUserAgent(new SVGDimension(1, 1));
		final var context = new BridgeContext(userAgent);
		context.setDynamicState(BridgeContext.STATIC);
		final var root = new PDFGVTBuilder().build(context, document);
		final var size = context.getDocumentSize();
		return new SVGImage(root, size.getWidth(), size.getHeight());
	}

	private static int visibleColorCount(final BufferedImage image) {
		var count = 0;
		for (final var color : RECT_COLORS) {
			if (colorBounds(image, color) != null) {
				++count;
			}
		}
		return count;
	}

	private static Rectangle colorBounds(final BufferedImage image, final Color color) {
		var minX = image.getWidth();
		var minY = image.getHeight();
		var maxX = -1;
		var maxY = -1;
		for (var y = 0; y < image.getHeight(); ++y) {
			for (var x = 0; x < image.getWidth(); ++x) {
				if (image.getRGB(x, y) == color.getRGB()) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		return maxX < 0 ? null : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}
}
