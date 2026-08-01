package net.zamasoft.pdfg2d.pdf.font.cid;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import net.zamasoft.pdfg2d.pdf.util.PDFUtils;

/**
 * Parses CID character set information from CMap files.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class CMapParser {
	private Reader in;

	/** Current character being parsed. */
	private int ch;

	private CMap cmap;

	/**
	 * Parses CMap metadata (encoding name, registry, ordering, supplement) from the
	 * given input stream and populates the provided {@link CMap} object.
	 *
	 * @param in   the raw input stream of the CMap file
	 * @param cmap the CMap object to populate with parsed metadata
	 * @return the populated {@code CMap} object
	 * @throws IOException if an I/O error occurs while reading the stream
	 */
	public CMap parse(InputStream in, CMap cmap) throws IOException {
		this.cmap = cmap;
		// BufferedReaderで1文字readのStreamDecoder往復を回避(2026-08-01、
		// JFR実測でトークナイザの文字読みが起動時間の上位だった)
		this.in = new java.io.BufferedReader(new InputStreamReader(new BufferedInputStream(in), "ISO-8859-1"));
		try {
			try {
				for (;;) {
					String token = this.nextToken();
					if (token.equals("begincidrange")) {
						break;
					} else if (token.equals("/CIDSystemInfo")) {
						this.parseCidSystemInfo();
					} else if (token.equals("/CMapName")) {
						token = this.nextToken();
						this.cmap.encoding = token.substring(1);
					}
				}
			} catch (EOFException e) {
				// ignore
			}
			return this.cmap;
		} finally {
			this.in.close();
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
				String registry = this.nextToken();
				this.cmap.registry = removeParenthesises(registry);
				String def = this.nextToken();
				if (!def.equals("def")) {
					return;
				}
			} else if (key.equals("/Ordering")) {
				String ordering = this.nextToken();
				this.cmap.ordering = removeParenthesises(ordering);
				String def = this.nextToken();
				if (!def.equals("def")) {
					return;
				}
			} else if (key.equals("/Supplement")) {
				String supplement = this.nextToken();
				this.cmap.supplement = Integer.parseInt(supplement.trim());
				String def = this.nextToken();
				if (!def.equals("def")) {
					return;
				}
			} else if (key.equals("end")) {
				break;
			}
		}
	}

	private static String removeParenthesises(String token) {
		token = token.trim();
		token = token.substring(1, token.length() - 1);
		return token;
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
		StringBuilder buff = new StringBuilder();
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