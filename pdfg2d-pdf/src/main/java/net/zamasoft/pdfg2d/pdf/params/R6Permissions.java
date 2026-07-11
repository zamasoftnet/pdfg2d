package net.zamasoft.pdfg2d.pdf.params;

/**
 * Permissions for the revision 6 security handler (AES-256, PDF 2.0).
 * The permission flags are identical to revision 3/4; revision 6 adds the
 * encrypted {@code Perms} verification entry, handled by the writer.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public final class R6Permissions extends R3Permissions {
	@Override
	public Type getType() {
		return Type.R6;
	}
}
