package net.zamasoft.pdfg2d.pdf.font.type2;

import java.awt.Shape;
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

	private static final boolean DEBUG = false;

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
	 * Writes the complete CFF binary data to the given output stream.
	 *
	 * @param out the output stream to receive the CFF data
	 * @throws IOException if an I/O error occurs while writing
	 */
	public void writeTo(OutputStream out) throws IOException {
		@SuppressWarnings("resource")
		final CFFOutputStream cout = new CFFOutputStream(out);
		BBox bbox = this.font.getBBox();
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

				cout1.writeCard8((byte) 2); // format=2
				cout1.writeCard16(1);
				cout1.writeCard16(this.font.getCharCount() - 1);

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

						final PathIterator j = shape.getPathIterator(null);
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
										if (DEBUG) {
											System.err.println("vmoveto " + dy);
										}
										cy += dy;
									} else if (dy == 0) {
										tout3.writeShort(dx);
										tout3.writeOperator(Type2OutputStream.HMOVETO);
										if (DEBUG) {
											System.err.println("hmoveto " + dx);
										}
										cx += dx;
									} else {
										tout3.writeShort(dx);
										tout3.writeShort(dy);
										tout3.writeOperator(Type2OutputStream.RMOVETO);
										if (DEBUG) {
											System.err.println("rmoveto " + dx + " " + dy);
										}
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
										if (DEBUG) {
											System.err.println("hmoveto " + 0);
										}
										closed = false;
									}
									double x = cord[0];
									double y = -cord[1];
									short dx = (short) Math.round(x - cx);
									short dy = (short) Math.round(y - cy);
									if (dx == 0) {
										tout3.writeShort(dy);
										tout3.writeOperator(Type2OutputStream.VLINETO);
										if (DEBUG) {
											System.err.println("vlineto " + dy);
										}
										cy += dy;
									} else if (dy == 0) {
										tout3.writeShort(dx);
										tout3.writeOperator(Type2OutputStream.HLINETO);
										if (DEBUG) {
											System.err.println("hlineto " + dx);
										}
										cx += dx;
									} else {
										tout3.writeShort(dx);
										tout3.writeShort(dy);
										tout3.writeOperator(Type2OutputStream.RLINETO);
										if (DEBUG) {
											System.err.println("rlineto " + dx + " " + dy);
										}
										cx += dx;
										cy += dy;
									}
								}
									break;

								case PathIterator.SEG_QUADTO: {
									if (closed) {
										tout3.writeShort((short) 0);
										tout3.writeOperator(Type2OutputStream.HMOVETO);
										if (DEBUG) {
											System.err.println("hmoveto " + 0);
										}
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
									if (DEBUG) {
										System.err.println("rrcurveto " + dxa + " " + dya + " " + dxb + " " + dyb + " "
												+ dxc + " " + dyc);
									}
								}
									break;

								case PathIterator.SEG_CUBICTO: {
									if (closed) {
										tout3.writeShort((short) 0);
										tout3.writeOperator(Type2OutputStream.HMOVETO);
										if (DEBUG) {
											System.err.println("hmoveto " + 0);
										}
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
									tout3.writeOperator(Type2OutputStream.RRCURVETO);
									if (DEBUG) {
										System.err.println("rrcurveto " + dxa + " " + dya + " " + dxb + " " + dyb + " "
												+ dxc + " " + dyc);
									}
									cx = xc;
									cy = yc;
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
