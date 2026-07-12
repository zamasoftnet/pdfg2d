package net.zamasoft.pdfg2d.pdf.form;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * A group of mutually-exclusive radio buttons that share one field name,
 * reflecting an HTML {@code <input type="radio">} group. Unlike the per-widget
 * {@link CheckBoxField}, a radio group is written as a single AcroForm field
 * ({@code /FT /Btn} with the {@code Radio} flag) whose {@code /Kids} are the
 * individual button widgets, so exactly one may be selected.
 *
 * @param name          the shared field name ({@code /T})
 * @param tooltip       the accessible tooltip ({@code /TU}), or null
 * @param selectedValue the on-value of the initially selected button, or null
 *                      for none
 * @param readOnly      whether the group is read-only
 * @param required      whether a selection is required
 * @param buttons       the individual buttons (widgets); each on-value should be
 *                      distinct within the group
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record RadioGroup(String name, String tooltip, String selectedValue, boolean readOnly, boolean required,
		List<Button> buttons) {

	/**
	 * One radio button widget within a {@link RadioGroup}.
	 *
	 * @param rect    the widget rectangle in page coordinates (top-left origin)
	 * @param onValue the button's export value (its {@code /AP} on-state name)
	 */
	public record Button(Rectangle2D rect, String onValue) {
	}
}
