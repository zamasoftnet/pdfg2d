# 変更履歴

pdfg2d の機能追加・改善の実施記録。提案と計画は [`PROPOSALS.md`](./PROPOSALS.md)、
実装済み機能の一覧は [`FEATURES.md`](./FEATURES.md) を参照。

## 2026-09-04 — 双方向テキストの論理出力 API（ActualText・`/K` 論理順・鏡像 CID alias）

- foliojet の段落単位 UBA（視覚順で glyph run を描く）に対し、抽出・アクセシビリティへ**論理順**を
  渡すための API を追加（設計は copperpdf4 `docs/bidi-logical-output-spike.md`）。API 未使用時の出力は
  バイト同一。
- 汎用 GC: `GC.beginTextReplacement(String logicalText)`（`State`、既定 no-op）。閉じるまでに描いた内容を
  意味上その文字列で置き換える。`RecorderGC` は `BeginTextReplacement`/`EndTextReplacement` を記録し、
  Form への再生では 1 回再現、ラスタ再生では破棄（ラスタ化テキストは非検索）。
- PDF: `PDFGraphicsOutput.beginActualText/endActualText` が `/Span <</ActualText <UTF-16>>> BDC … EMC` を書く
  （PDF 1.5 未満と入れ子は no-op）。`PDFGC.beginTextReplacement` はこれで実装し、タグ付きマーク・Artifact・
  レイヤーの BDC/EMC と正しく入れ子になる。**既定では no-op**——`PDFParams.withActualTextReplacement(true)` で
  opt-in。foliojet 側の実測（PyMuPDF/pypdfium2）で、PDFium は ActualText 無しの方が視覚順 glyph から論理順を
  正しく復元し、行単位 ActualText を与えると視覚順に崩れることが分かったため（同日追記）。
- タグ付き PDF: `StructureOrder(blockOrdinal, logicalStart, tieBreaker)` と
  `PDFPageOutput.beginStructContent(StructureRef, StructureOrder)`。MCID 番号と ParentTree は描画順のまま、
  各構造要素の `/K` だけを（order, 描画順）で並べる。Elem/MCR/OBJR を共通の ordered slot で扱うので
  Link の OBJR も相対順を保つ。ヒント無しなら従来どおり。
- フォント: `Font.toGID(int displayCodePoint, int logicalCodePoint, FontFeatureSet)`（既定は表示側の
  `toGID`）。埋め込み CID subset は既存の `semanticVariant` 鍵で（表示 GID, 論理文字）ごとに別 CID を採番し
  ToUnicode を論理文字にする（鏡像括弧を pdf.js のように ActualText を無視する抽出器でも正しく）。Identity
  CID は CID=GID なので alias 不可（表示 GID のまま。bidi 文書には埋め込み subset を推奨）。
- テスト: `TextReplacementTest`、`StructureOrderTest`、`OpenTypeEmbeddedCIDFontSubsetTest`（alias）、identity
  フォントの alias 不可。

## 2026-07-12 — タグ付き PDF の上流接続（foliojet）

- foliojet が HTML 構造をタグ付き PDF の論理構造ツリーへ流し込むようになった
  （`output.pdf.tagged`、既定 OFF、A-2a/A-3a/UA-1 で自動 ON）。
- 仕組み: 描画パスのボックス描画に**ゼロサイズの構造マーカー**
  （`StructDrawable`）を文書順で挿入し、ペイント時に
  `beginStructElement`/`endStructElement` を呼ぶ。pdfg2d が内容を自動マーク
  （text/Figure/Artifact）するため、マーカー間に描かれた内容が対応する構造要素に
  付く。`AbstractBlockBox` と各テーブルボックスの `draw` に実装。
- ロール対応（`TaggedPdf.blockRole`）: 見出し H1–H6、P、Div/Sect、L/LI、
  BlockQuote、Table/TR/TH/TD/TBody/Caption。
- **精緻化（PDF/UA-1 適合）**:
  - 要素の同一性で重複ネストを排除（`PageBox` が開いた要素集合を管理）。
    リスト項目のように 1 要素が複数ボックスに分かれても構造要素は 1 つになる。
  - **LI は LBody を内包**（PDF/UA 7.2）。
  - **TH は Scope 属性**を付与（PDF/UA 7.5、HTML `scope` 属性・既定 Column）。
  - フォーム有効時は**フォームウィジェットを `Form` 構造要素に内包**（PDF/UA
    7.18.4）。
  - **リンクを `Link` 構造要素に内包し、注釈に `/Contents`（代替説明、リンク
    テキストまたは URI）を付与**（PDF/UA 7.18.5、7.18.1）。
  - **画像を `Figure` 構造要素に内包し `/Alt`（HTML `alt`）を付与**（img/svg/
    object）。従来は画像が包含段落の `/Alt` を汚染していたのを修正。
  - 要素マッピング拡張: figcaption→Caption。
- 検証: `PdfUaValidationTest` が veraPDF PDF/UA-1 で、見出し・段落・リスト・表に
  加え**フォーム入力・画像・リンクを含む文書**の適合を実証。
