package net.zamasoft.pdfg2d.pdf.font.cid;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.pdfg2d.util.IntMap;
import net.zamasoft.pdfg2d.util.IntMapIterator;
import net.zamasoft.pdfg2d.util.MapIntMap;

/**
 * Character mapping information for CID fonts.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CIDTable implements Serializable {
	private static final Logger LOG = Logger.getLogger(CIDTable.class.getName());

	private static final long serialVersionUID = 0;

	public static final char MISSING_CHAR = '?';

	protected final Source cmapSource;

	protected final String javaEncoding;

	transient protected WeakReference<IntMap> toCID = null;

	transient protected int missingCID = 0;

	transient protected Charset charset = null;

	/**
	 * Constructs a CIDTable with the given CMap source and Java encoding name.
	 *
	 * @param cmapSource   the source of the CMap file
	 * @param javaEncoding the Java charset name used to decode character bytes
	 */
	public CIDTable(Source cmapSource, String javaEncoding) {
		this.cmapSource = cmapSource;
		this.javaEncoding = javaEncoding;
	}

	private synchronized IntMap getToCid() {
		IntMap toCid = null;
		if (this.toCID != null) {
			toCid = this.toCID.get();
			if (toCid != null) {
				return toCid;
			}
		}
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("load cid table:" + this.cmapSource);
		}

		CIDTableParser parser = new CIDTableParser();
		try {
			Charset charset = this.getCharset();
			// UTF-16BE matches Java's internal character code
			if (charset.name().equalsIgnoreCase("UTF-16BE")) {
				toCid = new MapIntMap();
				parser.parse(this.cmapSource, toCid);
			} else {
				toCid = new IntList();
				parser.parse(this.cmapSource, toCid);
				IntList intList = new IntList();
				CharsetDecoder decoder = charset.newDecoder();
				ByteBuffer in = ByteBuffer.allocate(4);
				CharBuffer out = CharBuffer.allocate(1);
				IntMapIterator i = toCid.getIterator();
				while (i.next()) {
					int cid = i.value();
					int c = i.key();
					if (cid != 0) {
						in.clear();
						if (c < 0xFF) {
							in.put((byte) (c & 0xFF));
						} else if (c <= 0xFFFF) {
							in.put((byte) ((c >> 8) & 0xFF));
							in.put((byte) (c & 0xFF));
						} else if (c <= 0xFFFFFF) {
							in.put((byte) ((c >> 16) & 0xFF));
							in.put((byte) ((c >> 8) & 0xFF));
							in.put((byte) (c & 0xFF));
						} else {
							in.put((byte) ((c >> 24) & 0xFF));
							in.put((byte) ((c >> 16) & 0xFF));
							in.put((byte) ((c >> 8) & 0xFF));
							in.put((byte) (c & 0xFF));
						}
						in.flip();
						out.clear();
						decoder.reset();
						decoder.decode(in, out, true);
						decoder.flush(out);
						intList.set(out.get(0), cid);
					}
				}
				toCid = intList;
			}
			// if (!toCid.contains(MISSING_CHAR)) {
			// System.err.println("missing missing char");
			// }
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		this.toCID = new WeakReference<IntMap>(toCid);
		return toCid;
	}

	/**
	 * Returns the Java charset used for encoding.
	 * 
	 * @return the charset
	 */
	public Charset getCharset() {
		if (this.charset == null) {
			this.charset = Charset.forName(this.javaEncoding);
		}
		return this.charset;
	}

	/**
	 * Returns the CID for missing/unknown characters.
	 * 
	 * @return the CID for the missing character glyph
	 */
	public int getMissingCID() {
		if (this.missingCID == 0) {
			this.missingCID = this.toCID(CIDTable.MISSING_CHAR);
		}
		return this.missingCID;
	}

	/**
	 * Converts a character code to its CID.
	 * 
	 * @param c the character code
	 * @return the corresponding CID
	 */
	public int toCID(int c) {
		IntMap toCid = this.getToCid();
		if (!toCid.contains(c)) {
			return this.getMissingCID();
		}
		return toCid.get(c);
	}

	/**
	 * Checks if this table contains a mapping for the given character.
	 * 
	 * @param c the character code
	 * @return true if the character is mapped, false otherwise
	 */
	public boolean containsChar(int c) {
		IntMap toCid = this.getToCid();
		return toCid.contains(c);
	}

	/**
	 * Returns an iterator over all character-to-CID mappings in this table.
	 *
	 * @return an iterator over the character-to-CID entries
	 */
	public IntMapIterator getIterator() {
		IntMap toCid = this.getToCid();
		return toCid.getIterator();
	}
}
