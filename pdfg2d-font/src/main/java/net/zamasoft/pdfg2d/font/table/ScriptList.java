package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Represents a script list in an OpenType font.
 * 
 * @param scriptRecords array of script records
 * @param scripts       array of scripts
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public record ScriptList(ScriptRecord[] scriptRecords, Script[] scripts) {

	/**
	 * Creates a new ScriptList by reading from the given file.
	 * 
	 * @param raf    the file to read from
	 * @param offset the offset to seek to
	 * @throws IOException if an I/O error occurs
	 */
	protected ScriptList(final RandomAccessFile raf, final int offset) throws IOException {
		this(readData(raf, offset));
	}

	private ScriptList(ScriptList other) {
		this(other.scriptRecords, other.scripts);
	}

	private static ScriptList readData(final RandomAccessFile raf, final int offset) throws IOException {
		synchronized (raf) {
			raf.seek(offset);
			final int scriptCount = raf.readUnsignedShort();
			final ScriptRecord[] scriptRecords = new ScriptRecord[scriptCount];
			final Script[] scripts = new Script[scriptCount];
			for (int i = 0; i < scriptCount; i++) {
				scriptRecords[i] = ScriptRecord.read(raf);
			}
			for (int i = 0; i < scriptCount; i++) {
				scripts[i] = Script.read(raf, offset + scriptRecords[i].offset());
			}
			return new ScriptList(scriptRecords, scripts);
		}
	}

	public int getScriptCount() {
		return this.scriptRecords.length;
	}

	public ScriptRecord getScriptRecord(final int i) {
		return this.scriptRecords[i];
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		sb.append(this.scriptRecords.length).append(':');
		for (final ScriptRecord record : this.scriptRecords) {
			final int tag = record.tag();
			sb.append((char) ((tag >> 24) & 0xff))
					.append((char) ((tag >> 16) & 0xff))
					.append((char) ((tag >> 8) & 0xff))
					.append((char) ((tag) & 0xff))
					.append('/');
		}
		return sb.toString();
	}
}
