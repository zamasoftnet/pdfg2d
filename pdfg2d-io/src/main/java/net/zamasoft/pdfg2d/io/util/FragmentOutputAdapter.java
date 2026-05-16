package net.zamasoft.pdfg2d.io.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import net.zamasoft.pdfg2d.io.FragmentedOutput;

/**
 * An OutputStream adapter for writing to a specific fragment.
 * <p>
 * This class allows code that expects an OutputStream to write data
 * to a specific fragment of a {@link FragmentedOutput}. All write
 * operations are directed to the fragment specified at construction.
 * </p>
 * <p>
 * Closing this adapter calls {@link FragmentedOutput#finishFragment(int)}
 * to signal that writing to this fragment is complete.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class FragmentOutputAdapter extends OutputStream {
	/** The target FragmentedOutput. */
	private final FragmentedOutput builder;

	/** The fragment ID to write to. */
	private final int fragmentId;

	/** Reusable buffer for single-byte writes. */
	private final byte[] singleByteBuffer = new byte[1];

	/**
	 * Creates a new adapter for the specified fragment.
	 * 
	 * @param builder    target FragmentedOutput; must not be null.
	 * @param fragmentId target fragment ID.
	 * @throws NullPointerException if builder is null.
	 */
	public FragmentOutputAdapter(final FragmentedOutput builder, final int fragmentId) {
		this.builder = Objects.requireNonNull(builder);
		this.fragmentId = fragmentId;
	}

	/**
	 * Writes a single byte to the fragment.
	 * 
	 * @param b the byte to write (only the lower 8 bits are used).
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public void write(final int b) throws IOException {
		this.singleByteBuffer[0] = (byte) b;
		this.builder.write(this.fragmentId, this.singleByteBuffer, 0, 1);
	}

	/**
	 * Writes a portion of a byte array to the fragment.
	 * 
	 * @param b   the byte array.
	 * @param off the start offset.
	 * @param len the number of bytes to write.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public void write(final byte[] b, final int off, final int len) throws IOException {
		this.builder.write(this.fragmentId, b, off, len);
	}

	/**
	 * Writes the entire byte array to the fragment.
	 * 
	 * @param b the byte array.
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public void write(final byte[] b) throws IOException {
		this.builder.write(this.fragmentId, b, 0, b.length);
	}

	/**
	 * Finishes writing to the fragment.
	 * <p>
	 * Calls {@link FragmentedOutput#finishFragment(int)} to signal completion.
	 * </p>
	 * 
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public void close() throws IOException {
		this.builder.finishFragment(this.fragmentId);
	}
}