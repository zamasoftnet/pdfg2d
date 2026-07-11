# pdfg2d 機能の現状と拡張提案

作成日: 2026-07-10。実装済み機能の棚卸しと、HTML+CSS 組版エンジン（foliojet / copperpdf）の
基盤としての拡張提案をまとめる。詳細な実装状況は [`FEATURES.md`](./FEATURES.md) を参照。

## 1. 実装済み機能の棚卸し（要約）

### 文書・カタログ
- PDF 1.2〜1.7 / PDF/A-1b / PDF/X-1a、Linearized PDF（Fast Web View）
- 文書情報、XMP メタデータ、Viewer Preferences
  （印刷系: Duplex、PrintScaling、PrintPageRange、NumCopies、PickTrayByPDFSize を含む）
- しおり、名前付き宛先、URI/文書内リンク注釈、添付ファイル、Open Action (JavaScript)
- 暗号化: RC4 (V2) / AES-128 (V4 AESV2) + 権限制御

### ページ・グラフィックス
- ページボックス: MediaBox / CropBox / BleedBox / TrimBox / ArtBox（ページ単位）
- Graphics2D ブリッジ（パス、クリップ、破線、テクスチャ、グラデーション）
- 透明度（ExtGState）、透明グループ、Form XObject（グループ画像・入れ子可）
- タイリングパターン、軸型・放射型シェーディング（マルチストップ、Type 2/3 関数）
- **印刷時のみ表示 / 画面のみ表示のウォーターマーク**（OCG の
  `ViewState`/`PrintState` Usage。`PDFGroupImage.setOCG(VIEW_OFF | PRINT_OFF)`）
- カラーモード: RGB / Gray / CMYK（変換込み）、CMYK オーバープリントフラグ
- 画像: Flate / JPEG / JPEG2000、独自 PNG デコーダ、CMYK JPEG、URI・インスタンス単位の重複排除

### テキスト・フォント
- Core 14 / CID キー付き / CID Identity（縦書き Identity-V）
- TrueType / OpenType(CFF) / WOFF の読み込み・埋め込み・実バイナリサブセット化、ToUnicode
- cmap format 0/2/4/6/8/10/12/13、kern、日本語禁則、justify、段組、フロート、letter-spacing
- SVG（Batik 連携）、絵文字フォント

## 2. 拡張提案

優先度は「HTML+CSS → 印刷物」というプロダクト文脈（画面表示と紙出力の両立、
商業印刷・オフィス印刷の両対応）で付けている。

### A. 印刷品質・商業印刷（優先度: 高）

1. **OCG レイヤー API の一般化**
   現状ウォーターマーク OCG は名前が `"WATERMARK"` 固定・フラグ 2 種のみ。
   名前付きレイヤーを複数作れる API（名前、初期状態、ロック、Intent）に一般化すると、
   CSS 側から「印刷専用要素」「画面専用要素」「校正用レイヤー」を任意個宣言できる。
   実装は既存の `nextOCG()` / `OCProperties` 出力の薄い拡張で済む。
2. **スポットカラー（Separation / DeviceN 色空間）**
   特色は商業印刷の必須要件。`Color` 階層に `SeparationColor`（名前 + 代替色 + tint 変換関数）を
   追加し、ページリソースに色空間を登録する。
3. **トンボ・裁ち落とし支援**
   TrimBox/BleedBox は実装済みなので、トンボ（コーナー/センター）描画ヘルパと
   「仕上がりサイズ + 裁ち落とし幅」からの各ボックス自動設定ユーティリティを追加。

   Foliojet側にこの機能がある。しかも面付けもできるようになっている。
   面付けも含めてFoliojetからpdfg2dに移せばFragmentedOutputによる高速化やメモリ節約ができるのではないか。
4. **ICC ベースの色 / レンダリングインテント**
   OutputIntent は対応済み。オブジェクト単位の ICCBased 色空間と `ri` オペレータ対応で
   PDF/X-4 への道が開ける。
5. **グラデーションのアルファ対応（SMask）**
   既知の制限（RGBA ストップのアルファは無視）。輝度シェーディングを SMask とする
   ExtGState を生成すれば CSS の `linear-gradient(rgba(...))` を忠実に再現できる。

### B. 文書構造・アクセシビリティ（優先度: 高〜中）

6. **タグ付き PDF（Tagged PDF / StructTree）→ 将来的に PDF/UA**
   ソースが HTML なら意味構造は既に手元にある。`beginStructElement(role)/endStructElement()`
   のような低レベル API を pdfg2d が提供し、構造ツリー構築は上流に任せる設計が自然。
   公文書・自治体案件ではアクセシビリティ要件が増えており戦略的価値が大きい。
