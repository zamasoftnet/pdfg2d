# フォントの仕様

フォントの種類、設定ファイル(`fonts.xml`)、幅情報と CMap の形式を説明します。
**この文書は pdfg2d のもの**です——フォントの読み込み・字形の埋め込み・
幅の計算は pdfg2d が受け持ちます。

- サーバー製品への導入手順(どこに置くか、いつ反映されるか)は、
  **サーバー製品の説明書**にあります。
- 文書の中でどのフォントが選ばれるか(`font-family` の解決)は、
  **組版エンジン(FolioJet)の説明書**にあります。

### <a id="admin-config-fonts-policy">フォントの種類</a>

サポートするフォントには [コアフォント](#admin-config-fonts-core)、
[CID-Keyedフォント](#admin-config-fonts-cid-keyed)、 [CID Identityフォント](#admin-config-fonts-cid-identity)、
[埋め込みフォント](#admin-config-fonts-cid-embedded)、 の４種類があります。
コアフォントは常に利用可能ですが、埋め込みフォント、CID Identityフォント、CID-Keyedフォントのどれを利用するかは <span class="ioprop">output.pdf.fonts.policy</span> によって選択されます。 また、Copper
PDFの拡張CSSプロパティ <b>-cssj-font-policy</b>(FolioJet の説明書)
によってドキュメント中で指定することもできます。デフォルトではコアフォントとCID-Keyedフォントだけが使われます。

<span class="ioprop">output.pdf.fonts.policy</span>または<b>-cssj-font-policy</b>(FolioJet の説明書)にcid-keyed,
cid-identity, embeddedを指定することで、 それぞれCID-Keyedフォント、CID
Identityフォント、埋め込みフォントを切り替えることができます。
コアフォントは常に使用されますが、"-core"を指定することで除外することができます<span class="since">3.0.0</span>。
またスペース区切で複数指定することも可能です<span class="since">2.0.1</span>。 "embedded
cid-keyed"のようにスペース区切りで複数指定された場合は、最初に指定されたものから優先的に使用されます<span class="since">2.0.9</span>。ただし、コアフォントの優先度は常に最低となります。
また、outlinesを指定すると、PDFでembeddedフォントをテキストではなくアウトライン化した状態で表示することができます<span class="since">3.1.1</span>。

**PDF/AまたはPDF/X**(FolioJet の説明書)を出力する場合は、上記の設定に関わらず、埋め込みフォントだけが使われます<span class="since">2.1.0</span>。

outlinesを指定すると、埋め込みフォントが使われ、なおかつ全てのフォントがアウトライン化されます。<span class="since">3.1.1</span>。outlines指定はPDF/A・PDF/Xでも有効です。

### <a id="admin-config-fonts-core">コアフォント</a>

コアフォントは、ほとんどのPDF表示環境が標準的にサポートしているフォントで、フォントの埋め込みをせずに表示することができます。
14種類あることから、コア14フォントとも呼ばれます。
コアフォントはさらにletter(欧文文字・記号だけで構成されるもの)、symbol(欧文文字・記号以外の文字を含むもの)の2種類に分けられます。

次の表はコアフォントの一覧です。

**コアフォント**

| 正式名称 | 略称 | ファミリ名 | 太字 | 斜体 | 種類 |
| --- | --- | --- | --- | --- | --- |
| Times Roman | Times-Roman | Times |  |  | letter |
| Times Bold | Times-Bold | Times |  |  | letter |
| Times Italic | Times-Italic | Times |  |  | letter |
| Times Bold Itatdc | Times-BoldItalic | Times |  |  | letter |
| Helvetica | Helvetica | Helvetica |  |  | letter |
| Helvetica Bold | Helvetica-Bold | Helvetica |  |  | letter |
| Helvetica Oblique | Helvetica-Oblique | Helvetica |  |  | letter |
| Helvetica Bold Oblique | Helvetica-BoldOblique | Helvetica |  |  | letter |
| Courier | Courier | Courier |  |  | letter |
| Courier Bold | Courier-Bold | Courier |  |  | letter |
| Courier Oblique | Courier-Oblique | Courier |  |  | letter |
| Courier Bold Oblique | Courier-BoldOblique | Courier |  |  | letter |
| Symbol | Symbol | Symbol |  |  | symbol |
| ITC Zapf Dingbats | ZapfDingbats | ZapfDingbats |  |  | symbol |

CSSの<span class="cssprop">font-family</span>プロパティによるフォントの指定は、
正式名称、略称、ファミリ名のいずれでも可能です。 ファミリ名を使用した場合は、 <span class="cssprop">font-style</span>,
<span class="cssprop">font-weight</span>
プロパティの指定により、同じファミリ名を持つフォントのうち、スタイルと太さが最も一致するフォントが自動的に選択されます。

コアフォントのフォントメトリックス情報(文字の幅などの情報)がAFM(Adobe Font Metrics)ファイルとして、 **fonts/afms**ディレクトリに収められています。
これらのファイルをユーザーが変更する必要はありません。

各文字とPDF内で使用される文字コードとの対応表が **fonts/encodings**ディレクトリに収められています。
UNICODE.txtはletterフォントの文字名とユニコードとの対応表です。
symbol.txtとzdingbat.txtはそれぞれSymbol, ITC Zapf Dingbatsのためのユニコード対応表です。
これらのファイルをユーザーが変更する必要はありません。

以下はletterフォントで使用できる文字セット(WinAnsiEncodingエンコーディング)の文字一覧表です。
それぞれの文字は8ビット(16進数で2桁)のユニコードに対応しており、縦が上位桁、横が下位桁です。
ただし、80から9Fまでのコードで空いている部分は対応する文字がないことを表し、
下に4桁の16進数字がある文字には、同じ文字に対して2つのコードがあります。
例えばダブルダガー(&#x87;)を表示する場合はドキュメント中で&amp;#x87;または&amp;#x2021と表記してください。

<table class="chartable"
style="font-family: 'Times-Roman'; page-break-inside: avoid; page-break-before: avoid;">
<caption>WinAnsiEncodingの文字一覧</caption>
<tbody>
<tr class="code">
<td>-</td>
<td>0</td>
<td>1</td>
<td>2</td>
<td>3</td>
<td>4</td>
<td>5</td>
<td>6</td>
<td>7</td>
<td>8</td>
<td>9</td>
<td>A</td>
<td>B</td>
<td>C</td>
<td>D</td>
<td>E</td>
<td>F</td>
</tr>
<tr>
<td class="code">2</td>
<td>&#x20;</td>
<td>&#x21;</td>
<td>&#x22;</td>
<td>&#x23;</td>
<td>&#x24;</td>
<td>&#x25;</td>
<td>&#x26;</td>
<td>&#x27;</td>
<td>&#x28;</td>
<td>&#x29;</td>
<td>&#x2A;</td>
<td>&#x2B;</td>
<td>&#x2C;</td>
<td>&#x2D;</td>
<td>&#x2E;</td>
<td>&#x2F;</td>
</tr>
<tr>
<td class="code">3</td>
<td>&#x30;</td>
<td>&#x31;</td>
<td>&#x32;</td>
<td>&#x33;</td>
<td>&#x34;</td>
<td>&#x35;</td>
<td>&#x36;</td>
<td>&#x37;</td>
<td>&#x38;</td>
<td>&#x39;</td>
<td>&#x3A;</td>
<td>&#x3B;</td>
<td>&#x3C;</td>
<td>&#x3D;</td>
<td>&#x3E;</td>
<td>&#x3F;</td>
</tr>
<tr>
<td class="code">4</td>
<td>&#x40;</td>
<td>&#x41;</td>
<td>&#x42;</td>
<td>&#x43;</td>
<td>&#x44;</td>
<td>&#x45;</td>
<td>&#x46;</td>
<td>&#x47;</td>
<td>&#x48;</td>
<td>&#x49;</td>
<td>&#x4A;</td>
<td>&#x4B;</td>
<td>&#x4C;</td>
<td>&#x4D;</td>
<td>&#x4E;</td>
<td>&#x4F;</td>
</tr>
<tr>
<td class="code">5</td>
<td>&#x50;</td>
<td>&#x51;</td>
<td>&#x52;</td>
<td>&#x53;</td>
<td>&#x54;</td>
<td>&#x55;</td>
<td>&#x56;</td>
<td>&#x57;</td>
<td>&#x58;</td>
<td>&#x59;</td>
<td>&#x5A;</td>
<td>&#x5B;</td>
<td>&#x5C;</td>
<td>&#x5D;</td>
<td>&#x5E;</td>
<td>&#x5F;</td>
</tr>
<tr>
<td class="code">6</td>
<td>&#x60;</td>
<td>&#x61;</td>
<td>&#x62;</td>
<td>&#x63;</td>
<td>&#x64;</td>
<td>&#x65;</td>
<td>&#x66;</td>
<td>&#x67;</td>
<td>&#x68;</td>
<td>&#x69;</td>
<td>&#x6A;</td>
<td>&#x6B;</td>
<td>&#x6C;</td>
<td>&#x6D;</td>
<td>&#x6E;</td>
<td>&#x6F;</td>
</tr>
<tr>
<td class="code">7</td>
<td>&#x70;</td>
<td>&#x71;</td>
<td>&#x72;</td>
<td>&#x73;</td>
<td>&#x74;</td>
<td>&#x75;</td>
<td>&#x76;</td>
<td>&#x77;</td>
<td>&#x78;</td>
<td>&#x79;</td>
<td>&#x7A;</td>
<td>&#x7B;</td>
<td>&#x7C;</td>
<td>&#x7D;</td>
<td>&#x7E;</td>
<td>&#x7F;</td>
</tr>
<tr>
<td class="code">8</td>
<td>&#x80;<br />20AC
</td>
<td class="negative"></td>
<td>&#x82;<br />201A
</td>
<td>&#x83;<br />0192
</td>
<td>&#x84;<br />201E
</td>
<td>&#x85;<br />2026
</td>
<td>&#x86;<br />2020
</td>
<td>&#x87;<br />2021
</td>
<td>&#x88;<br />02C6
</td>
<td>&#x89;<br />2030
</td>
<td>&#x8A;<br />0160
</td>
<td>&#x8B;<br />2039
</td>
<td>&#x8C;<br />0152
</td>
<td class="negative"></td>
<td>&#x8E;<br />017D
</td>
<td class="negative"></td>
</tr>
<tr>
<td class="code">9</td>
<td class="negative"></td>
<td>&#x91;<br />2018
</td>
<td>&#x92;<br />2019
</td>
<td>&#x93;<br />201C
</td>
<td>&#x94;<br />201D
</td>
<td>&#x95;<br />2022
</td>
<td>&#x96;<br />2013
</td>
<td>&#x97;<br />2014
</td>
<td>&#x98;<br />02DC
</td>
<td>&#x99;<br />2122
</td>
<td>&#x9A;<br />0161
</td>
<td>&#x9B;<br />203A
</td>
<td>&#x9C;<br />0153
</td>
<td class="negative"></td>
<td>&#x9E;<br />017E
</td>
<td>&#x9F;<br />0178
</td>
</tr>
<tr>
<td class="code">A</td>
<td>&#xA0;</td>
<td>&#xA1;</td>
<td>&#xA2;</td>
<td>&#xA3;</td>
<td>&#xA4;</td>
<td>&#xA5;</td>
<td>&#xA6;</td>
<td>&#xA7;</td>
<td>&#xA8;</td>
<td>&#xA9;</td>
<td>&#xAA;</td>
<td>&#xAB;</td>
<td>&#xAC;</td>
<td>&#xAD;</td>
<td>&#xAE;</td>
<td>&#xAF;</td>
</tr>
<tr>
<td class="code">B</td>
<td>&#xB0;</td>
<td>&#xB1;</td>
<td>&#xB2;</td>
<td>&#xB3;</td>
<td>&#xB4;</td>
<td>&#xB5;</td>
<td>&#xB6;</td>
<td>&#xB7;</td>
<td>&#xB8;</td>
<td>&#xB9;</td>
<td>&#xBA;</td>
<td>&#xBB;</td>
<td>&#xBC;</td>
<td>&#xBD;</td>
<td>&#xBE;</td>
<td>&#xBF;</td>
</tr>
<tr>
<td class="code">C</td>
<td>&#xC0;</td>
<td>&#xC1;</td>
<td>&#xC2;</td>
<td>&#xC3;</td>
<td>&#xC4;</td>
<td>&#xC5;</td>
<td>&#xC6;</td>
<td>&#xC7;</td>
<td>&#xC8;</td>
<td>&#xC9;</td>
<td>&#xCA;</td>
<td>&#xCB;</td>
<td>&#xCC;</td>
<td>&#xCD;</td>
<td>&#xCE;</td>
<td>&#xCF;</td>
</tr>
<tr>
<td class="code">D</td>
<td>&#xD0;</td>
<td>&#xD1;</td>
<td>&#xD2;</td>
<td>&#xD3;</td>
<td>&#xD4;</td>
<td>&#xD5;</td>
<td>&#xD6;</td>
<td>&#xD7;</td>
<td>&#xD8;</td>
<td>&#xD9;</td>
<td>&#xDA;</td>
<td>&#xDB;</td>
<td>&#xDC;</td>
<td>&#xDD;</td>
<td>&#xDE;</td>
<td>&#xDF;</td>
</tr>
<tr>
<td class="code">E</td>
<td>&#xE0;</td>
<td>&#xE1;</td>
<td>&#xE2;</td>
<td>&#xE3;</td>
<td>&#xE4;</td>
<td>&#xE5;</td>
<td>&#xE6;</td>
<td>&#xE7;</td>
<td>&#xE8;</td>
<td>&#xE9;</td>
<td>&#xEA;</td>
<td>&#xEB;</td>
<td>&#xEC;</td>
<td>&#xED;</td>
<td>&#xEE;</td>
<td>&#xEF;</td>
</tr>
<tr>
<td class="code">F</td>
<td>&#xF0;</td>
<td>&#xF1;</td>
<td>&#xF2;</td>
<td>&#xF3;</td>
<td>&#xF4;</td>
<td>&#xF5;</td>
<td>&#xF6;</td>
<td>&#xF7;</td>
<td>&#xF8;</td>
<td>&#xF9;</td>
<td>&#xFA;</td>
<td>&#xFB;</td>
<td>&#xFC;</td>
<td>&#xFD;</td>
<td>&#xFE;</td>
<td>&#xFF;</td>
</tr>
</tbody>
</table>

以下はSymbolで使用できる文字の一覧です。 文字の下の数字は16進ユニコードです。

<table class="chartable"
style="font-family: 'Symbol' serif; page-break-inside: avoid; page-break-before: avoid;">
<caption>Symbolの文字一覧</caption>
<tr>
<td>&#x00A0;<br />0020<br />00A0
</td>
<td>&#x0021;<br />0021
</td>
<td>&#x2200;<br />2200
</td>
<td>&#x0023;<br />0023
</td>
<td>&#x2203;<br />2203
</td>
<td>&#x0025;<br />0025
</td>
<td>&#x0026;<br />0026
</td>
<td>&#x220B;<br />220B
</td>
<td>&#x0028;<br />0028
</td>
<td>&#x0029;<br />0029
</td>
<td>&#x2217;<br />2217
</td>
<td>&#x002B;<br />002B
</td>
<td>&#x002C;<br />002C
</td>
<td>&#x2212;<br />2212
</td>
<td>&#x002E;<br />002E
</td>
<td>&#x002F;<br />002F
</td>
</tr>
<tr>
<td>&#x0030;<br />0030
</td>
<td>&#x0031;<br />0031
</td>
<td>&#x0032;<br />0032
</td>
<td>&#x0033;<br />0033
</td>
<td>&#x0034;<br />0034
</td>
<td>&#x0035;<br />0035
</td>
<td>&#x0036;<br />0036
</td>
<td>&#x0037;<br />0037
</td>
<td>&#x0038;<br />0038
</td>
<td>&#x0039;<br />0039
</td>
<td>&#x003A;<br />003A
</td>
<td>&#x003B;<br />003B
</td>
<td>&#x003C;<br />003C
</td>
<td>&#x003D;<br />003D
</td>
<td>&#x003E;<br />003E
</td>
<td>&#x003F;<br />003F
</td>
</tr>
<tr>
<td>&#x2245;<br />2245
</td>
<td>&#x0391;<br />0391
</td>
<td>&#x0392;<br />0392
</td>
<td>&#x03A7;<br />03A7
</td>
<td>&#x0394;<br />0394<br />2206
</td>
<td>&#x0395;<br />0395
</td>
<td>&#x03A6;<br />03A6
</td>
<td>&#x0393;<br />0393
</td>
<td>&#x0397;<br />0397
</td>
<td>&#x0399;<br />0399
</td>
<td>&#x03D1;<br />03D1
</td>
<td>&#x039A;<br />039A
</td>
<td>&#x039B;<br />039B
</td>
<td>&#x039C;<br />039C
</td>
<td>&#x039D;<br />039D
</td>
<td>&#x039F;<br />039F
</td>
</tr>
<tr>
<td>&#x03A0;<br />03A0
</td>
<td>&#x0398;<br />0398
</td>
<td>&#x03A1;<br />03A1
</td>
<td>&#x03A3;<br />03A3
</td>
<td>&#x03A4;<br />03A4
</td>
<td>&#x03A5;<br />03A5
</td>
<td>&#x03C2;<br />03C2
</td>
<td>&#x03A9;<br />03A9<br />2126
</td>
<td>&#x039E;<br />039E
</td>
<td>&#x03A8;<br />03A8
</td>
<td>&#x0396;<br />0396
</td>
<td>&#x005B;<br />005B
</td>
<td>&#x2234;<br />2234
</td>
<td>&#x005D;<br />005D
</td>
<td>&#x22A5;<br />22A5
</td>
<td>&#x005F;<br />005F
</td>
</tr>
<tr>
<td>&#xF8E5;<br />F8E5
</td>
<td>&#x03B1;<br />03B1
</td>
<td>&#x03B2;<br />03B2
</td>
<td>&#x03C7;<br />03C7
</td>
<td>&#x03B4;<br />03B4
</td>
<td>&#x03B5;<br />03B5
</td>
<td>&#x03C6;<br />03C6
</td>
<td>&#x03B3;<br />03B3
</td>
<td>&#x03B7;<br />03B7
</td>
<td>&#x03B9;<br />03B9
</td>
<td>&#x03D5;<br />03D5
</td>
<td>&#x03BA;<br />03BA
</td>
<td>&#x03BB;<br />03BB
</td>
<td>&#x00B5;<br />00B5<br />03BC
</td>
<td>&#x03BD;<br />03BD
</td>
<td>&#x03BF;<br />03BF
</td>
</tr>
<tr>
<td>&#x03C0;<br />03C0
</td>
<td>&#x03B8;<br />03B8
</td>
<td>&#x03C1;<br />03C1
</td>
<td>&#x03C3;<br />03C3
</td>
<td>&#x03C4;<br />03C4
</td>
<td>&#x03C5;<br />03C5
</td>
<td>&#x03D6;<br />03D6
</td>
<td>&#x03C9;<br />03C9
</td>
<td>&#x03BE;<br />03BE
</td>
<td>&#x03C8;<br />03C8
</td>
<td>&#x03B6;<br />03B6
</td>
<td>&#x007B;<br />007B
</td>
<td>&#x007C;<br />007C
</td>
<td>&#x007D;<br />007D
</td>
<td>&#x223C;<br />223C
</td>
<td class="negative"></td>
</tr>
<tr>
<td>&#x20AC;<br />20AC
</td>
<td>&#x03D2;<br />03D2
</td>
<td>&#x2032;<br />2032
</td>
<td>&#x2264;<br />2264
</td>
<td>&#x2044;<br />2044<br />2215
</td>
<td>&#x221E;<br />221E
</td>
<td>&#x0192;<br />0192
</td>
<td>&#x2663;<br />2663
</td>
<td>&#x2666;<br />2666
</td>
<td>&#x2665;<br />2665
</td>
<td>&#x2660;<br />2660
</td>
<td>&#x2194;<br />2194
</td>
<td>&#x2190;<br />2190
</td>
<td>&#x2191;<br />2191
</td>
<td>&#x2192;<br />2192
</td>
<td>&#x2193;<br />2193
</td>
</tr>
<tr>
<td>&#x00B0;<br />00B0
</td>
<td>&#x00B1;<br />00B1
</td>
<td>&#x2033;<br />2033
</td>
<td>&#x2265;<br />2265
</td>
<td>&#x00D7;<br />00D7
</td>
<td>&#x221D;<br />221D
</td>
<td>&#x2202;<br />2202
</td>
<td>&#x2022;<br />2022
</td>
<td>&#x00F7;<br />00F7
</td>
<td>&#x2260;<br />2260
</td>
<td>&#x2261;<br />2261
</td>
<td>&#x2248;<br />2248
</td>
<td>&#x2026;<br />2026
</td>
<td>&#xF8E6;<br />F8E6
</td>
<td>&#xF8E7;<br />F8E7
</td>
<td>&#x21B5;<br />21B5
</td>
</tr>
<tr>
<td>&#x2135;<br />2135
</td>
<td>&#x2111;<br />2111
</td>
<td>&#x211C;<br />211C
</td>
<td>&#x2118;<br />2118
</td>
<td>&#x2297;<br />2297
</td>
<td>&#x2295;<br />2295
</td>
<td>&#x2205;<br />2205
</td>
<td>&#x2229;<br />2229
</td>
<td>&#x222A;<br />222A
</td>
<td>&#x2283;<br />2283
</td>
<td>&#x2287;<br />2287
</td>
<td>&#x2284;<br />2284
</td>
<td>&#x2282;<br />2282
</td>
<td>&#x2286;<br />2286
</td>
<td>&#x2208;<br />2208
</td>
<td>&#x2209;<br />2209
</td>
</tr>
<tr>
<td>&#x2220;<br />2220
</td>
<td>&#x2207;<br />2207
</td>
<td>&#xF6DA;<br />F6DA
</td>
<td>&#xF6D9;<br />F6D9
</td>
<td>&#xF6DB;<br />F6DB
</td>
<td>&#x220F;<br />220F
</td>
<td>&#x221A;<br />221A
</td>
<td>&#x22C5;<br />22C5
</td>
<td>&#x00AC;<br />00AC
</td>
<td>&#x2227;<br />2227
</td>
<td>&#x2228;<br />2228
</td>
<td>&#x21D4;<br />21D4
</td>
<td>&#x21D0;<br />21D0
</td>
<td>&#x21D1;<br />21D1
</td>
<td>&#x21D2;<br />21D2
</td>
<td>&#x21D3;<br />21D3
</td>
</tr>
<tr>
<td>&#x25CA;<br />25CA
</td>
<td>&#x2329;<br />2329
</td>
<td>&#xF8E8;<br />F8E8
</td>
<td>&#xF8E9;<br />F8E9
</td>
<td>&#xF8EA;<br />F8EA
</td>
<td>&#x2211;<br />2211
</td>
<td>&#xF8EB;<br />F8EB
</td>
<td>&#xF8EC;<br />F8EC
</td>
<td>&#xF8ED;<br />F8ED
</td>
<td>&#xF8EE;<br />F8EE
</td>
<td>&#xF8EF;<br />F8EF
</td>
<td>&#xF8F0;<br />F8F0
</td>
<td>&#xF8F1;<br />F8F1
</td>
<td>&#xF8F2;<br />F8F2
</td>
<td>&#xF8F3;<br />F8F3
</td>
<td>&#xF8F4;<br />F8F4
</td>
</tr>
<tr>
<td class="negative"></td>
<td>&#x232A;<br />232A
</td>
<td>&#x222B;<br />222B
</td>
<td>&#x2320;<br />2320
</td>
<td>&#xF8F5;<br />F8F5
</td>
<td>&#x2321;<br />2321
</td>
<td>&#xF8F6;<br />F8F6
</td>
<td>&#xF8F7;<br />F8F7
</td>
<td>&#xF8F8;<br />F8F8
</td>
<td>&#xF8F9;<br />F8F9
</td>
<td>&#xF8FA;<br />F8FA
</td>
<td>&#xF8FB;<br />F8FB
</td>
<td>&#xF8FC;<br />F8FC
</td>
<td>&#xF8FD;<br />F8FD
</td>
<td>&#xF8FE;<br />F8FE
</td>
<td class="negative"></td>
</tr>
</table>

以下はZapfDingbatsで使用できる文字の一覧です。 文字の下の数字は16進ユニコードです。

<table class="chartable"
style="font-family: 'ZapfDingbats' serif; page-break-inside: avoid; page-break-before: avoid;">
<caption>ZapfDingbatsの文字一覧</caption>
<tr>
<td>&#x0020;<br />0020<br />00A0
</td>
<td>&#x2701;<br />2701
</td>
<td>&#x2702;<br />2702
</td>
<td>&#x2703;<br />2703
</td>
<td>&#x2704;<br />2704
</td>
<td>&#x260E;<br />260E
</td>
<td>&#x2706;<br />2706
</td>
<td>&#x2707;<br />2707
</td>
<td>&#x2708;<br />2708
</td>
<td>&#x2709;<br />2709
</td>
<td>&#x261B;<br />261B
</td>
<td>&#x261E;<br />261E
</td>
<td>&#x270C;<br />270C
</td>
<td>&#x270D;<br />270D
</td>
<td>&#x270E;<br />270E
</td>
<td>&#x270F;<br />270F
</td>
</tr>
<tr>
<td>&#x2710;<br />2710
</td>
<td>&#x2711;<br />2711
</td>
<td>&#x2712;<br />2712
</td>
<td>&#x2713;<br />2713
</td>
<td>&#x2714;<br />2714
</td>
<td>&#x2715;<br />2715
</td>
<td>&#x2716;<br />2716
</td>
<td>&#x2717;<br />2717
</td>
<td>&#x2718;<br />2718
</td>
<td>&#x2719;<br />2719
</td>
<td>&#x271A;<br />271A
</td>
<td>&#x271B;<br />271B
</td>
<td>&#x271C;<br />271C
</td>
<td>&#x271D;<br />271D
</td>
<td>&#x271E;<br />271E
</td>
<td>&#x271F;<br />271F
</td>
</tr>
<tr>
<td>&#x2720;<br />2720
</td>
<td>&#x2721;<br />2721
</td>
<td>&#x2722;<br />2722
</td>
<td>&#x2723;<br />2723
</td>
<td>&#x2724;<br />2724
</td>
<td>&#x2725;<br />2725
</td>
<td>&#x2726;<br />2726
</td>
<td>&#x2727;<br />2727
</td>
<td>&#x2605;<br />2605
</td>
<td>&#x2729;<br />2729
</td>
<td>&#x272A;<br />272A
</td>
<td>&#x272B;<br />272B
</td>
<td>&#x272C;<br />272C
</td>
<td>&#x272D;<br />272D
</td>
<td>&#x272E;<br />272E
</td>
<td>&#x272F;<br />272F
</td>
</tr>
<tr>
<td>&#x2730;<br />2730
</td>
<td>&#x2731;<br />2731
</td>
<td>&#x2732;<br />2732
</td>
<td>&#x2733;<br />2733
</td>
<td>&#x2734;<br />2734
</td>
<td>&#x2735;<br />2735
</td>
<td>&#x2736;<br />2736
</td>
<td>&#x2737;<br />2737
</td>
<td>&#x2738;<br />2738
</td>
<td>&#x2739;<br />2739
</td>
<td>&#x273A;<br />273A
</td>
<td>&#x273B;<br />273B
</td>
<td>&#x273C;<br />273C
</td>
<td>&#x273D;<br />273D
</td>
<td>&#x273E;<br />273E
</td>
<td>&#x273F;<br />273F
</td>
</tr>
<tr>
<td>&#x2740;<br />2740
</td>
<td>&#x2741;<br />2741
</td>
<td>&#x2742;<br />2742
</td>
<td>&#x2743;<br />2743
</td>
<td>&#x2744;<br />2744
</td>
<td>&#x2745;<br />2745
</td>
<td>&#x2746;<br />2746
</td>
<td>&#x2747;<br />2747
</td>
<td>&#x2748;<br />2748
</td>
<td>&#x2749;<br />2749
</td>
<td>&#x274A;<br />274A
</td>
<td>&#x274B;<br />274B
</td>
<td>&#x25CF;<br />25CF
</td>
<td>&#x274D;<br />274D
</td>
<td>&#x25A0;<br />25A0
</td>
<td>&#x274F;<br />274F
</td>
</tr>
<tr>
<td>&#x2750;<br />2750
</td>
<td>&#x2751;<br />2751
</td>
<td>&#x2752;<br />2752
</td>
<td>&#x25B2;<br />25B2
</td>
<td>&#x25BC;<br />25BC
</td>
<td>&#x25C6;<br />25C6
</td>
<td>&#x2756;<br />2756
</td>
<td>&#x25D7;<br />25D7
</td>
<td>&#x2758;<br />2758
</td>
<td>&#x2759;<br />2759
</td>
<td>&#x275A;<br />275A
</td>
<td>&#x275B;<br />275B
</td>
<td>&#x275C;<br />275C
</td>
<td>&#x275D;<br />275D
</td>
<td>&#x275E;<br />275E
</td>
<td class="negative"></td>
</tr>
<tr>
<td>&#xF8D7;<br />F8D7
</td>
<td>&#xF8D8;<br />F8D8
</td>
<td>&#xF8D9;<br />F8D9
</td>
<td>&#xF8DA;<br />F8DA
</td>
<td>&#xF8DB;<br />F8DB
</td>
<td>&#xF8DC;<br />F8DC
</td>
<td>&#xF8DD;<br />F8DD
</td>
<td>&#xF8DE;<br />F8DE
</td>
<td>&#xF8DF;<br />F8DF
</td>
<td>&#xF8E0;<br />F8E0
</td>
<td>&#xF8E1;<br />F8E1
</td>
<td>&#xF8E2;<br />F8E2
</td>
<td>&#xF8E3;<br />F8E3
</td>
<td>&#xF8E4;<br />F8E4
</td>
<td class="negative"></td>
<td class="negative"></td>
</tr>
<tr>
<td class="negative"></td>
<td>&#x2761;<br />2761
</td>
<td>&#x2762;<br />2762
</td>
<td>&#x2763;<br />2763
</td>
<td>&#x2764;<br />2764
</td>
<td>&#x2765;<br />2765
</td>
<td>&#x2766;<br />2766
</td>
<td>&#x2767;<br />2767
</td>
<td>&#x2663;<br />2663
</td>
<td>&#x2666;<br />2666
</td>
<td>&#x2665;<br />2665
</td>
<td>&#x2660;<br />2660
</td>
<td>&#x2460;<br />2460
</td>
<td>&#x2461;<br />2461
</td>
<td>&#x2462;<br />2462
</td>
<td>&#x2463;<br />2463
</td>
</tr>
<tr>
<td>&#x2464;<br />2464
</td>
<td>&#x2465;<br />2465
</td>
<td>&#x2466;<br />2466
</td>
<td>&#x2467;<br />2467
</td>
<td>&#x2468;<br />2468
</td>
<td>&#x2469;<br />2469
</td>
<td>&#x2776;<br />2776
</td>
<td>&#x2777;<br />2777
</td>
<td>&#x2778;<br />2778
</td>
<td>&#x2779;<br />2779
</td>
<td>&#x277A;<br />277A
</td>
<td>&#x277B;<br />277B
</td>
<td>&#x277C;<br />277C
</td>
<td>&#x277D;<br />277D
</td>
<td>&#x277E;<br />277E
</td>
<td>&#x277F;<br />277F
</td>
</tr>
<tr>
<td>&#x2780;<br />2780
</td>
<td>&#x2781;<br />2781
</td>
<td>&#x2782;<br />2782
</td>
<td>&#x2783;<br />2783
</td>
<td>&#x2784;<br />2784
</td>
<td>&#x2785;<br />2785
</td>
<td>&#x2786;<br />2786
</td>
<td>&#x2787;<br />2787
</td>
<td>&#x2788;<br />2788
</td>
<td>&#x2789;<br />2789
</td>
<td>&#x278A;<br />278A
</td>
<td>&#x278B;<br />278B
</td>
<td>&#x278C;<br />278C
</td>
<td>&#x278D;<br />278D
</td>
<td>&#x278E;<br />278E
</td>
<td>&#x278F;<br />278F
</td>
</tr>
<tr>
<td>&#x2790;<br />2790
</td>
<td>&#x2791;<br />2791
</td>
<td>&#x2792;<br />2792
</td>
<td>&#x2793;<br />2793
</td>
<td>&#x2794;<br />2794
</td>
<td>&#x2192;<br />2192
</td>
<td>&#x2194;<br />2194
</td>
<td>&#x2195;<br />2195
</td>
<td>&#x2798;<br />2798
</td>
<td>&#x2799;<br />2799
</td>
<td>&#x279A;<br />279A
</td>
<td>&#x279B;<br />279B
</td>
<td>&#x279C;<br />279C
</td>
<td>&#x279D;<br />279D
</td>
<td>&#x279E;<br />279E
</td>
<td>&#x279F;<br />279F
</td>
</tr>
<tr>
<td>&#x27A0;<br />27A0
</td>
<td>&#x27A1;<br />27A1
</td>
<td>&#x27A2;<br />27A2
</td>
<td>&#x27A3;<br />27A3
</td>
<td>&#x27A4;<br />27A4
</td>
<td>&#x27A5;<br />27A5
</td>
<td>&#x27A6;<br />27A6
</td>
<td>&#x27A7;<br />27A7
</td>
<td>&#x27A8;<br />27A8
</td>
<td>&#x27A9;<br />27A9
</td>
<td>&#x27AA;<br />27AA
</td>
<td>&#x27AB;<br />27AB
</td>
<td>&#x27AC;<br />27AC
</td>
<td>&#x27AD;<br />27AD
</td>
<td>&#x27AE;<br />27AE
</td>
<td>&#x27AF;<br />27AF
</td>
</tr>
<tr>
<td class="negative"></td>
<td>&#x27B1;<br />27B1
</td>
<td>&#x27B2;<br />27B2
</td>
<td>&#x27B3;<br />27B3
</td>
<td>&#x27B4;<br />27B4
</td>
<td>&#x27B5;<br />27B5
</td>
<td>&#x27B6;<br />27B6
</td>
<td>&#x27B7;<br />27B7
</td>
<td>&#x27B8;<br />27B8
</td>
<td>&#x27B9;<br />27B9
</td>
<td>&#x27BA;<br />27BA
</td>
<td>&#x27BB;<br />27BB
</td>
<td>&#x27BC;<br />27BC
</td>
<td>&#x27BD;<br />27BD
</td>
<td>&#x27BE;<br />27BE
</td>
<td class="negative"></td>
</tr>
</table>

### CIDフォント

欧文以外の文字をPDFに含める場合は、CIDフォントを用います。 フォントを埋め込む方法と、フォントを埋め込まない方法があり、
フォントを埋め込まない方法には、さらにCID IdentityとCID-Keyedフォントの２種類の方法があります。
どの種類のフォントを利用するかは、入出力プロパティ<span class="ioprop">output.pdf.fonts.policy</span>の設定によります。

埋め込みフォントまたは CID Identity を使うには、フォントをインストールしておく必要があります。
サーバー製品の出荷時の状態では、 **truetype ディレクトリ**(サーバー製品の説明書)に配置したフォントが自動的に読み込まれるようにfonts.xmlが設定されています。

#### <a id="admin-config-fonts-cid-embedded">埋め込みフォント</a>

PDFにフォントの字体データそのものを埋め込む方式で、 環境に関係なく、確実に同じ字体で文字が表示されることが保証されます。

フォントを埋め込む場合、文書中で使用されている文字のフォントだけをPDFに含めるため、
ファイルサイズは最小限に抑えられますが、出力されたPDFは編集や加工には適しません。

表示や印刷の見栄えに厳密さが求められる場合や、 広く配布する文書、あるいは長期保存する文書に適しています。

#### <a id="admin-config-fonts-cid-identity">CID Identity</a>

フォントの埋め込みをせず、グリフID(フォントファイルに含まれる文字の番号)をそのままPDF内に記述する方式です。 Copper
PDFの動作環境と、PDFを表示する環境に同一のフォントがインストールされている必要があります。
グリフIDは特定のフォントファイルに依存するため、 PDFを表示する環境にインストールされたフォントファイルが異なれば、
似たような書体のフォントであっても文字化けが発生するか、全く表示できなくなります。

同一のマシン上や、同じ組織内での編集や加工、印刷を目的とする文書に適しています。
フォントの埋め込みをする場合にくらべて、出力されるPDFのサイズは小さくなります。

#### <a id="admin-config-fonts-cid-keyed">CID-Keyedフォント</a>

CID Identityフォント同様にフォントの埋め込みをしませんが、
Adobe社により公開されているコード体系(CMap)を使用します。

PDFを表示する環境で利用可能な、書体の近いフォントが自動的に選ばれるので、 特定のフォントファイルに依存することはありません。
ただし、使用できる文字はAdobe社が提供するCMapファイルで定義された文字に限られます。
また、全ての環境でPDFを表示できることが保証されるものではありません。

特定の言語環境のWindowsやMacOS、特定のバージョン以降のAdobe Readerなど、
表示・編集・加工する環境が比較的限られ、表示や印刷の見栄えに厳密さが求められない文書には有効です。
PDFのファイルサイズは最も小さくなります。

##### PANOSEコード

CID-Keyedフォントの場合、表示環境にインストールされた、書体の近いフォントを選ぶために、
PANOSE-1(パノーズ)という10桁のコードを使います。
PDFでは、さらにクラスID・サブクラスIDというコードが先頭に付けられるため、12桁となります。

クラスID・サブクラスIDと、PANOSE-1コードはTrueTypeまたはOpenTypeフォントから、ツールを使って取得することができます。
[Microsoft社が配布しているFontTools.exe](http://download.microsoft.com/download/f/f/a/ffae9ec6-3bf6-488a-843d-b96d552fd815/FontTools.exe)に含まれるttfdump.exeというツールを利用すると便利です。

以下はmsgothic.ttcからフォントの情報を、out.txtというテキストファイルに書き出すコマンドです。
-c1は.ttcファイル(複数のTrueTypeフォントを含むファイル) の1番目のフォント情報を取得するためのオプションで、
2番目のフォント情報を取得する場合は-c2のように指定してください。 .ttfまたは.otfファイルではこのオプションは不要です。

```
C:\TTFDump>ttfdump.exe "C:\Windows\Fonts\msgothic.ttc" -c1 > out.txt
```

以下の部分(OS/2テーブル)のsFamilyClassがクラスID・サブクラスIDで、PANOSEが10桁のPANOSE-1コードです。
この場合、フォント設定ファイル中ではPANOSEコードを
<tt>8 1 2 11 6 9 7 2 5 8 2 4</tt>
と指定してください。

```
... 略
'OS/2' Table - OS/2 and Windows Metrics
---------------------------------------
Size = 96 bytes (expecting 96 bytes)
  'OS/2' version:           3
... 略 ...
  yStrikeoutSize:           13
  yStrikeoutPosition:       66
  sFamilyClass:             8    subclass = 1
  PANOSE:                   2 11  6  9  7  2  5  8  2  4
  Unicode Range 1( Bits 0 - 31 ): E00002FF
  Unicode Range 2( Bits 32- 63 ): 6AC7FDFB
略 ...
```

###### 参考情報

<dl>

<dt><a target="_blank" href="https://monotype.github.io/panose/pan1.htm">https://monotype.github.io/panose/pan1.htm</a></dt>
<dd>PANOSE-1コードの仕様です。</dd>

<dt><a target="_blank" href="https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6OS2.html">https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6OS2.html</a></dt>
<dd>TrueTypeフォントのOS/2テーブルの仕様です。</dd>

<dt><a target="_blank" href="http://download.microsoft.com/download/f/f/a/ffae9ec6-3bf6-488a-843d-b96d552fd815/FontTools.exe">http://download.microsoft.com/download/f/f/a/ffae9ec6-3bf6-488a-843d-b96d552fd815/FontTools.exe</a></dt>
<dd>Microsoft社のFontToolsのダウンロードURLです。ここでダウンロードしたFontTools.exeを実行すると、展開されたファイルの中にttfdump.exeがあります。</dd>

</dl>

##### <a id="admin-config-fonts-warray">フォント幅情報ファイル</a>

文字列をレイアウトするとき、各文字の幅や基底線の情報が必要です。
CID-Keyedフォントは、フォントそのものは含まないフォント幅情報ファイルをもとにレイアウトします。
フォント幅情報ファイルはfonts/warraysディレクトリに既定のものが用意されています。

出荷時のfonts.xmlの設定では、文書中がMincho, Gothicというフォント名で、
明朝体とゴシック体のフォントが使われるようになっています。 それぞれserif,
sans-serifというCSS総称フォント名にも結び付けられており、 またMinchoはデフォルトのフォントです。
つまり、CID-Keyedフォントが利用する設定では、対応するフォントがない場合は全てMinchoとなります。
これらのフォントは、実際には多くの日本語表示環境に入っていると考えられる、
等幅の明朝体、ゴシック体フォントが使用されるようにPANOSEが設定されています。

2.1.10 以降では、MS明朝、MSゴシック、MS P明朝、MS Pゴシック、MS UI
Gothicの幅情報が用意されています。 出荷時のfonts.xmlの設定では、それぞれの名前で文書中から使用できるようになっています。
これらのフォントを使うと、日本語Windows環境を対象とした軽量なPDFを生成することができます。
ただし、MS系フォントが入っていない環境では、隣り合う文字が重なったり、離れすぎてしまったりという現象が発生します。

手持ちのフォントから幅情報を抽出し、新たにフォント幅情報ファイルを作成する場合は、付属のツールを使ってください。
以下のように、javaコマンドで直接net.zamasoft.pdfg2d.pdf.tools.WArrayToolを実行してください。
クラスパスとクラス名に続く引数はそれぞれ、cmapファイル、Javaエンコーディング名、フォントファイルです。
複数のフォントを含むファイル(.ttc)の場合、さらにフォントの番号を続けることができます。

```bash
java -cp lib/*.jar \
	net.zamasoft.pdfg2d.pdf.tools.WArrayTool \
	conf/profiles/fonts/cmaps/UniJIS-UTF16-H \
	UTF-16BE \
	/usr/share/fonts/truetype/kochi/kochi-mincho.ttf \
	> conf/profiles/fonts/warrays/kochi-mincho.txt
```

### フォントファイルの種類

埋め込みフォント、CID Identityフォントを使用する場合、 あるいは後述するようにCID-Keyedフォントのためにフォントの[幅情報を抽出](#admin-config-fonts-warray)するためには、 Copper
PDFが動作する環境にフォントファイルがインストールされている必要があります。

サポートするフォントファイルの種類は以下の通りです。

<dl>

<dt>TrueType "OpenType(TrueType flavor)"</dt>
<dd>一般的にTrueTypeと呼ばれるフォントファイルです。 .ttfまたは.ttc(複数のフォントを含むもの)という拡張子のファイルとして配布されています。</dd>

<dt>OpenType CFF/Type2</dt>
<dd>一般的に単にOpenTypeと呼ばれる、 CFF/Type2形式のOpenTypeフォントファイルです。 .otfという拡張子のファイルとして配布されています。 2.0.3 からサポートされました。</dd>

</dl>

また、Copper
PDFは上記のフォントファイル以外に、Java実行環境によりサポートされるフォントファイルを使用することができます。
SunのJava実行環境(1.5.0以降)はTrueTypeに加えてType1フォント(.pfa, .pfb)、
F3フォント(.f3b)をサポートしています。

### <a id="admin-config-fonts">フォント設定ファイル</a>

PDF出力に使用するフォントの情報はフォント設定ファイル(**fonts.xml**)
に記述してください。 フォント設定ファイルはXML形式で、ルート要素はfontsです。
フォント設定ファイルには各種ファイルのファイルパスの情報も含まれており、 ファイルパスはフォント設定ファイルからの相対パスとなります。

fonts要素には次の要素が含まれています。

#### コアフォントのエンコーディング(encodings要素)

コアフォントのエンコーディング情報のファイルを設定します。 通常は編集する必要はありません。

letterフォント(SynbolとZapfDingbats以外のフォント)を使用する場合、使用できる文字セットにはStandardEncoding、
MacRomanEncoding、WinAnsiEncodingの3種類があります。 Copper
PDFの出荷時にはWinAnsiEncodingが設定されています。 [core-fonts要素のencoding属性](#admin-config-fonts-core-fonts-encoding)
の指定を変更することで切り替えることができます。

##### encodingsに含まれる要素

**encoding要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| src |  | グリフコードとグリフ名の対応を記述したファイルです。 |

#### <a id="admin-config-fonts-cmap">cmapファイル(cmaps要素)</a>

CID-Keyedフォントのエンコーディング情報のファイルを設定します。 通常は編集する必要はありません。

##### cmapsに含まれる要素

**cmap要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| src |  | Adobe Japan 1-4コードと文字コードの対応を記述したファイルです。 |
| java-encoding |  | 文字コードのJavaエンコーディング名です。 |

#### コアフォント(core-fonts要素)

コアフォントの設定です。 通常は編集する必要はありませんが、使用するエンコーディングを変更したり、
ドキュメントから参照する際の別名を追加することができます。

**core-fonts要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| unicode-src |  | ユニコードと文字名の対応を記述したファイルです。 |
| <a id="admin-config-fonts-core-fonts-encoding"></a>encoding |  | エンコーディング名です。encodingsで設定されたものの中から選択できます。 |

##### core-fontsに含まれる要素

core-fonts要素にはletter-font, symbol-fontのいずれかを含むことができます。

###### letter-font

letter-font要素は通常の欧文フォントの設定です。

**letter-font要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| src |  | AFMファイルです。 |
| encoding |  | core-fontsのencoding属性を上書きします。 |

###### symbol-font

symbol-font要素は記号フォント(SymbolまたはZapfDingbats)の設定です。

**symbol-font要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| src |  | AFMファイルです。 |
| encoding-src |  | ユニコードとグリフコードの対応を記述したファイルです。 |

###### letter-fontおよびsymbol-fontに含まれる要素

<p id="admin-config-fonts-alias">letter-fontおよびsymbol-font内にalias要素を追加することで、 ドキュメント中の<span class="cssprop">font-family</span>で参照することができる別名が追加されます。</p>

**alias要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| name |  | フォントの別名です。 |

<p id="admin-config-fonts-unicode-range">letter-fontおよびsymbol-font内のinclude, exclude要素により、 フォントが使用される文字範囲を指定できます<span class="since">2.0.3</span>。 includeにより、フォントが使用される文字範囲を明示することができ、 excludeにより除外する文字範囲を明示することができます。 include, excludeの記述がない場合は、利用可能な全ての文字でフォントが利用されます。</p>

**include要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| unicode-range |  | カンマで区切ったユニコード範囲(詳細は後述)。 |

**exclude要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| unicode-range |  | カンマで区切ったユニコード範囲(詳細は後述)。 |

unicode-rangeはU+に続く16進数によるユニコードとハイフンを用います。
例えば、日本語のかなを含める文字範囲の指定は次の通りです。

```xml
<include unicode-range="U+3030-30FF" />
```

また下位の桁をワイルドカードに置き換えることもできます。 以下の記述は U+1F00-1FFFと書くのと等価です。

```xml
<include unicode-range="U+1F??" />
```

複数の文字範囲はカンマで区切ってください。

```xml
<include unicode-range="U+3030-30FF,U+1F??" />
```

#### CIDフォント(cid-fonts要素)

##### cid-fontsに含まれる要素

cid-fonts要素にはcid-keyed-font, font-file, font-dir, system-font,
all-system-fontsのいずれかを含むことができます。

###### cid-keyed-font

cid-keyed-font要素はフォントファイルを使用する代わりに、
フォントの幅情報を記述したファイルを利用してCID-Keyedフォントを定義するものです。

<table id="cid-keyed-font">
<caption>cid-keyed-font要素</caption>
<thead>
<tr>
<th>属性</th>
<th>必須</th>
<th>説明</th>
</tr>
</thead>
<tbody>
<tr>
<td>name</td>
<td class="check" />
<td>フォント名です。</td>
</tr>
<tr>
<td>italic</td>
<td></td>
<td>フォントを斜体にする場合はtrue、そうでない場合はfalseを設定してください。</td>
</tr>
<tr>
<td>weight</td>
<td></td>
<td>フォントの太さです。100から900まで100刻みの値で設定してください。400が普通の太さです。</td>
</tr>
<tr>
<td>panose</td>
<td></td>
<td>PDFのFontDescripterのPanoseフィールドに対応する値です。
クラスID、サブクラスID、10桁のPANOSE-1コードの順でスペース区切りで記述した12の数字から構成されます。</td>
</tr>
<tr>
<td>cmap</td>
<td class="check" />
<td>横書きのCMap名です。</td>
</tr>
<tr>
<td>vcmap</td>
<td />
<td>縦書きのCMap名です。</td>
</tr>
<tr>
<td>warray</td>
<td class="check" />
<td>フォント幅情報ファイルです。</td>
</tr>
</tbody>
</table>

###### font-file

font-file要素はフォントファイルを直接指定します。

<table>
<caption>font-file要素</caption>
<thead>
<tr>
<th>属性</th>
<th>必須</th>
<th>説明</th>
</tr>
</thead>
<tbody>
<tr>
<td>name</td>
<td />
<td>フォント名フォントデータから取得されますが、ここで上書きすることもできます。</td>
</tr>
<tr>
<td>src</td>
<td class="check" />
<td>フォントファイルです。</td>
</tr>
<tr>
<td>index</td>
<td></td>
<td>複数のフォントを含むTTCファイルの中で、使用するフォントの番号です。
省略した場合は0です。TTCファイルでない場合は無視されます。</td>
</tr>
<tr>
<td>types</td>
<td class="check" />
<td>フォントのタイプをスペース区切りで記述します。値は次のいずれかです。
<dl>
<dt>embedded</dt>
<dd>埋め込みフォント</dd>
<dt>cid-identity</dt>
<dd>CID Identityフォント</dd>
<dt>cid-keyed</dt>
<dd>CID-Keyedフォント</dd>
</dl>
</td>
</tr>
<tr>
<td>italic</td>
<td></td>
<td>フォントが斜体かどうかはフォントデータから取得されますが、ここで上書きすることもできます。
斜体にする場合はtrue、そうでない場合はfalseを設定してください。</td>
</tr>
<tr>
<td>weight</td>
<td></td>
<td>フォントの太さはフォントデータから取得されますが、ここで上書きすることもできます。
100から900まで100刻みの値で設定してください。</td>
</tr>
<tr>
<td>cmap</td>
<td class="check"><br />(type="cid-keyed"の場合)</td>
<td>横書きのCMap名です。</td>
</tr>
<tr>
<td>vcmap</td>
<td />
<td>縦書きのCMap名です。</td>
</tr>
</tbody>
</table>

###### font-dir

font-dir要素は指定したディレクトリに存在するフォントファイルを直接まとめて読み込みます。

<table>
<caption>font-dir要素</caption>
<thead>
<tr>
<th>属性</th>
<th>必須</th>
<th>説明</th>
</tr>
</thead>
<tbody>
<tr>
<td>dir</td>
<td class="check" />
<td>フォントファイルが格納されるディレクトリです。</td>
</tr>
<tr>
<td>types</td>
<td class="check" />
<td>フォントのタイプをスペース区切りで記述します。値は次のいずれかです。
<dl>
<dt>embedded</dt>
<dd>埋め込みフォント</dd>
<dt>cid-identity</dt>
<dd>CID Identityフォント</dd>
</dl>
</td>
</tr>
</tbody>
</table>

###### system-font

system-fontはJava実行環境を利用してフォントを読み込みます。
OSやウィンドウシステムにインストールされたフォントを名前で指定できますが、 縦書きなどフォントの一部の機能に制約があります。
埋め込みフォントとCID Identityでは縦書き用の字形を使えないため、縦書きの字は立たずに行と一緒に横に倒れます
(和文も、text-orientation: uprightの欧文も)。縦書きに使うフォントはfont-file要素かfont-dir要素で読み込んでください。

<table>
<caption>system-font要素</caption>
<thead>
<tr>
<th>属性</th>
<th>必須</th>
<th>説明</th>
</tr>
</thead>
<tbody>
<tr>
<td>name</td>
<td />
<td>フォント名です。フォントデータから取得されますが、ここで上書きすることもできます。</td>
</tr>
<tr>
<td>src</td>
<td class="check"><br />srcまたはfileが必要</td>
<td>システムにインストールされたフォント名です。</td>
</tr>
<tr>
<td>file<span class="since">3.0.0</span></td>
<td class="check"><br />srcまたはfileが必要</td>
<td>フォントファイルです。 font-file要素による読み込みでは、Copper
PDF独自のプログラムで読み込みますが、
こちらではjava.awt.Fontを使用します。通常はfont-file要素を使ってください。</td>
</tr>
<tr>
<td>types</td>
<td class="check" />
<td>フォントのタイプをスペース区切りで記述します。値は次のいずれかです。
<dl>
<dt>embedded</dt>
<dd>埋め込みフォント</dd>
<dt>cid-identity</dt>
<dd>CID Identityフォント</dd>
<dt>cid-keyed</dt>
<dd>CID-Keyedフォント</dd>
</dl>
</td>
</tr>
<tr>
<td>italic</td>
<td></td>
<td>フォントが斜体かどうかはフォントデータから取得されますが、ここで上書きすることもできます。
斜体にする場合はtrue、そうでない場合はfalseを設定してください。</td>
</tr>
<tr>
<td>weight</td>
<td></td>
<td>フォントの太さはフォントデータから取得されますが、ここで上書きすることもできます。
100から900まで100刻みの値で設定してください。400が普通の太さです。</td>
</tr>
<tr>
<td>cmap</td>
<td class="check">(type="cid-keyed"の場合)</td>
<td>横書きのCMap名です。</td>
</tr>
<tr>
<td>vcmap</td>
<td />
<td>縦書きのCMap名です。</td>
</tr>
</tbody>
</table>

###### all-system-fonts

all-system-fontsはJava実行環境が利用可能なフォントを全て読み込みます。
system-fontと同じく、埋め込みフォントとCID Identityでは縦書きの字が横に倒れます。
縦書きに使うフォントはfont-dir要素かfont-file要素で読み込んでください。

<table>
<caption>all-system-fonts要素</caption>
<thead>
<tr>
<th>属性</th>
<th>必須</th>
<th>説明</th>
</tr>
</thead>
<tbody>
<tr>
<td>dir<span class="since">3.0.0</span></td>
<td />
<td>フォントファイルが格納されるディレクトリです。 font-dir要素による読み込みでは、Copper
PDF独自のプログラムで読み込みますが、
こちらではjava.awt.Fontを使用します。通常はfont-dir要素を使ってください。</td>
</tr>
<tr>
<td>types</td>
<td class="check" />
<td>フォントのタイプをスペース区切りで記述します。値は次のいずれかです。
<dl>
<dt>embedded</dt>
<dd>埋め込みフォント</dd>
<dt>cid-identity</dt>
<dd>CID Identityフォント</dd>
</dl>
</td>
</tr>
</tbody>
</table>

###### cid-keyed-font, font-file, system-fontに含まれる要素

フォントの名前はフォントデータから取得されますが、 cid-keyed-font, font-file,
system-fontにalias要素を追加することにより、さらにフォントの別名を追加できます。 記述方法は[core-font, symbol-fontのalias](#admin-config-fonts-alias)
と同じです。

cid-keyed-font, font-file, system-fontにinclude, exclude要素を追加することにより、
有効な文字範囲を指定することができます。 記述方法は[core-font, symbol-fontのinclude, exclude](#admin-config-fonts-unicode-range)
と同じです。

#### 一般フォントファミリ(generic-fonts要素)

<span class="cssprop">font-family</span>
で指定できるserif, sans-serif, monospace, fantasy, cursiveという
5種類の一般フォント・ファミリに対応するフォントを指定するものです。

##### generic-fontsに含まれる要素

generic-fontsにはCSSの一般フォントファミリ名に対応する5つの名前の要素、 serif, sans-serif,
monospace, fantasy, cursiveが含まれます。
要素名はCSSの一般フォントファミリ名そのものなので、fangsongのように
CSSが定めるその他の一般ファミリ名も同じ書き方で対応付けられます。
出荷時の設定では、中国語向けにfangsongも定義しています。

<span class="cssprop">font-family</span>等で指定するフォント名には、
実際のフォント名以外に5種類の一般フォントファミリ名を指定することができます。
generic-fontsは、この一般フォントファミリ名と実際のフォントとの対応付けをするものです。

**serif要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| font-family |  | カンマで区切ったフォント名を優先順位の高いものから順に記述します。 |
| lang |  | この連鎖を使う内容の言語をBCP-47のタグで指定します。空白で区切って複数書けます。省略すると言語を問わない既定の連鎖になります。 |

**sans-serif要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| font-family |  | カンマで区切ったフォント名を優先順位の高いものから順に記述します。 |
| lang |  | この連鎖を使う内容の言語をBCP-47のタグで指定します。空白で区切って複数書けます。省略すると言語を問わない既定の連鎖になります。 |

**monospace要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| font-family |  | カンマで区切ったフォント名を優先順位の高いものから順に記述します。 |
| lang |  | この連鎖を使う内容の言語をBCP-47のタグで指定します。空白で区切って複数書けます。省略すると言語を問わない既定の連鎖になります。 |

**fantasy要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| font-family |  | カンマで区切ったフォント名を優先順位の高いものから順に記述します。 |
| lang |  | この連鎖を使う内容の言語をBCP-47のタグで指定します。空白で区切って複数書けます。省略すると言語を問わない既定の連鎖になります。 |

**cursive要素**

| 属性 | 必須 | 説明 |
| --- | --- | --- |
| font-family |  | カンマで区切ったフォント名を優先順位の高いものから順に記述します。 |
| lang |  | この連鎖を使う内容の言語をBCP-47のタグで指定します。空白で区切って複数書けます。省略すると言語を問わない既定の連鎖になります。 |

#### 言語ごとに違うフォントを使う

同じ一般フォントファミリでも、内容の言語によって使うフォントを変えられます。
各要素に`lang`属性を付けると、その言語の内容にだけその連鎖が使われます。
`lang`属性の無い要素は、どの言語にも当てはまらなかったときの既定になります。

日本語・中国語・韓国語は同じ漢字でも字形が違うため、言語を見ずに1本の連鎖で
選ぶと、例えば中国語の本文に日本語の字形が出ます。

```xml
<generic-fonts>
	<sans-serif font-family="IPAゴシック,Gothic,UniGB-Heiti,UniKS-Gothic" />
	<sans-serif lang="zh-Hans zh zh-CN zh-SG" font-family="Noto Sans SC,UniGB-Heiti,IPAゴシック" />
	<sans-serif lang="zh-Hant zh-TW zh-MO" font-family="Noto Sans TC,IPAゴシック" />
	<sans-serif lang="ko" font-family="Noto Sans KR,UniKS-Gothic,IPAゴシック" />
</generic-fonts>
```

内容の言語は`lang`属性(またはXMLの`xml:lang`属性)で決まり、指定の無い要素は
親から受け継ぎます。連鎖は、タグの完全一致、言語とスクリプトの一致、
言語だけの一致の順に選ばれ、どれにも当てはまらなければ`lang`属性の無い
既定の連鎖が使われます。`zh-TW`のように地域だけを書いたタグは
`zh-Hant`とは機械的に結び付かないため、上の例のように同じ要素の`lang`属性へ
並べて書きます。

### フォント設定ファイルの設定例

#### デフォルトのフォントの変更

<span class="ioprop">output.pdf.fonts.policy</span>にembeddedを指定するか、
CSSで<span class="cssdecl">-cssj-font-policy: embedded;</span>を指定すると
埋め込みフォントが使用されますが、出荷時のフォント設定ファイルでは、大抵の場合は全ての文字が <img src="images/kumimoji.png" style="width: 10.5pt; height: 10.5pt; vertical-align: -1pt;" />
のような組み文字になってしまいます。
これは、デフォルトのフォントが一般フォントファミリのserifになっており、serifはCID-Keyedフォントにした対応付けてないためです。

<span class="ioprop">output.default-font-family</span>でデフォルトのフォントを変更するか、
あるいはフォント設定ファイルを修正して、一般フォントファミリを埋め込み可能なフォントに対応させます。

例えばtruetypeディレクトリにIPAexフォント <a target="_blank" href="https://ipafont.ipa.go.jp/">(https://ipafont.ipa.go.jp/)</a>
を配置した場合は、 generic-fontsの部分を以下のように設定することで、
フォントの埋め込みに、デフォルトでIPAフォントが使われるようになります。

```xml
<generic-fonts>
	<serif font-family="Mincho,IPAex明朝,UniKS-Myungjo,UniCNS-Ming,UniGB-Song" />
	<sans-serif font-family="Gothic,IPAexゴシック,UniKS-Gothic,UniCNS-Ming,UniGB-Heiti" />
	<monospace font-family="Gothic,IPAexゴシック,UniKS-Gothic,UniCNS-Ming,UniGB-Heiti" />
	<fantasy font-family="Comic-Sans-MS,Gothic,IPAexゴシック,UniKS-Gothic,UniCNS-Ming,UniGB-Heiti" />
	<cursive font-family="Comic-Sans-MS,Mincho,IPAex明朝,UniKS-Myungjo,UniCNS-Ming,UniGB-Song" />
</generic-fonts>
```

<!--
<h4>基本１４フォントを埋め込みにする</h4>
<p>
出荷時のフォント設定ファイルに記述されている１４種類のフォントは、「基本１４フォント」と呼ばれるものです。
基本１４フォントはPDFの表示環境で常に表示することができますが、
字体は環境によって微妙に違うことがあるため、完全に同じ表示を保証するためには埋め込みを行ってください。
</p>
<div class="example" title="例のタイトル"><pre><![CDATA[
<cid-fonts>
<font-file src="truetype/core/CourierStd-Bold.otf" types="embedded">
<alias name="Courier" />
<alias name="Courier-Bold" />
<alias name="Courier-New" />
</font-file>
<font-file src="truetype/core/CourierStd-BoldOblique.otf" types="embedded">
<alias name="Courier" />
<alias name="Courier-BoldOblique" />
<alias name="Courier-New" />
</font-file>
<font-file src="truetype/core/CourierStd-Oblique.otf" types="embedded">
<alias name="Courier" />
<alias name="Courier-Oblique" />
<alias name="Courier-New" />
</font-file>
<font-file src="truetype/core/CourierStd.otf" types="embedded">
<alias name="Courier" />
<alias name="Courier-New" />
</font-file>
<font-file src="truetype/core/MyriadPro-Bold.otf" types="embedded">
<alias name="Helvetica" />
<alias name="Helvetica-Bold" />
<alias name="Arial" />
</font-file>
<font-file src="truetype/core/MyriadPro-BoldIt.otf" types="embedded">
<alias name="Helvetica" />
<alias name="Helvetica-BoldOblique" />
<alias name="Arial" />
</font-file>
<font-file src="truetype/core/MyriadPro-It.otf" types="embedded">
<alias name="Helvetica" />
<alias name="Helvetica-Oblique" />
<alias name="Arial" />
</font-file>
<font-file src="truetype/core/MyriadPro-Regular.otf" types="embedded">
<alias name="Helvetica" />
<alias name="Arial" />
</font-file>
<font-file src="truetype/core/MinionPro-Bold.otf" types="embedded">
<alias name="Times" />
<alias name="Times-Bold" />
<alias name="Times-New-Roman" />
</font-file>
<font-file src="truetype/core/MinionPro-BoldIt.otf" types="embedded">
<alias name="Times" />
<alias name="Times-BoldItalic" />
<alias name="Times-New-Roman" />
</font-file>
<font-file src="truetype/core/MinionPro-It.otf" types="embedded">
<alias name="Times" />
<alias name="Times-Italic" />
<alias name="Times-New-Roman" />
</font-file>
<font-file src="truetype/core/MinionPro-Regular.otf" types="embedded">
<alias name="Times" />
<alias name="Times-Roman" />
<alias name="Times-New-Roman" />
</font-file>
<font-file src="truetype/core/SY______.PFB" types="embedded"/>
<font-file src="truetype/core/AdobePiStd.otf" types="embedded">
<alias name="ITC ZapfDingbats" />
<alias name="ZapfDingbats" />
</font-file>
</cid-fonts>
]]></pre></div>
-->
