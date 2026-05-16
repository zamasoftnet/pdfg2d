package net.zamasoft.pdfg2d.pdf.action;

import java.io.IOException;

import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Action that executes JavaScript.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class JavaScriptAction extends Action {
	protected final String script;

	/**
	 * Constructs a JavaScriptAction with the given JavaScript source.
	 *
	 * @param script the JavaScript source to execute
	 */
	public JavaScriptAction(final String script) {
		this.script = script;
	}

	/**
	 * Returns the JavaScript source code to be executed.
	 *
	 * @return the script string
	 */
	public String getScript() {
		return this.script;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws UnsupportedOperationException if the PDF version is earlier than 1.3
	 */
	@Override
	public void writeTo(final PDFOutput out, final PDFParams params) throws IOException {
		super.writeTo(out, params);
		if (params.version().v < PDFParams.Version.V_1_3.v) {
			throw new UnsupportedOperationException("JavaScript Action requires PDF 1.3 or later.");
		}
		out.writeName("S");
		out.writeName("JavaScript");
		out.lineBreak();

		out.writeName("JS");
		out.writeText(this.script);
		out.lineBreak();
	}

	/**
	 * Returns a human-readable string representation of this action.
	 *
	 * @return a string showing the JavaScript source
	 */
	@Override
	public String toString() {
		return "JavaScript: " + this.script;
	}
}
