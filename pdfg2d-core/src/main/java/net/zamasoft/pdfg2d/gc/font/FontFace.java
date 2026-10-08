package net.zamasoft.pdfg2d.gc.font;

import java.awt.Font;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;

/**
 * Represents a font face configuration.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class FontFace {
	public Source src = null;
	public int index = 0;
	public Font local = null;
	public FontFamilyList fontFamily = null;
	public Weight fontWeight = Weight.W_400;
	public Style fontStyle = Style.NORMAL;
	/**
	 * Width class of the {@code font-stretch} descriptor (OS/2 usWidthClass 1..9,
	 * 5=normal, 2026-08-29). In the @font-face path, this declared value determines
	 * the font's width class instead of the file's OS/2 value (as with weight/style).
	 */
	public int widthClass = net.zamasoft.pdfg2d.font.FontSource.NORMAL_WIDTH_CLASS;
	public UnicodeRangeList unicodeRange = null;
	public Panose panose = null;
	public String cmap = null, vcmap = null;

	/**
	 * Axis coordinates for variable fonts (font-variation-settings descriptor,
	 * 2026-08-20). Null means defaults. If specified, used as the coordinates
	 * for static instantiation ({@code VariableFontInstancer}).
	 */
	public java.util.Map<String, Double> variationSettings = null;

	/**
	 * Finds the source of a face when the face is first needed, or {@code null} for a face whose {@link #src} or
	 * {@link #local} is already set (2026-10-08). A document declares faces it never uses (a site's style sheet that
	 * imports a CJK web font in 61 unicode-range files, about 20MB, for a page in English); reading them all when the
	 * style sheet was parsed doubled the time of such a page. A manager that supports it keeps the face pending until a
	 * font style names its family.
	 */
	public Loader loader = null;

	/** Finds the source of a pending face (see {@link FontFace#loader}). */
	@FunctionalInterface
	public interface Loader {
		/**
		 * Tries the sources of the face in order. For each one that can be opened, sets {@link FontFace#src} or
		 * {@link FontFace#local} and calls {@code reader}; stops at the first that it reads.
		 *
		 * @param face   the face
		 * @param reader reads the face with its source set
		 * @return {@code false} when no source could be read
		 */
		public boolean load(FontFace face, Reader reader);
	}

	/** Reads a face whose source is set (see {@link Loader}). */
	@FunctionalInterface
	public interface Reader {
		/**
		 * Reads the face.
		 *
		 * @param face the face, its {@link FontFace#src} or {@link FontFace#local} set
		 * @throws java.io.IOException if the font cannot be read
		 */
		public void read(FontFace face) throws java.io.IOException;
	}

	@Override
	public String toString() {
		return "src=" + this.src + "/local=" + this.local + "/index=" + this.index + "/fontFamily=" + this.fontFamily
				+ "/fontWeight=" + this.fontWeight + "/fontStyle=" + this.fontStyle + "/widthClass=" + this.widthClass
				+ "/unicodeRange="
				+ this.unicodeRange + "/panose=" + this.panose + "/cmap=" + this.cmap + "/vcmap=" + this.vcmap;
	}
}

