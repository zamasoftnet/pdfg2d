package net.zamasoft.pdfg2d.gc.text;

/**
 * グリフ毎の送り量調整の読み取り専用ビューです(2026-08-01、90点計画
 * 増分12)。
 *
 * <p>
 * 従来はレンダラ・計測系へ{@code double[]}の生配列を渡していたため、
 * どこからでも変更できた(和文詰めとjustificationの二重適用バグの
 * 温床——B5c-2系の教訓)。読み取りはこの型、書き込みは
 * {@link TextImpl#addXAdvance(int, double)}等の意味のある操作だけに
 * 限定することで、不変条件(長さ=グリフ数、変更主体はレイアウト層のみ)を
 * 型で守る。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public interface GlyphAdvances {
	/** グリフ数(調整配列の論理長)を返します。 */
	int size();

	/**
	 * グリフの送り量調整を返します。
	 *
	 * @param glyphIndex グリフ位置(0始まり)
	 * @return 調整量(詰めは負)
	 */
	double get(int glyphIndex);
}
