package net.zamasoft.pdfg2d.font;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.text.Text;

/** Tests the feature-aware default method used by non-OpenType fonts. */
public class FontLigatureFeatureTest {
	private static final Font FONT = new Font() {
		private static final long serialVersionUID = 1L;

		@Override
		public FontSource getFontSource() {
			return null;
		}

		@Override
		public int toGID(final int c) {
			return c;
		}

		@Override
		public short getAdvance(final int gid) {
			return 0;
		}

		@Override
		public short getWidth(final int gid) {
			return 0;
		}

		@Override
		public short getKerning(final int sgid, final int gid) {
			return 0;
		}

		@Override
		public int getLigature(final int gid, final int cid) {
			return gid + cid;
		}

		@Override
		public void drawTo(final GC gc, final Text text) {
			// no-op
		}
	};

	@Test
	public void defaultFeatureOverloadDelegatesUnlessLigaIsDisabled() {
		assertEquals(11, FONT.getLigature(4, 7, FontFeatureSet.EMPTY));
		final var dlig = FontFeatureSet.of(new int[] { FontFeatureSet.packTag("dlig") }, new int[] { 1 });
		assertEquals(11, FONT.getLigature(4, 7, dlig));

		final var ligaOff = FontFeatureSet.of(new int[] { FontFeatureSet.packTag("liga") }, new int[] { 0 });
		assertEquals(-1, FONT.getLigature(4, 7, ligaOff));
	}
}
