# 縦組みの字形ごとの縦原点 — V1・V2・V3 実装報告

2026-09-12、実装担当: Codex。対象タスク: TECH-20260911-010、依頼元: ユーザー直接。

## 結果

第6版の設計に沿ってV1〜V3を実装した。Java 21で実ソースをコンパイルし、補助Gradle環境で
core・pdfの全テストを実行した結果は **246件中244件成功、失敗0、エラー0、既存のスキップ2件**。
新規テストは26件。指定の標準Gradle wrapperによる検証は、配布ファイルの取得がネットワーク制限で
拒否されるため完走できていない。補助環境の成功を標準ビルドの成功としては扱わない。

V4は依頼どおりClaudeへ引き継ぐ。設計全体の完了判定はV4終了後。

対象は `F:/dev/zamasoftnet-public/pdfg2d`、ブランチ `main`、ローカル開発環境。
開始時の未追跡ファイルは設計書とcoreの試験用書体4点で、これらは変更していない。
foliojet4・copperpdf4・zstreamのソースや設定は変更していない。
コミットなし、プッシュなし、デプロイなし、公開なし、リリースなし。

## 変更ファイル一覧

以下のパスはリポジトリルート相対。実装10ファイル、テスト7ファイル、文書2ファイル。

| ファイル | 変更 |
|---|---|
| `pdfg2d-core/src/main/java/net/zamasoft/pdfg2d/font/Font.java` | `getVerticalOrigin(int)`を追加 |
| `pdfg2d-core/src/main/java/net/zamasoft/pdfg2d/font/otf/OpenTypeFont.java` | 縦原点計算・キャッシュ、平行移動救済の撤去、ダッシュkerning |
| `pdfg2d-core/src/main/java/net/zamasoft/pdfg2d/gc/font/util/FontUtils.java` | `drawText`・`addTextPath`の縦書き変換 |
| `pdfg2d-pdf/src/main/java/net/zamasoft/pdfg2d/pdf/font/cid/CIDUtils.java` | `writeW2`、既存書き出しメソッドへの原点配列の搬送 |
| `pdfg2d-pdf/src/main/java/net/zamasoft/pdfg2d/pdf/font/cid/embedded/OpenTypeEmbeddedCIDFont.java` | 元GIDから原点を取得し、subset CIDで公開 |
| `pdfg2d-pdf/src/main/java/net/zamasoft/pdfg2d/pdf/font/cid/embedded/OpenTypeEmbeddedCIDFontSubset.java` | 原点の保持と縦書き登録時の更新 |
| `pdfg2d-pdf/src/main/java/net/zamasoft/pdfg2d/pdf/font/cid/embedded/SystemEmbeddedCIDFont.java` | 新引数にnullを渡す |
| `pdfg2d-pdf/src/main/java/net/zamasoft/pdfg2d/pdf/font/cid/identity/OpenTypeCIDIdentityFont.java` | 描画時の原点記録と書き出し |
| `pdfg2d-pdf/src/main/java/net/zamasoft/pdfg2d/pdf/font/cid/identity/SystemCIDIdentityFont.java` | 新引数にnullを渡す |
| `pdfg2d-pdf/src/main/java/net/zamasoft/pdfg2d/pdf/font/cid/missing/MissingCIDFont.java` | 新引数にnullを渡す。空の縦送り配列は維持 |
| `pdfg2d-core/src/test/java/net/zamasoft/pdfg2d/font/otf/OpenTypeVerticalOriginTest.java` | 新規6件: 実書体のGID・送り・原点・空字形・既定値 |
| `pdfg2d-core/src/test/java/net/zamasoft/pdfg2d/gc/font/util/FontUtilsVerticalOriginTest.java` | 新規3件: 輪郭配置・送り調整・原点差kerning |
| `pdfg2d-pdf/src/test/java/net/zamasoft/pdfg2d/g2d/gc/VerticalOriginDrawingTest.java` | 新規3件: BufferedImage・ImageFont・回転 |
| `pdfg2d-pdf/src/test/java/net/zamasoft/pdfg2d/pdf/font/cid/CIDUtilsVerticalMetricsTest.java` | 新規11件: W2の構文・既定値・疎配列・入力不整合 |
| `pdfg2d-pdf/src/test/java/net/zamasoft/pdfg2d/pdf/font/cid/OpenTypeVerticalMetricsPDFTest.java` | 新規2件: 埋め込みとIdentityのPDF実出力 |
| `pdfg2d-pdf/src/test/java/net/zamasoft/pdfg2d/pdf/font/cid/embedded/OpenTypeEmbeddedCIDFontSubsetTest.java` | 登録順とCID 0の原点検査を1件追加 |
| `pdfg2d-pdf/src/test/java/net/zamasoft/pdfg2d/pdf/impl/OpenTypeEmbeddedFontSharingTest.java` | W2の存在だけでなく必要なCIDだけを出すことを検査 |
| `docs/CHANGELOG.md` | 2026-09-12の節を追加 |
| `docs/VERTICAL_ORIGIN_STAGE_REPORT.md` | 本報告書 |

