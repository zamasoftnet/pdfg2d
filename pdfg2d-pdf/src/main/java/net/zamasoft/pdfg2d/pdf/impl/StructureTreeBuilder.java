package net.zamasoft.pdfg2d.pdf.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.ArrayDeque;
import java.util.Deque;

import net.zamasoft.pdfg2d.pdf.ObjectRef;
import net.zamasoft.pdfg2d.pdf.StructureOrder;
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
		/** Children share slots so elements, MCRs and OBJRs sort together. */
		final List<Kid> kids = new ArrayList<>();
		/** This element's slot in its parent, or {@code null} for Document. */
		Kid parentSlot;
		/** Page of the first contained MCID; used for the /Pg entry. */
		PDFPageOutputImpl page;
		ObjectRef ref;
		/**
		 * The implicit {@code P} wrapper receiving direct content of a
		 * grouping element (PDF/UA-2: ISO 32005 forbids content items in
		 * {@code Div}/{@code Sect} etc.). Reused while it stays the last kid.
		 */
		Elem contentWrapper;

		Elem(final Elem parent, final String role, final String alt) {
			this.parent = parent;
			this.role = role;
			this.alt = alt;
		}
	}

	/** One child entry, with an optional logical key and stable paint sequence. */
	private static final class Kid {
		final Object value;
		StructureOrder order;
		long paintSequence;

		Kid(final Object value, final StructureOrder order, final long paintSequence) {
			this.value = value;
			this.order = order;
			this.paintSequence = paintSequence;
		}
	}

	/** A marked-content reference within a page. */
	private record Mcid(PDFPageOutputImpl page, int mcid) {
	}

	/** An object reference (OBJR) to an annotation owned by an element. */
	private record ObjRef(PDFPageOutputImpl page, ObjectRef annot) {
	}

	/** Routing state restored after a paint-order content association. */
	private record ContentState(Elem elem, StructureOrder order) {
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

	/** Logical key inherited by marks and annotations in the current route. */
	private StructureOrder currentOrder;

	/** Stable sequence for child slots; never serialized into the PDF. */
	private long nextPaintSequence;

	/**
	 * Whether to attribute structure elements to the PDF 2.0 standard
	 * structure namespace (required by PDF/UA-2). When set, the
	 * {@code /StructTreeRoot} carries a {@code /Namespaces} array and every
	 * element a {@code /NS} reference.
	 */
	private final boolean pdf2Namespace;

	/** The PDF 2.0 standard structure namespace URI (ISO 32000-2 §14.8.6). */
	private static final String PDF2_NAMESPACE = "http://iso.org/pdf2/ssn";

	/**
	 * Structure types (among those this writer emits) that exist in the
	 * PDF 2.0 standard structure namespace. Legacy-only types (e.g.
	 * {@code Sect}, {@code BlockQuote}) get no {@code /NS} and thus stay in
	 * the default PDF 1.7 namespace, which ISO 14289-2 §8.2.4 also permits.
	 */
	private static final java.util.Set<String> PDF2_ROLES = java.util.Set.of("Document", "DocumentFragment", "Part",
			"Div", "Aside", "NonStruct", "P", "Title", "Lbl", "Em", "Strong", "Span", "Link", "Annot", "Form",
			"Ruby", "RB", "RT", "RP", "Warichu", "WT", "WP", "L", "LI", "LBody", "Table", "TR", "TH", "TD", "THead",
			"TBody", "TFoot", "Caption", "Figure", "Formula", "Artifact", "Sub", "FENote");

	/** Is the role a PDF 2.0 namespace type ({@code Hn} has unbounded n)? */
	private static boolean isPdf2Role(final String role) {
		if (PDF2_ROLES.contains(role)) {
			return true;
		}
		if (role.length() >= 2 && role.charAt(0) == 'H') {
			for (int i = 1; i < role.length(); ++i) {
				if (role.charAt(i) < '0' || role.charAt(i) > '9') {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Grouping types that must not own content items directly
	 * (ISO 32005 §6.2) — their marks go into an implicit {@code P} wrapper
	 * when writing PDF/UA-2.
	 */
	private static final java.util.Set<String> GROUPING_ROLES = java.util.Set.of("Div", "Sect", "Part", "Aside",
			"NonStruct", "Art", "DocumentFragment");

	StructureTreeBuilder(final boolean pdf2Namespace) {
		this.pdf2Namespace = pdf2Namespace;
	}

	/** Adds a child without changing the legacy insertion order. */
	private Kid addKid(final Elem parent, final Object value, final StructureOrder order) {
		final var kid = new Kid(value, order, this.nextPaintSequence++);
		parent.kids.add(kid);
		if (value instanceof Elem elem) {
			elem.parentSlot = kid;
		}
		return kid;
	}

	/** Keeps the earliest logical position seen for a multi-fragment element. */
	private void applyOrder(final Kid kid, final StructureOrder order) {
		if (kid != null && order != null && (kid.order == null || order.compareTo(kid.order) < 0)) {
			kid.order = order;
			kid.paintSequence = this.nextPaintSequence++;
		}
	}

	/**
	 * Returns the /K order. Unhinted entries retain their exact legacy slots;
	 * hinted entries are stably sorted among the common slots they occupy.
	 */
	private static List<Kid> orderedKids(final Elem elem) {
		final var ordered = new ArrayList<Kid>();
		for (final var kid : elem.kids) {
			if (kid.order != null) {
				ordered.add(kid);
			}
		}
		if (ordered.size() < 2) {
			return elem.kids;
		}
		ordered.sort((a, b) -> {
			final int comparison = a.order.compareTo(b.order);
			return comparison != 0 ? comparison : Long.compare(a.paintSequence, b.paintSequence);
		});
		final var result = new ArrayList<>(elem.kids);
		int next = 0;
		for (int i = 0; i < result.size(); ++i) {
			if (result.get(i).order != null) {
				result.set(i, ordered.get(next++));
			}
		}
		return result;
	}

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
		this.addKid(this.current, child, this.currentOrder);
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
	 * element's position in its parent's {@code /K} array defaults to
	 * declaration order (= logical document order), independent of when its
	 * content is painted. An ordering hint supplied while attaching content may
	 * refine that position. Attach content later with
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
		this.addKid(p, child, null);

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
	private final Deque<ContentState> contentRestore = new ArrayDeque<>();

	/**
	 * Routes subsequent marks (and annotation associations) to a declared
	 * element until {@link #endContent()}. May be nested and may interleave
	 * with painting order freely; declaration order is retained unless an
	 * explicit logical ordering hint is supplied.
	 *
	 * <p>
	 * A {@code null} (or foreign) target still pushes a restore frame so that
	 * every {@code beginContent} balances its {@code endContent} — callers can
	 * bracket unconditionally without corrupting an enclosing frame.
	 * </p>
	 */
	void beginContent(final StructureRef target) {
		this.beginContent(target, null);
	}

	/** Routes content with an optional logical ordering hint. */
	void beginContent(final StructureRef target, final StructureOrder order) {
		this.contentRestore.push(new ContentState(this.current, this.currentOrder));
		if (target instanceof Elem elem && this.owns(elem)) {
			this.current = elem;
			this.currentOrder = order;
			this.applyOrder(elem.parentSlot, order);
		}
	}

	/** Restores the routing state saved by {@link #beginContent}. */
	void endContent() {
		if (!this.contentRestore.isEmpty()) {
			final var state = this.contentRestore.pop();
			this.current = state.elem();
			this.currentOrder = state.order();
		}
	}

	/** Does the parent chain of {@code e} reach this tree's root? */
	private boolean owns(Elem e) {
		for (; e != null; e = e.parent) {
			if (e == this.document) {
				return true;
			}
		}
		return false;
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
		this.addKid(target, new ObjRef(page, annot), this.currentOrder);
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
		return this.mark(page, defaultRole, alt, this.currentOrder);
	}

	/**
	 * Registers a mark with an optional logical ordering hint.
	 *
	 * @param page        the page carrying the content
	 * @param defaultRole role used when no element is open
	 * @param alt         alternate description, or {@code null}
	 * @param order       logical ordering hint, or {@code null} for paint order
	 * @return the MCID and BDC tag to write
	 */
	Mark mark(final PDFPageOutputImpl page, final String defaultRole, final String alt,
			final StructureOrder order) {
		Elem target;
		if (this.current == this.document) {
			target = new Elem(this.document, defaultRole, alt);
			this.addKid(this.document, target, order);
		} else {
			target = this.current;
			this.applyOrder(target.parentSlot, order);
			if (alt != null && target.alt == null) {
				target.alt = alt;
			}
		}
		if (this.pdf2Namespace && GROUPING_ROLES.contains(target.role)) {
			// PDF/UA-2: グループ化要素は内容を直接持てない(ISO 32005 §6.2)。
			// 連続する内容は同じ暗黙Pへ継ぎ足し、間に子要素が入ったら
			// 新しいPを開く(順序保持)
			if (target.contentWrapper == null || target.kids.isEmpty()
					|| target.kids.get(target.kids.size() - 1).value != target.contentWrapper) {
				final var wrapper = new Elem(target, "P", null);
				this.addKid(target, wrapper, order);
				target.contentWrapper = wrapper;
			}
			target = target.contentWrapper;
			this.applyOrder(target.parentSlot, order);
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
		this.addKid(target, new Mcid(page, mcid), order);
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

		// PDF/UA-2: the PDF 2.0 standard structure namespace object, shared
		// by every element via /NS and listed in the root's /Namespaces.
		if (this.pdf2Namespace) {
			this.namespaceRef = xref.nextObjectRef();
			final var nsOut = sink.startObject(this.namespaceRef);
			nsOut.startHash();
			nsOut.writeName("Type");
			nsOut.writeName("Namespace");
			nsOut.writeName("NS");
			nsOut.writeString(PDF2_NAMESPACE);
			nsOut.endHash();
			sink.endObject();
		}

		// Iterative pre-order walk in legacy insertion order. Logical ordering is
		// applied only to each serialized /K array, not object allocation/emission.
		record WriteStep(Elem elem, ObjectRef parentRef) {
		}
		final Deque<WriteStep> work = new ArrayDeque<>();
		work.push(new WriteStep(this.document, rootRef));
		while (!work.isEmpty()) {
			final var step = work.pop();
			this.writeElem(sink, step.elem(), step.parentRef());
			for (var i = step.elem().kids.size() - 1; i >= 0; --i) {
				if (step.elem().kids.get(i).value instanceof Elem child) {
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
		if (this.namespaceRef != null) {
			out.writeName("Namespaces");
			out.startArray();
			out.writeObjectRef(this.namespaceRef);
			out.endArray();
		}
		out.endHash();
		sink.endObject();
		return rootRef;
	}

	/** The shared PDF 2.0 namespace object, allocated in {@link #writeTo}. */
	private ObjectRef namespaceRef = null;

	private void allocate(final Elem root, final XRefImpl xref) {
		// Iterative pre-order walk; structure depth follows document nesting
		// and must not be bounded by the Java stack.
		final Deque<Elem> work = new ArrayDeque<>();
		work.push(root);
		while (!work.isEmpty()) {
			final var elem = work.pop();
			elem.ref = xref.nextObjectRef();
			for (var i = elem.kids.size() - 1; i >= 0; --i) {
				if (elem.kids.get(i).value instanceof Elem child) {
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
		if (this.namespaceRef != null && isPdf2Role(elem.role)) {
			// PDF/UA-2: attribute the element to the PDF 2.0 standard
			// structure namespace. Legacy-only types (Sect, BlockQuote, ...)
			// get no /NS and stay in the default PDF 1.7 namespace.
			out.writeName("NS");
			out.writeObjectRef(this.namespaceRef);
		}
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
		for (final var slot : orderedKids(elem)) {
			final var kid = slot.value;
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
