package net.zamasoft.pdfg2d.font.emoji;

import java.util.List;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontSourceProvider;

/**
 * 絵文字フォントをフォントデータベースへ供給します(2026-08-01、
 * 旧Class.forName継ぎ目のServiceLoader化)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class EmojiFontSourceProvider implements FontSourceProvider {
	@Override
	public List<? extends FontSource> fontSources() {
		return List.of(EmojiFontSource.INSTANCES_LTR, EmojiFontSource.INSTANCES_TB);
	}
}
