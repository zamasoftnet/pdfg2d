package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.Loader;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.V2EncryptionParams;

import net.zamasoft.pdfg2d.pdf.params.V4EncryptionParams;
import net.zamasoft.pdfg2d.pdf.params.V4EncryptionParams.CFM;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

public class PDFEncryptionTest {

    @Test
    public void testEncryptionRC4() {
        final var file = TestOutputFiles.outputFile(getClass(), "encryption_rc4_test.pdf");
        assertDoesNotThrow(() -> {
            var params = PDFParams.createDefault();

            // V2 Encryption (RC4)
            final var encParams = new V2EncryptionParams();
            encParams.setUserPassword("user");
            encParams.setOwnerPassword("owner");
            encParams.setLength(128); // 128-bit RC4

            // Permissions
            final var perms = encParams.getPermissions();
            perms.setPrint(true);
            perms.setCopy(false);
            perms.setModify(false);

            params = params.withEncryption(encParams);

            try (final var g2d = new PDFGraphics2D(file, 595, 842, params)) {
                g2d.setPaint(Color.BLACK);
                g2d.drawString("Encryption RC4 Test", 100, 100);
            }
        });

        assertTrue(file.exists());

        // Verify with PDFBox
        // Load with user password
        try (final var doc = Loader.loadPDF(file, "user")) {
            assertTrue(doc.isEncrypted());
            final var currentAccess = doc.getCurrentAccessPermission();
            // User should have restricted permissions
            assertTrue(currentAccess.canPrint());
            assertFalse(currentAccess.canExtractContent()); // Copy
            assertFalse(currentAccess.canModify());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Verify owner can open
        try (final var doc = Loader.loadPDF(file, "owner")) {
            assertTrue(doc.isEncrypted());
            // Owner usually has full access
            final var currentAccess = doc.getCurrentAccessPermission();
            assertTrue(currentAccess.isOwnerPermission());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testEncryptionAES() {
        final var file = TestOutputFiles.outputFile(getClass(), "encryption_aes_test.pdf");
        assertDoesNotThrow(() -> {
            var params = PDFParams.createDefault();

            // V4 Encryption (AES)
            final var encParams = new V4EncryptionParams();
            encParams.setUserPassword("user");
            encParams.setOwnerPassword("owner");
            encParams.setLength(128);
            encParams.setCFM(CFM.AESV2); // AES 128

            // Permissions
            final var perms = encParams.getPermissions();
            perms.setPrintHigh(true);
            perms.setCopy(false);
            perms.setModify(false);

            params = params.withVersion(PDFParams.Version.V_1_6)
                    .withEncryption(encParams);

            try (final var g2d = new PDFGraphics2D(file, 595, 842, params)) {
                g2d.setPaint(Color.BLACK);
                g2d.drawString("Encryption AES Test", 100, 100);
            }
        });

        assertTrue(file.exists());

        // Verify with PDFBox
        try (final var doc = Loader.loadPDF(file, "user")) {
            assertTrue(doc.isEncrypted());
            final var currentAccess = doc.getCurrentAccessPermission();

            assertTrue(currentAccess.canPrint());
            assertFalse(currentAccess.canExtractContent());
            assertFalse(currentAccess.canModify());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testEncryptionAES256() {
        final var file = TestOutputFiles.outputFile(getClass(), "encryption_aes256_test.pdf");
        assertDoesNotThrow(() -> {
            var params = PDFParams.createDefault();

            final var encParams = new net.zamasoft.pdfg2d.pdf.params.V5EncryptionParams();
            encParams.setUserPassword("user");
            encParams.setOwnerPassword("owner");

            final var perms = encParams.getPermissions();
            perms.setPrintHigh(true);
            perms.setCopy(false);
            perms.setModify(false);

            // AES-256 (R6) is the standard encryption for PDF 2.0.
            params = params.withVersion(PDFParams.Version.V_2_0)
                    .withEncryption(encParams);

            try (final var g2d = new PDFGraphics2D(file, 595, 842, params)) {
                g2d.setPaint(Color.BLACK);
                g2d.drawString("Encryption AES-256 Test", 100, 100);
            }
        });

        assertTrue(file.exists());

        // Open with the user password.
        try (final var doc = Loader.loadPDF(file, "user")) {
            assertTrue(doc.isEncrypted());
            assertTrue(doc.getEncryption().getVersion() == 5, "Encryption V must be 5");
            final var currentAccess = doc.getCurrentAccessPermission();
            assertTrue(currentAccess.canPrint());
            assertFalse(currentAccess.canExtractContent());
            assertFalse(currentAccess.canModify());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Open with the owner password: full access.
        try (final var doc = Loader.loadPDF(file, "owner")) {
            assertTrue(doc.isEncrypted());
            assertTrue(doc.getCurrentAccessPermission().isOwnerPermission(),
                    "Owner password must grant owner permissions");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testAES256RequiresPdf17OrLater() {
        final var params = PDFParams.createDefault();
        final var encParams = new net.zamasoft.pdfg2d.pdf.params.V5EncryptionParams();
        encParams.setUserPassword("u");
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            final var p = params.withVersion(PDFParams.Version.V_1_6).withEncryption(encParams);
            final var f = TestOutputFiles.outputFile(getClass(), "aes256_reject.pdf");
            try (final var g2d = new PDFGraphics2D(f, 100, 100, p)) {
                g2d.drawString("x", 10, 10);
            }
        }, "AES-256 must require PDF 1.7 or later");
    }
}
