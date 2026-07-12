package net.zamasoft.pdfg2d.gc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.font.UnicodeRange;
import net.zamasoft.pdfg2d.gc.font.UnicodeRangeList;
import net.zamasoft.pdfg2d.gc.paint.CMYKColor;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.text.CharacterHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.FilterCharacterHandler;
import net.zamasoft.pdfg2d.gc.text.layout.SimpleLayoutGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.layout.control.LineBreak;
import net.zamasoft.pdfg2d.gc.text.layout.control.Tab;
import net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace;
import net.zamasoft.pdfg2d.util.CharList;
import net.zamasoft.pdfg2d.util.MapIntMap;

class CoreApiTest {
    @Test
    void testFontFamilyAndListNormalizeAndFormatNames() {
        final var serif = FontFamily.create("serif");
        final var customA = FontFamily.create("IPA ExMincho");
        final var customB = FontFamily.create("ipa  exmincho");

        assertTrue(serif.isGenericFamily(), "The serif keyword should resolve to a generic family");
        assertEquals(customA, customB, "Custom family names should be compared after normalization");
        assertEquals(FontFamilyList.SERIF, FontFamilyList.create(null));

        final var list = new FontFamilyList(serif, customA);
        assertEquals(2, list.getLength());
        assertSame(serif, list.get(0));
        assertEquals("serif 'IPA ExMincho'", list.toString());
    }

    @Test
    void testBBoxAndCmykColorExposeStableValueSemantics() {
        final var bbox = new BBox((short) 1, (short) 2, (short) 3, (short) 4);
        final var color = CMYKColor.create(1.2f, -0.5f, 0.25f, 0.5f, CMYKColor.OVERPRINT_STANDARD);

        assertEquals("[llx=1,lly=2,urx=3,ury=4]", bbox.toString());
        assertEquals(1.0f, color.getComponent(CMYKColor.C));
        assertEquals(0.0f, color.getComponent(CMYKColor.M));
        assertEquals(0.25f, color.getComponent(CMYKColor.Y));
        assertEquals(0.5f, color.getComponent(CMYKColor.K));
        assertEquals(0.0f, color.getRed());
        assertEquals(0.5f, color.getGreen());
        assertEquals(0.25f, color.getBlue());
        assertEquals(CMYKColor.OVERPRINT_STANDARD, color.getOverprint());
        assertEquals("-cssj-cmyk(1.0,0.0,0.25,0.5,1)", color.toString());
    }

    @Test
    void testRecorderGcCapturesAndReplaysCommands() {
        final var recorder = new RecorderGC(null);
        try (final var gcState = recorder.begin()) {
	        recorder.setLineWidth(2.5);
	        recorder.setLinePattern(new double[] { 3.0, 4.0 });
	        recorder.setFillPaint(RGBColor.create(1.0f, 0.0f, 0.0f));
	        recorder.transform(AffineTransform.getTranslateInstance(10, 20));
	        recorder.fill(new Rectangle2D.Double(1, 2, 30, 40));
        }

        final var page = recorder.getPage();
        assertEquals(7, page.commands().size());
        assertTrue(page.commands().get(0) instanceof RecorderGC.Begin);
        assertTrue(page.commands().get(5) instanceof RecorderGC.Fill);

        final var replay = new RecorderGC(null);
        page.drawTo(replay);
        final var replayPage = replay.getPage();
        assertEquals(page.commands().size(), replayPage.commands().size());
        assertEquals(1.0, replay.getLineWidth());
        assertArrayEquals(GC.STROKE_SOLID, replay.getLinePattern(),
                "The end command should restore the original graphics state");
    }

    @Test
    void testTextBreakingRulesBundleReturnsDefaultRulesForAnyLanguage() {
        final var jaRules = TextBreakingRulesBundle.getRules("ja");
        final var enRules = TextBreakingRulesBundle.getRules("en");

        assertNotNull(jaRules);
        assertSame(jaRules, enRules, "The current bundle implementation should return the shared default rules");
    }

