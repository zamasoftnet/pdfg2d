package net.zamasoft.pdfg2d.font.cff;

import java.awt.geom.GeneralPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import net.zamasoft.pdfg2d.font.Glyph;

/**
 * Parser and renderer for CFF (Compact Font Format) Type 2 charstrings.
 * <p>
 * A Type 2 charstring encodes a glyph outline as a sequence of operators and
 * operands.  This class reads the byte stream from a {@link RandomAccessFile},
 * interprets each operator (move-to, line-to, curve-to, hints, subroutine
 * calls, etc.) and produces two outputs simultaneously:
 * <ul>
 *   <li>A {@link java.awt.geom.GeneralPath} describing the outline geometry.</li>
 *   <li>A cleaned byte array that strips hint masks already consumed during
 *       parsing, suitable for embedding in an output PDF.</li>
 * </ul>
 * </p>
 * <p>
 * The operator constants defined here correspond to the byte values specified
 * in the Adobe Type 2 Charstring specification.  Two-byte operators (escape
 * sequences starting with {@code 0x0C}) are encoded as {@code 0x0C00 | b1}.
 * </p>
 * <p>
 * Instances are <em>not</em> thread-safe on their own; however, {@link #getGlyph}
 * synchronises on the shared {@code RandomAccessFile} so multiple callers may
 * share one {@code Type2CharString} instance backed by the same file.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class Type2CharString {
	public static final int HSTEM = 0x0001;

	public static final int VSTEM = 0x0003;

	public static final int VMOVETO = 0x0004;

	public static final int RLINETO = 0x0005;

	public static final int HLINETO = 0x0006;

	public static final int VLINETO = 0x0007;

	public static final int RRCURVETO = 0x0008;

	public static final int CALLSUBR = 0x000A;

	public static final int RETURN = 0x000B;

	public static final int ESCAPE = 0x000C;

	public static final int ENDCHAR = 0x000E;

	public static final int BLEND = 0x0010;

	public static final int HSTEMHM = 0x0012;

	public static final int HINTMASK = 0x0013;

	public static final int CNTRMASK = 0x0014;

	public static final int RMOVETO = 0x0015;

	public static final int HMOVETO = 0x0016;

	public static final int VSTEMHM = 0x0017;

	public static final int RCURVELINE = 0x0018;

	public static final int RLINECURVE = 0x0019;

	public static final int VVCURVETO = 0x001A;

	public static final int HHCURVETO = 0x001B;

	public static final int SHORTINT = 0x001C;

	public static final int CALLGSUBR = 0x001D;

	public static final int VHCURVETO = 0x001E;

	public static final int HVCURVETO = 0x001F;

	public static final int AND = 0x0C03;

	public static final int OR = 0x0C04;

	public static final int NOT = 0x0C05;

	public static final int STORE = 0x0C08;

	public static final int ABS = 0x0C09;

	public static final int ADD = 0x0C0A;

	public static final int SUB = 0x0C0B;

	public static final int DIV = 0x0C0C;

	public static final int LOAD = 0x0C0D;

	public static final int NEG = 0x0C0E;

	public static final int EQ = 0x0C0F;

	public static final int DROP = 0x0C12;

	public static final int PUT = 0x0C14;

	public static final int GET = 0x0C15;

	public static final int IFELSE = 0x0C16;

	public static final int RANDOM = 0x0C17;

	public static final int MUL = 0x0C18;

	public static final int SQRT = 0x0C1A;

	public static final int DUP = 0x0C1B;

	public static final int EXCH = 0x0C1C;

	public static final int INDEX = 0x0C1D;

	public static final int ROLL = 0x0C1E;

	public static final int HFLEX = 0x0C22;

	public static final int FLEX = 0x0C23;

	public static final int HFLEX1 = 0x0C24;

	public static final int FLEX1 = 0x0C25;

	public static final byte TYPE_OPERATOR = 1;

	public static final byte TYPE_INTEGER = 2;

	private static final boolean DEBUG = false;

	private final RandomAccessFile raf;

	private final Type2Stack operandStack = new Type2Stack();

	private final int[] execStack = new int[10];

	private int execDepth = 0;

	private int b0 = -1;

	/**
	 * Constructs a parser backed by the given random-access file.
	 *
	 * @param raf the file containing CFF charstring data; must remain open for
	 *            the lifetime of this object
	 */
	public Type2CharString(final RandomAccessFile raf) {
		this.raf = raf;
	}

	/**
	 * Parses the charstring at the given offset and returns the resulting glyph.
	 * <p>
	 * The method seeks to {@code offset} inside the backing file, then interprets
	 * the Type 2 byte stream until it encounters an {@code endchar} operator.
	 * Subroutine calls (both local and global) are resolved inline using the
	 * supplied offset tables.
	 * </p>
	 *
	 * @param ix                the glyph index (used only for debug logging)
	 * @param offset            the byte offset within the file where the charstring
	 *                          starts
	 * @param upm               units-per-em value of the font (currently unused but
	 *                          reserved for scaling)
	 * @param globalSubrOffsets array of absolute file offsets for global
	 *                          subroutines; the bias is applied automatically
	 *                          according to the array length
	 * @param localSubrOffsets  array of absolute file offsets for local
	 *                          subroutines; the bias is applied automatically
	 *                          according to the array length
	 * @return a {@link Glyph} containing the outline path and the cleaned
	 *         charstring bytes
	 * @throws RuntimeException wrapping any {@link java.io.IOException} encountered
	 *                          while reading the file
	 */
	public Glyph getGlyph(final int ix, final int offset, final short upm, final int[] globalSubrOffsets,
			final int[] localSubrOffsets) {
		if (DEBUG) {
			System.err.println("GLYPH: " + ix);
		}
		ByteArrayOutputStream buff = new ByteArrayOutputStream();
		GeneralPath path = new GeneralPath();
		path.moveTo(0, 0);
		synchronized (this.raf) {
			try {
				this.raf.seek(offset);
				int cx = 0, cy = 0;
				int op;
				int hintCount = 0;
				boolean closed = true;
				this.operandStack.clear();
				this.execDepth = 0;
				while ((op = this.scanOperator()) != ENDCHAR) {
					switch (op) {
						case RMOVETO: {
							if (!closed) {
								path.closePath();
								if (DEBUG) {
									System.err.println("closePath");
								}
								closed = true;
							}
							cx += this.operandStack.get(this.operandStack.size() - 2);
							cy += -this.operandStack.get(this.operandStack.size() - 1);
							path.moveTo(cx, cy);
							if (DEBUG) {
								System.err.println("rmoveto " + cx + " " + cy);
							}
							this.operandStack.writeTo(buff, op, 2);
							this.operandStack.clear();
						}
							break;
						case HMOVETO: {
							if (!closed) {
								path.closePath();
								if (DEBUG) {
									System.err.println("closePath");
								}
								closed = true;
							}
							cx += this.operandStack.get(this.operandStack.size() - 1);
							path.moveTo(cx, cy);
							if (DEBUG) {
								System.err.println("hmoveTo " + cx + " " + cy);
							}
							this.operandStack.writeTo(buff, op, 1);
							this.operandStack.clear();
						}
							break;
						case VMOVETO: {
							if (!closed) {
								path.closePath();
								if (DEBUG) {
									System.err.println("closePath");
								}
								closed = true;
							}
							cy += -this.operandStack.get(this.operandStack.size() - 1);
							path.moveTo(cx, cy);
							if (DEBUG) {
								System.err.println("vmoveTo " + cx + " " + cy);
							}
							this.operandStack.writeTo(buff, op, 1);
							this.operandStack.clear();
						}
							break;
						case RLINETO: {
							if (this.operandStack.size() < 2 || (this.operandStack.size() % 2) == 1) {
								throw new ArrayIndexOutOfBoundsException(this.operandStack.size());
							}
							for (int i = 0; i < this.operandStack.size() - 1; i += 2) {
								cx += this.operandStack.get(i);
								cy += -this.operandStack.get(i + 1);
								path.lineTo(cx, cy);
								if (DEBUG) {
									System.err.println("rlineto " + cx + " " + cy);
								}
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case HLINETO: {
							if (this.operandStack.size() < 1) {
								throw new ArrayIndexOutOfBoundsException(this.operandStack.size());
							}
							for (int i = 0; i < this.operandStack.size(); ++i) {
								if ((i % 2) == 0) {
									cx += this.operandStack.get(i);
								} else {
									cy += -this.operandStack.get(i);
								}
								path.lineTo(cx, cy);
								if (DEBUG) {
									System.err.println("hlineto " + cx + " " + cy);
								}
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case VLINETO: {
							if (this.operandStack.size() < 1) {
								throw new ArrayIndexOutOfBoundsException(this.operandStack.size());
							}
							for (int i = 0; i < this.operandStack.size(); ++i) {
								if ((i % 2) == 0) {
									cy += -this.operandStack.get(i);
								} else {
									cx += this.operandStack.get(i);
								}
								path.lineTo(cx, cy);
								if (DEBUG) {
									System.err.println("vlineto " + cx + " " + cy);
								}
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case RRCURVETO: {
							if (this.operandStack.size() < 1 || (this.operandStack.size() % 6) != 0) {
								throw new ArrayIndexOutOfBoundsException(this.operandStack.size());
							}
							for (int i = 0; i < this.operandStack.size() - 5; i += 6) {
								float x1 = cx += this.operandStack.get(i);
								float y1 = cy += -this.operandStack.get(i + 1);
								float x2 = cx += this.operandStack.get(i + 2);
								float y2 = cy += -this.operandStack.get(i + 3);
								float x3 = cx += this.operandStack.get(i + 4);
								float y3 = cy += -this.operandStack.get(i + 5);
								path.curveTo(x1, y1, x2, y2, x3, y3);
								if (DEBUG) {
									System.err.println(
											"rrcurveto " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x3 + " " + y3);
								}
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case HHCURVETO: {
							if (this.operandStack.size() < 2
									|| ((this.operandStack.size() % 4) != 0
											&& ((this.operandStack.size() - 1) % 4) != 0)) {
								throw new ArrayIndexOutOfBoundsException(this.operandStack.size());
							}
							int i = 0;
							if ((this.operandStack.size() % 2) == 1) {
								cy += -this.operandStack.get(i);
								++i;
							}
							for (; i < this.operandStack.size() - 3; i += 4) {
								float x1 = cx += this.operandStack.get(i);
								float y1 = cy;
								float x2 = cx += this.operandStack.get(i + 1);
								float y2 = cy += -this.operandStack.get(i + 2);
								float x3 = cx += this.operandStack.get(i + 3);
								float y3 = cy;
								path.curveTo(x1, y1, x2, y2, x3, y3);
								if (DEBUG) {
									System.err.println(
											"hhcurveto " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x3 + " " + y3);
								}
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case HVCURVETO: {
							if (this.operandStack.size() < 1 || (this.operandStack.size() % 4) > 1) {
								throw new ArrayIndexOutOfBoundsException(this.operandStack.size());
							}
							for (int i = 0; i < this.operandStack.size() - 3; i += 4) {
								float x1, y1, x2, y2, x3, y3;
								if (((i / 4) % 2) == 1) {
									x1 = cx;
									y1 = cy += -this.operandStack.get(i);
									x2 = cx += this.operandStack.get(i + 1);
									y2 = cy += -this.operandStack.get(i + 2);
									x3 = cx += this.operandStack.get(i + 3);
									if (i + 4 == this.operandStack.size() - 1) {
										y3 = cy += -this.operandStack.get(i + 4);
									} else {
										y3 = cy;
									}
								} else {
									x1 = cx += this.operandStack.get(i);
									y1 = cy;
									x2 = cx += this.operandStack.get(i + 1);
									y2 = cy += -this.operandStack.get(i + 2);
									y3 = cy += -this.operandStack.get(i + 3);
									if (i + 4 == this.operandStack.size() - 1) {
										x3 = cx += this.operandStack.get(i + 4);
									} else {
										x3 = cx;
									}
								}
								path.curveTo(x1, y1, x2, y2, x3, y3);
								if (DEBUG) {
									System.err.println(
											"hvcurveto " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x3 + " " + y3);
								}
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case RCURVELINE: {
							if (this.operandStack.size() < 1 || ((this.operandStack.size() - 2) % 6) != 0) {
								throw new ArrayIndexOutOfBoundsException(this.operandStack.size());
							}
							for (int i = 0; i < this.operandStack.size() - 7; i += 6) {
								float x1 = cx += this.operandStack.get(i);
								float y1 = cy += -this.operandStack.get(i + 1);
								float x2 = cx += this.operandStack.get(i + 2);
								float y2 = cy += -this.operandStack.get(i + 3);
								float x3 = cx += this.operandStack.get(i + 4);
								float y3 = cy += -this.operandStack.get(i + 5);
								path.curveTo(x1, y1, x2, y2, x3, y3);
								if (DEBUG) {
									System.err.println("rcurveline " + x1 + " " + y1 + " " + x2 + " " + y2 + " "
											+ x3 + " " + y3);
								}
							}
							cx += this.operandStack.get(this.operandStack.size() - 2);
							cy += -this.operandStack.get(this.operandStack.size() - 1);
							path.lineTo(cx, cy);
							if (DEBUG) {
								System.err.println("rcurveline " + cx + " " + cy);
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case RLINECURVE: {
							if (this.operandStack.size() < 1 || ((this.operandStack.size() - 6) % 2) != 0) {
								throw new ArrayIndexOutOfBoundsException(this.operandStack.size());
							}
							int i = 0;
							for (; i < this.operandStack.size() - 6; i += 2) {
								cx += this.operandStack.get(i);
								cy += -this.operandStack.get(i + 1);
								path.lineTo(cx, cy);
								if (DEBUG) {
									System.err.println("rlinecurve " + cx + " " + cy);
								}
							}
							float x1 = cx += this.operandStack.get(i);
							float y1 = cy += -this.operandStack.get(i + 1);
							float x2 = cx += this.operandStack.get(i + 2);
							float y2 = cy += -this.operandStack.get(i + 3);
							float x3 = cx += this.operandStack.get(i + 4);
							float y3 = cy += -this.operandStack.get(i + 5);
							path.curveTo(x1, y1, x2, y2, x3, y3);
							if (DEBUG) {
								System.err.println(
										"rlinecurve " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x3 + " " + y3);
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case VHCURVETO: {
							if (this.operandStack.size() < 1 || (this.operandStack.size() % 4) > 1) {
								throw new ArrayIndexOutOfBoundsException(this.operandStack.size());
							}
							for (int i = 0; i < this.operandStack.size() - 3; i += 4) {
								float x1, y1, x2, y2, x3, y3;
								if (((i / 4) % 2) == 0) {
									x1 = cx;
									y1 = cy += -this.operandStack.get(i);
									x2 = cx += this.operandStack.get(i + 1);
									y2 = cy += -this.operandStack.get(i + 2);
									x3 = cx += this.operandStack.get(i + 3);
									if (i + 4 == this.operandStack.size() - 1) {
										y3 = cy += -this.operandStack.get(i + 4);
									} else {
										y3 = cy;
									}
								} else {
									x1 = cx += this.operandStack.get(i);
									y1 = cy;
									x2 = cx += this.operandStack.get(i + 1);
									y2 = cy += -this.operandStack.get(i + 2);
									y3 = cy += -this.operandStack.get(i + 3);
									if (i + 4 == this.operandStack.size() - 1) {
										x3 = cx += this.operandStack.get(i + 4);
									} else {
										x3 = cx;
									}
								}
								path.curveTo(x1, y1, x2, y2, x3, y3);
								if (DEBUG) {
									System.err.println(
											"vwcurveto " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x3 + " " + y3);
								}
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case VVCURVETO: {
							if (this.operandStack.size() < 2
									|| ((this.operandStack.size() % 4) != 0
											&& ((this.operandStack.size() - 1) % 4) != 0)) {
								throw new ArrayIndexOutOfBoundsException(this.operandStack.size());
							}
							int i = 0;
							if ((this.operandStack.size() % 2) == 1) {
								cx += this.operandStack.get(i);
								++i;
							}
							for (; i < this.operandStack.size() - 3; i += 4) {
								float x1 = cx;
								float y1 = cy += -this.operandStack.get(i);
								float x2 = cx += this.operandStack.get(i + 1);
								float y2 = cy += -this.operandStack.get(i + 2);
								float x3 = cx;
								float y3 = cy += -this.operandStack.get(i + 3);
								path.curveTo(x1, y1, x2, y2, x3, y3);
								if (DEBUG) {
									System.err.println(
											"vvcurveto " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x3 + " " + y3);
								}
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case FLEX: {
							float x1 = cx += this.operandStack.get(0);
							float y1 = cy += -this.operandStack.get(1);
							float x2 = cx += this.operandStack.get(2);
							float y2 = cy += -this.operandStack.get(3);
							float x3 = cx += this.operandStack.get(4);
							float y3 = cy += -this.operandStack.get(5);
							float x4 = cx += this.operandStack.get(6);
							float y4 = cy += -this.operandStack.get(7);
							float x5 = cx += this.operandStack.get(8);
							float y5 = cy += -this.operandStack.get(9);
							float x6 = cx += this.operandStack.get(10);
							float y6 = cy += -this.operandStack.get(11);
							path.curveTo(x1, y1, x2, y2, x3, y3);
							if (DEBUG) {
								System.err.println("flex " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x3 + " " + y3);
							}
							path.curveTo(x4, y4, x5, y5, x6, y6);
							if (DEBUG) {
								System.err.println("flex " + x4 + " " + y4 + " " + x5 + " " + y5 + " " + x6 + " " + y6);
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case HFLEX: {
							float x1 = cx += this.operandStack.get(0);
							float y1 = cy;
							float x2 = cx += this.operandStack.get(1);
							float y2 = cy += -this.operandStack.get(2);
							float x3 = cx += this.operandStack.get(3);
							float y3 = cy;
							float x4 = cx += this.operandStack.get(4);
							float y4 = cy;
							float x5 = cx += this.operandStack.get(5);
							float y5 = cy;
							float x6 = cx += this.operandStack.get(6);
							float y6 = cy;
							path.curveTo(x1, y1, x2, y2, x3, y3);
							if (DEBUG) {
								System.err
										.println("hflex " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x3 + " " + y3);
							}
							path.curveTo(x4, y4, x5, y5, x6, y6);
							if (DEBUG) {
								System.err
										.println("hflex " + x4 + " " + y4 + " " + x5 + " " + y5 + " " + x6 + " " + y6);
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case HFLEX1: {
							float x1 = cx += this.operandStack.get(0);
							float y1 = cy += -this.operandStack.get(1);
							float x2 = cx += this.operandStack.get(2);
							float y2 = cy += -this.operandStack.get(3);
							float x3 = cx += this.operandStack.get(4);
							float y3 = cy;
							float x4 = cx += this.operandStack.get(5);
							float y4 = cy;
							float x5 = cx += this.operandStack.get(6);
							float y5 = cy += -this.operandStack.get(7);
							float x6 = cx += this.operandStack.get(8);
							float y6 = cy;
							path.curveTo(x1, y1, x2, y2, x3, y3);
							if (DEBUG) {
								System.err
										.println("hflex1 " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x3 + " " + y3);
							}
							path.curveTo(x4, y4, x5, y5, x6, y6);
							if (DEBUG) {
								System.err
										.println("hflex1 " + x4 + " " + y4 + " " + x5 + " " + y5 + " " + x6 + " " + y6);
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;
						case FLEX1: {
							float x1 = cx += this.operandStack.get(0);
							float y1 = cy += -this.operandStack.get(1);
							float x2 = cx += this.operandStack.get(2);
							float y2 = cy += -this.operandStack.get(3);
							float x3 = cx += this.operandStack.get(4);
							float y3 = cy += -this.operandStack.get(5);
							float x4 = cx += this.operandStack.get(6);
							float y4 = cy += -this.operandStack.get(7);
							float x5 = cx += this.operandStack.get(8);
							float y5 = cy += -this.operandStack.get(9);
							float x6 = cx += this.operandStack.get(10);
							float y6 = cy += -this.operandStack.get(10);
							path.curveTo(x1, y1, x2, y2, x3, y3);
							if (DEBUG) {
								System.err
										.println("flex1 " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x3 + " " + y3);
							}
							path.curveTo(x4, y4, x5, y5, x6, y6);
							if (DEBUG) {
								System.err
										.println("flex1 " + x4 + " " + y4 + " " + x5 + " " + y5 + " " + x6 + " " + y6);
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size());
							this.operandStack.clear();
							closed = false;
						}
							break;

						case HSTEM:
						case VSTEM:
						case HSTEMHM:
						case VSTEMHM: {
							hintCount += this.operandStack.size() / 2;
							if (DEBUG) {
								System.err.println("hintCount: " + hintCount);
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size() / 2 * 2);
							this.operandStack.clear();
							break;
						}

						case HINTMASK:
						case CNTRMASK: {
							hintCount += this.operandStack.size() / 2;
							if (DEBUG) {
								System.err.println("hintCount: " + hintCount);
							}
							this.operandStack.writeTo(buff, op, this.operandStack.size() / 2 * 2);
							this.operandStack.clear();
							int maskBytes = (hintCount + 7) / 8;
							if (DEBUG) {
								System.err.println("maskBytes: " + maskBytes);
							}
							for (int i = 0; i < maskBytes; ++i) {
								buff.write(this.raf.read());
							}
							break;
						}

						case CALLSUBR: {
							int six = (int) this.operandStack.pop();
							if (localSubrOffsets.length < 1240) {
								six += 107;
							} else if (localSubrOffsets.length < 33900) {
								six += 1131;
							} else {
								six += 32768;
							}
							if (DEBUG) {
								System.err.println("callsubr: " + six + "|" + this.execDepth);
							}
							this.execStack[this.execDepth++] = (int) this.raf.getFilePointer();
							this.raf.seek(localSubrOffsets[six]);
						}
							break;

						case CALLGSUBR: {
							int six = (int) this.operandStack.pop();
							if (globalSubrOffsets.length < 1240) {
								six += 107;
							} else if (globalSubrOffsets.length < 33900) {
								six += 1131;
							} else {
								six += 32768;
							}
							if (DEBUG) {
								System.err.println("callgsubr: " + six + "|" + this.execDepth);
							}
							this.execStack[this.execDepth++] = (int) this.raf.getFilePointer();
							this.raf.seek(globalSubrOffsets[six]);
						}
							break;

						case RETURN:
							this.raf.seek(this.execStack[--this.execDepth]);
							if (DEBUG) {
								System.err.println("return: " + this.execDepth);
							}
							break;

						default:
							throw new UnsupportedOperationException(Integer.toHexString(op));
					}

				}
				if (!closed) {
					path.closePath();
					if (DEBUG) {
						System.err.println("closePath");
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		buff.write(ENDCHAR);
		byte[] charString = buff.toByteArray();
		if (DEBUG) {
			for (byte b : charString) {
				System.err.print(Integer.toHexString((int) b & 0xFF) + " ");
			}
			System.err.println();
		}
		return new Glyph(path, charString);
	}

	private int scanOperator() throws IOException {
		for (;;) {
			byte type = this.nextType();
			switch (type) {
				case TYPE_OPERATOR:
					return this.readOperator();
				case TYPE_INTEGER:
					this.operandStack.push(this.readInteger());
					break;
				default:
					throw new IllegalStateException();
			}
		}
	}

	private byte nextType() throws IOException {
		this.b0 = this.raf.read();
		if (this.b0 <= 31 && this.b0 != 28) {
			return TYPE_OPERATOR;
		}
		return TYPE_INTEGER;
	}

	private int readOperator() throws IOException {
		if (this.b0 == -1) {
			if (this.nextType() != TYPE_OPERATOR) {
				throw new IOException("Not an operator.");
			}
		}
		int b = this.b0;
		this.b0 = -1;
		if (b == 12) {
			b <<= 8;
			int a = this.raf.read();
			b |= a;
		}
		return b;
	}

	private int readInteger() throws IOException {
		if (this.b0 == -1) {
			if (this.nextType() != TYPE_INTEGER) {
				throw new IOException("Not an integer.");
			}
		}
		int b0 = this.b0;
		this.b0 = -1;
		if (b0 >= 32 && b0 <= 246) {
			return b0 - 139;
		}
		if (b0 >= 247 && b0 <= 250) {
			int b1 = this.raf.read();
			return (b0 - 247) * 256 + b1 + 108;
		}
		if (b0 >= 251 && b0 <= 254) {
			int b1 = this.raf.read();
			return -(b0 - 251) * 256 - b1 - 108;
		}
		if (b0 == 28) {
			int b1 = this.raf.read();
			int b2 = this.raf.read();
			return b1 << 8 | b2;
		}
		if (b0 == 255) {
			int b1 = this.raf.read();
			int b2 = this.raf.read();
			int b3 = this.raf.read();
			int b4 = this.raf.read();
			return b1 << 24 | b2 << 16 | b3 << 8 | b4;
		}
		throw new IOException("Invalid integer: " + b0);
	}

}
