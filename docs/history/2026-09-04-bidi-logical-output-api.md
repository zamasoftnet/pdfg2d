# 双方向テキストの論理出力 API（2026-09-04）

foliojet の段落単位 UBA（batch A-1b 以降）は glyph run を視覚順に描く。抽出器・支援技術へ論理順を渡すため、
copperpdf4 `docs/bidi-logical-output-spike.md` の結論（untagged は論理行単位の ActualText、tagged は描画順を
変えず `/K` だけ論理順、鏡像は CID alias で ToUnicode も正す）に従い、pdfg2d に次を追加した。実装は codex に
委託し、維持者が全モジュールの試験（core 76 / demo 161 / font 27 / pdf 128 / svg 15 / svg-emoji 3）で確認した。

## 追加 API

- `GC.beginTextReplacement(String)` → `State`（既定 no-op）。`RecorderGC` は Begin/End を記録。
- `PDFGraphicsOutput.beginActualText(String)` / `endActualText()`。
- `PDFPageOutput.beginStructContent(StructureRef, StructureOrder)`、`record StructureOrder(long blockOrdinal,
  int logicalStart, int tieBreaker)`。
- `Font.toGID(int displayCodePoint, int logicalCodePoint, FontFeatureSet)`。

## 契約

- API 未使用時の出力はバイト同一（`/K` の順序・MCID・ParentTree・subset 採番のいずれも不変）。
- ActualText の意味上の粒度は **1 論理行**（foliojet 側が決める）。shadow や blur の再描画には付けない。
- ラスタ化される filter group 内のテキストは非検索（Recorder のラスタ再生で scope を破棄）。
- PDF 1.5 未満では marked-content の ActualText は書かない（no-op）。
- Identity CID フォントは alias 不可。

## 抽出器ごとの見込み（spike より）

Acrobat / MuPDF は ActualText を採用、pdf.js は無視（CID alias で鏡像だけ救う）、PDFBox 3.0.3 は未対応
（trunk は挙動が異なる）。全抽出器を同時に満たす粒度は無いため、foliojet の試験は生の ActualText 値と
`/K` 順を検査し、PDFBox の抽出結果は観測として別に固定する。

## レビューと修正(同日)

codex 読み取り専用レビュー(conditional)を受け、順序ヒントの契約を「同じ writer が宣言した有効な Elem へ
切り替えるときだけ適用。null/foreign target では routing と currentOrder を変えず、対応する endStructContent の
釣り合いだけ取る」と定めて `PDFPageOutput.beginStructContent(StructureRef, StructureOrder)` の Javadoc に明記。
試験を実 PDF 解析へ強化: ToUnicode CMap(bfchar/bfrange、sparse CID、縦 em-dash variant との共存)、旧 API 限定の
バイト同一、同一キーの描画順安定、hint/null 混在、複数頁 MCR の `/Pg`、ActualText とタグ/レイヤー/artifact/
vector replay の BDC/EMC・BT/ET stack 整合。veraPDF は pdfg2d のテストクラスパスに無いので PDF/UA は構造不変条件で
検証(foliojet 側 `PdfUaValidationTest` が veraPDF で検証)。

## 既定 OFF への変更(同日、実測)

foliojet が生成した bidi 文書を PyMuPDF(MuPDF)と pypdfium2(PDFium)で抽出し、`/ActualText` を潰した複製と比較した
(copperpdf4 `docs/bidi-logical-output-spike.md` §7)。PDFium は ActualText 無しで論理順 `אבג ABC`、有りで視覚順
`ABC גבא`。MuPDF はどちらも不完全。spike の予測(MuPDF は ActualText を正しく使う)は外れた。ブラウザの PDF
出力も bidi に ActualText を使わないので、`PDFGC.beginTextReplacement` は `PDFParams.actualTextReplacement`
(既定 false)が真のときだけ ActualText を書くように変えた。低水準の `beginActualText/endActualText`、`/K` 論理順、
CID alias、Recorder 命令は不変。

