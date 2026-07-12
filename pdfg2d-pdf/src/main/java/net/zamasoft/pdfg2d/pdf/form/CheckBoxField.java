package net.zamasoft.pdfg2d.pdf.form;

import java.awt.geom.Rectangle2D;

/**
 * A checkbox field ({@code /FT /Btn}). A radio button is a checkbox sharing a
 * field name with a distinct {@link #onValue()} per option.
 *
 * @param name     the field name (shared across radio options)
 * @param rect     the widget rectangle (top-left origin)
 * @param onValue  the export value when checked (the "on" appearance state)
 * @param checked  whether initially checked
 * @param radio    whether this is a radio button (mutually exclusive by name)
 * @param tooltip  the accessible tooltip, or null
 * @param readOnly whether the field is read-only
 * @param required whether the field is required
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record CheckBoxField(String name, Rectangle2D rect, String onValue, boolean checked, boolean radio,
		String tooltip, boolean readOnly, boolean required) implements FormField {
}
