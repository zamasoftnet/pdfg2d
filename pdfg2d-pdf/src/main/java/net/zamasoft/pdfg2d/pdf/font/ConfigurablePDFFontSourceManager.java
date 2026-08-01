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

	/**
	 * font-dirスキャンの永続索引ファイル(通常はfonts.xml.db)。nullなら
	 * 索引なし。2026-08-01に蘇生——引数は昔からあったが長らく無視されて
	 * いた。
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
	 * 設定の再検査をこの間隔より頻繁には行いません(ミリ秒)。
	 * {@code -Dnet.zamasoft.pdfg2d.font.pollIntervalMillis} で変更でき、
	 * {@code 0} を指定すると毎回検査する従来の挙動に戻ります。
	 */
	private static final long POLL_INTERVAL_MS = Long
			.getLong("net.zamasoft.pdfg2d.font.pollIntervalMillis", 1000L);

	/**
	 * 最後に設定を検査した時刻({@link System#nanoTime()}のミリ秒換算)。
	 * {@code nanoTime}の原点は任意(負値も許される)ため、0 で初期化すると
	 * 負の環境では {@code now - 0 < interval} が長期間真のままになり、
	 * ホットリロードが止まる。実時刻基準で初期化する(2026-07-30)。
	 */
	private long lastPollAt = System.nanoTime() / 1_000_000L - POLL_INTERVAL_MS;

	protected synchronized void poll() {
		// **検査の間隔をあける**(2026-07-29)。
		//
		// {@link #lookup}は同期メソッドで、そこから毎回この poll が呼ばれ、
		// {@code config.exists()} が**ファイルシステムのstat**を行っていた。
		// lookup は文字の並びごとに呼ばれる
		// ({@code StyledTextUnitizer.characters} → {@code FontManagerImpl
		// .getFontListMetrics})ため、<b>全変換の全文字がグローバルロックの中で
		// syscallを1回する</b>ことになり、並行変換が事実上直列化していた。
		//
		// 実測(2026-07-29、掃過24スレッド): CPU時間/経過時間の比が
		// <b>1.3</b>しかなく、スレッドダンプでは20本がこのモニタ待ちだった。
		// 24スレッドでも12スレッドでも同じ速度、という症状の原因である。
		//
		// 設定ファイルの更新検出(ホットリロード)は維持するが、1秒に1回で
		// 十分である——設定を書き換えた運用者が1秒待てないことはない。
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

			// クラスパス上のモジュール(絵文字フォント等)からの追加フォント。
			// 旧Class.forName+フィールド反射をServiceLoaderの型付きSPIへ
			// 置換(2026-08-01)。プロバイダ不在は正常(非搭載構成)
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
}
