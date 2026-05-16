package net.zamasoft.pdfg2d.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.awt.GraphicsDevice;
import java.awt.Color;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.gvt.FillShapePainter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.PatternPaint;
import org.apache.batik.gvt.ShapeNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.pdfg2d.gc.NoOpGC;
import net.zamasoft.pdfg2d.gc.RecorderGC;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.Pattern;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;

class SVGApiTest {
    @Test
    void testSvgDimensionIsMutableAndFormatsValues() {
        final var dimension = new SVGDimension(10, 20);
        dimension.setSize(15, 25);

        assertEquals(15.0, dimension.getWidth());
        assertEquals(25.0, dimension.getHeight());
        assertTrue(dimension.toString().contains("[15.0,25.0]"));
    }

    @Test
    void testSvgImageExposesDimensionsAndDrawsThroughBridgeGraphics2d() {
        final var shape = new Rectangle2D.Double(0, 0, 40, 20);
        final var node = new ShapeNode();
        node.setShape(shape);
        final var painter = new FillShapePainter(shape);
        painter.setPaint(Color.BLUE);
        node.setShapePainter(painter);

        final var image = new SVGImage(node, 40, 20);
        final var recorder = new RecorderGC(null);
        image.drawTo(recorder);

        final var page = recorder.getPage();
        assertEquals(node, image.getNode());
        assertEquals(40.0, image.getWidth());
        assertEquals(20.0, image.getHeight());
        assertNull(image.getAltString());
        assertTrue(page.commands().size() >= 3, "Drawing an SVG image should record graphics commands");
    }

    @Test
    void testSvgUserAgentAndGraphicsConfigurationExposeExpectedDefaults() {
        final var viewport = new SVGDimension(120, 80);
        final var userAgent = new SVGUserAgent(viewport);
        final var configuration = SVGGraphicsConfiguration.SHARED_INSTANCE;

        assertEquals(viewport, userAgent.getViewportSize());
        assertEquals(120.0, userAgent.getViewportSize().getWidth());
        assertEquals(80.0, userAgent.getViewportSize().getHeight());

        assertEquals(Transparency.TRANSLUCENT, configuration.createCompatibleImage(10, 10).getTransparency());
        assertEquals(Transparency.OPAQUE,
                configuration.createCompatibleImage(10, 10, Transparency.OPAQUE).getTransparency());
        assertEquals(2.0, configuration.getNormalizingTransform().getScaleX());
        assertTrue(configuration.getDevice() instanceof SVGGraphicsDevice);
        assertThrows(UnsupportedOperationException.class, () -> configuration.createCompatibleVolatileImage(1, 1));
    }

    @Test
    void testSvgGraphicsDeviceAndConfigurationExposePrinterLikeCharacteristics() {
        final var configuration = SVGGraphicsConfiguration.SHARED_INSTANCE;
        final var device = (SVGGraphicsDevice) configuration.getDevice();

        assertEquals(GraphicsDevice.TYPE_PRINTER, device.getType());
        assertEquals(configuration, device.getBestConfiguration(null));
        assertEquals(configuration, device.getDefaultConfiguration());
        assertEquals(1, device.getConfigurations().length);
        assertTrue(configuration.getDevice() instanceof SVGGraphicsDevice);
        assertTrue(device.getIDstring().contains("SVGGraphicsDevice"));
        assertNull(configuration.getBounds());
        assertEquals(Transparency.TRANSLUCENT, configuration.getColorModel().getTransparency());
        assertEquals(Transparency.OPAQUE, configuration.getColorModel(Transparency.OPAQUE).getTransparency());
        assertEquals(1.0, configuration.getDefaultTransform().getScaleX());
        assertThrows(UnsupportedOperationException.class,
                () -> configuration.createCompatibleVolatileImage(1, 1, Transparency.TRANSLUCENT));
    }

