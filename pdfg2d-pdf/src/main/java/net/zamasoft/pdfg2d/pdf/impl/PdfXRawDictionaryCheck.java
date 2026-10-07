package net.zamasoft.pdfg2d.pdf.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Checks dictionary bodies that callers write raw through the low-level API
 * (an extended graphics state, an annotation) against PDF/X before they reach
 * the file (2026-10-07). Those writers hand out a plain output, so the keys
 * cannot be checked as they are written; under PDF/X the body is buffered,
 * read back here and only then copied to the file.
 *
 * <p>
 * The reader understands the PDF object syntax the writers produce (names,
 * numbers, booleans, null, strings, arrays, dictionaries and indirect
 * references); it is not a general PDF parser.
 * </p>
 */
final class PdfXRawDictionaryCheck {
	/** A PDF name, kept apart from strings. */
	record Name(String value) {
	}

	private final byte[] data;
	private int pos;

	private PdfXRawDictionaryCheck(final byte[] data) {
		this.data = data;
	}

	/**
	 * Refuses an extended graphics state that PDF/X does not allow: a transfer
	 * function other than /Default, /HTP, a halftone with /HalftoneName or a
	 * type other than 1 or 5, and under PDF/X-1a and X-3 (no transparency) a
	 * soft mask, an alpha below 1 or a blend mode other than Normal or
	 * Compatible. Same rules as the regression preflight R8 and R9.
	 */
	static void checkGraphicsState(final byte[] body, final PDFParams.Version version) {
		final Map<String, Object> gs = readBody(body);
		for (final String key : new String[] { "TR", "TR2" }) {
			if (gs.containsKey(key) && !new Name("Default").equals(gs.get(key))) {
				throw refused("/" + key + " other than /Default");
			}
		}
		if (gs.containsKey("HTP")) {
			throw refused("/HTP");
		}
		final Object ht = gs.get("HT");
		if (ht instanceof Map<?, ?> halftone) {
			if (halftone.containsKey("HalftoneName")) {
				throw refused("a halftone with /HalftoneName");
			}
			final Object type = halftone.get("HalftoneType");
			if (!(type instanceof Double d) || (d.doubleValue() != 1 && d.doubleValue() != 5)) {
				throw refused("a halftone of a type other than 1 or 5");
			}
		} else if (ht != null && !new Name("Default").equals(ht)) {
			throw refused("/HT other than /Default or a halftone dictionary");
		}
		if (version.isPdfXOnPdf14()) {
			if (gs.containsKey("SMask") && !new Name("None").equals(gs.get("SMask"))) {
				throw refused("a soft mask (" + version.pdfxVersion() + " has no transparency)");
			}
			for (final String key : new String[] { "CA", "ca" }) {
				if (gs.containsKey(key) && !(gs.get(key) instanceof Double d && d.doubleValue() == 1)) {
					throw refused("/" + key + " other than 1 (" + version.pdfxVersion() + " has no transparency)");
				}
			}
			if (gs.containsKey("BM") && !normalBlend(gs.get("BM"))) {
				throw refused("a blend mode other than Normal or Compatible (" + version.pdfxVersion()
						+ " has no transparency)");
			}
		}
	}

	/**
	 * Refuses an annotation whose action is anything but an in-document GoTo,
	 * or that carries additional actions (PDF/X-1a and X-3, 6.13 and 6.14).
	 */
	static void checkAnnotation(final byte[] body) {
		final Map<String, Object> annot = readBody(body);
		final Object action = annot.get("A");
		if (action instanceof Map<?, ?> a && !new Name("GoTo").equals(a.get("S"))) {
			throw new UnsupportedOperationException(
					"PDF/X-1a and PDF/X-3 allow only GoTo actions on annotations, not " + a.get("S") + ".");
		}
		if (annot.containsKey("AA")) {
			throw new UnsupportedOperationException("PDF/X-1a and PDF/X-3 do not allow additional actions (/AA).");
		}
	}

