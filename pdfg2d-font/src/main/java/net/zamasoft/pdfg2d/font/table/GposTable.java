package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * GPOS (Glyph Positioning) table.
 * 
 * @param scriptList  the script list
 * @param featureList the feature list
 * @param lookupList  the lookup list
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public record GposTable(ScriptList scriptList, FeatureList featureList, LookupList lookupList)
		implements Table, LookupSubtableFactory {

	protected GposTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private GposTable(GposTable other) {
		this(other.scriptList, other.featureList, other.lookupList);
	}

	private static GposTable readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());

			// GPOS Header
			raf.readInt(); // version
			final int scriptListOffset = raf.readUnsignedShort();
			final int featureListOffset = raf.readUnsignedShort();
			final int lookupListOffset = raf.readUnsignedShort();

			// Pair adjustment (type 2) is parsed for kerning; other lookup
			// types are not needed for positioning here.
			final LookupSubtableFactory factory = (type, subRaf, offset1) -> switch (type) {
				case 2 -> PairPos.read(subRaf, offset1);
				default -> null;
			};

			// Script List
			final ScriptList scriptList = new ScriptList(raf, de.offset() + scriptListOffset);

			// Feature List
			final FeatureList featureList = new FeatureList(raf, de.offset() + featureListOffset);

			// Lookup List
			final LookupList lookupList = new LookupList(raf, de.offset() + lookupListOffset, factory);

			return new GposTable(scriptList, featureList, lookupList);
		}
	}

	@Override
	public LookupSubtable read(final int type, final RandomAccessFile raf, final int offset) throws IOException {
		return switch (type) {
			case 2 -> PairPos.read(raf, offset);
			default -> null;
		};
	}

	/** The OpenType feature tag for horizontal kerning. */
	private static final int TAG_KERN = 0x6b65726e;

	/**
	 * Collects the {@link PairPos} subtables of every {@code kern} feature,
	 * across scripts and languages.
	 *
	 * @return the pair-positioning subtables (possibly empty)
	 */
	public java.util.List<PairPos> collectKernPairPos() {
		final var result = new java.util.ArrayList<PairPos>();
		final var records = this.featureList.featureRecords();
		final var features = this.featureList.features();
		for (int i = 0; i < records.length; i++) {
			if (records[i].tag() != TAG_KERN) {
				continue;
			}
			final var feature = features[i];
			for (int li = 0; li < feature.getLookupCount(); li++) {
				final var lookup = this.lookupList.lookups()[feature.getLookupListIndex(li)];
				for (int si = 0; si < lookup.getSubtableCount(); si++) {
					if (lookup.getSubtable(si) instanceof PairPos pp) {
						result.add(pp);
					}
				}
			}
		}
		return result;
	}

	@Override
	public int getType() {
		return GPOS;
	}

	public ScriptList getScriptList() {
		return this.scriptList;
	}

	public FeatureList getFeatureList() {
		return this.featureList;
	}

	public LookupList getLookupList() {
		return this.lookupList;
	}

	@Override
	public String toString() {
		return "GPOS";
	}
}
