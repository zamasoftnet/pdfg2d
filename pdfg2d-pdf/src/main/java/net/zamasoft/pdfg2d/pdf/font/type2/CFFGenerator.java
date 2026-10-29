package net.zamasoft.pdfg2d.pdf.font.type2;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.pdf.font.PDFEmbeddedFont;

/**
 * Generates a CFF (Compact Font Format / Adobe Type 2) binary stream from a
 * {@link PDFEmbeddedFont} subset.  The output is suitable for embedding as a
 * {@code FontFile3} stream with subtype {@code CIDFontType0C} inside a PDF
 * font descriptor.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CFFGenerator {
	protected String subsetName;

	protected PDFEmbeddedFont font;

	protected BBox bbox;

	/**
	 * Sets the subset name (the six-letter tag + PostScript name) that will be
	 * written into the CFF Name INDEX, e.g. {@code "ABCDEF+FontName"}.
	 *
	 * @param subsetName the subset-qualified font name
	 */
	public void setSubsetName(String subsetName) {
		this.subsetName = subsetName;
	}

	/**
	 * Sets the embedded font whose glyph data will be written into the CFF stream.
	 *
	 * @param font the font to embed
	 */
	public void setEmbedableFont(PDFEmbeddedFont font) {
		this.font = font;
	}

	/**
	 * Sets the bounding box written to the CFF Top DICT. When omitted, the
	 * bounding box is calculated from the glyphs in the embedded subset.
	 *
	 * @param bbox the subset bounding box in glyph-space units
	 */
	public void setBBox(BBox bbox) {
		this.bbox = bbox;
	}

	/**
	 * Calculates a bounding box for the glyphs actually present in an embedded
	 * subset. OpenType and AWT glyph outlines use the Java2D convention where Y
	 * increases downwards; CFF charstrings invert Y while they are written, so the
	 * returned box applies the same conversion.
	 *
	 * <p>
	 * Empty glyphs such as spaces do not affect the box. If an outline cannot be
	 * inspected, the source font box is returned so that the generated CFF never
	 * clips an unknown glyph.
	 * </p>
	 *
	 * @param font the embedded subset font
	 * @return a box enclosing the subset glyph outlines
	 */
	public static BBox calculateSubsetBBox(PDFEmbeddedFont font) {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		boolean found = false;

		for (int gid = 0; gid < font.getGlyphCount(); ++gid) {
			final Shape shape = font.getShape(gid);
			if (shape == null) {
				if (font.getCharString(gid) != null) {
					return font.getBBox();
				}
				continue;
			}
			final var bounds = shape.getBounds2D();
			if (bounds.isEmpty()) {
				continue;
			}
			final double x1 = bounds.getMinX();
			final double y1 = -bounds.getMaxY();
			final double x2 = bounds.getMaxX();
			final double y2 = -bounds.getMinY();
			if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2)
					|| !Double.isFinite(y2)) {
				return font.getBBox();
			}
			minX = Math.min(minX, x1);
			minY = Math.min(minY, y1);
			maxX = Math.max(maxX, x2);
			maxY = Math.max(maxY, y2);
			found = true;
		}

		if (!found) {
			return font.getBBox();
		}
		return new BBox(toShort(Math.floor(minX)), toShort(Math.floor(minY)), toShort(Math.ceil(maxX)),
				toShort(Math.ceil(maxY)));
	}

	private static short toShort(double value) {
		return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
	}

	/**
	 * Returns the outline with its overlapping contours merged. TrueType glyphs may overlap (Andika draws the stem
	 * and the bar of "t" as two contours), while CFF outlines are expected not to: some rasterizers left the overlap
	 * white (Acrobat at small zoom, print RIPs, Chrome showing a paged SVG small; 2026-10-29).
	 *
	 * <p>
	 * Only an outline in which two contours of the same orientation have intersecting bounds is merged, by the
	 * nonzero rule CFF fills with. {@link Area} gives its outer contours counter-clockwise in the y-up space of CFF,
	 * as CFF expects. Other outlines, holes inside a contour among them, are returned as they are.
	 * </p>
	 *
	 * @param shape a glyph outline in Java2D coordinates
	 * @return the outline without overlapping contours
	 */
	static Shape withoutOverlaps(final Shape shape) {
		if (!mayOverlap(shape)) {
			return shape;
		}
		final Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
		path.append(shape, false);
		return new Area(path);
	}

	/**
	 * Whether two contours of the same orientation have intersecting bounds. The bounds take the control points
	 * (they hold the curve); the orientation is the sign of the exact area of the segments, as the polygon of the
	 * points can have the other sign for a contour with deep curves.
	 */
	private static boolean mayOverlap(final Shape shape) {
		final List<double[]> contours = new ArrayList<>();
		// minX, minY, maxX, maxY, the signed area
		double[] c = null;
		double px = 0, py = 0, sx = 0, sy = 0;
		final double[] cord = new double[6];
		for (final PathIterator i = shape.getPathIterator(null); !i.isDone(); i.next()) {
			final int type = i.currentSegment(cord);
			if (type == PathIterator.SEG_CLOSE) {
				continue;
			}
			if (type == PathIterator.SEG_MOVETO) {
				if (c != null) {
					c[4] += cross(px, py, sx, sy) / 2;
					contours.add(c);
				}
				c = new double[] { cord[0], cord[1], cord[0], cord[1], 0 };
				px = sx = cord[0];
				py = sy = cord[1];
				continue;
			}
			final int points = type == PathIterator.SEG_QUADTO ? 2 : type == PathIterator.SEG_CUBICTO ? 3 : 1;
			for (int k = 0; k < points; ++k) {
				c[0] = Math.min(c[0], cord[k * 2]);
				c[1] = Math.min(c[1], cord[k * 2 + 1]);
				c[2] = Math.max(c[2], cord[k * 2]);
				c[3] = Math.max(c[3], cord[k * 2 + 1]);
			}
			// The area between the segment and the origin (Green's theorem on the Bezier forms)
			final double x1 = cord[0], y1 = cord[1], x2 = cord[2], y2 = cord[3], x3 = cord[4], y3 = cord[5];
			switch (type) {
				case PathIterator.SEG_QUADTO:
					c[4] += (2 * cross(px, py, x1, y1) + 2 * cross(x1, y1, x2, y2) + cross(px, py, x2, y2)) / 6;
					px = x2;
					py = y2;
					break;
				case PathIterator.SEG_CUBICTO:
					c[4] += (6 * cross(px, py, x1, y1) + 3 * cross(px, py, x2, y2) + cross(px, py, x3, y3)
							+ 3 * cross(x1, y1, x2, y2) + 3 * cross(x1, y1, x3, y3) + 6 * cross(x2, y2, x3, y3)) / 20;
					px = x3;
					py = y3;
					break;
				default:
					c[4] += cross(px, py, x1, y1) / 2;
					px = x1;
					py = y1;
					break;
			}
		}
		if (c != null) {
			c[4] += cross(px, py, sx, sy) / 2;
			contours.add(c);
		}
		for (int a = 0; a < contours.size(); ++a) {
			final double[] ca = contours.get(a);
			for (int b = a + 1; b < contours.size(); ++b) {
				final double[] cb = contours.get(b);
				if (Math.signum(ca[4]) == Math.signum(cb[4]) && ca[0] < cb[2] && cb[0] < ca[2] && ca[1] < cb[3]
						&& cb[1] < ca[3]) {
					return true;
				}
			}
		}
		return false;
	}

	private static double cross(final double ax, final double ay, final double bx, final double by) {
		return ax * by - bx * ay;
	}

	/**
	 * Writes the complete CFF binary data to the given output stream.
	 *
	 * @param out the output stream to receive the CFF data
	 * @throws IOException if an I/O error occurs while writing
	 */
	public void writeTo(OutputStream out) throws IOException {
		@SuppressWarnings("resource")
		final CFFOutputStream cout = new CFFOutputStream(out);
		BBox bbox = this.bbox != null ? this.bbox : calculateSubsetBBox(this.font);
		short defaultWidth = this.font.getWidth(0);

		// Header
		cout.writeHeader((byte) 1, (byte) 0, (byte) 4, (byte) 2);

		// Name INDEX
		// Note: Must be sorted by name
		byte[][] nameIndex = { CFFOutputStream.toBytes(this.subsetName) };
		cout.writeIndex(nameIndex, (byte) 1);

		int offset = cout.getOffset();

		// Content immediately after Top DICT
		byte[] afterTopDict;
		try (ByteArrayOutputStream bout1 = new ByteArrayOutputStream();
				CFFOutputStream cout1 = new CFFOutputStream(bout1)) {
			// String INDEX
			byte[][] stringIndex = { CFFOutputStream.toBytes(this.font.getRegistry()),
					CFFOutputStream.toBytes(this.font.getOrdering()) };
			cout1.writeIndex(stringIndex, (byte) 1);

			// Global Subrs INDEX
			cout1.writeCard16((short) 0);

			afterTopDict = bout1.toByteArray();
		}

		offset += afterTopDict.length;

		try (ByteArrayOutputStream bout1 = new ByteArrayOutputStream();
				CFFOutputStream cout1 = new CFFOutputStream(bout1)) {

			int padding1 = 0;

			// Top DICT INDEX
			try (ByteArrayOutputStream bout2 = new ByteArrayOutputStream();
					CFFOutputStream cout2 = new CFFOutputStream(bout2)) {

				// ROS
				cout2.writeNSSID(0); // Registry
				cout2.writeNSSID(1); // Ordering
				cout2.writeInteger(this.font.getSupplement());
				cout2.writeOperator(CFFOutputStream.ROS);

				// FontBBox
				cout2.writeInteger(bbox.llx());
				cout2.writeInteger(bbox.lly());
				cout2.writeInteger(bbox.urx());
				cout2.writeInteger(bbox.ury());
				cout2.writeOperator(CFFOutputStream.FONT_BBOX);

				// CIDCount
				cout2.writeInteger(0xFFFF);
				cout2.writeOperator(CFFOutputStream.CID_COUNT);

				offset += cout2.getOffset();
				offset += 5;// header
				offset += 6;// Charsets
				offset += 6;// CharStrings
				offset += 7;// FDSelect
				offset += 7;// FDArray

				// Charsets
				padding1 += 5 - cout2.writeInteger(offset + cout1.getOffset());
				cout2.writeOperator(CFFOutputStream.CHARSETS);

				// GIDs 1 to count - 1; nLeft does not count the first one (it named one glyph too many)
				if (this.font.getGlyphCount() > 1) {
					cout1.writeCard8((byte) 2); // format=2
					cout1.writeCard16(1);
					cout1.writeCard16(this.font.getGlyphCount() - 2);
				} else {
					cout1.writeCard8((byte) 0); // format=0, .notdef alone
				}

				// CharStrings INDEX
				padding1 += 5 - cout2.writeInteger(offset + cout1.getOffset());
				cout2.writeOperator(CFFOutputStream.CHAR_STRINGS);

				final List<byte[]> fonts = new ArrayList<byte[]>();
				for (int i = 0; i < this.font.getGlyphCount(); ++i) {
					short width = this.font.getWidth(i);
					{
						final byte[] charString = this.font.getCharString(i);
						if (charString != null) {
							if (defaultWidth != width) {
								final ByteArrayOutputStream bout3 = new ByteArrayOutputStream();
								final Type2OutputStream tout3 = new Type2OutputStream(bout3);
								tout3.writeShort((short) (width - defaultWidth));
								tout3.write(charString);
								tout3.close();
								fonts.add(bout3.toByteArray());
							} else {
								fonts.add(charString);
							}
							continue;
						}
					}
					final Shape shape = this.font.getShape(i);
					if (shape == null) {
						// Outline-less glyph (e.g. space). The width operand
						// must still be encoded, otherwise the glyph falls
						// back to defaultWidthX and no longer matches the /W
						// array (PDF/A 6.3.6 width consistency).
						if (defaultWidth != width) {
							final ByteArrayOutputStream bout3 = new ByteArrayOutputStream();
							try (final Type2OutputStream tout3 = new Type2OutputStream(bout3)) {
								tout3.writeShort((short) (width - defaultWidth));
								tout3.writeOperator(Type2OutputStream.ENDCHAR);
							}
							fonts.add(bout3.toByteArray());
						} else {
							fonts.add(Type2OutputStream.ENDCHAR);
						}
						continue;
					}
					try (final ByteArrayOutputStream bout3 = new ByteArrayOutputStream();
							final Type2OutputStream tout3 = new Type2OutputStream(bout3)) {
						if (defaultWidth != width) {
							tout3.writeShort((short) (width - defaultWidth));
						}

						final PathIterator j = withoutOverlaps(shape).getPathIterator(null);
						double cx = 0, cy = 0;
						boolean closed = true;
						final double[] cord = new double[6];
						LOOP: while (!j.isDone()) {
							int type = j.currentSegment(cord);
							switch (type) {
								case PathIterator.SEG_MOVETO: {
									double x = cord[0];
									double y = -cord[1];
									short dx = (short) Math.round(x - cx);
									short dy = (short) Math.round(y - cy);
									if (dx == 0) {
										tout3.writeShort(dy);
										tout3.writeOperator(Type2OutputStream.VMOVETO);
										cy += dy;
									} else if (dy == 0) {
										tout3.writeShort(dx);
										tout3.writeOperator(Type2OutputStream.HMOVETO);
										cx += dx;
									} else {
										tout3.writeShort(dx);
										tout3.writeShort(dy);
										tout3.writeOperator(Type2OutputStream.RMOVETO);
										cx += dx;
										cy += dy;
									}
									closed = false;
								}
									break;

								case PathIterator.SEG_LINETO: {
									if (closed) {
										tout3.writeShort((short) 0);
										tout3.writeOperator(Type2OutputStream.HMOVETO);
										closed = false;
									}
									double x = cord[0];
									double y = -cord[1];
									short dx = (short) Math.round(x - cx);
									short dy = (short) Math.round(y - cy);
									if (dx == 0) {
										tout3.writeShort(dy);
										tout3.writeOperator(Type2OutputStream.VLINETO);
										cy += dy;
									} else if (dy == 0) {
										tout3.writeShort(dx);
										tout3.writeOperator(Type2OutputStream.HLINETO);
										cx += dx;
									} else {
										tout3.writeShort(dx);
										tout3.writeShort(dy);
										tout3.writeOperator(Type2OutputStream.RLINETO);
										cx += dx;
										cy += dy;
									}
								}
									break;

								case PathIterator.SEG_QUADTO: {
									if (closed) {
										tout3.writeShort((short) 0);
										tout3.writeOperator(Type2OutputStream.HMOVETO);
										closed = false;
									}
									double x1 = cord[0];
									double y1 = -cord[1];
									double x2 = cord[2];
									double y2 = -cord[3];
									double xa = (cx + 2f * (x1 - cx) / 3f);
									double ya = (cy + 2f * (y1 - cy) / 3f);
									double xb = (xa + (x2 - cx) / 3f);
									double yb = (ya + (y2 - cy) / 3f);
									double xc = x2;
									double yc = y2;
									short dxa = (short) Math.round(xa - cx);
									short dya = (short) Math.round(ya - cy);
									cx += dxa;
									cy += dya;
									short dxb = (short) Math.round(xb - cx);
									short dyb = (short) Math.round(yb - cy);
									cx += dxb;
									cy += dyb;
									short dxc = (short) Math.round(xc - cx);
									short dyc = (short) Math.round(yc - cy);
									cx += dxc;
									cy += dyc;
									tout3.writeShort(dxa);
									tout3.writeShort(dya);
									tout3.writeShort(dxb);
									tout3.writeShort(dyb);
									tout3.writeShort(dxc);
									tout3.writeShort(dyc);
									tout3.writeOperator(Type2OutputStream.RRCURVETO);
								}
									break;

								case PathIterator.SEG_CUBICTO: {
									if (closed) {
										tout3.writeShort((short) 0);
										tout3.writeOperator(Type2OutputStream.HMOVETO);
										closed = false;
									}
									double xa = cord[0];
									double ya = -cord[1];
									double xb = cord[2];
									double yb = -cord[3];
									double xc = cord[4];
									double yc = -cord[5];
									short dxa = (short) Math.round(xa - cx);
									short dya = (short) Math.round(ya - cy);
									cx += dxa;
									cy += dya;
									short dxb = (short) Math.round(xb - cx);
									short dyb = (short) Math.round(yb - cy);
									cx += dxb;
									cy += dyb;
									short dxc = (short) Math.round(xc - cx);
									short dyc = (short) Math.round(yc - cy);
									cx += dxc;
									cy += dyc;
									tout3.writeShort(dxa);
									tout3.writeShort(dya);
									tout3.writeShort(dxb);
									tout3.writeShort(dyb);
									tout3.writeShort(dxc);
									tout3.writeShort(dyc);
									// cx and cy stay the rounded point written; setting them to the exact end made the
									// following relative points drift (2026-10-29)
									tout3.writeOperator(Type2OutputStream.RRCURVETO);
								}
									break;

								case PathIterator.SEG_CLOSE:
									j.next();
									if (j.isDone()) {
										break LOOP;
									}
									closed = true;
									continue;
							}
							j.next();
						}
						tout3.writeOperator(Type2OutputStream.ENDCHAR);
						byte[] charString = bout3.toByteArray();
						fonts.add(charString);
					}
				}
				cout1.writeIndex((byte[][]) fonts.toArray(new byte[fonts.size()][]), (byte) 4);

				// FDSelect
				padding1 += 5 - cout2.writeInteger(offset + cout1.getOffset());
				cout2.writeOperator(CFFOutputStream.FD_SELECT);

				cout1.writeCard8((byte) 3);
				cout1.writeCard16((short) 1);
				cout1.writeCard16((short) 0);
				cout1.writeCard8((byte) 0);
				cout1.writeCard16((short) this.font.getGlyphCount());

				// Font DICT INDEX
				padding1 += 5 - cout2.writeInteger(offset + cout1.getOffset());
				cout2.writeOperator(CFFOutputStream.FD_ARRAY);

				try (ByteArrayOutputStream bout3 = new ByteArrayOutputStream();
						CFFOutputStream cout3 = new CFFOutputStream(bout3)) {

					// Private DICT
					byte[] privateDict;
					try (ByteArrayOutputStream bout4 = new ByteArrayOutputStream();
							CFFOutputStream cout4 = new CFFOutputStream(bout4)) {
						cout4.writeInteger(1);
						cout4.writeOperator(CFFOutputStream.LANGUAGE_GROUP);

						cout4.writeInteger(defaultWidth);
						cout4.writeOperator(CFFOutputStream.DEFAULT_WIDTHX);

						cout4.writeInteger(defaultWidth);
						cout4.writeOperator(CFFOutputStream.NOMINAL_WIDTHX);

						privateDict = bout4.toByteArray();
					}

					cout3.writeInteger(privateDict.length);
					int padding2 = 5 - cout3.writeInteger(offset + cout1.getOffset() + 5 + cout3.getOffset() + 6);
					cout3.writeOperator(CFFOutputStream.PRIVATE);

					cout1.writeIndex(new byte[][] { bout3.toByteArray() }, (byte) 1);

					// Padding to align references
					for (int i = 0; i < padding2; ++i) {
						cout1.write(0);
					}

					cout1.write(privateDict);
				}

				cout.writeIndex(new byte[][] { bout2.toByteArray() }, (byte) 1);
			}

			cout.write(afterTopDict);

			// Padding to align references
			for (int i = 0; i < padding1; ++i) {
				cout.write(0);
			}

			cout.write(bout1.toByteArray());
		}
		cout.flush();
	}
}