    @Test
    void testSvgBridgeGraphics2dConvertsColorAndPatternPaints() throws Exception {
        final var gc = new NoOpGC(null);
        final var bridge = new SVGBridgeGraphics2D(gc);

        bridge.setPaint(Color.RED);
        assertTrue(gc.getStrokePaint() instanceof RGBColor);
        assertEquals(Paint.Type.COLOR, gc.getFillPaint().getPaintType());

        final var tileShape = new Rectangle2D.Double(0, 0, 20, 10);
        final var node = new ShapeNode();
        node.setShape(tileShape);
        node.setTransform(new AffineTransform());
        final var painter = new FillShapePainter(tileShape);
        painter.setPaint(Color.GREEN);
        node.setShapePainter(painter);

        final var rect = new Rectangle2D.Double(5, 7, 20, 10);
        final var patternTransform = AffineTransform.getScaleInstance(2, 3);
        final var patternPaint = new PatternPaint(node, rect, false, patternTransform);
        bridge.setPaint(patternPaint);

        assertTrue(gc.getStrokePaint() instanceof Pattern);
        final var pattern = (Pattern) gc.getStrokePaint();
        assertEquals(Paint.Type.PATTERN, pattern.getPaintType());
        assertEquals(20.0, pattern.getImage().getWidth());
        assertEquals(10.0, pattern.getImage().getHeight());
        assertEquals(2.0, pattern.getTransform().getScaleX());
        assertEquals(21.0, pattern.getTransform().getTranslateY());
        assertEquals(-5.0, node.getTransform().getTranslateX());
        assertEquals(-7.0, node.getTransform().getTranslateY());

        bridge.setPaint(null);
        assertTrue(gc.getFillPaint() instanceof Pattern);
    }

    @Test
    void testBatikPatternSvgDrawsAsInternalPatternPaint() throws Exception {
        final var built = buildSvg("""
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20">
                  <defs>
                    <pattern id="tile" x="0" y="0" width="10" height="10" patternUnits="userSpaceOnUse">
                      <rect x="0" y="0" width="10" height="10" fill="#00ff00"/>
                    </pattern>
                  </defs>
                  <rect x="0" y="0" width="40" height="20" fill="url(#tile)"/>
                </svg>
                """, new PDFGVTBuilder());

        final var recorder = new RecorderGC(null);
        built.image().drawTo(recorder);
        final var page = recorder.getPage();

        assertEquals(40.0, built.image().getWidth());
        assertEquals(20.0, built.image().getHeight());
        assertTrue(page.commands().stream()
                .filter(RecorderGC.SetFillPaint.class::isInstance)
                .map(RecorderGC.SetFillPaint.class::cast)
                .anyMatch(cmd -> cmd.paint() instanceof Pattern),
                "Batik pattern fills should be converted to internal Pattern paint");
        assertTrue(page.commands().stream().anyMatch(RecorderGC.Fill.class::isInstance),
                "Rendering the SVG should emit a fill command");
    }

