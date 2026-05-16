package net.zamasoft.pdfg2d.pdf.params;

/**
 * PDF access permissions for encryption revision 2 (RC4 40-bit, PDF 1.1+).
 * <p>
 * Defines the four permission bits available in the original PDF security
 * handler (revision 2): printing, modifying, copying, and adding annotations.
 * Each flag maps to a specific bit position in the 32-bit permission integer
 * written to the encryption dictionary entry {@code P}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class R2Permissions extends Permissions {
	/** Permission bit for printing the document (bit 3, 0-indexed from 1). */
	public static final int PRINT = 1 << 2;

	/** Permission bit for modifying the document contents (bit 4). */
	public static final int MODIFY = 1 << 3;

	/** Permission bit for copying or extracting text and graphics (bit 5). */
	public static final int COPY = 1 << 4;

	/** Permission bit for adding or modifying annotations (bit 6). */
	public static final int ADD = 1 << 5;

	@Override
	public Type getType() {
		return Type.R2;
	}

	@Override
	public int getFlags() {
		return this.flags;
	}

	/**
	 * Returns whether adding or modifying annotations is permitted.
	 *
	 * @return {@code true} if adding annotations is permitted
	 */
	public boolean isAdd() {
		return (this.flags & ADD) != 0;
	}

	/**
	 * Sets whether adding or modifying annotations is permitted.
	 *
	 * @param add {@code true} to permit adding annotations
	 */
	public void setAdd(final boolean add) {
		if (add) {
			this.flags |= ADD;
		} else {
			this.flags &= ~ADD;
		}
	}

	/**
	 * Returns whether copying or extracting text and graphics is permitted.
	 *
	 * @return {@code true} if copying content is permitted
	 */
	public boolean isCopy() {
		return (this.flags & COPY) != 0;
	}

	/**
	 * Sets whether copying or extracting text and graphics is permitted.
	 *
	 * @param copy {@code true} to permit copying content
	 */
	public void setCopy(final boolean copy) {
		if (copy) {
			this.flags |= COPY;
		} else {
			this.flags &= ~COPY;
		}
	}

	/**
	 * Returns whether modifying the document contents is permitted.
	 *
	 * @return {@code true} if modification is permitted
	 */
	public boolean isModify() {
		return (this.flags & MODIFY) != 0;
	}

	/**
	 * Sets whether modifying the document contents is permitted.
	 *
	 * @param modify {@code true} to permit modification
	 */
	public void setModify(final boolean modify) {
		if (modify) {
			this.flags |= MODIFY;
		} else {
			this.flags &= ~MODIFY;
		}
	}

	/**
	 * Returns whether printing the document is permitted.
	 *
	 * @return {@code true} if printing is permitted
	 */
	public boolean isPrint() {
		return (this.flags & PRINT) != 0;
	}

	/**
	 * Sets whether printing the document is permitted.
	 *
	 * @param print {@code true} to permit printing
	 */
	public void setPrint(final boolean print) {
		if (print) {
			this.flags |= PRINT;
		} else {
			this.flags &= ~PRINT;
		}
	}
}
