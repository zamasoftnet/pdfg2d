package net.zamasoft.pdfg2d.font.otf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.table.NameRecord;
import net.zamasoft.pdfg2d.font.table.NameTable;
import net.zamasoft.pdfg2d.font.table.Table;

public class OpenTypeFontSourceNameTest {
	private static NameRecord name(final short platformId, final short encodingId, final String value) {
		return new NameRecord(platformId, encodingId, (short) 0, Table.NAME_POSTSCRIPT_NAME, (short) 0, (short) 0,
				value);
	}

	@Test
	public void asciiPostScriptNameWinsOverLocalizedRecord() {
		final var table = new NameTable(new NameRecord[] {
				name(Table.PLATFORM_MACINTOSH, Table.ENCODING_ROMAN, "GothicA1-Regular"),
				name(Table.PLATFORM_MICROSOFT, Table.ENCODING_UCS2, "고딕A1-Regular") });

		assertEquals("GothicA1-Regular", OpenTypeFontSource.selectPostScriptName(table));
	}

	@Test
	public void decodedWindowsNameIsTheFallbackWhenNoAsciiNameExists() {
		final var table = new NameTable(new NameRecord[] {
				name(Table.PLATFORM_MACINTOSH, Table.ENCODING_KOREAN, "\ufffd\ufffdA1-Regular"),
				name(Table.PLATFORM_MICROSOFT, Table.ENCODING_UCS2, "고딕A1-Regular") });

		assertEquals("고딕A1-Regular", OpenTypeFontSource.selectPostScriptName(table));
	}
}
