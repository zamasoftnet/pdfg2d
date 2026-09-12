# 字面 bounds API — G0 実装報告

2026-09-12、実装担当: Codex。タスク: TECH-20260911-011 の G0、依頼元: ユーザー直接。
対象: `F:/dev/zamasoftnet-public/pdfg2d`、ブランチ `main`、ローカル開発環境。
開始時の作業ツリーは clean。

**G0 の pdfg2d 側を実装済み。補助 Gradle 環境で全255件中253件成功・既存スキップ2件・
失敗0・エラー0。新規9件はすべて成功。** 標準 Gradle は配布取得の権限制約で未完走のため、
その確認は指定どおり Claude へ引き継ぐ。

## 対応範囲

copperpdf4 の `docs/design/2026-09-12-punctuation-ink-gap-design.md` 第5版の
§1-3・§2-2・§3 の単体(pdfg2d)・§4 の G0 に沿って、pdfg2d 側を実装した。
公開 API の追加は `GlyphBounds` と `ShapedFont.getGlyphBounds(int)` のみ。

- `GlyphBounds` は4つの double を持つ不変 record。1000 upm 正規化・y 下向きで、
  font-size 換算、字形配置、合成太字・斜体の変換前。既存の輪郭に記録された縦回転は含む。
  既存 `Font` の直列化契約と同様に、キャッシュへ保持してもよいよう `Serializable` を実装する。
- default メソッドは `getShapeByGID(gid).getBounds2D()` から作り、キャッシュしない。
  null path、空 bounds、move/close だけの path は null。
  任意の Shape を float の GeneralPath にコピーしないよう、`Glyph.isBlank()` と同じ
  PathIterator の線分有無判定を使い、double の精度を維持する。
- `OpenTypeFont` はインスタンスごとの `HashMap<Integer, GlyphBounds>` を持つ。
  `containsKey` で「未計算」と「計算済み null」を区別し、検索・輪郭取得・保存を
  `getVerticalOrigin` と同じ `synchronized (cache)` の範囲に置く。
- 埋め込みと Identity は明示的に override し、親クラスのキャッシュ処理を共用する。
  輪郭取得は動的ディスパッチでそれぞれの `getShapeByGID` を通る。
  埋め込みではキーが subset CID で、記録済み `VERTICAL_SHAPE_ROTATE` 適用後の輪郭を測る。
  Identity ではキーが元 GID。親用・子用のキャッシュを二重に作らない。

前提となる V4 の完了は、読取専用で参照した
`F:/dev/CopperPDF/copper4/copperpdf4/docs/history/2026-09-12-vertical-origin-per-glyph.md`
の V4・実物検証・ゲートの記録で確認した。今回 V4 を再実行したという意味ではない。
foliojet4・copperpdf4・zstream は変更していない。G1 以降は対象外。

## 変更ファイル一覧

リポジトリルート相対。実装5ファイル、テスト2ファイル、文書2ファイル。

| ファイル | 変更 |
|---|---|
| `pdfg2d-core/src/main/java/net/zamasoft/pdfg2d/font/GlyphBounds.java` | 不変 record を追加 |
| `pdfg2d-core/src/main/java/net/zamasoft/pdfg2d/font/ShapedFont.java` | キャッシュなしの default API |
| `pdfg2d-core/src/main/java/net/zamasoft/pdfg2d/font/otf/OpenTypeFont.java` | null を含めた同期キャッシュ |
| `pdfg2d-pdf/src/main/java/net/zamasoft/pdfg2d/pdf/font/cid/embedded/OpenTypeEmbeddedCIDFont.java` | subset CID をキーとする override |
| `pdfg2d-pdf/src/main/java/net/zamasoft/pdfg2d/pdf/font/cid/identity/OpenTypeCIDIdentityFont.java` | Identity GID をキーとする override |
| `pdfg2d-core/src/test/java/net/zamasoft/pdfg2d/font/otf/OpenTypeGlyphBoundsTest.java` | 新規6件 |
| `pdfg2d-pdf/src/test/java/net/zamasoft/pdfg2d/pdf/font/cid/OpenTypeGlyphBoundsPDFTest.java` | 新規3件 |
| `docs/CHANGELOG.md` | 2026-09-12 の節へ1行追記 |
| `docs/GLYPH_BOUNDS_STAGE_REPORT.md` | 本報告 |

## 試験内容

- pgothic の U+300C: 横 GID 15 は `(216, -828, 488, -102)`、
  縦 GID 28 は設計の fontTools 測定値 `(222, -234, 948, 38)` と照合（許容 ±0.5）。
- IPAex 明朝の 2048 upm・縦 GID 7497 は
  `(226.5625, -209.9609375, 946.2890625, 37.109375)` と照合（許容 0.00001）。
  整数化されないことも明示的に検査する。
