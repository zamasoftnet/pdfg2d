package net.zamasoft.pdfg2d.font;

import java.io.Serializable;

/**
 * Represents a bounding box defined by two points: lower-left and upper-right.
 * All values are expressed in the font's design units (typically 1/1000 em).
 *
 * @param llx the x-coordinate of the lower-left corner
 * @param lly the y-coordinate of the lower-left corner
 * @param urx the x-coordinate of the upper-right corner
 * @param ury the y-coordinate of the upper-right corner
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record BBox(short llx, short lly, short urx, short ury) implements Serializable {

	@Override
	public String toString() {
		return "[llx=" + this.llx + ",lly=" + this.lly + ",urx=" + this.urx + ",ury=" + this.ury + "]";
	}
}