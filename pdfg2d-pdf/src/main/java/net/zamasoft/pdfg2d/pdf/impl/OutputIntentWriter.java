package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.PDFFragmentOutput;
import net.zamasoft.pdfg2d.pdf.params.OutputIntent;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * {@code /OutputIntents}のOutputIntent辞書とICCプロファイルストリームを
 * 書き出します(2026-08-01、PDFWriterImplコンストラクタからの抽出——
 * ViewerPreferencesWriter/XMPMetadataWriterと同じ様式)。
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
final class OutputIntentWriter {

	private OutputIntentWriter() {
		// static use only
	}

	/**
	 * OutputIntentオブジェクトを書き出します。
	 *
	 * @param mainFlow 出力先
	 * @param xref     クロスリファレンス(ICCプロファイル用の間接参照を割り当てる)
	 * @param params   PDF生成パラメータ
	 * @param ref      OutputIntent辞書に割り当て済みの間接参照
	 * @throws IOException if an I/O error occurs
	 */
	static void write(final PDFFragmentOutputImpl mainFlow, final XRefImpl xref, final PDFParams params,
			final ObjectRef ref) throws IOException {
		final var pdfVersion = params.version();
		mainFlow.startObject(ref);
		mainFlow.startHash();
		mainFlow.writeName("Type");
		mainFlow.writeName("OutputIntent");
		mainFlow.lineBreak();

		mainFlow.writeName("S");
		mainFlow.writeName(pdfVersion.isPdfX() ? "GTS_PDFX" : "GTS_PDFA1");
		mainFlow.lineBreak();

		// Resolve the intent: explicit configuration wins; otherwise a
		// built-in profile is chosen so that the intent's color space
		// matches the device color space actually emitted (PDF/A-1
		// requires DeviceRGB/DeviceCMYK content to be backed by an
		// output intent of the same type).
		var intent = params.outputIntent();
		if (intent == null) {
			if (pdfVersion == PDFParams.Version.V_PDFX1A
					|| params.effectiveColorMode() == PDFParams.ColorMode.CMYK) {
				intent = new OutputIntent("Probe Profile", null, null, "Probe CMYK profile",
						PDFWriterImpl.loadResource("Probev1_ICCv2.icc"), 4);
			} else {
				intent = new OutputIntent("sRGB IEC61966-2.1", null, null, null,
						PDFWriterImpl.loadResource("sRGB_IEC61966-2-1_no_black_scaling.icc"), 3);
			}
		}

		mainFlow.writeName("OutputConditionIdentifier");
		mainFlow.writeString(intent.outputConditionIdentifier());
		mainFlow.lineBreak();

		if (intent.outputCondition() != null) {
			mainFlow.writeName("OutputCondition");
			mainFlow.writeText(intent.outputCondition());
			mainFlow.lineBreak();
		}

		if (intent.registryName() != null) {
			mainFlow.writeName("RegistryName");
			mainFlow.writeString(intent.registryName());
			mainFlow.lineBreak();
		}

		if (intent.info() != null) {
			mainFlow.writeName("Info");
			mainFlow.writeText(intent.info());
			mainFlow.lineBreak();
		}

		final var profileData = intent.iccProfile();
		if (profileData != null) {
			final var profRef = xref.nextObjectRef();
			mainFlow.writeName("DestOutputProfile");
			mainFlow.writeObjectRef(profRef);
			mainFlow.lineBreak();

			mainFlow.endHash();
			mainFlow.endObject();

			mainFlow.startObject(profRef);
			mainFlow.startHash();

			mainFlow.writeName("N");
			mainFlow.writeInt(intent.colorComponents());
			mainFlow.lineBreak();

			try (final var pout = mainFlow.startStreamFromHash(PDFFragmentOutput.Mode.BINARY)) {
				pout.write(profileData);
			}
		} else {
			mainFlow.endHash();
		}
		mainFlow.endObject();
	}
}
