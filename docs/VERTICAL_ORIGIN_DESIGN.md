# 縦組みの字形ごとの縦原点 設計書（第6版、2026-09-12）

> 発端: copper4 のフォント別ランダム試験（`copperpdf4/dev/tools/punct-fuzz`、
> `copperpdf4/docs/history/2026-09-11-punctuation-fuzz-by-font.md`）で、縦送りが比例する書体
> （IPA P 明朝・IPA P ゴシック）の開き括弧が次の字に重なった。原因は pdfg2d が縦原点を
> 定数 880 で持つこと。本書は pdfg2d（+ 利用側 foliojet4）の直し方を決める。
> 手順は設計→codex レビュー→§0 反映→codex 委託→Claude が実物で検証。

## 0. レビューの経緯

| 版 | レビュー | 反映 |
|---|---|---|
| 第1版 | codex（read-only、2026-09-12 05:30 頃、12 件: 高 4・中 6・低 2、「いいえ」） | 第2版で反映（縦字形の横送りは 1000、単位換算、`addTextPath`、実数 vx、DW2 の実態、共有 subset の更新規則、ダッシュ間隔、変換の掛け順、PDFBox の挙動、vmtx 条件と ID 空間、回転フラグ、完了条件） |
| 第2版 | codex（read-only、05:40 頃、反映済み 8・不十分 4、新規 高 2・中 3・低 2、「いいえ」） | 第3版で反映。H1: §2-3 を「外部変換 × T(中心寄せ, ペン+縦原点) × S」の合成として各メソッド別に書き直し（`fm.getWidth` はユーザー座標、現行の中央寄せは `T × at`）。H2: `MyGVTGlyphVector` の二重変換（`at.concatenate` の字形単位の移動と最後の `createTransformedShape(at)`）を V4 の対象に明記。M1: W2 の入力は正の縦送り、`w1y = −advanceY` の符号契約を明記。M2: 試験用書体を作り直し（A の tsb を変えて「奇数幅 637 かつ原点 829」、VORG 無し CFF、2048 upm の期待値 879、ダッシュ 2 種 860/880）、丸めはゼロ方向切捨て。M3: 「レイアウト不変」を撤回し、ダッシュ由来の差分は説明・検証に。L1: `writeW2` の呼出元 5 か所と null/空の契約。L2: 例の字を分けた。加えて、2048 upm の書体で非空字形が 879 になるため DW2 の既定 vy を最頻値にする（§2-2）。第2版の「不十分 4 件」の対応: #3（`addTextPath` の対象漏れ）→ §1-3 の行と §2-3 のメソッド別合成、#5（DW2/W2 の現状説明）→ §1-3 の `writeWArray2` の行と §2-2 の省略規則、#8（変換順と ImageFont）→ §2-3、#12（完了条件）→ §3 の試験用書体の表と §4 の「完了は V4 まで」 |
| 第3版 | codex（read-only、05:50 頃、反映済み 11・不十分 1、新規 中 3・低 2、「いいえ」） | 第4版で反映。中: W2 の省略を「既定の cid は必ず書かない（区間を切る）」に統一し §3 の不在検査と整合。中: `318.5` の直接試験の入力配列を確定（§3）。中: IPAex の「全字形 879・W2 空」を非空字形に限定し、空白（空字形=880）が W2 に出ることを明記。低: 最頻値・疎配列・同率の規則を §2-2 に明記。低: 不十分 4 件の対応表を §0 に追加 |
| 第4版 | codex（read-only、05:55 頃、反映済み 3・不十分 2、新規 中 3・低 1、「いいえ」） | 第5版で反映。中: §3(e) を「vy の頻度からは未使用を除外、advance は現行の補完集計」に直し期待値を固定。中: IPAex の W2 空は「「組 だけ」の限定例にし、非空でも 859 の字形（U+2010 のハイフン等）があることを明記。中: vert 無しの fixture を `pgothic-novert-subset.ttf`（vert/vrt2 とも無し）に変更。低: §1-2 の表を「元単位」と「1000 単位・切捨て後」に分けた（HG/MS は upm 256、BIZen は VORG 1802/2048 → 879） |
| 第5版 | codex（read-only、06:00 頃、反映済み 11・残存誤記 1、新規 中 1・低 1、**「はい（条件付き）」**） | 第6版で条件を反映。中: vert 無し書体の A の tsb を 100 にし（yMax+tsb=829）、それでも 880 を返す検査を V1 に追加。低: `writeW2` の w0 の契約（使用 cid の w0 は必須、不整合は `IllegalArgumentException`）。残存誤記: 「2048 upm の書体は全字形 879」を訂正 |

