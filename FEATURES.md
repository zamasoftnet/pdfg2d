# pdfg2d Features

## 概要
pdfg2d は、Java の `Graphics2D`/独自 `GC` API を PDF 出力へ変換するためのマルチモジュールライブラリです。単純な図形描画だけでなく、PDF バージョン制御、メタデータ、暗号化、添付、リンク、Open Action、Viewer Preferences、SVG、画像埋め込み、複雑なテキストレイアウトまで扱えます。

## モジュール構成
- `pdfg2d-core`: 描画 API、色、ペイント、テキストレイアウト、フォント抽象、ユーティリティ。
- `pdfg2d-pdf`: PDF 生成本体、PDFWriter、ページ出力、画像埋め込み、注釈、暗号化、各種 PDF 設定。
- `pdfg2d-font`: TrueType/OpenType/CFF/WOFF 読み込みとグリフ解析。
- `pdfg2d-io`: 分割出力、テンポラリ出力、ストリーム出力。
- `pdfg2d-resolver`: `file:` `data:` `zip:` を含む Source/Resolver 層と制限付きリゾルバ。
- `pdfg2d-svg`: Batik ベースの SVG 描画連携。
- `pdfg2d-svg-emoji`: 絵文字フォント支援。既定ビルドでは無効で、`-PincludeEmojiFonts=true` で有効化します。
- `pdfg2d-demo`: 実行サンプルと統合テスト。

## Rust 実装状況

2026-04-07 時点の `pdfg2d-rust` の到達点です。

- 実装済み
  - PDF バージョンヘッダ出力
  - xref / trailer
  - 文書情報 `Title` `Author` `Subject` `Keywords` `Creator` `Producer`
  - Viewer Preferences の一部
    - `NonFullScreenPageMode`
    - `PickTrayByPDFSize`
    - `ViewArea`
    - `ViewClip`
    - `PrintArea`
    - `PrintClip`
    - `PrintScaling`
    - `PrintPageRange`
    - `NumCopies`
  - Open Action JavaScript
  - しおり
  - URI リンク注釈
  - 添付ファイル
  - 権限制御付き暗号化
  - PDF 本文圧縮
  - 画像圧縮 `FLATE` `JPEG`
  - Core 14 フォント参照
  - TrueType / OpenType(CFF 含む) / WOFF の最小読み込みと PDF 埋め込み
  - TrueType / OpenType(CFF 含む) / WOFF の実バイナリ subset 化
  - PDF レベルの最小 subset 化
  - Type0 / CIDFontType2 による CID / CJK の最小実装
  - CID 向け縦書き `Identity-V`
  - font fallback
  - 英語/日本語の最小テキストレイアウト
  - 段落折り返し、justify、縦組み列配置
  - mixed font / mixed size / bold / italic span
  - plain text / styled span の `\n` `\t`
  - 日本語禁則の最小実装
  - 基本図形、変形、クリッピング、破線、複数ページ
  - alpha / ExtGState
  - ラスタ画像埋め込み
  - linear / radial gradient
  - Linearized PDF 出力
  - SVG の最小実装
    - `usvg` ベースの path / group / clip-path / opacity / linearGradient / radialGradient / colored pattern
    - pattern 内の linearGradient / radialGradient
    - raster `image` ノード
    - pattern 内の raster `image` ノード
    - nested pattern
    - `usvg` flattened text による SVG `text`
    - pattern 内の SVG `text`
    - raster image alpha soft mask
    - PDF 1.4+ 向け transparency-group Form XObject fallback
  - `pdfg2d-io` 相当の in-memory 分割出力、patch-back、position tracking、adapter / wrapper
  - `pdfg2d-resolver` 相当の `file:` `data:` `zip:` `http:` `https:`、cached resolver、restricted resolver、URL/stream/wrapper utilities
  - HTTP resolver の最小設定 API
    - timeout
    - redirect
    - user-agent
    - TLS config
    - https-only policy
    - status policy
  - remote HTTP/HTTPS body の lazy fetch と `release()` による cache clear
  - remote HTTP/HTTPS response の status code / header / header name access
  - remote metadata 取得時の `HEAD` と body 取得時の `GET` の分離
