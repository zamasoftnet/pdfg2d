# PDF/A-1b / PDF/X-1a 対応の堅牢化計画

作成日: 2026-07-11。コード調査とウェブ調査（ISO 19005-1 / ISO 15930-4、veraPDF ルール、
PDF Association TechNote）に基づく現状評価と対応計画。
実施記録の全体は [`CHANGELOG.md`](./CHANGELOG.md) を参照。

> **実施状況（2026-07-11）**: 第1弾〜第4弾まで実施済み。
> P0 全件（conformance="B"、X-1a 暗号化拒否、OpenAction 拒否、透明グループ抑止）、
> バイナリマーカー全 PDF 出力（旧実装の 127 混入バグも修正）、veraPDF による
> PDF/A-1b/2b/3b の CI 検証（XMP の XML 宣言混入と CFF スペース幅欠落の 2 バグを検出・修正）、
> X-1a の CMYK 強制・OutputIntent API（`PDFParams.withOutputIntent`）・
> TrimBox/ArtBox 排他 + 包含検証、PDF/A-2b/3b 対応（透明許可、A-3 添付 +
> AFRelationship + catalog /AF）まで完了。残: P2-10（注釈の位置ベース許可）のみ。
>
> **追補（2026-07-11 第2次）**: 規格バリエーションを拡大実装。
> PDF/X-4・PDF/X-6・PDF/VT-1、PDF/A-2u/3u・2a/3a・4/4f、
> PDF 2.0 出力、タグ付き PDF 基盤（StructTreeRoot / ParentTree / 明示構造 API
> `beginStructElement` + 自動タグ: テキスト=P・画像=Figure+Alt・図形=Artifact）、
> PDF/UA-1（pdfuaid、DisplayDocTitle 強制、フォント埋め込み強制）。
> PDF/A 全プロファイルと PDF/UA-1 は veraPDF で検証済み。PDF/X 系と VT は
> 生成側ガード + 規則ベーステストで担保（OSS 検証器が存在しないため）。
> veraPDF 回帰は 5 フォント（和・韓・タイ、TTF/CFF-OTF）に拡大済み（P2-11 完了）。
>
> **追補（2026-07-11 第3次）**: 旧・既知の制限を解消。
> ICCBased RGB 色空間 + レンダリングインテントを実装し、X-4/X-6 で RGB プロファイル
> 設定時（`PDFParams.withSRGBProfile` 等）は CMYK 変換せず **RGB 入稿ワークフローを
> 維持**できるようになった（X-1a は常に CMYK 強制。判定は `effectiveColorMode()`）。
> PDF/VT の DPart は `nextDocumentPart(metadata)` によるレコード単位分割に対応。
> スポットカラー（Separation）も PDF/A の OutputIntent 整合込みで実装済み。

## 1. 現状の実装（正しく出来ていること）

| 項目 | PDF/A-1b | PDF/X-1a | 実装箇所 |
| --- | --- | --- | --- |
| ヘッダ PDF 1.4 | ✅ | ✅ | `PDFWriterImpl` |
| バイナリマーカー（>127 の4バイトコメント） | ✅ | ❌ 未出力 | `PDFWriterImpl` |
| OutputIntent（ICC 埋め込み） | ✅ sRGB | △ "Probe Profile" | `PDFWriterImpl` |
| フォント埋め込み強制（Core14 拒否） | ✅ | ✅ | `PDFGC.drawText` |
| サブセットの CIDSet | ✅ | — | `CIDUtils` |
| 暗号化の拒否 | ✅ | ❌ **未拒否** | `PDFWriterImpl` |
| 添付ファイルの拒否 | ✅ | ✅ | `PDFWriterImpl.addAttachment` |
| 透明（SMask/alpha ExtGState）の抑止 | ✅ | ✅ | `ImageFlow` / `PDFGC` / `PDFTransparencyRable` |
| 注釈: /F Print フラグ付与 | ✅ | （全拒否） | `PDFPageOutputImpl` |
| X: ArtBox 自動設定 / TrimBox 系 API | — | ✅ | `PDFPageOutputImpl` |
| X: GTS_PDFXVersion / Trapped / Title 既定値 | — | ✅ | `PDFWriterImpl` |
| XMP を非圧縮 (RAW) で出力 | ✅ | ✅ | `XMPMetadataWriter` |
| OCG（1.5 機能）の拒否 | ✅（例外） | ✅ | `PDFGroupImageImpl` |

## 2. 発見した問題（重要度順）

### P0 — 準拠を直接壊すバグ

1. **`pdfaid:conformance` が `"A"` になっている**（`XMPMetadataWriter`）。
   本来 PDF/A-1**b** なら `"B"`。現状はタグ付き PDF 要件を持つ PDF/A-1**a** を宣言して
   しまっており、veraPDF 等の検証器ではほぼ確実に不合格になる。1 文字の修正。
2. **PDF/X-1a で暗号化が拒否されない**。ISO 15930 系はパスワード保護を禁止。
   `PDFWriterImpl` の暗号化ガードに `V_PDFX1A` を追加する。
3. **OpenAction（JavaScript）が PDF/A・PDF/X でも出力される**。
   両規格とも JavaScript / 各種アクションを禁止。`close()` で例外にする。
4. **透明グループ付き Form XObject が PDF/A-1b/X-1a でも生成できる**
   （`PDFWriterImpl.createGroupImage` が常に `/Group /S /Transparency` を書く。
   バージョンガードは「1.4 以上」のみで A-1b(1412)/X-1a(1421) が素通り）。
   A-1/X-1a では透明グループなしの Form XObject にフォールバックするか例外にする。

### P1 — 色管理の不整合（条件次第で不合格）