- **読み順位置**: `Drawer` を `Visitor.visitBox` に渡し、リンク注釈とフォーム
  フィールドをペイント時に文書順で発行（`PDFOutputDrawable`）。これにより
  `Link`/`Form` 構造要素が包含ブロック配下にネストする（従来は文書直下に集約）。
  copperpdf imageTest 591/0（レンダリング非破壊）で確認。
- **マーカーは非表示なのでレンダリング非破壊（imageTest 安全）、既定 OFF により
  未タグ文書の出力は Copper PDF 3.2 と完全同一**。
- 検証: **`PdfUaValidationTest` が veraPDF で PDF/UA-1 適合を実証**（見出し・段落・
  リスト・表を含む文書）。`OutputPdfProfileTest` で `/S /H1`・`/P`・`/L`・`/LI`・
  `/Table`・`/TR`・`/TH`・`/TD` をバイト検証。foliojet 全テスト緑。
- 残: ページまたぎ要素の単一化、フォームウィジェットの構造帰属、リンクの
  インライン構造、Figure alt、より広い要素マッピング。

## 2026-07-12 — フォームの製品統合（foliojet）

- foliojet に `output.pdf.forms`（既定 OFF のオプトイン）を追加。有効時、HTML の
  フォーム部品を pdfg2d の対話フォームフィールドとして出力する
  （`AbstractVisitor.visitBox` で検出 → `PDFVisitor`）:
  - text/password → `TextField`、checkbox → `CheckBoxField`、
    submit/reset/button → `PushButtonField`、textarea → 複数行 `TextField`
  - **radio → `RadioGroup`**: 同名のラジオを 1 つの親フィールド + `/Kids`
    ウィジェットにまとめ、相互排他を保証（pdfg2d に `RadioGroup` レコードと
    `PDFPageOutput.addRadioGroup()` を新設）
  - **select → `ChoiceField`**: `<option>` をビジター走査で収集し、選択値と
    combo/list を反映（`endPage` で発行）
  - PDF/X はフォーム禁止のため自動スキップ
- **既定 OFF によりフォームを含まない文書の出力は Copper PDF 3.2 と完全同一**。
  有効時のみフォーム部品の見た目が対話ウィジェットに変わる。
  `_9510_FORM/FormFieldTest`（foliojet バイト検証）、`AcroFormTest`
  （pdfg2d, PDFBox でラジオグループを検証）でテスト、両プロダクト全テスト緑。
- 残: 外観ストリームの精緻化、タグ付き文書での `Form` 構造要素併発。

## 2026-07-12 — AcroForm（対話フォーム）と電子インボイス（Factur-X）

- **AcroForm 対話フォームフィールド**（`pdf.form`、新設）: `FormField` sealed
  interface + レコード `TextField`/`CheckBoxField`/`ChoiceField`/
  `PushButtonField`。`PDFPageOutput.addFormField()` が Widget 注釈 + Catalog
  `/AcroForm`（`/FT` Tx/Btn/Ch、`/T`/`/V`/`/TU`/`/Ff`/`/DA`/`/DR`、標準
  Helvetica/ZapfDingbats フォント、チェックボックス on/off 外観ストリーム、
  NeedAppearances）を発行。PDF/X では拒否（`UnsupportedOperationException`）。
  タグ付き文書では B3 の `associateAnnotation()`（OBJR + `/StructParent`）で
  構造木に関連付け。PDFBox でテキスト値・チェック状態・選択肢・必須/ツール
  チップを検証（`AcroFormTest`）。
- **電子インボイス（Factur-X / ZUGFeRD）埋め込みプロファイル**: `Attachment`
  に `afRelationship`（`/Alternative` 等、PDF/A-3 の関連付けファイル）を追加。
  `FacturX` ディスクリプタ（`PDFMetaInfo.setFacturX()`）を設定すると、
  `XMPMetadataWriter` が Factur-X の `fx:` スキーマ（`DocumentType`/
  `DocumentFileName`/`Version`/`ConformanceLevel`）と、それを PDF/A 検証器に
  受理させる PDF/A 拡張スキーマ宣言を出力する。veraPDF で PDF/A-3B 準拠を
  検証（`PDFAVeraPDFComplianceTest#testFacturXInvoice`）。請求書 XML の中身
  （CII/UBL）の生成は呼び出し側の責務でスコープ外。

## 2026-07-12 — bidi の製品統合

- `TextImpl.reverse()` を追加（RTL ランを視覚順に配置するためのグリフ反転コピー、
  クラスタ保持、単体テスト付き）。
- foliojet 側で `AbstractLineBox.align()` に UAX #9 視覚順並べ替え + RTL ラン反転を
  実装。純 LTR 行では厳密 no-op（`Bidi.isLeftToRight()` ガード）のため既存の
  レンダリング（Copper PDF 3.2 レイアウト）を保持。pdfg2d 253 / foliojet 367 /
  copperpdf 591 画像で LTR 出力の不変を実証。
- 保留: ハイフネーション・ルビの foliojet 統合（既存ゴールデンを変えるため、
  3.2 レイアウト維持の制約下では見送り）。

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
