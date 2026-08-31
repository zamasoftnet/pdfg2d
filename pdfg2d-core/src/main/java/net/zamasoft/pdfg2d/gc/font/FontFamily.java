package net.zamasoft.pdfg2d.gc.font;

import java.io.Serializable;

import net.zamasoft.pdfg2d.gc.font.util.FontUtils;

/**
 * Represents a font family.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record FontFamily(GenericFamily genericFamily, String name) implements Serializable {

	/**
	 * Represents a generic font family.
	 */
	public enum GenericFamily {
		NONE, SERIF, SANS_SERIF, CURSIVE, FANTASY, MONOSPACE,
		/**
		 * {@code fangsong}: the Chinese imitation-Song face. CSS Fonts lists it
		 * beside the other generics; it needs its own entry because no other
		 * generic stands for it (2026-08-31).
		 */
		FANGSONG
	}

	public static final FontFamily SERIF_VALUE = new FontFamily(GenericFamily.SERIF, "serif");

	public static final FontFamily SANS_SERIF_VALUE = new FontFamily(GenericFamily.SANS_SERIF, "sans-serif");

	public static final FontFamily CURSIVE_VALUE = new FontFamily(GenericFamily.CURSIVE, "cursive");

	public static final FontFamily FANTASY_VALUE = new FontFamily(GenericFamily.FANTASY, "fantasy");

	public static final FontFamily MONOSPACE_VALUE = new FontFamily(GenericFamily.MONOSPACE, "monospace");

	public static final FontFamily FANGSONG_VALUE = new FontFamily(GenericFamily.FANGSONG, "fangsong");

	/**
	 * Creates a FontFamily from a name.
	 * 
	 * @param name the font family name
	 * @return the created FontFamily
	 */
	public static FontFamily create(final String name) {
		if (name == null) {
			return FontFamily.SERIF_VALUE;
		}
		return switch (name.toLowerCase(java.util.Locale.ROOT)) {
			case "serif" -> FontFamily.SERIF_VALUE;
			case "cursive" -> FontFamily.CURSIVE_VALUE;
			case "fantasy" -> FontFamily.FANTASY_VALUE;
			case "monospace" -> FontFamily.MONOSPACE_VALUE;
			case "sans-serif" -> FontFamily.SANS_SERIF_VALUE;
			case "fangsong" -> FontFamily.FANGSONG_VALUE;
			default -> new FontFamily(name);
		};
	}

	/**
	 * Creates a new FontFamily.
	 * 
	 * @param name the font family name
	 */
	public FontFamily(final String name) {
		this(GenericFamily.NONE, name);
	}

	/**
	 * Returns whether this is a generic family.
	 * 
	 * @return true if generic family, false otherwise
	 */
	public boolean isGenericFamily() {
		return this.genericFamily != GenericFamily.NONE;
	}

	/**
	 * Returns the generic family code.
	 * 
	 * @return the generic family code
	 */
	public GenericFamily getGenericFamily() {
		return this.genericFamily;
	}

	/**
	 * Returns the font family name.
	 * 
	 * @return the family name
	 */
	public String getName() {
		return this.name;
	}

	@Override
	public String toString() {
		return this.getName();
	}

	@Override
	public boolean equals(final Object o) {
		if (o instanceof FontFamily a) {
			if (a.isGenericFamily() != this.isGenericFamily()) {
				return false;
			}
			if (a.isGenericFamily()) {
				return a.genericFamily == this.genericFamily;
			}
			return FontUtils.normalizeName(a.name).equals(FontUtils.normalizeName(this.name));
		}
		return false;
	}

	@Override
	public int hashCode() {
		if (this.isGenericFamily()) {
			return this.genericFamily.ordinal();
		}
		return FontUtils.normalizeName(this.name).hashCode();
	}
}
