package net.zamasoft.pdfg2d.pdf.gc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.RecorderGC;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList.FontPolicy;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.text.TextClip;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.pdf.PDFMetaInfo;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.TaggedParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** Text as a clip (CSS background-clip: text) and invisible text, in PDF (2026-10-09). */
public class TextClipTest {
	private static final File FONT = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");
	private static final FontPolicyList EMBEDDED = new FontPolicyList(new FontPolicy[] { FontPolicy.EMBEDDED });

	@FunctionalInterface
	private interface Drawing {
		void draw(PDFGC gc) throws Exception;
	}

	private static String content(final PDFParams params, final Drawing drawing) throws Exception {
		final var metaInfo = new PDFMetaInfo();
		metaInfo.setCreationDate(0);
		metaInfo.setModDate(0);
		final var bytes = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(bytes);
		final var pdf = new PDFWriterImpl(builder,
				params.withCompression(PDFParams.Compression.NONE).withFileId(new byte[16]).withMetaInfo(metaInfo));
		final var page = pdf.nextPage(200, 200);
		try (final var gc = new PDFGC(page)) {
			drawing.draw(gc);
		}
		pdf.close();
		builder.close();
		final String raw = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
		final StringBuilder streams = new StringBuilder();
		for (int at = 0, begin; (begin = raw.indexOf("stream", at)) >= 0;) {
			final int end = raw.indexOf("endstream", begin);
			streams.append(raw, begin + "stream".length(), end).append('\n');
			at = end + "endstream".length();
		}
		return streams.toString();
	}

