package net.zamasoft.pdfg2d.font.emoji;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Dimension2D;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGOMDocument;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ImageFont;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.util.FontUtils;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.svg.PDFGVTBuilder;
import net.zamasoft.pdfg2d.svg.SVGBridgeGraphics2D;
import net.zamasoft.pdfg2d.svg.SVGDimension;
import net.zamasoft.pdfg2d.svg.SVGUserAgent;

/**
 * Font implementation for rendering emoji characters as SVG graphics.
 * 
 * <p>
 * This class loads emoji SVG files from the bundled emoji.zip resource and
 * renders them using Apache Batik's GVT (Graphic Vector Toolkit) rendering
 * engine.
 * 
 * <p>
 * For PDF output with version 1.4+, emoji graphics are cached as PDF group
 * images
 * for better performance. For other outputs, the GVT graphics nodes are cached
 * and painted directly.
 * 
 * @since 1.0
 */
class EmojiFont implements ImageFont {

	private static final long serialVersionUID = 2L;

	/** The font source providing metadata for this font. */
	protected final EmojiFontSource source;

	/** Cache of parsed GVT graphics nodes by glyph ID. */
	protected final Map<Integer, GraphicsNode> gidToNode = new HashMap<>();

	/** Cache of PDF group images by glyph ID (for PDF 1.4+ output). */
	protected final Map<Integer, PDFGroupImage> gidToImage = new HashMap<>();

	/** Standard viewport size for emoji rendering (128x128 pixels). */
	protected static final Dimension2D VIEWPORT = new SVGDimension(128, 128);

	/**
	 * Creates a new emoji font with the specified source.
	 *
	 * @param source the font source providing emoji metadata
	 */
	public EmojiFont(final EmojiFontSource source) {
		this.source = source;
	}

	/**
	 * Converts a Unicode code point to a glyph ID.
	 *
	 * @param c the Unicode code point
	 * @return the glyph ID, or 0 if no emoji exists for this code point
	 */
	@Override
	public int toGID(final int c) {
		final var code = Integer.toHexString(c);
		final var gid = EmojiFontSource.codeToGid.get(code);
		return gid != null ? gid : 0;
	}

	/**
	 * Returns the advance width for a glyph.
	 *
	 * @param gid the glyph ID
	 * @return always 1000 (full em-width)
	 */
	@Override
	public short getAdvance(final int gid) {
		return 1000;
	}

	/**
	 * Returns the width for a glyph.
	 *
	 * @param gid the glyph ID
	 * @return always 1000 (full em-width)
	 */
	@Override
	public short getWidth(final int gid) {
		return 1000;
	}

	/**
	 * Returns the bounding box for this font.
	 *
	 * @return the bounding box from the font source
	 */
	public BBox getBBox() {
		return this.source.getBBox();
	}

	/**
	 * Returns the font source for this font.
	 *
	 * @return the emoji font source
	 */
	@Override
	public FontSource getFontSource() {
		return this.source;
	}

	/**
	 * Returns the kerning adjustment between two glyphs.
	 *
	 * @param sgid the first glyph ID
	 * @param gid  the second glyph ID
	 * @return always 0 (no kerning for emoji)
	 */
	@Override
	public short getKerning(final int sgid, final int gid) {
		return 0;
	}

	/**
	 * Returns a ligature glyph ID for combining two characters.
	 * 
	 * <p>
	 * This is used for emoji sequences like skin tone modifiers and
	 * zero-width joiner (ZWJ) sequences.
	 *
	 * @param gid the current glyph ID, or -1 for the first character
	 * @param cid the next character code point
	 * @return the combined glyph ID, or -1 if no ligature exists
	 */
	@Override
	public int getLigature(final int gid, final int cid) {
		if (gid == -1) {
			return -1;
		}
		final var scode = EmojiFontSource.gidToCode.get(gid);
		final var code = scode + "_" + Integer.toHexString(cid);
		final var fgid = EmojiFontSource.codeToGid.get(code);
		return fgid != null ? fgid : -1;
	}

	/**
	 * Draws text using this font to the graphics context.
	 *
	 * @param gc   the graphics context
	 * @param text the text to draw
	 * @throws IOException       if an I/O error occurs
	 * @throws GraphicsException if a graphics error occurs
	 */
	@Override
	public void drawTo(final GC gc, final Text text) throws IOException, GraphicsException {
		FontUtils.drawText(gc, this, text);
	}

