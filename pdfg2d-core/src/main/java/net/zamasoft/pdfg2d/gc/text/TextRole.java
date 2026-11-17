package net.zamasoft.pdfg2d.gc.text;

import net.zamasoft.pdfg2d.gc.GC;

/**
 * What the text drawn in a {@link GC#beginTextRole(TextRole)} scope is to a reader, beyond its characters: whether
 * a reading aid reads it, skips it, or reads it in place of other text.
 *
 * <p>
 * Text outside every scope is body text. Scopes nest; the innermost role that names a kind of text wins, so the base
 * of a ruby inside a footnote is footnote text and its annotation is ruby. {@link #RUBY} and {@link #RUBY_BASE} name
 * no kind of their own: they only tie a ruby's annotations to its base.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public enum TextRole {
	/**
	 * One ruby unit: its base ({@link #RUBY_BASE}) and its annotations ({@link #RUBY_TEXT}), drawn in any order. A
	 * back-end ties the annotations to the base when the unit closes.
	 */
	RUBY(false),

	/** The base text of the enclosing {@link #RUBY} unit. It keeps the kind of the text around the unit. */
	RUBY_BASE(false),

	/** An annotation of the enclosing {@link #RUBY} unit, read in place of its base. */
	RUBY_TEXT(true),

	/** An emphasis mark over a character. It is decoration and is not read. */
	EMPHASIS(true),

	/** Content of a page margin box, such as a running head or a page number. */
	MARGIN(true),

	/** The body of a footnote in the page's footnote area. */
	FOOTNOTE(true);

	private final boolean kind;

	TextRole(final boolean kind) {
		this.kind = kind;
	}

	/**
	 * Returns whether this role names a kind of text of its own. {@link #RUBY} and {@link #RUBY_BASE} do not: text in
	 * them keeps the kind of the scope around them.
	 *
	 * @return whether this role names a kind of text
	 */
	public boolean isKind() {
		return this.kind;
	}
}
