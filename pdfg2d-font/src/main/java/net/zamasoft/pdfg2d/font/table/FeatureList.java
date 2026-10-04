package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Represents a feature list in an OpenType font.
 * 
 * @param featureRecords array of feature records
 * @param features       array of features
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public record FeatureList(FeatureRecord[] featureRecords, Feature[] features) {

	/**
	 * Creates a new FeatureList by reading from the given file.
	 * 
	 * @param raf    the file to read from
	 * @param offset the offset to seek to
	 * @throws IOException if an I/O error occurs
	 */
	public FeatureList(final RandomAccessFile raf, final int offset) throws IOException {
		this(readData(raf, offset));
	}

	private FeatureList(FeatureList other) {
		this(other.featureRecords, other.features);
	}

	private static FeatureList readData(final RandomAccessFile raf, final int offset) throws IOException {
		synchronized (raf) {
			raf.seek(offset);
			final int featureCount = raf.readUnsignedShort();
			final FeatureRecord[] featureRecords = new FeatureRecord[featureCount];
			final Feature[] features = new Feature[featureCount];
			for (int i = 0; i < featureCount; i++) {
				featureRecords[i] = FeatureRecord.read(raf);
			}
			for (int i = 0; i < featureCount; i++) {
				features[i] = Feature.read(raf, offset + featureRecords[i].offset());
			}
			return new FeatureList(featureRecords, features);
		}
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		sb.append(this.featureRecords.length).append(':');
		for (final FeatureRecord featureRecord : this.featureRecords) {
			final int tag = featureRecord.tag();
			sb.append((char) ((tag >> 24) & 0xff))
					.append((char) ((tag >> 16) & 0xff))
					.append((char) ((tag >> 8) & 0xff))
					.append((char) ((tag) & 0xff))
					.append('/');
		}
		return sb.toString();
	}
}
