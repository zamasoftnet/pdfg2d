package net.zamasoft.pdfg2d.pdf.font;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
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

	public ConfigurablePDFFontSourceManager(Source config) {
		this(config, null);
	}

	public ConfigurablePDFFontSourceManager(Source config, File dbFile) {
		this.config = config;
		this.configURI = this.config.getURI();
		this.poll();
	}

	protected synchronized void poll() {
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
			PDFFontSourceManagerConfigurationHandler handler = new PDFFontSourceManagerConfigurationHandler(
					this.config.getURI());
			try (InputStream in = new BufferedInputStream(this.config.getInputStream())) {
				parser.setContentHandler(handler);
				parser.parse(new InputSource(in));
			}

			this.configURI = this.config.getURI();
			this.configValidity = this.config.getValidity();

			try {
				Class<?> clazz = Class.forName("net.zamasoft.pdfg2d.font.emoji.EmojiFontSource");
				clazz.getField("INSTANCES_LTR").get(null);
				FontLoader.add((FontSource) clazz.getField("INSTANCES_LTR").get(null), handler.nameToFonts);
				FontLoader.add((FontSource) clazz.getField("INSTANCES_TB").get(null), handler.nameToFonts);
			} catch (Exception e) {
				e.printStackTrace();
				// ignore
			}

			this.nameToFonts = MultimapUtils.unmodifiableMap(handler.nameToFonts);
			this.genericToFamily = Collections.unmodifiableMap(handler.genericToFamily);
			this.allFonts = handler.allFonts;

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
}
