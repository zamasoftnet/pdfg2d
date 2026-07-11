package net.zamasoft.pdfg2d.pdf.impl;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import net.zamasoft.pdfg2d.pdf.util.io.FinishingDeflaterOutputStream;

import net.zamasoft.zstream.io.util.FragmentOutputAdapter;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.util.codec.ASCII85OutputStream;
import net.zamasoft.pdfg2d.pdf.util.codec.ASCIIHexOutputStream;
import net.zamasoft.pdfg2d.pdf.util.io.FastBufferedOutputStream;

/**
 * Concrete implementation of {@link PDFFragmentOutput} that serializes PDF
 * objects, dictionaries, and streams into a {@link FragmentedOutput} segment.
 * <p>
 * Each instance owns one fragment of the fragmented output (identified by
 * {@link #id}).  When a new in-line fragment is needed (e.g. to defer writing
 * a stream length value), {@link #forkFragment()} inserts a new fragment
 * immediately before the current anchor and returns a fresh
 * {@code PDFFragmentOutputImpl} targeting that new fragment.
 * </p>
 * <p>
 * Encryption is applied transparently if an {@link Encryption} instance is
 * configured on the owning {@link PDFWriterImpl}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class PDFFragmentOutputImpl extends PDFFragmentOutput {
	private final PDFWriterImpl pdfWriter;

	/** ID of the fragment currently being written; updated by {@link #forkFragment()}. */
	private int id;
	/** ID of the fragment that follows this one in output order; {@code -1} if none. */
	private int anchorId = -1;

	/** Number of bytes written to this fragment so far. */
	private int length = 0;

	/** Fragment that will receive the deferred stream-length value. */
	private PDFFragmentOutputImpl streamLengthFlow = null;

	/** Byte offset within this fragment where the current stream body started. */
	private int startStreamPosition = 0;

	/** The PDF object currently open, or {@code null} when between objects. */
	private ObjectRef currentRef;

	/** Shared scratch buffer for encoding/compression pipelines; lazily allocated. */
	private byte[] buffer = null;

	/**
	 * Creates a new fragment output.
	 *
	 * @param out        underlying byte sink for this fragment
	 * @param pdfWriter  owning writer that provides configuration and shared state
	 * @param id         fragment ID for the initial fragment
	 * @param nextId     anchor fragment ID ({@code -1} if this is the last fragment)
	 * @param currentRef the PDF object currently being written, or {@code null}
	 * @throws IOException if an I/O error occurs
	 */
	public PDFFragmentOutputImpl(final OutputStream out, final PDFWriterImpl pdfWriter, final int id,
			final int nextId, final ObjectRef currentRef) throws IOException {
		super(out, pdfWriter.getParams().platformEncoding());
		this.setPrecision(pdfWriter.getParams().precision());
		this.pdfWriter = pdfWriter;
		this.id = id;
		this.anchorId = nextId;
		this.currentRef = currentRef;
	}

	/**
	 * Returns a lazily-allocated scratch buffer shared by encoding pipelines in
	 * this fragment.
	 *
	 * @return scratch buffer of length {@link PDFWriterImpl#BUFFER_SIZE}
	 */
	protected byte[] getBuffer() {
		if (this.buffer == null) {
			this.buffer = new byte[PDFWriterImpl.BUFFER_SIZE];
		}
		return this.buffer;
	}

	/**
	 * Creates a new fragment and links it to the current output structure.
	 * 
	 * @return A new PDFFragmentOutputImpl instance.
	 * @throws IOException If an I/O error occurs.
	 */
	protected PDFFragmentOutputImpl forkFragment() throws IOException {
		this.close();
		final var builder = this.pdfWriter.builder;
		final var nextId = this.pdfWriter.nextId();
		if (this.anchorId == -1) {
			builder.addFragment();
		} else {
			builder.insertFragmentBefore(this.anchorId);
		}
		final var streamOut = new FragmentOutputAdapter(builder, nextId);
		this.id = this.pdfWriter.nextId();
		if (this.anchorId == -1) {
			builder.addFragment();
		} else {
			builder.insertFragmentBefore(this.anchorId);
		}
		final var newFragOut = new PDFFragmentOutputImpl(streamOut, this.pdfWriter, nextId, this.id, this.currentRef);
		this.out = new FragmentOutputAdapter(builder, this.id);
		this.length = 0;
		return newFragOut;
	}

	/** Byte offset within the fragment where the current object header started. */
	private int objectStartPosition = 0;

	@Override
	public void startObject(final ObjectRef ref) throws IOException {
		this.breakBefore();
		this.objectStartPosition = this.getLength();
		((ObjectRefImpl) ref).setPosition(this.id, this.getLength());
		this.writeInt(ref.objectNumber());
		this.writeInt(ref.generationNumber());
		this.writeOperator("obj");
		this.lineBreak();
		if (this.currentRef != null) {
			throw new IllegalStateException("Already inside object: " + this.currentRef);
		}
		this.currentRef = ref;
	}

	@Override
	public void writeObjectRef(final ObjectRef ref) throws IOException {
		if (this.currentRef != null) {
			this.pdfWriter.xref.addDependency(this.currentRef, ref);
		}
		super.writeObjectRef(ref);
	}

	@Override
	public void endObject() throws IOException {
		this.writeLine("endobj");
		if (this.currentRef == null) {
			throw new IllegalStateException("Already outside object");
		}
		((ObjectRefImpl) this.currentRef).setLength(this.getLength() - this.objectStartPosition);
		this.currentRef = null;
	}

	@Override
	public OutputStream startStream(final Mode mode) throws IOException {
		if (this.streamLengthFlow != null) {
			throw new IllegalStateException("Cannot nest streams: " + this.streamLengthFlow);
		}
		this.startHash();

		return this.startStreamFromHash(mode);
	}

	@Override
	public OutputStream startStreamFromHash(final Mode mode) throws IOException {
		if (this.streamLengthFlow != null) {
			throw new IllegalStateException("Cannot nest streams: " + this.streamLengthFlow);
		}

		final var compression = this.pdfWriter.params.compression();
		switch (mode) {
			case RAW -> {
			}
			case ASCII -> {
				switch (compression) {
					case ASCII -> {
						this.writeName("Filter");
						this.startArray();
						this.writeName("ASCII85Decode");
						this.writeName("FlateDecode");
						this.endArray();
						this.breakBefore();
					}
					case BINARY -> {
						this.writeName("Filter");
						this.startArray();
						this.writeName("FlateDecode");
						this.endArray();
						this.breakBefore();
					}
					default -> {
					}
				}
			}
			case BINARY -> {
				switch (compression) {
					case NONE -> {
						this.writeName("Filter");
						this.startArray();
						this.writeName("ASCIIHexDecode");
						this.endArray();
						this.breakBefore();
					}
					case ASCII -> {
						this.writeName("Filter");
						this.startArray();
						this.writeName("ASCII85Decode");
						this.writeName("FlateDecode");
						this.endArray();
						this.breakBefore();
					}
					case BINARY -> {
						this.writeName("Filter");
						this.startArray();
						this.writeName("FlateDecode");
						this.endArray();
						this.breakBefore();
					}
				}
			}
		}

		this.writeName("Length");
		this.write(' ');
		this.streamLengthFlow = this.forkFragment();
		this.lineBreak();
		this.endHash();
		this.writeLine("stream");
		this.flush();
		this.startStreamPosition = this.getLength();

		var flowOut = (OutputStream) new FilterOutputStream(this) {
			@Override
			public void close() throws IOException {
				PDFFragmentOutputImpl.this.endStream();
			}
		};

		// Apply encryption if enabled
		if (this.pdfWriter.encryption != null) {
			flowOut = this.pdfWriter.encryption.getEncryptor(this.currentRef).getOutputStream(flowOut);
		}

		// Apply final output encoding/compression based on mode and configuration
		final var output = switch (mode) {
			case RAW -> flowOut;
			case ASCII -> {
				final var encodedOut = switch (compression) {
					case ASCII -> new FinishingDeflaterOutputStream(new ASCII85OutputStream(flowOut));
					case BINARY -> new FinishingDeflaterOutputStream(flowOut);
					default -> flowOut;
				};
				yield new FastBufferedOutputStream(encodedOut, this.getBuffer());
			}
			case BINARY -> {
				final var encodedOut = switch (compression) {
					case NONE -> new ASCIIHexOutputStream(flowOut);
					case ASCII -> new FinishingDeflaterOutputStream(new ASCII85OutputStream(flowOut));
					case BINARY -> new FinishingDeflaterOutputStream(flowOut);
				};
				yield new FastBufferedOutputStream(encodedOut, this.getBuffer());
			}
		};
		return output;
	}

	/**
	 * Finalizes the current stream fragment, calculating its length and writing
	 * the end-of-stream markers.
	 * 
	 * @throws IOException If an I/O error occurs.
	 */
	protected void endStream() throws IOException {
		this.streamLengthFlow.writeInt(this.getLength() - this.startStreamPosition);
		this.streamLengthFlow.close();
		this.streamLengthFlow = null;
		this.startStreamPosition = 0;
		// Required EOL before endstream in some PDF profiles
		this.lineBreak();
		this.writeLine("endstream");
	}

	@Override
	public void writeBytes16(final int c) throws IOException {
		if (this.pdfWriter.encryption == null) {
			super.writeBytes16(c);
			return;
		}
		final var data = new byte[2];
		data[0] = (byte) ((c >> 8) & 0xFF);
		data[1] = (byte) (c & 0xFF);
		this.writeEncryptedBytes8(data, 0, data.length);
	}

	@Override
	public void writeBytes16(final int[] a, final int off, final int len) throws IOException {
		if (this.pdfWriter.encryption == null) {
			super.writeBytes16(a, off, len);
			return;
		}
		final var data = new byte[len * 2];
		for (var i = 0; i < len; ++i) {
			final var c = a[i + off];
			data[i * 2] = (byte) ((c >> 8) & 0xFF);
			data[i * 2 + 1] = (byte) (c & 0xFF);
		}
		this.writeEncryptedBytes8(data, 0, data.length);
	}

	/**
	 * Writes encrypted bytes. If the encryptor uses block encryption, it encrypts
	 * the entire block; otherwise, it encrypts in-place.
	 * 
	 * @param data Byte array to encrypt and write.
	 * @param off  Offset in the buffer.
	 * @param len  Length of data.
	 * @throws IOException If an I/O error occurs.
	 */
	protected void writeEncryptedBytes8(final byte[] data, final int off, final int len) throws IOException {
		final var encryptor = this.pdfWriter.encryption.getEncryptor(this.currentRef);
		final byte[] outData;
		final int outOff;
		final int outLen;
		if (encryptor.isBlock()) {
			outData = encryptor.blockEncrypt(data, off, len);
			outOff = 0;
			outLen = outData.length;
		} else {
			encryptor.fastEncrypt(data, off, len);
			outData = data;
			outOff = off;
			outLen = len;
		}
		super.writeBytes8(outData, outOff, outLen);
	}

	@Override
	public void writeString(final String str) throws IOException {
		if (this.pdfWriter.encryption == null) {
			super.writeString(str);
			return;
		}
		final var data = str.getBytes(this.nameEncoding);
		this.writeEncryptedBytes8(data, 0, data.length);
	}

	@Override
	public void writeText(final String text) throws IOException {
		if (this.pdfWriter.encryption == null) {
			super.writeText(text);
			return;
		}
		this.writeUTF16(text);
	}

	@Override
	public void writeUTF16(final String text) throws IOException {
		if (this.pdfWriter.encryption == null) {
			super.writeUTF16(text);
			return;
		}
		final var data = new byte[text.length() * 2 + 2];
		data[0] = (byte) 0xFE;
		data[1] = (byte) 0xFF;
		for (var i = 0; i < text.length(); ++i) {
			final var c = text.charAt(i);
			data[i * 2 + 2] = (byte) ((c >> 8) & 0xFF);
			data[i * 2 + 3] = (byte) (c & 0xFF);
		}
		this.writeEncryptedBytes8(data, 0, data.length);
	}

	/**
	 * Returns the number of bytes written to this fragment so far.
	 *
	 * @return byte count
	 */
	protected int getLength() {
		return this.length;
	}

	/**
	 * Returns the ID of the fragment currently being written.
	 *
	 * @return fragment ID
	 */
	protected int getId() {
		return this.id;
	}

	@Override
	public void write(final byte[] buff, final int off, final int len) throws IOException {
		super.write(buff, off, len);
		this.length += len;
	}

	@Override
	public void write(final byte[] buff) throws IOException {
		super.write(buff);
		this.length += buff.length;
	}

	@Override
	public void write(final int c) throws IOException {
		super.write(c);
		this.length++;
	}

	@Override
	public void close() throws IOException {
		if (this.streamLengthFlow != null) {
			throw new IllegalStateException("Stream not closed");
		}
		if (this.out == null) {
			throw new IllegalStateException("Already closed");
		}
		super.close();
		this.out = null;
	}
}