## 1. 現状

### 1-1. 症状

IPA P ゴシック 14pt 縦組み `漢字、「組版」。（乙36）。` で、「（【 の字形が 0.43em 下に描かれて
次の字（組・乙・文）の上に重なる。閉じ括弧・読点・句点（送り 0.5em、字面が上寄せ）は無事。

### 1-2. 書体側の事実（font-pack 29 書体を fontTools で調査。値は縦字形（`vert` 置換後の GID）のもの）

値は元単位（upm）で示し、右端に 1000 単位へ正規化して切り捨てた縦原点を添える。

| 書体 | upm | 「 の縦字形の vmtx（縦送り, tsb）元単位 | hmtx（横送り）元単位 | 縦原点（VORG または yMax+tsb）元単位 | 1000 単位・切捨て |
|---|---|---|---|---|---|
| IPAPGothic（`ipagp.otf`、TrueType 輪郭） | 1000 | (570, 216) | 1000 | 450 | 450 |
| IPAPMincho | 1000 | (530, …) | 1000 | 410 | 410 |
| IPAGothic/IPAMincho | 1000 | (1000, …) | 1000 | 880 | 880 |
| IPAexMincho/Gothic | 2048 | (2048, …) | 2048 | 1802（ハイフン U+2010 の縦字形は 1760） | 879（ハイフンは 859） |
| Noto Sans/Serif JP、小塚、ヒラギノ、こぶりな（CFF、VORG） | 1000 | (1000, …) | 1000 | 880（VORG） | 880 |
| BIZen Antique（CFF、VORG） | 2048 | (2048, …) | 2048 | 1802（VORG） | 879 |
| HG 明朝L/ゴシックB、MS 明朝/ゴシック | 256 | (256, …) | 256 | 220 | 859 |
| Meiryo | 2048 | (2048, …) | 2048 | 字形ごと（1741〜1805） | 850〜881 |
| YOzFontP04 | 2048 | (1002, …) | 2048 | 1760 | 859 |
| Sawarabi Mincho | 1000 | — | — | vmtx が無意味（`vert` 自体が無い） | — |

OpenType の定義: 縦原点の y（横書き座標系）は、CFF 輪郭の書体で VORG があれば `VORG.vertOriginY(gid)`、
それ以外は `yMax(gid) + vmtx.topSideBearing(gid)`（VORG は TrueType 輪郭では無視する仕様）。
字形の縦原点はこの値であり、**880 は AJ1 系の慣習値にすぎない**。IPA P 系は比例縦送りの字形を
「送り箱の中に字面を置く」設計にしているため、縦原点が 450 になる。横送りは縦字形でも 1000 なので、
縦の中心寄せ（vx = w0/2 = 500）は現行の固定 500 と一致する。横送りが 1000 でない縦字形（upright の欧文・数字、
`vpal` 等）では一致しない。

### 1-3. pdfg2d の現状（定数 880 の箇所）

