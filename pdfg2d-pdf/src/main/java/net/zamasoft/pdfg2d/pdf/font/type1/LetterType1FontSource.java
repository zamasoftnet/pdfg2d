package net.zamasoft.pdfg2d.pdf.font.type1;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.pdfg2d.pdf.font.type1.AFMFontInfo.AFMGlyphInfo;
import net.zamasoft.pdfg2d.pdf.font.type1.Encoding.CodeMap;
import net.zamasoft.pdfg2d.pdf.font.type1.GlyphMap.Codes;
import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.pdfg2d.util.ShortList;

/**
 * Type1 Latin letter font source.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class LetterType1FontSource extends AbstractType1FontSource {
	private static final Logger LOG = Logger.getLogger(LetterType1FontSource.class.getName());

	private static final long serialVersionUID = 0;

	private final GlyphMap unicodeEncoding;

	private final Encoding pdfEncoding;

	private record Tables(IntList charToGid, GlyphInfo[] gidToGi) {
	}

	/**
	 * Read for every glyph by every conversion, so reading takes no lock. Threads that race build equal tables. It
	 * used to be a synchronized WeakReference: 16 concurrent conversions ran 1.4 times as fast as one (2026-10-31).
	 */
	transient private volatile Tables tables = null;

	public LetterType1FontSource(GlyphMap unicodeEncoding, Encoding pdfEncoding, AFMFontInfo fontInfo) {
		super(fontInfo);
		this.pdfEncoding = pdfEncoding;
		this.unicodeEncoding = unicodeEncoding;
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("new font: " + this.getFontName());
		}
	}

	private Tables getTables() {
		Tables tables = this.tables;
		if (tables == null) {
			tables = this.buildTables();
			this.tables = tables;
		}
		return tables;
	}

	private Tables buildTables() {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("build GlyphInfo: " + this.getFontName());
		}

		IntList charToGid = new IntList(-1);
		GlyphInfo[] gidToGi = new GlyphInfo[256];
		for (int i = 0; i < this.pdfEncoding.codeMaps.length; ++i) {
			CodeMap pc = this.pdfEncoding.codeMaps[i];
			Codes uc = (Codes) this.unicodeEncoding.nameToCodes.get(pc.name);
			for (int j = 0; j < uc.codes.length; ++j) {
				int code = uc.codes[j];
				charToGid.set(code, pc.gid);
			}
		}
		charToGid.pack();

		for (int i = 0; i < this.pdfEncoding.codeMaps.length; ++i) {
			CodeMap codeMap = this.pdfEncoding.codeMaps[i];
			int gid = codeMap.gid;
			String name = codeMap.name;
			AFMGlyphInfo aci = (AFMGlyphInfo) this.fontInfo.nameToGi.get(name);
			assert aci != null : this.getFontName() + "/" + name;

			IntList sgidToLigature;
			if (aci.nameToLigature != null) {
				sgidToLigature = new IntList(-1);
				for (Iterator<Entry<String, String>> j = aci.nameToLigature.entrySet().iterator(); j.hasNext();) {
					Entry<String, String> e = (Entry<String, String>) j.next();
					CodeMap scm = (CodeMap) this.pdfEncoding.nameToCodeMap.get(e.getKey());
					if (scm == null) {
						continue;
					}
					CodeMap lcm = (CodeMap) this.pdfEncoding.nameToCodeMap.get(e.getValue());
					if (lcm == null) {
						continue;
					}
					sgidToLigature.set(scm.gid, lcm.gid);
				}
				if (sgidToLigature.isEmpty()) {
					sgidToLigature = null;
				} else {
					sgidToLigature.pack();
				}
			} else {
				sgidToLigature = null;
			}
			ShortList sgidToKerning;
			if (aci.nameToKerning != null) {
				sgidToKerning = new ShortList();
				for (Iterator<Entry<String, Short>> j = aci.nameToKerning.entrySet().iterator(); j.hasNext();) {
					Entry<String, Short> e = j.next();
					String sname = (String) e.getKey();
					Short kerning = (Short) e.getValue();
					CodeMap scm = (CodeMap) this.pdfEncoding.nameToCodeMap.get(sname);
					if (scm == null) {
						continue;
					}
					sgidToKerning.set(scm.gid, kerning.shortValue());
				}
				if (sgidToKerning.isEmpty()) {
					sgidToKerning = null;
				} else {
					sgidToKerning.pack();
				}
			} else {
				sgidToKerning = null;
			}

			gidToGi[gid] = new GlyphInfo(aci.advance, sgidToLigature == null ? null : sgidToLigature.toArray(),
					sgidToKerning == null ? null : sgidToKerning.toArray());
		}
		return new Tables(charToGid, gidToGi);
	}

	protected GlyphInfo[] getGidToGi() {
		return this.getTables().gidToGi();
	}

	protected IntList getCidToGid() {
		return this.getTables().charToGid();
	}

	int toGID(int c) {
		return this.getCidToGid().get(c);
	}

	String getEncoding() {
		return this.pdfEncoding.name;
	}
}