	private static boolean normalBlend(final Object bm) {
		if (bm instanceof Name name) {
			return name.value().equals("Normal") || name.value().equals("Compatible");
		}
		if (bm instanceof List<?> list && !list.isEmpty()) {
			for (final Object o : list) {
				if (!normalBlend(o)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	private static UnsupportedOperationException refused(final String what) {
		return new UnsupportedOperationException("PDF/X does not allow " + what + " in an extended graphics state.");
	}

	/** Reads the inside of a dictionary (the key/value pairs, without the brackets). */
	static Map<String, Object> readBody(final byte[] body) {
		return new PdfXRawDictionaryCheck(body).readPairs(false);
	}

	private Map<String, Object> readPairs(final boolean bracketed) {
		final Map<String, Object> dict = new LinkedHashMap<>();
		for (;;) {
			this.skipSpace();
			if (this.pos >= this.data.length) {
				if (bracketed) {
					throw new IllegalStateException("unterminated dictionary");
				}
				return dict;
			}
			if (bracketed && this.peek(">>")) {
				this.pos += 2;
				return dict;
			}
			final Object key = this.readObject();
			if (!(key instanceof Name name)) {
				throw new IllegalStateException("dictionary key is not a name: " + key);
			}
			dict.put(name.value(), this.readObject());
		}
	}

	private Object readObject() {
		this.skipSpace();
		if (this.pos >= this.data.length) {
			throw new IllegalStateException("missing value");
		}
		final int c = this.data[this.pos] & 0xFF;
		if (c == '/') {
			++this.pos;
			final int start = this.pos;
			while (this.pos < this.data.length && !delimiter(this.data[this.pos] & 0xFF)) {
				++this.pos;
			}
			return new Name(decodeName(new String(this.data, start, this.pos - start, StandardCharsets.ISO_8859_1)));
		}
		if (this.peek("<<")) {
			this.pos += 2;
			return this.readPairs(true);
		}
		if (c == '<') {
			final int end = this.indexOf('>', this.pos);
			this.pos = end + 1;
			return "";
		}
		if (c == '(') {
			this.skipLiteralString();
			return "";
		}
		if (c == '[') {
			++this.pos;
			final List<Object> list = new ArrayList<>();
			for (;;) {
				this.skipSpace();
				if (this.pos >= this.data.length) {
					throw new IllegalStateException("unterminated array");
				}
				if (this.data[this.pos] == ']') {
					++this.pos;
					return list;
				}
				list.add(this.readObject());
			}
		}
		final int start = this.pos;
		while (this.pos < this.data.length && !delimiter(this.data[this.pos] & 0xFF)) {
			++this.pos;
		}
		final String token = new String(this.data, start, this.pos - start, StandardCharsets.ISO_8859_1);
		switch (token) {
		case "true":
			return Boolean.TRUE;
		case "false":
			return Boolean.FALSE;
		case "null":
			return null;
		default:
			break;
		}
		final double number;
		try {
			number = Double.parseDouble(token);
		} catch (final NumberFormatException e) {
			throw new IllegalStateException("unexpected token: " + token);
		}
		// An indirect reference: "n g R"
		final int save = this.pos;
		this.skipSpace();
		final int genStart = this.pos;
		while (this.pos < this.data.length && Character.isDigit(this.data[this.pos])) {
			++this.pos;
		}
		if (this.pos > genStart) {
			this.skipSpace();
			if (this.pos < this.data.length && this.data[this.pos] == 'R'
					&& (this.pos + 1 >= this.data.length || delimiter(this.data[this.pos + 1] & 0xFF))) {
				++this.pos;
				return "ref";
			}
		}
		this.pos = save;
		return Double.valueOf(number);
	}

	private void skipLiteralString() {
		int depth = 0;
		while (this.pos < this.data.length) {
			final int c = this.data[this.pos++] & 0xFF;
			if (c == '\\') {
				++this.pos;
			} else if (c == '(') {
				++depth;
			} else if (c == ')' && --depth == 0) {
				return;
			}
		}
		throw new IllegalStateException("unterminated string");
	}

	private void skipSpace() {
		while (this.pos < this.data.length) {
			final int c = this.data[this.pos] & 0xFF;
			if (c == '%') {
				while (this.pos < this.data.length && this.data[this.pos] != '\n' && this.data[this.pos] != '\r') {
					++this.pos;
				}
			} else if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\f' || c == 0) {
				++this.pos;
			} else {
				return;
			}
		}
	}

	private boolean peek(final String s) {
		if (this.pos + s.length() > this.data.length) {
			return false;
		}
		for (int i = 0; i < s.length(); ++i) {
			if (this.data[this.pos + i] != s.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private int indexOf(final char c, final int from) {
		for (int i = from; i < this.data.length; ++i) {
			if (this.data[i] == c) {
				return i;
			}
		}
		throw new IllegalStateException("unterminated hex string");
	}

	private static boolean delimiter(final int c) {
		return c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\f' || c == 0 || c == '(' || c == ')'
				|| c == '<' || c == '>' || c == '[' || c == ']' || c == '{' || c == '}' || c == '/' || c == '%';
	}

	private static String decodeName(final String raw) {
		if (raw.indexOf('#') < 0) {
			return raw;
		}
		final StringBuilder b = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); ++i) {
			final char c = raw.charAt(i);
			if (c == '#' && i + 2 < raw.length()) {
				b.append((char) Integer.parseInt(raw.substring(i + 1, i + 3), 16));
				i += 2;
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}
}