| 場所 | 何をしているか |
|---|---|
| `FontSource.DEFAULT_VERTICAL_ORIGIN = 880` | 定数の正本（2026-09-02 に集約） |
| `CIDUtils.writeWArray2` | `DW2 [880 -最頻縦送り]`（`WArray.buildFromWidths` が最頻値を既定にし、**既定値だけの区間**を省く。混在区間の中の既定値は残る）。書いた項には `vx=DEFAULT_H(500)`、`vy=880` を字形によらず書く。`WArray` は幅しか持たない |
| `FontUtils.drawText`（G2D: 画像・SVG・Graphics2D 出力の本文） | run 先頭で `gc.transform(T(−fontSize/2, 0.88·fontSize))`。`at = S(fontSize/1000)` にペン送りをユーザー座標で `preConcatenate`。字形ごとに `at2 = T((fontSize − fm.getWidth(gid))/2, 0) × at`（`fm.getWidth` はユーザー座標の幅） |
| `FontUtils.addTextPath`（文字影・文字クリップ用の輪郭。foliojet4 `AbstractTextBox` が使う） | run 先頭で `at.preConcatenate(T(−fontSize/2, 0.88·fontSize))`。字形ごとに `at2 = 外部 transform × T(中心寄せ, 0) × at` |
| `OpenTypeFont.verticalDashKerning` | 連続ダッシュ（U+2014/2015/2500/2501）の字面間隔を `advance(first) + minY(second) − maxY(first)` で計算。両字形の縦原点が同じと仮定 |
| `OpenTypeFont.adjustShape` の `VERTICAL_SHAPE_ADJUST`（値 1） | 「880 移動後の字面下端が縦送りを超えるとき字形を上へ平行移動する」救済。`ADJUST_VERTICAL = false` で無効 |
| foliojet4 `WebFontSubset.transform`（paged SVG の web font） | 字形座標で `translate(dx, DEFAULT_VERTICAL_ORIGIN)` |
| foliojet4 `MyGVTGlyphVector.getOutline`（SVG 内テキストの Batik 経路） | `at = S` に対し `at.concatenate(T(−fontSize/2, 0.88·fontSize))`（**字形座標に掛かるので実質 1/1000**）、字形ごとに `T(中心寄せ) × at`、最後に `path.createTransformedShape(at)` で**もう一度 `at` を掛けている** |

縦送り（w1）は `OpenTypeFont.getVAdvance` が vmtx から字形ごとに取っている（vmtx は `Direction.TB` かつ
有効な `vert`/`vrt2` 置換があるときだけ読む）。**送りは書体、原点は定数**という不整合が症状の本体。
`VorgTable` は `pdfg2d-font` に実装済み（`TableFactory` が読む、取得は `getVertOrigunY(gid)`）だが誰も使っていない。

字形の座標: `Glyph.path()` は 1000 upm 正規化・**y 下向き**（`Type2CharString` が `cy += -dy`、TrueType も同様）。
vmtx の bearing と `glyf` の yMax は**元フォント単位**。

## 2. 変更の形

### 2-1. 字形ごとの縦原点を `Font` の契約にする

```java
// net.zamasoft.pdfg2d.font.Font
/**
 * Returns the vertical origin of the glyph: the y coordinate, in the
 * horizontal design space and normalised to 1000 units per em, of the
 * point the vertical pen sits on (ISO 32000 "position vector" v_y).
 * The argument is this font's glyph id (the subset CID for an embedded
 * font). VORG for CFF outlines when the font has it, otherwise
 * yMax + vmtx top side bearing, otherwise the conventional 880.
 */
public default short getVerticalOrigin(final int gid) {
    return FontSource.DEFAULT_VERTICAL_ORIGIN;
}
```

- `OpenTypeFont`（引数は source の GID）: 縦書きでないとき、または vmtx を読んでいないとき（TB でない、
  または vert/vrt2 が無い）は 880。それ以外:
  - 輪郭が CFF で `Table.VORG` があれば `VorgTable.getVertOrigunY(gid)`（元単位）。
  - 無ければ元単位で `yMax + tsb`。yMax は TrueType なら `glyf` の記述（`GlyfDescript.getYMaximum()`）、
    CFF なら `getGlyph(gid).path().getBounds2D()` の `−minY × upm / 1000`（path は 1000 正規化・y 下向き。
    Java 21 の `Path2D.getBounds2D` は曲線の厳密な極値を返す）。空字形（path 無し・`isBlank`）は 880。
  - 元単位の値を `× 1000 / upm` で正規化し、`getVAdvance` と同じく **`(short)` キャスト（ゼロ方向切捨て）**。
    gid ごとに `ShortList` で cache。
  - 例（IPAPGothic、upm 1000）: 「 = 234 + 216 = 450、（ = 224 + 206 = 430、組 = 833 + 47 = 880。
    IPAexMincho（upm 2048）: 1710 + 92 = 1802 → 879（切捨て）。
