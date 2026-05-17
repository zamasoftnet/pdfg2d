package net.zamasoft.pdfg2d.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList.FontPolicy;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Pattern;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.PageLayoutGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.layout.PageLayoutGlyphHandler.Alignment;
import net.zamasoft.pdfg2d.gc.text.layout.SimpleLayoutGlyphHandler;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.util.PDFUtils;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

class DemoFeatureCoverageTest {
    @Test
    void testPagesAppCreatesTwoLabeledPages() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "pages.pdf");

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
            try (final var g2d = new PDFGraphics2D(pdf.nextPage(PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM),
                    PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM)))) {
                g2d.setFont(new Font(Font.SERIF, Font.PLAIN, 38));
                g2d.drawString("Page 1", 10, 100);
            }
            try (final var g2d = new PDFGraphics2D(pdf.nextPage(PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM),
                    PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM)))) {
                g2d.setFont(new Font(Font.SERIF, Font.PLAIN, 38));
                g2d.drawString("Page 2", 10, 100);
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            assertEquals(2, document.getNumberOfPages(), "PagesApp should create a two-page document");
            final var text = extractText(document);
            assertTrue(text.contains("Page 1"), "The first page label should be searchable");
            assertTrue(text.contains("Page 2"), "The second page label should be searchable");
        }
    }

    @Test
    void testGraphics2DBridgeDemoProducesClippedShapesAndSearchableText() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "graphics2d-bridge.pdf");
        final var params = PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            try (final var gc = new PDFGC(pdf.nextPage(400, 400))) {
                Graphics2DBridgeDemo.draw1(new BridgeGraphics2D(gc));
            }
            try (final var gc = new PDFGC(pdf.nextPage(400, 400))) {
                Graphics2DBridgeDemo.draw2(new BridgeGraphics2D(gc));
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            assertEquals(2, document.getNumberOfPages(), "The bridge demo should create two pages");

            final String firstPageStream;
            try (final var contents = document.getPage(0).getContents()) {
                firstPageStream = new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
            assertTrue(firstPageStream.contains(" W") || firstPageStream.contains("W*"),
                    "The first bridge page should contain a clipping operator");
            assertTrue(firstPageStream.contains(" d"),
                    "The first bridge page should contain a dash pattern operator");

            final var text = extractText(document);
            assertTrue(text.contains("盗人"), "The bridge demo should keep attributed Japanese text searchable");
            assertTrue(text.contains("斬りたくもあり"), "The bridge demo should keep the second line searchable");
        }
    }

    @Test
    void testViewerPreferencesDemoWritesCatalogSettings() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "viewer-preferences.pdf");
        final var params = ViewerPreferencesDemo.createDemoParams();

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            for (int i = 0; i < 10; ++i) {
                final var page = pdf.nextPage(300, 300);
                page.setBleedBox(new Rectangle2D.Double(10, 10, 280, 280));
                try (final var gc = new PDFGC(page)) {
                    gc.fill(new Rectangle2D.Double(10, 10, 280, 280));
                }
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            final var info = document.getDocumentInformation();
            assertEquals("タイトル", info.getTitle(), "The viewer preferences demo should also set document metadata");

            final COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
            final COSDictionary preferences = catalog.getCOSDictionary(COSName.getPDFName("ViewerPreferences"));
            assertEquals("UseThumbs",
                    preferences.getNameAsString(COSName.getPDFName("NonFullScreenPageMode")),
                    "The non-full-screen mode should be written");
            assertTrue(preferences.getBoolean(COSName.getPDFName("PickTrayByPDFSize"), false),
                    "The pick-tray preference should be enabled");
            assertEquals("BleedBox", preferences.getNameAsString(COSName.getPDFName("ViewArea")));
            assertEquals("BleedBox", preferences.getNameAsString(COSName.getPDFName("ViewClip")));
            assertEquals("None", preferences.getNameAsString(COSName.getPDFName("PrintScaling")));
            assertEquals(4, preferences.getInt(COSName.getPDFName("NumCopies")));

            final COSArray printRange = preferences.getCOSArray(COSName.getPDFName("PrintPageRange"));
            assertEquals(6, printRange.size(), "The configured print page ranges should be preserved");
            assertEquals(2, printRange.getInt(0));
            assertEquals(9, printRange.getInt(5));
        }
    }

    @Test
    void testRasterImageDemoEmbedsImageXObject() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "image.pdf");

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                gc.drawImage(pdf.addImage(RasterImageDemo.createSampleImage()));
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            assertTrue(hasImageXObject(document.getPage(0).getResources()), "A raster demo should embed an image XObject");
            assertTrue(countColoredPixels(renderPage(document, 0)) > 5_000,
                    "The raster image should produce visible pixels when rendered");
        }
    }

    @Test
    void testRasterImageCanBeLoadedFromSource() throws Exception {
        final var imageFile = TestOutputFiles.outputFile(getClass(), "sample.png");
        ImageIO.write(RasterImageDemo.createSampleImage(), "png", imageFile);

        final var file = TestOutputFiles.outputFile(getClass(), "image-from-source.pdf");
        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                gc.drawImage(pdf.loadImage(new FileSource(imageFile)));
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            assertTrue(hasImageXObject(document.getPage(0).getResources()),
                    "Loading an image from Source should still embed an image XObject");
            assertTrue(countColoredPixels(renderPage(document, 0)) > 5_000,
                    "The Source-backed image should render visible pixels");
        }
    }

    @Test
    void testSvgRenderingDemoProducesVisibleContent() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "svg.pdf");

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                gc.drawImage(SVGRenderingDemo.loadSampleSvgImage());
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            final byte[] contents;
            try (final var stream = document.getPage(0).getContents()) {
                contents = stream.readAllBytes();
            }
            assertTrue(contents.length > 1_000, "The SVG demo should emit a non-trivial page content stream");
        }
    }

    @Test
    void testComplexTextDemoRendersMultilingualText() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "complex-text.pdf");

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                final var graphics = new BridgeGraphics2D(gc);
                Graphics2DBridgeDemo.draw2(graphics);
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            final var text = extractText(document);
            assertTrue(text.contains("盗人"), "The complex text demo should keep Japanese text searchable");
            assertTrue(text.contains("斬りたく"), "The complex text demo should keep repeated vertical text searchable");
        }
    }

    @Test
    void testCore14PolicyUsesType1FontResources() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "core14-fonts.pdf");

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                final var glyphs = new SimpleLayoutGlyphHandler();
                glyphs.setGC(gc);
                try (final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("en"), glyphs)) {
                    tlf.setDirection(Direction.LTR);
                    tlf.setFontFamilies(FontFamilyList.SERIF);
                    tlf.setFontPolicy(new FontPolicyList(new FontPolicy[] { FontPolicy.CORE }));
                    tlf.setFontSize(18);
                    tlf.characters("Core font coverage");
                    tlf.flush();
                }
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            assertTrue(hasFontSubtype(document.getPage(0).getResources(), "Type1"),
                    "Core 14 font policy should emit a Type1 font resource");
        }
    }

    @Test
    void testJapaneseTextUsesCidFontResources() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "cid-fonts.pdf");

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                final var glyphs = new SimpleLayoutGlyphHandler();
                glyphs.setGC(gc);
                try (final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("ja"), glyphs)) {
                    tlf.setDirection(Direction.LTR);
                    tlf.setFontFamilies(FontFamilyList.SERIF);
                    tlf.setFontPolicy(FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
                    tlf.setFontSize(18);
                    tlf.characters("日本語のCIDフォント");
                    tlf.flush();
                }
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            assertTrue(hasFontSubtype(document.getPage(0).getResources(), "Type0"),
                    "Japanese text should emit a CID-backed Type0 font resource");
        }
    }

    @Test
    void testPdfGcGradientPaintCreatesShadingResources() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "gradient-paint.pdf");

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                gc.setFillPaint(new LinearGradient(
                        20, 20, 180, 20,
                        new double[] { 0.0, 1.0 },
                        new net.zamasoft.pdfg2d.gc.paint.Color[] {
                                RGBColor.create(1.0f, 0, 0),
                                RGBColor.create(0, 0, 1.0f) },
                        new AffineTransform()));
                gc.fill(new Rectangle2D.Double(20, 20, 160, 80));

                gc.setFillPaint(new RadialGradient(
                        120, 180, 50, 120, 180,
                        new double[] { 0.0, 1.0 },
                        new net.zamasoft.pdfg2d.gc.paint.Color[] { RGBColor.WHITE, RGBColor.BLACK },
                        new AffineTransform()));
                gc.fill(new Rectangle2D.Double(70, 130, 100, 100));
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            final COSDictionary resources = document.getPage(0).getResources().getCOSObject();
            final COSDictionary patterns = resources.getCOSDictionary(COSName.PATTERN);
            assertTrue(patterns != null && patterns.size() >= 2,
                    "Gradient paints should create pattern resources");
            assertTrue(countPatternEntriesWithShading(patterns) >= 2,
                    "Gradient paints should create shading-backed pattern entries");
        }
    }

    @Test
    void testPdfGcPatternPaintCreatesPatternResource() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "pattern-paint.pdf");

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                final var image = pdf.addImage(RasterImageDemo.createSampleImage());
                gc.setFillPaint(new Pattern(image, AffineTransform.getScaleInstance(0.25, 0.25)));
                gc.fill(new Rectangle2D.Double(20, 20, 180, 180));
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            final COSDictionary resources = document.getPage(0).getResources().getCOSObject();
            final COSDictionary patterns = resources.getCOSDictionary(COSName.PATTERN);
            assertTrue(patterns != null && patterns.size() >= 1,
                    "Pattern paint should create a pattern resource");
        }
    }

    @Test
    void testStyledTextAppRendersJapaneseAndEnglishParagraphs() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "styled-text.pdf");

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
            try (final var gc = new PDFGC(pdf.nextPage(PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM),
                    PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM)))) {
                gc.transform(AffineTransform.getTranslateInstance(PDFUtils.mmToPt(10), PDFUtils.mmToPt(10)));
                try (final var lgh = new PageLayoutGlyphHandler(gc)) {
                    lgh.setLineAdvance(PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM - 20));
                    lgh.setAlign(Alignment.JUSTIFY);
                    lgh.setLineHeight(1.616);

                    try (final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("ja"), lgh)) {
                        tlf.setDirection(Direction.LTR);
                        tlf.setFontFamilies(FontFamilyList.SERIF);
                        tlf.setFontSize(24);
                        tlf.characters("兵は詭道なり。");
                        tlf.setFontSize(16);
                        tlf.characters(
                                """
                                        故に能なるも之に不能を示し、用なるも之に不用を示し、近くとも之に遠きを示し、遠くとも之に近きを示し、利にして之を誘い、乱にして之を取り、実にして之に備え、強にして之を避け、怒にして之を撓し、卑にして之を驕らせ、佚にして之を労し、親にして之を離す。
                                        """);
                    }

                    try (final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("en"), lgh)) {
                        tlf.setFontFamilies(FontFamilyList.SANS_SERIF);
                        tlf.setFontStyle(Style.ITALIC);
                        tlf.setFontSize(12);
                        tlf.characters(
                                """
                                        All warfare is based on deception. Hence, when able to attack, we must seem unable; when using our forces, we must seem inactive; when we are near, we must make the enemy believe we are far away; when far away, we must make him believe we are near.
                                        """);
                    }
                }
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            final var text = extractText(document);
            assertTrue(text.contains("兵は詭道なり"), "The styled text demo should preserve Japanese text");
            assertTrue(text.contains("All warfare is based on deception"),
                    "The styled text demo should preserve English text");
        }
    }

    @Test
    void testTextOutlineDemoWritesFillStrokeTextMode() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "text-outline.pdf");
        final var params = PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                gc.setStrokePaint(RGBColor.BLACK);
                gc.setFillPaint(RGBColor.WHITE);
                gc.setTextMode(GC.TextMode.FILL_STROKE);

                final var outlinedGlyphs = new SimpleLayoutGlyphHandler();
                outlinedGlyphs.setGC(gc);
                try (final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("en"), outlinedGlyphs)) {
                    tlf.setDirection(Direction.LTR);
                    tlf.setFontFamilies(FontFamilyList.SERIF);
                    tlf.setFontSize(32);
                    tlf.characters("Outline");
                    tlf.flush();
                }

                gc.setTextMode(GC.TextMode.FILL);
                final var filledGlyphs = new SimpleLayoutGlyphHandler();
                filledGlyphs.setGC(gc);
                try (final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("en"), filledGlyphs)) {
                    tlf.setDirection(Direction.LTR);
                    tlf.setFontFamilies(FontFamilyList.SERIF);
                    tlf.setFontSize(24);
                    tlf.characters("Fill");
                    tlf.flush();
                }
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            final String stream;
            try (final var contents = document.getPage(0).getContents()) {
                stream = new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
            assertTrue(stream.contains("2 Tr"), "Outlined text should switch to fill-stroke mode");
            assertTrue(stream.contains("0 Tr"), "The demo should switch back to fill mode for the second block");
        }
    }

    @Test
    void testTransparencyGroupDemoCreatesTransparencyGroupXObjects() throws Exception {
        final var file = TestOutputFiles.outputFile(getClass(), "group-image.pdf");
        final var params = PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);

        try (final var out = new FileOutputStream(file)) {
            final var builder = new StreamFragmentedOutput(out);
            final var pdf = new PDFWriterImpl(builder, params);
            try (final var gc = new PDFGC(pdf.nextPage(300, 300))) {
                TransparencyGroupDemo.draw(gc);
            }
            pdf.close();
            builder.close();
        }

        try (final var document = Loader.loadPDF(file)) {
            assertTrue(hasTransparencyGroup(document.getPage(0).getResources()),
                    "Transparency groups should be emitted as form XObjects with a group dictionary");
        }
    }

    private static String extractText(final PDDocument document) throws Exception {
        final var stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }

    private static BufferedImage renderPage(final PDDocument document, final int pageIndex) throws Exception {
        return new PDFRenderer(document).renderImageWithDPI(pageIndex, 96);
    }

    private static int countColoredPixels(final BufferedImage image) {
        var coloredPixels = 0;
        for (var y = 0; y < image.getHeight(); ++y) {
            for (var x = 0; x < image.getWidth(); ++x) {
                final int rgb = image.getRGB(x, y) & 0x00FFFFFF;
                if (rgb != 0x00FFFFFF) {
                    ++coloredPixels;
                }
            }
        }
        return coloredPixels;
    }

    private static boolean hasImageXObject(final PDResources resources) throws Exception {
        for (final COSName name : resources.getXObjectNames()) {
            final PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject) {
                return true;
            }
            if (xObject instanceof final PDFormXObject form && form.getResources() != null
                    && hasImageXObject(form.getResources())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTransparencyGroup(final PDResources resources) throws Exception {
        for (final COSName name : resources.getXObjectNames()) {
            final PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof final PDFormXObject form) {
                if (form.getGroup() != null) {
                    return true;
                }
                if (form.getResources() != null && hasTransparencyGroup(form.getResources())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasFontSubtype(final PDResources resources, final String subtype) throws Exception {
        for (final COSName name : resources.getFontNames()) {
            final PDFont font = resources.getFont(name);
            if (subtype.equals(font.getCOSObject().getNameAsString(COSName.SUBTYPE))) {
                return true;
            }
        }
        return false;
    }

    private static int countPatternEntriesWithShading(final COSDictionary patterns) {
        var count = 0;
        for (final COSName name : patterns.keySet()) {
            final COSDictionary pattern = (COSDictionary) patterns.getDictionaryObject(name);
            if (pattern != null && pattern.containsKey(COSName.SHADING)) {
                ++count;
            }
        }
        return count;
    }
}
