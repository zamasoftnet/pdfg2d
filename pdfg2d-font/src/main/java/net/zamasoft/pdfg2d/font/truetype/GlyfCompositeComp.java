package net.zamasoft.pdfg2d.font.truetype;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Component of a composite glyph.
 * 
 * @param firstIndex   the index of the first point in this component
 * @param firstContour the index of the first contour in this component
 * @param argument1    raw argument 1
 * @param argument2    raw argument 2
 * @param flags        component flags
 * @param glyphIndex   the index of the glyph to be used as a component
 * @param xscale       the x-scale factor
 * @param yscale       the y-scale factor
 * @param scale01      the scale factor from y to x
 * @param scale10      the scale factor from x to y
 * @param xtranslate   the x-translation
 * @param ytranslate   the y-translation
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 */
public record GlyfCompositeComp(
		int firstIndex,
		int firstContour,
		short argument1,
		short argument2,
		short flags,
		int glyphIndex,
		double xscale,
		double yscale,
		double scale01,
		double scale10,
		int xtranslate,
		int ytranslate) {

	public static final short ARG_1_AND_2_ARE_WORDS = 0x0001;
	public static final short ARGS_ARE_XY_VALUES = 0x0002;
	public static final short ROUND_XY_TO_GRID = 0x0004;
	public static final short WE_HAVE_A_SCALE = 0x0008;
	public static final short MORE_COMPONENTS = 0x0020;
	public static final short WE_HAVE_AN_X_AND_Y_SCALE = 0x0040;
	public static final short WE_HAVE_A_TWO_BY_TWO = 0x0080;
	public static final short WE_HAVE_INSTRUCTIONS = 0x0100;
	public static final short USE_MY_METRICS = 0x0200;

	protected static GlyfCompositeComp read(final int firstIndex, final int firstContour, final RandomAccessFile raf)
			throws IOException {
		final short flags = (short) (raf.read() << 8 | raf.read());
		// glyphIndexはuint16。shortへ丸めてabsを取ると、glyphが32768個以上の
		// フォントで別のglyphを指す(2026-09-01)
		final int glyphIndex = raf.read() << 8 | raf.read();

		short argument1, argument2;
		// Get the arguments as just their raw values
		if ((flags & ARG_1_AND_2_ARE_WORDS) != 0) {
			argument1 = (short) (raf.read() << 8 | raf.read());
			argument2 = (short) (raf.read() << 8 | raf.read());
		} else {
			// **符号付き**の1バイト。read()の0..255をそのまま使うと、
			// 左へ寄せる成分(例: -16)が+240へ化けて合成字が崩れる
			// ——Gothic A1のά(α+トノス)やзで、字面が右へずれて
			// 次の字と重なっていた(2026-09-01)
			argument1 = (byte) raf.read();
			argument2 = (byte) raf.read();
		}

		int xtranslate = 0;
		int ytranslate = 0;
		// Assign the arguments according to the flags
		if ((flags & ARGS_ARE_XY_VALUES) != 0) {
			xtranslate = argument1;
			ytranslate = argument2;
		}

		// Get the scale values (if any)
		double xscale = 1.0;
		double yscale = 1.0;
		double scale01 = 0.0;
		double scale10 = 0.0;
		if ((flags & WE_HAVE_A_SCALE) != 0) {
			final int i = (short) (raf.read() << 8 | raf.read());
			xscale = yscale = (double) i / (double) 0x4000;
		} else if ((flags & WE_HAVE_AN_X_AND_Y_SCALE) != 0) {
			short i = (short) (raf.read() << 8 | raf.read());
			xscale = (double) i / (double) 0x4000;
			i = (short) (raf.read() << 8 | raf.read());
			yscale = (double) i / (double) 0x4000;
		} else if ((flags & WE_HAVE_A_TWO_BY_TWO) != 0) {
			int i = (short) (raf.read() << 8 | raf.read());
			xscale = (double) i / (double) 0x4000;
			i = (short) (raf.read() << 8 | raf.read());
			scale01 = (double) i / (double) 0x4000;
			i = (short) (raf.read() << 8 | raf.read());
			scale10 = (double) i / (double) 0x4000;
			i = (short) (raf.read() << 8 | raf.read());
			yscale = (double) i / (double) 0x4000;
		}

		return new GlyfCompositeComp(firstIndex, firstContour, argument1, argument2, flags, glyphIndex, xscale, yscale,
				scale01, scale10, xtranslate, ytranslate);
	}

	public int getFirstIndex() {
		return this.firstIndex;
	}

	public int getFirstContour() {
		return this.firstContour;
	}

	public short getArgument1() {
		return this.argument1;
	}

	public short getArgument2() {
		return this.argument2;
	}

	public short getFlags() {
		return this.flags;
	}

	public int getGlyphIndex() {
		return this.glyphIndex;
	}

	public double getScale01() {
		return this.scale01;
	}

	public double getScale10() {
		return this.scale10;
	}

	public double getXScale() {
		return this.xscale;
	}

	public double getYScale() {
		return this.yscale;
	}

	public int getXTranslate() {
		return this.xtranslate;
	}

	public int getYTranslate() {
		return this.ytranslate;
	}

	/**
	 * Transforms an x-coordinate of a point for this component.
	 * 
	 * @param x The x-coordinate of the point to transform
	 * @param y The y-coordinate of the point to transform
	 * @return The transformed x-coordinate
	 */
	public int scaleX(final int x, final int y) {
		return Math.round((float) (x * this.xscale + y * this.scale10));
	}

	/**
	 * Transforms a y-coordinate of a point for this component.
	 * 
	 * @param x The x-coordinate of the point to transform
	 * @param y The y-coordinate of the point to transform
	 * @return The transformed y-coordinate
	 */
	public int scaleY(final int x, final int y) {
		return Math.round((float) (x * this.scale01 + y * this.yscale));
	}
}
