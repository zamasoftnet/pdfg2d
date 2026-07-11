package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.demo.DemoUtils;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Cross-document font subset reuse: with a shared font source manager,
 * generating the same text twice must produce byte-identical embedded font
 * programs (content-derived subset tags + cached subset generation), while
 * different glyph sets must get different subset tags.
 */
public class FontSubsetCacheTest {

	private File generate(final PDFFontSourceManager fsm, final String name, final String text) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		final var params = PDFParams.createDefault().withFontSourceManager(fsm);
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				final var g2d = new BridgeGraphics2D(gc);
				g2d.setColor(Color.BLACK);
				g2d.setFont(new Font("SubsetCacheTest", Font.PLAIN, 14));
				g2d.drawString(text, 50, 100);
				g2d.dispose();
			}
			pdf.close();
			builder.close();
		}
		return file;
	}

	private static byte[] embeddedFontProgram(final PDDocument doc) throws Exception {
		for (final var page : doc.getPages()) {
			for (final var fontName : page.getResources().getFontNames()) {
				final var font = page.getResources().getFont(fontName);
				final var desc = font.getCOSObject().getCOSArray(COSName.DESCENDANT_FONTS);
				if (desc == null) {
					continue;
				}
				final var descendant = (org.apache.pdfbox.cos.COSDictionary) desc.getObject(0);
				final var fd = descendant.getCOSDictionary(COSName.FONT_DESC);
				final var ff3 = fd.getCOSStream(COSName.FONT_FILE3);
				if (ff3 != null) {
					try (final var in = ff3.createInputStream()) {
						return in.readAllBytes();
					}
				}
			}
		}
		return null;
	}

	private static String baseFontName(final PDDocument doc) throws Exception {
		final var page = doc.getPage(0);
		for (final var fontName : page.getResources().getFontNames()) {
			return page.getResources().getFont(fontName).getName();
		}
		return null;
	}

	@Test
	public void testIdenticalSubsetsAreByteIdenticalAcrossDocuments() throws Exception {
		try (final var fsm = new PDFFontSourceManager()) {
			final var face = new FontFace();
			face.src = new net.zamasoft.zstream.resolver.protocol.file.FileSource(
					DemoUtils.getResourceFile("ipaexm.ttf"));
			face.fontFamily = FontFamilyList.create("SubsetCacheTest");
			fsm.addFontFace(face);

			final var a = generate(fsm, "subset_a.pdf", "同一サブセット ABC");
			final var b = generate(fsm, "subset_b.pdf", "同一サブセット ABC");

			try (final var docA = Loader.loadPDF(a); final var docB = Loader.loadPDF(b)) {
				final var progA = embeddedFontProgram(docA);
				final var progB = embeddedFontProgram(docB);
				assertNotNull(progA, "Embedded font program expected");
				assertArrayEquals(progA, progB,
						"Identical glyph subsets must reuse identical program bytes");
			}
		}
	}

	@Test
	public void testDifferentSubsetsGetDifferentTags() throws Exception {
		try (final var fsm = new PDFFontSourceManager()) {
			final var face = new FontFace();
			face.src = new net.zamasoft.zstream.resolver.protocol.file.FileSource(
					DemoUtils.getResourceFile("ipaexm.ttf"));
			face.fontFamily = FontFamilyList.create("SubsetCacheTest");
			fsm.addFontFace(face);

			final var a = generate(fsm, "subset_c.pdf", "あいうえお");
			final var b = generate(fsm, "subset_d.pdf", "かきくけこ順序違い");

			try (final var docA = Loader.loadPDF(a); final var docB = Loader.loadPDF(b)) {
				assertNotEquals(baseFontName(docA), baseFontName(docB),
						"Different subsets must carry different subset tags");
			}
		}
	}
}
