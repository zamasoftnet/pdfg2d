package net.zamasoft.pdfg2d.font;

import java.util.List;

/**
 * SPI for modules on the classpath to provide additional fonts
 * (2026-08-01, 90-point plan increment 7).
 *
 * <p>
 * The old implementation loaded emoji fonts using {@code Class.forName("...EmojiFontSource")} and
 * reflection on public fields. This bridged the module boundary (pdfg2d-pdf cannot depend on
 * pdfg2d-svg), but implicitly depended on class name strings and field names.
 * A typed contract through {@link java.util.ServiceLoader} replaces it.
 * Register implementations in
 * {@code META-INF/services/net.zamasoft.pdfg2d.font.FontSourceProvider}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public interface FontSourceProvider {
	/**
	 * Returns the fonts supplied by this provider. Called each time the font database
	 * is built, so the provider should return shared instances for sources
	 * that are expensive to create.
	 *
	 * @return the supplied fonts (may be empty)
	 */
	List<? extends FontSource> fontSources();
}
