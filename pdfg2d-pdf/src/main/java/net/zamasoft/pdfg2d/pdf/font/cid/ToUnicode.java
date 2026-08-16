package net.zamasoft.pdfg2d.pdf.font.cid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the ToUnicode CMap for a CID font. The ToUnicode CMap maps CID codes
 * to Unicode code points, enabling PDF viewers and text extraction tools to recover
 * the original Unicode text from a PDF page content stream. Entries are stored as
 * ranges respecting the PDF specification constraint that runs cannot span byte
 * boundaries.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class ToUnicode implements Serializable {
	private static final long serialVersionUID = 0;

	protected final Unicode[] unicodes;

	/**
	 * Constructs a ToUnicode with the given array of Unicode mapping entries.
	 *
	 * @param unicodes the array of Unicode mapping ranges
	 */
	public ToUnicode(Unicode[] unicodes) {
		this.unicodes = unicodes;
	}

	/**
	 * Returns the array of Unicode mapping entries that make up this CMap.
	 *
	 * @return the array of Unicode mapping ranges
	 */
	public Unicode[] getUnicodes() {
		return this.unicodes;
	}

	/**
	 * A single range entry in the ToUnicode CMap, mapping a contiguous range of CID
	 * codes to a corresponding array of Unicode code points.
	 */
	public static class Unicode implements Serializable {
		private static final long serialVersionUID = 0;

		/** First and last CID codes. */
		int firstCode, lastCode;

		/** List of Unicode characters. */
		int[] unicodes;

		/**
		 * Constructs an entry for a range of characters.
		 * 
		 * @param firstCode the first character code
		 * @param lastCode  the last character code
		 * @param unicodes  the list of Unicode characters
		 */
		public Unicode(int firstCode, int lastCode, int[] unicodes) {
			this.firstCode = firstCode;
			this.lastCode = lastCode;
			this.unicodes = unicodes;
		}

		/**
		 * Constructs an entry mapping a single CID code to the given Unicode code points.
		 *
		 * @param code     the CID code
		 * @param unicodes the corresponding Unicode code points
		 */
		public Unicode(int code, int[] unicodes) {
			this(code, code, unicodes);
		}

		/**
		 * Constructs an entry mapping CID code 0 to the given Unicode code points.
		 *
		 * @param unicodes the corresponding Unicode code points
		 */
		public Unicode(int[] unicodes) {
			this(0, 0, unicodes);
		}

		/**
		 * Returns the first CID code in this mapping range.
		 *
		 * @return the first CID code
		 */
		public int getFirstCode() {
			return this.firstCode;
		}

		/**
		 * Returns the last CID code in this mapping range.
		 *
		 * @return the last CID code
		 */
		public int getLastCode() {
			return this.lastCode;
		}

		/**
		 * Returns the array of Unicode code points for the codes in this range.
		 *
		 * @return the Unicode code point array
		 */
		public int[] getUnicodes() {
			return this.unicodes;
		}

		/**
		 * Returns the Unicode code point for the given CID code.
		 * If the code maps beyond the end of the stored array, the last element is returned.
		 *
		 * @param code the CID code to look up
		 * @return the corresponding Unicode code point
		 * @throws ArrayIndexOutOfBoundsException if {@code code} is outside the range
		 *         [{@code firstCode}, {@code lastCode}]
		 */
		public int getUnicode(int code) {
			if (code < this.firstCode || code > this.lastCode) {
				throw new ArrayIndexOutOfBoundsException(code);
			}
			int index = code - this.firstCode;
			if (index >= this.unicodes.length) {
				return this.unicodes[this.unicodes.length - 1];
			}
			return this.unicodes[index];
		}
	}

	/**
	 * Builds an optimal ToUnicode from a character array.
	 * 
	 * @param unicodes the Unicode character array
	 * @return the ToUnicode instance
	 */
	public static ToUnicode buildFromChars(int[] unicodes) {
		List<ToUnicode.Unicode> list = new ArrayList<ToUnicode.Unicode>();
		int[] runUnicodes = new int[256];
		int position = 0;
		int startCid = -1;
		for (int cid = 0; cid < unicodes.length; ++cid) {
			int unicode = unicodes[cid];
			if (unicode == 0) {
				if (position == 0) {
					continue;
				}
				unicode = (runUnicodes[position - 1] + (cid - startCid));
			}

			if (startCid == -1) {
				// First character
				startCid = cid;
				runUnicodes[position++] = unicode;
				continue;
			} else if (cid % 256 != 0) {// SPEC PDF 7.10.1 (runs cannot span byte boundaries)
				runUnicodes[position++] = unicode;
				continue;
			}
			// End of run
			int[] temp = new int[position];
			System.arraycopy(runUnicodes, 0, temp, 0, position);
			list.add(new ToUnicode.Unicode(startCid, cid - 1, temp));
			startCid = cid;
			runUnicodes[0] = unicode;
			position = 1;
		}
		if (startCid != -1) {
			int[] temp = new int[position];
			System.arraycopy(runUnicodes, 0, temp, 0, position);
			list.add(new ToUnicode.Unicode(startCid, unicodes.length - 1, temp));
		}
		return new ToUnicode((ToUnicode.Unicode[]) list.toArray(new ToUnicode.Unicode[list.size()]));
	}

	/**
	 * Builds a ToUnicode map whose negative entries are genuinely unmapped.
	 * This is used by direction-specific Type0 wrappers that share a descendant
	 * CID font: a CID allocated by the other direction must not acquire an
	 * inferred Unicode value in this wrapper.
	 *
	 * @param unicodes CID-to-Unicode values, with negative entries for gaps
	 * @return the sparse ToUnicode map
	 */
	public static ToUnicode buildFromSparseChars(final int[] unicodes) {
		final List<ToUnicode.Unicode> list = new ArrayList<>();
		final int[] run = new int[256];
		int startCid = -1;
		int length = 0;
		for (int cid = 0; cid < unicodes.length; ++cid) {
			final int unicode = unicodes[cid];
			if (unicode < 0 || (startCid >= 0 && cid % 256 == 0)) {
				if (startCid >= 0) {
					final int[] values = new int[length];
					System.arraycopy(run, 0, values, 0, length);
					list.add(new ToUnicode.Unicode(startCid, startCid + length - 1, values));
					startCid = -1;
					length = 0;
				}
				if (unicode < 0) {
					continue;
				}
			}
			if (startCid < 0) {
				startCid = cid;
			}
			run[length++] = unicode;
		}
		if (startCid >= 0) {
			final int[] values = new int[length];
			System.arraycopy(run, 0, values, 0, length);
			list.add(new ToUnicode.Unicode(startCid, startCid + length - 1, values));
		}
		return new ToUnicode(list.toArray(new ToUnicode.Unicode[0]));
	}
}