7. **PDF/A-2b / PDF/A-3b**
   A-1b は実装済み。A-3 は添付ファイルを許すため「元の HTML/データを埋めた PDF」
   （請求書 + データのハイブリッド）が作れる。
8. **PDF 2.0 (ISO 32000-2) 出力と AES-256 (R6) 暗号化**
   現状の暗号化上限は AES-128。長期的な要件対応として。

### C. テキスト（優先度: 中）

9. **ハイフネーション**
   justify の既知の制限。Liang アルゴリズム + TeX パターン（LGPL 注意、または
   libhyphen 形式）で `PageLayoutGlyphHandler` の行分割候補を増やす。
10. **双方向テキスト（bidi）/ RTL**
    現状 RTL は論理順のまま出力する制限を明文化済み。`java.text.Bidi` で並べ替えれば
    外部依存なしで基本対応できる。アラビア語 shaping まで踏み込むなら HarfBuzz 系
    (harfbuzz-ng の JNI) の検討が必要。
11. **GPOS/GSUB ベースのカーニング・リガチャ**
    現状は kern テーブルのみ。OpenType 専用フォントでは GPOS しか持たないものが多く、
    高品質組版の詰めに効く。
12. **ルビ支援**
    日本語印刷物の必須機能。レイアウトは上流エンジン側でも、
    ベースラインからのオフセット付き小書きテキストを効率よく出す下回りがあると良い。
13. **カラーフォント（COLR/CPAL, SVG-in-OT）**
    絵文字は専用モジュールで対応済みだが、一般のカラーフォントにも広げられる。

### D. ファイルサイズ・性能（優先度: 中）

14. **オブジェクトストリーム + XRef ストリーム（PDF 1.5）**
    小さな辞書オブジェクトが多い文書でファイルサイズを 10〜30% 削減できる。
15. **Deflate 圧縮レベルの設定（`PDFParams`）**
    ベンチマーク（200 ページ×2000 図形）では deflate が生成時間の約 27%。
    サーバーサイド大量生成では `BEST_SPEED`、配布用では高圧縮、と選べる価値がある。
    ※ `PDFParams` はレコードのためコンポーネント追加は API 破壊。利用側の追随と同時に実施。
16. **ページ並列生成**
    フラグメント構造上、ページコンテンツの並列書き込みは筋が良い。フォント読み込みは
    並列化済みなので次のボトルネックはここ。
17. **文書間フォントサブセットキャッシュ**
    バッチ生成（同じフォントで大量の PDF）でサブセット計算を再利用する。

### E. コード健全性（優先度: 低〜中、ただし今回実害が出た所）

18. **Batik 依存を pdfg2d-pdf から pdfg2d-svg へ移動**
    `G2DUtils.fromAwtPaint` が Batik の `LinearGradientPaint`/`RadialGradientPaint` のみを
    見ていたため、標準 `java.awt` のマルチストップグラデーションが黙って単色に
    なるバグが実際にあった（今回修正済み）。AWT 変換と Batik 変換をモジュールで分離すれば
    再発しない構造にできる。
19. **veraPDF による PDF/A 検証の CI 組み込み**
    PDF/A-1b 出力の準拠性を回帰テストで保証する。
20. **`PDFGC.drawText`（約 220 行）のさらなる分割**
    今回 PDFWriterImpl / PDFGC の分割を実施済みだが、テキスト描画部は次の候補。

## 3. 今回（2026-07-10）の変更で解決済みの項目

- cmap format 10 の文字マッピング実装（旧 TODO）
- RTL / ハイフネーション / アルファグラデーションの TODO を「既知の制限」として明文化
- `PDFWriterImpl`（2282→約1100行）、`PDFGC`（1790→約1400行）の分割
  （`LinearizedPDFAssembler` / `XMPMetadataWriter` / `ViewerPreferencesWriter` / `PaintResources`）
- java.awt マルチストップグラデーションが PDF シェーディングにならないバグ修正
- ファイル ID 生成の `SecureRandom` 化、`writeName` キャッシュ、`writeString` バッファ化、
  Deflater 出力バッファ拡大（512B→8KB）+ ネイティブメモリの確定的解放
- テスト大幅追加（cmap 全フォーマット、全グリフデコード、PDF プリミティブ、
  印刷専用ウォーターマーク、グラデーション、ViewerPreferences、テキストレイアウト等）
