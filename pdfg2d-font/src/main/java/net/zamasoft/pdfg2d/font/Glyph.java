package net.zamasoft.pdfg2d.font;

import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;

/**
 * An individual glyph within a font.
 * 
 * @param path       the outline path of the glyph
 * @param charString the raw charstring data for CFF fonts
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 */
public record Glyph(GeneralPath path, byte[] charString) {

	/**
	 * 描かれる輪郭を1つも持たないglyphかどうかを返します。
	 *
	 * <p>
	 * cmapに登録がありながら字形が空のフォントが実在します
	 * (JejuGothic系の漢字、Adobe Blankの全字)。これを「表示できる」と
	 * 扱うと代替フォントへ落ちず警告も出ないまま文字が消えるため、
	 * フォント選択がこの判定を使います(2026-09-01)。
	 * </p>
	 *
	 * @return 移動と閉じだけで、線も曲線も無いなら{@code true}
	 */
	public boolean isBlank() {
		if (this.path == null) {
			return true;
		}
		final double[] coords = new double[6];
		for (final PathIterator i = this.path.getPathIterator(null); !i.isDone(); i.next()) {
			final int type = i.currentSegment(coords);
			if (type != PathIterator.SEG_MOVETO && type != PathIterator.SEG_CLOSE) {
				return false;
			}
		}
		return true;
	}
}
