package net.zamasoft.pdfg2d.pdf.form;

import java.awt.geom.Rectangle2D;

/**
 * A single-line or multi-line text input field ({@code /FT /Tx}).
 *
 * @param name      the field name
 * @param rect      the widget rectangle (top-left origin)
 * @param value     the initial value, or null
 * @param tooltip   the accessible tooltip, or null
 * @param fontSize  the field font size in points (0 = auto)
 * @param multiline whether the field accepts multiple lines
 * @param maxLength the maximum character count, or 0 for unlimited
 * @param readOnly  whether the field is read-only
 * @param required  whether the field is required
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record TextField(String name, Rectangle2D rect, String value, String tooltip, double fontSize,
		boolean multiline, int maxLength, boolean readOnly, boolean required) implements FormField {
}
