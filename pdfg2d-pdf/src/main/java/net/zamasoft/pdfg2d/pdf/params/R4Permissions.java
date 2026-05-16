package net.zamasoft.pdfg2d.pdf.params;

/**
 * PDF access permissions for encryption revision 4 (AES-128, PDF 1.5+).
 * <p>
 * Inherits the full permission bit set from {@link R3Permissions}. Revision 4
 * adds support for the AES cipher via the {@code CF}/{@code StmF}/{@code StrF}
 * crypt filter mechanism but does not introduce new permission bits.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class R4Permissions extends R3Permissions {
	/**
	 * Returns the revision type {@link Permissions.Type#R4}.
	 *
	 * @return {@link Permissions.Type#R4}
	 */
	@Override
	public Type getType() {
		return Type.R4;
	}
}
