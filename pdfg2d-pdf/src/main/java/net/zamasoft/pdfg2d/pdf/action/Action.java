package net.zamasoft.pdfg2d.pdf.action;

import java.io.IOException;

import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Base class representing a PDF action that can be triggered by user
 * interaction or document events (PDF spec section 12.6).
 * Actions may be chained by setting a sequence of {@code next} actions.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class Action {

	private Action[] next = null;

	/**
	 * Returns the sequence of actions to execute after this action completes,
	 * or {@code null} if no next actions are defined.
	 *
	 * @return the next action array, or {@code null}
	 */
	public Action[] getNext() {
		return this.next;
	}

	/**
	 * Sets the sequence of actions to execute after this action completes.
	 *
	 * @param next the next action array, or {@code null} for none
	 */
	public void setNext(Action[] next) {
		this.next = next;
	}

	/**
	 * Writes this action's PDF dictionary entries to the given output stream.
	 * Subclasses must override this method and call {@code super.writeTo()} first.
	 *
	 * @param out    the PDF output stream to write to
	 * @param params the PDF document parameters
	 * @throws IOException if an I/O error occurs
	 */
	public void writeTo(PDFOutput out, PDFParams params) throws IOException {
		out.writeName("Type");
		out.writeName("Action");
		out.lineBreak();

		if (this.next != null && this.next.length > 0) {
			out.writeName("Next");
			if (this.next.length == 1) {
				out.startHash();
				this.next[0].writeTo(out, params);
				out.endHash();
			} else {
				out.startArray();
				for (int i = 0; i < this.next.length; ++i) {
					out.startHash();
					this.next[i].writeTo(out, params);
					out.endHash();
				}
				out.endArray();
			}
			out.lineBreak();
		}
	}
}
