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

	@Override
	public String toString() {
		return "src=" + this.src + "/local=" + this.local + "/index=" + this.index + "/fontFamily=" + this.fontFamily
				+ "/fontWeight=" + this.fontWeight + "/fontStyle=" + this.fontStyle + "/widthClass=" + this.widthClass
				+ "/unicodeRange="
				+ this.unicodeRange + "/panose=" + this.panose + "/cmap=" + this.cmap + "/vcmap=" + this.vcmap;
	}
}

