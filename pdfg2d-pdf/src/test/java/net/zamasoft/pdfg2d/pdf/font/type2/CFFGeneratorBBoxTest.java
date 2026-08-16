package net.zamasoft.pdfg2d.pdf.font.type2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.PDFEmbeddedFont;

public class CFFGeneratorBBoxTest {

	private static final BBox SOURCE_BBOX = new BBox((short) -1000, (short) -1000, (short) 3000, (short) 2000);

	private static final class StubFont implements PDFEmbeddedFont {
		private static final long serialVersionUID = 1L;

		private final Shape[] shapes;
		private final byte[][] charStrings;

		StubFont(Shape... shapes) {
			this(shapes, new byte[shapes.length][]);
		}

		StubFont(Shape[] shapes, byte[][] charStrings) {
			this.shapes = shapes;
			this.charStrings = charStrings;
		}

		public Shape getShape(int gid) {
			return this.shapes[gid];
		}

		public byte[] getCharString(int gid) {
			return this.charStrings[gid];
		}

		public int getGlyphCount() {
			return this.shapes.length;
		}

		public int getCharCount() {
			return this.shapes.length;
		}

		public BBox getBBox() {
			return SOURCE_BBOX;
		}

		public String getPSName() {
			return "Stub";
		}

		public String getRegistry() {
			return "Adobe";
		}

		public String getOrdering() {
			return "Identity";
		}

		public int getSupplement() {
			return 0;
		}

		public String getName() {
			return "F0";
		}

		public FontSource getFontSource() {
			return null;
		}

		public int toGID(int c) {
			return c;
		}

		public short getAdvance(int gid) {
			return 1000;
		}

		public short getWidth(int gid) {
			return 1000;
		}

		public short getKerning(int sgid, int gid) {
			return 0;
		}

		public int getLigature(int gid, int cid) {
			return -1;
		}

		public void drawTo(GC gc, Text text) {
		}

		public void writeTo(PDFFragmentOutput out, XRef xref) {
		}
	}

	@Test
	public void testCalculatesSubsetBoxAndConvertsJava2DYAxis() {
		final var first = new Rectangle2D.Double(10.25, -30.75, 80.5, 120.25);
		final var second = new Rectangle2D.Double(-5.5, 20.25, 50.25, 40.5);

		assertEquals(new BBox((short) -6, (short) -90, (short) 91, (short) 31),
				CFFGenerator.calculateSubsetBBox(new StubFont(first, second)));
	}

	@Test
	public void testEmptyGlyphsDoNotExpandSubsetBox() {
		final var empty = new Path2D.Double();
		final var visible = new Rectangle2D.Double(100, -800, 800, 900);

		assertEquals(new BBox((short) 100, (short) -100, (short) 900, (short) 800),
				CFFGenerator.calculateSubsetBBox(new StubFont(null, empty, visible)));
	}

	@Test
	public void testFallsBackWhenOnlySourceBoxIsAvailable() {
		assertEquals(SOURCE_BBOX, CFFGenerator.calculateSubsetBBox(new StubFont((Shape) null)));
	}

	@Test
	public void testFallsBackForOpaqueCharString() {
		final Shape[] shapes = { new Rectangle2D.Double(0, 0, 500, 500), null };
		final byte[][] charStrings = { null, new byte[] { 14 } };
		assertEquals(SOURCE_BBOX, CFFGenerator.calculateSubsetBBox(new StubFont(shapes, charStrings)));
	}
}
