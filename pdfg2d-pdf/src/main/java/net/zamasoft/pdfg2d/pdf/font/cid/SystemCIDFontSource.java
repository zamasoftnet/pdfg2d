package net.zamasoft.pdfg2d.pdf.font.cid;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

import net.zamasoft.pdfg2d.font.AbstractFontSource;
import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.gc.font.Panose;

/**
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class SystemCIDFontSource extends AbstractFontSource implements CIDFontSource {
	private static final long serialVersionUID = 1L;

	private static final char[] X = new char[] { 'x' };

	private static final char[] H = new char[] { 'H' };

	private static final char[] S = new char[] { ' ' };

	protected final Font awtFont;

	protected String fontName;

	private BBox bbox = null;

	private short ascent, descent, xHeight, capHeight, spaceAdvance;

	protected Panose panose = null;

	public SystemCIDFontSource(Font awtFont) {
		this.awtFont = awtFont = awtFont.deriveFont(1000f);
		this.fontName = awtFont.getFontName();
		this.aliases = new String[] { awtFont.getFamily(), awtFont.getPSName() };
		this.setItalic(awtFont.isItalic());
		Float weight = (Float) awtFont.getAttributes().get(TextAttribute.WEIGHT);
		double rweight;
		if (weight != null) {
			rweight = Math.max(weight.doubleValue(), TextAttribute.WEIGHT_EXTRA_LIGHT.doubleValue());
		} else {
			rweight = TextAttribute.WEIGHT_MEDIUM.doubleValue();
		}
		rweight = Math.min(rweight, TextAttribute.WEIGHT_ULTRABOLD.doubleValue());
		rweight = (rweight - TextAttribute.WEIGHT_EXTRA_LIGHT.doubleValue())
				/ (TextAttribute.WEIGHT_ULTRABOLD.doubleValue() - TextAttribute.WEIGHT_EXTRA_LIGHT.doubleValue());
		if (rweight <= 0.1) {
			this.setWeight(Weight.W_100);
		} else if (rweight <= 0.2) {
			this.setWeight(Weight.W_200);
		} else if (rweight <= 0.3) {
			this.setWeight(Weight.W_300);
		} else if (rweight <= 0.4) {
			this.setWeight(Weight.W_400);
		} else if (rweight <= 0.5) {
			this.setWeight(Weight.W_500);
		} else if (rweight <= 0.6) {
			this.setWeight(Weight.W_600);
		} else if (rweight <= 0.7) {
			this.setWeight(Weight.W_700);
		} else if (rweight <= 0.8) {
			this.setWeight(Weight.W_800);
		} else {
			this.setWeight(Weight.W_900);
		}
	}

	public Direction getDirection() {
		return Direction.LTR;
	}

	public Font getAwtFont() {
		return this.awtFont;
	}

	public void setFontName(String fontName) {
		this.fontName = fontName;
	}

	public Panose getPanose() {
		return this.panose;
	}

	public void setPanose(Panose panose) {
		this.panose = panose;
	}

	public FontRenderContext getFontRenderContext() {
		return SystemFontRenderContext.SHARED_INSTANCE;
	}

	private void loadMetrics() {
		if (this.bbox != null) {
			return;
		}
		if (this.awtFont.canDisplay('H') || this.awtFont.canDisplay('x')) {
			LineMetrics lm = this.awtFont.getLineMetrics("Hx", this.getFontRenderContext());
			this.ascent = (short) lm.getAscent();
			this.descent = (short) lm.getDescent();
		} else {
			this.ascent = DEFAULT_ASCENT;
			this.descent = DEFAULT_DESCENT;
		}

		if (this.awtFont.canDisplay('x')) {
			GlyphVector gv = this.awtFont.createGlyphVector(this.getFontRenderContext(), X);
			this.xHeight = (short) gv.getVisualBounds().getHeight();
		} else {
			this.xHeight = DEFAULT_X_HEIGHT;
		}
		if (this.awtFont.canDisplay('H')) {
			GlyphVector gv = this.awtFont.createGlyphVector(this.getFontRenderContext(), H);
			this.capHeight = (short) gv.getVisualBounds().getHeight();
		} else {
			this.capHeight = DEFAULT_CAP_HEIGHT;
		}
		GlyphVector gv = this.awtFont.createGlyphVector(this.getFontRenderContext(), S);
		this.spaceAdvance = (short) gv.getGlyphMetrics(0).getAdvance();

		Rectangle2D bounds = this.awtFont.getMaxCharBounds(this.getFontRenderContext());
		this.bbox = new BBox((short) bounds.getX(), (short) -bounds.getMaxY(), (short) bounds.getMaxX(),
				(short) -bounds.getY());
	}

	public synchronized BBox getBBox() {
		this.loadMetrics();
		return this.bbox;
	}

	public String getFontName() {
		return this.fontName;
	}

	public synchronized short getXHeight() {
		this.loadMetrics();
		return this.xHeight;
	}

	public synchronized short getSpaceAdvance() {
		this.loadMetrics();
		return this.spaceAdvance;
	}

	public synchronized short getCapHeight() {
		this.loadMetrics();
		return this.capHeight;
	}

	public synchronized short getAscent() {
		this.loadMetrics();
		return this.ascent;
	}

	public synchronized short getDescent() {
		this.loadMetrics();
		return this.descent;
	}

	public short getStemH() {
		return 0;
	}

	public short getStemV() {
		return 0;
	}

	public boolean canDisplay(int c) {
		// The code point, not (char) c: a character outside the BMP took the coverage of the BMP character of its low
		// 16 bits, U+20041 that of 'A' (codex, 2026-11-26)
		return this.awtFont.canDisplay(c);
	}
}

class SystemFontRenderContext extends FontRenderContext implements Serializable {
	private static final long serialVersionUID = 0;

	public static final SystemFontRenderContext SHARED_INSTANCE = new SystemFontRenderContext(new AffineTransform(),
			true, true);

	private SystemFontRenderContext(AffineTransform tx, boolean isAntiAliased, boolean usesFractionalMetrics) {
		super(tx, isAntiAliased, usesFractionalMetrics);
	}
}
