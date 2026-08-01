package net.zamasoft.pdfg2d.pdf.font.cid.keyed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.PDFGraphicsOutput;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDFont;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDUtils;
import net.zamasoft.pdfg2d.pdf.font.cid.CMap;
import net.zamasoft.pdfg2d.pdf.font.cid.WArray;
import net.zamasoft.pdfg2d.pdf.font.util.PDFFontUtils;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

class CIDKeyedFont extends CIDFont {
	private static final long serialVersionUID = 1L;

	private final CMap cmap;

	CIDKeyedFont(CIDKeyedFontSource source, String name, ObjectRef fontRef, CMap cmap) {
		super(source, name, fontRef);
		assert cmap != null;
		this.cmap = cmap;
	}

	transient private CharsetEncoder charsetEncoder = null;

	transient private CharBuffer cbuff = null;

	private static final int CALLOC = 512;

	transient private ByteBuffer bbuff = null;

	public void drawTo(GC gc, Text text) throws IOException, GraphicsException {
		assert text.getCharCount() > 0;
		if (gc instanceof PDFGC) {
			PDFGraphicsOutput out = ((PDFGC) gc).getPDFGraphicsOutput();
			// Native character encoding
			char[] ch = text.getChars();
			int clen = text.getCharCount();
			final net.zamasoft.pdfg2d.gc.text.GlyphAdvances xadvances = text.xAdvances();
			if (xadvances == null) {
				this.writeByte8(out, ch, 0, clen);
				out.writeOperator("Tj");
			} else {
				out.startArray();
				byte[] clusterLengths = text.getClusterLengths();
				int glyphCount = text.getGlyphCount();
				int len = 0;
				int off = 0;
				double size = text.getFontMetrics().getFontSize();
				for (int i = 0; i < glyphCount; ++i) {
					double xadvance = xadvances.get(i);
					if (xadvance != 0) {
						// Use negative values for vertical writing (SPEC PDF1.3 8.7.1.1)
						if (this.source.getDirection() == Direction.TB) {
							xadvance = -xadvance;
						}
						if (len > 0) {
							this.writeByte8(out, ch, off, len);
							off += len;
							len = 0;
						}
						out.writeReal(-xadvance * 1000.0 / size);
					}
					len += clusterLengths[i];
				}
				if (len > 0) {
					this.writeByte8(out, ch, off, len);
				}
				out.endArray();
				out.writeOperator("TJ");
			}
		} else {
			CIDKeyedFontSource source = (CIDKeyedFontSource) this.getFontSource();
			PDFFontUtils.drawAwtFont(gc, source, source.getAwtFont(), text);
		}
	}

	private void writeByte8(PDFGraphicsOutput out, char[] ch, int off, int len) throws IOException {
		if (this.charsetEncoder == null) {
			this.charsetEncoder = this.cmap.getCIDTable().getCharset().newEncoder();
			this.cbuff = CharBuffer.allocate(CALLOC);
		}
		this.charsetEncoder.reset();
		int buffLen = (int) Math.ceil(len * this.charsetEncoder.maxBytesPerChar());
		if (this.bbuff == null || this.bbuff.capacity() < buffLen) {
			this.bbuff = ByteBuffer.allocate(buffLen);
		}
		while (len > 0) {
			this.cbuff.clear();
			int llen = Math.min(len, CALLOC);
			for (int i = 0; i < llen; ++i) {
				char c = ch[i + off];
				// Convert NBSP (\u00A0) to space
				c = (c == '\u00A0') ? '\u0020' : c;
				this.cbuff.put(c);
			}
			len -= llen;
			off += llen;
			this.cbuff.flip();
			this.charsetEncoder.encode(this.cbuff, this.bbuff, len == 0);
		}
		this.charsetEncoder.flush(this.bbuff);
		this.bbuff.flip();
		out.writeBytes8(this.bbuff.array(), this.bbuff.arrayOffset(), this.bbuff.limit());
		this.bbuff.clear();
	}

	public void writeTo(PDFFragmentOutput out, XRef xref) throws IOException {
		CIDKeyedFontSource source = (CIDKeyedFontSource) this.source;

		// Main font
		out.startObject(this.fontRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("Font");
		out.lineBreak();
		out.writeName("Subtype");
		out.writeName("Type0");
		out.lineBreak();
		out.writeName("BaseFont");
		out.writeName(source.getFontName());
		out.lineBreak();
		out.writeName("DescendantFonts");
		out.startArray();
		ObjectRef xfontRef = xref.nextObjectRef();
		out.writeObjectRef(xfontRef);
		out.endArray();
		out.lineBreak();
		out.writeName("Encoding");
		out.writeName(this.getEncoding());
		out.endHash();
		out.endObject();

		// Descendant font
		out.startObject(xfontRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("Font");
		out.lineBreak();
		out.writeName("Subtype");
		out.writeName("CIDFontType2");
		out.lineBreak();
		out.writeName("BaseFont");
		out.writeName(source.getFontName());
		out.lineBreak();
		out.writeName("FontDescriptor");
		ObjectRef fontDescRef = xref.nextObjectRef();
		out.writeObjectRef(fontDescRef);
		out.lineBreak();
		out.writeName("CIDSystemInfo");
		out.startHash();
		out.writeName("Registry");
		out.writeString(this.getRegistry());
		out.writeName("Ordering");
		out.writeString(this.getOrdering());
		out.writeName("Supplement");
		out.writeInt(this.getSupplement());
		out.lineBreak();
		out.writeName("CIDToGIDMap");
		out.writeName("Identity");
		out.lineBreak();
		out.endHash();

		// WArray
		CIDUtils.writeWArray(out, source.getWArray());

		out.endHash();
		out.endObject();

		// Font descriptor
		out.startObject(fontDescRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("FontDescriptor");
		out.lineBreak();
		out.writeName("FontName");
		out.writeName(source.getFontName());
		out.lineBreak();
		CIDUtils.writeFlagsAndPanose(out, source);
		out.writeName("FontBBox");
		BBox bbox = source.getBBox();
		out.startArray();
		out.writeInt(bbox.llx());
		out.writeInt(bbox.lly());
		out.writeInt(bbox.urx());
		out.writeInt(bbox.ury());
		out.endArray();
		out.lineBreak();
		out.writeName("StemV");
		out.writeInt(92);
		out.lineBreak();
		out.writeName("ItalicAngle");
		out.writeInt(0);
		out.lineBreak();
		out.writeName("CapHeight");
		out.writeInt(source.getCapHeight());
		out.lineBreak();
		out.writeName("XHeight");
		out.writeInt(source.getXHeight());
		out.lineBreak();
		out.writeName("Ascent");
		out.writeInt(source.getAscent());
		out.lineBreak();
		out.writeName("Descent");
		out.writeInt(-source.getDescent());
		out.lineBreak();

		out.endHash();
		out.endObject();
	}

	public int toGID(int c) {
		// Convert Unicode to CID code
		int gid = this.cmap.getCIDTable().toCID(c);
		return gid;
	}

	public short getAdvance(int gid) {
		if (this.source.getDirection() == Direction.TB) {
			return 1000;
		}
		return this.getWidth(gid);
	}

	public short getWidth(int gid) {
		CIDKeyedFontSource source = (CIDKeyedFontSource) this.source;
		WArray wa = source.getWArray();
		short w = wa.getWidth(gid);
		return w;
	}

	String getEncoding() {
		return this.cmap.getEncoding();
	}

	String getOrdering() {
		return this.cmap.getOrdering();
	}

	String getRegistry() {
		return this.cmap.getRegistry();
	}

	int getSupplement() {
		return this.cmap.getSupplement();
	}
}