    @Test
    void testNoOpGcRestoresStateAndCreatesGroupImages() {
        final var gc = new NoOpGC(null);
        gc.setLineWidth(5);
        gc.setLinePattern(new double[] { 1, 2, 3 });
        try (final var gcState = gc.begin()) {
	        gc.setLineWidth(7);
	        gc.resetState();
	        assertEquals(5.0, gc.getLineWidth(), "resetState should restore the state from the stack top");

	        gc.setFillAlpha(0.25f);
        }
        assertEquals(5.0, gc.getLineWidth());
        assertArrayEquals(new double[] { 1, 2, 3 }, gc.getLinePattern());

        final var image = gc.createGroupImage(12, 34).finish();
        assertEquals(12.0, image.getWidth());
        assertEquals(34.0, image.getHeight());
        assertEquals("", image.getAltString());
    }

    @Test
    void testUnicodeRangeAndPolicyValueObjectsBehaveAsExpected() {
        final var wildcard = UnicodeRange.parseRange("U+30??");
        final var explicit = UnicodeRange.parseRange("U+0041-U+005A");
        final var ranges = new UnicodeRangeList(new UnicodeRange[] { wildcard, explicit });

        assertTrue(wildcard.contains(0x3042));
        assertTrue(explicit.contains('A'));
        assertEquals("U+3000-30ff", wildcard.toString());
        assertTrue(ranges.canDisplay(0x3042));
        assertTrue(ranges.canDisplay('Z'));
        assertEquals("U+3000-30ff, U+41-5a", ranges.toString());
        assertTrue(!ranges.isEmpty());

        final var policies = new FontPolicyList(
                new FontPolicyList.FontPolicy[] { FontPolicyList.FontPolicy.CORE, FontPolicyList.FontPolicy.OUTLINES });
        assertEquals(2, policies.getLength());
        assertEquals(FontPolicyList.FontPolicy.OUTLINES, policies.get(1));
        assertEquals("core outlines", policies.toString());
    }

    @Test
    void testFontStyleImplExposesFontStyleInterfaceValues() {
        final var style = new FontStyleImpl(
                FontFamilyList.SANS_SERIF,
                12.5,
                FontStyle.Style.ITALIC,
                FontStyle.Weight.W_700,
                FontStyle.Direction.TB,
                FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);

        assertSame(FontFamilyList.SANS_SERIF, style.getFamily());
        assertEquals(12.5, style.getSize());
        assertEquals(FontStyle.Style.ITALIC, style.getStyle());
        assertEquals(FontStyle.Weight.W_700, style.getWeight());
        assertEquals(FontStyle.Direction.TB, style.getDirection());
        assertSame(FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE, style.getPolicy());
        assertTrue(style.toString().contains("FontStyleImpl"));
    }

    @Test
    void testRgbGrayAndRgbaColorsClampAndExposeComponents() {
        final var gray = GrayColor.create(-0.5f);
        final var rgb = RGBColor.create(1.2f, 0.25f, -0.2f);
        final Color rgba = RGBAColor.create(0.5f, 1.1f, -0.1f, 0.75f);
        final Color opaque = RGBAColor.create(0.25f, 0.5f, 0.75f, 1.0f);

        assertSame(GrayColor.BLACK, gray);
        assertEquals(Paint.Type.COLOR, gray.getPaintType());
        assertEquals(Color.Type.GRAY, gray.getColorType());
        assertEquals(0.0f, gray.getComponent(0));
        assertThrows(IllegalArgumentException.class, () -> gray.getComponent(1));
        assertEquals("-cssj-gray(0.0)", gray.toString());

        assertEquals(Color.Type.RGB, rgb.getColorType());
        assertEquals(1.0f, rgb.getRed());
        assertEquals(0.25f, rgb.getGreen());
        assertEquals(0.0f, rgb.getBlue());
        assertEquals(1.0f, rgb.getAlpha());
        assertThrows(IllegalArgumentException.class, () -> rgb.getComponent(3));
        assertEquals("rgb(1.0,0.25,0.0)", rgb.toString());

        assertTrue(rgba instanceof RGBAColor);
        assertEquals(Color.Type.RGBA, rgba.getColorType());
        assertEquals(0.75f, rgba.getAlpha());
        assertEquals(0.75f, rgba.getComponent(RGBAColor.A));
        assertEquals("rgba(0.5,1.0,0.0,0.75)", rgba.toString());
        assertTrue(opaque instanceof RGBColor);
    }

