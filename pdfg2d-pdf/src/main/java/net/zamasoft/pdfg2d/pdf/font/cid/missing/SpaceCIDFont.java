package net.zamasoft.pdfg2d.pdf.font.cid.missing;

import java.io.IOException;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.ObjectRef;

class SpaceCIDFont extends MissingCIDFont {
	private static final long serialVersionUID = 1L;

	public SpaceCIDFont(MissingCIDFontSource source, String name, ObjectRef fontRef) {
		super(source, name, fontRef);
	}

	public short getAdvance(int gid) {
		int c = this.unicodes.get(gid);
		switch (c) {
			// Space characters
			case 0x007F:
			case 0x0020:
			case 0x00A0:
			case 0x2028:
			case 0x2029:
			case 0x202F:
				return (short) 500;
			// Fixed-width spaces (Unicode "General Punctuation"): widths in 1/1000 em
			case 0x2000: // EN QUAD
			case 0x2002: // EN SPACE
			case 0x2007: // FIGURE SPACE (digit width)
				return (short) 500;
			case 0x2001: // EM QUAD
			case 0x2003: // EM SPACE
			case 0x3000: // IDEOGRAPHIC SPACE
				return (short) 1000;
			case 0x2004: // THREE-PER-EM SPACE
				return (short) 333;
			case 0x2005: // FOUR-PER-EM SPACE
			case 0x2008: // PUNCTUATION SPACE
				return (short) 250;
			case 0x2006: // SIX-PER-EM SPACE
				return (short) 167;
			case 0x2009: // THIN SPACE
				return (short) 200;
			case 0x200A: // HAIR SPACE
				return (short) 100;
			case 0x205F: // MEDIUM MATHEMATICAL SPACE (4/18 em)
				return (short) 222;
		}
		return (short) 0;
	}

	public short getWidth(int gid) {
		return (short) 0;
	}

	public void drawTo(GC gc, Text text) throws IOException, GraphicsException {
		// ignore
	}
}
