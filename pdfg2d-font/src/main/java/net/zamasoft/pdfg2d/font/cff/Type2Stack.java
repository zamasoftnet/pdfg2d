package net.zamasoft.pdfg2d.font.cff;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Fixed-capacity operand stack used when interpreting Type 2 CharString
 * programs (CFF glyph outlines).  Values are stored as integers; fixed-point
 * fractions are represented as integers scaled by 65536.  The maximum stack
 * depth is 48 entries as required by the Type 2 CharString specification.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class Type2Stack {
	private int size = 0;
	private final int[] values;

	/**
	 * Creates a new empty Type 2 stack with a capacity of 48 elements.
	 */
	public Type2Stack() {
		final int size = 48;
		this.values = new int[size];
	}

	/**
	 * Pushes an integer value onto the top of the stack.
	 *
	 * @param value the value to push
	 */
	public void push(final int value) {
		this.values[this.size] = value;
		++this.size;
	}

	/**
	 * Returns the element at the given position without modifying the stack.
	 *
	 * @param ix zero-based index from the bottom of the stack
	 * @return the element at the given index
	 */
	public int get(final int ix) {
		assert ix < this.size;
		return this.values[ix];
	}

	/**
	 * Pops and returns the top element.
	 *
	 * @return the top element
	 */
	public int pop() {
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

	/**
	 * Writes the top {@code size} stack values followed by the given operator
	 * byte(s) to {@code out} using the compact Type 2 integer encoding.
	 * The operator is written as a two-byte escape sequence when its high byte
	 * equals {@code 0x0C}, otherwise as a single byte.
	 *
	 * @param out  the output stream to write to
	 * @param op   the Type 2 operator code
	 * @param size the number of top-of-stack values to write before the operator
	 * @throws IOException if an I/O error occurs
	 */
	public void writeTo(final OutputStream out, final int op, final int size) throws IOException {
		for (int i = this.size - size; i < this.size; ++i) {
			int a = this.values[i];
			if (a >= -107 && a <= 107) {
				out.write(a + 139);
			} else if (a >= 108 && a <= 1131) {
				a -= 108;
				out.write((a >> 8) + 247);
				out.write(a);
			} else if (a >= -1131 && a <= -108) {
				a += 108;
				out.write((-a >> 8) + 251);
				out.write(-a);
			} else if (a >= -32768 && a <= 32767) {
				out.write(28);
				out.write(a >> 8);
				out.write(a);
			} else {
				out.write(255);
				out.write(a >> 24);
				out.write(a >> 16);
				out.write(a >> 8);
				out.write(a);
			}
		}
		if ((op & 0xFF00) == 0x0C00) {
			out.write(op >> 8);
			out.write(op);
		} else {
			out.write(op);
		}
	}
}
