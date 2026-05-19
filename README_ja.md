# pdfg2d

[English](README.md)

## 概要
pdfg2dは、Javaの `java.awt.Graphics2D` APIを使用してPDFを出力するための高性能なライブラリです。

## 必要要件
* Java 21 以降

## インストール
pdfg2dはMaven Central Repositoryから入手可能です。

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

SVGおよび絵文字サポートを含める場合:
```kotlin
implementation("io.github.mimidesunya:pdfg2d-svg:1.2.0")
```

## 機能一覧
* **Java 21 サポート**: 最新のJava機能を利用しています。
* **対応PDFバージョン**: PDF 1.2 から 1.7、PDF/A-1b、PDF/X-1aに対応。
* **高度な機能**:
    * しおり、権限設定、ビューア設定、文書情報設定。
    * 暗号化: Arcfour (RC4) および AES。
    * カラーモード: RGB, Gray, CMYK。
    * ファイル添付、ハイパーリンク、Open JavaScriptアクション。
* **グラフィックス機能**:
    * `java.awt.Graphics2D` との完全な互換性。
    * グループイメージ、タイリングパターン、シェーディングパターン。
    * [SVG画像サポート](https://github.com/mimidesunya/pdfg2d/blob/main/pdfg2d-demo/src/main/java/net/zamasoft/pdfg2d/demo/SVGTigerApp.java)。
    * [絵文字サポート](https://github.com/mimidesunya/pdfg2d/blob/main/pdfg2d-demo/src/main/java/net/zamasoft/pdfg2d/demo/EmojiApp.java)。
* **圧縮**:
    * PDF: Deflate, Deflate + Ascii85。
    * 画像: Deflate, JPEG, JPEG2000。
* **フォント**:
    * Standard 14 Fonts (Core 14 Fonts)。
    * CID-Keyed Fonts (中国語、日本語、韓国語、香港/台湾)。
    * 埋め込みフォント (TrueType, OpenType/CFF, WOFF)。

## ソースコードからのビルド
このプロジェクトはGradleを使用しています。

`zstream-io` および `zstream-resolver` は
[zamasoftnet/zstream](https://github.com/zamasoftnet/zstream) リポジトリで開発されています。
Gradle設定では、このリポジトリのローカルチェックアウトを指す composite build を使用しています。

プロジェクトをビルドするには:
```bash
./gradlew build
```

絵文字フォントを含めてビルドするには（時間がかかります）:
```bash
./gradlew build -PincludeEmojiFonts=true
```

## サンプルコード
[完全なソースコード](https://github.com/mimidesunya/pdfg2d/blob/main/pdfg2d-demo/src/main/java/net/zamasoft/pdfg2d/demo/DrawApp.java)

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
            // 背景を白で塗りつぶし
            g2d.setColor(Color.WHITE);
            g2d.fill(new Rectangle2D.Double(0, 0, PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM),
                    PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM)));

            // 赤い四角形を描画
            g2d.setColor(Color.RED);
            g2d.fill(new Rectangle2D.Double(PDFUtils.mmToPt(51), 0, PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM - 51),
                    PDFUtils.mmToPt(154)));

            // 青い四角形を描画
            g2d.setColor(Color.BLUE);
            g2d.fill(new Rectangle2D.Double(0, PDFUtils.mmToPt(154), PDFUtils.mmToPt(51),
                    PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM) - 154));

            // 黄色い四角形を描画
            g2d.setColor(Color.YELLOW);
            g2d.fill(new Rectangle2D.Double(PDFUtils.mmToPt(187), PDFUtils.mmToPt(182), PDFUtils.mmToPt(PDFUtils.PAPER_A4_WIDTH_MM - 187),
                    PDFUtils.mmToPt(PDFUtils.PAPER_A4_HEIGHT_MM) - 182));
            
            // 線の描画
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

## ライセンス
[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
