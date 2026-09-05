package net.zamasoft.pdfg2d.gc.font.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/** {@link FontUtils#addTextPath}のペン送りと行方向を検証する。 */
public class FontUtilsLeadingXAdvanceTest {
	private static final FontSource SOURCE = (FontSource) Proxy.newProxyInstance(
			FontUtilsLeadingXAdvanceTest.class.getClassLoader(), new Class<?>[] { FontSource.class },
			(proxy, method, args) -> switch (method.getName()) {
				case "getDirection" -> FontStyle.Direction.LTR;
				case "getBBox" -> new BBox((short) 0, (short) -200, (short) 1000, (short) 800);
				case "isItalic" -> false;
				case "toString" -> "FixedFontSource";
				default -> defaultValue(method.getReturnType());
			});

	private static final ShapedFont FONT = (ShapedFont) Proxy.newProxyInstance(
			FontUtilsLeadingXAdvanceTest.class.getClassLoader(), new Class<?>[] { ShapedFont.class },
			(proxy, method, args) -> switch (method.getName()) {
				case "getFontSource" -> SOURCE;
				case "getShapeByGID" -> new Rectangle2D.Double(0, -800, 600, 800);
				case "toString" -> "FixedShapedFont";
				default -> defaultValue(method.getReturnType());
			});

	private static final FontMetrics METRICS = new FontMetrics() {
		private static final long serialVersionUID = 1L;

		@Override
		public double getFontSize() {
			return 10;
		}

		@Override
		public double getXHeight() {
			return 5;
		}

		@Override
		public double getAscent() {
			return 8;
		}

		@Override
		public double getDescent() {
			return 2;
		}

		@Override
		public double getAdvance(final int gid) {
			return 10;
		}

		@Override
		public double getWidth(final int gid) {
			return 10;
		}

		@Override
		public double getSpaceAdvance() {
			return 10;
		}

		@Override
		public double getKerning(final int gid, final int sgid) {
			return 0;
		}

		@Override
		public FontSource getFontSource() {
			return SOURCE;
		}
	};

	@Test
	void leadingAdjustmentMovesTheFirstOutline() {
		final GeneralPath baseline = outline(0);
		final GeneralPath adjusted = outline(3);
		assertEquals(baseline.getBounds2D().getMinX() + 3, adjusted.getBounds2D().getMinX(), 0.0001);
		assertEquals(baseline.getBounds2D().getWidth(), adjusted.getBounds2D().getWidth(), 0.0001);
	}

	@Test
	void horizontalFontOutlineFollowsRunDirection() {
		final var vertical = outline(FontStyle.Direction.TB, 3).getBounds2D();
		assertTrue(vertical.getHeight() > vertical.getWidth() * 2);
		assertTrue(Math.abs(vertical.getMinX()) <= 10 && Math.abs(vertical.getMaxX()) <= 10);
		assertEquals(-3, vertical.getMinX(), 0.0001, "BBox中央の補正を回転前のY方向へ適用する");

		final var horizontal = outline(FontStyle.Direction.LTR, 3).getBounds2D();
		assertTrue(horizontal.getWidth() > horizontal.getHeight() * 2);
	}

	private static GeneralPath outline(final double leadingAdjustment) {
		return outline(FontStyle.Direction.LTR, 1, leadingAdjustment);
	}

	private static GeneralPath outline(final FontStyle.Direction direction, final int glyphCount) {
		return outline(direction, glyphCount, 0);
	}

	private static GeneralPath outline(final FontStyle.Direction direction, final int glyphCount,
			final double leadingAdjustment) {
		final TextImpl text = new TextImpl(0,
				new FontStyleImpl(FontFamilyList.SERIF, 10, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
						direction, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE),
				METRICS);
		for (int i = 0; i < glyphCount; ++i) {
			text.appendGlyph(new char[] { 'a' }, 0, (byte) 1, 1);
		}
		text.pack();
		if (leadingAdjustment != 0) {
			text.addXAdvance(0, leadingAdjustment);
		}
		final GeneralPath path = new GeneralPath();
		FontUtils.addTextPath(path, FONT, text, new AffineTransform());
		return path;
	}

	private static Object defaultValue(final Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == char.class) {
			return '\0';
		}
		return 0;
	}
}
