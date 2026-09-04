package net.zamasoft.pdfg2d.pdf.font.cid.missing;

import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: GenericType0FontFace.java,v 1.2 2005/06/06 04:42:24 harumanx
 *          Exp $
 */
public class SpaceCIDFontSource extends MissingCIDFontSource {
	private static final long serialVersionUID = 1L;

	public static final SpaceCIDFontSource INSTANCES_LTR = new SpaceCIDFontSource(Direction.LTR);
	public static final SpaceCIDFontSource INSTANCES_TB = new SpaceCIDFontSource(Direction.TB);

	SpaceCIDFontSource(Direction direction) {
		super(direction);
	}

	public String getFontName() {
		return "SPACE";
	}

	public boolean canDisplay(int c) {
		switch (c) {
			// Control codes
			case 0x0000:
			case 0x000B:
			case 0x001C:
			case 0x001D:
			case 0x001E:
			case 0x001F:
				// Zero-width spaces
			case 0x200B:
			case 0x200C:
			case 0x200D:
			case 0x200E:
			case 0x200F:
			case 0x202A:
			case 0x202B:
			case 0x202C:
			case 0x202D:
			case 0x202E:
			case 0x2060:
			case 0xFEFF:
				// Space characters
			case 0x007F:
			case 0x0020:
			case 0x00A0:
			case 0x2028:
			case 0x2029:
			case 0x202F:
				// Fixed-width spaces (U+2000..U+200A), medium mathematical space and
				// the ideographic space: drawn as nothing but with their proper
				// advance, so a stray THIN SPACE no longer falls to the missing
				// glyph font (2026-09-04, user report 281f for U+2009).
			case 0x2000:
			case 0x2001:
			case 0x2002:
			case 0x2003:
			case 0x2004:
			case 0x2005:
			case 0x2006:
			case 0x2007:
			case 0x2008:
			case 0x2009:
			case 0x200A:
			case 0x205F:
			case 0x3000:
				return true;
		}
		return false;
	}

	public PDFFont createFont(String name, ObjectRef fontRef) {
		return new SpaceCIDFont(this, name, fontRef);
	}
}
