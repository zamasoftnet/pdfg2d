# 変更履歴

pdfg2d の機能追加・改善の実施記録。提案と計画は [`PROPOSALS.md`](./PROPOSALS.md)、
実装済み機能の一覧は [`FEATURES.md`](./FEATURES.md) を参照。

## 2026-07-12 — テキスト再設計（§C）と OpenType シェーピング

- **段落パイプライン**（`gc.text.pipeline`、新設）: GlyphRun（HarfBuzz 型・
  x/y 両軸の advance/offset）、Paragraph/Item、Itemizer、Shaper（既存グリフ選択を
  再利用）、BreakNode（Box/Glue/Penalty）、LineBreaker（貪欲）、ParagraphLayout、
  PipelineTextDrawer。既存出力を壊さず coexist する新基盤。
- **C6 bidi**（UAX #9）: `java.text.Bidi` でレベル解決 + L2 視覚並べ替え。
- **C5 ハイフネーション**: Liang アルゴリズム（TeX パターン）+ flagged Penalty による
  ハイフン字形挿入。
- **C8 ルビ**: 第一級ノード。max(base, ruby) 幅の結合ボックス、mono 配分、行高拡張。
- **C7 GSUB リガチャ + GPOS ペアカーニング**: pdfg2d-font のパーサを改修
  （`LigatureSubstFormat1` に Coverage を保持、GPOS `PairPos`〔format 1/2〕と
  `ClassDef` パーサを新規実装、`Coverage.getGlyphIds()` を追加）。core の
  `OpenTypeFont` が `liga`/`kern` フィーチャを読み、埋め込みサブセットフォントは
  グリフ ID を翻訳して適用。fi リガチャ・// カーニングを実フォント（フリーフォント
  のサブセット）で検証。
- **C9 カラーフォント（COLR/CPAL）**: `ColrTable`（v0）・`CpalTable` パーサを新規実装。
  `ColorGlyphFont` インターフェースを追加し、`OpenTypeFont` がカラーグリフの各レイヤーを
  CPAL パレット色で塗り重ねて描画（テキスト色レイヤーは現在の塗り色を使用）。
  `PDFTextRenderer` はカラーグリフを含むランをアウトライン塗り経路へ回送。
  2 レイヤー（赤/青）のカラーグリフを実描画し PDFBox で色を検証。

### フォントパーサの改善（付随）

- `LigatureSubstFormat1` が Coverage を破棄していた不具合を修正（GSUB リガチャが
  引けるようになった）。
- GPOS のルックアップサブテーブルが未解析だった（type 2 PairPos を実装）。
- `Coverage` にグリフ列挙 API（`getGlyphIds()`）を追加。

## 2026-07-11 — GC API の再設計（破壊的変更）

- **`GC.begin()` が AutoCloseable な `GC.State` を返すようになり、`GC.end()` は削除**。
  状態の保存/復元(q/Q)の対応が型で強制され、try-with-resources で書ける:
  `try (var state = gc.begin()) { ... }`。保存と復元がレキシカルスコープを
  共有できない場合(ページの開始と終了が別メソッド等)は `State` を保持して
  明示的に `close()` する。`close()` は冪等(2 回目以降は無視)。
- 全実装(`PDFGC`・`G2DGC`・`NoOpGC`・`RecorderGC`)と全呼び出し箇所
  (pdfg2d 約 40、foliojet 約 150)を移行。`RecorderGC` の再生も State
  スタックで対応。
- `GC.LineJoin` の定数フィールドを `j` から `code` に改名(`LineCap`/`TextMode` と統一)。
- `setStrokeAlpha`/`setFillAlpha` に `throws GraphicsException` を宣言し、
  状態アルファと `RGBAColor`(ペイント側アルファ)の関係を Javadoc に明文化
  (両者は同一チャンネルで後勝ち)。

## 2026-07-11 — 商業印刷機能（スポットカラー・ICC・レイヤー・VT/面付け発展）

- **スポットカラー（Separation 色空間）**: `SpotColor`（名前 + 代替色 + tint +
  オーバープリント、`REGISTRATION`〔/All〕定数付き）を `Color` 階層に追加。
  同一カラーラント（版）は文書内で 1 つの色空間オブジェクトを共有。
  Graphics2D ブリッジ用の `SpotPaint`（AWT 描画時は代替色で表示）。
  PDF/A では Separation の代替色を OutputIntent の色空間に整合させる。
- **ICCBased RGB 色空間 + レンダリングインテント**: `PDFParams.withRGBProfile` /
  `withSRGBProfile`（sRGB v2 プロファイル同梱）で RGB コンテンツを ICCBased で出力。
  `withRenderingIntent` で既定インテント（`ri` オペレータ）を指定。
  **PDF/X-4/X-6 で RGB プロファイル設定時は CMYK 変換せず RGB 入稿ワークフローを維持**
  （X-1a は常に CMYK 強制。判定は `PDFParams.effectiveColorMode()`）。
