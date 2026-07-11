package net.zamasoft.pdfg2d.pdf;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.OutputStream;

import net.zamasoft.pdfg2d.gc.paint.CMYKColor;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.Color.Type;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.util.ColorUtils;

/**
 * Abstract base for writing PDF graphics content to an output stream.
 * <p>
 * Provides typed write helpers for the most common PDF content-stream
 * constructs that deal with coordinate geometry and colour: positions,
 * rectangles, affine transforms, fill/stroke colours, and resource
 * references.
 * </p>
 * <p>
 * Coordinate system: pdfg2d uses a top-left origin internally, but PDF uses a
 * bottom-left origin.  The {@code write*} helpers in this class perform the
 * y-flip ({@code pdfY = height - javaY}) automatically.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public abstract class PDFGraphicsOutput extends PDFOutput {
	protected final double width, height;

	protected final PDFWriter pdfWriter;

	/**
	 * Constructs a PDFGraphicsOutput.
	 *
	 * @param pdfWriter the PDF writer that owns this output
	 * @param out       the underlying output stream
	 * @param width     the width of the graphics area in points
	 * @param height    the height of the graphics area in points
	 * @throws IOException if an I/O error occurs
	 */
	public PDFGraphicsOutput(final PDFWriter pdfWriter, final OutputStream out, final double width,
			final double height) throws IOException {
		super(out, pdfWriter.getParams().platformEncoding());
		final var params = pdfWriter.getParams();
		this.setPrecision(params.precision());
		this.width = width;
		this.height = height;
		this.pdfWriter = pdfWriter;
	}

	/**
	 * Returns the creating PDFWriter.
	 * 
	 * @return the PDFWriter
	 */
	public PDFWriter getPdfWriter() {
		return this.pdfWriter;
	}

	/**
	 * Returns the width of the graphics area in PDF user units (points).
	 *
	 * @return the width in points
	 */
	public double getWidth() {
		return width;
	}

	/**
	 * Returns the height of the graphics area in PDF user units (points).
	 *
	 * @return the height in points
	 */
	public double getHeight() {
		return height;
	}

	/**
	 * Declares that a named resource of a given type is used in this content stream.
	 * The resource must have been registered with the PDF writer before use.
	 *
	 * @param type the PDF resource type (e.g., "Font", "XObject", "Pattern")
	 * @param name the resource name
	 * @throws IOException if an I/O error occurs
	 */
	public abstract void useResource(String type, String name) throws IOException;

	/**
	 * Opens a marked-content sequence for real content in a tagged PDF and
	 * returns its MCID. The base implementation (patterns, group images,
	 * untagged documents) does nothing and returns {@code -1}; page outputs
	 * of tagged documents override this.
	 *
	 * @param role the structure type used when no structure element is open
	 * @param alt  an alternate description (for figures), or {@code null}
	 * @return the MCID, or {@code -1} when no sequence was opened
	 * @throws IOException if an I/O error occurs
	 */
	public int beginMark(final String role, final String alt) throws IOException {
		return -1;
	}

	/**
	 * Opens an artifact marked-content sequence (decorative content that is
	 * not part of the logical structure). Returns {@code false} when the
	 * document is not tagged.
	 *
	 * @return {@code true} when a sequence was opened
	 * @throws IOException if an I/O error occurs
	 */
	public boolean beginArtifact() throws IOException {
		return false;
	}

	/**
	 * Closes the innermost marked-content sequence opened by
	 * {@link #beginMark} or {@link #beginArtifact}.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	public void endMark() throws IOException {
	}

	/**
	 * Writes coordinates relative to the bottom-left origin of PDF.
	 * 
	 * @param x X coordinate (top-left origin)
	 * @param y Y coordinate (top-left origin)
	 * @throws IOException If an I/O error occurs
	 */
	public void writePosition(final double x, final double y) throws IOException {
		this.writeReal(x);
		this.writeReal(this.height - y);
	}

	/**
	 * Writes a rectangle specified by two points.
	 * 
	 * @param x1 X1 coordinate
	 * @param y1 Y1 coordinate
	 * @param x2 X2 coordinate
	 * @param y2 Y2 coordinate
	 * @throws IOException If an I/O error occurs
	 */
	public void writeRectangle(final double x1, final double y1, final double x2, final double y2) throws IOException {
		this.startArray();
		this.writePosition(x1, y2);
		this.writePosition(x2, y1);
		this.endArray();
	}

	/**
	 * Writes a rectangle in 're' (rectangle) operator format: x, y, width, height.
	 * Note: PDF 're' operator uses bottom-left origin.
	 * 
	 * @param x      X coordinate (top-left origin)
	 * @param y      Y coordinate (top-left origin)
	 * @param width  Rectangle width
	 * @param height Rectangle height
	 * @throws IOException If an I/O error occurs
	 */
	public void writeRect(final double x, final double y, final double width, final double height) throws IOException {
		this.writePosition(x, y + height);
		this.writeReal(width);
		this.writeReal(height);
	}

	/**
	 * Writes an affine transformation matrix in PDF 'cm' operator format.
	 * 
	 * @param at The affine transform to write
	 * @throws IOException If an I/O error occurs
	 */
	public void writeTransform(final AffineTransform at) throws IOException {
		// Convert top-left origin to bottom-left origin
		final var tpdf = new AffineTransform(1, 0, 0, -1, 0, this.height);
		final var iat = new AffineTransform(at);
		iat.preConcatenate(tpdf);
		iat.concatenate(tpdf);

		this.writeReal(iat.getScaleX());
		this.writeReal(iat.getShearY());
		this.writeReal(iat.getShearX());
		this.writeReal(iat.getScaleY());
		this.writeReal(iat.getTranslateX());
		this.writeReal(iat.getTranslateY());
	}

	/**
	 * Writes the fill color (non-stroking color).
	 * 
	 * @param color The color to set
	 * @throws IOException If an I/O error occurs
	 */
	public void writeFillColor(final Color color) throws IOException {
		final var params = this.pdfWriter.getParams();
		// Spot/DeviceN colors are never converted; their alternates follow the
		// color mode inside the Separation/DeviceN color space instead.
		final var named = color.getColorType() == Type.SPOT || color.getColorType() == Type.DEVICEN;
		final var processedColor = named ? color : switch (params.effectiveColorMode()) {
			case GRAY -> (color.getColorType() != Type.GRAY) ? ColorUtils.toGray(color) : color;
			case CMYK -> (color.getColorType() != Type.CMYK) ? ColorUtils.toCMYK(color) : color;
			default -> color;
		};

		switch (processedColor.getColorType()) {
			case GRAY -> {
				this.writeReal(processedColor.getComponent(0));
				this.writeOperator("g");
			}
			case RGB, RGBA -> {
				final var icc = this.pdfWriter.useICCBasedRGB();
				if (icc != null) {
					this.useResource("ColorSpace", icc);
					this.writeName(icc);
					this.writeOperator("cs");
					this.writeReal(processedColor.getComponent(RGBColor.R));
					this.writeReal(processedColor.getComponent(RGBColor.G));
					this.writeReal(processedColor.getComponent(RGBColor.B));
					this.writeOperator("scn");
				} else {
					this.writeReal(processedColor.getComponent(RGBColor.R));
					this.writeReal(processedColor.getComponent(RGBColor.G));
					this.writeReal(processedColor.getComponent(RGBColor.B));
					this.writeOperator("rg");
				}
			}
			case CMYK -> {
				this.writeReal(processedColor.getComponent(CMYKColor.C));
				this.writeReal(processedColor.getComponent(CMYKColor.M));
				this.writeReal(processedColor.getComponent(CMYKColor.Y));
				this.writeReal(processedColor.getComponent(CMYKColor.K));
				this.writeOperator("k");
			}
			case SPOT -> {
				final var spot = (net.zamasoft.pdfg2d.gc.paint.SpotColor) processedColor;
				final var csName = this.pdfWriter.useSeparation(spot.name(), spot.alternate());
				this.useResource("ColorSpace", csName);
				this.writeName(csName);
				this.writeOperator("cs");
				this.writeReal(spot.tint());
				this.writeOperator("scn");
			}
			case DEVICEN -> {
				final var devn = (net.zamasoft.pdfg2d.gc.paint.DeviceNColor) processedColor;
				final var csName = this.pdfWriter.useDeviceN(devn.colorants());
				this.useResource("ColorSpace", csName);
				this.writeName(csName);
				this.writeOperator("cs");
				for (final var tint : devn.tints()) {
					this.writeReal(tint);
				}
				this.writeOperator("scn");
			}
			default -> throw new IllegalStateException("Unsupported color type: " + processedColor.getColorType());
		}
	}

	/**
	 * Writes the stroke color.
	 * 
	 * @param color The color to set
	 * @throws IOException If an I/O error occurs
	 */
	public void writeStrokeColor(final Color color) throws IOException {
		final var params = this.pdfWriter.getParams();
		if (color.getColorType() == Type.SPOT) {
			final var spot = (net.zamasoft.pdfg2d.gc.paint.SpotColor) color;
			final var csName = this.pdfWriter.useSeparation(spot.name(), spot.alternate());
			this.useResource("ColorSpace", csName);
			this.writeName(csName);
			this.writeOperator("CS");
			this.writeReal(spot.tint());
			this.writeOperator("SCN");
			return;
		}
		if (color.getColorType() == Type.DEVICEN) {
			final var devn = (net.zamasoft.pdfg2d.gc.paint.DeviceNColor) color;
			final var csName = this.pdfWriter.useDeviceN(devn.colorants());
			this.useResource("ColorSpace", csName);
			this.writeName(csName);
			this.writeOperator("CS");
			for (final var tint : devn.tints()) {
				this.writeReal(tint);
			}
			this.writeOperator("SCN");
			return;
		}
		final var processedColor = switch (params.effectiveColorMode()) {
			case GRAY -> (color.getColorType() != Type.GRAY) ? ColorUtils.toGray(color) : color;
			case CMYK -> (color.getColorType() != Type.CMYK) ? ColorUtils.toCMYK(color) : color;
			default -> color;
		};

		switch (processedColor.getColorType()) {
			case GRAY -> {
				this.writeReal(processedColor.getComponent(0));
				this.writeOperator("G");
			}
			case RGB, RGBA -> {
				final var icc = this.pdfWriter.useICCBasedRGB();
				if (icc != null) {
					this.useResource("ColorSpace", icc);
					this.writeName(icc);
					this.writeOperator("CS");
					this.writeReal(processedColor.getComponent(RGBColor.R));
					this.writeReal(processedColor.getComponent(RGBColor.G));
					this.writeReal(processedColor.getComponent(RGBColor.B));
					this.writeOperator("SCN");
				} else {
					this.writeReal(processedColor.getComponent(RGBColor.R));
					this.writeReal(processedColor.getComponent(RGBColor.G));
					this.writeReal(processedColor.getComponent(RGBColor.B));
					this.writeOperator("RG");
				}
			}
			case CMYK -> {
				this.writeReal(processedColor.getComponent(CMYKColor.C));
				this.writeReal(processedColor.getComponent(CMYKColor.M));
				this.writeReal(processedColor.getComponent(CMYKColor.Y));
				this.writeReal(processedColor.getComponent(CMYKColor.K));
				this.writeOperator("K");
			}
			default -> throw new IllegalStateException("Unsupported color type: " + processedColor.getColorType());
		}
	}
}