	private static TextImpl text(final PDFGC gc, final String s) throws Exception {
		final var source = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.LTR);
		final var style = new FontStyleImpl(FontFamilyList.create(source.getFontName()), 12,
				FontStyle.Style.NORMAL, FontStyle.Weight.W_400, FontStyle.Direction.LTR, EMBEDDED);
		final var metrics = new FontMetricsImpl((PDFWriterImpl) gc.getPdfWriter(), source, style);
		final var font = metrics.getFont();
		final var text = new TextImpl(0, style, metrics);
		for (final char c : s.toCharArray()) {
			text.appendGlyph(new char[] { c }, 0, (byte) 1, font.toGID(c, FontFeatureSet.EMPTY));
		}
		text.pack();
		return text;
	}

	private static int count(final String text, final String regex) {
		final Matcher m = Pattern.compile(regex).matcher(text);
		int count = 0;
		while (m.find()) {
			++count;
		}
		return count;
	}

	private static final GC.Painter FILL = g -> {
		g.setFillPaint(RGBColor.create(1, 0, 0));
		g.fill(new Rectangle2D.Double(0, 0, 100, 50));
	};

	/** All runs are shown in one text object in mode 7, placed by text matrices, and the paint follows inside. */
	@Test
	public void runsClipInOneTextObject() throws Exception {
		final String content = content(PDFParams.createDefault(), gc -> {
			final TextClip clip = new TextClip();
			clip.add(text(gc, "AB"), AffineTransform.getTranslateInstance(10, 20));
			final AffineTransform rotated = AffineTransform.getTranslateInstance(10, 40);
			rotated.rotate(Math.PI / 2);
			clip.add(text(gc, "CD"), rotated);
			gc.clipToText(clip, FILL);
		});
		assertEquals(1, count(content, "\\bBT\\b"), content);
		assertTrue(content.matches("(?s).*\\bq\\s+BT\\s+7 Tr\\s+.*\\bTJ\\b.*\\bTJ\\b.*\\bET\\s+0 Tr\\s.*\\bre\\s+f\\s+Q\\b.*"),
				content);
		// The first run: translation only, y flipped (200 - 20); the second: rotated
		assertTrue(content.contains("1 0 0 1 10 180 Tm"), content);
		assertTrue(content.matches("(?s).*\\b0 -1 1 0 10 160 Tm.*"), content);
	}

	/** Nothing is painted, and no text object is written, when the clip has no runs. */
	@Test
	public void emptyClipPaintsNothing() throws Exception {
		final String content = content(PDFParams.createDefault(), gc -> gc.clipToText(new TextClip(), FILL));
		assertFalse(content.contains(" re"), content);
		assertFalse(content.contains("BT"), content);
	}

	/** On an untagged page the clip is the only copy of text that paints nothing; visible text is still drawn. */
	@Test
	public void clipShowsInvisibleText() throws Exception {
		final String content = content(PDFParams.createDefault(), gc -> {
			final TextImpl hidden = text(gc, "AB");
			final TextImpl shown = text(gc, "CD");
			final TextClip clip = new TextClip();
			clip.add(hidden, AffineTransform.getTranslateInstance(10, 20));
			clip.add(shown, AffineTransform.getTranslateInstance(10, 40));
			gc.clipToText(clip, FILL);
			gc.setFillPaint(RGBAColor.create(0, 0, 0, 0));
			gc.drawText(hidden, 10, 20);
			gc.setFillPaint(RGBColor.create(0, 0, 1));
			gc.drawText(shown, 10, 40);
		});
		assertEquals(2, count(content, "\\bBT\\b"), content);
		assertEquals(3, count(content, "\\bTJ\\b"), content);
	}

	/** Tagged: the clip is an artifact and the text that paints nothing keeps its own marked copy. */
	@Test
	public void taggedKeepsTheText() throws Exception {
		final String content = content(PDFParams.createDefault().withTagged(TaggedParams.TAGGED), gc -> {
			final TextImpl hidden = text(gc, "AB");
			final TextClip clip = new TextClip();
			clip.add(hidden, AffineTransform.getTranslateInstance(10, 20));
			gc.clipToText(clip, FILL);
			gc.setFillPaint(RGBAColor.create(0, 0, 0, 0));
			gc.drawText(hidden, 10, 20);
		});
		assertTrue(content.matches("(?s).*/Artifact\\s+BMC\\s+BT\\s+7 Tr.*"), content);
		assertEquals(2, count(content, "\\bBT\\b"), content);
	}

	/** A recording replays the clip as a clip, so PDF still clips with text. */
	@Test
	public void recordingReplaysTheClip() throws Exception {
		final String content = content(PDFParams.createDefault(), gc -> {
			final RecorderGC recorder = new RecorderGC(gc.getFontManager());
			final TextClip clip = new TextClip();
			clip.add(text(gc, "AB"), AffineTransform.getTranslateInstance(10, 20));
			recorder.clipToText(clip, FILL);
			recorder.getPage().drawTo(gc);
		});
		assertTrue(content.matches("(?s).*\\bBT\\s+7 Tr\\s+.*\\bET\\s+0 Tr\\s.*\\bre\\s+f\\b.*"), content);
	}

	/** Where transparency is not allowed, text with alpha 0 is written invisible (3 Tr) instead of opaque. */
	@Test
	public void invisibleTextWithoutTransparency() throws Exception {
		final Drawing drawing = gc -> {
			gc.setFillPaint(RGBAColor.create(0, 0, 0, 0));
			gc.drawText(text(gc, "AB"), 10, 20);
			gc.setFillPaint(RGBColor.create(0, 0, 0));
			gc.drawText(text(gc, "CD"), 10, 40);
		};
		final String v13 = content(PDFParams.createDefault().withVersion(PDFParams.Version.V_1_3), drawing);
		assertEquals(1, count(v13, "\\b3 Tr\\b"), v13);
		assertTrue(v13.matches("(?s).*\\bBT\\s+[^B]*?3 Tr\\s.*?TJ\\s+0 Tr\\s+ET\\b.*"), v13);
		final String v17 = content(PDFParams.createDefault(), drawing);
		assertEquals(0, count(v17, "\\b3 Tr\\b"), v17);
	}

	/** The default clip (Java2D, SVG) uses the outlines that the font fills. */
	@Test
	public void outlineOfRuns() throws Exception {
		content(PDFParams.createDefault(), gc -> {
			final TextClip clip = new TextClip();
			clip.add(text(gc, "AB"), AffineTransform.getTranslateInstance(10, 20));
			final Rectangle2D bounds = TextClip.outline(clip.getRuns(), gc.getFontManager()).getBounds2D();
			assertFalse(bounds.isEmpty());
			assertTrue(bounds.getMinX() > 9 && bounds.getMaxX() < 40 && bounds.getMaxY() <= 21, bounds.toString());
		});
	}
}
