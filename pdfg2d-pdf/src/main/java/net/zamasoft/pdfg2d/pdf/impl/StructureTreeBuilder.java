package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.pdfg2d.pdf.ObjectRef;

/**
 * Collects the logical structure of a tagged PDF while content is being
 * written, and emits the {@code /StructTreeRoot}, structure elements and
 * parent tree when the document closes.
 * <p>
 * Content producers call {@link #mark} for every marked-content sequence;
 * the returned MCID is written into the content stream's {@code BDC}
 * properties. When no structure element is open (via {@link #begin}/
 * {@link #end}), each mark becomes its own leaf element under the
 * {@code Document} root — a flat but conforming structure. Callers that know
 * the logical structure (e.g. an HTML layout engine) group content into
 * meaningful elements with {@code begin}/{@code end}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
final class StructureTreeBuilder {

	/** A structure element node. */
	private static final class Elem {
		final Elem parent;
		final String role;
		String alt;
		/** Children: either {@link Elem} or {@link Mcid}. */
		final List<Object> kids = new ArrayList<>();
		/** Page of the first contained MCID; used for the /Pg entry. */
		PDFPageOutputImpl page;
		ObjectRef ref;

		Elem(final Elem parent, final String role, final String alt) {
			this.parent = parent;
			this.role = role;
			this.alt = alt;
		}
	}

	/** A marked-content reference within a page. */
	private record Mcid(PDFPageOutputImpl page, int mcid) {
	}

	/** Result of {@link #mark}: the MCID and the tag to use for BDC. */
	record Mark(int mcid, String tag) {
	}

	private final Elem document = new Elem(null, "Document", null);

	private Elem current = this.document;

	/** Page -> parent tree key (the page's /StructParents value). */
	private final Map<PDFPageOutputImpl, Integer> pageKeys = new LinkedHashMap<>();

	/** Parent tree key -> MCID-indexed list of owning elements. */
	private final Map<Integer, List<Elem>> parentTree = new LinkedHashMap<>();

	/**
	 * Opens a structure element with the given role; subsequent marks attach
	 * to it until {@link #end()} is called. May be nested.
	 *
	 * @param role the structure type (e.g. {@code "P"}, {@code "H1"},
	 *             {@code "Table"})
	 */
	void begin(final String role) {
		final var child = new Elem(this.current, role, null);
		this.current.kids.add(child);
		this.current = child;
	}

	/** Closes the innermost open structure element. */
	void end() {
		if (this.current != this.document) {
			this.current = this.current.parent;
		}
	}

	/**
	 * Registers a marked-content sequence on the given page.
	 *
	 * @param page        the page carrying the content
	 * @param defaultRole role used when no element is open
	 * @param alt         alternate description (for figures), or {@code null}
	 * @return the MCID and BDC tag to write
	 */
	Mark mark(final PDFPageOutputImpl page, final String defaultRole, final String alt) {
		final Elem target;
		if (this.current == this.document) {
			target = new Elem(this.document, defaultRole, alt);
			this.document.kids.add(target);
		} else {
			target = this.current;
			if (alt != null && target.alt == null) {
				target.alt = alt;
			}
		}
		if (target.page == null) {
			target.page = page;
		}

		final var key = this.pageKeys.computeIfAbsent(page, p -> {
			final var k = this.pageKeys.size();
			p.setStructParents(k);
			return k;
		});
		final var elems = this.parentTree.computeIfAbsent(key, k -> new ArrayList<>());
		final var mcid = elems.size();
		elems.add(target);
		target.kids.add(new Mcid(page, mcid));
		return new Mark(mcid, target.role);
	}

	/**
	 * Writes the structure element objects, the parent tree and the
	 * {@code /StructTreeRoot}, and returns the root's reference for the
	 * catalog.
	 *
	 * @param out  the objects flow to write to
	 * @param xref the cross-reference table for allocating references
	 * @return the {@code StructTreeRoot} reference
	 * @throws IOException if an I/O error occurs
	 */
	ObjectRef writeTo(final PDFObjectSink sink, final XRefImpl xref) throws IOException {
		final var rootRef = xref.nextObjectRef();
		this.allocate(this.document, xref);

		this.writeElem(sink, this.document, rootRef);

		// Parent tree: a number tree mapping each page's /StructParents key
		// to the MCID-indexed array of owning structure elements.
		final var parentTreeRef = xref.nextObjectRef();
		var out = sink.startObject(parentTreeRef);
		out.startHash();
		out.writeName("Nums");
		out.startArray();
		for (final var e : this.parentTree.entrySet()) {
			out.writeInt(e.getKey());
			out.startArray();
			for (final var elem : e.getValue()) {
				out.writeObjectRef(elem.ref);
			}
			out.endArray();
		}
		out.endArray();
		out.endHash();
		sink.endObject();

		out = sink.startObject(rootRef);
		out.startHash();
		out.writeName("Type");
		out.writeName("StructTreeRoot");
		out.writeName("K");
		out.writeObjectRef(this.document.ref);
		out.writeName("ParentTree");
		out.writeObjectRef(parentTreeRef);
		out.writeName("ParentTreeNextKey");
		out.writeInt(this.pageKeys.size());
		out.endHash();
		sink.endObject();
		return rootRef;
	}

	private void allocate(final Elem elem, final XRefImpl xref) {
		elem.ref = xref.nextObjectRef();
		for (final var kid : elem.kids) {
			if (kid instanceof Elem child) {
				this.allocate(child, xref);
			}
		}
	}

	private void writeElem(final PDFObjectSink sink, final Elem elem, final ObjectRef parentRef)
			throws IOException {
		final var out = sink.startObject(elem.ref);
		out.startHash();
		out.writeName("Type");
		out.writeName("StructElem");
		out.writeName("S");
		out.writeName(elem.role);
		out.writeName("P");
		out.writeObjectRef(parentRef);
		out.lineBreak();
		if (elem.page != null) {
			out.writeName("Pg");
			out.writeObjectRef(elem.page.getPageRef());
			out.lineBreak();
		}
		if (elem.alt != null) {
			out.writeName("Alt");
			out.writeText(elem.alt);
			out.lineBreak();
		}
		out.writeName("K");
		out.startArray();
		for (final var kid : elem.kids) {
			if (kid instanceof Elem child) {
				out.writeObjectRef(child.ref);
			} else if (kid instanceof Mcid m) {
				if (m.page() == elem.page) {
					out.writeInt(m.mcid());
				} else {
					// MCID on a different page than /Pg: use a full
					// marked-content reference dictionary.
					out.startHash();
					out.writeName("Type");
					out.writeName("MCR");
					out.writeName("Pg");
					out.writeObjectRef(m.page().getPageRef());
					out.writeName("MCID");
					out.writeInt(m.mcid());
					out.endHash();
				}
			}
		}
		out.endArray();
		out.endHash();
		sink.endObject();

		for (final var kid : elem.kids) {
			if (kid instanceof Elem child) {
				this.writeElem(sink, child, elem.ref);
			}
		}
	}
}
