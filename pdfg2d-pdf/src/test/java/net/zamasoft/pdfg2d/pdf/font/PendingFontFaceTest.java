package net.zamasoft.pdfg2d.pdf.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

/**
 * A face with a {@link FontFace#loader} is read when a font style first names its family, and never if no style does
 * (2026-10-08). A site's style sheet that imports a CJK web font in 61 unicode-range files and never uses it doubled the
 * time of the page when every face was read as the style sheet was parsed.
 */
public class PendingFontFaceTest {
	private static final File FONT = new File("src/test/resources/mulish/Mulish-VariableFont_wght.ttf");

	private static final FontPolicyList EMBEDDED = new FontPolicyList(
			new FontPolicyList.FontPolicy[] { FontPolicyList.FontPolicy.EMBEDDED });

	private static FontStyle style(final String... families) {
		final FontFamily[] list = new FontFamily[families.length];
		for (int i = 0; i < families.length; ++i) {
			list[i] = new FontFamily(families[i]);
		}
		return new FontStyleImpl(new FontFamilyList(list), 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, EMBEDDED, FontFeatureSet.EMPTY, true, true, FontStyle.TextOrientation.MIXED,
				net.zamasoft.pdfg2d.font.FontSource.NORMAL_WIDTH_CLASS, null);
	}

	/** A face of {@code family} whose loader counts its calls into {@code calls}. */
	private static FontFace face(final String family, final List<String> calls, final boolean found) {
		final FontFace face = new FontFace();
		face.fontFamily = FontFamilyList.create(family);
		face.fontWeight = FontStyle.Weight.W_400;
		face.loader = (pending, reader) -> {
			calls.add(family);
			if (!found) {
				return false;
			}
			pending.src = new FileSource(FONT);
			try {
				reader.read(pending);
				return true;
			} catch (final java.io.IOException e) {
				return false;
			}
		};
		return face;
	}

	@Test
	public void aFaceIsReadWhenAStyleFirstNamesItsFamily() throws Exception {
		final List<String> calls = new ArrayList<>();
		try (final PDFFontSourceManager manager = new PDFFontSourceManager(true, true)) {
			manager.addFontFace(face("Used", calls, true));
			manager.addFontFace(face("Unused", calls, true));
			assertEquals(List.of(), calls, "read when declared");

			assertEquals(0, manager.lookup(style("Other")).length);
			assertEquals(List.of(), calls, "read for a style that does not name the family");

			final FontSource[] used = manager.lookup(style("Used"));
			assertTrue(used.length > 0, "the used family has fonts");
			manager.lookup(style("Used", "Other"));
			assertEquals(List.of("Used"), calls, "only the used family, once");
		}
	}

	@Test
	public void aFaceWithoutAReadableSourceIsTriedOnce() throws Exception {
		final List<String> calls = new ArrayList<>();
		try (final PDFFontSourceManager manager = new PDFFontSourceManager(true, true)) {
			manager.addFontFace(face("Missing", calls, false));
			assertEquals(0, manager.lookup(style("Missing")).length);
			assertEquals(0, manager.lookup(style("Missing", "Other")).length);
			assertEquals(List.of("Missing"), calls);
		}
	}
}
