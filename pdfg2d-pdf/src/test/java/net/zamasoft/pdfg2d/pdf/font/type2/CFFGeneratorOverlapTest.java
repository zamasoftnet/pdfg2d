package net.zamasoft.pdfg2d.pdf.font.type2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.PDFEmbeddedFont;

/**
 * Overlapping TrueType contours become one CFF contour (2026-10-29): Andika's "t", "k" and "W" kept the stem and the
 * bars as overlapping contours in the CFF, and Acrobat at small zoom, print RIPs and Chrome showing a paged SVG small
 * left the crossings white.
 */
public class CFFGeneratorOverlapTest {
	/** A "+": a bar and a stem drawn as two contours of the same orientation, as TrueType fonts may. */
	private static Shape cross() {
		final Path2D.Double p = new Path2D.Double(Path2D.WIND_NON_ZERO);
		p.append(new Rectangle2D.Double(0, -600, 600, 100), false);
		p.append(new Rectangle2D.Double(250, -850, 100, 600), false);
		return p;
	}

	@Test
	public void testOverlappingContoursAreMergedCounterClockwise() {
		final Shape merged = CFFGenerator.withoutOverlaps(cross());
		final List<Double> areas = signedAreasYUp(merged);
		assertEquals(1, areas.size(), "one contour: " + areas);
		// Counter-clockwise in CFF's y-up space; the union of 60000 and 60000 less the 10000 they share
		assertEquals(110000, areas.get(0), 0.5);
		assertTrue(merged.contains(300, -550));
		assertTrue(merged.contains(20, -550));
		assertTrue(merged.contains(300, -830));
		assertFalse(merged.contains(100, -800));
	}

	/**
	 * A contour with a deep curve whose polygon of points has the other orientation still counts as the same
	 * orientation as the square it overlaps (codex, 2026-10-29).
	 */
	@Test
	public void testOrientationFollowsTheCurves() {
		final Path2D.Double p = new Path2D.Double(Path2D.WIND_NON_ZERO);
		p.moveTo(0, 0);
		p.lineTo(50, -20);
		p.lineTo(100, 0);
		p.lineTo(100, 100);
		p.quadTo(50, -130, 0, 100);
		p.closePath();
		p.append(new Rectangle2D.Double(0, 10, 20, 20), false);
		final Shape merged = CFFGenerator.withoutOverlaps(p);
		assertEquals(1, signedAreasYUp(merged).size());
	}

	/** Cubic curves ending at fractions keep each written end within half a unit of the real one. */
	@Test
	public void testCubicEndsDoNotDrift() throws Exception {
		final Path2D.Double p = new Path2D.Double(Path2D.WIND_NON_ZERO);
		final List<double[]> ends = new ArrayList<>();
		double x = 0.4, y = -0.4;
		p.moveTo(x, y);
		ends.add(new double[] { x, y });
		for (int i = 0; i < 12; ++i) {
			p.curveTo(x + 3.3, y - 5.3, x + 7.3, y - 5.3, x + 10.4, y - 0.3);
			x += 10.4;
			y -= 0.3;
			ends.add(new double[] { x, y });
		}
		p.closePath();
		final List<double[]> written = points(generate(new StubFont(null, p)).get(1));
		assertEquals(ends.size(), written.size());
		for (int i = 0; i < ends.size(); ++i) {
			// CFF turns y up
			assertEquals(ends.get(i)[0], written.get(i)[0], 0.5, "x of end " + i);
			assertEquals(-ends.get(i)[1], written.get(i)[1], 0.5, "y of end " + i);
		}
	}

	@Test
	public void testHolesAndSeparateContoursAreKept() {
		final Path2D.Double ring = new Path2D.Double(Path2D.WIND_NON_ZERO);
		ring.moveTo(0, 0);
		ring.lineTo(500, 0);
		ring.lineTo(500, -500);
		ring.lineTo(0, -500);
		ring.closePath();
		ring.moveTo(100, -100);
		ring.lineTo(100, -400);
		ring.lineTo(400, -400);
		ring.lineTo(400, -100);
		ring.closePath();
		assertSame(ring, CFFGenerator.withoutOverlaps(ring));

		final Path2D.Double colon = new Path2D.Double(Path2D.WIND_NON_ZERO);
		colon.append(new Rectangle2D.Double(0, -500, 100, 100), false);
		colon.append(new Rectangle2D.Double(0, -100, 100, 100), false);
		assertSame(colon, CFFGenerator.withoutOverlaps(colon));
	}