- `OpenTypeEmbeddedCIDFont` / `OpenTypeEmbeddedCIDFontSubset`（引数は subset の CID）: `heights` と同じ規則で
  `origins`（cid→縦原点）を持つ。`initialize`/`register` に `origin` を足し、**縦書きの登録（`verticalMetrics`）
  だけが値を書き、横書きの登録は既存値を上書きしない**（横書きが先に登録した cid は 880 の仮値、あとから縦書きが
  実値へ更新。CID 0 も同じ）。`getVerticalOrigin(cid)` はそれを返す。`signature()`・subset 名の hash・
  `FontSubsetCache.Key` は変えない（共有 CFF プログラムは輪郭だけで、縦原点は輪郭に焼き込まない）。
- `OpenTypeCIDIdentityFont`（引数は GID）: `heights` と同様に `origins` を `drawTo` で記録し、書き出しに渡す。
- `CIDKeyedFont`（内蔵 AJ1 等、送り固定 1000）・`MissingCIDFont`・`SystemEmbeddedCIDFont`・`SystemCIDIdentityFont`
  （AWT フォント）は既定の 880 のまま（変更なし）。

### 2-2. W2 を字形ごとに書く

`WArray`（幅だけの構造）は変えない。`CIDUtils.writeWArray2(out, WArray)` を
`writeW2(out, short[] w0, short[] advanceY, short[] vy)` に置き換える。

- 契約: 配列は cid 順、`Short.MIN_VALUE` は未使用 cid。`advanceY` は**正の縦送り**（現行 `heights` と同じ）、
  PDF に書く `w1y = −advanceY`。`vy == null` は全 cid 880 とみなす。`advanceY == null || advanceY.length == 0`
  のときは現行どおり **DW2 も W2 も書かない**（横書き、`MissingCIDFont` の `new short[0]`）。
- `DW2 [vyDefault −advanceDefault]`: `advanceDefault` は現行どおり最頻縦送り（`WArray.buildFromWidths` の規則）、
  `vyDefault` は**使用 cid の最頻 vy**（2048 upm の書体は漢字・仮名・括弧など大半の字形が 879 になるので、880 固定だと大半の cid を列挙してしまう）。
- PDF の既定（ISO 32000-1 §9.7.4.3）は「w1y = DW2[1]、vy = DW2[0]、**vx = w0/2**（W/DW から得たその字形の横幅の半分）」。
  PDFBox 3.0.3（copperpdf4 の imageTest が使う）も同じ既定で位置ベクトルを適用する（codex が実 JAR で確認）。
- 最頻値の規則: `advanceDefault` は `WArray.buildFromWidths` に `advanceY` を渡して得る `getDefaultWidth()`（現行と同じ。
  未使用位置を直前値で補って数える癖も現行どおり）。`vyDefault` は **`vy[cid] != Short.MIN_VALUE` かつ
  `advanceY[cid] != Short.MIN_VALUE` の cid だけ**を数えた最頻値、同率なら小さい値、該当 cid が無ければ 880。
  配列長は `advanceY` と `vy` で同じ（違えば短い方を超える cid は未使用扱い）。**使用 cid（`advanceY[cid] != Short.MIN_VALUE`）の `w0[cid]` は必須**——`w0` が短い、または `Short.MIN_VALUE` なら `IllegalArgumentException`（vx を決められない）。
- 各 cid について `(advanceY[cid], vy[cid]) == (advanceDefault, vyDefault)` のものは**必ず書かない**（vx は既定の w0/2 で正しい。
  区間はここで切る。現行 `WArray` の「混在区間の中の既定値は書く」挙動は W2 には持ち込まない）。
  それ以外の cid だけ `c [w1y vx vy]` で書き、`vx = w0[cid] / 2.0` を**実数**で書く（`writeReal`。横幅 637 なら 318.5。
  `short` に丸めると省略した cid と明示した cid で位置が変わる）。連続する非既定 cid で 3 つ組が同じなら
  `cfirst clast w1y vx vy`、違えば `cfirst [w1y vx vy w1y vx vy …]` にまとめる（どちらも ISO 32000-1 §9.7.4.3 の形式）。