- 未実装
  - Java と同等レベルの高度な字形配置・shaping
  - SVG の高度機能
    - ネイティブ PDF text としての selectable な SVG text 出力
    - pattern 内の text
  - svg-emoji
- Rust では不要
  - Java2D / Graphics2D ブリッジ相当は parity 対象外
- Rust テスト
  - `tests/parity_smoke.rs` `tests/io_parity.rs` `tests/font_parity.rs` `tests/text_layout_parity.rs` `tests/resolver_parity.rs` `tests/linearized_pdf_parity.rs` で smoke/parity を確認
  - `tests/java_parity.rs` で Java 生成 PDF との差分比較を確認
  - 生成 PDF は `target/generated-test-files/` に保存
  - 保存後の構文検証は Rust 側テストヘルパーで実行

## PDF 文書機能
- PDF 1.2 から 1.7、PDF/A-1b、PDF/X-1a を出力できます。
- Linearized PDF を生成できます。
- 文書情報として `Title` `Author` `Subject` `Keywords` `Creator` `Producer` を設定できます。
- Viewer Preferences を設定できます。
  例: 非フルスクリーン時のページモード、用紙サイズに応じた給紙、表示/印刷領域、印刷倍率、コピー数、印刷対象ページ範囲。
- Open Action として JavaScript を埋め込めます。
- しおりを追加できます。
- URI リンク注釈を追加できます。
- ファイル添付を埋め込めます。
- 権限制御付き暗号化を設定できます。
  例: RC4/AES、ユーザーパスワード、オーナーパスワード、印刷/コピー/編集権限。
- RGB / Gray / CMYK のカラーモードを切り替えられます。
- PDF 本文と画像の圧縮方式を切り替えられます。

## 描画機能
- `PDFGraphics2D` により `java.awt.Graphics2D` 互換の描画ができます。
- 図形塗りつぶし、線描画、クリッピング、破線、変形、複数ページ出力に対応します。
- `PDFGC` により低レベル描画 API を直接利用できます。
- 透明色、塗り/線の alpha、透明グループ、グループ画像を扱えます。
- テキストモードとして `FILL` `STROKE` `FILL_STROKE` を使えます。
- ラスタ画像を `BufferedImage` または Source から埋め込めます。
- SVG を PDF に描画できます。
- Java2D ブリッジ経由で通常の AWT 描画コードを PDF 出力へ流せます。

## テキスト・フォント機能
- Core 14 フォントを利用できます。
- CID-Keyed Fonts を利用でき、日本語・中国語・韓国語系文字へ対応します。
- TrueType、OpenType/CFF、WOFF の埋め込みフォントを読み込めます。
- 日本語/英語の改行規則を伴うテキストレイアウトを利用できます。
- 横書き・縦書き、字形配置、強調、斜体、太字、複合テキスト描画に対応します。
- ページ幅に基づく段落レイアウト、行送り、両端揃えができます。

## デモで確認できる機能
- `DrawApp`: 基本図形描画。
- `PagesApp`: 複数ページ生成。
- `Graphics2DBridgeDemo`: Graphics2D ブリッジ、複数ページ、属性付き文字。
- `LinkAnnotationDemo`: リンク注釈。
- `OpenActionDemo`: Open JavaScript Action。
- `RasterImageDemo`: ラスタ画像埋め込み。
- `SVGRenderingDemo` / `SVGTigerApp`: SVG 描画。
- `StyledTextApp`: 段落レイアウトと両端揃え。
- `ComplexTextDemo`: 多言語テキストと縦書き。
- `TextOutlineDemo`: 文字アウトライン描画。
- `TransparencyDemo`: alpha と線スタイル。
- `TransparencyGroupDemo`: 透明グループ。
- `ViewerPreferencesDemo`: Viewer Preferences。
- `PdfBoxGraphics2dPerformanceDemo`: PDFBox Graphics2D との比較用デモ。
- `EmojiApp`: 絵文字描画デモ。`pdfg2d-svg-emoji` を有効化した場合に利用します。

## 機能とテスト有無

### PDF 文書機能
- PDF 1.2 から 1.7、PDF/A-1b、PDF/X-1a: テスト有。`PDFVersionTest`
- Linearized PDF: テスト有。`LinearizedPDFTest`
  - Java 側は linearization dictionary、primary xref、`qpdf --check` 互換まで検証。
