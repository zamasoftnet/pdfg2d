package net.zamasoft.pdfg2d.g2d.gc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.image.GroupImageGC;

class G2DGCGroupImageTest {
	@Test
	void rotatedTranslatedGroupUsesTheTransformedBoundsInsteadOfAnEndpoint() {
		final BufferedImage canvas = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = canvas.createGraphics();
		try {
			final G2DGC gc = new G2DGC(graphics, null);
			gc.transform(AffineTransform.getTranslateInstance(15, 5));
			gc.transform(AffineTransform.getQuadrantRotateInstance(1));

			final GroupImageGC group = assertDoesNotThrow(() -> gc.createGroupImage(20, 30));
			final Graphics2D groupGraphics = ((G2DGC) group).getGraphics2D();
			groupGraphics.setColor(Color.RED);
			groupGraphics.fill(new Rectangle2D.Double(0, 0, 20, 30));
			gc.drawImage(group.finish());
		} finally {
			graphics.dispose();
		}

		// translate(15,5) + rotate(90deg) maps the rectangle to
		// x=[-15,15], y=[5,25]. Its visible part must remain at that position.
		assertEquals(Color.RED.getRGB(), canvas.getRGB(5, 15));
		assertEquals(0, canvas.getRGB(20, 15));
	}
}
