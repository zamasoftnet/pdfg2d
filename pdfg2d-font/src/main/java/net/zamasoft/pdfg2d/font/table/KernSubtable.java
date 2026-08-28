package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Interface for kerning subtables.
 * 
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public interface KernSubtable {

	/**
	 * Returns the number of kerning pairs.
	 * 
	 * @return the kerning pair count
	 */
	int getKerningPairCount();

	/**
	 * Returns a kerning pair at the given index.
	 *
	 * @param i the index
	 * @return the kerning pair
	 */
	KerningPair getKerningPair(int i);

	/**
	 * Returns whether this subtable holds plain horizontal pair kerning
	 * (coverage: horizontal, not minimum, not cross-stream).
	 *
	 * @return true if applicable to horizontal advance adjustment
	 */
	boolean isHorizontal();

	/**
	 * Reads a KernSubtable from the given file.
	 *
	 * @param raf the file to read from
	 * @return the kerning subtable, or null if unknown format
	 * @throws IOException if an I/O error occurs
	 */
	static KernSubtable read(final RandomAccessFile raf) throws IOException {
		final long start = raf.getFilePointer();
		raf.readUnsignedShort(); // version
		final int length = raf.readUnsignedShort();
		final int coverage = raf.readUnsignedShort();
		final int format = coverage >> 8;
		// coverage: bit0=horizontal, bit1=minimum, bit2=cross-stream
		final boolean horizontal = (coverage & 0x01) != 0 && (coverage & 0x06) == 0;

		final KernSubtable table = switch (format) {
			case 0 -> KernSubtableFormat0.read(raf, horizontal);
			case 2 -> KernSubtableFormat2.read(raf);
			default -> null;
		};
		// 次の副表の先頭へ進める。未知形式は宣言長で読み飛ばす。lengthは
		// 16bitで、大きなペア表では溢れて実際より短い値が入っている実フォント
		// があるため(Arial等の既知問題)、解析済み位置より後退はしない
		final long declaredEnd = start + length;
		if (raf.getFilePointer() < declaredEnd) {
			raf.seek(declaredEnd);
		}
		return table;
	}
}
