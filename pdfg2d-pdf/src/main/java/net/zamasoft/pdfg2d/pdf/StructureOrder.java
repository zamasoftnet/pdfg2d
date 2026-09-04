package net.zamasoft.pdfg2d.pdf;

/**
 * Generic logical-order hint for content associated with a PDF structure
 * element. The fields form a lexicographic key; a paint-sequence number kept
 * internally by the writer stabilizes otherwise equal keys.
 *
 * @param blockOrdinal stable ordinal of the containing logical block
 * @param logicalStart logical character offset within that block
 * @param tieBreaker   caller-defined ordering discriminator at the same offset
 * @since 1.3
 */
public record StructureOrder(long blockOrdinal, int logicalStart, int tieBreaker)
		implements Comparable<StructureOrder> {
	@Override
	public int compareTo(final StructureOrder other) {
		int comparison = Long.compare(this.blockOrdinal, other.blockOrdinal);
		if (comparison == 0) {
			comparison = Integer.compare(this.logicalStart, other.logicalStart);
		}
		if (comparison == 0) {
			comparison = Integer.compare(this.tieBreaker, other.tieBreaker);
		}
		return comparison;
	}
}