- **OCG レイヤー API の一般化**: `PDFWriter.createOptionalContentGroup(name,
  viewable, printable, initiallyOn, locked)` + `PDFGC.beginLayer/endLayer`（/OC BDC）+
  `PDFGroupImage.setOCG`。OCProperties に ON/OFF/Locked 配列を出力。
  従来のウォーターマーク用フラグは同 API へ委譲（PDF/A では /AS 用法辞書を抑止）。
- **PDF/VT のレコード単位 DPart**: `PDFWriter.nextDocumentPart(Map metadata)` で
  レコード境界を宣言し、DPM メタデータ付き DPart 葉ノードをページ範囲に対応付け。
- **カット&スタック面付け**: `GridPDFImposition.Order.CUT_AND_STACK`
  （断裁後に山を重ねると通し順になる配置）。
- **Foliojet**: CSS 拡張関数 `-cssj-spot(名前, 代替色 [, tint%] [, overprint])` を追加。

## 2026-07-11 — グラデーション SMask + 性能・健全性

- グラデーションのアルファ付きストップを輝度ソフトマスク（/SMask /Luminosity +
  DeviceGray シェーディング Form）で再現。PDFBox レンダリングと veraPDF (A-2b) で検証。
  透明不可のプロファイル（PDF/A-1b・X-1a）では従来どおりアルファを破棄。
- Deflate 圧縮レベル設定: `PDFParams.withDeflateLevel(-1|0..9)`（全ストリームに適用）。
- オブジェクトストリーム + XRef ストリーム（PDF 1.5+）: `PDFParams.withObjectStreams(true)`。
  構造ツリーの小オブジェクトを /ObjStm にパックし、type 0/1/2 エントリの
  クロスリファレンスストリームで出力（暗号化・Linearized とは排他）。
- 文書間フォントサブセットキャッシュ: サブセットタグを内容ハッシュ由来に変更
  （決定的出力）し、生成済み CFF プログラムを弱参照キャッシュ。
- `ParallelPageRenderer`（pdfg2d-core）: ページ描画を RecorderGC へ並列実行し
  呼び出しスレッドで順序どおり再生。実測 4 コアで 2.8 倍、出力は直列とバイト一致。
- Batik 依存の分離: グラデーション変換を pdfg2d-svg の `BatikPaintUtils` へ移動し
  pdfg2d-pdf から batik-gvt 依存を除去（radial focus 座標 fx/fy 取り違えも修正）。
- `PDFGC.drawText` を `PDFTextRenderer` へ抽出。
- veraPDF 回帰を 5 フォント（和・韓・タイ、TTF/CFF-OTF）× PDF/A-2b に拡大。

## 2026-07-11 — 面付け・トンボの移植（Foliojet → pdfg2d）

- Foliojet から移植・拡張: `PrinterMarks`（コーナー/センター/背トンボ・ノンブル）、
  `PagePlacement`、`Trims`（pdfg2d-core、汎用 GC ベース）、
  `SinglePagePDFImposition`（トンボ + ページボックス自動設定）、
  `GridPDFImposition`（行×列無制限。N-up / 同一面付け / 中綴じ〔左右綴じ・クリープ補正〕）。
- 論理ページを Form XObject として即時ストリーム出力するため中綴じでもメモリ一定
  （FragmentedOutput の設計意図の実証）。
- Foliojet 側は委譲化し全 367 テスト green を維持。
- 意図的に対象外: 折り丁面付け（折りスキーム + 本格クリープ）は専門 RIP の領域。

## 2026-07-11 — PDF/A・PDF/X 堅牢化 + プロファイル全対応

- P0 準拠バグ修正: `pdfaid:conformance="B"`（"A" 誤宣言）、XMP への XML 宣言混入
  （JDK Transformer 起因 → 手書きシリアライズ化）、CFF サブセッタのスペース幅欠落、
  バイナリマーカー範囲（>127 保証）、PDF/X の暗号化 / OpenAction 拒否、
  A-1b/X-1a での透明グループ抑止。
- veraPDF（greenfield）をテスト依存として組み込み、PDF/A 全プロファイルと
  PDF/UA-1 を毎ビルド検証。
- OutputIntent 設定 API（`PDFParams.withOutputIntent`: 印刷条件識別子 / RegistryName /
  Info / ICC プロファイル埋め込み）、X-1a の CMYK 強制・TrimBox/ArtBox 排他 + 包含検証。
- プロファイル全対応: PDF/A-2b/2u/2a、3b/3u/3a、4/4f、PDF/X-4/X-6、PDF/VT-1、
  PDF 2.0 出力、タグ付き PDF（StructTreeRoot / ParentTree / 明示構造 API +
  自動タグ）、PDF/UA-1（`Version` 列挙をメタデータ駆動に再設計）。

## 2026-07-10 — 品質改善第1弾

- cmap format 10 実装、TODO の制限明文化、`PDFWriterImpl` / `PDFGC` の大規模分割。
- java.awt マルチストップグラデーションが単色になるバグ修正。
- `SecureRandom` 化、`writeName` キャッシュ、`writeString` バッファ化、
  Deflater 出力バッファ拡大。
- テスト 80→147（cmap 全フォーマット、全グリフデコード、PDF プリミティブ等）。
