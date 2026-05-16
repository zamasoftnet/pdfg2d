package net.zamasoft.pdfg2d.font;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.zamasoft.pdfg2d.font.table.CmapTable;
import net.zamasoft.pdfg2d.font.table.Table;

public class FontFileTest {
    @ParameterizedTest
    @ValueSource(strings = { "test.otf", "test.ttf", "test.woff" })
    public void testLoadSupportedFontFormats(String resourceName) throws Exception {
        File file = new File("src/test/resources/data/" + resourceName);
        assertTrue(file.isFile(), "Test font should exist: " + resourceName);

        FontFile font = new FontFile(file);
        OpenTypeFont otf = font.getFont();
        assertNotNull(otf, "OpenTypeFont should not be null");

        CmapTable cmapt = (CmapTable) otf.getTable(Table.CMAP);
        assertNotNull(cmapt, "CmapTable should not be null");

        assertTrue(cmapt.getTableCount() > 0, "CmapTable should expose at least one subtable");

        var cmap = cmapt.getCmapFormat(Table.PLATFORM_UNICODE, (short) -1);
        if (cmap == null) {
            cmap = cmapt.getCmapFormat(Table.PLATFORM_MICROSOFT, (short) -1);
        }
        assertNotNull(cmap, "CmapFormat should not be null");
    }

    @Test
    public void testLoadFont() throws Exception {
        testLoadSupportedFontFormats("test.otf");
    }
}
