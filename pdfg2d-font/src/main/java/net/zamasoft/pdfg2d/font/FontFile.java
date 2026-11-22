package net.zamasoft.pdfg2d.font;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.zip.InflaterInputStream;

import net.zamasoft.pdfg2d.font.util.BufferedRandomAccessFile;

/**
 * Provides access to TTC (TrueType Collection), TTF (TrueType / OpenType), and
 * WOFF (Web Open Font Format) files.  WOFF files are transparently decompressed
 * to a temporary file on construction; TTC files expose each contained font via
 * an index.
 *
 * <p>Example usage:
 * <pre>{@code
 * var fontFile = new FontFile(new File("NotoSans.ttf"));
 * try (var font = fontFile.getFont()) {
 *     // use font ...
 * }
 * }</pre>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class FontFile implements AutoCloseable {

	/** The last-modified timestamp of the source file at construction time. */
	public final long timestamp;

	private final OpenTypeFont[] fonts;
	private final long[] offsets;
	private final File file;

	/**
	 * Returns the decompressed sfnt file (a temporary file for WOFF/WOFF2;
	 * raw TTF/TTC files are returned unchanged). Exposed on 2026-08-20 because
	 * static instantiation of variable fonts requires sfnt bytes.
	 */
	public File getSfntFile() {
		return this.file;
	}
	private final boolean woff;

	/**
	 * @param file The TTC, TTF, or WOFF file.
	 * @throws IOException If an I/O error occurs.
	 */
	public FontFile(final File file) throws IOException {
		this.timestamp = file.lastModified();
		try (final RandomAccessFile raf = new BufferedRandomAccessFile(file, "r")) {
			final byte[] tagBytes = new byte[4];
			raf.readFully(tagBytes);
			String tag = new String(tagBytes, StandardCharsets.ISO_8859_1);

			if ("wOF2".equals(tag)) {
				// WOFF2 (supported since 2026-08-05). Woff2Decoder handles Brotli decompression
				// and reversing glyph table transforms. From here onward, treat it as a regular sfnt.
				this.file = Woff2Decoder.extract(raf, file);
				this.woff = true;
				this.offsets = new long[] { 0 };
				this.fonts = new OpenTypeFont[1];
			} else if ("wOFF".equals(tag)) {
				// WOFF
				this.file = extractWoff(raf, file);
				this.woff = true;

				// Re-read header from extracted file to check if it's TTC
				try (final RandomAccessFile tempRaf = new BufferedRandomAccessFile(this.file, "r")) {
					tempRaf.readFully(tagBytes);
					tag = new String(tagBytes, StandardCharsets.ISO_8859_1);
					if ("ttcf".equals(tag)) {
						tempRaf.skipBytes(4);
						final int numFonts = tempRaf.readInt();
						this.offsets = new long[numFonts];
						this.fonts = new OpenTypeFont[numFonts];
						for (int i = 0; i < numFonts; i++) {
							this.offsets[i] = tempRaf.readInt();
						}
					} else {
						// Single TTF
						this.offsets = new long[] { 0 };
						this.fonts = new OpenTypeFont[1];
					}
				}
			} else {
				this.file = file;
				this.woff = false;
				if ("ttcf".equals(tag)) {
					// TTC
					raf.skipBytes(4);
					final int numFonts = raf.readInt();
					this.offsets = new long[numFonts];
					this.fonts = new OpenTypeFont[numFonts];
					for (int i = 0; i < numFonts; i++) {
						this.offsets[i] = raf.readInt();
					}
				} else {
					// Single TTF
					this.offsets = new long[] { 0 };
					this.fonts = new OpenTypeFont[1];
				}
			}
		}
	}

	/**
	 * Largest sfnt that a WOFF or WOFF2 file may unpack to. Web fonts stay far below it; the limit only keeps a
	 * crafted font from filling the temporary directory or the heap.
	 */
	static final int MAX_SFNT_SIZE = 64 * 1024 * 1024;

	/**
	 * Largest plausible ratio between the unpacked sfnt and the WOFF or WOFF2 file it comes from. Font data
	 * compresses by a factor of a few; beyond a hundred the declared sizes are not those of the data.
	 */
	static final int MAX_EXPANSION = 100;

	private File extractWoff(final RandomAccessFile raf, final File originalFile) throws IOException {
		final File tempFile = File.createTempFile("pdfg2d-font-", ".dat");
		boolean extracted = false;
		try {
			writeWoffAsSfnt(raf, originalFile, tempFile);
			extracted = true;
		} finally {
			if (!extracted) {
				// A font that cannot be unpacked must not leave its partial copy behind.
				tempFile.delete();
			}
		}
		tempFile.deleteOnExit();
		return tempFile;
	}

	private static void writeWoffAsSfnt(final RandomAccessFile raf, final File originalFile, final File tempFile)
			throws IOException {
		try (final DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(tempFile)))) {
			final long fileLength = raf.length();
			raf.seek(4); // Skip signature
			final int version = raf.readInt();
			raf.skipBytes(4); // length
			final int numTables = raf.readUnsignedShort();
			raf.skipBytes(2); // reserved
			final long totalSfntSize = raf.readInt() & 0xFFFFFFFFL;

			// The table directory is stored as it is, so a font has no more tables than its own length has room for.
			if (44L + 20L * numTables > fileLength) {
				throw new IOException("Truncated WOFF table directory: " + originalFile);
			}
			// The lengths in the directory decide how much is written below. They are bounded by the size the
			// header declares for the whole sfnt, by a fixed limit, and by what the file could plausibly unpack to.
			final long sizeLimit = Math.min(totalSfntSize, Math.min(MAX_SFNT_SIZE, fileLength * MAX_EXPANSION));

			// Write SFNT header
			out.writeInt(version);
			out.writeShort(numTables);
			out.writeShort(0); // searchRange
			out.writeShort(0); // entrySelector
			out.writeShort(0); // rangeShift

			// Write table directory
			int outOffset = out.size() + numTables * 16;
			final byte[] tagBuff = new byte[4];

			// Store directory info to avoid seeking back to directory table repeatedly if
			// possible,
			// but determining optimal order is complex. We stick to seek.
			// However, to avoid 'new FileInputStream', we use a wrapper around 'raf'.

			// We need to read all directory entries first because we write them
			// sequentially
			// to the output file BEFORE writing data.
			final long[] inOffsets = new long[numTables];
			final long[] compLens = new long[numTables];
			final int[] origLens = new int[numTables];
			final int[] outOffsets = new int[numTables];

			for (int i = 0; i < numTables; i++) {
				raf.seek(44 + i * 20); // TableDirectory entries start at 44
				raf.readFully(tagBuff);
				final long inOffset = raf.readInt() & 0xFFFFFFFFL;
				final long compLen = raf.readInt() & 0xFFFFFFFFL;
				final long origLength = raf.readInt() & 0xFFFFFFFFL;
				final int origChecksum = raf.readInt();

				// Each table, and all of them together with the header and the directory, must fit in the limit.
				if (outOffset + ((origLength + 3) & ~3L) > sizeLimit) {
					throw new IOException("WOFF table sizes exceed the size of the font: " + originalFile);
				}
				// The data of a table lies inside the file.
				if (inOffset + compLen > fileLength) {
					throw new IOException("Truncated WOFF table data: " + originalFile);
				}
				final int origLen = (int) origLength;

				// Keep positions for later data reading
				inOffsets[i] = inOffset;
				compLens[i] = compLen;
				origLens[i] = origLen;

				// Write Directory Entry in SFNT format
				out.write(tagBuff);
				out.writeInt(origChecksum);
				out.writeInt(outOffset);
				out.writeInt(origLen);

				outOffsets[i] = outOffset;
				// **Align tables on 4-byte boundaries** (2026-08-05). sfnt requires each table
				// to start on a 4-byte boundary, so reconstruction from WOFF also requires
				// realignment and padding (WOFF specification §"Decoding").
				// Writing tables back-to-back without alignment makes CFF (OTTO) WOFF files
				// unreadable with `OffSize must be 1-4: 0`.
				outOffset += (origLen + 3) & ~3;
			}

			// Write table data
			final byte[] buff = new byte[4096]; // Increased buffer size

			// Inner class to wrap RAF as InputStream
			class RAFInputStream extends InputStream {
				private long remaining;

				RAFInputStream(long length) {
					this.remaining = length;
				}

				@Override
				public int read() throws IOException {
					if (this.remaining <= 0)
						return -1;
					final int b = raf.read();
					if (b >= 0)
						this.remaining--;
					return b;
				}

				@Override
				public int read(final byte[] b, final int off, final int len) throws IOException {
					if (this.remaining <= 0)
						return -1;
					final int toRead = (int) Math.min(len, this.remaining);
					final int read = raf.read(b, off, toRead);
					if (read > 0)
						this.remaining -= read;
					return read;
				}

				@Override
				public void close() {
					// Do not close the underlying RAF
				}
			}

			for (int i = 0; i < numTables; i++) {
				raf.seek(inOffsets[i]);
				final long compLen = compLens[i];
				final int origLen = origLens[i];
				int remainder = origLen;

				InputStream source = new RAFInputStream(compLen);
				if (compLen != origLen) {
					source = new InflaterInputStream(source);
				}

				try (final InputStream in = source) {
					int len;
					while (remainder > 0 && (len = in.read(buff, 0, Math.min(remainder, buff.length))) != -1) {
						out.write(buff, 0, len);
						remainder -= len;
					}
				}

				// A table that ends before its declared length is a broken font. Filling the rest with zeros
				// would let a font of a few bytes write as much as its directory claims.
				if (remainder > 0) {
					throw new IOException("Truncated WOFF table data: " + originalFile);
				}

				// Padding to the next 4-byte boundary.
				for (int pad = (4 - (origLen & 3)) & 3; pad > 0; --pad) {
					out.writeByte(0);
				}
			}
		}
	}

	/**
	 * Returns true if file is a Font Collection (TTC).
	 *
	 * @return true if TTC.
	 */
	public boolean isFontCollection() {
		return this.fonts != null && this.fonts.length > 1;
	}

	/**
	 * Returns the number of fonts in the file.
	 * Always 1 for TTF/OTF.
	 *
	 * @return Number of fonts.
	 */
	public int getNumFonts() {
		return this.fonts.length;
	}

	/**
	 * Returns the font at the specified index.
	 * For TTF, index must be 0.
	 *
	 * @param i Index of the font.
	 * @return The font.
	 * @throws IOException If I/O error occurs.
	 */
	public synchronized OpenTypeFont getFont(final int i) throws IOException {
		if (this.fonts[i] == null) {
			final RandomAccessFile fontRaf = new BufferedRandomAccessFile(this.file, "r");
			fontRaf.seek(this.offsets[i]);
			if (this.woff) {
				this.fonts[i] = new OpenTypeFont(fontRaf, () -> {
					if (!this.file.delete()) {
						// ignore
					}
				});
			} else {
				this.fonts[i] = new OpenTypeFont(fontRaf);
			}
		}
		return this.fonts[i];
	}

	/**
	 * Returns the first font.
	 *
	 * @return The first font.
	 * @throws IOException If I/O error occurs.
	 */
	public OpenTypeFont getFont() throws IOException {
		return this.getFont(0);
	}

	/**
	 * Closes opened fonts and deletes temporary files decompressed from WOFF/WOFF2 (2026-10-05).
	 * Does not delete raw TTF/TTC files.
	 *
	 * <p>
	 * Calls that use only {@link #getSfntFile()} (variable font detection and copy creation) leave the decompressed
	 * result without an owner unless they close it here. Without opening a font, {@link OpenTypeFont} cleanup
	 * does not run either, so the files stayed until process exit in long-running servers. Do not use after closing.
	 * </p>
	 */
	@Override
	public synchronized void close() {
		for (final OpenTypeFont font : this.fonts) {
			if (font != null) {
				font.close();
			}
		}
		if (this.woff) {
			this.file.delete();
		}
	}
}
