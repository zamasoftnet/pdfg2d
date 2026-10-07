package net.zamasoft.pdfg2d.font.emoji;

import java.util.List;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontSourceProvider;

/**
 * Supplies emoji fonts to the font database (2026-08-01: replaced the former Class.forName integration point
 * with ServiceLoader).
 *
 * @author MIYABE Tatsuhiko
 */
public final class EmojiFontSourceProvider implements FontSourceProvider {
	@Override
	public List<? extends FontSource> fontSources() {
		return List.of(EmojiFontSource.INSTANCES_LTR, EmojiFontSource.INSTANCES_TB);
	}
}