- 呼び出し側は `writeIdentityFont`（`OpenTypeCIDIdentityFont`、`SystemCIDIdentityFont`(w2=null)）、
  `writeEmbeddedFontProgram`（`OpenTypeEmbeddedCIDFont`）、`writeEmbeddedFont`（`SystemEmbeddedCIDFont`、
  `MissingCIDFont`(縦書きで `w2 = new short[0]`)）の 5 か所。`short[] vy` 引数を足し、`w0` は既に持っている `w`。

期待される差: 全角書体（Noto 等）では現行と同じく W2 はほぼ空。IPA P 系では 「（【 などに `[-570 500 450]` の形が並ぶ。
IPAex（2048 upm）は漢字・仮名・括弧が 879 なので DW2 が `[879 -1000]` になり、「組 だけの文書では W2 が空。空白（U+0020・U+3000 は空字形で 880）やハイフン（U+2010 の縦字形は 859）を含む文書ではその cid が W2 に出る。

### 2-3. G2D 描画（`FontUtils.drawText` と `addTextPath` の縦書き分岐）

完成形を「**外部変換 × T(中心寄せ x, ペン y + 縦原点) × S(fontSize/1000)**」と定め、メソッド別に組み立てる。
`fm.getWidth(gid)` と `fm.getAdvance(gid)` はユーザー座標（fontSize 換算済み）、`getVerticalOrigin` は 1000 単位。

- `drawText`: run 先頭の `gc.transform(T(−fontSize/2, 0.88·fontSize))` を `gc.transform(T(−fontSize/2, 0))` に。
  ペン送りは現行どおり `at.preConcatenate(T(0, dy))`（`at = S` にユーザー座標の送りを積む）。字形ごとに、**送りを積んだ後の
  `at`** から `at2 = T((fontSize − fm.getWidth(gid))/2, font.getVerticalOrigin(gid)·fontSize/1000) × at`
  （`AffineTransform.getTranslateInstance(...)` に `concatenate(at)`。現行の `width != 0` のときだけ複製する分岐は
  やめ、常に複製）。`ImageFont` 分岐は `at2 = T(0, 0.88·fontSize) × at` で現行と同じ位置にする。
- `addTextPath`: run 先頭の `at.preConcatenate(T(−fontSize/2, 0.88·fontSize))` を `T(−fontSize/2, 0)` に。字形ごとに
  `at2 = new AffineTransform(transform); at2.translate((fontSize − fm.getWidth(gid))/2, font.getVerticalOrigin(gid)·fontSize/1000); at2.concatenate(at)`
  （現行の `translate(width, 0)` はこの位置で外部変換の後・`at` の前に掛かっているので、y を足すだけ。常に複製）。
- 横書き分岐・横倒し（sideways）分岐は変えない。

### 2-4. `verticalDashKerning`

字面間隔を `advance(first) + (origin(second) + minY(second)) − (origin(first) + maxY(first))` にする
（各字形の字面は「縦原点 + path の y」、すべて 1000 単位）。同じ字形の反復では差が消えるので現行の試験は通ったままになる。
正値判定・丸め・上限の扱いは現行のまま。原点の異なるダッシュ 2 種の試験を足す（§3）。
**この変更はレイアウトに影響しうる**（`TextImpl` は kerning を advance から引く）。原点の異なるダッシュが
連続する文書では行の長さが変わる。

### 2-5. `adjustShape` の救済コードを撤去

`VERTICAL_SHAPE_ADJUST`（値 1）・`ADJUST_VERTICAL` と平行移動分岐を削除する（無効な上に、正しい縦原点があれば不要）。
`VERTICAL_SHAPE_ROTATE` は**値 2 のまま**残す（`shapeFlags` は Serializable な subset と署名に入るので値を詰めない）。
埋め込み CFF は正規化・回転後の path から再生成しているが、縦原点は path に加えず W2 に任せる。

### 2-6. 利用側（foliojet4、V4、Claude が行う）

