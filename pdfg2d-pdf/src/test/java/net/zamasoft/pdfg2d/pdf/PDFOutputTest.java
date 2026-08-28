package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the low-level PDF token serializer {@link PDFOutput}.
 * Every PDF object in the document ultimately passes through this class, so
 * its escaping and number formatting rules are verified byte-exactly here.
 */
public class PDFOutputTest {

	private ByteArrayOutputStream buff;
	private PDFOutput out;

	private PDFOutput create() {
		this.buff = new ByteArrayOutputStream();
		this.out = new PDFOutput(this.buff, "UTF-8");
		return this.out;
	}

	private String result() throws IOException {
		this.out.flush();
		return this.buff.toString(StandardCharsets.ISO_8859_1);
	}

	@Test
	public void testIntegersAreSpaceSeparated() throws Exception {
		final var o = create();
		o.writeInt(42);
		o.writeInt(-7);
		o.writeInt(0);
		assertEquals("42 -7 0", result());
	}

	@Test
	public void testRealDefaultPrecisionRoundsToOneDecimal() throws Exception {
		final var o = create();
		o.writeReal(1.25);
		o.writeReal(2.0);
		o.writeReal(-3.14);
		assertEquals("1.3 2 -3.1", result());
	}

	@Test
	public void testRealDropsValuesBelowEpsilon() throws Exception {
		final var o = create();
		o.writeReal(0.04); // below the 0.05 epsilon for precision 1
		assertEquals("0", result());
	}

	@Test
	public void testRealClampsToPdfImplementationLimit() throws Exception {
		// 32767 is the maximum real magnitude guaranteed by PDF readers
		final var o = create();
		o.writeReal(1e6);
		o.writeReal(-1e6);
		assertEquals("32767 -32767", result());
	}

	@Test
	public void testRealExactSkipsTheClamp() throws Exception {
		// Transformation matrix coefficients must never be clamped: with large
		// scale factors the translation of an intermediate cm legitimately
		// exceeds 32767 and is compensated by subsequent transforms. Clamping
		// it corrupts the geometry (a 2x1 placeholder JPEG stretched ~112x
		// vanished off-page, kanaloco.jp 2026-08-09).
		final var o = create();
		o.writeRealExact(1e6);
		o.writeRealExact(-1e6);
		assertEquals("1000000 -1000000", result());
	}

	@Test
	public void testCoefficientKeepsHighPrecisionAndRestoresStreamPrecision() throws Exception {
		// 行列係数(scale/shear)の丸め誤差は座標値に乗算される。座標既定の
		// 低精度(例: 0.375→0.4)で係数を書くと、丸め前の係数×ページ寸法で
		// 計算された平行移動項と食い違い、拡大縮小されたSVG・画像が
		// ページ寸法×誤差ぶんずれてクリップにかかる(A4で4pt超。MDNの
		// マスクアイコンが上に欠ける形で発覚、2026-08-27)
		final var o = create();
		o.setPrecision(2);
		o.writeRealCoefficient(0.375);
		o.writeReal(513.1275); // 座標・平行移動は従来precisionのまま
		assertEquals("0.375 513.13", result());
	}

	@Test
	public void testRealHonorsConfiguredPrecision() throws Exception {
		final var o = create();
		o.setPrecision(3);
		o.writeReal(1.2345);
		o.writeReal(0.5);
		assertEquals("1.235 0.5", result());
	}

	@Test
	public void testStringEscapesDelimitersAndControls() throws Exception {
		final var o = create();
		o.writeString("a(b)c\\d\ne");
		assertEquals("(a\\(b\\)c\\\\d\\ne)", result());
	}

	@Test
	public void testNameIsSlashPrefixed() throws Exception {
		final var o = create();
		o.writeName("Type");
		o.writeName("Name With Space");
		final var s = result();
		assertTrue(s.startsWith("/Type"));
		// Space must be #-escaped inside a PDF name
		assertTrue(s.contains("/Name#20With#20Space"), s);
	}

	@Test
	public void testOverlongNameIsRejected() {
		final var o = create();
		assertThrows(IllegalArgumentException.class, () -> o.writeName("x".repeat(128)));
	}

	@Test
	public void testTextSwitchesToUTF16ForNonAscii() throws Exception {
		final var o = create();
		o.writeText("Hello");
		assertEquals("(Hello)", result());

		final var o2 = create();
		o2.writeText("あ"); // U+3042
		assertEquals("<FEFF3042>", result());
	}

	@Test
	public void testBytes8WritesHexString() throws Exception {
		final var o = create();
		o.writeBytes8(new byte[] { (byte) 0xAB, (byte) 0xCD, 0x01 }, 0, 3);
		assertEquals("<ABCD01>", result());
	}

	@Test
	public void testBytes16WritesHexString() throws Exception {
		final var o = create();
		o.writeBytes16(0x1234);
		o.writeBytes16(new int[] { 0xBEEF }, 0, 1);
		assertEquals("<1234> <BEEF>", result());
	}

	@Test
	public void testDateUsesPdfFormatWithTimezone() throws Exception {
		final var o = create();
		final var tokyo = TimeZone.getTimeZone("Asia/Tokyo");
		final var time = ZonedDateTime.of(2020, 12, 20, 17, 0, 0, 0, ZoneId.of("Asia/Tokyo")).toInstant()
				.toEpochMilli();
		o.writeDate(time, tokyo);
		assertEquals("(D:20201220170000+09'00')", result());
	}

	@Test
	public void testHashAndArrayDelimiters() throws Exception {
		final var o = create();
		o.startHash();
		o.writeName("K");
		o.startArray();
		o.writeInt(1);
		o.writeInt(2);
		o.endArray();
		o.endHash();
		final var s = result();
		assertTrue(s.contains("<<"), s);
		// Tokens after a delimiter are still space-separated by design
		assertTrue(s.contains("/K [ 1 2 ]"), s);
		assertTrue(s.contains(">>"), s);
	}

	@Test
	public void testLineBreakSuppressesLeadingSpace() throws Exception {
		final var o = create();
		o.writeOperator("q");
		o.lineBreak();
		o.writeOperator("Q");
		assertEquals("q\r\nQ", result());
	}

	@Test
	public void testEqualsComparesAtOutputPrecision() {
		final var o = create();
		assertTrue(o.equals(1.0, 1.04)); // both round to 1.0
		assertFalse(o.equals(1.0, 1.2));
		assertTrue(o.equals(5.0, 5.0));
	}

	@Test
	public void testBooleanAndNullLiterals() throws Exception {
		final var o = create();
		o.writeBoolean(true);
		o.writeBoolean(false);
		o.writeNull();
		assertEquals("true false null", result());
	}

	@Test
	public void testObjectRefSerialization() throws Exception {
		final var o = create();
		o.writeObjectRef(new ObjectRef(12, 0) {
		});
		assertEquals("12 0 R", result());
	}
}
