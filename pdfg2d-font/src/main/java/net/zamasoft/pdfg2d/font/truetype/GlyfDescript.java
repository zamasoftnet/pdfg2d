package net.zamasoft.pdfg2d.font.truetype;

import net.zamasoft.pdfg2d.font.table.GlyfTable;
import net.zamasoft.pdfg2d.font.table.Program;

/**
 * Abstract base class for TrueType glyph descriptions, covering both simple
 * and composite glyph outlines as defined in the OpenType/TrueType {@code glyf}
 * table specification.  Subclasses implement {@link GlyfSimpleDescript} for
 * simple glyphs and {@link GlyfCompositeDescript} for composite glyphs.
 *
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class GlyfDescript implements Program {

	/** Flag bit: the point is on the curve (as opposed to off-curve / control point). */
	public static final byte onCurve = 0x01;
	/** Flag bit: the x-coordinate is a short vector (1 byte, not 2). */
	public static final byte xShortVector = 0x02;
	/** Flag bit: the y-coordinate is a short vector (1 byte, not 2). */
	public static final byte yShortVector = 0x04;
	/** Flag bit: the previous flag byte is repeated the number of times given by the next byte. */
	public static final byte repeat = 0x08;
	/**
	 * Flag bit (x-axis): when set together with {@link #xShortVector} the x delta
	 * is positive; when set without {@link #xShortVector} the x value is the same
	 * as the preceding point (delta is zero).
	 */
	public static final byte xDual = 0x10;
	/**
	 * Flag bit (y-axis): when set together with {@link #yShortVector} the y delta
	 * is positive; when set without {@link #yShortVector} the y value is the same
	 * as the preceding point (delta is zero).
	 */
	public static final byte yDual = 0x20;

	protected final GlyfTable parentTable;
	private final int numberOfContours;
	private final short xMin;
	private final short yMin;
	private final short xMax;
	private final short yMax;
	private final short[] instructions;

	/**
	 * Constructs a glyph description with the given bounding-box and instruction
	 * data, as read from the {@code glyf} table.
	 *
	 * @param parentTable       the {@link GlyfTable} that owns this description
	 * @param numberOfContours  number of contours ({@code -1} for composite glyphs)
	 * @param xMin              minimum x-coordinate of the bounding box
	 * @param yMin              minimum y-coordinate of the bounding box
	 * @param xMax              maximum x-coordinate of the bounding box
	 * @param yMax              maximum y-coordinate of the bounding box
	 * @param instructions      the TrueType hinting instructions, or {@code null}
	 */
	protected GlyfDescript(final GlyfTable parentTable, final int numberOfContours, final short xMin, final short yMin,
			final short xMax, final short yMax, final short[] instructions) {
		this.parentTable = parentTable;
		this.numberOfContours = numberOfContours;
		this.xMin = xMin;
		this.yMin = yMin;
		this.xMax = xMax;
		this.yMax = yMax;
		this.instructions = instructions;
	}

	/**
	 * Returns the TrueType hinting instructions for this glyph.
	 *
	 * @return array of instruction bytes, or {@code null} if none
	 */
	@Override
	public short[] getInstructions() {
		return this.instructions;
	}

	/**
	 * Returns the number of contours in this glyph description.
	 * Returns {@code -1} for composite glyphs.
	 *
	 * @return the number of contours
	 */
	public int getNumberOfContours() {
		return this.numberOfContours;
	}

	/**
	 * Returns the maximum x-coordinate of this glyph's bounding box.
	 *
	 * @return the maximum x-coordinate in font units
	 */
	public short getXMaximum() {
		return this.xMax;
	}

	/**
	 * Returns the minimum x-coordinate of this glyph's bounding box.
	 *
	 * @return the minimum x-coordinate in font units
	 */
	public short getXMinimum() {
		return this.xMin;
	}

	/**
	 * Returns the maximum y-coordinate of this glyph's bounding box.
	 *
	 * @return the maximum y-coordinate in font units
	 */
	public short getYMaximum() {
		return this.yMax;
	}

	/**
	 * Returns the minimum y-coordinate of this glyph's bounding box.
	 *
	 * @return the minimum y-coordinate in font units
	 */
	public short getYMinimum() {
		return this.yMin;
	}

	/**
	 * Returns the index of the last point of the {@code i}-th contour.
	 *
	 * @param i the zero-based contour index
	 * @return the end-point index of the specified contour
	 */
	public abstract int getEndPtOfContours(int i);

	/**
	 * Returns the flag byte for the {@code i}-th point.
	 *
	 * @param i the zero-based point index
	 * @return the flag byte (see {@link #onCurve}, {@link #xShortVector}, etc.)
	 */
	public abstract byte getFlags(int i);

	/**
	 * Returns the x-coordinate of the {@code i}-th point in font units.
	 *
	 * @param i the zero-based point index
	 * @return the x-coordinate
	 */
	public abstract short getXCoordinate(int i);

	/**
	 * Returns the y-coordinate of the {@code i}-th point in font units.
	 *
	 * @param i the zero-based point index
	 * @return the y-coordinate
	 */
	public abstract short getYCoordinate(int i);

	/**
	 * Returns {@code true} if this is a composite glyph description.
	 *
	 * @return {@code true} for composite glyphs, {@code false} for simple glyphs
	 */
	public abstract boolean isComposite();

	/**
	 * Returns the total number of points across all contours.
	 *
	 * @return the total point count
	 */
	public abstract int getPointCount();

	/**
	 * Returns the total number of contours in this glyph description.
	 *
	 * @return the contour count
	 */
	public abstract int getContourCount();
}