    @Test
    void testGradientPaintsExposeTypeAndValidateRequiredArguments() {
        final var fractions = new double[] { 0.0, 1.0 };
        final Color[] colors = new Color[] { RGBColor.BLACK, RGBColor.WHITE };
        final var transform = AffineTransform.getScaleInstance(2, 3);

        final var linear = new LinearGradient(1, 2, 3, 4, fractions, colors, transform);
        final var radial = new RadialGradient(10, 20, 30, 11, 21, fractions, colors, transform);

        assertEquals(Paint.Type.LINEAR_GRADIENT, linear.getPaintType());
        assertEquals(Paint.Type.RADIAL_GRADIENT, radial.getPaintType());
        assertTrue(linear.toString().contains("x1=1.0"));
        assertTrue(radial.toString().contains("radius=30.0"));
        assertThrows(NullPointerException.class, () -> new LinearGradient(0, 0, 1, 1, null, colors, transform));
        assertThrows(NullPointerException.class, () -> new LinearGradient(0, 0, 1, 1, fractions, null, transform));
        assertThrows(NullPointerException.class, () -> new LinearGradient(0, 0, 1, 1, fractions, colors, null));
    }

    @Test
    void testMapIntMapIteratesInAscendingKeyOrder() {
        final var map = new MapIntMap();
        map.set(5, 50);
        map.set(1, 10);
        map.set(3, 30);

        assertTrue(map.contains(1));
        assertFalse(map.contains(2));
        assertEquals(30, map.get(3));

        final var iterator = map.getIterator();
        assertTrue(iterator.next());
        assertEquals(1, iterator.key());
        assertEquals(10, iterator.value());
        assertTrue(iterator.next());
        assertEquals(3, iterator.key());
        assertEquals(30, iterator.value());
        assertTrue(iterator.next());
        assertEquals(5, iterator.key());
        assertEquals(50, iterator.value());
        assertFalse(iterator.next());
    }

    @Test
    void testCharListExpandsWithDefaultValuesAndPacksArray() {
        final var chars = new CharList('-');

        assertTrue(chars.isEmpty());
        chars.set(2, 'C');
        chars.set(0, 'A');

        assertEquals(3, chars.size());
        assertFalse(chars.isEmpty());
        assertEquals('A', chars.get(0));
        assertEquals('-', chars.get(1));
        assertEquals('C', chars.get(2));
        assertEquals('-', chars.get(10));

        assertArrayEquals(new char[] { 'A', '-', 'C' }, chars.toArray());
        assertEquals(3, chars.size());
    }

    @Test
    void testTextImplAppendsSplitsAndAccountsForSpacing() {
        final var metrics = new StubFontMetrics();
        final var text = new TextImpl(4, new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL,
                FontStyle.Weight.W_400, FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE),
                metrics);

        assertEquals(4, text.getCharOffset());
        assertSame(metrics, text.getFontMetrics());
        assertEquals(8.0, text.getAscent());
        assertEquals(2.0, text.getDescent());

        assertEquals(11.0, text.appendGlyph("A".toCharArray(), 0, (byte) 1, 11));
        assertEquals(10.0, text.appendGlyph("BC".toCharArray(), 0, (byte) 2, 12));
        text.setLetterSpacing(0.5);
        text.getXAdvances(true)[1] = 1.25;
        text.pack();

        assertEquals(2, text.getGlyphCount());
        assertEquals(3, text.getCharCount());
        assertArrayEquals(new char[] { 'A', 'B', 'C' }, text.getChars());
        assertEquals(23.25, text.getAdvance(), 0.0001);
        assertEquals(13.0, text.glyphAdvance(13), 0.0001);

