# PDF の要素単位 filter 捕捉と部分ラスタ化（2026-09-03）

`pdfg2d-pdf/build/pdf-exact-rendering-design.md` §0「§1 filter」の確定事項に従い、
PDF の CSS `filter` 相当を要素単位で捕捉し、必要な要素だけ生成画像へ変換する経路を追加した。

## 実装した経路

- 通常の `PDFGC.createGroupImage` は従来どおり Form XObject を即時生成する。
- `PDFGC.createFilterGroup` だけが、全 capability を通知する `RecorderGroupImageGC` を返す。
- 効果なしと opacity のみは、録画内容を Form XObject へ再生してベクタのまま描く。
- 色行列、blur、drop-shadow は現在のユーザー空間で録画を Java2D へ再生し、色行列、
  premultiply、blur、drop-shadow、opacity の順に適用して `addGeneratedImage` へ渡す。
- filter 解像度は `PDFParams.filterRasterDpi`（既定 300 dpi、72〜600 dpi）で、現在の変換の
  最大特異値を掛ける。16,000,000 画素を超える場合は効果なしの Form へ縮退する。
- 生成画像は artifact ではなく `/Figure` とし、録画画像の alt が空なら `filter` を使う。

## 統合後の部分ラスタ化修正

レイアウト側は filtered element の背景・各文字行・各画像を別々の filter group として描き、
各 group の nominal size にページ寸法を渡す。このため nominal box 全体を画素化すると、小さな
要素でも drawable の数だけページ大の画像と SMask が生じた。

- `RecorderGC` が変換と状態保存・復元を追いながら、図形、線幅、ぼかし、文字、画像、画像効果の
  保守的な content bounds を group-local user space で記録する。子 recorder の実 content bounds も
  親へ伝播する。`RecorderImage.getContentBounds()` は防御的コピーを返す。
- `PDFGC` は content bounds と nominal box の共通部分だけを filter kernel 分広げて画素化する。
  画素上限も縮小後の領域へ適用し、生成画像の配置は切り出し領域の原点を含む `cm` で戻す。
- 1000×1000 の group 内にある 50×20 の矩形を回帰ケースにし、画像寸法と配置原点が内容に
  追従することを PDF の辞書・content stream で固定した。

## 再生時の決定

- `RecorderGC` は変換、破線配列、Shape、GroupEffects の色行列を録画時に複製する。
  Shape は矩形を含め `Path2D.Double` に固定し、可変オブジェクトを保持しない方を選んだ。
- 入れ子の `RecorderImage` は再生先の `createGroupImage` へ再生してから配置し、opacity や
  blend が子の各命令へ個別適用されないようにした。
- `PixelBackedImage` は利用側（foliojet）にあり pdfg2d から型依存できないため、公開された
  `getPixels()` 契約を遅延検出して画素画像へ差し替える。通常の px→pt 変換を運ぶ
  `TransformedImage` は包み直して保持する。画素を取得できない `PDFImage`、`PDFGroupImage`、
  PDF 専用 Pattern タイルはラスタ再生では空になる。
- filter の opacity と呼出し時の fill alpha は Java2D 経路と同じく乗算する。

## 受け入れた意味上の制限

- ラスタ化した要素内の文字は検索・選択できず、元の構造タグと MCID も保持しない。
- artifact scope は recorder に保持されない。
- Core-14、外部 CID、System CID の文字は Java2D の既存代替経路で再整形される。
- 特色、DeviceN、overprint は RGB へ平坦化される。

テストクラスは追加・更新したが、依頼どおり Gradle、コンパイル、テストは実行していない。
