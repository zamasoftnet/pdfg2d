# pdfg2d 拡張提案

作成日: 2026-07-10 / 最終更新: 2026-07-12。HTML+CSS 組版エンジン
（foliojet / copperpdf）の基盤としての**未実装の**拡張提案をまとめる。

- 実装済み機能の一覧は [`FEATURES.md`](./FEATURES.md)
- 実施済みの記録（変更履歴）は [`CHANGELOG.md`](./CHANGELOG.md)
- PDF/A・PDF/X の準拠計画と実施記録は [`PDFA_PDFX_PLAN.md`](./PDFA_PDFX_PLAN.md)
- テキストパイプライン再設計の設計は [`TEXT_PIPELINE_REDESIGN.md`](./TEXT_PIPELINE_REDESIGN.md)

> **2026-07-12 時点**: 旧 §A〜§D（DeviceN、PDF/X 注釈位置検証、タグ付き PDF の
> 意味構造、AES-256、ハイフネーション、bidi、GPOS/GSUB、ルビ、COLR/CPAL、
> ObjStm パック拡大）は **pdfg2d 側ではすべて実装済み**。詳細は CHANGELOG 参照。
> 本ファイルは残る「未実装」だけを列挙する。



HTML から「紙の帳票の代替となる PDF」を生成する文脈で価値の高い機能群。
いずれも**レンダリング(見た目)を変えずに追加できる**か、既存基盤の拡張で
実現でき、Copper PDF 3.2 レイアウト維持の制約と両立しやすい。

- **PDF フォーム（AcroForm）** — ✅ pdfg2d 実装済み（2026-07-12） / 製品統合が残
  HTML の `<input>`/`<textarea>`/`<select>`/チェック/ラジオを、入力可能な
  PDF フォームフィールドに反映する。
  **pdfg2d 側**は `pdf.form`（`FormField` sealed interface + `TextField`/
  `CheckBoxField`/`ChoiceField`/`PushButtonField`）と
  `PDFPageOutput.addFormField()` を実装済み。Widget 注釈 + Catalog
  `/AcroForm`（`/FT` Tx/Btn/Ch、`/T`名前、`/V`値、`/AP`外観、`/DA`/`/DR`、
  チェックボックス外観ストリーム、`/Ff` フラグ）を発行し、PDF/X では拒否、
  タグ付き文書では B3 の `associateAnnotation()`(OBJR + /StructParent) で
  構造木に関連付ける（`AcroFormTest` で PDFBox により検証）。
  **残る作業（製品）**: foliojet がフォーム部品を静的画像（`CheckBoxImage` 等）
  として描画している箇所を `addFormField()` 呼び出しに置き換える。
  **外観ストリームを現在の静的描画と一致させれば見た目は不変**(imageTest 安全)
  のまま入力層だけを足せる。→ セクション A に統合作業として記載。
  なお **署名フィールドの配置**（空の `/Sig` Widget を置き、後から署名させる）
  は生成時の関心事だが未実装。一方 **署名する行為そのもの**（`/ByteRange`
  ハッシュ + CMS 埋め込み、証明書・鍵・HSM 管理、PAdES-LTV）は生成後の後処理で
  あり **本プロジェクトのスコープ外**（「意図的に対象外」参照）。
- **電子インボイス（Factur-X / ZUGFeRD）の埋め込みプロファイル** — ✅ pdfg2d 実装済み（2026-07-12）
  PDF/A-3 に機械可読な請求書 XML（Factur-X/ZUGFeRD の CII 等）を埋め込む。
  日本の適格請求書・電子インボイス対応にも直結。
  **pdfg2d 側**は `Attachment` に `afRelationship`（`/Alternative` 等）を追加し、
  `FacturX` ディスクリプタ（`PDFMetaInfo.setFacturX()`）で XMP の Factur-X 拡張
  スキーマ（`fx:DocumentType`/`DocumentFileName`/`Version`/`ConformanceLevel`
  + PDF/A 拡張スキーマ宣言）を出力する。veraPDF で PDF/A-3B 準拠を検証済み
  （`PDFAVeraPDFComplianceTest#testFacturXInvoice`）。
  ただし **請求書 XML の中身（CII/UBL の構造化データ）を生成するのはスコープ外**：
  それは構造化データを持つ呼び出し側アプリの責務で、pdfg2d は「呼び出し側が
  用意した XML を Factur-X 準拠で埋め込む」ところまでを担う。Peppol BIS /
  JP PINT の UBL 埋め込みも同じ仕組み（呼び出し側が UBL を用意）で対応可能。