- 文書情報 `Title` `Author` `Subject` `Keywords` `Creator` `Producer`: テスト有。`PDFMetaInfoTest`
- Viewer Preferences: テスト有。`DemoFeatureCoverageTest`
- Open Action JavaScript: テスト有。`PDFOpenActionTest`
- しおり: テスト有。`PDFBookmarkTest`
- URI リンク注釈: テスト有。`PDFLinkTest`
- 添付ファイル: テスト有。`PDFAttachmentTest`
- 権限制御付き暗号化: テスト有。`PDFEncryptionTest`
- RGB / Gray / CMYK カラーモード: テスト有。`PDFColorModeTest`
- PDF 本文圧縮: テスト有。`PDFCompressionTest`
- 画像圧縮 `FLATE` `JPEG`: テスト有。`PDFImageCompressionTest`
- 画像圧縮 `JPEG2000`: テスト有。`ImageFlowJpeg2000Test`

### 描画機能
- `PDFGraphics2D` による `Graphics2D` 互換描画: テスト有。`DemoFeatureCoverageTest`
- 図形塗りつぶし、線描画、クリッピング、破線、変形: テスト有。`GraphicsDrawingTest`, `DemoFeatureCoverageTest`
- 複数ページ出力: テスト有。`DemoFeatureCoverageTest`
- `PDFGC` 低レベル描画 API: テスト有。`DemoFeatureCoverageTest`
- 透明色、塗り/線 alpha: テスト有。`TransparencyTest`
- 透明グループ、グループ画像: テスト有。`DemoFeatureCoverageTest`
- テキストモード `FILL` `STROKE` `FILL_STROKE`: テスト有。`DemoFeatureCoverageTest`
- ラスタ画像 `BufferedImage` 埋め込み: テスト有。`DemoFeatureCoverageTest`
- ラスタ画像 `Source` 埋め込み: テスト有。`DemoFeatureCoverageTest`
- Gradient / Pattern paint: テスト有。`DemoFeatureCoverageTest`
- Java2D ブリッジ経由の描画: テスト有。`DemoFeatureCoverageTest`

### テキスト・フォント機能
- Core 14 フォント: テスト有。`DemoFeatureCoverageTest`
- CID-Keyed Fonts: テスト有。`DemoFeatureCoverageTest`
- TrueType、OpenType/CFF、WOFF 読み込み: テスト有。`FontFileTest`
- 日本語/英語の改行規則を伴うテキストレイアウト: テスト有。`DemoFeatureCoverageTest`
- 横書き・縦書き、字形配置、強調、斜体、太字、複合テキスト描画: テスト有。`DemoFeatureCoverageTest`, `TextRenderingTest`
- ページ幅に基づく段落レイアウト、行送り、両端揃え: テスト有。`DemoFeatureCoverageTest`

### モジュール別 API / 基盤機能
- `pdfg2d-core` 描画 API、Recorder/NoOp、paint 値オブジェクト: テスト有。`CoreApiTest`
- `pdfg2d-io` 分割出力、パッチ書き戻し: テスト有。`FragmentedOutputTest`
- `pdfg2d-resolver` `file:` `data:` `zip:` を含む Source/Resolver: テスト有。`ResolverApiTest`

### SVG / Batik 連携
- SVG 描画全般: テスト有。`SVGApiTest`, `DemoFeatureCoverageTest`
- Batik pattern / patternTransform: テスト有。`SVGApiTest`
- Batik linearGradient / radialGradient: テスト有。`SVGApiTest`
- Batik clip-path: テスト有。`SVGApiTest`
- 半透明 SVG と PDF transparency group 分岐: テスト有。`SVGApiTest`
- SVG の `fill-opacity` / `stroke-opacity`: テスト有。`SVGApiTest`

### SVG Emoji
- `emoji.zip` リソース整合性: テスト有。`BuildEmojiIndexToolTest`
- `BuildEmojiIndexTool` の INDEX 生成と重複除去: テスト有。`BuildEmojiIndexToolTest`
- 実際の絵文字描画統合: 未テスト

## 備考
- 上記の「テスト有」は、自動テストで代表的な正常系または主要分岐を検証していることを意味します。
- 境界値、異常系、全組み合わせまで完全網羅していることを意味するものではありません。
