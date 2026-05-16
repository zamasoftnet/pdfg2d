/*
 * Copyright 2015 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.zamasoft.pdfg2d.font.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * An optimised subclass of {@link RandomAccessFile} that adds an internal
 * read-ahead buffer to reduce the number of native I/O calls.  Reads are
 * served from the buffer whenever possible; the buffer is refilled lazily
 * when the current position moves past the buffered region.
 *
 * <p>This class is derived from the implementation published at
 * https://code.google.com/p/jmzreader/wiki/BufferedRandomAccessFile (Apache
 * 2.0 licence) and augmented to handle unsigned bytes correctly when reading
 * font binary data.
 *
 * @author jg
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class BufferedRandomAccessFile extends RandomAccessFile {
	/**
	 * Uses a byte instead of a char buffer for efficiency reasons.
	 */
	private final byte[] buffer;
	private int bufend = 0;
	private int bufpos = 0;

	/**
	 * The position inside the actual file.
	 */
	private long realpos = 0;

	/**
	 * Buffer size.
	 */
	private final int BUFSIZE;

	/**
	 * Creates a new instance of the BufferedRandomAccessFile.
	 *
	 * @param filename The path of the file to open.
	 * @param mode     Specifies the mode to use ("r", "rw", etc.) See the
	 *                 BufferedLineReader documentation for more information.
	 * @param bufsize  The buffer size (in bytes) to use.
	 * @throws FileNotFoundException If the mode is "r" but the given string does
	 *                               not denote an
	 *                               existing regular file, or if the mode begins
	 *                               with "rw" but the
	 *                               given string does not denote an existing,
	 *                               writable regular file
	 *                               and a new regular file of that name cannot be
	 *                               created, or if some
	 *                               other error occurs while opening or creating
	 *                               the file.
	 */
	public BufferedRandomAccessFile(final String filename, final String mode, final int bufsize)
			throws FileNotFoundException {
		super(filename, mode);
		this.BUFSIZE = bufsize;
		this.buffer = new byte[this.BUFSIZE];
	}

	/**
	 * Creates a new instance of the BufferedRandomAccessFile.
	 *
	 * @param file The file to open.
	 * @param mode Specifies the mode to use ("r", "rw", etc.) See the
	 *             BufferedLineReader documentation for more information.
	 * @throws FileNotFoundException If the mode is "r" but the given file path does
	 *                               not denote an
	 *                               existing regular file, or if the mode begins
	 *                               with "rw" but the
	 *                               given file path does not denote an existing,
	 *                               writable regular
	 *                               file and a new regular file of that name cannot
	 *                               be created, or if
	 *                               some other error occurs while opening or
	 *                               creating the file.
	 */
	public BufferedRandomAccessFile(final File file, final String mode) throws FileNotFoundException {
		super(file, mode);
		this.BUFSIZE = 512;
		this.buffer = new byte[this.BUFSIZE];
	}

	/**
	 * Reads a single byte from the file, returning it as an unsigned value in the
	 * range {@code 0}–{@code 255}, or {@code -1} at end-of-file.
	 *
	 * @return the unsigned byte value, or {@code -1} at end-of-file
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public final int read() throws IOException {
		if (this.bufpos >= this.bufend && fillBuffer() < 0) {
			return -1;
		}
		if (this.bufend == 0) {
			return -1;
		}
		// FIX to handle unsigned bytes
		return (this.buffer[this.bufpos++] + 256) & 0xFF;
		// End of fix
	}

	/**
	 * Reads the next BUFSIZE bytes into the internal buffer.
	 *
	 * @return The total number of bytes read into the buffer, or -1 if there is no
	 *         more data because the end of the file has been reached.
	 * @throws IOException If the first byte cannot be read for any reason other
	 *                     than end of
	 *                     file, or if the random access file has been closed, or if
	 *                     some
	 *                     other I/O error occurs.
	 */
	private int fillBuffer() throws IOException {
		final int n = super.read(this.buffer, 0, this.BUFSIZE);

		if (n >= 0) {
			this.realpos += n;
			this.bufend = n;
			this.bufpos = 0;
		}
		return n;
	}

	/**
	 * Clears the local buffer.
	 *
	 * @throws IOException If an I/O error occurs.
	 */
	private void invalidate() throws IOException {
		this.bufend = 0;
		this.bufpos = 0;
		this.realpos = super.getFilePointer();
	}

	/**
	 * Reads up to {@code b.length} bytes into the given array.
	 *
	 * @param b the buffer to read into
	 * @return the number of bytes actually read, or {@code -1} at end-of-file
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public int read(final byte[] b) throws IOException {
		return this.read(b, 0, b.length);
	}

	/**
	 * Reads up to {@code len} bytes starting at offset {@code off} in the given
	 * array.  Data is served from the internal buffer where possible.
	 *
	 * @param b   the buffer to read into
	 * @param off the start offset in {@code b}
	 * @param len the maximum number of bytes to read
	 * @return the number of bytes actually read, or {@code -1} at end-of-file
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException {
		int leftover = this.bufend - this.bufpos;
		if (len <= leftover) {
			System.arraycopy(this.buffer, this.bufpos, b, off, len);
			this.bufpos += len;
			return len;
		}
		System.arraycopy(this.buffer, this.bufpos, b, off, leftover);
		this.bufpos += leftover;
		if (fillBuffer() > 0) {
			final int bytesRead = read(b, off + leftover, len - leftover);
			if (bytesRead > 0) {
				leftover += bytesRead;
			}
		}
		return leftover > 0 ? leftover : -1;
	}

	/**
	 * Returns the logical file pointer (the position of the next byte that will
	 * be read), taking the internal buffer into account.
	 *
	 * @return the current file offset in bytes
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public long getFilePointer() throws IOException {
		return this.realpos - this.bufend + this.bufpos;
	}

	/**
	 * Repositions the file pointer to the given offset.  If the new position
	 * falls within the current buffer window the buffer is reused; otherwise the
	 * buffer is invalidated and the underlying file is sought directly.
	 *
	 * @param pos the new file offset in bytes from the start of the file
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public void seek(final long pos) throws IOException {
		final int n = (int) (this.realpos - pos);
		if (n >= 0 && n <= this.bufend) {
			this.bufpos = this.bufend - n;
		} else {
			super.seek(pos);
			invalidate();
		}
	}
}