- **ページラベル（/PageLabels）**
  「表紙・i・ii・1・2…」のような論理ページ番号。報告書・帳票の目次や参照に有用。
  Catalog `/PageLabels` 数ツリーの出力のみで、レンダリング非破壊の小規模追加。
- **フォームのデフォルト値**（AcroForm の一部）— ✅ pdfg2d 実装済み
  各フィールドの初期値（`/V`）は各 `FormField` レコード（`TextField.value`、
  `CheckBoxField.checked`、`ChoiceField.selected`）で生成時に設定できる。

> バーコード / QR は foliojet に既存（`BarcodeImage`）。JavaScript による
> フィールド計算（自動合計等）は PDF/A で禁止のため既定オフの任意対応とする。
> **FDF/XFDF によるデータ入出力**や**記入済みフォームのフラット化**は、生成では
> なくフォーム処理・後処理なのでスコープ外（「意図的に対象外」参照）。

## A. 製品（foliojet / copperpdf）へのテキスト機能統合

pdfg2d 側の実装は完了しているが、製品の CSS 組版エンジンへ通す作業が残る項目。
**制約: 現時点では Copper PDF 3.2 のレイアウトを維持する必要があるため、既存の
レンダリング（ゴールデン画像）を変える改修は保留**。

1. **bidi の製品統合** — ✅ 実装済み（2026-07-12）
   `AbstractLineBox.align()` に UAX #9 視覚順並べ替え + RTL ラン反転を追加。
   純 LTR 行では厳密 no-op のため 3.2 レイアウトを保持。foliojet 367 /
   copperpdf 591 画像で検証済み。残: 方向境界をまたぐネストしたインライン
   ボックスの分割（ブラウザ級 bidi の残り）。
2. **ハイフネーション（foliojet）** — 保留（3.2 レイアウト維持のため）
   pdfg2d の `Hyphenator`（Liang）は実装済み。foliojet 側は CSS `hyphens`
   プロパティのパース、単語コンテキスト付き分割点挿入、行末ハイフン描画を
   二パス行形成リプレイに追加する必要がある。`hyphens: auto` ゲートで既存
   ゴールデンは保護できるが、既存レンダリングを変える性質のため現時点は保留。
3. **ルビの発展（foliojet）** — 保留（3.2 レイアウト維持のため）
   foliojet の `RubyBox`（inline-block + nowrap）は基本描画のみ。ルビ掛け
   （JIS X 4051）・熟語ルビの行またぎ・Ruby/RB/RT タグ付き構造が未対応。
   いずれも既存ジオメトリを変える（ルビテストは幅を許容 0 で断言）。
10. **AcroForm の製品統合（foliojet）** — ✅ 実装済み（2026-07-12）
   `output.pdf.forms`（既定 OFF、フォーム部品を対話フィールド化するオプトイン）
   を追加。`AbstractVisitor.visitBox` がフォーム部品を検出し、`PDFVisitor` が
   pdfg2d の `pdf.form` フィールドを発行:
   - `<input type=text/password>` → `TextField`
   - `<input type=checkbox>` → `CheckBoxField`
   - `<input type=radio>` → **`RadioGroup`（同名をまとめ、親フィールド + `/Kids`
     ウィジェットの単一ラジオフィールドとして出力）**
   - `<select>` → `ChoiceField`（`<option>` 子要素をビジター走査で収集し、
     選択値・combo/list を反映。`endPage` でまとめて発行）
   - `<textarea>` → 複数行 `TextField`
   - `<input type=submit/reset/button>` → `PushButtonField`
   PDF/X ではフォーム禁止のため自動スキップ。**既定 OFF なのでフォームを含まない
   文書の出力は 3.2 と 1 バイトも変わらず**、有効時のみフォーム部品の見た目が
   対話ウィジェットへ変わる。`_9510_FORM/FormFieldTest`（foliojet）+
   `AcroFormTest`（pdfg2d, PDFBox でラジオグループ検証）でテスト済み、両プロダクト
   全テスト緑。残: 外観ストリームの精緻化、タグ付き文書での `Form` 構造要素 +
   `/TU` 同時発行（B4 と一体）。

## B. アクセシビリティ

