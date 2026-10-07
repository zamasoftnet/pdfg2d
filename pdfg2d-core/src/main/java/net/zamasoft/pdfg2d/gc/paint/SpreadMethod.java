package net.zamasoft.pdfg2d.gc.paint;

/**
 * How to paint outside a gradient's domain (equivalent to SVG spreadMethod, 2026-08-29).
 */
public enum SpreadMethod {
	/** Fills with the endpoint colors (default). */
	PAD,
	/** Repeats the period (CSS repeating-*-gradient). */
	REPEAT,
	/** Repeats with alternating direction. */
	REFLECT
}
