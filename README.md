# pdfg2d

[日本語 (Japanese)](README_ja.md)

## What is this?
pdfg2d is a high-performance PDF generator for Java, providing a `java.awt.Graphics2D` implementation that outputs to PDF.

## Requirements
* Java 21 or later

## Installation
pdfg2d is available on the Maven Central Repository.

### Maven
```xml
<dependency>
    <groupId>io.github.mimidesunya</groupId>
    <artifactId>pdfg2d-pdf</artifactId>
    <version>1.2.0</version>
</dependency>
```

### Gradle
```kotlin
implementation("io.github.mimidesunya:pdfg2d-pdf:1.2.0")
```

To include SVG and Emoji support:
```kotlin
implementation("io.github.mimidesunya:pdfg2d-svg:1.2.0")
```

## Features
* **Java 21 Support**: Leverages modern Java features.
* **PDF Versions**: PDF 1.2 to 1.7 and PDF 2.0; Linearized PDF (Fast Web View).
* **Archival / accessibility**: PDF/A-1b, 2b/2u/2a, 3b/3u/3a, 4/4f and PDF/UA-1 (all validated with veraPDF); Tagged PDF with an explicit structure API and automatic tagging fallback.
* **Prepress**: PDF/X-1a, PDF/X-4, PDF/X-6 and PDF/VT-1 (per-record DPart metadata), TrimBox/ArtBox validation, configurable OutputIntent.
* **Commercial Printing**:
    * Spot colors (Separation color spaces) with tint and overprint, including the registration color.
    * ICC-based RGB color (sRGB profile bundled) and rendering intents — a true RGB workflow under PDF/X-4/X-6.
    * Optional content layers (OCG): named layers with view/print visibility, initial state and locking (e.g. print-only watermarks).
    * Imposition with printer's marks: single-page, N-up, step-and-repeat, saddle-stitch (with creep) and cut-and-stack — streamed with constant memory.
* **Advanced Functionality**:
    * Bookmarks, Permissions, Viewer Preferences, Meta Information.
    * Encryption: Arcfour (RC4) and AES.
    * Color Modes: RGB, Gray, CMYK.
    * File Attachments, Hyperlinks, Open JavaScript Actions.
* **Graphics Capabilities**:
    * Full `java.awt.Graphics2D` bridge.
    * Group Images, Tiling Patterns, Shading Patterns (with alpha gradients via luminosity soft masks).
    * [SVG Images support](https://github.com/mimidesunya/pdfg2d/blob/main/pdfg2d-demo/src/main/java/net/zamasoft/pdfg2d/demo/SVGTigerApp.java).
    * [Emoji support](https://github.com/mimidesunya/pdfg2d/blob/main/pdfg2d-demo/src/main/java/net/zamasoft/pdfg2d/demo/EmojiApp.java).
* **Compression & Performance**:
    * PDF: Deflate (configurable level), Deflate + Ascii85, object streams + cross-reference streams.
    * Images: Deflate, JPEG, JPEG2000.
    * Parallel page rendering with byte-identical output; cross-document font subset cache.
* **Fonts**:
    * Core 14 Fonts.
    * CID-Keyed Fonts (Chinese, Japanese, Korean, HK/Taiwanese).
    * Embedded Fonts (TrueType, OpenType/CFF, WOFF) with real binary subsetting.

See [`docs/`](docs/README.md) for the feature inventory, extension proposals and changelog (Japanese).

## Building from Source
This project uses Gradle.

The `zstream-io` and `zstream-resolver` dependencies are developed in the
[zamasoftnet/zstream](https://github.com/zamasoftnet/zstream) repository.
The Gradle settings use a composite build that points to a local checkout of
that repository.

To build the project:
```bash
./gradlew build
```

To build with Emoji fonts (which takes longer):
```bash
./gradlew build -PincludeEmojiFonts=true
```

## Example
[Full Example Source](https://github.com/mimidesunya/pdfg2d/blob/main/pdfg2d-demo/src/main/java/net/zamasoft/pdfg2d/demo/DrawApp.java)

```java
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.File;

import net.zamasoft.pdfg2d.PDFGraphics2D;
import net.zamasoft.pdfg2d.pdf.util.PDFUtils;

public class DrawApp {
    public static void main(String[] args) throws Exception {
        try (PDFGraphics2D g2d = new PDFGraphics2D(new File("out/draw.pdf"))) {
            g2d.setColor(Color.WHITE);
            g2d.fill(new Rectangle2D.Double(0, 0, PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM),
                    PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM)));

            g2d.setColor(Color.RED);
            g2d.fill(new Rectangle2D.Double(PDFUtils.mmToPt(51), 0, PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM - 51),
                    PDFUtils.mmToPt(154)));

            g2d.setColor(Color.BLUE);
            g2d.fill(new Rectangle2D.Double(0, PDFUtils.mmToPt(154), PDFUtils.mmToPt(51),
                    PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM) - 154));

            g2d.setColor(Color.YELLOW);
            g2d.fill(new Rectangle2D.Double(PDFUtils.mmToPt(187), PDFUtils.mmToPt(182), PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM - 187),
                    PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM) - 182));
            
            g2d.setStroke(new BasicStroke((float)PDFUtils.mmToPt(7)));
            g2d.setColor(Color.BLACK);
            g2d.draw(new Line2D.Double(PDFUtils.mmToPt(51), 0, PDFUtils.mmToPt(51), PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM)));
            g2d.draw(new Line2D.Double(0, PDFUtils.mmToPt(154), PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM), PDFUtils.mmToPt(154)));
            g2d.draw(new Line2D.Double(PDFUtils.mmToPt(187), PDFUtils.mmToPt(154), PDFUtils.mmToPt(187), PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM)));

            g2d.setStroke(new BasicStroke((float)PDFUtils.mmToPt(14)));
            g2d.draw(new Line2D.Double(0, PDFUtils.mmToPt(70), PDFUtils.mmToPt(51), PDFUtils.mmToPt(70)));
            g2d.draw(new Line2D.Double(PDFUtils.mmToPt(187), PDFUtils.mmToPt(182), PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM), PDFUtils.mmToPt(182)));
        }
    }
}
```

## License
[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