    @Test
    void testBatikPatternTransformIsReflectedInInternalPatternPaint() throws Exception {
        final var built = buildSvg("""
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20">
                  <defs>
                    <pattern id="tile" x="0" y="0" width="10" height="10"
                             patternUnits="userSpaceOnUse"
                             patternTransform="translate(3 4) scale(2 3)">
                      <rect x="0" y="0" width="10" height="10" fill="#00ff00"/>
                    </pattern>
                  </defs>
                  <rect x="0" y="0" width="40" height="20" fill="url(#tile)"/>
                </svg>
                """, new PDFGVTBuilder());

        final var recorder = new RecorderGC(null);
        built.image().drawTo(recorder);

        final var pattern = recorder.getPage().commands().stream()
                .filter(RecorderGC.SetFillPaint.class::isInstance)
                .map(RecorderGC.SetFillPaint.class::cast)
                .map(RecorderGC.SetFillPaint::paint)
                .filter(Pattern.class::isInstance)
                .map(Pattern.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals(2.0, pattern.getTransform().getScaleX(), 0.001);
        assertEquals(3.0, pattern.getTransform().getScaleY(), 0.001);
        assertEquals(3.0, pattern.getTransform().getTranslateX(), 0.001);
        assertEquals(4.0, pattern.getTransform().getTranslateY(), 0.001);
    }

    @Test
    void testPdfGvtBuilderWrapsOnlyTransparentNodes() throws Exception {
        final var built = buildSvg("""
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20">
                  <g>
                    <rect x="0" y="0" width="10" height="10" fill="#ff0000" opacity="0.5"/>
                    <rect x="15" y="0" width="10" height="10" fill="#0000ff"/>
                  </g>
                </svg>
                """, new PDFGVTBuilder());

        assertEquals(1, countNodesWithFilterType(built.root(), PDFTransparencyRable.class),
                "Only partially transparent SVG nodes should be wrapped with PDFTransparencyRable");
    }

    @Test
    void testBatikLinearGradientSvgDrawsAsInternalLinearGradientPaint() throws Exception {
        final var built = buildSvg("""
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20">
                  <defs>
                    <linearGradient id="grad" x1="0" y1="0" x2="40" y2="0">
                      <stop offset="0%" stop-color="#ff0000"/>
                      <stop offset="100%" stop-color="#0000ff"/>
                    </linearGradient>
                  </defs>
                  <rect x="0" y="0" width="40" height="20" fill="url(#grad)"/>
                </svg>
                """, new PDFGVTBuilder());

        final var recorder = new RecorderGC(null);
        built.image().drawTo(recorder);

        assertTrue(recorder.getPage().commands().stream()
                .filter(RecorderGC.SetFillPaint.class::isInstance)
                .map(RecorderGC.SetFillPaint.class::cast)
                .anyMatch(cmd -> cmd.paint() != null && cmd.paint().getPaintType() == Paint.Type.LINEAR_GRADIENT),
                "Batik linear gradients should be converted to internal LinearGradient paint");
    }

    @Test
    void testBatikRadialGradientSvgDrawsAsInternalRadialGradientPaint() throws Exception {
        final var built = buildSvg("""
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40">
                  <defs>
                    <radialGradient id="grad" cx="20" cy="20" r="16" fx="16" fy="16">
                      <stop offset="0%" stop-color="#ffffff"/>
                      <stop offset="100%" stop-color="#000000"/>
                    </radialGradient>
                  </defs>
                  <circle cx="20" cy="20" r="16" fill="url(#grad)"/>
                </svg>
                """, new PDFGVTBuilder());

        final var recorder = new RecorderGC(null);
        built.image().drawTo(recorder);

        assertTrue(recorder.getPage().commands().stream()
                .filter(RecorderGC.SetFillPaint.class::isInstance)
                .map(RecorderGC.SetFillPaint.class::cast)
                .anyMatch(cmd -> cmd.paint() != null && cmd.paint().getPaintType() == Paint.Type.RADIAL_GRADIENT),
                "Batik radial gradients should be converted to internal RadialGradient paint");
    }

    @Test
    void testBatikClipPathProducesClipCommand() throws Exception {
        final var built = buildSvg("""
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20">
                  <defs>
                    <clipPath id="clip">
                      <circle cx="10" cy="10" r="8"/>
                    </clipPath>
                  </defs>
                  <rect x="0" y="0" width="40" height="20" fill="#00ff00" clip-path="url(#clip)"/>
                </svg>
                """, new PDFGVTBuilder());

        final var recorder = new RecorderGC(null);
        built.image().drawTo(recorder);

        assertTrue(recorder.getPage().commands().stream().anyMatch(RecorderGC.Clip.class::isInstance),
                "Batik clipPath should be emitted as a clip command");
    }

    @Test
    void testTransparentSvgPaintsThroughRecordedGroupImage() throws Exception {
        final var built = buildSvg("""
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20">
                  <rect x="0" y="0" width="20" height="20" fill="#ff0000" opacity="0.5"/>
                </svg>
                """, new PDFGVTBuilder(true));

        final var recorder = new RecorderGC(null);
        built.image().drawTo(recorder);

        assertTrue(recorder.getPage().commands().stream().anyMatch(RecorderGC.DrawImage.class::isInstance),
                "Transparent SVG content should render through a recorded group image when vector mode is forced");
    }

    @Test
    void testTransparentSvgUsesPdfTransparencyGroupOnlyWhenSupported() throws Exception {
        final var transparentSvg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20">
                  <rect x="0" y="0" width="20" height="20" fill="#ff0000" opacity="0.5"/>
                </svg>
                """;

        final var pdf14 = renderSvgToPdfBytes(transparentSvg, new PDFGVTBuilder(true),
                PDFParams.createDefault()
                        .withVersion(PDFParams.Version.V_1_4)
                        .withCompression(PDFParams.Compression.NONE));
        final var pdf13 = renderSvgToPdfBytes(transparentSvg, new PDFGVTBuilder(false),
                PDFParams.createDefault()
                        .withVersion(PDFParams.Version.V_1_3)
                        .withCompression(PDFParams.Compression.NONE));

        assertTrue(pdf14.contains("/Group"),
                "Transparent SVG should emit a transparency group when PDF transparency is supported");
        assertTrue(pdf14.contains("/Subtype /Form"),
                "Transparency-group output should use a Form XObject");
        assertTrue(!pdf13.contains("/Group"),
                "Transparent SVG should avoid transparency groups for PDF 1.3 fallback output");
    }

    @Test
    void testSvgFillAndStrokeOpacityAreWrittenToSeparateExtGStateEntries() throws Exception {
        final var svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20">
                  <rect x="0" y="0" width="12" height="12" fill="#ff0000" fill-opacity="0.4"/>
                  <line x1="20" y1="2" x2="38" y2="18" stroke="#0000ff" stroke-width="3" stroke-opacity="0.25"/>
                </svg>
                """;

        final var pdf = renderSvgToPdfBytes(svg, new PDFGVTBuilder(),
                PDFParams.createDefault()
                        .withVersion(PDFParams.Version.V_1_4)
                        .withCompression(PDFParams.Compression.NONE));

        assertTrue(pdf.contains("/ca 0.4") || pdf.contains("/ca 0.40"),
                "fill-opacity should be written as non-stroking alpha in ExtGState");
        assertTrue(pdf.contains("/CA 0.25"),
                "stroke-opacity should be written as stroking alpha in ExtGState");
    }

    private static BuiltSvg buildSvg(final String svg, final org.apache.batik.bridge.GVTBuilder builder) throws Exception {
        final var parser = XMLResourceDescriptor.getXMLParserClassName();
        final var factory = new SAXSVGDocumentFactory(parser);
        final Document document;
        try (final var in = new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))) {
            document = factory.createDocument("memory:test.svg", in);
        }

