package net.zamasoft.pdfg2d.font;

import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;

/**
 * A wrapper for {@link FontSource}.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class FontSourceWrapper implements FontSource {
	private static final long serialVersionUID = 0L;
	protected final FontSource source;

	/**
	 * Creates a new FontSourceWrapper.
	 * 
	 * @param source the font source to wrap
	 */
	public FontSourceWrapper(final FontSource source) {
		this.source = source;
	}

	@Override
	public String[] getAliases() {
		return this.source.getAliases();
	}

	@Override
	public boolean canDisplay(final int c) {
		return this.source.canDisplay(c);
	}

	@Override
	public Font createFont() {
		return this.source.createFont();
	}

	@Override
	public Direction getDirection() {
		return this.source.getDirection();
	}

	@Override
	public short getAscent() {
		return this.source.getAscent();
	}

	@Override
	public BBox getBBox() {
		return this.source.getBBox();
	}

	@Override
	public short getCapHeight() {
		return this.source.getCapHeight();
	}

	@Override
	public short getDescent() {
		return this.source.getDescent();
	}

	@Override
	public String getFontName() {
		return this.source.getFontName();
	}

	@Override
	public short getStemH() {
		return this.source.getStemH();
	}

	@Override
	public short getStemV() {
		return this.source.getStemV();
	}

	@Override
	public Weight getWeight() {
		return this.source.getWeight();
	}

	@Override
	public short getXHeight() {
		return this.source.getXHeight();
	}

	@Override
	public short getSpaceAdvance() {
		return this.source.getSpaceAdvance();
	}

	@Override
	public short getEmbeddingLicenseFlags() {
		return this.source.getEmbeddingLicenseFlags();
	}

	@Override
	public boolean isItalic() {
		return this.source.isItalic();
	}

	@Override
	public String toString() {
		return "FontSourceWrapper:" + this.source.toString();
	}
}
