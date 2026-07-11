package net.zamasoft.pdfg2d.pdf.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.PDFOutput;

/**
 * Packs stream-less indirect objects into compressed object streams
 * ({@code /ObjStm}, PDF 1.5). Object bodies are buffered in memory; when the
 * per-stream capacity is reached (or on {@link #close()}), one object stream
 * is emitted through the writer's object flow and every contained reference
 * is marked for a type-2 cross-reference entry.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
final class ObjectStreamWriter implements PDFObjectSink {

	/** Objects per object stream; a modest bound keeps buffers small. */
	private static final int MAX_OBJECTS = 100;

	private final PDFWriterImpl writer;

	private final ByteArrayOutputStream buff = new ByteArrayOutputStream(1 << 14);

	private final PDFOutput out;

	private final List<ObjectRefImpl> refs = new ArrayList<>();

	private final List<Integer> offsets = new ArrayList<>();

	ObjectStreamWriter(final PDFWriterImpl writer) {
		this.writer = writer;
		this.out = new PDFOutput(this.buff, writer.getParams().platformEncoding());
		this.out.setPrecision(writer.getParams().precision());
	}

	@Override
	public PDFOutput startObject(final ObjectRef ref) throws IOException {
		if (this.refs.size() >= MAX_OBJECTS) {
			this.flush();
		}
		this.refs.add((ObjectRefImpl) ref);
		this.offsets.add(this.buff.size());
		return this.out;
	}

	@Override
	public void endObject() throws IOException {
		this.out.lineBreak();
	}

	/** Emits the buffered objects as one object stream. */
	void flush() throws IOException {
		if (this.refs.isEmpty()) {
			return;
		}
		this.out.flush();

		// Index part: "objnum offset" pairs preceding the object bodies
		final var header = new StringBuilder();
		for (var i = 0; i < this.refs.size(); ++i) {
			header.append(this.refs.get(i).objectNumber()).append(' ').append(this.offsets.get(i)).append('\n');
		}
		final var headerBytes = header.toString().getBytes(StandardCharsets.US_ASCII);

		final var containerRef = this.writer.xref.nextObjectRef();
		final var flow = this.writer.objectsFlow;
		flow.startObject(containerRef);
		flow.startHash();
		flow.writeName("Type");
		flow.writeName("ObjStm");
		flow.writeName("N");
		flow.writeInt(this.refs.size());
		flow.writeName("First");
		flow.writeInt(headerBytes.length);
		flow.lineBreak();
		try (final var sout = flow.startStreamFromHash(PDFFragmentOutput.Mode.BINARY)) {
			sout.write(headerBytes);
			this.buff.writeTo(sout);
		}
		flow.endObject();

		for (var i = 0; i < this.refs.size(); ++i) {
			this.refs.get(i).setCompressed(containerRef.objectNumber(), i);
		}
		this.refs.clear();
		this.offsets.clear();
		this.buff.reset();
	}

	/** Flushes any remaining buffered objects. */
	void close() throws IOException {
		this.flush();
	}
}
