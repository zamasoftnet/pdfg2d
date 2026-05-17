package net.zamasoft.pdfg2d.pdf.font.type1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.pdf.font.type1.AFMFontInfo.AFMGlyphInfo;
import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.pdfg2d.util.ShortList;

/**
 * Type1 symbolic font source.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class SymbolicType1FontSource extends AbstractType1FontSource {
	private static final Logger LOG = Logger.getLogger(SymbolicType1FontSource.class.getName());

	private static final long serialVersionUID = 0;

	private final Source toUnicodeFile;

	transient private WeakReference<IntList> charToGid = null;

	transient private WeakReference<GlyphInfo[]> gidToGi = null;

	public SymbolicType1FontSource(AFMFontInfo fontInfo, Source toUnicodeFile) throws IOException {
		super(fontInfo);
		this.toUnicodeFile = toUnicodeFile;
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("new font: " + this.getFontName());
		}
	}

	protected synchronized GlyphInfo[] getGidToGi() {
		GlyphInfo[] gidToGi = null;
		if (this.gidToGi != null) {
			gidToGi = (GlyphInfo[]) this.gidToGi.get();
			if (gidToGi != null) {
				return gidToGi;
			}
		}
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("build gidToGi: " + this.getFontName());
		}
		gidToGi = new GlyphInfo[256];
		for (Iterator<AFMGlyphInfo> i = this.fontInfo.nameToGi.values().iterator(); i.hasNext();) {
			AFMGlyphInfo aci = (AFMGlyphInfo) i.next();
			if (aci.gid == -1) {
				continue;
			}

			IntList sgidToLigature;
			if (aci.nameToLigature != null) {
				sgidToLigature = new IntList(-1);
				for (Iterator<Entry<String, String>> j = aci.nameToLigature.entrySet().iterator(); j.hasNext();) {
					Entry<String, String> e = j.next();
					AFMGlyphInfo sci = (AFMGlyphInfo) this.fontInfo.nameToGi.get(e.getKey());
					AFMGlyphInfo lci = (AFMGlyphInfo) this.fontInfo.nameToGi.get(e.getValue());
					sgidToLigature.set(sci.gid, lci.gid);
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
					Entry<String, Short> e = (Entry<String, Short>) j.next();
					Short kerning = (Short) e.getValue();
					AFMGlyphInfo sci = (AFMGlyphInfo) this.fontInfo.nameToGi.get(e.getKey());
					sgidToKerning.set(sci.gid, kerning.shortValue());
				}
				if (sgidToKerning.isEmpty()) {
					sgidToKerning = null;
				} else {
					sgidToKerning.pack();
				}
			} else {
				sgidToKerning = null;
			}

			gidToGi[aci.gid] = new GlyphInfo(aci.advance, sgidToLigature == null ? null : sgidToLigature.toArray(),
					sgidToKerning == null ? null : sgidToKerning.toArray());
		}
		this.gidToGi = new WeakReference<GlyphInfo[]>(gidToGi);
		return gidToGi;
	}

	protected synchronized IntList getCidToGid() {
		IntList charToGid = null;
		synchronized (this) {
			if (this.charToGid != null) {
				charToGid = (IntList) this.charToGid.get();
				if (charToGid != null) {
					return charToGid;
				}
			}
		}
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("build charToGid: " + this.getFontName());
		}
		charToGid = new IntList(-1);
		try {
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(this.toUnicodeFile.getInputStream(), "ISO-8859-1"))) {
				for (String line = in.readLine(); line != null; line = in.readLine()) {
					if (line.startsWith("#")) {
						continue;
					}
					String[] pair = line.split("\t");
					if (pair.length < 2) {
						continue;
					}
					int unicode = Integer.parseInt(pair[0].trim(), 16);
					int gid = Integer.parseInt(pair[1].trim(), 16);
					charToGid.set(unicode, gid);
				}
				charToGid.pack();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		this.charToGid = new WeakReference<IntList>(charToGid);
		return charToGid;
	}

	int toGID(int c) {
		int gid = this.getCidToGid().get(c);
		return gid;
	}

	String getEncoding() {
		return null;
	}
}
