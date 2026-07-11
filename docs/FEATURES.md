# pdfg2d Features

最終更新: 2026-07-11。実装済み機能の一覧と、機能ごとの自動テストの対応。
拡張提案は [`PROPOSALS.md`](./PROPOSALS.md)、変更履歴は [`CHANGELOG.md`](./CHANGELOG.md)、
Rust 実装の到達点は [`RUST_PARITY.md`](./RUST_PARITY.md) を参照。

## 概要
pdfg2d は、Java の `Graphics2D`/独自 `GC` API を PDF 出力へ変換するためのマルチモジュールライブラリです。単純な図形描画だけでなく、PDF バージョン制御、準拠プロファイル（PDF/A・PDF/X・PDF/VT・PDF/UA）、メタデータ、暗号化、添付、リンク、Open Action、Viewer Preferences、SVG、画像埋め込み、複雑なテキストレイアウト、面付け・トンボ、スポットカラーまで扱えます。

## モジュール構成
- `pdfg2d-core`: 描画 API、色（スポットカラー含む）、ペイント、テキストレイアウト、フォント抽象、面付けプリミティブ（`Trims`/`PagePlacement`/`PrinterMarks`）、`ParallelPageRenderer`、ユーティリティ。
- `pdfg2d-pdf`: PDF 生成本体、PDFWriter、ページ出力、画像埋め込み、注釈、暗号化、面付け（`SinglePagePDFImposition`/`GridPDFImposition`）、各種 PDF 設定。
- `pdfg2d-font`: TrueType/OpenType/CFF/WOFF 読み込みとグリフ解析。
- `zstream-io`: 分割出力、テンポラリ出力、ストリーム出力。
- `zstream-resolver`: `file:` `data:` `zip:` を含む Source/Resolver 層と制限付きリゾルバ。
- `pdfg2d-svg`: Batik ベースの SVG 描画連携。
- `pdfg2d-svg-emoji`: 絵文字フォント支援。既定ビルドでは無効で、`-PincludeEmojiFonts=true` で有効化します。
- `pdfg2d-demo`: 実行サンプルと統合テスト（veraPDF 検証を含む）。

## PDF 文書機能
- **PDF バージョン**: PDF 1.2〜1.7 および PDF 2.0。Linearized PDF（Fast Web View）。
- **アーカイブ/アクセシビリティ**: PDF/A-1b、2b/2u/2a、3b/3u/3a、4/4f、PDF/UA-1
  （すべて veraPDF 自動検証テスト付き）。
  - タグ付き PDF: `PDFPageOutput.beginStructElement/endStructElement` による明示構造 +
    自動タグ（テキスト=P、画像=Figure+Alt、図形=Artifact）。
  - PDF/A-3/4f は添付ファイル（AFRelationship / catalog AF 配列）に対応。
- **プリプレス**: PDF/X-1a、PDF/X-4、PDF/X-6、PDF/VT-1。
  - CMYK 強制（X-1a。X-4/X-6 は RGB プロファイル設定時 RGB 入稿ワークフローを維持）、
    TrimBox/ArtBox 排他・包含検証、OutputIntent（RegistryName / Info / 印刷条件設定 API
    `PDFParams.withOutputIntent`）。
  - PDF/VT: `PDFWriter.nextDocumentPart(metadata)` によるレコード単位の DPart 階層
    （DPM メタデータ付き）。
- **色**:
  - カラーモード RGB / Gray / CMYK（変換込み）、CMYK オーバープリントフラグ。
  - **スポットカラー**: `SpotColor`（Separation 色空間、名前 + 代替色 + tint +
    オーバープリント、レジストレーションカラー /All）。Graphics2D からは `SpotPaint`。
  - **ICCBased RGB**: `PDFParams.withSRGBProfile` / `withRGBProfile` で RGB コンテンツを
    ICC 管理色として出力。`withRenderingIntent` で既定レンダリングインテント（`ri`）。
- **レイヤー（OCG）**: `createOptionalContentGroup(name, viewable, printable, initiallyOn,
  locked)` + `PDFGC.beginLayer/endLayer` + `PDFGroupImage.setOCG`。
  印刷時のみ / 画面のみ表示のウォーターマーク（ViewState/PrintState Usage）を含む。
- **面付け・トンボ**: 1面付け（トンボ + ページボックス自動設定）、N-up、同一面付け（名刺等）、
  中綴じ（左右綴じ・クリープ補正）、カット&スタック。論理ページを Form XObject として
  即時ストリーム出力するためメモリ一定。
- **ファイルサイズ・性能**: Deflate レベル設定（`withDeflateLevel`）、
  オブジェクトストリーム + XRef ストリーム（`withObjectStreams`）、
  文書間フォントサブセットキャッシュ（決定的サブセットタグ）、
  `ParallelPageRenderer` によるページ描画の並列化（出力は直列とバイト一致）。
- 文書情報として `Title` `Author` `Subject` `Keywords` `Creator` `Producer` を設定できます。
- Viewer Preferences を設定できます。
  例: 非フルスクリーン時のページモード、用紙サイズに応じた給紙、表示/印刷領域、印刷倍率、コピー数、印刷対象ページ範囲。
- Open Action として JavaScript を埋め込めます（PDF/A・PDF/X では拒否）。
- しおり、名前付き宛先、URI/文書内リンク注釈を追加できます。
- ファイル添付を埋め込めます（PDF/A-1・PDF/X では拒否）。
- 権限制御付き暗号化を設定できます。
  例: RC4/AES-128、ユーザーパスワード、オーナーパスワード、印刷/コピー/編集権限。
