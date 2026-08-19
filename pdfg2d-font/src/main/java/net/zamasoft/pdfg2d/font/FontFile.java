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
public class FontFile {

	/** The last-modified timestamp of the source file at construction time. */
	public final long timestamp;

	private final OpenTypeFont[] fonts;
	private final long[] offsets;
	private final File file;

	/**
	 * 解凍済みの sfnt ファイルを返します(WOFF/WOFF2 は一時ファイル、
	 * 生の TTF/TTC はそのまま。2026-08-20、可変フォントの静的
	 * インスタンス化が sfnt バイト列を必要とするため公開)。
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
				// WOFF2(2026-08-05対応)。Brotli伸長と字形表の組み替え戻しは
				// Woff2Decoderが行う。ここから先は普通のsfntとして扱える
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

	private File extractWoff(final RandomAccessFile raf, final File originalFile) throws IOException {
		final File tempFile = File.createTempFile("pdfg2d-font-", ".dat");
		try (final DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(tempFile)))) {
			raf.seek(4); // Skip signature
			final int version = raf.readInt();
			raf.skipBytes(4); // length
			final short numTables = raf.readShort();
			raf.skipBytes(2); // reserved
			raf.skipBytes(4); // totalSfntSize
			raf.skipBytes(22); // majorVersion to metaOrigLength

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
			final int[] inOffsets = new int[numTables];
			final int[] compLens = new int[numTables];
			final int[] origLens = new int[numTables];
			final int[] outOffsets = new int[numTables];

			for (int i = 0; i < numTables; i++) {
				raf.seek(44 + i * 20); // TableDirectory entries start at 44
				raf.readFully(tagBuff);
				raf.skipBytes(4); // offset
				raf.skipBytes(4); // compLen
				final int origLen = raf.readInt();
				final int origChecksum = raf.readInt();

				// Read positions for later data reading
				raf.seek(44 + i * 20 + 4);
				inOffsets[i] = raf.readInt();
				compLens[i] = raf.readInt();
				origLens[i] = origLen;

				// Write Directory Entry in SFNT format
				out.write(tagBuff);
				out.writeInt(origChecksum);
				out.writeInt(outOffset);
				out.writeInt(origLen);

				outOffsets[i] = outOffset;
				// **表は4バイト境界へ揃える**(2026-08-05)。sfnt は各表を
				// 4バイト境界から始める決まりで、WOFF から組み直すときも
				// 揃え直しと詰め物が要る(WOFF仕様 §「Decoding」)。
				// 揃えずに詰めて書くと CFF 版(OTTO)の WOFF が
				// `OffSize must be 1-4: 0` で読めなくなる。
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
				final int compLen = compLens[i];
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

				while (remainder > 0) {
					out.writeByte(0);
					remainder--;
				}

				// 4バイト境界までの詰め物
				for (int pad = (4 - (origLen & 3)) & 3; pad > 0; --pad) {
					out.writeByte(0);
				}
			}
		}
		tempFile.deleteOnExit();
		return tempFile;
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
}