検証補助ファイルはGit管理対象外の `build/vertical-origin-verification/` に置いた。
既存の `FontUtilsLeadingXAdvanceTest` は変更せず全件実行に含めた。

## 各段階の対応

### V1 — 実装済み、補助環境のcoreテスト成功

- `Font.getVerticalOrigin(gid)`の既定値は880。
- OpenTypeはvmtxが有効な縦書きだけ実値を返す。CFFはVORGの
  `getVertOrigunY`を優先し、なければ正規化pathの `-minY * upm / 1000 + tsb`。
  TrueTypeはglyfの元単位yMaxとtsbを加算する。
- 空字形は880。1000単位への変換は`short`キャストで切り捨てる。
  `ShortList`でGIDごとに保持し、共有フォントの並行参照に備えてキャッシュ操作を同期する。
- pgothicの「450、（430、【430、組880、乙880、A829、GID 24のダッシュ860、
  ─880、…880、読点880を確認。縦送りも設計表と照合した。
- sansjp-vorgの「700、（・組・Aの880、sansjp-novorgの「730とその他880を確認。
- IPAexの「・組879、U+2010のGID 12237が859、空白880を確認。
- 横書きとvert/vrt2なしの縦書きでは880。novert書体のAも880。
- 提供書体のU+2015欠落については後述。

### V2 — 実装済み、補助環境のpdfテスト成功

- subsetの横書き登録は880を仮置きし、縦書き登録が実値を更新する。横書きの再登録では
  上書きしない。CID 0も同じ規則。元GIDの原点取得には`super.getVerticalOrigin`を使い、
  公開する`getVerticalOrigin`はsubset CIDを受ける。
- Identityは描画したGIDの原点を記録して書き出す。
- `writeW2`はDW2の最頻値、vy同率時の小さい値、既定CIDの完全省略、
  実数vx、連続した同一3つ組の範囲形式、それ以外の配列形式を実装。
  advanceの頻度は既存WArrayの未使用位置補完規則を維持し、vyの頻度は使用CIDだけを数える。
- null/空のadvanceではDW2・W2を出さない。nullのvyは880。
  短いvy・未使用vyを除外し、使用advanceの横幅不足は`IllegalArgumentException`。
- 指定の5呼出元を更新。`writeWArray2`を撤去した。
- `ADJUST_VERTICAL`・`VERTICAL_SHAPE_ADJUST`・平行移動分岐を撤去。
  `adjustShape`の署名と回転フラグ値2を維持した。輪郭に原点は焼き込まない。
- `WArray`・subsetの`signature()`・subset名のハッシュ・`FontSubsetCache.Key`は変更なし。
- PDF実出力は埋め込み・Identityの両方で検査。pgothicの入力「A組乙では
  「に`-570 500 450`、Aに`-1000 318.5 829`、組・乙には明示W2なし。
  IPAexの「組ではDW2 `[879 -1000]`・W2空、全角空白とハイフンを足すと880と859の項が出る。
- 横→縦・縦→横の共有登録、CID 0の更新、共有プログラムが1本であることを確認した。

### V3 — 実装済み、補助環境のcore・pdfテスト成功

- 両FontUtilsメソッドのrun先頭から固定y移動を外し、字形ごとに中心寄せと原点を適用。
  外側の変換、ユーザー単位の送り、1000単位の輪郭拡縮を設計の順で合成する。
