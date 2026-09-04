package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType GSUB (Glyph Substitution) table.
 * <p>
 * Provides lookup tables for substituting one glyph (or sequence of glyphs)
 * with another, supporting features such as ligature formation, vertical
 * forms, and script-specific alternate glyphs.  The table is structured as a
 * script list, feature list, and lookup list as defined by the OpenType
 * specification.
 * </p>
 * <p>Currently implemented lookup types:
 * <ul>
 *   <li>Type 1 – Single substitution (one glyph → one glyph)</li>
 *   <li>Type 4 – Ligature substitution (multiple glyphs → one glyph)</li>
 * </ul>
 * </p>
 *
 * @param scriptList  the script list mapping scripts to language systems and features
 * @param featureList the feature list associating feature tags with lookup indices
 * @param lookupList  the lookup list containing the actual substitution data
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record GsubTable(ScriptList scriptList, FeatureList featureList, LookupList lookupList)
		implements Table, LookupSubtableFactory {

	protected GsubTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private GsubTable(GsubTable other) {
		this(other.scriptList, other.featureList, other.lookupList);
	}

	private static GsubTable readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());

			// GSUB Header
			raf.readInt(); // version
			final int scriptListOffset = raf.readUnsignedShort();
			final int featureListOffset = raf.readUnsignedShort();
			final int lookupListOffset = raf.readUnsignedShort();

			// We need a temporary factory to read the lookup list
			final LookupSubtableFactory factory = (type, subRaf, offset1) -> {
				return switch (type) {
					case 1 -> SingleSubst.read(subRaf, offset1);
					case 4 -> LigatureSubst.read(subRaf, offset1);
					default -> null;
				};
			};

			// Script List
			final ScriptList scriptList = new ScriptList(raf, de.offset() + scriptListOffset);

			// Feature List
			final FeatureList featureList = new FeatureList(raf, de.offset() + featureListOffset);

			// Lookup List
			final LookupList lookupList = new LookupList(raf, de.offset() + lookupListOffset, factory);

			return new GsubTable(scriptList, featureList, lookupList);
		}
	}

	/**
	 * 1 - Single - Replace one glyph with one glyph 2 - Multiple - Replace one
	 * glyph
	 * with more than one glyph 3 - Alternate - Replace one glyph with one of many
	 * glyphs 4 - Ligature - Replace multiple glyphs with one glyph 5 - Context -
	 * Replace one or more glyphs in context 6 - Chaining - Context Replace one or
	 * more glyphs in chained context
	 */
	@Override
	public LookupSubtable read(final int type, final RandomAccessFile raf, final int offset) throws IOException {
		return switch (type) {
			case 1 -> SingleSubst.read(raf, offset);
			case 4 -> LigatureSubst.read(raf, offset);
			default -> null;
		};
	}

	/** The OpenType feature tag for standard ligatures. */
	private static final int TAG_LIGA = 0x6c696761;

	/**
	 * Collects the {@link LigatureSubstFormat1} subtables of every
	 * {@code liga} feature, across scripts and languages.
	 *
	 * @return the ligature substitution subtables (possibly empty)
	 */
	public java.util.List<LigatureSubstFormat1> collectLigatures() {
		return this.collectLigatures(TAG_LIGA);
	}

	/**
	 * Collects the {@link LigatureSubstFormat1} subtables of every feature
	 * record with the given tag, across scripts and languages. Shared lookups
	 * are deduplicated, and subtables are returned in ascending lookup-list
	 * index order.
	 *
	 * @param tag the packed 4-byte feature tag
	 * @return the ligature substitution subtables (possibly empty)
	 */
	public java.util.List<LigatureSubstFormat1> collectLigatures(final int tag) {
		final var records = this.featureList.featureRecords();
		final var features = this.featureList.features();
		final var byLookup = new java.util.TreeMap<Integer, java.util.List<LigatureSubstFormat1>>();
		for (int i = 0; i < records.length; i++) {
			if (records[i].tag() != tag) {
				continue;
			}
			final var feature = features[i];
			for (int li = 0; li < feature.getLookupCount(); li++) {
				final int lookupIndex = feature.getLookupListIndex(li);
				if (byLookup.containsKey(lookupIndex)) {
					continue;
				}
				final var subs = new java.util.ArrayList<LigatureSubstFormat1>();
				final var lookup = this.lookupList.lookups()[lookupIndex];
				for (int si = 0; si < lookup.getSubtableCount(); si++) {
					if (lookup.getSubtable(si) instanceof LigatureSubstFormat1 ls) {
						subs.add(ls);
					}
				}
				byLookup.put(lookupIndex, subs);
			}
		}
		final var result = new java.util.ArrayList<LigatureSubstFormat1>();
		for (final var subs : byLookup.values()) {
			result.addAll(subs);
		}
		return result;
	}

	/**
	 * Collects the {@link SingleSubst} subtables of every feature record with
	 * the given tag, across scripts and languages (same collection policy as
	 * {@link #collectLigatures()}: no script/language filtering, shared lookups
	 * deduplicated). Subtables are returned in ascending lookup-list order so
	 * that composing them applies the font's intended precedence.
	 *
	 * @param tag the packed 4-byte feature tag (e.g. {@code 0x6a703738} for
	 *            {@code jp78})
	 * @return the single-substitution subtables (possibly empty)
	 */
	public java.util.List<SingleSubst> collectSingleSubstitutions(final int tag) {
		final var records = this.featureList.featureRecords();
		final var features = this.featureList.features();
		final var byLookup = new java.util.TreeMap<Integer, java.util.List<SingleSubst>>();
		for (int i = 0; i < records.length; i++) {
			if (records[i].tag() != tag) {
				continue;
			}
			final var feature = features[i];
			for (int li = 0; li < feature.getLookupCount(); li++) {
				final int lookupIndex = feature.getLookupListIndex(li);
				if (byLookup.containsKey(lookupIndex)) {
					continue;
				}
				final var subs = new java.util.ArrayList<SingleSubst>();
				final var lookup = this.lookupList.lookups()[lookupIndex];
				for (int si = 0; si < lookup.getSubtableCount(); si++) {
					if (lookup.getSubtable(si) instanceof SingleSubst ss) {
						subs.add(ss);
					}
				}
				byLookup.put(lookupIndex, subs);
			}
		}
		final var result = new java.util.ArrayList<SingleSubst>();
		for (final var subs : byLookup.values()) {
			result.addAll(subs);
		}
		return result;
	}

	@Override
	public int getType() {
		return GSUB;
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
		return "GSUB";
	}
}
