package net.zamasoft.pdfg2d.font.emoji;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontSourceProvider;

/**
 * 絵文字フォントSPIの登録テストです(2026-08-01、旧Class.forName継ぎ目の
 * ServiceLoader化)。META-INF/servicesの登録漏れ・タイポをここで検出する。
 */
public class EmojiFontSourceProviderTest {

	@Test
	public void testProviderIsDiscoverable() {
		boolean found = false;
		for (final FontSourceProvider provider : ServiceLoader.load(FontSourceProvider.class)) {
			if (provider instanceof EmojiFontSourceProvider) {
				found = true;
				assertEquals(2, provider.fontSources().size());
				assertTrue(provider.fontSources().contains(EmojiFontSource.INSTANCES_LTR));
				assertTrue(provider.fontSources().contains(EmojiFontSource.INSTANCES_TB));
			}
		}
		assertTrue(found, "EmojiFontSourceProviderがServiceLoaderで見つかる");
	}
}
