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

## B. アクセシビリティ

4. **タグ付き PDF の上流接続（foliojet / copperpdf）**
   pdfg2d 側の API（`beginStructElement`/`endStructElement`、表 Scope、Link
   の OBJR 関連付け、見出しレベル検証）は実装済み。foliojet は現状タグ付き
   PDF を一切出力していない（`beginStructElement` 未使用）。HTML の要素構造を
   ボックスツリー経由で pdfg2d の構造 API へ流し込めば、アクセシブルな
   タグ付き PDF / PDF/UA を製品として出せる。**タグはメタデータでレイアウトを
   変えないため、3.2 レイアウト維持と両立する**。規模は大きい（全ドキュメントの
   構造伝播）が、レンダリング非破壊で進められる数少ない残項目。

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
