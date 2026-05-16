package net.zamasoft.pdfg2d.pdf.font.cid;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;

import net.zamasoft.pdfg2d.resolver.Source;
import net.zamasoft.pdfg2d.resolver.SourceResolver;
import net.zamasoft.pdfg2d.resolver.composite.CompositeSourceResolver;
import net.zamasoft.pdfg2d.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.pdf.util.PDFUtils;
import net.zamasoft.pdfg2d.util.IntMap;

/**
 * Parses the CID table from a CMap file.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
class CIDTableParser {
	private Reader in;

	/** Current character being parsed. */
	private int ch;

	private SourceResolver resolver;

	/**
	 * Parses the CID table from the given CMap source and populates the provided
	 * integer map with character-code-to-CID mappings.
	 *
	 * @param source the CMap source to parse
	 * @param toCID  the map to populate with character-code-to-CID entries
	 * @throws IOException   if an I/O error occurs while reading the source
	 * @throws CMapException if the CMap file contains invalid data
	 */
	public void parse(Source source, IntMap toCID) throws IOException, CMapException {
		this.in = new InputStreamReader(new BufferedInputStream(source.getInputStream()), "ISO-8859-1");
		try {
			try {
				String ptoken = null;
				for (;;) {
					String token = this.nextToken();
					if (token.equals("usecmap")) {
						CIDTableParser parser = new CIDTableParser();
						String href = ptoken.substring(1);
						if (this.resolver == null) {
							this.resolver = CompositeSourceResolver.createGenericCompositeSourceResolver();
						}
						Source source2 = this.resolver.resolve(URIHelper.resolve("UTF-8", source.getURI(), href));
						try {
							parser.parse(source2, toCID);
						} finally {
							this.resolver.release(source2);
						}
					} else if (token.equals("begincidrange")) {
						this.parseCidRanges(toCID);
					} else if (token.equals("begincidchar")) {
						this.parseCidChars(toCID);
					} else if (token.equals("/CIDSystemInfo")) {
						this.parseCidSystemInfo();
					}
					ptoken = token;
				}
			} catch (EOFException e) {
				// ignore
			} catch (URISyntaxException e) {
				IOException ioe = new IOException();
				ioe.initCause(e);
				throw ioe;
			}
		} finally {
			this.in.close();
		}
	}

	private static int parseCode(String a) {
		int code;
		if (a.length() <= 4) {
			code = Integer.parseInt(a, 16);
		} else {
			// Surrogate pair
			int h = Integer.parseInt(a.substring(0, 4), 16);
			int l = Integer.parseInt(a.substring(4, 8), 16);
			code = 0x10000 + (h - 0xD800) * 0x400 + (l - 0xDC00);
		}
		return code;
	}

	private void parseCidChars(IntMap toCID) throws IOException, CMapException {
		for (;;) {
			String a = this.nextToken();
			if (a.equals("endcidchar")) {
				break;
			}
			String b = this.nextToken();
			a = a.substring(1, a.length() - 1).trim();
			try {
				int code = parseCode(a);
				int offset = Integer.parseInt(b);
				toCID.set(code, offset);
			} catch (NumberFormatException e) {
				// throw e;
			}
		}
	}

	private void parseCidRanges(IntMap toCID) throws IOException, CMapException {
		for (;;) {
			String a = this.nextToken();
			if (a.equals("endcidrange")) {
				break;
			}
			String b = this.nextToken();
			String c = this.nextToken();

			a = a.substring(1, a.length() - 1).trim();
			b = b.substring(1, b.length() - 1).trim();
			try {
				int start = parseCode(a);
				int end = parseCode(b);
				if (a.length() != b.length() || a.length() % 2 != 0) {
					throw new CMapException(
							"Start and end character codes must have matching byte lengths and be even-digit hex values");
				}
				int offset = Integer.parseInt(c);

				int len = end - start + 1;
				for (int j = 0; j < len; ++j) {
					toCID.set(start + j, offset + j);
				}
			} catch (NumberFormatException e) {
				// throw e;
			}
		}
	}

	private void parseCidSystemInfo() throws IOException {
		this.nextToken();
		String dict = this.nextToken();
		if (!dict.equals("dict")) {
			return;
		}
		String dup = this.nextToken();
		if (!dup.equals("dup")) {
			return;
		}
		String begin = this.nextToken();
		if (!begin.equals("begin")) {
			return;
		}
		for (;;) {
			String key = this.nextToken();
			if (key.equals("/Registry")) {
				this.nextToken();
				String def = this.nextToken();
				if (!def.equals("def")) {
					return;
				}
			} else if (key.equals("/Ordering")) {
				this.nextToken();
				String def = this.nextToken();
				if (!def.equals("def")) {
					return;
				}
			} else if (key.equals("/Supplement")) {
				this.nextToken();
				String def = this.nextToken();
				if (!def.equals("def")) {
					return;
				}
			} else if (key.equals("end")) {
				break;
			}
		}
	}

	private void skipWhiteSpace() throws IOException {
		for (; this.ch != -1 && Character.isWhitespace((char) this.ch); this.ch = this.in.read()) {
			// do nothing
		}
		if (this.ch == -1) {
			throw new EOFException();
		}
	}

	private String nextToken() throws IOException {
		this.skipWhiteSpace();
		StringBuffer buff = new StringBuffer();
		for (; this.ch != -1 && !Character.isWhitespace((char) this.ch); this.ch = this.in.read()) {
			buff.append((char) this.ch);
		}
		if (this.ch == -1) {
			throw new EOFException();
		}
		String s = buff.toString();
		return PDFUtils.decodeName(s, "MS932");
	}
}