- `drawText`の先頭xAdvanceもペン用`at`へ積む。常に字形用変換を複製し、
  ImageFontは従来の0.88emを維持する。横書き・sideways分岐は変更なし。
- 12/36pt、外側の拡縮、先頭・途中のxAdvance、letterSpacing、異なる横幅と原点を持つ
  「A組の連続配置を検査。括弧の字面は送り0.57em内に収まり、次の組と重ならない。
- `addTextPath`と実際のG2DGC描画について、変換後の全path座標を比較（許容誤差0.0001）、
  画像の字面境界を比較した。非アンチエイリアス描画の境界量子化は1画素まで許容する。
  これは字形位置の許容差ではなく、座標一致の検査とは独立したラスタ境界の許容差。
- ImageFontが受け取った変換を書き換えても次字のペンを壊さないことを確認。
  回転フラグ2・回転中心・従来原点での回転後画素一致も確認した。
- ダッシュと罫線の双方向、および同字反復のkerningを検査。原点差は±20、同字反復では0。

## 実行コマンドと結果

使用JDK: Temurin OpenJDK 21.0.7。最終集計はJUnit XMLによる。

| コマンド | 結果 |
|---|---|
| `.\gradlew.bat :pdfg2d-core:test --offline` | 実行前に失敗。Gradle 8.11.1取得時に`SocketException: Permission denied: getsockopt`。テスト実行0件 |
| `.\gradlew.bat :pdfg2d-core:test :pdfg2d-pdf:test --offline` | 同じ環境要因で失敗。テスト実行0件。`build/vertical-origin-verification/standard-gradle.log`に保存 |
| `gradle --offline --no-daemon :pdfg2d-core:test` | 既存Gradle 8.9ではfoojay-resolver-convention 0.9.0の解決に失敗。テスト実行0件 |
| ローカルGradle 8.11.1の直接実行、読取専用依存キャッシュの利用 | 標準設定での起動を回復できず。テスト実行0件 |
| `gradle --offline --no-daemon -p build/vertical-origin-verification coreTest` | 初回の実テストは82件中1失敗。U+2015が提供書体にないことを検出。書体や実装で補わず、欠落の明示とGID 24の検査に修正 |
| `gradle --offline --no-daemon -p build/vertical-origin-verification coreTest pdfTest`（途中） | pdf 156件中3失敗・2スキップ。FontIndexTestのJUnit一時フォルダ後始末が権限で失敗。補助環境の`java.io.tmpdir`を作業領域内へ設定して解消 |
| 同上（G2D試験追加後） | pdf 159件中新規G2D試験2失敗・2スキップ。画像の初期描画色差と、座標変換の適用場所による非AA境界画素差を調査。描画色を明示し、全path座標一致＋ラスタ境界検査に分けた |
| `gradle --offline --no-daemon -p build/vertical-origin-verification pdfTest --tests '*VerticalOriginDrawingTest'`（診断途中） | 3件中1失敗。色差解消後は複数字形の境界画素差のみ。最終全件実行では解消 |
| **`gradle --offline --no-daemon -p build/vertical-origin-verification coreTest pdfTest`（最終）** | **BUILD SUCCESSFUL。core 86件成功、pdf 160件中158件成功・2スキップ、失敗0・エラー0** |
| `gradle --offline --no-daemon -p build/vertical-origin-verification compileJava` | import整理後の最終ソースで成功。既存SVGソースのunchecked警告のみ |
| `git diff --check` | 成功 |
| `& 'C:\Users\comra\AppData\Local\Programs\Python\Python311\python.exe' 'F:\AGENTS\tools\check_text.py' docs/VERTICAL_ORIGIN_STAGE_REPORT.md docs/CHANGELOG.md` | `OK: C1制御文字なし`。秘密情報を走査しないよう変更文書に対象を限定 |