4. **タグ付き PDF の上流接続（foliojet）** — ✅ v1 実装済み（2026-07-12）
   pdfg2d 側 API（`beginStructElement`/`endStructElement`、内容の自動マーク
   〔text/Figure/Artifact〕、表 Scope、Link の OBJR 関連付け、見出しレベル検証）
   に加え、**foliojet が HTML 構造をタグツリーへ流し込むようになった**。
   `output.pdf.tagged`（既定 OFF、A-2a/A-3a/UA-1 では自動 ON）有効時、描画パスの
   ボックス描画に構造マーカー（`StructDrawable`、ゼロサイズ・描画順＝文書順）を
   挿入し、pdfg2d の自動マークした内容が対応する構造要素に付く仕組み。
   対応ロール: 見出し H1–H6、P、Div/Sect、L/LI、BlockQuote、Table/TR/TH/TD/
   TBody/Caption（`TaggedPdf.blockRole`）。`AbstractBlockBox` と各テーブル
   ボックスの `draw` で発行。要素の同一性で重複ネストを排除（`PageBox` が開いた
   要素集合を管理。リスト項目など 1 要素が複数ボックスに分かれても構造要素は 1 つ）。
   PDF/UA 準拠のため **LI は LBody を内包**、**TH は Scope 属性**（HTML `scope`
   属性、既定 Column）を付与。**マーカーは非表示なのでレンダリング非破壊
   （imageTest 安全）で、既定 OFF により未タグ文書の出力は不変**。
   フォーム有効時はフォームウィジェットを **`Form` 構造要素に内包**（PDF/UA 7.18.4）。
   **リンクは `Link` 構造要素 + 注釈の `/Contents`（代替説明）**（PDF/UA 7.18.5）、
   **画像は `Figure` 構造要素 + `/Alt`**（HTML `alt` 属性、img/svg/object）を付与。
   要素マッピングは figcaption→Caption 等も対応。
   **`_9520_UA/PdfUaValidationTest` が veraPDF で PDF/UA-1 適合を検証**（見出し・
   段落・リスト・表・フォーム入力・画像・リンクを含む文書、埋め込みフォント使用）。
   `_9500_PROFILE` でも `/S /H1`・`/P`・`/L`・`/Table` 等をバイト検証。
   **リンク・フォームは読み順位置に配置**：`Drawer` を `Visitor.visitBox` に渡し、
   対話オブジェクト（注釈・フォームフィールド）をペイント時に文書順で発行
   （`PDFOutputDrawable`）することで、`Link`/`Form` 構造要素が包含ブロック配下に
   ネストする（従来は文書直下）。残: ページまたぎ要素の単一化（現状ページ毎に分割）、
   `Link` 要素へのリンクテキスト内包、より広い要素マッピング（span/em/strong 等の
   インライン）。

## C. テキスト（さらなる高度化・pdfg2d 側）

5. **Knuth-Plass 最適行分割**
   段落パイプラインの `LineBreaker` は貪欲法。box-glue-penalty モデルは
   最適法の入力にそのまま使えるため、戦略の差し替えで導入できる（設計済み）。
6. **アラビア語・インド系の shaping（Tier 2）**
   bidi の並べ替えは実装済みだが、接続形・文脈置換・並べ替えを伴う本格
   shaping は未対応。`Shaper` インターフェースを HarfBuzz シグネチャに
   合わせてあるため、必要なら HarfBuzz JNI 実装の差し替えで対応（純 Java 方針は既定で維持）。
7. **DeviceN 色空間の多チャンネル拡張**
   DeviceN（複数カラーラント）は実装済み。tint 変換関数の高度化
   （NChannel、スポット + プロセスの厳密な掛け合わせ）は将来。
8. **COLR v1 / SVG-in-OT カラーフォント**
   COLR v0（レイヤー）は実装済み。COLR v1（グラデーション・合成）や
   SVG-in-OT への拡張は将来。

## 意図的に対象外とするもの

- **折り丁面付け**（折りスキーム + 本格クリープ計算）: 面付け専用 RIP /
  インポジションソフトの領域。pdfg2d は 1 面付け・N-up・同一面付け・中綴じ・
  カット&スタックまでをスコープとする。
- **TrapNet（トラッピング注釈）**: 最新の RIP はイントラップが標準のため実装しない。
- **電子署名の実行**（最終バイト列への署名・CMS/PAdES 埋め込み、証明書/鍵/HSM
  管理、PAdES-LTV）: PDF 生成完了後の後処理であり、専用の署名サービス/ツールの
  領域。pdfg2d は署名させるための**空の署名フィールドを置くところまで**をスコープ
  とする（フォーム対応に含める）。
- **請求書 XML（CII/UBL）の生成**: 構造化された請求データを持つ呼び出し側アプリの
  責務。pdfg2d は呼び出し側が用意した XML を Factur-X 準拠で埋め込むところまで。
- **FDF/XFDF によるフォームデータの入出力・記入済みフォームのフラット化**:
  フォームへの記入・記入結果の読み出し・記入後の静的化は、PDF 生成ではなく
  フォーム処理/後処理。pdfg2d はフォーム（フィールドとデフォルト値）を生成する
  ところまでを担う。
