package net.zamasoft.pdfg2d.pdf.params;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.PDFMetaInfo;

/**
 * {@code withXxx}が、変えない項目をすべて引き継ぐことを確かめます。
 *
 * <p>
 * {@link PDFParams}はレコードで、項目は後から足されてきました。
 * {@code withXxx}は新しいインスタンスを組み直すので、<b>足した項目を
 * 引数へ書き足し忘れると、そのメソッドを呼んだ瞬間に黙って既定へ戻ります。</b>
 * 実際に{@code withTagged}が4項目、{@code withDeflateLevel}が3項目、
 * {@code withObjectStreams}が2項目を落としており、レンダリングインテントの
 * 指定が消えていました。
 * </p>
 *
 * <p>
 * 目で見て気づける類ではないので、全部の{@code withXxx}を機械的に当たります。
 * 項目を足したら、この試験が落ちて教えてくれます。
 * </p>
 */
class PDFParamsWithMethodsTest {
	/** どの項目も既定と違う値にしたPDFParamsを作ります。 */
	private static PDFParams distinctive() {
		return PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_1_5)
				.withCompression(PDFParams.Compression.ASCII)
				.withPrecision(4)
				.withBookmarks(true)
				.withDeflateLevel(3)
				.withObjectStreams(true);
	}

	@Test
	void everyWithMethodKeepsTheOtherComponents() throws Exception {
		final RecordComponent[] components = PDFParams.class.getRecordComponents();
		assertNotNull(components, "PDFParams must stay a record");

		final List<String> problems = new ArrayList<>();
		for (final Method method : PDFParams.class.getMethods()) {
			if (!method.getName().startsWith("with") || method.getParameterCount() != 1
					|| method.getReturnType() != PDFParams.class) {
				continue;
			}
			final PDFParams before = distinctive();
			final Object argument = sampleFor(method.getParameterTypes()[0]);
			if (argument == NO_SAMPLE) {
				continue;
			}
			final PDFParams after;
			try {
				after = (PDFParams) method.invoke(before, argument);
			} catch (final Exception e) {
				// 組み合わせが成り立たない指定は、ここでは扱わない
				continue;
			}
			// 変えた項目の名前。withFooBar -> fooBar。
			// withRGBProfile -> rgbProfile のように頭字語では綴りが揃わないので、
			// 大文字小文字を無視して突き合わせる
			final String changed = method.getName().substring(4);
			for (final RecordComponent component : components) {
				if (component.getName().equalsIgnoreCase(changed)) {
					continue;
				}
				final Object a = component.getAccessor().invoke(before);
				final Object b = component.getAccessor().invoke(after);
				if (!Objects.deepEquals(a, b)) {
					problems.add(method.getName() + " lost " + component.getName() + ": " + a + " -> " + b);
				}
			}
		}
		assertEquals(List.of(), problems, "every withXxx must carry the untouched components over");
	}

	private static final Object NO_SAMPLE = new Object();

	/** 型ごとに「いまと違う値」を1つ用意します。作れない型は飛ばします。 */
	private static Object sampleFor(final Class<?> type) {
		if (type == boolean.class) {
			return Boolean.TRUE;
		}
		if (type == int.class) {
			return Integer.valueOf(2);
		}
		if (type == String.class) {
			return "UTF-8";
		}
		if (type.isEnum()) {
			final Object[] values = type.getEnumConstants();
			return values.length > 1 ? values[1] : values[0];
		}
		if (type == PDFMetaInfo.class) {
			return new PDFMetaInfo();
		}
		if (type == ViewerPreferences.class) {
			return new ViewerPreferences();
		}
		if (type == byte[].class) {
			return new byte[] { 1, 2, 3, 4 };
		}
		// フォント管理や暗号化のように、ここで安全に作れないものは対象外
		return NO_SAMPLE;
	}
}
