package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSource;

/**
 * Manages font resources and their serialization into the PDF document.
 * This class tracks unique font sources and ensures each is correctly
 * mapped to a PDF font resource name.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class FontFlow {
	private final XRefImpl xref;

	private final Map<String, ObjectRef> nameToResourceRef;

	private final PDFFragmentOutputImpl objectsFlow;

	/** Mapping from FontSource to PDFFont. */
	private final Map<FontSource, Font> fonts = new HashMap<>();
	private final List<PDFFont> fontList = new ArrayList<>();

	/**
	 * Constructs a new FontFlow.
	 *
	 * @param nameToResourceRef the map from resource name to object reference used
	 *                          by the PDF resource dictionary
	 * @param objectsFlow       the fragment output to which font objects are written
	 * @param xref              the cross-reference table used to allocate new object
	 *                          numbers
	 * @throws IOException if an I/O error occurs during initialization
	 */
	public FontFlow(final Map<String, ObjectRef> nameToResourceRef, final PDFFragmentOutputImpl objectsFlow,
			final XRefImpl xref) throws IOException {
		this.xref = xref;
		this.nameToResourceRef = nameToResourceRef;
		this.objectsFlow = objectsFlow;
	}

	/**
	 * Returns a {@link Font} for the given {@link FontSource}, creating and
	 * registering it as a PDF resource the first time it is requested.
	 * <p>
	 * If {@code source} is a {@link PDFFontSource}, a new indirect object reference
	 * is allocated and the font is added to the resource dictionary under a
	 * generated name (e.g. {@code F0}, {@code F1}, …).  Non-PDF sources are
	 * created directly without allocating a PDF object.
	 * </p>
	 *
	 * @param source the font source describing the typeface to use
	 * @return the {@link Font} instance associated with {@code source}
	 * @throws IOException if an I/O error occurs while creating the font
	 */
	public Font useFont(final FontSource source) throws IOException {
		var font = this.fonts.get(source);
		if (font != null) {
			return font;
		}

		if (source instanceof final PDFFontSource pdfSource) {
			final var name = "F" + this.fonts.size();
			final var fontRef = this.xref.nextObjectRef();
			this.nameToResourceRef.put(name, fontRef);

			font = pdfSource.createFont(name, fontRef);
			this.fontList.add((PDFFont) font);
		} else {
			font = source.createFont();
		}
		this.fonts.put(source, font);

		return font;
	}

	/**
	 * Writes all registered PDF fonts to the object stream and finalizes the font
	 * flow.
	 * <p>
	 * This method must be called exactly once, after all pages have been rendered,
	 * to flush each {@link PDFFont}'s data (widths, encoding, etc.) into the PDF
	 * output.
	 * </p>
	 *
	 * @throws IOException if an I/O error occurs while writing font data
	 */
	public void close() throws IOException {
		for (final PDFFont font : this.fontList) {
			font.writeTo(this.objectsFlow, this.xref);
		}
	}
}
