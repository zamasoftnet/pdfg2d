package net.zamasoft.pdfg2d.pdf.font.cid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.pdfg2d.util.ShortMapIterator;

/**
 * Represents font DW (default width) and W (width) attributes.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class WArray implements Serializable {
	private static final long serialVersionUID = 0;

	private static final Width[] EMPTY_WIDTHS = new Width[0];

	private final short defaultWidth;

	private final Width[] widths;

	/**
	 * 
	 * @param defaultWidth the default width (DW)
	 * @param widths       the array of W elements (W)
	 */
	public WArray(short defaultWidth, Width[] widths) {
		this.defaultWidth = defaultWidth;
		this.widths = widths == null ? EMPTY_WIDTHS : widths;
	}

	/**
	 * Constructs a WArray with the given default width and no per-glyph width entries.
	 *
	 * @param defaultWidth the default width (DW) applied to all glyphs without an explicit entry
	 */
	public WArray(short defaultWidth) {
		this(defaultWidth, null);
	}

	/**
	 * Returns the default width (DW).
	 * 
	 * @return the default width
	 */
	public short getDefaultWidth() {
		return this.defaultWidth;
	}

	/**
	 * Returns the array of W elements (W).
	 * 
	 * @return the width array
	 */
	public Width[] getWidths() {
		return this.widths;
	}

	/**
	 * Returns the width of a glyph.
	 * 
	 * @param gid the glyph ID
	 * @return the width
	 */
	public short getWidth(final int gid) {
		for (final var width : this.widths) {
			if (width.firstCode() <= gid && width.lastCode() >= gid) {
				return width.getWidth(gid);
			}
		}
		return this.defaultWidth;
	}

	/**
	 * Builds an optimal WArray from a list of widths.
	 * 
	 * @param widths the width iterator
	 * @return the WArray instance
	 */
	public static WArray buildFromWidths(ShortMapIterator widths) {
		final List<Width> list = new ArrayList<Width>();
		final IntList widthCounts = new IntList();// Use most frequent width as default
		final short[] runWidths = new short[255];

		int position = 0;
		int startCid = -1;
		boolean run = false;
		int cid = widths.key();
		while (widths.next()) {
			cid = widths.key();
			short advance = widths.value();
			// Short.MIN_VALUE indicates default width
			if (advance == Short.MIN_VALUE) {
				if (position == 0) {
					continue;
				}
				advance = runWidths[position - 1];
			}

			int count = widthCounts.get(advance);
			++count;
			widthCounts.set(advance, count);

			if (startCid == -1) {
				// First character
				startCid = cid;
				runWidths[position++] = advance;
				continue;
			} else {
				if (runWidths[position - 1] == advance) {
					// Start/continue run
					run = true;
					continue;
				} else if (startCid == cid - 1 || (!run && position < runWidths.length)) {
					// Start/continue array
					runWidths[position++] = advance;
					continue;
				}
			}
			// End of run
			short[] temp = new short[position];
			System.arraycopy(runWidths, 0, temp, 0, position);
			list.add(new Width(startCid, cid - 1, temp));
			startCid = cid;
			runWidths[0] = advance;
			position = 1;
			run = false;
		}
		if (startCid != -1) {
			short[] temp = new short[position];
			System.arraycopy(runWidths, 0, temp, 0, position);
			list.add(new Width(startCid, cid, temp));
		}
		short defaultWidth = 0;
		if (!widthCounts.isEmpty()) {
			int maxCount = 0;
			for (short i = 0; i < widthCounts.size(); ++i) {
				int count = widthCounts.get(i);
				if (count > maxCount) {
					defaultWidth = i;
					maxCount = count;
				}
			}
		}
		final var newList = new ArrayList<Width>();
		for (final var width : list) {
			if (width.widths().length == 1 && width.widths()[0] == defaultWidth) {
				continue;
			}
			newList.add(width);
		}
		return new WArray(defaultWidth, (Width[]) newList.toArray(new Width[newList.size()]));
	}

	/**
	 * Returns a human-readable representation of the default width and all width entries.
	 *
	 * @return a multi-line string showing the default width followed by each width entry
	 */
	public String toString() {
		final var buff = new StringBuilder();
		buff.append(this.defaultWidth).append('\n');
		for (final var w : this.widths) {
			buff.append(w).append('\n');
		}
		return buff.toString();
	}
}
