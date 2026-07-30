package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.ArrayDeque;
import java.util.Deque;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.StructureRef;

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

	/** A structure element node (also the public {@link StructureRef} handle). */
	private static final class Elem implements StructureRef {
		final Elem parent;
		final String role;
		String alt;
		/** Table-cell scope ({@code "Row"}/{@code "Column"}/{@code "Both"}), or null. */
		String scope;
		/** Children: {@link Elem}, {@link Mcid} or {@link ObjRef}. */
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

	/** An object reference (OBJR) to an annotation owned by an element. */
	private record ObjRef(PDFPageOutputImpl page, ObjectRef annot) {
	}

	/** Structure types that must nest by increasing heading level. */
	private static int headingLevel(final String role) {
		return (role.length() == 2 && role.charAt(0) == 'H' && role.charAt(1) >= '1' && role.charAt(1) <= '6')
				? role.charAt(1) - '0'
				: -1;
	}

	/** Result of {@link #mark}: the MCID and the tag to use for BDC. */
	record Mark(int mcid, String tag) {
	}

	private final Elem document = new Elem(null, "Document", null);

	private Elem current = this.document;

	/** Highest heading level seen so far (1..6), for skip detection. */
	private int lastHeadingLevel = 0;

	/** Set when a heading level was skipped (e.g. H1 then H3). */
	private boolean headingSkip = false;

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
		this.begin(role, null);
	}

	/**
	 * Opens a structure element with the given role and an optional table-cell
	 * scope.
	 *
	 * @param role  the structure type
	 * @param scope the table-header scope ({@code "Row"}, {@code "Column"},
	 *              {@code "Both"}) for a {@code TH}, or {@code null}
	 */
	void begin(final String role, final String scope) {
		final var child = new Elem(this.current, role, null);
		child.scope = scope;
		this.current.kids.add(child);
		this.current = child;

		final var level = headingLevel(role);
		if (level > 0) {
			// WCAG/PDF-UA: heading levels must not skip downward (H1 -> H3).
			if (this.lastHeadingLevel > 0 && level > this.lastHeadingLevel + 1) {
				this.headingSkip = true;
			}
			this.lastHeadingLevel = level;
		}
	}

	/** Closes the innermost open structure element. */
	void end() {
		if (this.current != this.document) {
			this.current = this.current.parent;
		}
	}

	/**
	 * Declares a structure element without opening it for content. The
	 * element's position in its parent's {@code /K} array is fixed by
	 * declaration order (= logical document order), independent of when its
	 * content is painted. Attach content later with
	 * {@link #beginContent(StructureRef)} (2026-07-30, B-2).
	 *
	 * @param parent the declared parent, or {@code null} for the root
	 * @param role   the structure type
	 * @param scope  the table-header scope, or {@code null}
	 * @return the handle to attach content and annotations to
	 */
	StructureRef declare(final StructureRef parent, final String role, final String scope) {
		final var p = parent instanceof Elem elem ? elem : this.document;
		final var child = new Elem(p, role, null);
		child.scope = scope;
		p.kids.add(child);

		final var level = headingLevel(role);
		if (level > 0) {
			// Declaration order is logical order, so skip detection is
			// meaningful here (same rule as begin()).
			if (this.lastHeadingLevel > 0 && level > this.lastHeadingLevel + 1) {
				this.headingSkip = true;
			}
			this.lastHeadingLevel = level;
		}
		return child;
	}

	/**
	 * Stack of {@link #current} values saved by {@link #beginContent} so the
	 * paint-order traversal can attach content to declared elements without
	 * disturbing the (legacy) implicit begin()/end() nesting.
	 */
	private final Deque<Elem> contentRestore = new ArrayDeque<>();

	/**
	 * Routes subsequent marks (and annotation associations) to a declared
	 * element until {@link #endContent()}. May be nested and may interleave
	 * with painting order freely; the structure order is fixed by
	 * {@link #declare}.
	 */
	void beginContent(final StructureRef target) {
		if (!(target instanceof Elem elem)) {
			return;
		}
		this.contentRestore.push(this.current);
		this.current = elem;
	}

	/** Restores the routing state saved by {@link #beginContent}. */
	void endContent() {
		if (!this.contentRestore.isEmpty()) {
			this.current = this.contentRestore.pop();
		}
	}

	/**
	 * Associates an annotation (typically a Link) with the currently open
	 * structure element via an object reference (OBJR), and returns the
	 * element's parent-tree key for the annotation's {@code /StructParent}.
	 *
	 * @param page  the page carrying the annotation
	 * @param annot the annotation object reference
	 * @return the {@code /StructParent} key, or -1 when no element is open
	 */
	int associateAnnotation(final PDFPageOutputImpl page, final ObjectRef annot) {
		if (this.current == this.document) {
			return -1;
		}
		final var target = this.current;
		target.kids.add(new ObjRef(page, annot));
		if (target.page == null) {
			target.page = page;
		}
		// The annotation gets its own parent-tree key resolving directly to
		// the owning element (a single ref, not an MCID-indexed array).
		final var annotKey = this.nextKey++;
		this.annotOwners.put(annotKey, target);
		return annotKey;
	}

	/**
	 * Returns whether any heading level was skipped downward (a PDF/UA
	 * violation). Meaningful only after all content has been marked.
	 *
	 * @return {@code true} if a heading level was skipped
	 */
	boolean hasHeadingSkip() {
		return this.headingSkip;
	}

	/** Shared ascending parent-tree key counter (pages and annotations). */
	private int nextKey = 0;

	/** Annotation /StructParent key -> owning element. */
	private final Map<Integer, Elem> annotOwners = new LinkedHashMap<>();

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
			final var k = this.nextKey++;
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

		// Iterative pre-order walk (same emission order as the former
		// recursion: parent object first, then children in /K order).
		record WriteStep(Elem elem, ObjectRef parentRef) {
		}
		final Deque<WriteStep> work = new ArrayDeque<>();
		work.push(new WriteStep(this.document, rootRef));
		while (!work.isEmpty()) {
			final var step = work.pop();
			this.writeElem(sink, step.elem(), step.parentRef());
			for (var i = step.elem().kids.size() - 1; i >= 0; --i) {
				if (step.elem().kids.get(i) instanceof Elem child) {
					work.push(new WriteStep(child, step.elem().ref));
				}
			}
		}

		// Parent tree: a number tree mapping each page's /StructParents key to
		// the MCID-indexed array of owning elements, and each annotation's
		// /StructParent key to its single owning element. Keys are emitted in
		// ascending order as the number-tree format requires.
		final var parentTreeRef = xref.nextObjectRef();
		var out = sink.startObject(parentTreeRef);
		out.startHash();
		out.writeName("Nums");
		out.startArray();
		final var keys = new java.util.TreeSet<Integer>();
		keys.addAll(this.parentTree.keySet());
		keys.addAll(this.annotOwners.keySet());
		for (final var key : keys) {
			out.writeInt(key);
			final var elems = this.parentTree.get(key);
			if (elems != null) {
				out.startArray();
				for (final var elem : elems) {
					out.writeObjectRef(elem.ref);
				}
				out.endArray();
			} else {
				out.writeObjectRef(this.annotOwners.get(key).ref);
			}
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
		out.writeInt(this.nextKey);
		out.endHash();
		sink.endObject();
		return rootRef;
	}

	private void allocate(final Elem root, final XRefImpl xref) {
		// Iterative pre-order walk; structure depth follows document nesting
		// and must not be bounded by the Java stack.
		final Deque<Elem> work = new ArrayDeque<>();
		work.push(root);
		while (!work.isEmpty()) {
			final var elem = work.pop();
			elem.ref = xref.nextObjectRef();
			for (var i = elem.kids.size() - 1; i >= 0; --i) {
				if (elem.kids.get(i) instanceof Elem child) {
					work.push(child);
				}
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
		if (elem.scope != null) {
			// Table-header scope attribute (PDF/UA Table requirement).
			out.writeName("A");
			out.startHash();
			out.writeName("O");
			out.writeName("Table");
			out.writeName("Scope");
			out.writeName(elem.scope);
			out.endHash();
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
			} else if (kid instanceof ObjRef o) {
				// Object reference to an associated annotation (e.g. a Link).
				out.startHash();
				out.writeName("Type");
				out.writeName("OBJR");
				out.writeName("Pg");
				out.writeObjectRef(o.page().getPageRef());
				out.writeName("Obj");
				out.writeObjectRef(o.annot());
				out.endHash();
			}
		}
		out.endArray();
		out.endHash();
		sink.endObject();
	}
}
