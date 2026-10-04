package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Language system table.
 * 
 * @param featureIndex indices of features in the feature list
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public record LangSys(int[] featureIndex) {

	/**
	 * Creates a new LangSys by reading from the given file.
	 * 
	 * @param raf the file to read from
	 * @return a new LangSys instance
	 * @throws IOException if an I/O error occurs
	 */
	protected static LangSys read(final RandomAccessFile raf) throws IOException {
		raf.readUnsignedShort(); // lookupOrder
		raf.readUnsignedShort(); // reqFeatureIndex
		final int featureCount = raf.readUnsignedShort();
		final int[] featureIndex = new int[featureCount];
		for (int i = 0; i < featureCount; i++) {
			featureIndex[i] = raf.readUnsignedShort();
		}
		return new LangSys(featureIndex);
	}

}