	/**
	 * Draws a single glyph to the graphics context.
	 * 
	 * <p>
	 * This method loads the emoji SVG from the bundled resource, parses it using
	 * Batik, and renders it to the graphics context. For PDF output with
	 * transparency
	 * support (PDF 1.4+), the rendered emoji is cached as a PDF group image for
	 * better performance on subsequent draws.
	 *
	 * @param gc  the graphics context
	 * @param gid the glyph ID to draw
	 * @param at  optional transform to apply
	 */
	@Override
	public void drawGlyphForGid(final GC gc, final int gid, final AffineTransform at) {
		final var cachedImage = this.gidToImage.get(gid);
		final var gvtRoot = cachedImage != null ? null
				: this.gidToNode.computeIfAbsent(gid, k -> loadEmojiGraphicsNode(gc, gid));

		// Render the emoji to the graphics context
		try (final var gcState = gc.begin()) {
			if (at != null) {
				gc.transform(at);
			}
			gc.transform(AffineTransform.getTranslateInstance(0, -this.source.getAscent()));

			if (cachedImage != null) {
				try (final var gcState2 = gc.begin()) {
					gc.drawImage(cachedImage);
				}
			} else {
				gc.transform(AffineTransform.getScaleInstance(
						1000.0 / VIEWPORT.getWidth(),
						1000.0 / VIEWPORT.getHeight()));
				try (final var gcState2 = gc.begin()) {
					final Graphics2D g2d = new SVGBridgeGraphics2D(gc);
					gvtRoot.paint(g2d);
					g2d.dispose();
				}
			}
		}
	}

	/**
	 * Loads and parses an emoji SVG file, optionally caching as a PDF group image.
	 *
	 * @param gc  the graphics context (used to determine caching strategy)
	 * @param gid the glyph ID to load
	 * @return the parsed GVT graphics node
	 */
	private GraphicsNode loadEmojiGraphicsNode(final GC gc, final int gid) {
		var code = EmojiFontSource.gidToCode.get(gid);
		// Remove trailing ZWJ suffix for file lookup
		if (code.endsWith("_200d")) {
			code = code.substring(0, code.length() - 5);
		}
		final var fileName = "emoji_u" + code + ".svg";

		try {
			final var emojiStream = EmojiFontSource.getEmojiStream(fileName);
			if (emojiStream == null) {
				return null;
			}
			try (final var is = new BufferedInputStream(emojiStream)) {
				// Parse SVG document using Batik
				final var factory = new SAXSVGDocumentFactory(
						XMLResourceDescriptor.getXMLParserClassName());
				final var doc = (SVGOMDocument) factory.createDocument(null, is);
				final UserAgent ua = new SVGUserAgent(VIEWPORT);
				final var loader = new DocumentLoader(ua);
				final var ctx = new BridgeContext(ua, loader);
				ctx.setDynamic(false);
				final GVTBuilder gvt = new PDFGVTBuilder();
				final var gvtRoot = gvt.build(ctx, doc);

				// Cache strategy depends on output type and PDF version
				if (gc instanceof final PDFGC pdfGc
						&& pdfGc.getPDFGraphicsOutput().getPdfWriter().getParams()
								.version().v >= PDFParams.Version.V_1_4.v) {
					// Create cached PDF group image for better performance
					this.cacheAsPdfGroupImage(pdfGc, gid, gvtRoot);
				} else {
					// Cache raw GVT node for non-PDF or older PDF versions
					this.gidToNode.put(gid, gvtRoot);
				}
				return gvtRoot;
			}
		} catch (final Exception e) {
			throw new RuntimeException("Failed to load emoji: " + fileName, e);
		}
	}

	/**
	 * Caches an emoji as a PDF group image for efficient reuse.
	 *
	 * @param pdfGc   the PDF graphics context
	 * @param gid     the glyph ID
	 * @param gvtRoot the GVT graphics node to render
	 * @throws IOException if an I/O error occurs
	 */
	private void cacheAsPdfGroupImage(final PDFGC pdfGc, final int gid,
			final GraphicsNode gvtRoot) throws IOException {
		final var image = pdfGc.getPDFGraphicsOutput().getPdfWriter().createGroupImage(1000, 1000);
		final var gc2 = new PDFGC(image);
		gc2.transform(AffineTransform.getScaleInstance(
				1000.0 / VIEWPORT.getWidth(),
				1000.0 / VIEWPORT.getHeight()));
		try (final var gcState = gc2.begin()) {
			final Graphics2D g2d = new SVGBridgeGraphics2D(gc2);
			gvtRoot.paint(g2d);
			g2d.dispose();
		}
		image.close();
		this.gidToImage.put(gid, image);
	}
}
