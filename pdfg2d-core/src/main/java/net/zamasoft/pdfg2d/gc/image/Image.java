package net.zamasoft.pdfg2d.gc.image;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * Represents an image.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface Image {
	/**
	 * 固有寸法の種別です(css-images-3 §sizing、2026-08-27)。
	 * ラスタ画像は常にSIZE。SVGはwidth/height属性が無ければviewBoxの
	 * 縦横比だけを持ち(RATIO)、viewBoxも無ければ寸法情報なし(NONE)。
	 * 背景描画のbackground-size:autoはこれで既定サイズ規則を分岐する
	 * (SIZE=原寸、RATIO=contain制約、NONE=配置領域いっぱい)。
	 */
	public enum Intrinsic {
		SIZE, RATIO, NONE;
	}

	/**
	 * 固有寸法の種別を返します({@link Intrinsic}参照)。
	 * {@link #getWidth()}/{@link #getHeight()}はRATIO/NONEでも描画用の
	 * 具体値(viewBox寸法・既定300x150)を返す——本メソッドは
	 * その値が「固有寸法」か「代用値」かを区別する。
	 *
	 * @return 固有寸法の種別
	 */
	public default Intrinsic getIntrinsic() {
		return Intrinsic.SIZE;
	}

	/**
	 * Returns the image width.
	 * 
	 * @return the width
	 */
	public double getWidth();

	/**
	 * Returns the image height.
	 * 
	 * @return the height
	 */
	public double getHeight();

	/**
	 * Draws the image.
	 * 
	 * @param gc the graphics context
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void drawTo(final GC gc) throws GraphicsException;

	/**
	 * Returns the alternative string for the image.
	 * 
	 * @return the alternative string
	 */
	public String getAltString();
}
