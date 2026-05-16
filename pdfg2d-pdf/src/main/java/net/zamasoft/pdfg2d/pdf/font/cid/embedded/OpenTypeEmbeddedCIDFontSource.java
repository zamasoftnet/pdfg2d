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
