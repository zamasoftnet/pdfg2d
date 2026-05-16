package net.zamasoft.pdfg2d.pdf.font.cid;

/**
 * Signals that a CMap file contains invalid or unrecognised data.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CMapException extends Exception {
	private static final long serialVersionUID = 0;

	/**
	 * Constructs a new exception with the specified detail message.
	 *
	 * @param message the detail message describing the parse error
	 */
	public CMapException(String message) {
		super(message);
	}

	/**
	 * Constructs a new exception with the specified detail message and cause.
	 *
	 * @param message the detail message describing the parse error
	 * @param t       the cause of this exception
	 */
	public CMapException(String message, Throwable t) {
		super(message, t);
	}
}
