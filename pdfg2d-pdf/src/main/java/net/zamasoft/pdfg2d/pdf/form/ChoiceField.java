package net.zamasoft.pdfg2d.pdf.form;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * A list-box or drop-down (combo) choice field ({@code /FT /Ch}), reflecting
 * an HTML {@code <select>}.
 * <p>
 * An option has a display string and an export value, like the text and the
 * {@code value} attribute of an HTML {@code <option>}. When
 * {@link #exportValues()} is {@code null} the display strings are also the
 * export values.
 * </p>
 *
 * @param name         the field name
 * @param rect         the widget rectangle (top-left origin)
 * @param options      the option display strings
 * @param selected     the export value of the initially selected option (its
 *                     display string when there are no export values), or
 *                     null
 * @param combo        whether a drop-down ({@code true}) or list box
 * @param tooltip      the accessible tooltip, or null
 * @param fontSize     the field font size in points (0 = auto)
 * @param readOnly     whether the field is read-only
 * @param required     whether the field is required
 * @param exportValues the option export values, one for each of
 *                     {@code options} in the same order, or null when the
 *                     display strings are the export values
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record ChoiceField(String name, Rectangle2D rect, List<String> options, String selected, boolean combo,
		String tooltip, double fontSize, boolean readOnly, boolean required, List<String> exportValues)
		implements FormField {

	public ChoiceField {
		// Each option has its export value when export values are given
		if (exportValues != null && options != null && exportValues.size() != options.size()) {
			throw new IllegalArgumentException("Export values must match the options: " + exportValues.size()
					+ " export values for " + options.size() + " options.");
		}
	}

	/**
	 * Creates a choice field whose option display strings are also its export
	 * values.
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
	 */
	public ChoiceField(final String name, final Rectangle2D rect, final List<String> options, final String selected,
			final boolean combo, final String tooltip, final double fontSize, final boolean readOnly,
			final boolean required) {
		this(name, rect, options, selected, combo, tooltip, fontSize, readOnly, required, null);
	}
}