補助環境は標準設定の代用品で、リポジトリの `settings.gradle`・`build.gradle` は変更していない。
Gradle 8.9内蔵のJavaプラグイン、既存キャッシュからコピーしたJAR45本、既存zstream JARを使用した。
font・core・pdf・svgの実ソースをまとめてコンパイルし、coreとpdfの全テストソースをそれぞれの
元のモジュールディレクトリを作業ディレクトリとしてJUnit 5.13.4で実行している。
新規テストだけの代替実行ではない。一方、標準の依存解決・モジュール境界・JaCoCo等の同一性は保証しない。

最終JUnit XML・HTMLは以下にある（ローカル生成物、Git管理対象外）。

- `build/vertical-origin-verification/build/test-results/coreTest/`
- `build/vertical-origin-verification/build/test-results/pdfTest/`
- `build/vertical-origin-verification/build/reports/tests/coreTest/index.html`
- `build/vertical-origin-verification/build/reports/tests/pdfTest/index.html`

既存のスキップ2件:

- `LinearizedPDFTest.testLinearizedPDFGeneration`: qpdf不在による既存のassumption。
- `FontStretchSelectionTest.os2WidthClassIsReadFromFontDirScan`: DejaVu Sans Condensed不在による既存のassumption。

## 設計との差・利用側の確認

1. **提供pgothic fixtureにはU+2015のcmapがない。** 実読取結果はU+2015→0、
   U+2014→元GID 11→縦GID 24、GID 24の縦送り960・原点860。
   設計の「―と—が24」という記載とは異なる。fixtureは変更せず、U+2015の欠落をテストに明記し、
   実在するU+2014とU+2500で原点差のkerningを検査した。U+2015を文字から選ぶ試験にはfixture補充が必要。
2. G2DGCはcoreではなくpdfモジュールにあるため、BufferedImage試験をpdf側に配置した。
   coreに依存の逆転を導入せず、coreでは輪郭配置・kerningを試験した。
3. 画像の画素完全一致は外部変換ありのJava2D非AA描画では成立しない場合がある。
   12ptの診断例は字面境界が `(93,56,16,32)` と `(92,55,16,32)`。
   最終検査では変換後path座標の一致を必須とし、画像境界には1画素の量子化を許容した。
   回転の固定条件では画素完全一致を維持した。
4. 標準ビルドの実行制約は上記のとおり。標準コマンドの再実行は未完事項として残す。

公開APIの新しいメソッド名は `Font.getVerticalOrigin` と `CIDUtils.writeW2` のみ。
設計に指定された既存の `writeIdentityFont`・`writeEmbeddedFont`・`writeEmbeddedFontProgram` は
`vy`引数を追加する署名変更を行った。`writeWArray2`は撤去した。

利用側のJavaソースを以下で読取検索し、**0件（rg終了コード1、検索エラーなし）**を確認した。

```powershell
rg -n 'writeWArray2|writeIdentityFont|writeEmbeddedFontProgram|writeEmbeddedFont\(' `
  'F:\dev\CopperPDF\copper4\foliojet4' `
  'F:\dev\CopperPDF\copper4\copperpdf4' -g '*.java'
```

## 未実施・引継ぎ

- 標準の `./gradlew :pdfg2d-core:test :pdfg2d-pdf:test` または `./gradlew build` の完走。
  Gradle配布とプラグインを解決できる環境で再実行する必要がある。
- V4: foliojet4の `WebFontSubset.transform` と `MyGVTGlyphVector.getOutline`、
  punct-fuzz、PDF・PNG・paged SVG・SVG内テキスト・文字影・クリップの実物確認、
  foliojet4全件・copperpdf4 imageTest・unitRemote・doccheck。ユーザー指定によりClaude担当。
  異なる縦原点を持つダッシュ対はkerningが変わるため、利用側のレイアウト差分の説明が必要。
- 開発部門の正本 `F:/AGENTS/座間ソフト/開発/TASKS.md`・`HANDOFF.md` の更新は、
  本セッションではリポジトリ外が書込不可のため未実施。本報告をTECH-20260911-010の引継ぎとして転記してほしい。
  旧 `F:/AGENTS/技術/START_HERE.md` は不存在で、`F:/dev/AGENTS.md` が示す移転先の運用資料を参照した。
- ロールバックは本報告の19ファイルについて今回の差分だけを戻す。開始時から存在した設計書・
  試験用書体には触れない。コミットを作っていないためリバート用コミットはない。
