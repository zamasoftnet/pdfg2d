package net.zamasoft.pdfg2d.font;

import java.util.List;

/**
 * クラスパス上のモジュールが追加フォントを供給するためのSPIです
 * (2026-08-01、90点計画増分7)。
 *
 * <p>
 * 旧実装は絵文字フォントを{@code Class.forName("...EmojiFontSource")}+
 * publicフィールド反射で取り込んでいた——モジュール境界(pdfg2d-pdfは
 * pdfg2d-svgに依存できない)を跨ぐための継ぎ目だが、クラス名文字列と
 * フィールド名に暗黙依存していた。{@link java.util.ServiceLoader}経由の
 * 型付き契約に置換する。実装は
 * {@code META-INF/services/net.zamasoft.pdfg2d.font.FontSourceProvider}で
 * 登録する。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public interface FontSourceProvider {
	/**
	 * このプロバイダが供給するフォントを返します。フォントデータベース
	 * 構築のたびに呼ばれるため、生成コストの高いソースはプロバイダ側で
	 * 共有インスタンスを返すこと。
	 *
	 * @return 供給するフォント(空可)
	 */
	List<? extends FontSource> fontSources();
}
