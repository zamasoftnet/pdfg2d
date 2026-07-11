package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import net.zamasoft.pdfg2d.demo.DemoUtils;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.TaggedParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

/**
 * Object stream / cross-reference stream tests. Tagged documents produce
 * many small structure-element dictionaries, which is exactly where packing
 * pays off; correctness of the type-2 entries is verified by PDFBox parsing
 * and by veraPDF (PDF/A-2a validates the structure tree reachable only
 * through the xref stream).
 */
public class ObjectStreamTest {

	@BeforeAll
	public static void initVeraPDF() {
		VeraGreenfieldFoundryProvider.initialise();
	}

	/** Generates a tagged multi-paragraph document. */
	private File generate(final String name, final PDFParams params) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		try (final var fsm = new PDFFontSourceManager()) {
			final var face = new FontFace();
			face.src = new FileSource(DemoUtils.getResourceFile("ipaexm.ttf"));
			face.fontFamily = FontFamilyList.create("ObjStmTest");
			fsm.addFontFace(face);
			final var p = params.withFontSourceManager(fsm);
			try (final var out = new FileOutputStream(file)) {
				final var builder = new StreamFragmentedOutput(out);
				final var pdf = new PDFWriterImpl(builder, p);
				for (var page = 0; page < 3; ++page) {
					try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
						final var g2d = new BridgeGraphics2D(gc);
						g2d.setColor(Color.BLACK);
						g2d.setFont(new Font("ObjStmTest", Font.PLAIN, 12));
						for (var line = 0; line < 30; ++line) {
							g2d.drawString("段落 " + page + "-" + line + " のテキストです。", 50, 60 + line * 24);
						}
						g2d.dispose();
					}
				}
				pdf.close();
				builder.close();
			}
		}
		return file;
	}

	@Test
	public void testObjectStreamsShrinkTaggedDocuments() throws Exception {
		final var plain = generate("objstm_off.pdf",
				PDFParams.createDefault().withTagged(new TaggedParams("ja", false)));
		final var packed = generate("objstm_on.pdf",
				PDFParams.createDefault().withTagged(new TaggedParams("ja", false)).withObjectStreams(true));

		final var raw = new String(Files.readAllBytes(packed.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/ObjStm"), "Structure elements must be packed into object streams");
		assertTrue(raw.contains("/Type /XRef"), "A cross-reference stream must be used");

		try (final var doc = Loader.loadPDF(packed)) {
			assertNotNull(doc.getDocumentCatalog().getStructureTreeRoot(),
					"The structure tree must stay reachable through type-2 entries");
			assertTrue(doc.getNumberOfPages() == 3);
		}

		assertTrue(packed.length() < plain.length(),
				"Packed file must be smaller: " + packed.length() + " vs " + plain.length());
	}

	@Test
	public void testPdfA2aWithObjectStreamsIsCompliant() throws Exception {
		final var file = generate("objstm_pdfa2a.pdf",
				PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFA2A)
						.withTagged(new TaggedParams("ja", false)).withObjectStreams(true));
		try (final var parser = Foundries.defaultInstance().createParser(new FileInputStream(file),
				PDFAFlavour.PDFA_2_A);
				final var validator = Foundries.defaultInstance().createValidator(PDFAFlavour.PDFA_2_A, false)) {
			final var result = validator.validate(parser);
			if (!result.isCompliant()) {
				final var failures = result.getTestAssertions().stream()
						.filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
						.map(a -> a.getRuleId() + " " + a.getMessage())
						.distinct()
						.collect(Collectors.joining("\n"));
				assertTrue(result.isCompliant(), "veraPDF failures with object streams:\n" + failures);
			}
		}
	}

	@Test
	public void testInvalidCombinationsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_1_4).withObjectStreams(true), "requires PDF 1.5+");
		assertThrows(IllegalArgumentException.class, () -> {
			final var enc = new net.zamasoft.pdfg2d.pdf.params.V2EncryptionParams();
			enc.setUserPassword("u");
			enc.setOwnerPassword("o");
			PDFParams.createDefault().withEncryption(enc).withObjectStreams(true);
		}, "no encryption with object streams");
		assertThrows(IllegalArgumentException.class,
				() -> PDFParams.createDefault().withLinearized(true).withObjectStreams(true),
				"no linearization with object streams");
	}
}
