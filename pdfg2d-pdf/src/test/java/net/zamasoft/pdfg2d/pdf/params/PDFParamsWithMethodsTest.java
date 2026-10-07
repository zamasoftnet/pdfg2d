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
 * Verifies that {@code withXxx} preserves every component it does not change.
 *
 * <p>
 * {@link PDFParams} is a record whose components have been added over time.
 * {@code withXxx} constructs a new instance, so <b>if a newly added component is omitted
 * from the arguments, calling the method silently resets it to its default.</b>
 * In fact, {@code withTagged} dropped four components, {@code withDeflateLevel} dropped three,
 * and {@code withObjectStreams} dropped two, causing the rendering intent setting to be lost.
 * </p>
 *
 * <p>
 * Visual inspection cannot catch this reliably, so check every {@code withXxx} mechanically.
 * When a component is added, this test fails and alerts you.
 * </p>
 */
class PDFParamsWithMethodsTest {
	/** Creates PDFParams with every component set to a value different from its default. */
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
				// Do not handle incompatible combinations of settings here
				continue;
			}
			// Name of the changed component: withFooBar -> fooBar.
			// Acronyms can differ in case, as in withRGBProfile -> rgbProfile,
			// so compare case-insensitively
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

	/** Supplies one value different from the current value for each type. Skips types it cannot instantiate. */
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
		// Exclude objects that cannot be safely created here, such as font managers and encryption
		return NO_SAMPLE;
	}
}