- `WebFontSubset.transform`: 字形座標で `translate(dx, this.font.getVerticalOrigin(gid))`（現行の `DEFAULT_VERTICAL_ORIGIN` を置換）。
- `MyGVTGlyphVector.getOutline` の縦書き分岐: `FontUtils.drawText` と同じ合成に書き直す（run 先頭の `at.concatenate(T(…))` は
  字形座標に掛かっていて実質無効、最後の `createTransformedShape(at)` は `at` の二重適用）。SVG 内テキスト（`<svg><text writing-mode="tb">`）
  の fixture を作り、複数 font-size・複数字形で PDF 出力と位置が一致することを確認する。既存の挙動が壊れていた可能性があるので、
  直す前に現行出力を記録しておく。
- `AbstractTextBox` の文字影・クリップは `FontUtils.addTextPath` 経由なので pdfg2d 側の変更で直る。
- レイアウト: 行の位置・送り・詰めは縦原点に依存しないが、§2-4 のダッシュ kerning は依存する。ダッシュ由来の差分は説明し、
  必要なら golden を更新する。

## 3. 検証

試験用書体（用意済み、`pdfg2d-core/src/test/resources/`。fontTools で作成、期待値は fontTools で測定）:

| 書体 | 内容 | 期待値（縦字形の GID: 縦送り, 縦原点） |
|---|---|---|
| `pgothic-vert-subset.ttf`（IPA P ゴシック部分集合、TrueType、1000 upm、VORG 無し、vert 有り、32 glyph。IPA フォントライセンス v1.0） | 「(U+300C)=GID 28: 570, **450**。（(U+FF08)=26: 550, 430。【(U+3010)=30: 550, 430。組(U+7D44)=6: 1000, 880。乙(U+4E59)=3: 1000, 880。A(U+0041)=21: hmtx **637**、vmtx (1000, 100)（試験用に tsb を変更）→ 1000, **829**。—(U+2014)=24: 960, **860**（U+2015 は fontTools では同じ GID 24 に見えるが pdfg2d の cmap 読取では未定義になるので、テストは U+2014 と U+2500 で原点差を検査する）。─(U+2500)=2: 1000, 880。…(U+2026)=25: 1000, 880。、(U+3001)=22: 500, 880 |
| `sansjp-vorg-subset.otf`（Noto Sans JP 部分集合、CFF、VORG 有り、OFL。試験用に VORG 個別値を 1 つ） | 「=GID 28: 1000, **700（VORG。yMax+tsb は 880）**。（=24: 1000, 880。組=19: 1000, 880。A=3: hmtx 608、1000, 880 |
| `sansjp-novorg-subset.otf`（同、VORG を外し、「 の tsb を 500 に） | 「=GID 28: 1000, **730（CFF の path yMax 230 + tsb 500）**。他は 880 |
| `pdfg2d-demo/src/main/resources/ipaexm.ttf`（既存、2048 upm、変更しない） | 「=GID 7497: 2048→1000、原点 1802→**879**（切捨て）。組=2528 も 879。‐(U+2010) の縦字形 GID 12237 は yMax 992 + tsb 768 = 1760 → **859** |
| `pgothic-novert-subset.ttf`（IPA P ゴシック部分集合、`vert`/`vrt2` 無し。A の vmtx tsb を 100 にしてある） | 縦書きで読んでも vmtx を読まないので全字形 880。**A も 880**（vmtx を誤って採用すると 729+100=829 になる対照） |

