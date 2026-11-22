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
	public void testRadioGroupIsSingleField() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "acroform_radio.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withCompression(PDFParams.Compression.NONE));
			final var page = pdf.nextPage(400, 400);
			try (final var gc = new PDFGC(page)) {
				page.addRadioGroup(new net.zamasoft.pdfg2d.pdf.form.RadioGroup("gender", "性別", "m", false, false,
						List.of(new net.zamasoft.pdfg2d.pdf.form.RadioGroup.Button(
								new Rectangle2D.Double(50, 50, 14, 14), "m"),
								new net.zamasoft.pdfg2d.pdf.form.RadioGroup.Button(
										new Rectangle2D.Double(50, 70, 14, 14), "f"))));
			}
			pdf.close();
			builder.close();
		}

		try (final var doc = Loader.loadPDF(file)) {
			final var form = doc.getDocumentCatalog().getAcroForm();
			assertNotNull(form);
			assertEquals(1, form.getFields().size(), "A radio group is one field, not two");
			final var radio = (org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton) form.getField("gender");
			assertNotNull(radio);
			assertEquals(2, radio.getWidgets().size(), "The group must have two button widgets");
			assertEquals("m", radio.getValue(), "The initially selected button is 'm'");
			assertTrue(radio.getOnValues().containsAll(List.of("m", "f")), "Both on-states must be present");
		}
	}

	/**
	 * Radio groups of the same name on different pages are one field with the
	 * widgets of both pages: each page used to get its own field of that name,
	 * with its own selection.
	 */
	@Test
	public void testRadioGroupAcrossPagesIsSingleField() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "acroform_radio_pages.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withCompression(PDFParams.Compression.NONE));
			final var first = pdf.nextPage(400, 400);
			try (final var gc = new PDFGC(first)) {
				first.addRadioGroup(new net.zamasoft.pdfg2d.pdf.form.RadioGroup("answer", null, "yes", false, false,
						List.of(new net.zamasoft.pdfg2d.pdf.form.RadioGroup.Button(
								new Rectangle2D.Double(50, 50, 14, 14), "yes"))));
			}
			final var second = pdf.nextPage(400, 400);
			try (final var gc = new PDFGC(second)) {
				second.addRadioGroup(new net.zamasoft.pdfg2d.pdf.form.RadioGroup("answer", null, null, false, false,
						List.of(new net.zamasoft.pdfg2d.pdf.form.RadioGroup.Button(
								new Rectangle2D.Double(50, 50, 14, 14), "no"))));
			}
			pdf.close();
			builder.close();
		}

		try (final var doc = Loader.loadPDF(file)) {
			final var form = doc.getDocumentCatalog().getAcroForm();
			assertNotNull(form);
			assertEquals(1, form.getFields().size(), "The groups of one name are one field");
			final var radio = (org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton) form.getField("answer");
			assertNotNull(radio);
			assertEquals(2, radio.getWidgets().size(), "The field must have the widgets of both pages");
			assertEquals("yes", radio.getValue(), "The button selected on the first page stays selected");
			assertEquals(java.util.Set.of("yes", "no"), radio.getOnValues());
			assertEquals(1, doc.getPage(0).getAnnotations().size(), "One widget on the first page");
			assertEquals(1, doc.getPage(1).getAnnotations().size(), "One widget on the second page");
		}
	}

	/**
	 * An option with an export value is written as the pair of the export
	 * value and the display string, and the field's value is the export value.
	 */
	@Test
	public void testChoiceExportValues() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "acroform_choice_export.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withCompression(PDFParams.Compression.NONE));
			final var page = pdf.nextPage(400, 400);
			try (final var gc = new PDFGC(page)) {
				page.addFormField(new ChoiceField("country", new Rectangle2D.Double(50, 50, 120, 20),
						List.of("United Kingdom", "Japan"), "gb", true, null, 12, false, false,
						List.of("gb", "jp")));
			}
			pdf.close();
			builder.close();
		}

		try (final var doc = Loader.loadPDF(file)) {
			final var country = (PDComboBox) doc.getDocumentCatalog().getAcroForm().getField("country");
			assertNotNull(country);
			assertEquals(List.of("United Kingdom", "Japan"), country.getOptionsDisplayValues());
			assertEquals(List.of("gb", "jp"), country.getOptionsExportValues());
			assertEquals(List.of("gb"), country.getValue());
		}

		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> new ChoiceField("x", new Rectangle2D.Double(10, 10, 100, 20), List.of("a", "b"), null, true,
						null, 12, false, false, List.of("a")),
				"Each option needs its export value");
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
