/*
 * $Id: ASCII85OutputStream.java 1565 2018-07-04 11:51:25Z miyabe $
 * ============================================================================
 *                    The Apache Software License, Version 1.1
 * ============================================================================
 *
 * Copyright (C) 1999-2003 The Apache Software Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or withthis.out modifica-
 * tion, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following acknowledgment: "This product includes software
 *    developed by the Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "FOP" and "Apache Software Foundation" must not be used to
 *    endorse or promote products derived from this software withthis.out prior
 *    written permission. For written permission, please contact
 *    apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache", nor may
 *    "Apache" appear in their name, withthis.out prior written permission of the
 *    Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * APACHE SOFTWARE FOUNDATION OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLU-
 * DING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ============================================================================
 *
 * This software consists of voluntary contributions made by many individuals
 * on behalf of the Apache Software Foundation and was originally created by
 * James Tauber <jtauber@jtauber.com>. For more information on the Apache
 * Software Foundation, please see <http://www.apache.org/>.
 */
//package org.apache.fop.render.ps;
package net.zamasoft.pdfg2d.pdf.util.codec;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.zamasoft.pdfg2d.pdf.PDFOutput;

/**
 * Output stream filter that encodes binary data using the PDF
 * {@code ASCII85Decode} encoding (also known as Base-85).
 * <p>
 * Every group of four input bytes is converted to five printable ASCII
 * characters in the range {@code !} (0x21) through {@code u} (0x75).
 * The all-zeros group is represented by the single character {@code z}.
 * A line break is inserted every 80 characters to keep lines readable.
 * The end-of-data marker ({@code ~>}) is written automatically when
 * {@link #finish()} or {@link #close()} is called.
 * </p>
 * <p>
 * This implementation is derived from the Apache FOP project and is
 * licensed under the Apache Software License, Version 1.1.
 * </p>
 *
 * @author Jeremias Maerki (Apache FOP), adapted by MIYABE Tatsuhiko
 * @since 1.0
 */
public class ASCII85OutputStream extends FilterOutputStream {

	private static final int ZERO = 0x7A; // "z"

	private static final byte[] ZERO_ARRAY = { (byte) ZERO };

	private static final int START = 0x21; // "!"

	private static final byte[] EOD = { 0x7E, 0x3E }; // "~>"

	private static final long base85_4 = 85;

	private static final long base85_3 = base85_4 * base85_4;

	private static final long base85_2 = base85_3 * base85_4;

	private static final long base85_1 = base85_2 * base85_4;

	private int pos = 0;

	private long buffer = 0;

	private int posinline = 0;

	/**
	 * Constructs an ASCII85OutputStream that wraps the given stream.
	 *
	 * @param out the underlying stream to write encoded data to
	 */
	public ASCII85OutputStream(final OutputStream out) {
		super(out);
	}

	/** {@inheritDoc} */
	@Override
	public void write(final int b) throws IOException {
		if (this.pos == 0) {
			this.buffer += (b << 24) & 0xff000000L;
		} else if (this.pos == 1) {
			this.buffer += (b << 16) & 0xff0000L;
		} else if (this.pos == 2) {
			this.buffer += (b << 8) & 0xff00L;
		} else {
			this.buffer += b & 0xffL;
		}
		this.pos++;

		if (this.pos > 3) {
			checkedWrite(convertWord(this.buffer));
			this.buffer = 0;
			this.pos = 0;
		}
	}

	private void checkedWrite(byte[] buf) throws IOException {
		checkedWrite(buf, buf.length, false);
	}

	private void checkedWrite(byte[] buf, boolean nosplit) throws IOException {
		checkedWrite(buf, buf.length, nosplit);
	}

	private void checkedWrite(byte[] buf, int len) throws IOException {
		checkedWrite(buf, len, false);
	}

	private void checkedWrite(byte[] buf, int len, boolean nosplit) throws IOException {
		if (this.posinline + len > 80) {
			int firstpart = (nosplit ? 0 : len - (this.posinline + len - 80));
			if (firstpart > 0)
				this.out.write(buf, 0, firstpart);
			this.out.write(PDFOutput.EOL);
			int rest = len - firstpart;
			if (rest > 0)
				this.out.write(buf, firstpart, rest);
			this.posinline = rest;
		} else {
			this.out.write(buf, 0, len);
			this.posinline += len;
		}
	}

	/**
	 * This converts a 32 bit value (4 bytes) into 5 bytes using base 85. each byte
	 * in the result starts with zero at the '!' character so the resulting base85
	 * number fits into printable ascii chars
	 * 
	 * @param word the 32 bit unsigned (hence the long datatype) word
	 * @return 5 bytes (or a single byte of the 'z' character for word values of 0)
	 */
	private byte[] convertWord(long word) {
		word = word & 0xffffffff;

		if (word == 0) {
			return ZERO_ARRAY;
		}
		if (word < 0) {
			word = -word;
		}
		byte c1 = (byte) ((word / base85_1) & 0xFF);
		byte c2 = (byte) (((word - (c1 * base85_1)) / base85_2) & 0xFF);
		byte c3 = (byte) (((word - (c1 * base85_1) - (c2 * base85_2)) / base85_3) & 0xFF);
		byte c4 = (byte) (((word - (c1 * base85_1) - (c2 * base85_2) - (c3 * base85_3)) / base85_4) & 0xFF);
		byte c5 = (byte) (((word - (c1 * base85_1) - (c2 * base85_2) - (c3 * base85_3) - (c4 * base85_4))) & 0xFF);

		byte[] ret = { (byte) (c1 + START), (byte) (c2 + START), (byte) (c3 + START), (byte) (c4 + START),
				(byte) (c5 + START) };
		return ret;
	}

	/**
	 * Flushes any remaining buffered bytes and writes the end-of-data marker
	 * ({@code ~>}) to the stream.
	 * <p>
	 * If there are between 1 and 3 bytes left in the internal buffer they are
	 * padded with zero bytes to form a complete 4-byte group; only the first
	 * {@code n + 1} characters of the resulting 5-character encoding are written.
	 * </p>
	 *
	 * @throws IOException if an I/O error occurs while writing
	 */
	public void finish() throws IOException {
		// now take care of the trailing few bytes.
		// with n leftover bytes, we append 0 bytes to make a full group of 4
		// then convert like normal (except not applying the special zero rule)
		// and write this.out the first n+1 bytes from the result
		if (this.pos > 0) {
			int rest = this.pos;
			/*
			 * byte[] lastdata = new byte[4]; int i = 0; for (int j = 0; j < 4; j++) { if (j
			 * < rest) { lastdata[j] = data[i++]; } else { lastdata[j] = 0; } }
			 * 
			 * long val = ((lastdata[0] < < 24) & 0xff000000L) + ((lastdata[1] < < 16) &
			 * 0xff0000L) + ((lastdata[2] < < 8) & 0xff00L) + (lastdata[3] & 0xffL);
			 */

			byte[] conv;
			// special rule for handling zeros at the end
			if (this.buffer != 0) {
				conv = convertWord(this.buffer);
			} else {
				conv = new byte[5];
				for (int j = 0; j < 5; j++) {
					conv[j] = (byte) '!';
				}
			}
			// assert rest+1 <= 5
			checkedWrite(conv, rest + 1);
		}
		// finally write the two character end of data marker
		checkedWrite(EOD, true);

		flush();
	}

	/**
	 * Calls {@link #finish()} to flush remaining data and write the end-of-data
	 * marker, then closes the underlying stream.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public void close() throws IOException {
		finish();
		super.close();
	}

}
