package net.zamasoft.pdfg2d.pdf.form;

import java.awt.geom.Rectangle2D;

/**
 * A push button ({@code /FT /Btn} with the pushbutton flag), reflecting an
 * HTML {@code <button>} or {@code <input type=button>}.
 *
 * @param name     the field name
 * @param rect     the widget rectangle (top-left origin)
 * @param caption  the button caption
 * @param tooltip  the accessible tooltip, or null
 * @param readOnly whether the field is read-only
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record PushButtonField(String name, Rectangle2D rect, String caption, String tooltip, boolean readOnly)
		implements FormField {
	@Override
	public boolean required() {
		return false;
	}
}
