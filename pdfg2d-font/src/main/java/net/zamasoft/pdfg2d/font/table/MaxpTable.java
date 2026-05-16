package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code maxp} (Maximum Profile) table.
 * <p>
 * Establishes the memory requirements for this font by recording the maximum
 * sizes of various tables and structures within the font.  All values are
 * non-negative integers read as unsigned shorts from the font file.
 * </p>
 *
 * @param numGlyphs             the total number of glyphs in the font
 * @param maxPoints             maximum number of points in a non-composite glyph
 * @param maxContours           maximum number of contours in a non-composite glyph
 * @param maxCompositePoints    maximum number of points in a composite glyph
 * @param maxCompositeContours  maximum number of contours in a composite glyph
 * @param maxZones              1 if no twilight zone is used, or 2 if the twilight zone is used
 * @param maxTwilightPoints     maximum number of points in the twilight zone (zone 0)
 * @param maxStorage            number of Storage Area locations
 * @param maxFunctionDefs       number of FDEF entries (functions)
 * @param maxInstructionDefs    number of IDEF entries (instruction definitions)
 * @param maxStackElements      maximum stack depth across the font program, CVT program, and all glyph instructions
 * @param maxSizeOfInstructions maximum byte count for glyph instructions
 * @param maxComponentElements  maximum number of components referenced at the top level for a composite glyph
 * @param maxComponentDepth     maximum levels of recursion for composite glyphs
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 */
public record MaxpTable(
		int numGlyphs,
		int maxPoints,
		int maxContours,
		int maxCompositePoints,
		int maxCompositeContours,
		int maxZones,
		int maxTwilightPoints,
		int maxStorage,
		int maxFunctionDefs,
		int maxInstructionDefs,
		int maxStackElements,
		int maxSizeOfInstructions,
		int maxComponentElements,
		int maxComponentDepth) implements Table {

	protected MaxpTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private MaxpTable(MaxpTable other) {
		this(
				other.numGlyphs,
				other.maxPoints,
				other.maxContours,
				other.maxCompositePoints,
				other.maxCompositeContours,
				other.maxZones,
				other.maxTwilightPoints,
				other.maxStorage,
				other.maxFunctionDefs,
				other.maxInstructionDefs,
				other.maxStackElements,
				other.maxSizeOfInstructions,
				other.maxComponentElements,
				other.maxComponentDepth);
	}

	private static MaxpTable readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());
			raf.readInt(); // versionNumber
			return new MaxpTable(
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort(),
					raf.readUnsignedShort());
		}
	}

	/**
	 * Returns the maximum levels of recursion for composite glyphs.
	 *
	 * @return maximum component depth
	 */
	public int getMaxComponentDepth() {
		return this.maxComponentDepth;
	}

	/**
	 * Returns the maximum number of top-level components referenced in a composite glyph.
	 *
	 * @return maximum component element count
	 */
	public int getMaxComponentElements() {
		return this.maxComponentElements;
	}

	/**
	 * Returns the maximum number of contours in a composite glyph.
	 *
	 * @return maximum composite contour count
	 */
	public int getMaxCompositeContours() {
		return this.maxCompositeContours;
	}

	/**
	 * Returns the maximum number of points in a composite glyph.
	 *
	 * @return maximum composite point count
	 */
	public int getMaxCompositePoints() {
		return this.maxCompositePoints;
	}

	/**
	 * Returns the maximum number of contours in a non-composite glyph.
	 *
	 * @return maximum contour count
	 */
	public int getMaxContours() {
		return this.maxContours;
	}

	/**
	 * Returns the number of FDEF entries (function definitions).
	 *
	 * @return maximum function definition count
	 */
	public int getMaxFunctionDefs() {
		return this.maxFunctionDefs;
	}

	/**
	 * Returns the number of IDEF entries (instruction definitions).
	 *
	 * @return maximum instruction definition count
	 */
	public int getMaxInstructionDefs() {
		return this.maxInstructionDefs;
	}

	/**
	 * Returns the maximum number of points in a non-composite glyph.
	 *
	 * @return maximum point count
	 */
	public int getMaxPoints() {
		return this.maxPoints;
	}

	/**
	 * Returns the maximum byte count for glyph instructions.
	 *
	 * @return maximum instruction byte count
	 */
	public int getMaxSizeOfInstructions() {
		return this.maxSizeOfInstructions;
	}

	/**
	 * Returns the maximum stack depth across the font program and all glyph instructions.
	 *
	 * @return maximum stack element count
	 */
	public int getMaxStackElements() {
		return this.maxStackElements;
	}

	/**
	 * Returns the number of Storage Area locations.
	 *
	 * @return maximum storage count
	 */
	public int getMaxStorage() {
		return this.maxStorage;
	}

	/**
	 * Returns the maximum number of points in the twilight zone (zone 0).
	 *
	 * @return maximum twilight point count
	 */
	public int getMaxTwilightPoints() {
		return this.maxTwilightPoints;
	}

	/**
	 * Returns {@code 1} if no twilight zone is used, or {@code 2} if the twilight zone is used.
	 *
	 * @return zone count (1 or 2)
	 */
	public int getMaxZones() {
		return this.maxZones;
	}

	/**
	 * Returns the total number of glyphs in the font.
	 *
	 * @return glyph count
	 */
	public int getNumGlyphs() {
		return this.numGlyphs;
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return MAXP;
	}
}
