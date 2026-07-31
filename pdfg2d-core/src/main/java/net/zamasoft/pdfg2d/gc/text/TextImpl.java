package net.zamasoft.pdfg2d.gc.text;

import java.io.Serializable;

import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * Represents renderable text.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class TextImpl extends AbstractText implements Serializable {
	private static final long serialVersionUID = 0L;

	private static final int INIT_LEN = 50;

	/** The font style of the text. */
	public final FontStyle fontStyle;

	/** The font metrics of the text. */
	public final FontMetrics fontMetrics;

	private int charOffset;

	/** The character buffer. */
	public char[] chars;

	/** The number of characters. */
	public int charCount;

	/** The glyph buffer. */
	public int[] glyphIds;

	/** The number of characters corresponding to each glyph. */
	public byte[] clusterLengths;

	/** The number of glyphs. */
	public int glyphCount;

	/**
	 * The total width of the text.
	 */
	public double advance = 0;

	/**
	 * The extra spacing added after each glyph.
	 */
	public double letterSpacing = 0;

	/**
	 * The extra spacing added for each glyph.
	 */
	public double[] xadvances = null;

	/**
	 * Constructs a new TextImpl.
	 *
	 * @param charOffset  the character offset of the first character within the
	 *                    source string
	 * @param fontStyle   the font style for this text run
	 * @param fontMetrics the font metrics for this text run
	 */
	public TextImpl(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		assert fontStyle != null : "FontStyle required.";
		assert fontMetrics != null : "FontMetrics required.";
		this.fontStyle = fontStyle;
		this.fontMetrics = fontMetrics;
		this.charOffset = charOffset;
		final int len = Math.max(this.glyphCount, INIT_LEN);
		this.glyphIds = new int[len];
		this.clusterLengths = new byte[len];
		this.chars = new char[len];
	}

	@Override
	public int getCharOffset() {
		return this.charOffset;
	}

	@Override
	public double getAdvance() {
		double advance = this.advance + this.letterSpacing * this.glyphCount;
		if (this.xadvances != null) {
			for (int i = 0; i < this.xadvances.length; ++i) {
				advance += this.xadvances[i];
			}
		}
		return advance;
	}

	@Override
	public double[] getXAdvances(final boolean make) {
		if (make) {
			this.xadvances = new double[this.glyphCount];
		}
		return this.xadvances;
	}

	/**
	 * Returns the per-glyph adjustment array, allocating (or growing) it as
	 * needed while <b>preserving</b> any adjustments already present - unlike
	 * {@code getXAdvances(true)} which resets the array. Use this from
	 * consumers that add their own spacing on top of earlier adjustments
	 * (e.g. justification over Japanese punctuation trimming).
	 *
	 * @return the adjustment array, aligned to {@link #getGlyphCount()}
	 */
	public double[] ensureXAdvances() {
		if (this.xadvances == null) {
			this.xadvances = new double[this.glyphCount];
		} else if (this.xadvances.length < this.glyphCount) {
			final double[] grown = new double[this.glyphCount];
			System.arraycopy(this.xadvances, 0, grown, 0, this.xadvances.length);
			this.xadvances = grown;
		}
		return this.xadvances;
	}

	/**
	 * Adds a per-glyph advance adjustment, preserving any adjustments already
	 * present (unlike {@code getXAdvances(true)} which resets the array).
	 * Used by layout-level spacing (Japanese punctuation trimming etc.) so
	 * that measurement and drawing observe the same adjusted advances.
	 *
	 * @param glyphIndex the glyph index (0-based, &lt; {@link #getGlyphCount()})
	 * @param delta      the advance delta to add (negative to tighten)
	 */
	public void addXAdvance(final int glyphIndex, final double delta) {
		assert glyphIndex >= 0 && glyphIndex < this.glyphCount : "glyphIndex out of range: " + glyphIndex;
		if (this.xadvances == null) {
			this.xadvances = new double[this.glyphCount];
		} else if (this.xadvances.length < this.glyphCount) {
			// Allocated before further glyphs were appended - grow, keeping
			// existing adjustments
			final double[] grown = new double[this.glyphCount];
			System.arraycopy(this.xadvances, 0, grown, 0, this.xadvances.length);
			this.xadvances = grown;
		}
		this.xadvances[glyphIndex] += delta;
	}

	@Override
	public double getAscent() {
		return this.fontMetrics.getAscent();
	}

	@Override
	public double getDescent() {
		return this.fontMetrics.getDescent();
	}

	@Override
	public FontStyle getFontStyle() {
		return this.fontStyle;
	}

	@Override
	public FontMetrics getFontMetrics() {
		return this.fontMetrics;
	}

	@Override
	public char[] getChars() {
		return this.chars;
	}

	@Override
	public int getCharCount() {
		return this.charCount;
	}

	@Override
	public int[] getGlyphIds() {
		return this.glyphIds;
	}

	@Override
	public byte[] getClusterLengths() {
		return this.clusterLengths;
	}

	@Override
	public int getGlyphCount() {
		return this.glyphCount;
	}

	@Override
	public double getLetterSpacing() {
		return this.letterSpacing;
	}

	/**
	 * Sets the letter spacing added after each glyph.
	 *
	 * @param letterSpacing the letter spacing to set
	 */
	public void setLetterSpacing(final double letterSpacing) {
		this.letterSpacing = letterSpacing;
	}

	/**
	 * Splits this text run at the specified glyph offset and returns the first
	 * part. This text instance is modified in place to become the second (tail)
	 * part.
	 *
	 * @param goff the glyph index at which to split (must be &gt; 0 and
	 *             &lt; {@link #getGlyphCount()})
	 * @return the first (head) part of the split
	 */
	public Text split(final int goff) {
		assert goff > 0 : "Cannot split at goff <= 0: goff=" + goff;
		assert goff < this.glyphCount : "Cannot split at goff >= glyphCount (" + this.glyphCount + "): goff=" + goff;

		// The previous text is 'text', and 'this' becomes the subsequent text
		final TextImpl text = new TextImpl(this.charOffset, this.fontStyle, this.fontMetrics);
		text.glyphCount = goff;
		text.glyphIds = new int[text.glyphCount];
		text.clusterLengths = new byte[text.glyphCount];
		for (int i = 0; i < text.glyphCount; ++i) {
			final int gid = this.glyphIds[i];
			text.glyphIds[i] = gid;
			text.advance += this.fontMetrics.getAdvance(gid);
			if (i > 0) {
				text.advance -= this.fontMetrics.getKerning(text.glyphIds[i - 1], gid);
			}
			final int clen = (text.clusterLengths[i] = this.clusterLengths[i]);
			text.charCount += clen;
		}
		text.letterSpacing = this.letterSpacing;

		text.chars = new char[text.charCount];
		System.arraycopy(this.chars, 0, text.chars, 0, text.charCount);

		this.glyphCount -= text.glyphCount;
		System.arraycopy(this.glyphIds, text.glyphCount, this.glyphIds, 0, this.glyphCount);
		System.arraycopy(this.clusterLengths, text.glyphCount, this.clusterLengths, 0, this.glyphCount);
		if (this.xadvances != null) {
			// Carry per-glyph adjustments across the split (head gets its
			// prefix, the tail keeps its suffix). Without this, adjustments
			// silently vanish on line splitting. The array may be shorter
			// than glyphCount if it was allocated before further appends -
			// copy defensively.
			final double[] prev = this.xadvances;
			text.xadvances = new double[text.glyphCount];
			System.arraycopy(prev, 0, text.xadvances, 0, Math.min(prev.length, text.glyphCount));
			final double[] tail = new double[this.glyphCount];
			final int available = Math.max(0, prev.length - text.glyphCount);
			System.arraycopy(prev, text.glyphCount, tail, 0, Math.min(available, this.glyphCount));
			this.xadvances = tail;
		}

		this.advance -= text.advance;
		// Restore kerning at the split point
		this.advance += this.fontMetrics.getKerning(text.glyphIds[text.glyphCount - 1], this.glyphIds[0]);

		this.charCount -= text.charCount;
		this.charOffset += text.charCount;
		System.arraycopy(this.chars, text.charCount, this.chars, 0, this.charCount);

		return text;
	}

	/**
	 * Returns the advance width contributed by the specified glyph when appended
	 * after the current last glyph, taking kerning into account.
	 *
	 * @param gid the glyph ID to measure
	 * @return the advance width of the glyph
	 */
	public double glyphAdvance(final int gid) {
		double advance = this.fontMetrics.getAdvance(gid);
		if (this.glyphCount > 0) {
			final double kerning = this.fontMetrics.getKerning(this.glyphIds[this.glyphCount - 1], gid);
			advance -= kerning;
		}
		return advance;
	}

	/**
	 * Append the glyph to tail of Text object.
	 * 
	 * @param ch   the characters
	 * @param coff the character offset
	 * @param clen the character length
	 * @param gid  the glyph ID
	 * @return the advance width
	 */
	public double appendGlyph(final char[] ch, final int coff, final byte clen, final int gid) {
		final double advance = this.glyphAdvance(gid);
		final int newGlyphCount = this.glyphCount + 1;
		if (newGlyphCount > this.glyphIds.length) {
			final int newLen = newGlyphCount * 3 / 2;
			{
				final int[] a = this.glyphIds;
				this.glyphIds = new int[newLen];
				System.arraycopy(a, 0, this.glyphIds, 0, a.length);
			}
			{
				final byte[] a = this.clusterLengths;
				this.clusterLengths = new byte[newLen];
				System.arraycopy(a, 0, this.clusterLengths, 0, a.length);
			}
		}
		if (this.xadvances != null && this.xadvances.length < newGlyphCount) {
			// Keep per-glyph adjustments aligned with glyph growth - font
			// drawTo implementations iterate xadvances by glyphCount
			final double[] a = this.xadvances;
			this.xadvances = new double[Math.max(newGlyphCount, this.glyphIds.length)];
			System.arraycopy(a, 0, this.xadvances, 0, a.length);
		}

		final int newCharCount = this.charCount + clen;
		if (newCharCount > this.chars.length) {
			final char[] a = this.chars;
			this.chars = new char[Math.max(INIT_LEN, newCharCount * 3 / 2)];
			System.arraycopy(a, 0, this.chars, 0, a.length);
		}
		System.arraycopy(ch, coff, this.chars, this.charCount, clen);
		this.charCount = newCharCount;

		this.glyphIds[this.glyphCount] = gid;
		this.clusterLengths[this.glyphCount] = clen;
		this.glyphCount = newGlyphCount;
		this.advance += advance;
		return advance;
	}

	/**
	 * Returns a copy of this run with its glyphs in reverse order, for placing
	 * a right-to-left run visually. Clusters are kept with their glyphs so the
	 * characters remain associated for text extraction.
	 *
	 * @return a glyph-reversed copy
	 */
	public TextImpl reverse() {
		final TextImpl r = new TextImpl(this.charOffset, this.fontStyle, this.fontMetrics);
		r.letterSpacing = this.letterSpacing;
		// Character start offset of each glyph's cluster (prefix sums).
		final int[] charStart = new int[this.glyphCount];
		int pos = 0;
		for (int g = 0; g < this.glyphCount; ++g) {
			charStart[g] = pos;
			pos += this.clusterLengths[g];
		}
		for (int g = this.glyphCount - 1; g >= 0; --g) {
			r.appendGlyph(this.chars, charStart[g], this.clusterLengths[g], this.glyphIds[g]);
		}
		if (r.glyphCount > 0) {
			r.pack();
		}
		return r;
	}

	/**
	 * Trims all internal arrays to their exact sizes, reducing memory usage.
	 * Should be called after all glyphs have been appended.
	 */
	public void pack() {
		assert this.glyphCount > 0 : "Empty text";
		if (this.glyphIds.length != this.glyphCount) {
			final int[] glyphIds = new int[this.glyphCount];
			System.arraycopy(this.glyphIds, 0, glyphIds, 0, this.glyphCount);
			this.glyphIds = glyphIds;

			final byte[] clusterLengths = new byte[this.glyphCount];
			System.arraycopy(this.clusterLengths, 0, clusterLengths, 0, this.glyphCount);
			this.clusterLengths = clusterLengths;
		}
		if (this.charCount != this.chars.length) {
			final char[] chars = new char[this.charCount];
			System.arraycopy(this.chars, 0, chars, 0, this.charCount);
			this.chars = chars;
		}
	}
}
