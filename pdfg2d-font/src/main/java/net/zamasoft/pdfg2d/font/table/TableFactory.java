package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

import net.zamasoft.pdfg2d.font.OpenTypeFont;
import net.zamasoft.pdfg2d.font.cff.CFFTable;

/**
 * Factory that instantiates the correct {@link Table} implementation for each
 * OpenType/TrueType table tag.  Unknown or unimplemented table types return
 * {@code null}.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class TableFactory {
	private TableFactory() {
	}

	/**
	 * Creates a table object for the given directory entry.
	 *
	 * @param otf the font that owns the table
	 * @param de  the directory entry describing the table's position and type
	 * @param raf the random-access file to read from
	 * @return the parsed {@link Table}, or {@code null} if the table type is
	 *         unknown or not yet implemented
	 * @throws IOException if the table data cannot be read
	 */
	public static Table create(final OpenTypeFont otf, final DirectoryEntry de, final RandomAccessFile raf)
			throws IOException {
		return switch (de.tag()) {
			case Table.BASE -> null; // Not implemented
			case Table.CFF -> new CFFTable(de, raf);
			case Table.DSIG -> null;
			case Table.EBDT -> null;
			case Table.EBLC -> null;
			case Table.EBSC -> null;
			case Table.GDEF -> null;
			case Table.GPOS -> new GposTable(de, raf);
			case Table.GSUB -> new GsubTable(de, raf);
			case Table.JSTF -> null;
			case Table.LTSH -> null; // new LtshTable(de, raf);
			case Table.MMFX -> null;
			case Table.MMSD -> null;
			case Table.OS_2 -> new Os2Table(de, raf);
			case Table.PCLT -> null;
			case Table.VDMX -> null; // new VdmxTable(de, raf);
			case Table.VORG -> new VorgTable(de, raf);
			case Table.CMAP -> new CmapTable(otf, de, raf);
			case Table.CVT -> new CvtTable(de, raf);
			case Table.FPGM -> new FpgmTable(de, raf);
			case Table.FVAR -> null;
			case Table.GASP -> null;
			case Table.GLYF -> null;
			case Table.HDMX -> null;
			case Table.HEAD -> new HeadTable(de, raf);
			case Table.HHEA -> new HheaTable(de, raf);
			case Table.HMTX -> null;
			case Table.KERN -> new KernTable(de, raf);
			case Table.LOCA -> null;
			case Table.MAXP -> new MaxpTable(de, raf);
			case Table.NAME -> new NameTable(de, raf);
			case Table.PREP -> new PrepTable(de, raf);
			case Table.POST -> new PostTable(de, raf);
			case Table.VHEA -> new VheaTable(de, raf);
			case Table.VMTX -> null;
			default -> null;
		};
	}
}
