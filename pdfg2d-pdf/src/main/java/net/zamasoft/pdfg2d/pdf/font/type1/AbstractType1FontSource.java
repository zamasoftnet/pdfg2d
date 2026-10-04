package net.zamasoft.pdfg2d.pdf.font.type1;

import java.awt.Font;
import java.util.Set;
import java.util.TreeSet;

import net.zamasoft.pdfg2d.font.AbstractFontSource;
import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSource;
import net.zamasoft.pdfg2d.pdf.font.type1.AFMFontInfo.AFMGlyphInfo;
import net.zamasoft.pdfg2d.pdf.font.util.PDFFontUtils;
import net.zamasoft.pdfg2d.util.IntList;

/**
 * Standard Type1 font source.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class AbstractType1FontSource extends AbstractFontSource implements PDFFontSource {
	private static final long serialVersionUID = 1L;

	protected final AFMFontInfo fontInfo;

	private final BBox bbox;

	private final short spaceAdvance;

	transient protected Font awtFont = null;

	protected AbstractType1FontSource(AFMFontInfo fontInfo) {
		this.fontInfo = fontInfo;

		Set<String> aliases = new TreeSet<String>();
		if (fontInfo.familyName != null) {
			aliases.add(fontInfo.familyName);
		}
		if (fontInfo.fullName != null) {
			aliases.add(fontInfo.fullName);
		}
		this.aliases = aliases.toArray(new String[aliases.size()]);

		this.isItalic = fontInfo.italic;
		this.weight = fontInfo.weight;
		this.bbox = fontInfo.bbox;
		this.spaceAdvance = ((AFMGlyphInfo) fontInfo.nameToGi.get("space")).advance;
	}

	protected abstract GlyphInfo[] getGidToGi();

	protected abstract IntList getCidToGid();

	protected synchronized Font getAwtFont() {
		if (this.awtFont == null) {
			this.awtFont = PDFFontUtils.toAwtFont(this);
		}
		return this.awtFont;
	}

	public Direction getDirection() {
		return Direction.LTR;
	}

	public String getFontName() {
		return this.fontInfo.fontName;
	}

	public Type getType() {
		return Type.CORE;
	}

	short getAdvance(int gid) {
		if (!this.canDisplayGID(gid)) {
			return 0;
		}
		GlyphInfo gi = this.getGidToGi()[gid];
		return gi.advance;
	}

	/** AFM の KPX の値(負なら詰める)です。first の後ろに second が続く組。 */
	short getKerning(int first, int second) {
		if (!this.canDisplayGID(first)) {
			return 0;
		}
		GlyphInfo gi = this.getGidToGi()[first];
		return gi.getKerning(second);
	}

	int getLigature(int gid, int cid) {
		if (!this.canDisplayGID(gid)) {
			return -1;
		}
		GlyphInfo gi = this.getGidToGi()[gid];
		return gi.getLigature(this.getCidToGid().get(cid));
	}

	abstract int toGID(int c);

	abstract String getEncoding();

	public boolean canDisplay(int c) {
		int gid = this.toGID(c);
		return this.canDisplayGID(gid);
	}

	private boolean canDisplayGID(int gid) {
		if (gid < 0 || gid >= this.getGidToGi().length) {
			return false;
		}
		GlyphInfo gi = this.getGidToGi()[gid];
		if (gi == null) {
			return false;
		}
		return true;
	}

	/**
	 * @return this.Returns the bbox.
	 */
	public BBox getBBox() {
		return this.bbox;
	}

	/**
	 * @return this.Returns the ascent.
	 */
	public short getAscent() {
		return this.fontInfo.ascent;
	}

	/**
	 * @return this.Returns the capHeight.
	 */
	public short getCapHeight() {
		return this.fontInfo.capHeight;
	}

	/**
	 * @return this.Returns the descent.
	 */
	public short getDescent() {
		return this.fontInfo.descent;
	}

	/**
	 * @return this.Returns the stemh.
	 */
	public short getStemH() {
		return this.fontInfo.stemh;
	}

	/**
	 * @return this.Returns the stemv.
	 */
	public short getStemV() {
		return this.fontInfo.stemv;
	}

	/**
	 * @return this.Returns the xHeight.
	 */
	public short getXHeight() {
		return this.fontInfo.xHeight;
	}

	public short getSpaceAdvance() {
		return this.spaceAdvance;
	}

	public PDFFont createFont(String name, ObjectRef fontRef) {
		return new Type1Font(this, name, this.getEncoding(), fontRef);
	}

	public net.zamasoft.pdfg2d.font.Font createFont() {
		return this.createFont(null, null);
	}
}
