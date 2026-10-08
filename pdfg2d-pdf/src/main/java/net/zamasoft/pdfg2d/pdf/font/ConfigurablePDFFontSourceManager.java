package net.zamasoft.pdfg2d.pdf.font;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.ParserAdapter;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceValidity;
import net.zamasoft.zstream.resolver.SourceValidity.Validity;
import net.zamasoft.zstream.resolver.protocol.url.URLSource;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontSourceManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.pdf.font.util.MultimapUtils;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PDFFontSourceManagerImpl.java,v 1.1 2007-05-06 15:37:19
 *          miyabeExp $
 */
public class ConfigurablePDFFontSourceManager extends PDFFontSourceManager {
	private static final Logger LOG = Logger.getLogger(FontSourceManager.class.getName());

	private final Source config;

	private URI configURI = null;

	private SourceValidity configValidity = null;

	private static final class DefaultFontSourceManager {
		private static final FontSourceManager INSTANCE = create();

		private static FontSourceManager create() {
			final URL url = ConfigurablePDFFontSourceManager.class.getResource("builtin/fonts.xml");
			try {
				final Source source = new URLSource(url);
				return new ConfigurablePDFFontSourceManager(source, null);
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static FontSourceManager getDefaultFontSourceManager() {
		return DefaultFontSourceManager.INSTANCE;
	}

	/**
	 * Persistent index file for font-dir scanning (usually fonts.xml.db).
	 * Null means no index. Revived on 2026-08-01: the argument had existed for a long time
	 * but had long been ignored.
	 */
	private final File dbFile;

	public ConfigurablePDFFontSourceManager(Source config) {
		this(config, null);
	}

	public ConfigurablePDFFontSourceManager(Source config, File dbFile) {
		this.config = config;
		this.dbFile = dbFile;
		this.configURI = this.config.getURI();
		this.poll();
	}

	/**
	 * Minimum interval between configuration checks (milliseconds).
	 * Can be changed with {@code -Dnet.zamasoft.pdfg2d.font.pollIntervalMillis};
	 * {@code 0} restores the previous behavior of checking every time.
	 */
	private static final long POLL_INTERVAL_MS = Long
			.getLong("net.zamasoft.pdfg2d.font.pollIntervalMillis", 1000L);

	/**
	 * Time of the last configuration check ({@link System#nanoTime()} converted to milliseconds).
	 * The origin of {@code nanoTime} is arbitrary (negative values are allowed), so initializing to 0
	 * can leave {@code now - 0 < interval} true for a long time in negative-time environments,
	 * stopping hot reload. Initialize relative to the actual current time (2026-07-30).
	 */
	private long lastPollAt = System.nanoTime() / 1_000_000L - POLL_INTERVAL_MS;

	protected synchronized void poll() {
		// **Space out checks** (2026-07-29).
		//
		// {@link #lookup} is synchronized and calls this poll each time;
		// {@code config.exists()} performed a **filesystem stat**.
		// lookup is called for each character sequence
		// ({@code StyledTextUnitizer.characters} → {@code FontManagerImpl
		// .getFontListMetrics}), so <b>every character in every conversion made a syscall inside a global lock</b>,
		// effectively serializing concurrent conversions.
		//
		// Measurements (2026-07-29, 24-thread sweep): the CPU-time/elapsed-time ratio was
		// only <b>1.3</b>, and thread dumps showed 20 threads waiting on this monitor.
		// This explained why 24 threads and 12 threads ran at the same speed.
		//
		// Retain configuration change detection (hot reload), but checking once per second
		// is sufficient: operators can wait one second after editing the configuration.
		if (this.configValidity != null) {
			final long now = System.nanoTime() / 1_000_000L;
			if (now - this.lastPollAt < POLL_INTERVAL_MS) {
				return;
			}
			this.lastPollAt = now;
		}

		try {
			if (!this.config.exists()) {
				Exception e = new FileNotFoundException(this.config.getURI().toString());
				LOG.log(Level.SEVERE, this.config + " not found", e);
				throw new RuntimeException(e);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (this.configValidity != null && this.configValidity.getValid() == Validity.VALID
				&& this.configURI.equals(this.config.getURI())) {
			return;
		}

		LOG.fine("Building font database from " + this.config.getURI() + "...");
		SAXParserFactory parserFactory = SAXParserFactory.newInstance();
		XMLReader parser;
		try {
			parser = new ParserAdapter(parserFactory.newSAXParser().getParser());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		try {
			final FontIndex fontIndex = this.dbFile == null ? null : new FontIndex(this.dbFile);
			PDFFontSourceManagerConfigurationHandler handler = new PDFFontSourceManagerConfigurationHandler(
					this.config.getURI(), fontIndex);
			try (InputStream in = new BufferedInputStream(this.config.getInputStream())) {
				parser.setContentHandler(handler);
				parser.parse(new InputSource(in));
			}
			if (fontIndex != null) {
				fontIndex.save();
			}

			this.configURI = this.config.getURI();
			this.configValidity = this.config.getValidity();

			// Additional fonts from modules on the classpath (emoji fonts, etc.).
			// Replaced the old Class.forName + field reflection with a typed ServiceLoader SPI
			// (2026-08-01). Having no provider is normal (configuration without the module).
			for (final net.zamasoft.pdfg2d.font.FontSourceProvider provider : java.util.ServiceLoader
					.load(net.zamasoft.pdfg2d.font.FontSourceProvider.class)) {
				try {
					for (final FontSource source : provider.fontSources()) {
						FontLoader.add(source, handler.catalog.nameToFonts);
					}
				} catch (final Exception e) {
					LOG.log(Level.WARNING, "Failed to load fonts from " + provider.getClass().getName(), e);
				}
			}

			this.nameToFonts = MultimapUtils.unmodifiableMap(handler.catalog.nameToFonts);
			this.genericToFamily = Collections.unmodifiableMap(handler.catalog.genericToFamily);
			final Map<String, List<GenericFamily>> genericToLangFamily = new HashMap<>();
			for (final Map.Entry<String, List<GenericFamily>> entry : handler.catalog.genericToLangFamily.entrySet()) {
				genericToLangFamily.put(entry.getKey(), List.copyOf(entry.getValue()));
			}
			this.genericToLangFamily = Collections.unmodifiableMap(genericToLangFamily);
			this.allFonts = handler.catalog.allFonts;

			this.fontListCache = null;

			LOG.fine("Font database built successfully");
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to load " + this.config.getURI(), e);
			throw new RuntimeException(e);
		}
	}

	public synchronized FontSource[] lookup(FontStyle fontStyle) {
		this.poll();
		return super.lookup(fontStyle);
	}

	@Override
	public synchronized void lookupFamily(final FontStyle fontStyle, final net.zamasoft.pdfg2d.gc.font.FontFamily family,
			final List<FontSource> fontList) {
		this.poll();
		super.lookupFamily(fontStyle, family, fontList);
	}
}