5. **PDF/X-1a で DeviceRGB が出力できてしまう**。X-1a は CMYK・グレー・特色のみ。
   既定の `ColorMode.PRESERVE` のまま X-1a を選ぶと RGB オペレータ（`rg`/`RG`）が出る。
   対応: `V_PDFX1A` 選択時は ColorMode を CMYK に強制（または RGB 色投入時に例外）。
6. **PDF/A-1b: OutputIntent とデバイス色空間の不一致**。
   A-1 では DeviceRGB は RGB の OutputIntent（または DefaultRGB）、DeviceCMYK は
   CMYK の OutputIntent（または DefaultCMYK）が必要。現状 OutputIntent は sRGB 固定
   なので、`ColorMode.CMYK` + PDF/A-1b の組み合わせが非準拠になる。
   対応: colorMode に応じて OutputIntent プロファイルを切り替える（CMYK 用 ICC の同梱）。
7. **PDF/X の OutputIntent が不完全**。`OutputConditionIdentifier` が "Probe Profile"
   （プレースホルダ）で、`/RegistryName`（"http://www.color.org"）と `/Info` がない。
   対応: `PDFParams` に印刷条件（例: Japan Color 2001 Coated、FOGRA39）と
   ICC プロファイルを指定できる API を追加し、identifier / registry / info を正しく出力。
   日本の印刷会社向けには Japan Color 系を既定にできると実用的。

### P2 — 堅牢化・網羅性

8. **バイナリマーカーを全 PDF で出力**（現在 PDF/A のみ）。X-1a でも実質必須、
   他バージョンでもベストプラクティス。無条件出力に変更してよい。
9. **X-1a: TrimBox/ArtBox の排他検証**。ページに TrimBox と ArtBox を両方設定した場合は
   例外にする（規格は「どちらか一方のみ」）。ボックスの包含関係
   （Trim/Art ⊆ Bleed ⊆ Media）の検証も追加。
10. **注釈の扱いの精緻化（X-1a）**: 現状は全拒否（安全側）。規格上は
    「Bleed/Trim の外側」なら可なので、必要になったら位置検証付きで許可する。
11. **フォント検証の受け皿**: veraPDF が頻繁に検出する failure は
    CIDSet 不完全・Widths と実フォントの不一致・symbolic TrueType の cmap 構成。
    サブセッタ出力をテストフォント群で veraPDF に通す回帰テストで担保する。
12. **PDF/A: XMP と Info 辞書の完全一致**の再確認（Title/Author/Producer/日付）。
    実装上は同じソースから出しているので概ね一致するが、検証テストを追加する。

### P3 — 検証基盤（再発防止の本丸）

13. **veraPDF を CI に組み込む**（PDF/A-1b 用）。
    - Maven Central の veraPDF ライブラリ（greenfield パーサ）をテスト依存に追加し、
      生成した PDF/A-1b を毎ビルド検証する JUnit テストを作る。
    - 既存の `PDFVersionTest` / `TextRenderingTest` 相当の内容（テキスト・画像・
      グラデーション・注釈・しおり）を PDF/A-1b で生成 → veraPDF 合格を assert。
14. **PDF/X-1a の自己検証**: オープンソースの X-1a 検証器は事実上ないため、
    生成側での禁止事項チェック（本計画の P0〜P2）+ 生 PDF の規則ベーステスト
    （`/OutputConditionIdentifier`・`/RegistryName`・RGB オペレータ不在など）で担保。
    リリース前の Acrobat Preflight での手動確認手順も README に記す。
15. **将来**: PDF/A-2b/3b 対応（透明 OK・AES OK・添付 OK になり、HTML 由来文書とは
    実は相性が良い）。A-1b の制約回避コードの多くが不要になるため、対応順序としては
    「A-1b を堅牢化 → A-2b/3b を追加」が良い。

## 3. 実施順序の提案

1. **第1弾（半日）**: P0 の 4 件 + P2-8。すべて小さな修正。
   併せて回帰テスト（JS OpenAction 拒否、X-1a 暗号化拒否、conformance="B"、
   A-1b でのグループ画像挙動）を追加。
2. **第2弾（1〜2日）**: veraPDF の CI 組み込み（P3-13）。ここで新たに見つかる
   フォント系 failure（P2-11）を潰す。
3. **第3弾（1〜2日）**: 色管理（P1-5〜7）。`PDFParams` に OutputIntent 設定 API を
   追加するため、利用側（foliojet / copperpdf）の追随と同一作業で行う。
4. **第4弾**: X-1a の箱検証・注釈精緻化（P2-9,10）、PDF/A-2b/3b（P3-15）。

## 参考資料

- ISO 19005-1 (PDF/A-1) 概要: https://pdfa.org/resource/iso-19005-1-pdf-a-1/
- ISO 15930 (PDF/X) 概要: https://pdfa.org/resource/iso-15930-pdfx/
- PDF/X Application Notes: https://printtechnologies.org/standards/files/pdf-x-application-notes_v4-sep06.pdf
- PDF/X-1a 実務要件（prepressure）: https://www.prepressure.com/pdf/basics/pdfx-1a
- OutputIntent RegistryName 要件: https://duon.zendesk.com/hc/en-us/articles/15265450987420
- 色と OutputIntent のベストプラクティス（PDF Tools AG）: https://www.pdf-tools.com/pdf-knowledge/a-best-practice-using-output-intents/
- veraPDF 検証ルール（1b プロファイル）: https://demo.verapdf.org/api/profiles/1b
- veraPDF の CIDSet / symbolic TrueType cmap 関連 issue: https://github.com/veraPDF/veraPDF-library/issues/983 , /issues/818
