package net.zamasoft.pdfg2d.font.truetype;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.font.table.GlyfTable;
import net.zamasoft.pdfg2d.font.table.Program;

/**
 * TrueType glyph description for composite glyphs.  A composite glyph is
 * assembled from one or more referenced component glyphs, each optionally
 * transformed by a 2×2 affine matrix and/or translation.  Component data is
 * stored as a list of {@link GlyfCompositeComp} records read from the
 * {@code glyf} table.
 *
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class GlyfCompositeDescript extends GlyfDescript {

	private final List<GlyfCompositeComp> components;

	private GlyfCompositeDescript(final GlyfTable parentTable, final short xMin, final short yMin, final short xMax,
			final short yMax, final short[] instructions, final List<GlyfCompositeComp> components) {
		super(parentTable, -1, xMin, yMin, xMax, yMax, instructions);
		this.components = components;
	}

	/**
	 * Reads a composite glyph description from the current position in the given
	 * random-access file.
	 *
	 * @param parentTable the {@link GlyfTable} that owns this description
	 * @param raf         the file to read from, positioned at the start of the
	 *                    bounding-box data
	 * @return the parsed {@link GlyfCompositeDescript}
	 * @throws IOException if the data cannot be read or is malformed
	 */
	public static GlyfCompositeDescript read(final GlyfTable parentTable, final RandomAccessFile raf)
			throws IOException {
		final short xMin = (short) (raf.read() << 8 | raf.read());
		final short yMin = (short) (raf.read() << 8 | raf.read());
		final short xMax = (short) (raf.read() << 8 | raf.read());
		final short yMax = (short) (raf.read() << 8 | raf.read());

		// Get all of the composite components
		final List<GlyfCompositeComp> components = new ArrayList<>();
		GlyfCompositeComp comp;
		int firstIndex = 0;
		int firstContour = 0;
		do {
			comp = GlyfCompositeComp.read(firstIndex, firstContour, raf);
			components.add(comp);

			final long off = raf.getFilePointer();
			final GlyfDescript desc = parentTable.getDescription(comp.getGlyphIndex());
			raf.seek(off);
			if (desc != null) {
				firstIndex += desc.getPointCount();
				firstContour += desc.getContourCount();
			}
		} while ((comp.getFlags() & GlyfCompositeComp.MORE_COMPONENTS) != 0);

		// Are there hinting instructions to read?
		short[] instructions = null;
		if ((comp.getFlags() & GlyfCompositeComp.WE_HAVE_INSTRUCTIONS) != 0) {
			instructions = Program.readInstructions(raf, (raf.read() << 8 | raf.read()));
		}

		return new GlyfCompositeDescript(parentTable, xMin, yMin, xMax, yMax, instructions, components);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getEndPtOfContours(final int i) {
		final GlyfCompositeComp c = getCompositeCompEndPt(i);
		if (c != null) {
			final GlyfDescript gd = this.parentTable.getDescription(c.getGlyphIndex());
			return gd.getEndPtOfContours(i - c.getFirstContour()) + c.getFirstIndex();
		}
		return 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getFlags(final int i) {
		final GlyfCompositeComp c = getCompositeComp(i);
		if (c != null) {
			final GlyfDescript gd = this.parentTable.getDescription(c.getGlyphIndex());
			return gd.getFlags(i - c.getFirstIndex());
		}
		return 0;
	}

	/**
	 * {@inheritDoc}
	 * The coordinate is transformed by the component's affine matrix and
	 * translation before being returned.
	 */
	@Override
	public short getXCoordinate(final int i) {
		final GlyfCompositeComp c = getCompositeComp(i);
		if (c != null) {
			final GlyfDescript gd = this.parentTable.getDescription(c.getGlyphIndex());
			final int n = i - c.getFirstIndex();
			final int x = gd.getXCoordinate(n);
			final int y = gd.getYCoordinate(n);
			short x1 = (short) c.scaleX(x, y);
			x1 += c.getXTranslate();
			return x1;
		}
		return 0;
	}

	/**
	 * {@inheritDoc}
	 * The coordinate is transformed by the component's affine matrix and
	 * translation before being returned.
	 */
	@Override
	public short getYCoordinate(final int i) {
		final GlyfCompositeComp c = getCompositeComp(i);
		if (c != null) {
			final GlyfDescript gd = this.parentTable.getDescription(c.getGlyphIndex());
			final int n = i - c.getFirstIndex();
			final int x = gd.getXCoordinate(n);
			final int y = gd.getYCoordinate(n);
			short y1 = (short) c.scaleY(x, y);
			y1 += c.getYTranslate();
			return y1;
		}
		return 0;
	}

	/**
	 * Always returns {@code true} since this is a composite glyph.
	 *
	 * @return {@code true}
	 */
	@Override
	public boolean isComposite() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 * Calculated as the first-index offset of the last component plus that
	 * component's own point count.
	 */
	@Override
	public int getPointCount() {
		final GlyfCompositeComp c = this.components.get(this.components.size() - 1);
		final GlyfDescript gd = this.parentTable.getDescription(c.getGlyphIndex());
		return c.getFirstIndex() + (gd == null ? 0 : gd.getPointCount());
	}

	/**
	 * {@inheritDoc}
	 * Calculated as the first-contour offset of the last component plus that
	 * component's own contour count.
	 */
	@Override
	public int getContourCount() {
		final GlyfCompositeComp c = this.components.get(this.components.size() - 1);
		final GlyfDescript gd = this.parentTable.getDescription(c.getGlyphIndex());
		return c.getFirstContour() + (gd == null ? 0 : gd.getContourCount());
	}

	/**
	 * Returns the first-point index of the {@code i}-th component.
	 *
	 * @param i the zero-based component index
	 * @return the first-point index of the specified component
	 */
	public int getComponentIndex(final int i) {
		return this.components.get(i).getFirstIndex();
	}

	/**
	 * Returns the number of component glyphs that make up this composite glyph.
	 *
	 * @return the component count
	 */
	public int getComponentCount() {
		return this.components.size();
	}

	/**
	 * Returns the component that contains the point at the given absolute index,
	 * or {@code null} if no component owns that index.
	 *
	 * @param i the absolute point index
	 * @return the owning {@link GlyfCompositeComp}, or {@code null}
	 */
	protected GlyfCompositeComp getCompositeComp(final int i) {
		for (int n = 0; n < this.components.size(); n++) {
			final GlyfCompositeComp c = this.components.get(n);
			final GlyfDescript gd = this.parentTable.getDescription(c.getGlyphIndex());
			if (c.getFirstIndex() <= i && i < (c.getFirstIndex() + gd.getPointCount())) {
				return c;
			}
		}
		return null;
	}

	protected GlyfCompositeComp getCompositeCompEndPt(final int i) {
		for (int j = 0; j < this.components.size(); j++) {
			final GlyfCompositeComp c = this.components.get(j);
			final GlyfDescript gd = this.parentTable.getDescription(c.getGlyphIndex());
			if (c.getFirstContour() <= i && i < (c.getFirstContour() + gd.getContourCount())) {
				return c;
			}
		}
		return null;
	}
}