| 種別 | 内容 |
|---|---|
| 単体（pdfg2d-core、V1） | `OpenTypeFont.getVerticalOrigin`: 上表の全値。横書きの Font は 880。`vert`/`vrt2` を**両方**持たない縦書きの Font（`pgothic-novert-subset.ttf`、用意済み。`ipaexm-novert2500.ttf` は U+2500 の置換だけを外した書体なので使わない）は vmtx を読まず 880 |
| 単体（pdfg2d-pdf、V2） | `writeW2` を配列入力で直接: (a) `w0=[1000,637,1000]`、`advanceY=[1000,1000,1000]`、`vy=[880,829,880]` → DW2 `[880 -1000]`、W2 に `1 [-1000 318.5 829]` だけ（`318.5` が実数）。(b) 全 cid が既定 → DW2 だけで W2 は `[ ]`。(c) 連続 3 cid が同じ非既定 3 つ組 → 範囲形式、隣接で違う → 配列形式。(d) `advanceY` が空/null → DW2 も W2 も無し、`vy` が null → 全 880。(e) 疎: `w0=[1000,MIN,MIN,1000,1000]`、`advanceY=[1000,MIN,MIN,500,500]`、`vy=[880,MIN,MIN,880,880]` → `advanceDefault` は現行 `WArray` の補完集計で **1000**（未使用位置を直前値 1000 で補って数える）、`vyDefault` は未使用を除いて 880、W2 は `3 4 -500 500 880`（範囲形式）だけ。未使用 cid は書かない。(f) `vy` 同率 → 小さい値。PDF を文字列で検査: pgothic（縦書き埋め込み）で 「 の cid に `-570 500 450`、A に `-1000 318.5 829`、組 の cid は W2 に**現れない**；ipaexm で 「組 だけ使うと DW2 `[879 -1000]` で W2 が `[ ]`、U+3000 を足すとその cid が `[-1000 vx 880]`、U+2010 を足すとその cid が `[-1000 vx 859]` で現れる；Identity（非埋め込み）も同じ；共有 subset の登録順（横→縦、縦→横）で origins が正しい。`OpenTypeEmbeddedFontSharingTest` の `/W2 ` 断定を「必要な cid だけ」へ |
| 単体（G2D、V3） | `FontUtils.drawText` と `addTextPath` を `G2DGC`（BufferedImage）で走らせ、pgothic の 「 の字面 bounds が `[pen, pen + 0.57·size]` に収まり 組 と重ならない；font-size 12/36 と外側の拡縮変換で相対位置が同じ；`ImageFont` と回転記号（`VERTICAL_SHAPE_ROTATE`）が不変；`verticalDashKerning`: pgothic の ―(860) と ─(880) の対で原点差が効く（同じ字形の対では現行と同じ） |
| 実物（V4、Claude） | copper4 の `dev/tools/punct-fuzz/punct_fuzz_v.py` を IPAP 2 書体で再走し、開き括弧の重なりが 0。PDF・PNG（G2D 経路、`output.type=image/png`）・paged SVG・SVG 内テキスト・文字影（`text-shadow`）・クリップの各出力で `漢字、「組版」。（乙36）。` を目視 |
| 回帰（V4） | foliojet4 全件（`DisplayListGoldenTest`。ダッシュ kerning 由来の差分は説明し必要なら golden 更新）、copperpdf4 `imageTest`（PDFBox 3.0.3。差分が出たら原因を特定し、基準の更新は Claude が判断）、`unitRemote`、doccheck |

## 4. 段階分け

| 段階 | 変更 | 次へ進む条件 |
|---|---|---|
| V1 | `Font.getVerticalOrigin` + `OpenTypeFont`（VORG/vmtx、単位・切捨て・空字形）+ 単体 | pdfg2d-core のテスト緑 |
| V2 | subset/identity の origins（更新規則）、`writeW2`（最頻 DW2・実数 vx・省略規則・null/空）、5 か所の呼出元、`adjustShape` の撤去 + PDF の W2 検査 | pdfg2d-pdf のテスト緑、`OpenTypeEmbeddedFontSharingTest` 更新 |
| V3 | `FontUtils.drawText`・`addTextPath`・`verticalDashKerning` + 単体 | pdfg2d-core のテスト緑 |
| V4 | foliojet4 の 2 か所 + 実物検証・回帰（Claude） | punct-fuzz 縦で IPAP の重なり 0、3 層ゲート緑（差分の説明が付く） |

V2 で完了扱いにしない。**完了は V4 まで**。

## 5. やらないこと

- 「；」「：」の後ろの詰めが字面を食う件（foliojet4 の cl-05 の詰め）。別設計 `copperpdf4/docs/design/2026-09-12-punctuation-ink-gap-design.md`。
- `vert` の無い書体（Sawarabi）に回転で縦字形を合成すること。
- `vpal`（比例縦送りの feature）の適用。今回は書体の既定の vmtx を尊重するだけ。
