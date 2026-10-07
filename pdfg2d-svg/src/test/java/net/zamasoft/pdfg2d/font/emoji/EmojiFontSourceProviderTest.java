package net.zamasoft.pdfg2d.font.emoji;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontSourceProvider;

/**
 * Tests emoji font SPI registration (2026-08-01: replaced the former Class.forName integration point
 * with ServiceLoader). Detects missing registrations and typos in META-INF/services.
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