        final var head = (TextImpl) text.split(1);
        assertEquals(4, head.getCharOffset());
        assertEquals(1, head.getGlyphCount());
        assertEquals(1, head.getCharCount());
        assertArrayEquals(new char[] { 'A' }, head.getChars());
        assertEquals(11.5, head.getAdvance(), 0.0001);

        assertEquals(5, text.getCharOffset());
        assertEquals(1, text.getGlyphCount());
        assertEquals(2, text.getCharCount());
        assertEquals('B', text.getChars()[0]);
        assertEquals('C', text.getChars()[1]);
        assertEquals(13.75, text.getAdvance(), 0.0001);
    }

    @Test
    void testTextImplReverseFlipsGlyphOrderAndKeepsClusters() {
        final var metrics = new StubFontMetrics();
        final var text = new TextImpl(0, new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL,
                FontStyle.Weight.W_400, FontStyle.Direction.RTL, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE),
                metrics);
        // Three glyphs: 'A' (1 char), "BC" ligature (2 chars), 'D' (1 char).
        text.appendGlyph("A".toCharArray(), 0, (byte) 1, 11);
        text.appendGlyph("BC".toCharArray(), 0, (byte) 2, 12);
        text.appendGlyph("D".toCharArray(), 0, (byte) 1, 13);
        text.pack();

        final var reversed = text.reverse();
        // Glyphs reversed: D, BC, A. Clusters (char counts) travel with them.
        assertEquals(3, reversed.getGlyphCount());
        assertArrayEquals(new int[] { 13, 12, 11 }, reversed.getGlyphIds());
        assertArrayEquals(new byte[] { 1, 2, 1 }, reversed.getClusterLengths());
        // Characters follow the reversed glyph order: D, B, C, A.
        assertArrayEquals(new char[] { 'D', 'B', 'C', 'A' }, reversed.getChars());
        assertEquals(4, reversed.getCharCount());
    }

    @Test
    void testFontListMetricsAndTextControlsExposeExpectedMetrics() {
        final var metricsA = new StubFontMetrics(12, 7, 2, 5, 4);
        final var metricsB = new StubFontMetrics(14, 9, 3, 6, 5);
        final var listMetrics = new FontListMetrics(new FontMetrics[] { metricsA, metricsB });
        final var tab = new Tab(listMetrics, 3);
        final var lineBreak = new LineBreak(listMetrics, 4);
        final var whiteSpace = new WhiteSpace(listMetrics, 5);

        assertEquals(2, listMetrics.getLength());
        assertSame(metricsB, listMetrics.getFontMetrics(1));
        assertEquals(9.0, listMetrics.getMaxAscent());
        assertEquals(3.0, listMetrics.getMaxDescent());
        assertEquals(6.0, listMetrics.getMaxXHeight());
        assertTrue(listMetrics.toString().contains("StubFontMetrics"));

        assertEquals('\t', tab.getControlChar());
        assertEquals(3, tab.getCharOffset());
        assertEquals(0.0, tab.getAdvance());
        assertEquals(9.0, tab.getAscent());
        assertEquals(3.0, tab.getDescent());
        assertEquals("\\t", tab.toString());

        assertEquals('\n', lineBreak.getControlChar());
        assertEquals(4, lineBreak.getCharOffset());
        assertEquals(0.0, lineBreak.getAdvance());
        assertEquals("\\n", lineBreak.toString());

        assertEquals(' ', whiteSpace.getControlChar());
        assertEquals(5, whiteSpace.getCharOffset());
        assertEquals(4.0, whiteSpace.getAdvance());
        whiteSpace.setWordSpacing(2.5);
        assertEquals(6.5, whiteSpace.getAdvance());
        whiteSpace.collapse();
        assertEquals(0.0, whiteSpace.getAdvance());
        assertEquals("[SPACE]", whiteSpace.toString());
    }

    @Test
    void testSimpleLayoutGlyphHandlerDrawsTextAndProcessesControls() {
        final var gc = new RecorderGC(null);
        final var handler = new SimpleLayoutGlyphHandler();
        final var metrics = new StubFontMetrics();
        final var style = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
                FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);

        handler.setGC(gc);
        handler.setLetterSpacing(0.5);
        handler.startTextRun(0, style, metrics);
        handler.glyph(0, "A".toCharArray(), 0, (byte) 1, 11);
        handler.endTextRun();

        final var page = gc.getPage();
        assertEquals(1, page.commands().size());
        assertTrue(page.commands().get(0) instanceof RecorderGC.DrawText);
        final var command = (RecorderGC.DrawText) page.commands().get(0);
        assertEquals(0.0, command.x());
        assertEquals(0.0, command.y());
        assertEquals(11.5, handler.getAdvance(), 0.0001);
        assertEquals(0.5, handler.getLetterSpacing());

        final var listMetrics = new FontListMetrics(new FontMetrics[] { metrics });
        final var tab = new Tab(listMetrics, 1);
        handler.control(tab);
        assertEquals(12.5, tab.getAdvance(), 0.0001);
        assertEquals(24.0, handler.getAdvance(), 0.0001);

        handler.control(new LineBreak(listMetrics, 2));
        assertEquals(0.0, handler.getAdvance(), 0.0001);
        handler.flush();
        handler.close();
    }

    @Test
    void testFilterCharacterHandlerForwardsEventsToDelegate() {
        final var delegate = new RecordingCharacterHandler();
        final var filter = new FilterCharacterHandler(delegate);
        final var style = new FontStyleImpl(FontFamilyList.MONOSPACE, 10, FontStyle.Style.OBLIQUE,
                FontStyle.Weight.W_500, FontStyle.Direction.RTL, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
        final var control = new LineBreak(new FontListMetrics(new FontMetrics[] { new StubFontMetrics() }), 9);

        filter.fontStyle(style);
        filter.characters(7, "xyz".toCharArray(), 0, 3);
        filter.control(control);
        filter.flush();
        filter.close();

        assertSame(delegate, filter.getCharacterHandler());
        assertEquals(List.of("font:OBLIQUE", "chars:7:xyz", "control:\\n", "flush", "flush"), delegate.events);
    }

    private static final class StubFontMetrics implements FontMetrics {
        private static final long serialVersionUID = 1L;

        private final double fontSize;
        private final double ascent;
        private final double descent;
        private final double xHeight;
        private final double spaceAdvance;

        private StubFontMetrics() {
            this(12, 8, 2, 6, 4);
        }

        private StubFontMetrics(final double fontSize, final double ascent, final double descent, final double xHeight,
                final double spaceAdvance) {
            this.fontSize = fontSize;
            this.ascent = ascent;
            this.descent = descent;
            this.xHeight = xHeight;
            this.spaceAdvance = spaceAdvance;
        }

        @Override
        public double getFontSize() {
            return this.fontSize;
        }

        @Override
        public double getXHeight() {
            return this.xHeight;
        }

        @Override
        public double getAscent() {
            return this.ascent;
        }

        @Override
        public double getDescent() {
            return this.descent;
        }

        @Override
        public double getAdvance(final int gid) {
            return gid;
        }

        @Override
        public double getWidth(final int gid) {
            return gid;
        }

        @Override
        public double getSpaceAdvance() {
            return this.spaceAdvance;
        }

        @Override
        public double getKerning(final int gid, final int sgid) {
            return gid == 11 && sgid == 12 ? 2 : 0;
        }

        @Override
        public FontSource getFontSource() {
            return null;
        }

        @Override
        public String toString() {
            return "StubFontMetrics[size=" + this.fontSize + "]";
        }
    }

    private static final class RecordingCharacterHandler implements CharacterHandler {
        private final List<String> events = new ArrayList<>();

        @Override
        public void fontStyle(final FontStyle fontStyle) {
            this.events.add("font:" + fontStyle.getStyle());
        }

        @Override
        public void characters(final int charOffset, final char[] ch, final int off, final int len) {
            this.events.add("chars:" + charOffset + ":" + new String(ch, off, len));
        }

        @Override
        public void control(final TextControl control) {
            this.events.add("control:" + control);
        }

        @Override
        public void flush() {
            this.events.add("flush");
        }

        @Override
        public void close() {
            this.events.add("close");
        }
    }
}
