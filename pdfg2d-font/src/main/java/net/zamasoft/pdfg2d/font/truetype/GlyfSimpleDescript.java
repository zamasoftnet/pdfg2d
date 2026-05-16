package net.zamasoft.pdfg2d.font.truetype;

import java.io.IOException;
import java.io.RandomAccessFile;

import net.zamasoft.pdfg2d.font.table.GlyfTable;
import net.zamasoft.pdfg2d.font.table.Program;

/**
 * TrueType simple glyph description.  A simple glyph consists of one or more
 * contours each made up of on-curve and off-curve (quadratic Bézier control)
 * points.  Coordinates and flags are decoded from the compact run-length
 * encoded format used in the {@code glyf} table.
 *
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class GlyfSimpleDescript extends GlyfDescript {

	private final int[] endPtsOfContours;
	private final byte[] flags;
	private final short[] xCoordinates;
	private final short[] yCoordinates;
	private final int count;

	private GlyfSimpleDescript(final GlyfTable parentTable, final int numberOfContours, final short xMin,
			final short yMin, final short xMax, final short yMax, final short[] instructions,
			final int[] endPtsOfContours, final byte[] flags, final short[] xCoordinates, final short[] yCoordinates,
			final int count) {
		super(parentTable, numberOfContours, xMin, yMin, xMax, yMax, instructions);
		this.endPtsOfContours = endPtsOfContours;
		this.flags = flags;
		this.xCoordinates = xCoordinates;
		this.yCoordinates = yCoordinates;
		this.count = count;
	}

	/**
	 * Reads a simple glyph description from the current position in the given
	 * random-access file.
	 *
	 * @param parentTable      the {@link GlyfTable} that owns this description
	 * @param numberOfContours the number of contours in the glyph (must be &gt; 0)
	 * @param raf              the file to read from, positioned at the start of the
	 *                         bounding-box data
	 * @return the parsed {@link GlyfSimpleDescript}
	 * @throws IOException if the data cannot be read or is malformed
	 */
	public static GlyfSimpleDescript read(final GlyfTable parentTable, final int numberOfContours,
			final RandomAccessFile raf) throws IOException {
		final short xMin = (short) (raf.read() << 8 | raf.read());
		final short yMin = (short) (raf.read() << 8 | raf.read());
		final short xMax = (short) (raf.read() << 8 | raf.read());
		final short yMax = (short) (raf.read() << 8 | raf.read());

		final int[] endPtsOfContours = new int[numberOfContours];
		for (int i = 0; i < numberOfContours; i++) {
			endPtsOfContours[i] = (raf.read() << 8 | raf.read());
		}

		// The last end point index reveals the total number of points
		final int count = endPtsOfContours[numberOfContours - 1] + 1;
		final byte[] flags = new byte[count];
		final short[] xCoordinates = new short[count];
		final short[] yCoordinates = new short[count];

		final int instructionCount = (raf.read() << 8 | raf.read());
		final short[] instructions = Program.readInstructions(raf, instructionCount);
		readFlags(flags, count, raf);
		readCoords(xCoordinates, yCoordinates, flags, count, raf);

		return new GlyfSimpleDescript(parentTable, numberOfContours, xMin, yMin, xMax, yMax, instructions,
				endPtsOfContours, flags, xCoordinates, yCoordinates, count);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getEndPtOfContours(final int i) {
		return this.endPtsOfContours[i];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getFlags(final int i) {
		return this.flags[i];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public short getXCoordinate(final int i) {
		return this.xCoordinates[i];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public short getYCoordinate(final int i) {
		return this.yCoordinates[i];
	}

	/**
	 * Always returns {@code false} since this is a simple glyph.
	 *
	 * @return {@code false}
	 */
	@Override
	public boolean isComposite() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getPointCount() {
		return this.count;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getContourCount() {
		return this.getNumberOfContours();
	}

	/**
	 * The table is stored as relative values, but we'll store them as absolutes.
	 */
	private static void readCoords(final short[] xCoordinates, final short[] yCoordinates, final byte[] flags,
			final int count, final RandomAccessFile raf) throws IOException {
		short x = 0;
		short y = 0;
		for (int i = 0; i < count; i++) {
			if ((flags[i] & xDual) != 0) {
				if ((flags[i] & xShortVector) != 0) {
					x += (short) raf.read();
				}
			} else {
				if ((flags[i] & xShortVector) != 0) {
					x += (short) -((short) raf.read());
				} else {
					x += (short) (raf.read() << 8 | raf.read());
				}
			}
			xCoordinates[i] = x;
		}

		for (int i = 0; i < count; i++) {
			if ((flags[i] & yDual) != 0) {
				if ((flags[i] & yShortVector) != 0) {
					y += (short) raf.read();
				}
			} else {
				if ((flags[i] & yShortVector) != 0) {
					y += (short) -((short) raf.read());
				} else {
					y += (short) (raf.read() << 8 | raf.read());
				}
			}
			yCoordinates[i] = y;
		}
	}

	/**
	 * The flags are run-length encoded.
	 */
	private static void readFlags(final byte[] flags, final int flagCount, final RandomAccessFile raf)
			throws IOException {
		try {
			for (int index = 0; index < flagCount; index++) {
				flags[index] = (byte) raf.read();
				if ((flags[index] & repeat) != 0) {
					final int repeats = raf.read();
					for (int i = 1; i <= repeats; i++) {
						flags[index + i] = flags[index];
					}
					index += repeats;
				}
			}
		} catch (final ArrayIndexOutOfBoundsException e) {
			System.out.println("error: array index out of bounds");
		}
	}
}
