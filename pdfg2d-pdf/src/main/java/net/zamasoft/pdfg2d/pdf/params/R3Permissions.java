package net.zamasoft.pdfg2d.pdf.params;

/**
 * PDF access permissions for encryption revision 3 (RC4 variable-length,
 * PDF 1.4+).
 * <p>
 * Extends the revision 2 permission set with four additional bits:
 * filling interactive form fields, extracting content for accessibility,
 * assembling the document, and high-quality printing. These bits are only
 * meaningful for revision 3 (and later) readers.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class R3Permissions extends R2Permissions {
	/** Permission bit for filling in existing interactive form fields (bit 9). */
	public static final int FILL = 1 << 8;

	/**
	 * Permission bit for extracting text and graphics for accessibility or other
	 * purposes (bit 10).
	 */
	public static final int EXTRACT = 1 << 9;

	/**
	 * Permission bit for assembling the document — inserting, rotating, or deleting
	 * pages and creating navigation elements (bit 11).
	 */
	public static final int ASSEMBLE = 1 << 10;

	/**
	 * Permission bit for printing in high quality (bit 12).
	 * When denied, only low-quality (degraded) printing may be performed.
	 */
	public static final int PRINT_HIGH = 1 << 11;

	@Override
	public Type getType() {
		return Type.R3;
	}

	@Override
	public int getFlags() {
		return this.flags;
	}

	/**
	 * Returns whether assembling the document (inserting, rotating, or deleting
	 * pages) is permitted.
	 *
	 * @return {@code true} if document assembly is permitted
	 */
	public boolean isAssemble() {
		return (this.flags & ASSEMBLE) != 0;
	}

	/**
	 * Sets whether assembling the document is permitted.
	 *
	 * @param assemble {@code true} to permit document assembly
	 */
	public void setAssemble(final boolean assemble) {
		if (assemble) {
			this.flags |= ASSEMBLE;
		} else {
			this.flags &= ~ASSEMBLE;
		}
	}

	/**
	 * Returns whether extracting text and graphics for accessibility is permitted.
	 *
	 * @return {@code true} if content extraction is permitted
	 */
	public boolean isExtract() {
		return (this.flags & EXTRACT) != 0;
	}

	/**
	 * Sets whether extracting text and graphics for accessibility is permitted.
	 *
	 * @param extract {@code true} to permit content extraction
	 */
	public void setExtract(final boolean extract) {
		if (extract) {
			this.flags |= EXTRACT;
		} else {
			this.flags &= ~EXTRACT;
		}
	}

	/**
	 * Returns whether filling in interactive form fields is permitted.
	 *
	 * @return {@code true} if form filling is permitted
	 */
	public boolean isFill() {
		return (this.flags & FILL) != 0;
	}

	/**
	 * Sets whether filling in interactive form fields is permitted.
	 *
	 * @param fill {@code true} to permit form filling
	 */
	public void setFill(final boolean fill) {
		if (fill) {
			this.flags |= FILL;
		} else {
			this.flags &= ~FILL;
		}
	}

	/**
	 * Returns whether high-quality printing is permitted.
	 * When {@code false}, only low-quality (degraded) printing may be performed.
	 *
	 * @return {@code true} if high-quality printing is permitted
	 */
	public boolean isPrintHigh() {
		return (this.flags & PRINT_HIGH) != 0;
	}

	/**
	 * Sets whether high-quality printing is permitted.
	 *
	 * @param printHigh {@code true} to permit high-quality printing
	 */
	public void setPrintHigh(final boolean printHigh) {
		if (printHigh) {
			this.flags |= PRINT_HIGH;
		} else {
			this.flags &= ~PRINT_HIGH;
		}
	}
}
