package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.FileOutputStream;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.form.CheckBoxField;
import net.zamasoft.pdfg2d.pdf.form.ChoiceField;
import net.zamasoft.pdfg2d.pdf.form.TextField;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Verifies that HTML-style form controls become interactive PDF AcroForm
 * fields (text, checkbox and choice), readable by a PDF form processor.
 */
public class AcroFormTest {

	@Test
	public void testFormFieldsAreInteractive() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "acroform.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withCompression(PDFParams.Compression.NONE));
			final var page = pdf.nextPage(400, 400);
			// Draw the static appearance, then attach interactive fields over it
			// (fields, like annotations, must be added while the page is open).
			try (final var gc = new PDFGC(page)) {
				gc.setFillPaint(net.zamasoft.pdfg2d.gc.paint.RGBColor.create(0.9f, 0.9f, 0.9f));
				gc.fill(new Rectangle2D.Double(50, 50, 200, 20));
				page.addFormField(new TextField("name", new Rectangle2D.Double(50, 50, 200, 20),
						"山田太郎", "氏名", 12, false, 40, false, true));
				page.addFormField(new CheckBoxField("agree", new Rectangle2D.Double(50, 90, 14, 14),
						"Yes", true, false, "同意する", false, false));
				page.addFormField(new ChoiceField("pref", new Rectangle2D.Double(50, 120, 120, 20),
						List.of("東京", "大阪", "京都"), "大阪", true, "都道府県", 12, false, false));
			}
			pdf.close();
			builder.close();
		}

		final var raw = new String(java.nio.file.Files.readAllBytes(file.toPath()),
				java.nio.charset.StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/AcroForm"), "The catalog must carry an AcroForm dictionary");

		try (final var doc = Loader.loadPDF(file)) {
			final var form = doc.getDocumentCatalog().getAcroForm();
			assertNotNull(form, "PDFBox must read an AcroForm");
			assertEquals(3, form.getFields().size(), "Three fields expected");

			final var name = (PDTextField) form.getField("name");
			assertNotNull(name);
			assertEquals("山田太郎", name.getValue());
			assertTrue(name.isRequired());
			assertEquals("氏名", name.getAlternateFieldName());

			final var agree = (PDCheckBox) form.getField("agree");
			assertTrue(agree.isChecked(), "The checkbox must be initially checked");

			final var pref = (PDComboBox) form.getField("pref");
			assertEquals(List.of("東京", "大阪", "京都"), pref.getOptions());
			assertEquals("大阪", pref.getValue().get(0));
		}
	}

	@Test
	public void testFormsRejectedInPdfX() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "acroform_pdfx.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFX1A));
			final var page = pdf.nextPage(400, 400);
			org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
					() -> page.addFormField(new TextField("x", new Rectangle2D.Double(10, 10, 100, 20),
							null, null, 12, false, 0, false, false)),
					"PDF/X forbids interactive form fields");
		}
	}
}
