# gc.text 再設計案 — 段落パイプライン

作成日: 2026-07-11。ステータス: **設計提案(未実装)**。
[`PROPOSALS.md`](./PROPOSALS.md) §C(ハイフネーション・bidi・GPOS/GSUB・ルビ・
カラーフォント)をすべて自然に受け止めることを目標に、破壊的変更を前提として
テキストパイプラインを再設計する。

## 1. 診断 — 現行設計の天井

現行は SAX 型の push パイプラインである:

```
CharacterHandler → TextShaper(字形化) → TextAtomizer(分割) → GlyphHandler(行組み)
```

ストリーミング(O(1) メモリ)・関心の分離・クラスタ対応データモデル
(`clusterLengths`)は美点だが、**1 パス push であること自体**が以下を原理的に塞ぐ:

1. **bidi(UAX #9)**: 行分割は論理順で行い、並べ替えは行確定後に行う(規則 L2)。
   1 パスでは「行が確定してから並べ替える」機会がない。
2. **文脈依存字形(GSUB/GPOS)**: リガチャ・マーク配置・アラビア語接続形は
   ラン全体を見てから決まる。`TextAtomizer` は直前 1 文字しか保持しない。
3. **最適行分割(Knuth-Plass)**: 段落全体のコスト最小化が前提。
   push 型は貪欲法しか書けない。
4. **ハイフネーション**: 「切った場合にハイフン字形を挿入する」という
   break-with-insertion の概念がストリームにない。
5. `Text` モデルに y 方向のオフセット/送りがなく、GPOS マーク配置を表現できない。

## 2. 中心となる知見 — 「段落」が正しいバッファ単位

業界と学術の到達点は一致している:

| 知見 | 出典 | 本設計での使い方 |
| --- | --- | --- |
| 行分割は box-glue-penalty モデルに帰着する | Knuth & Plass, *Breaking Paragraphs into Lines* (1981) | 分割・justify・ハイフン・禁則・ルビ余白を単一モデルで表現 |
| 字形化は「ラン単位の純関数」 | HarfBuzz | `shape(text, font, features) → GlyphRun` |
| bidi は「段落でレベル付与 → 論理順で行分割 → 行内で並べ替え」 | UAX #9 | Itemizer + LineFinisher に分置 |
| 分割機会はクラス対規則 | UAX #14 / JIS X 4051 | penalty の生成規則 |
| ランの itemization(script × 方向 × フォント) | Pango / Chrome LayoutNG | Itemizer |
| 禁則・ルビ掛け・和欧間隔は glue/penalty で表現できる | pTeX / LuaTeX-ja の実装史 | 日本語規則を JIS X 4051 → glue/penalty 写像で実装 |

文書全体をバッファする必要はない。**段落は有界メモリ**(高々数 KB)であり、
テキスト処理のあらゆる段落スコープアルゴリズムはここで完結する。
「文書は段落のストリーム、段落は一括処理」— これは FragmentedOutput の思想
(必要な単位だけ保持して流す)のテキスト版である。

## 3. 提案アーキテクチャ

パイプラインを「ハンドラの連鎖」から「**純関数のステージ列 + 素朴なデータ**」に変える。

```
Paragraph ─Itemizer→ Item[] ─Shaper→ GlyphRun[] ─LineBreaker→ Line[] ─LineFinisher→ PositionedLine[] ─→ GC.drawText
          (UAX#9/#24,        (HarfBuzz モデル,     (box-glue-penalty,      (bidi L2 並べ替え,
           フォント解決)       GSUB/GPOS はここ)     貪欲 or Knuth-Plass)     justify, 位置確定)
```

### 3.1 データモデル

```java
/** 論理順の段落: テキスト + スタイル区間 + インラインオブジェクト。 */
record Paragraph(char[] text, List<StyleSpan> spans,
        List<InlineObject> objects, ParagraphStyle style) {}

record StyleSpan(int begin, int end, FontStyle style) {}

/** itemization 結果: (フォント, script, bidi レベル, スタイル) が一定の最大区間。 */
record Item(int begin, int end, byte bidiLevel, Script script,
        FontSource font, FontStyle style) {}

/** 字形化結果 — HarfBuzz のバッファモデルをそのまま採る。 */
final class GlyphRun {
    Item item;
    int[] gids;
    int[] clusters;        // 各グリフの対応文字先頭(論理順インデックス)
    double[] xAdvances, yAdvances;   // GPOS 適用後の送り
    double[] xOffsets, yOffsets;     // マーク配置等の描画オフセット
}

/** 行分割の入力 — Knuth の boxes/glue/penalties。 */
sealed interface BreakNode permits Box, Glue, Penalty {}
record Box(double width, GlyphSlice glyphs) implements BreakNode {}
record Glue(double width, double stretch, double shrink) implements BreakNode {}
record Penalty(double width, int cost, boolean flagged /* ハイフン挿入 */,
        GlyphSlice insertion /* 分割時に挿入する字形 */) implements BreakNode {}

/** 出力: 位置確定済みの行。 */
record PositionedRun(GlyphRun run, int glyphBegin, int glyphEnd,
        double x, double y) {}
record PositionedLine(List<PositionedRun> runs,
        double ascent, double descent) {}
```

現行 `Text`(`clusterLengths` は byte 長)は HarfBuzz 式の
`clusters`(先頭インデックス)に置き換える。分割はクラスタ境界のみで行う
(cluster-safe breaking)。

### 3.2 ステージ

```java
interface Itemizer   { List<Item> itemize(Paragraph p); }
interface Shaper     { GlyphRun shape(Paragraph p, Item item); }
interface LineBreaker{ List<LineCandidate> breakLines(List<BreakNode> nodes, LineWidths widths); }
interface LineFinisher { PositionedLine finish(LineCandidate line, Justify justify); }
```

- **Itemizer**: `java.text.Bidi` でレベル付与(外部依存なし)、UAX #24 で script
  解決、`FontManager` でフォールバック解決。出力は最大等質ラン。
- **Shaper**: ラン単位の純関数。既定実装は純 Java
  (cmap + kern + GSUB リガチャ + GPOS ペア/マークを段階的に実装)。
  インターフェースを HarfBuzz のシグネチャに合わせておくことで、
  将来 harfbuzz JNI 実装を差し替え可能にする(pure-Java 方針は既定で維持)。
- **LineBreaker**: GlyphRun 列 + 分割規則(UAX #14 ∪ JIS X 4051)から
  BreakNode 列を作り、行長列に対して分割点を選ぶ。
  **既定は貪欲法(現行と同じ結果傾向・O(n))、オプションで Knuth-Plass**。
  同じ入力モデルなので切り替えは戦略の差し替えだけになる。
- **LineFinisher**: 行内 Item を bidi レベルで並べ替え(UAX #9 L2)、
  glue を配分して justify(欧文: 語間、和文: 字間 + JIS の優先順位)、
  座標を確定する。縦書き(TB)は yAdvance 主体になるだけで同一コード経路。

### 3.3 レンダリング

`GC.drawText(Text, x, y)` は残す(`Text` ≒ `PositionedRun`)。描画層は
「置くだけ」に徹する。PDF 側は xOffsets/yOffsets を TJ / Ts(rise)に写像する
(`PDFTextRenderer` の拡張は小さい)。`RecorderGC`/`ParallelPageRenderer` は
drawText を記録するだけなので**無改造**。段落単位の処理は並列化とも相性が良い。

## 4. §C 項目の写像 — 全項目に「自明な置き場所」がある

| §C 項目 | 置き場所 | 実装の骨子 |
| --- | --- | --- |
| ハイフネーション | LineBreaker | Liang パターン(libhyphen 形式)が flagged Penalty(+ハイフン字形の insertion)を注入するだけ |
| bidi / RTL | Itemizer + LineFinisher | UAX #9 の教科書どおりの二分置。`java.text.Bidi` で依存ゼロ |
| GPOS/GSUB | Shaper + GlyphRun | モデルが最初から offsets/advances 両軸を持つ。リガチャは clusters で表現済み |
| ルビ | **段落モデルの第一級ノード**(§4.1) | 不透明な箱ではなく `RubyAnnotation` ノードとして LineBreaker が理解する。詳細は §4.1 |
| カラーフォント | 描画層 | `drawGlyphForGid` フック(EmojiFont 前例)のまま。パイプライン無関係 |
| 禁則(既存) | penalty 生成規則 | JIS X 4051 の文字クラス対 → cost ∞/有限の Penalty。pTeX 系の定石 |
| justify・letter-spacing(既存) | Glue | 字間 glue の一様加算。XAdvances のアドホック調整を置換 |

### 4.1 ルビは pdfg2d 側の第一級ノードにする

現在の foliojet のルビは `RubyBox`(inline-block + nowrap)+ `RubyBodyBox` という
**汎用ボックスによる擬態**であり、原理的な制限を抱えている:

- 不可分なインラインボックスなので**行をまたげない**(長い熟語ルビが行末で組めない)
- **ルビ掛け(JIS X 4051)ができない** — 箱の幅が max(基底, ルビ) になるため、
  ルビが長いと前後の仮名にかけずに字間を押し広げる
- モノ/熟語/グループルビの配分規則を持たない
- pdfg2d がルビと知らないため、タグ付き PDF の **Ruby/RB/RT 構造要素**
  (PDF 1.5+、PDF/UA の読み上げ順に必要)を出力できない

ルビが行分割・字間 glue・行高と相互作用する以上、LineBreaker が pdfg2d に
ある新設計では、ルビは分割モデルが理解する第一級ノードでなければならない:

```java
/** 基底テキスト区間 + 注釈。列(column)= 基底1文字または熟語の1語。 */
record RubyAnnotation(int baseBegin, int baseEnd,
        Paragraph annotation,          // ルビ文字列(独立した小段落)
        Distribution dist,             // MONO / JUKUGO / GROUP
        Align align, Position position /* over/under */) {}
```

分割モデルへの写像:
- 各列は Box。**熟語ルビは列境界に有限コストの Penalty** を置き、行またぎ時は
  注釈を列へ再配分する(モノ/グループは Penalty ∞)
- ルビ掛けは「前後の隣接文字クラス(仮名には掛かる・漢字には掛からない)に
  依存する幅を持つ glue」— ノード生成時に隣接クラスから計算できる
- 行高への寄与は `PositionedLine` の ascent 拡張として LineFinisher が報告

foliojet に残るのはポリシーのみ: HTML `ruby/rb/rt/rp` の正規化、rt への CSS
カスケード(フォントサイズ等)、`ruby-position`/`ruby-align` のパラメータ写像。
`RubyBox`/`RubyBodyBox` の箱ハックは削除される。副産物として、pdfg2d が
ルビを意味として知るため Ruby/RB/RT 構造要素の自動タグが可能になり、
PDF/UA での読み順が正しくなる。

### 4.2 スクリプト対応の階層 — タイ文字は Tier 1、接続系は Tier 2

このパイプラインはスクリプトごとの要件を 3 つの既存拡張点に落とせる。
タイ文字を例にすると:

1. **単語分割(辞書ベース)** — タイ語は分かち書きしないため、行分割機会は
   辞書による単語分割で得る(UAX #14 自身が SA クラスを「辞書に委譲」と定義)。
   これは**ハイフネーション・禁則と同じスロット**(分割機会 = Penalty の生成器)
   に入る。純 Java のトライ + 単語表(libthai/ICU 由来、数万語)で足りる。
2. **マーク配置** — 母音・声調記号の積み上げは GPOS mark-to-base /
   mark-to-mark で、`GlyphRun` の y オフセット(§3.1)がまさに受け皿。
3. **クラスタ安全な分割** — 基底子音 + 結合記号は 1 クラスタになるため、
   「クラスタ境界でしか切らない」制約(§8)がそのまま正しさを保証する。

Shaper は Item(script 付き)単位の純関数なので、HarfBuzz と同様に
**script でシェーパを選択**できる(タイ語の SARA AM 分解などの固有処理は
タイ用シェーパステップに閉じる)。

| Tier | スクリプト | 必要なもの | 方針 |
| --- | --- | --- | --- |
| 1 | ラテン・CJK・タイ(+ラオ等の SA 系) | cmap + GSUB リガチャ + GPOS マーク + 辞書分割 | **純 Java で実装** |
| 2 | アラビア・インド系・モンゴル | 接続形・文脈置換・並べ替えの本格 shaping | 対象外。必要になったら HarfBuzz JNI シェーパの差し替え(§3.2)で対応 |

モンゴル文字は意図的に対象外とする(縦書き専用 + アラビア語級の接続 shaping で
費用対効果が合わない)。ただし設計上は Shaper の差し替えで閉じるため、
将来の追加をモデル変更なしで受けられる。

なお現状でもタイ語フォント(FT Meuang)の埋め込み・PDF/A 検証は通っている
(`PDFAVeraPDFComplianceTest`)。欠けているのは組版品質 — 単語境界での行分割と
マークの正確な積み上げ — であり、それがこの §4.2 の範囲である。

## 5. なぜこれが「シンプル」か

- ステージ間はすべて**不変に近い素朴なデータ**(record と配列)。
  ハンドラ連鎖・`flush()` の微妙な意味論・`FilterCharacterHandler`/
  `FilterGlyphHandler` の状態共有が全部消える。
- 各ステージは**単体でゴールデンテスト可能**な純関数
  (「この段落 → この Item 列」「この Item → この GlyphRun」)。
- 「最強」の根拠は新規性ではなく**収斂**である: TeX・HarfBuzz・ICU・
  LayoutNG が独立に到達した同型の構造であり、これ以上単純化すると
  §C のどれかが原理的に入らなくなる。

## 6. 移行計画(破壊的変更の範囲)

| 現行 | 処遇 |
| --- | --- |
| `Text` / `TextImpl` | `GlyphRun`/`PositionedRun` ベースに置換(y 軸追加、clusters 化) |
| `GlyphHandler`/`TextShaper`/`CharacterHandler` 連鎖 | 廃止 → ステージインターフェース |
| `TextAtomizer` + `TextBreakingRules` | penalty 生成規則として LineBreaker に吸収 |
| `PageLayoutGlyphHandler`(665 行) | LineBreaker + LineFinisher + 段組み配置に分解 |
| `TextLayoutHandler`(ACI 入力) | `Paragraph` ビルダーとして再実装(公開 API はほぼ維持可能) |
| `GC.drawText` / `PDFTextRenderer` / Recorder / 並列化 | ほぼ無傷(オフセット写像の追加のみ) |
| foliojet | 自前の分割実装(`TextUnitizer`/`JapaneseHyphenation`/`BuilderGlyphHandler` 等)を削除し、Paragraph 構築 + パラメータ供給側に再設計(§7) |

実施順の提案(各段で全テスト green を維持):

1. **モデル交換**: `GlyphRun`(clusters + 両軸 advance/offset)を導入し、
   既存パイプラインの内部表現を置換。出力は現状と同一(ゴールデンテストで担保)。
2. **段落バッファ化**: Paragraph/Itemizer を導入し、貪欲 LineBreaker +
   LineFinisher で現行の行組み結果を再現。ここで旧ハンドラ連鎖を削除。
3. **知見の投入**: bidi 並べ替え → GSUB リガチャ/GPOS ペア → ハイフネーション →
   (必要なら)Knuth-Plass、の順に個別追加。それぞれ独立にテストできる。

## 7. foliojet 統合 — 「使い切れていない」の正体と解消

現状の foliojet は pdfg2d のテキスト API を**ほぼ使っていない**。使っているのは
インターフェース(`CharacterHandler`/`GlyphHandler`)と字形化だけで、
分割・行組みは自前の並行実装を持つ:

| foliojet の自前実装 | pdfg2d の対応物 | 関係 |
| --- | --- | --- |
| `TextUnitizer` | `TextAtomizer` | ほぼ同一のコピー |
| `JapaneseHyphenation`(実体は禁則) | `JapaneseBreakingRules` | ほぼ同一のコピー |
| `BitSetCharacterSet` | `BitSetCharacterSet` | 完全な重複(両リポジトリに同名クラス) |
| `BuilderGlyphHandler` + `TwoPassBlockBuilder` | `PageLayoutGlyphHandler` | 行分割・幅確定の別実装 |
| `StyledTextUnitizer` | — | CSS 固有(white-space つぶし、word-spacing) |

重複が生じた原因は、現行 `PageLayoutGlyphHandler` が「完結した(=閉じた)
ページ組みレイアウタ」であり、CSS が要求するポリシー注入点
(インラインボックス、フロートによる行幅変化、white-space、vertical-align)を
持たないことにある。**API の境界が「機構」と「ポリシー」の間に引かれていない**。

本設計はこの境界を引き直す。機構(itemization・字形化・分割の力学・bidi・
justify の配分)は pdfg2d、ポリシー(CSS の解釈)は foliojet:

```
foliojet(ポリシー)                pdfg2d(機構)
──────────────────               ──────────────────
HTML/CSS → Paragraph 構築    →   Itemizer / Shaper
  ・white-space つぶし             LineBreaker / LineFinisher
  ・インライン端 = InlineObject
  ・置換要素 = InlineObject
行幅の供給(フロート・字下げ)  →   LineWidths(行ごとの可用幅)
CSS プロパティ → パラメータ    →   text-align/justify → Justify、
  line-break/word-break            禁則規則セットの選択、
  hyphens                          Hyphenator の有効化
PositionedLine を受領         ←   行ボックス積み上げ、vertical-align、
                                   装飾描画(下線・背景)は foliojet
```

このために設計へ以下を明示的に入れる(いずれも §3 のモデルの小さな拡張):

1. **インライン端マーカー**: `Paragraph` の `InlineObject` に
   「開始端/終了端」(border/padding/margin の幅と装飾参照)を含める。
   分割モデル上は幅を持つ Box になる(LayoutNG の open/close tag item と同型)。
2. **`LineWidths`**: 行番号→可用幅のコールバック。フロート回り込み・
   text-indent・shape-outside がここに集約される。
3. **固有幅の問い合わせ**: min-content / max-content は BreakNode 列から
   自明に計算できる(min = 強制分割間の最大不可分列、max = 全長)。
   `TwoPassBlockBuilder` の 2 パス測定が「同じ列への 2 つのクエリ」になる。
4. **ラン→スパンの遡及**: `PositionedRun` が由来 `StyleSpan` を保持し、
   foliojet が自分のインラインボックスへ対応付けて装飾を描けるようにする。

この接続の効果: foliojet 側の `TextUnitizer`・`JapaneseHyphenation`・
`BitSetCharacterSet`・`BuilderGlyphHandler` の分割ロジックは**削除**され、
bidi・ハイフネーション・GPOS/GSUB は境界の下に入るため
**foliojet は無改造で恩恵を受ける**(CSS プロパティをパラメータに写像するだけ)。

既知の難所: CSS `::first-line`(1 行目だけスタイルが違う)は
「分割結果がスタイルに依存し、スタイルが分割結果に依存する」循環を持つ。
LayoutNG と同じく「仮組み → 1 行目だけ再 itemize + 再字形化 → 再分割」で解く。
段落パイプラインが純関数なので、この再実行が安全にできる。

## 8. 既知のリスクと対処

- **行末の再字形化**: ハイフン挿入やアラビア語接続形は分割点で字形が変わる。
  対処: 分割確定後に該当ランだけ re-shape(LayoutNG と同じ)。
- **クラスタ境界の分割**: clusters を跨ぐ分割は禁止(モデルで強制)。
- **性能**: 段落バッファは高々数 KB。貪欲法は O(n)。字形化はラン単位で
  純関数なのでキャッシュ可能。現行より遅くなる要素は原理的にない
  (現行も全グリフを一度は流している)。
