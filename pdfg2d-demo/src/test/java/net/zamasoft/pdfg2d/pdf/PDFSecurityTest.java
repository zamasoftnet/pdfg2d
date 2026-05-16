package net.zamasoft.pdfg2d.pdf;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.V2EncryptionParams; // 128-bit RC4 (V2)
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class PDFSecurityTest {

    @Test
    public void testPermissionsAndEncryptionRC4() throws Exception {
        final var tempFile = TestOutputFiles.outputFile(getClass(), "test-security-rc4.pdf");

        var params = PDFParams.createDefault();

        // Setup RC4 128-bit encryption (V2EncryptionParams indicates V2 algorithm,
        // typically standard 40-128 bit RC4)
        final var encParams = new V2EncryptionParams();
        encParams.setLength(128); // 128-bit
        encParams.setUserPassword("user123");
        encParams.setOwnerPassword("owner123");

        // Permissions
        final var perms = encParams.getPermissions();
        perms.setPrint(true);
        perms.setCopy(false); // Disallow copying
        perms.setModify(false); // Disallow msg

        params = params.withEncryption(encParams);

        try (final var out = new FileOutputStream(tempFile)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            try (final var page = pdf.nextPage(595, 842)) {
                // content
            }
            pdf.close();
            builder.close();
        }

        // Verify with PDFBox
        try (final var doc = Loader.loadPDF(tempFile, "user123")) {
            Assertions.assertTrue(doc.isEncrypted(), "Document should be encrypted");

            final var ap = doc.getCurrentAccessPermission();
            Assertions.assertTrue(ap.canPrint(), "Printing should be allowed");
            Assertions.assertFalse(ap.canExtractContent(), "Content extraction (copy) should NOT be allowed");
            Assertions.assertFalse(ap.canModify(), "Modification should NOT be allowed");
        }
    }
}
