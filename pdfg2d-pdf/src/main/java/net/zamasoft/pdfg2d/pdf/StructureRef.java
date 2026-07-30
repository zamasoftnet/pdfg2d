package net.zamasoft.pdfg2d.pdf;

/**
 * A handle to a declared logical-structure element of a tagged PDF
 * (see {@link PDFPageOutput#declareStructElement}).
 * <p>
 * Declaring elements up front decouples the <em>logical</em> order of the
 * structure tree (the {@code /K} arrays, which follow declaration order)
 * from the <em>paint</em> order of the content streams: content painted in
 * any order can be attached to its declared element with
 * {@link PDFPageOutput#beginStructContent(StructureRef)}. This is what a
 * layout engine needs when z-ordering reorders painting but the document
 * structure must stay in document order (2026-07-30, B-2).
 * </p>
 *
 * @since 1.3
 */
public interface StructureRef {
}