- IPAex の横・縦で実在するスペース（GID != 0）は null。
  `getShapeByGID` の呼び出しを派生クラスで数え、通常字形・空白とも2回目に増えないことを確認する。
- 8スレッド・32タスクから同じ通常字形と空白を取得し、輪郭取得は各1回、
  通常字形の record は全呼び出しで同一インスタンスとなることを検査する。
- default 実装はキャッシュせず、任意の double 座標の Shape を丸めない。
  元 Shape を変更しても返却済み record は変わらない。
  null、空 path、bounds が空の図形、bounds は空でない move/close のみの path を検査する。
- PDFWriter に登録した埋め込み・Identity フォントで、それぞれ横・縦の PDF を生成する。
  埋め込みの「は CID 1（元 GID 15/28 と異なる）、Identity は元 GID のまま引けること、
  再取得時の record 共有、`getShapeByGID` との一致、─の中心回り90度回転を検査する。
  両 CID フォントで実在するスペースの null も確認する。

## 実行コマンドと結果

使用 JDK: Temurin OpenJDK 21.0.7。補助環境は前回の
`build/vertical-origin-verification/` を再利用した。標準ビルド設定・依存設定は変更していない。

| コマンド | 結果 |
|---|---|
| `.\gradlew.bat :pdfg2d-core:test :pdfg2d-pdf:test --offline` | テスト実行前に失敗。Gradle 8.11.1 の配布取得で `SocketException: Permission denied: getsockopt` |
| `gradle --offline --no-daemon -p build/vertical-origin-verification coreTest pdfTest`（初回） | core 92件中1件失敗。テストが実スペースの Glyph オブジェクト存在を仮定していた。輪郭なし TrueType では Glyph 自体が null となるので、テストを修正。pdf は未実行 |
| 同上（2回目） | core 92件中1件失敗。sansjp 部分集合には U+0020 の cmap がなく GID 0 だった。空白テストは IPAex の実在スペースを横・縦で検証する形へ変更。pdf は未実行 |
| 同上（最終、2026-09-12 14:12 JST） | **BUILD SUCCESSFUL（45秒）。core 92件成功、pdf 163件中161件成功・2スキップ。失敗0・エラー0。JUnit XMLで集計** |
| `git diff --check` | 成功 |
| `& 'C:\Users\comra\AppData\Local\Programs\Python\Python311\python.exe' 'F:\AGENTS\tools\check_text.py' docs/GLYPH_BOUNDS_STAGE_REPORT.md docs/CHANGELOG.md` | `OK: C1制御文字なし` |

途中の失敗は試験の前提の誤りで、bounds 実装の変更は不要だった。
補助環境は Gradle 8.9 の Java プラグイン、既存のローカル JAR・zstream JAR を使用し、
font/core/pdf/svg の実ソースをまとめてコンパイルする。
core と pdf の既存を含む全テストを各モジュールのディレクトリで JUnit 5.13.4 により実行する。
標準 Gradle の依存解決・モジュール境界・JaCoCo 等と同一の検証ではない。

既存のスキップは `LinearizedPDFTest.testLinearizedPDFGeneration`（qpdf 不在）と
`FontStretchSelectionTest.os2WidthClassIsReadFromFontDirScan`（DejaVu Sans Condensed 不在）。
コンパイル時は既存 SVG ソースの unchecked 警告のみ。

最終結果の XML・HTML は Git 管理外の以下に出力される。

- `build/vertical-origin-verification/build/test-results/coreTest/`
- `build/vertical-origin-verification/build/test-results/pdfTest/`
- `build/vertical-origin-verification/build/reports/tests/coreTest/index.html`
- `build/vertical-origin-verification/build/reports/tests/pdfTest/index.html`

## 設計との差・引継ぎ

- API の意味と対象段階に設計との差はない。キャッシュは3クラスに重複実装せず、
  親のインスタンスフィールドと処理を共用した。各子クラスに override はある。
- 指定の pgothic 測定値は縦 GID 28 の値であり、横 GID 15 には適用しない。
  横書きと IPAex の期待値は、Python 標準ライブラリの `struct` で fixture の
  head/loca/glyf を読んで正規化した値でも確認した。
  この環境の Python 3.11 には fontTools がなく、fontTools 自体の再実行はしていない。
- 標準 Gradle の再実行はユーザー指定どおり Claude へ引き継ぐ。
  補助環境の成功を標準ビルド成功としては扱わない。
- 開発部門の `F:/AGENTS/座間ソフト/開発/TASKS.md`・`HANDOFF.md` は
  本セッションの書込範囲外のため更新していない。本報告を TECH-20260911-011 の G0 記録として転記する。
- ロールバックは上記9ファイルの今回の差分のみを戻す。試験用書体は変更していない。
  コミットなし、プッシュなし、デプロイなし、公開なし、リリースなし。