	/** The CFF has one contour for the cross, and its charset names as many glyphs as its CharStrings hold. */
	@Test
	public void testGeneratedCFF() throws Exception {
		final CFFGenerator gen = new CFFGenerator();
		gen.setSubsetName("ABCDEF+Stub");
		gen.setEmbedableFont(new StubFont(null, cross(), new Rectangle2D.Double(0, -500, 500, 500)));
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		gen.writeTo(out);
		final byte[] b = out.toByteArray();
		final int charset = topDictOperand(b, 15);
		final List<byte[]> glyphs = generate(b);
		assertEquals(3, glyphs.size());
		assertEquals(2, b[charset]); // format 2
		final int first = card16(b, charset + 1), nLeft = card16(b, charset + 3);
		assertEquals(1, first);
		assertEquals(glyphs.size() - 1, first + nLeft, "the last GID the charset names");
		assertEquals(1, moveTos(glyphs.get(1)), "the cross is one contour");
		assertEquals(1, moveTos(glyphs.get(2)));
	}

	/** The CharStrings of a font generated by {@link CFFGenerator}. */
	private static List<byte[]> generate(final PDFEmbeddedFont font) throws Exception {
		final CFFGenerator gen = new CFFGenerator();
		gen.setSubsetName("ABCDEF+Stub");
		gen.setEmbedableFont(font);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		gen.writeTo(out);
		return generate(out.toByteArray());
	}

	/** The CharStrings INDEX of a CFF. */
	private static List<byte[]> generate(final byte[] b) {
		final int[] pos = { topDictOperand(b, 17) };
		return index(b, pos);
	}

	/** The last operand of an operator of the Top DICT. */
	private static int topDictOperand(final byte[] b, final int operator) {
		final int[] pos = { b[2] & 0xFF };
		index(b, pos); // Name INDEX
		final byte[] topDict = index(b, pos).get(0);
		final List<Integer> operands = new ArrayList<>();
		for (int i = 0; i < topDict.length;) {
			final int b0 = topDict[i] & 0xFF;
			if (b0 <= 21) {
				final int op = b0 == 12 ? 1200 + (topDict[i + 1] & 0xFF) : b0;
				i += b0 == 12 ? 2 : 1;
				if (op == operator) {
					return operands.get(operands.size() - 1);
				}
				operands.clear();
			} else if (b0 == 28) {
				operands.add((int) (short) (((topDict[i + 1] & 0xFF) << 8) | (topDict[i + 2] & 0xFF)));
				i += 3;
			} else if (b0 == 29) {
				operands.add(((topDict[i + 1] & 0xFF) << 24) | ((topDict[i + 2] & 0xFF) << 16)
						| ((topDict[i + 3] & 0xFF) << 8) | (topDict[i + 4] & 0xFF));
				i += 5;
			} else if (b0 <= 246) {
				operands.add(b0 - 139);
				i += 1;
			} else if (b0 <= 250) {
				operands.add((b0 - 247) * 256 + (topDict[i + 1] & 0xFF) + 108);
				i += 2;
			} else {
				operands.add(-(b0 - 251) * 256 - (topDict[i + 1] & 0xFF) - 108);
				i += 2;
			}
		}
		throw new AssertionError("no operator " + operator);
	}

	/**
	 * The points a Type 2 charstring without hints and widths moves and draws to, in order: the start of each contour
	 * and the end of each segment.
	 */
	private static List<double[]> points(final byte[] cs) {
		final List<double[]> out = new ArrayList<>();
		final List<Integer> args = new ArrayList<>();
		int x = 0, y = 0;
		for (int i = 0; i < cs.length;) {
			final int b0 = cs[i] & 0xFF;
			if (b0 == 28) {
				args.add((int) (short) (((cs[i + 1] & 0xFF) << 8) | (cs[i + 2] & 0xFF)));
				i += 3;
			} else if (b0 >= 32 && b0 <= 246) {
				args.add(b0 - 139);
				i += 1;
			} else if (b0 >= 247 && b0 <= 250) {
				args.add((b0 - 247) * 256 + (cs[i + 1] & 0xFF) + 108);
				i += 2;
			} else if (b0 >= 251 && b0 <= 254) {
				args.add(-(b0 - 251) * 256 - (cs[i + 1] & 0xFF) - 108);
				i += 2;
			} else {
				switch (b0) {
					case 21, 5 -> { // rmoveto, rlineto
						x += args.get(0);
						y += args.get(1);
					}
					case 22, 6 -> x += args.get(0); // hmoveto, hlineto
					case 4, 7 -> y += args.get(0); // vmoveto, vlineto
					case 8 -> { // rrcurveto
						for (int k = 0; k < 6; k += 2) {
							x += args.get(k);
							y += args.get(k + 1);
						}
					}
					case 14 -> {
						return out;
					}
					default -> throw new AssertionError("operator " + b0);
				}
				out.add(new double[] { x, y });
				args.clear();
				i += 1;
			}
		}
		return out;
	}

