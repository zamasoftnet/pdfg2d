package net.zamasoft.pdfg2d.pdf.form;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * A list-box or drop-down (combo) choice field ({@code /FT /Ch}), reflecting
 * an HTML {@code <select>}.
 *
 * @param name     the field name
 * @param rect     the widget rectangle (top-left origin)
 * @param options  the option display strings
 * @param selected the initially selected option, or null
 * @param combo    whether a drop-down ({@code true}) or list box
 * @param tooltip  the accessible tooltip, or null
 * @param fontSize the field font size in points (0 = auto)
 * @param readOnly whether the field is read-only
 * @param required whether the field is required
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record ChoiceField(String name, Rectangle2D rect, List<String> options, String selected, boolean combo,
		String tooltip, double fontSize, boolean readOnly, boolean required) implements FormField {
}
