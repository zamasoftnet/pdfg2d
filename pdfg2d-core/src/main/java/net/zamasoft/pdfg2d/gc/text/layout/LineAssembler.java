package net.zamasoft.pdfg2d.gc.text.layout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.Element;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;

/**
 * 行の組み立て(グリフ蓄積・切断機会でのrun分割・justify・行メトリクス
 * 計測)です(2026-08-01、90点計画増分13——
 * {@link PageLayoutGlyphHandler#endLine}が行分割・justify・計測・段あふれ・
 * 描画バッファ登録を同時に行っていたのを分離した)。
 *
 * <p>
 * このクラスは「切断機会({@link #markBreakOpportunity()})までの内容で
 * 1行を確定し、開いているテキスト単位を次行へ持ち越す」ところまでを担い、
 * 段配置・ページ送り・描画は{@link PageLayoutGlyphHandler}に残る。
 * XMLやGCなしで行分割を単体テストできる。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class LineAssembler {

	/**
	 * 確定した1行です。
	 *
	 * @param elements 行の内容(描画順)
	 * @param ascent   行の最大アセント(fontSize指定時は補正済み)
	 * @param descent  行の最大ディセント(同)
	 */
	record LineBox(Element[] elements, double ascent, double descent) {
	}

	private TextImpl text = null;

	private final List<Element> textBuffer = new ArrayList<>();

	private double letterSpacing = 0;

	private double advance = 0;

	/** 現在のテキスト単位(切断機会以降)の要素数です。 */
	private int textUnitElementCount = 0;

	/** 現在のテキスト単位のうち、開いているrunに属するグリフ数です。 */
	private int textUnitGlyphCount = 0;

	void setLetterSpacing(final double letterSpacing) {
		this.letterSpacing = letterSpacing;
	}

	double getLetterSpacing() {
		return this.letterSpacing;
	}

	/** 蓄積中の行の送り量(タブ位置計算と切断判定に使う)。 */
	double advance() {
		return this.advance;
	}

	void startTextRun(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.closeTextRun();
		this.text = new TextImpl(charOffset, fontStyle, fontMetrics);
		this.text.setLetterSpacing(this.letterSpacing);
	}

	void glyph(final char[] ch, final int coff, final byte clen, final int gid) {
		this.advance += this.text.appendGlyph(ch, coff, clen, gid);
		this.advance += this.letterSpacing;
		++this.textUnitGlyphCount;
	}

	void endTextRun() {
		assert this.text.getGlyphCount() > 0;
	}

	/** 開いているrunを確定して行バッファへ移します。 */
	void closeTextRun() {
		if (this.text != null) {
			this.text.pack();
			this.textBuffer.add(this.text);
			++this.textUnitElementCount;
			this.textUnitGlyphCount = 0;
			this.text = null;
		}
	}

	/** 制御要素(タブ・改行マーカー等)を行バッファへ加えます。 */
	void addControl(final Control control) {
		this.closeTextRun();
		this.textBuffer.add(control);
		++this.textUnitElementCount;
		this.advance += control.getAdvance();
	}

	/**
	 * 切断機会を刻みます。以降の内容は「次行へ持ち越しうる単位」として
	 * 数え直される。
	 */
	void markBreakOpportunity() {
		this.textUnitElementCount = 0;
		this.textUnitGlyphCount = 0;
	}

	/**
	 * 行を確定します。
	 *
	 * @param last       強制改行(改行文字・末尾)なら{@code true}——開いている
	 *                   単位を含む全内容で行を閉じる。{@code false}ならあふれ
	 *                   切断——最後の切断機会までで行を閉じ、現在の単位
	 *                   (開いているrunの分割を含む)は次行へ持ち越す
	 * @param justify    行末を揃える(あふれ切断の行のみ適用)か
	 * @param maxAdvance 行の最大送り量(justifyの分配に使う)
	 * @param fontSize   固定行高のためのフォントサイズ(0なら実測ディセント)
	 * @return 確定した行
	 */
	LineBox breakLine(final boolean last, final boolean justify, final double maxAdvance, final double fontSize) {
		final Element[] elements;
		double advance;
		if (last) {
			int elementCount = this.textBuffer.size();
			if (this.text != null) {
				++elementCount;
			}
			elements = new Element[elementCount];
			for (int i = 0; i < this.textBuffer.size(); ++i) {
				elements[i] = this.textBuffer.get(i);
			}
			if (this.text != null) {
				this.text.pack();
				elements[elementCount - 1] = this.text;
				this.text = null;
			}
			advance = this.advance;
			this.textBuffer.clear();
		} else {
			advance = 0;
			int count = this.textBuffer.size() - this.textUnitElementCount;
			int elementCount = count;
			if (this.text != null) {
				if (this.text.getGlyphCount() <= this.textUnitGlyphCount) {
					if (this.textUnitElementCount > 0) {
						++elementCount;
						++count;
					}
				} else {
					++elementCount;
				}
			}
			elements = new Element[elementCount];
			final Iterator<Element> i = this.textBuffer.iterator();
			for (int j = 0; j < count; ++j) {
				final Element e = i.next();
				elements[j] = e;
				advance += e.getAdvance();
				i.remove();
			}
			if (this.text != null && this.text.getGlyphCount() > this.textUnitGlyphCount) {
				final int pos = this.text.getGlyphCount() - this.textUnitGlyphCount;
				final Element e = this.text.split(pos);
				elements[elementCount - 1] = e;
				advance += e.getAdvance();
			}

			// Justify by distributing the leftover width as extra letter
			// spacing across all glyphs of the line. Known limitation: there
			// is no hyphenation, so an overlong unbreakable word wraps early
			// and the previous line may be stretched noticeably.
			if (justify) {
				int glyphCount = 0;
				for (final Element e : elements) {
					if (e instanceof Text text) {
						glyphCount += text.getGlyphCount();
					}
				}
				if (glyphCount >= 2) {
					final double letterSpacing = (maxAdvance - advance) / (double) (glyphCount - 1);
					for (final Element e : elements) {
						if (e instanceof TextImpl t) {
							t.setLetterSpacing(t.getLetterSpacing() + letterSpacing);
						}
					}
				}
			}
		}
		this.advance -= advance;

		// Calculate ascent/descent
		double maxAscent = 0;
		double maxDescent = 0;
		for (final Element e : elements) {
			if (e instanceof Text text) {
				maxAscent = Math.max(maxAscent, text.getAscent());
				maxDescent = Math.max(maxDescent, text.getDescent());
			} else if (e instanceof Control control) {
				maxAscent = Math.max(maxAscent, control.getAscent());
				maxDescent = Math.max(maxDescent, control.getDescent());
			}
		}
		if (fontSize != 0) {
			maxDescent = fontSize - maxAscent;
		}
		return new LineBox(elements, maxAscent, maxDescent);
	}
}
