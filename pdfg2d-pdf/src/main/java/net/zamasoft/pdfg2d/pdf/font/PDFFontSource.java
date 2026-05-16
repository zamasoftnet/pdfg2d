package net.zamasoft.pdfg2d.pdf.font;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.pdf.ObjectRef;

/**
 * A font source that produces {@link PDFFont} instances for use in PDF generation.
 * Each implementation corresponds to a particular PDF font embedding strategy
 * (core, embedded, CID-identity, or CID-keyed).
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface PDFFontSource extends FontSource {
	/**
	 * Enumerates the PDF font embedding strategies supported by this library.
	 */
	public static enum Type {
		/**
		 * Unknown/missing font.
		 */
		MISSING,

		/**
		 * Core PDF font.
		 */
		CORE,

		/**
		 * Embedded font.
		 */
		EMBEDDED,

		/**
		 * CID-Identity external font.
		 */
		CID_IDENTITY,

		/**
		 * CID-Keyed font.
		 */
		CID_KEYED;
	}

	/**
	 * Returns the font type.
	 * 
	 * @return the font type
	 */
	public Type getType();

	/**
	 * Creates a new {@link PDFFont} instance with the given PDF resource name and object reference.
	 *
	 * @param name    the PDF resource name used to reference this font within page content streams
	 * @param fontRef the indirect object reference for the font dictionary in the PDF file
	 * @return the newly created PDF font
	 */
	public PDFFont createFont(String name, ObjectRef fontRef);
}
