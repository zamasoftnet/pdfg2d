package net.zamasoft.pdfg2d.pdf.font.cid;

import java.io.IOException;
import java.io.Serializable;

import net.zamasoft.zstream.resolver.Source;

/**
 * Character mapping information for general CID fonts.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CMap implements Serializable {
	private static final long serialVersionUID = 0;

	protected final CIDTable cidTable;

	protected String encoding;

	protected String registry, ordering;

	protected int supplement;

	/**
	 * Constructs a CMap by loading and parsing the given source file.
	 *
	 * @param source       the source of the CMap file
	 * @param javaEncoding the Java charset name used for character decoding
	 * @throws IOException if the source cannot be read
	 */
	public CMap(Source source, String javaEncoding) throws IOException {
		this.cidTable = new CIDTable(source, javaEncoding);
		CMapParser parser = new CMapParser();
		parser.parse(source.getInputStream(), this);
	}

	/**
	 * Returns the CID table.
	 * 
	 * @return the CID table
	 */
	public CIDTable getCIDTable() {
		return this.cidTable;
	}

	/**
	 * Returns the PDF encoding name.
	 * 
	 * @return the encoding name
	 */
	public String getEncoding() {
		return this.encoding;
	}

	/**
	 * Returns the PDF registry name.
	 * 
	 * @return the registry name
	 */
	public String getRegistry() {
		return this.registry;
	}

	/**
	 * Returns the PDF ordering.
	 * 
	 * @return the ordering
	 */
	public String getOrdering() {
		return this.ordering;
	}

	/**
	 * Returns the PDF supplement number.
	 * 
	 * @return the supplement number
	 */
	public int getSupplement() {
		return this.supplement;
	}
}