- PDF 本文と画像の圧縮方式を切り替えられます。

## 描画機能
- `PDFGraphics2D` により `java.awt.Graphics2D` 互換の描画ができます。
- 図形塗りつぶし、線描画、クリッピング、破線、変形、複数ページ出力に対応します。
- `PDFGC` により低レベル描画 API を直接利用できます。
- 透明色、塗り/線の alpha、透明グループ、グループ画像を扱えます。
- タイリングパターン、軸型・放射型シェーディング（マルチストップ、Type 2/3 関数、
  文書内キャッシュ）。
- グラデーションのアルファ（RGBA ストップ）を輝度ソフトマスクで再現
  （透明可のプロファイルのみ。PDF/A-1b・X-1a ではアルファを破棄）。
- テキストモードとして `FILL` `STROKE` `FILL_STROKE` を使えます。
- ラスタ画像を `BufferedImage` または Source から埋め込めます
  （Flate / JPEG / JPEG2000、独自 PNG デコーダ、CMYK JPEG、URI・インスタンス単位の重複排除）。
- SVG を PDF に描画できます。
- Java2D ブリッジ経由で通常の AWT 描画コードを PDF 出力へ流せます。

## テキスト・フォント機能
- Core 14 フォントを利用できます。
- CID-Keyed Fonts を利用でき、日本語・中国語・韓国語系文字へ対応します。
- TrueType、OpenType/CFF、WOFF の埋め込みフォントを読み込め、実バイナリサブセット化します
  （ToUnicode、CIDSet。サブセット幅の整合は veraPDF で検証済み）。
- cmap format 0/2/4/6/8/10/12/13、kern テーブル。
- 日本語/英語の改行規則（禁則）を伴うテキストレイアウトを利用できます。
- 横書き・縦書き（Identity-V）、字形配置、強調、斜体、太字、複合テキスト描画に対応します。
- ページ幅に基づく段落レイアウト、行送り、両端揃え、段組、フロート、letter-spacing。
- 絵文字フォント（専用モジュール）。

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

全 228 テスト（2026-07-11 時点）。veraPDF（greenfield）をテスト依存として組み込み、
PDF/A 全プロファイルと PDF/UA-1 を毎ビルド検証しています。

### PDF 文書機能
- PDF 1.2 から 1.7、PDF/A-1b、PDF/X-1a: テスト有。`PDFVersionTest`
- PDF/A 全プロファイル（1b〜4f）の veraPDF 検証 + 5 フォントパラメタライズ: テスト有。`PDFAVeraPDFComplianceTest`
- PDF/X-1a/X-4 の規則ベース検証（CMYK 強制、ボックス、OutputIntent、pdfxid）: テスト有。`PDFXConformanceTest`
- タグ付き PDF / PDF/UA-1（veraPDF PDFUA_1 含む）: テスト有。`TaggedPDFTest`
- スポットカラー、ICCBased + レンダリングインテント、レイヤー、VT レコード DPart、
  カット&スタック: テスト有。`CommercialPrintTest`
- 面付け（1面付け・N-up・中綴じ・クリープ）: テスト有。`ImpositionTest`
- オブジェクトストリーム + XRef ストリーム: テスト有。`ObjectStreamTest`
- Deflate レベル: テスト有。`DeflateLevelTest`
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
- グラデーションのアルファ（SMask）: テスト有。`GradientAlphaTest`
- テキストモード `FILL` `STROKE` `FILL_STROKE`: テスト有。`DemoFeatureCoverageTest`
- ラスタ画像 `BufferedImage` 埋め込み: テスト有。`DemoFeatureCoverageTest`
- ラスタ画像 `Source` 埋め込み: テスト有。`DemoFeatureCoverageTest`
- Gradient / Pattern paint: テスト有。`DemoFeatureCoverageTest`
- Java2D ブリッジ経由の描画: テスト有。`DemoFeatureCoverageTest`

### テキスト・フォント機能
- Core 14 フォント: テスト有。`DemoFeatureCoverageTest`
- CID-Keyed Fonts: テスト有。`DemoFeatureCoverageTest`
- TrueType、OpenType/CFF、WOFF 読み込み: テスト有。`FontFileTest`
- フォントサブセットキャッシュ（決定的出力・文書間バイト一致）: テスト有。`FontSubsetCacheTest`
- 日本語/英語の改行規則を伴うテキストレイアウト: テスト有。`DemoFeatureCoverageTest`
- 横書き・縦書き、字形配置、強調、斜体、太字、複合テキスト描画: テスト有。`DemoFeatureCoverageTest`, `TextRenderingTest`
- ページ幅に基づく段落レイアウト、行送り、両端揃え: テスト有。`DemoFeatureCoverageTest`

### モジュール別 API / 基盤機能
- `pdfg2d-core` 描画 API、Recorder/NoOp、paint 値オブジェクト: テスト有。`CoreApiTest`
- `ParallelPageRenderer`（直列出力とのバイト一致・速度）: テスト有。`ParallelPageTest`
- 生成ベンチマーク: テスト有。`GenerationBenchmarkTest`（deflate が生成時間の約 27% という実測を保持）
- `zstream-io` 分割出力、パッチ書き戻し: テスト有。`FragmentedOutputTest`
- `zstream-resolver` `file:` `data:` `zip:` を含む Source/Resolver: テスト有。`ResolverApiTest`

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
