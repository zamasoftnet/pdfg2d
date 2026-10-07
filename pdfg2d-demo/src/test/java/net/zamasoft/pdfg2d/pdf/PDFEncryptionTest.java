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

    /**
     * V4 RC4 (CFM V2) also writes the key length to the top-level /Length. When only the crypt filter had it,
     * PDFBox read the top-level value and tried a 40-bit key, failing even with an empty password
     * (2026-10-05, I/O property boundary-value tests).
     */
    @Test
    public void testEncryptionV4RC4OpensWithEmptyUserPassword() throws IOException {
        final var file = TestOutputFiles.outputFile(getClass(), "encryption_v4_rc4_test.pdf");
        assertDoesNotThrow(() -> {
            final var encParams = new V4EncryptionParams();
            encParams.setLength(128);
            encParams.setCFM(CFM.V2);
            final var params = PDFParams.createDefault().withVersion(PDFParams.Version.V_1_5).withEncryption(encParams);
            try (final var g2d = new PDFGraphics2D(file, 595, 842, params)) {
                g2d.setPaint(Color.BLACK);
                g2d.drawString("Encryption V4 RC4 Test", 100, 100);
            }
        });
        try (final var doc = Loader.loadPDF(file)) {
            assertTrue(doc.isEncrypted());
            assertTrue(doc.getEncryption().getVersion() == 4, "Encryption V must be 4");
            assertTrue(doc.getEncryption().getLength() == 128, "the key length must be written at the top level");
            final var text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            assertTrue(text.contains("Encryption V4 RC4 Test"), text);
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

    /**
     * The salts are random, so one file says little. The password hash stopped
     * one round early whenever E's last byte hit the boundary, and about 1 file
     * in 15 did not open (2026-10-06).
     */
    @Test
    public void testEncryptionAES256OpensEveryTime() throws IOException {
        final var file = TestOutputFiles.outputFile(getClass(), "encryption_aes256_repeat.pdf");
        for (var i = 0; i < 100; ++i) {
            final var encParams = new net.zamasoft.pdfg2d.pdf.params.V5EncryptionParams();
            encParams.setUserPassword("user");
            encParams.setOwnerPassword("owner");
            final var params = PDFParams.createDefault().withVersion(PDFParams.Version.V_2_0)
                    .withEncryption(encParams);
            try (final var g2d = new PDFGraphics2D(file, 100, 100, params)) {
                g2d.drawString("x", 10, 10);
            }
            try (final var doc = Loader.loadPDF(file, "user")) {
                assertTrue(doc.isEncrypted());
            }
            try (final var doc = Loader.loadPDF(file, "owner")) {
                assertTrue(doc.getCurrentAccessPermission().isOwnerPermission());
            }
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
