package net.zamasoft.pdfg2d.pdf.font.cid.embedded;

import java.io.File;
import java.io.IOException;

import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDFontSource;

/**
 * A {@link CIDFontSource} backed by an OpenType font file that is fully
 * embedded in the PDF output.  The font program is subset-embedded so that
 * only the glyphs actually used in the document are written into the file.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class OpenTypeEmbeddedCIDFontSource extends OpenTypeFontSource implements CIDFontSource {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new font source from an OpenType font file.
	 *
	 * @param otfFont   the OpenType font file (TTF/OTF)
	 * @param index     the zero-based font index within a TTC collection, or
	 *                  {@code 0} for single-font files
	 * @param direction the primary writing direction of the font
	 * @throws IOException if the font file cannot be read
	 */
	public OpenTypeEmbeddedCIDFontSource(File otfFont, int index, Direction direction) throws IOException {
		super(otfFont, index, direction);
	}

	/**
	 * 永続フォント索引からの再構築です(2026-08-01、FontIndex参照)。
	 * ファイルI/Oを行わない。
	 */
	public OpenTypeEmbeddedCIDFontSource(final File file, final int index, final Direction direction, final short upm,
			final net.zamasoft.pdfg2d.font.BBox bbox, final String fontName, final String[] aliases,
			final boolean italic, final net.zamasoft.pdfg2d.gc.font.FontStyle.Weight weight,
			final net.zamasoft.pdfg2d.gc.font.Panose panose, final short ascent, final short descent,
			final short spaceAdvance, final net.zamasoft.pdfg2d.font.table.GenericCmapFormat cmap,
			final net.zamasoft.pdfg2d.font.table.UvsCmapFormat uvsCmap) {
		super(file, index, direction, upm, bbox, fontName, aliases, italic, weight, panose, ascent, descent,
				spaceAdvance, cmap, uvsCmap);
	}

	/**
	 * Creates a new {@link PDFFont} instance that writes this embedded CID font
	 * into the given PDF object reference.
	 *
	 * @param name    the internal PDF font resource name, or {@code null} to
	 *                auto-assign
	 * @param fontRef the indirect object reference allocated for this font
	 *                dictionary
	 * @return a new {@link OpenTypeEmbeddedCIDFont} instance
	 */
	public PDFFont createFont(String name, ObjectRef fontRef) {
		return new OpenTypeEmbeddedCIDFont(this, name, fontRef);
	}

	/**
	 * Creates a new font instance without binding it to a specific PDF object
	 * reference.  Equivalent to {@code createFont(null, null)}.
	 *
	 * @return a new {@link Font} instance
	 */
	public Font createFont() {
		return this.createFont(null, null);
	}

	/**
	 * Returns {@link net.zamasoft.pdfg2d.pdf.font.PDFFontSource.Type#EMBEDDED},
	 * indicating that the full font program is embedded in the PDF.
	 *
	 * @return {@link net.zamasoft.pdfg2d.pdf.font.PDFFontSource.Type#EMBEDDED}
	 */
	public Type getType() {
		return Type.EMBEDDED;
	}
}
