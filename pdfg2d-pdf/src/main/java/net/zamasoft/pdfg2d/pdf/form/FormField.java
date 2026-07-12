package net.zamasoft.pdfg2d.pdf.form;

import java.awt.geom.Rectangle2D;

/**
 * An interactive PDF form field (AcroForm), reflecting an HTML form control.
 * The field's widget appearance is placed at {@link #rect()} in page
 * coordinates (top-left origin, like annotations).
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public sealed interface FormField permits TextField, CheckBoxField, ChoiceField, PushButtonField {

	/** Returns the fully-qualified field name ({@code /T}). */
	String name();

	/** Returns the widget rectangle in page coordinates (top-left origin). */
	Rectangle2D rect();

	/** Returns the accessible tooltip / alternate name ({@code /TU}), or null. */
	String tooltip();

	/** Returns whether the field is read-only. */
	boolean readOnly();

	/** Returns whether the field must be filled before submitting. */
	boolean required();
}
