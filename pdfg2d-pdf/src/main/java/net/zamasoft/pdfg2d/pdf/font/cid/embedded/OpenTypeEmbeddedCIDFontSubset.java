package net.zamasoft.pdfg2d.pdf.font.cid.embedded;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.XRef;
import net.zamasoft.pdfg2d.pdf.font.cid.CIDUtils;
import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.pdfg2d.util.ShortList;

/**
 * One physical OpenType subset shared by direction-specific Type0 fonts.
 * CIDs identify an outline (source GID plus transformation variant), while
 * Unicode mappings deliberately remain on each Type0 wrapper.
 */
public final class OpenTypeEmbeddedCIDFontSubset implements Serializable {
	private static final long serialVersionUID = 1L;

	private record GlyphKey(int sourceGid, int shapeFlags, int semanticVariant) implements Serializable {
		private static final long serialVersionUID = 1L;
	}

	private final Map<GlyphKey, Integer> glyphs = new HashMap<>();
	private final IntList cidToSourceGid = new IntList(-1);
	private final IntList cidToShapeFlags = new IntList();
	private final IntList cidToSemanticVariant = new IntList();
	private final ShortList widths = new ShortList(Short.MIN_VALUE);
	private final ShortList heights = new ShortList(Short.MIN_VALUE);
	private int glyphCount = 1;
	private boolean initialized;

	private transient ObjectRef descendantRef;
	private transient String subsetName;
	private transient boolean written;

	OpenTypeEmbeddedCIDFontSubset() {
		// Created by OpenTypeEmbeddedCIDFontSource for one PDF document.
	}

	void initialize(final short width, final short height, final boolean verticalMetrics) {
		if (!this.initialized) {
			this.cidToSourceGid.set(0, 0);
			this.widths.set(0, width);
			this.heights.set(0, height);
			this.initialized = true;
		} else if (verticalMetrics) {
			this.heights.set(0, height);
		}
	}

	int register(final int sourceGid, final int shapeFlags, final int semanticVariant, final short width,
			final short height, final boolean verticalMetrics) {
		final var key = new GlyphKey(sourceGid, shapeFlags, semanticVariant);
		final var existing = this.glyphs.get(key);
		if (existing != null) {
			if (verticalMetrics) {
				this.heights.set(existing, height);
			}
			return existing;
		}
		final int cid = this.glyphCount++;
		this.glyphs.put(key, cid);
		this.cidToSourceGid.set(cid, sourceGid);
		this.cidToShapeFlags.set(cid, shapeFlags);
		this.cidToSemanticVariant.set(cid, semanticVariant);
		this.widths.set(cid, width);
		this.heights.set(cid, height);
		return cid;
	}

	int sourceGid(final int cid) {
		return this.cidToSourceGid.get(cid);
	}

	int shapeFlags(final int cid) {
		return this.cidToShapeFlags.get(cid);
	}

	short width(final int cid) {
		return this.widths.get(cid);
	}

	short height(final int cid) {
		return this.heights.get(cid);
	}

	int glyphCount() {
		return this.glyphCount;
	}

	short[] widths() {
		return this.widths.toArray();
	}

	short[] heights() {
		return this.heights.toArray();
	}

	int[] signature() {
		final int[] signature = new int[this.glyphCount * 3];
		for (int cid = 0, p = 0; cid < this.glyphCount; ++cid) {
			signature[p++] = this.cidToSourceGid.get(cid);
			signature[p++] = this.cidToShapeFlags.get(cid);
			signature[p++] = this.cidToSemanticVariant.get(cid);
		}
		return signature;
	}

	void prepare(final XRef xref, final String psName) {
		if (this.descendantRef == null) {
			this.descendantRef = xref.nextObjectRef();
			this.subsetName = CIDUtils.createEmbeddedSubsetName(this.widths(), this.heights(), this.signature(), psName);
		}
	}

	ObjectRef descendantRef() {
		return this.descendantRef;
	}

	String subsetName() {
		return this.subsetName;
	}

	boolean isWritten() {
		return this.written;
	}

	void markWritten() {
		this.written = true;
	}
}
