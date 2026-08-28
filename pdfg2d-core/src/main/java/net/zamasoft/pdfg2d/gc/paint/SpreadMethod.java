package net.zamasoft.pdfg2d.gc.paint;

/**
 * グラデーションの定義域外の塗り方(SVGのspreadMethod相当、2026-08-29)。
 */
public enum SpreadMethod {
	/** 端の色で埋める(既定)。 */
	PAD,
	/** 周期を繰り返す(CSSのrepeating-*-gradient)。 */
	REPEAT,
	/** 折り返して繰り返す。 */
	REFLECT
}