        final var userAgent = new SVGUserAgent(new SVGDimension(1, 1));
        final var context = new BridgeContext(userAgent);
        context.setDynamicState(BridgeContext.STATIC);
        final var root = builder.build(context, document);
        final var size = context.getDocumentSize();
        return new BuiltSvg(new SVGImage(root, size.getWidth(), size.getHeight()), root);
    }

    private static int countNodesWithFilterType(final GraphicsNode node, final Class<?> filterType) {
        var count = 0;
        if (node.getFilter() != null && filterType.isInstance(node.getFilter())) {
            ++count;
        }
        if (node instanceof final Iterable<?> children) {
            for (final var child : children) {
                if (child instanceof final GraphicsNode graphicsNode) {
                    count += countNodesWithFilterType(graphicsNode, filterType);
                }
            }
        }
        return count;
    }

    private String renderSvgToPdfBytes(final String svg, final org.apache.batik.bridge.GVTBuilder builder,
            final PDFParams params) throws Exception {
        final var built = buildSvg(svg, builder);
        final var file = TestOutputFiles.outputFile(getClass(), "svg-" + System.nanoTime() + ".pdf");
        try (final var g2d = new PDFGraphics2D(file, built.image().getWidth(), built.image().getHeight(), params)) {
            built.root().paint(g2d);
        }
        return java.nio.file.Files.readString(file.toPath(), Charset.forName("ISO-8859-1"));
    }

    private record BuiltSvg(SVGImage image, GraphicsNode root) {
    }
}
