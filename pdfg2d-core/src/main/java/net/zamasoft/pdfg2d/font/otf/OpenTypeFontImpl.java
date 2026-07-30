package net.zamasoft.pdfg2d.font.otf;

import net.zamasoft.pdfg2d.util.CharList;

/**
 * An implementation of {@link OpenTypeFont} that caching unicode characters.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class OpenTypeFontImpl extends OpenTypeFont {
	private static final long serialVersionUID = 1L;

	protected final CharList unicodes = new CharList();

	/**
	 * Creates a new OpenTypeFontImpl.
	 * 
	 * @param source the font source
	 */
	public OpenTypeFontImpl(final OpenTypeFontSource source) {
		super(source);
	}

	@Override
	public int toGID(final int c) {
		final int gid = super.toGID(c);
		this.unicodes.set(gid, (char) c);
		return gid;
	}

	@Override
	public int toGID(final int c, final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		final int gid = super.toGID(c, features);
		this.unicodes.set(gid, (char) c);
		return gid;
	}

	@Override
	protected int toChar(final int gid) {
		return this.unicodes.get(gid);
	}
}
