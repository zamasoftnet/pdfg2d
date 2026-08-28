package net.zamasoft.pdfg2d.font;

import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;

/**
 * An abstract implementation of {@link FontSource}.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class AbstractFontSource implements FontSource {
	private static final long serialVersionUID = 1L;
	/**
	 * Default ascent.
	 */
	protected static final short DEFAULT_ASCENT = 860;

	/**
	 * Default descent.
	 */
	protected static final short DEFAULT_DESCENT = 140;

	/**
	 * Default x-height.
	 */
	protected static final short DEFAULT_X_HEIGHT = 500;

	/**
	 * Default cap-height.
	 */
	protected static final short DEFAULT_CAP_HEIGHT = 700;

	private static final String[] EMPTY_STRINGS = new String[0];

	protected String[] aliases;

	protected Weight weight = Weight.W_400;

	protected boolean isItalic = false;

	/** 幅級(OS/2 usWidthClass 1..9)。索引復元・face宣言で上書きされる(2026-08-29)。 */
	protected int widthClass = NORMAL_WIDTH_CLASS;

	/**
	 * Constructs a new AbstractFontSource.
	 */
	public AbstractFontSource() {
		this.aliases = EMPTY_STRINGS;
	}

	@Override
	public String[] getAliases() {
		return this.aliases;
	}

	/**
	 * Sets the italic status of this font source.
	 * 
	 * @param isItalic true if italic, false otherwise
	 */
	public final void setItalic(final boolean isItalic) {
		this.isItalic = isItalic;
	}

	@Override
	public final boolean isItalic() {
		return this.isItalic;
	}

	/**
	 * Sets the weight of this font source.
	 * 
	 * @param weight the font weight
	 */
	public final void setWeight(final Weight weight) {
		this.weight = weight;
	}

	@Override
	public final Weight getWeight() {
		return this.weight;
	}

	/**
	 * 幅級を設定します(2026-08-29)。範囲外の値はOS/2の未定義値
	 * (0や旧仕様の値)として通常幅に丸める。
	 *
	 * @param widthClass OS/2 usWidthClass
	 */
	public final void setWidthClass(final int widthClass) {
		this.widthClass = widthClass >= 1 && widthClass <= 9 ? widthClass : NORMAL_WIDTH_CLASS;
	}

	@Override
	public final int getWidthClass() {
		return this.widthClass;
	}

	@Override
	public String toString() {
		final var buff = new StringBuilder(this.getFontName());
		final var aliases = this.getAliases();
		if (aliases != null && aliases.length > 0) {
			buff.append("; ").append(String.join("; ", aliases));
		}
		buff.append(this.getClass());
		return buff.toString();
	}
}