	private static int card16(final byte[] b, final int i) {
		return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
	}

	/** Reads the INDEX at {@code pos[0]} and moves {@code pos[0]} past it. */
	private static List<byte[]> index(final byte[] b, final int[] pos) {
		final int count = card16(b, pos[0]);
		final List<byte[]> items = new ArrayList<>();
		if (count == 0) {
			pos[0] += 2;
			return items;
		}
		final int offSize = b[pos[0] + 2] & 0xFF;
		final int offsets = pos[0] + 3;
		final int base = offsets + (count + 1) * offSize - 1;
		int prev = offset(b, offsets, offSize);
		for (int i = 1; i <= count; ++i) {
			final int next = offset(b, offsets + i * offSize, offSize);
			final byte[] item = new byte[next - prev];
			System.arraycopy(b, base + prev, item, 0, item.length);
			items.add(item);
			prev = next;
		}
		pos[0] = base + prev;
		return items;
	}

	private static int offset(final byte[] b, final int at, final int size) {
		int v = 0;
		for (int i = 0; i < size; ++i) {
			v = (v << 8) | (b[at + i] & 0xFF);
		}
		return v;
	}

	/** The number of rmoveto, hmoveto and vmoveto in a Type 2 charstring without hints. */
	private static int moveTos(final byte[] cs) {
		int moves = 0;
		for (int i = 0; i < cs.length;) {
			final int b0 = cs[i] & 0xFF;
			if (b0 == 28) {
				i += 3;
			} else if (b0 == 255) {
				i += 5;
			} else if (b0 >= 247) {
				i += 2;
			} else if (b0 >= 32) {
				i += 1;
			} else {
				if (b0 == 21 || b0 == 22 || b0 == 4) {
					++moves;
				}
				i += b0 == 12 ? 2 : 1;
			}
		}
		return moves;
	}

	/** Signed area of each contour with y turned up (positive is counter-clockwise), by its points. */
	private static List<Double> signedAreasYUp(final Shape s) {
		final List<Double> out = new ArrayList<>();
		double sum = 0, sx = 0, sy = 0, px = 0, py = 0;
		boolean open = false;
		final double[] c = new double[6];
		for (final PathIterator i = s.getPathIterator(null, 0.1); !i.isDone(); i.next()) {
			final int type = i.currentSegment(c);
			final double x = c[0], y = -c[1];
			if (type == PathIterator.SEG_MOVETO) {
				if (open) {
					out.add((sum + px * sy - sx * py) / 2);
				}
				sum = 0;
				sx = px = x;
				sy = py = y;
				open = true;
			} else if (type == PathIterator.SEG_LINETO) {
				sum += px * y - x * py;
				px = x;
				py = y;
			}
		}
		if (open) {
			out.add((sum + px * sy - sx * py) / 2);
		}
		return out;
	}

	private static final class StubFont implements PDFEmbeddedFont {
		private static final long serialVersionUID = 1L;

		private final Shape[] shapes;

		StubFont(final Shape... shapes) {
			this.shapes = shapes;
		}

		public Shape getShape(final int gid) {
			return this.shapes[gid];
		}

		public byte[] getCharString(final int gid) {
			return null;
		}

		public int getGlyphCount() {
			return this.shapes.length;
		}

		public int getCharCount() {
			return this.shapes.length;
		}

		public BBox getBBox() {
			return new BBox((short) 0, (short) -200, (short) 1000, (short) 900);
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

		public int toGID(final int c) {
			return c;
		}

		public short getAdvance(final int gid) {
			return 1000;
		}

		public short getWidth(final int gid) {
			return 1000;
		}

		public short getKerning(final int sgid, final int gid) {
			return 0;
		}

		public int getLigature(final int gid, final int cid) {
			return -1;
		}

		public void drawTo(final GC gc, final Text text) {
		}

		public void writeTo(final PDFFragmentOutput out, final XRef xref) {
		}
	}
}
