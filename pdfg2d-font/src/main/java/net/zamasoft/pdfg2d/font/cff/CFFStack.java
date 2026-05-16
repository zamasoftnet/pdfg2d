package net.zamasoft.pdfg2d.font.cff;

/**
 * Fixed-capacity operand stack used when parsing CFF (Compact Font Format)
 * DICT data.  The maximum stack depth is 48 entries as required by the CFF
 * specification (section 4).
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class CFFStack {
	private int size = 0;
	private final Number[] values;

	/**
	 * Creates a new empty CFF stack with a capacity of 48 elements.
	 */
	public CFFStack() {
		final int size = 48;
		this.values = new Number[size];
	}

	/**
	 * Pushes a number onto the top of the stack.
	 *
	 * @param value the value to push
	 */
	public void push(final Number value) {
		this.values[this.size] = value;
		++this.size;
	}

	/**
	 * Returns the element at the given position without modifying the stack.
	 * Returns {@code 0} if the index is at or beyond the current stack depth.
	 *
	 * @param ix zero-based index from the bottom of the stack
	 * @return the element, or {@code 0} if the index is out of bounds
	 */
	public Number get(final int ix) {
		if (ix >= this.size) {
			return 0;
		}
		return this.values[ix];
	}

	/**
	 * Pops and returns the top element.  Returns {@code 0} if the stack is empty.
	 *
	 * @return the top element, or {@code 0} if the stack is empty
	 */
	public Number pop() {
		if (this.size == 0) {
			return 0;
		}
		return this.values[--this.size];
	}

	/**
	 * Clears all elements from the stack.
	 */
	public void clear() {
		this.size = 0;
	}

	/**
	 * Removes the bottom {@code count} elements, shifting the remaining elements
	 * towards the bottom.
	 *
	 * @param count the number of elements to remove from the bottom
	 */
	public void clear(final int count) {
		this.size -= count;
		System.arraycopy(this.values, count, this.values, 0, this.size);
	}

	/**
	 * Returns the current number of elements on the stack.
	 *
	 * @return the stack depth
	 */
	public int size() {
		return this.size;
	}
}
