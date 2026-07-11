Below is a **developer-facing implementation guide** for **Linearized PDF (a.k.a. “Fast Web View”)**. It is derived from the **PDF 1.7 Reference, Appendix F (Linearized PDF)** and practical implementation notes from **qpdf**. Your uploaded file is a cover/package document that points to the PDF 1.7 Reference and related errata as the primary specification set.

---

## 1. What “Linearized PDF” means (engineering definition)

A Linearized PDF is a PDF whose **byte layout + hint data** is arranged so a client (e.g., a browser/PDF viewer) can:

1. decide quickly (by reading ≤ 1024 bytes) whether the file is linearized,
2. download only a small initial byte range to render the “first page”, and
3. later request additional byte ranges to render other pages efficiently using **hint tables**.

Linearization is intended for **generate-once, read-many** files; if you append incremental updates, the result is typically **no longer treated as linearized** by conforming readers.

---

## 2. Hard compliance requirements (MUSTs)

### 2.1 Linearization parameter dictionary MUST be the first indirect object

Immediately after the PDF header, the **first object** in the body must be an **indirect dictionary object** called the **linearization parameter dictionary**. All values inside it are **direct objects** (no indirect references). It is not referenced from anywhere else in the PDF.

### 2.2 That dictionary MUST be fully within the first 1024 bytes

The linearization parameter dictionary must be entirely contained within the first **1024 bytes** of the file.

### 2.3 /L MUST equal actual file length

The dictionary’s `/L` entry must equal the exact byte length of the file. If it does not, a reader must treat the file as **non-linearized** (ordinary PDF), ignoring linearization data (unless doing special “updated file” validation).

---

## 3. Linearization parameter dictionary (exact fields you must write)

This is the first object and contains these keys (all direct values):

* **`/Linearized` (number, required)**
  Version of linearized format. Current is **1.0**.

* **`/L` (integer, required)**
  Total file length in bytes (must match actual).

* **`/H` (array, required)**
  Either `[offset1 length1]` or `[offset1 length1 offset2 length2]`.

  * `offset1`: file offset (from beginning of file) of the **primary hint stream object** (offset of the *stream object*, not stream data).
  * `length1`: total length of that stream object **including overhead**; if `/Length` is an indirect reference, the *indirect length object must immediately follow the stream object* and `length1` includes it too.
  * If overflow hint stream exists: `offset2`/`length2` describe it similarly.

* **`/O` (integer, required)**
  Object number of the **first page’s page object**.

* **`/E` (integer, required)**
  File offset (from beginning of file) of the **end of the first-page section** (end of “part 6” in the canonical structure).

* **`/N` (integer, required)**
  Number of pages.

* **`/T` (integer, required)**

  * If using traditional main xref tables (including hybrid): offset of the **whitespace character preceding the first entry** of the **main cross-reference table** (entry for object 0).
  * If using xref streams only (PDF 1.5+): offset of the **main cross-reference stream object**.

* **`/P` (integer, optional; default 0)**
  Page number of the first page. If the document catalog `/OpenAction` opens to a page other than page 0, that page should be treated as the first page and `/P` reflects it.

---

## 4. File layout (what order bytes must appear in)

A linearized file is organized so a reader can parse early xref and hints. Key constraints you must implement:

### 4.1 There are TWO cross-reference regions in an unusual linkage order

* The file ends with a `startxref` that points to the **first-page cross-reference** section (near the beginning/middle), not the main xref.
* The **first-page trailer** has a `/Prev` that points to the **main xref** near the end. A non-linearization-aware reader interprets this as an incremental update chain (but in reverse physical order).
* The **main trailer** (at end) **should not** have `/Prev`.

### 4.2 Cross-reference streams are allowed

In PDF 1.5+, linearized files may use **xref streams** in place of traditional xref tables; the logical requirements above still apply with syntax changes.

---

## 5. The “first-page section” (what must be placed early)

This section contains everything needed to display the first page (usually page 0 unless `/OpenAction` changes that).

### 5.1 The first page’s page object MUST be the first object in this section

* The page object for the first page must be **the first object** in the first-page section.
* It must explicitly specify all required inheritable attributes (e.g., `Resources`, `MediaBox`) because attributes must not rely on inheritance from ancestor page tree nodes.

### 5.2 What objects MUST be included (closure rules)

Include:

* The page object for the first page.
* If catalog `/PageMode` is `UseOutlines`: the **entire outline hierarchy** here.
* All objects the first-page page object refers to (to arbitrary depth), **except**:

  * page tree nodes, and
  * other page objects.
    This includes objects reachable via `Contents`, `Resources`, `Annots`, and `B` (beads), but not `Thumb`.

### 5.3 Recommended object ordering (SHOULD)

For early interaction and progressive display, recommended ordering from the page object is: annotations first, beads, then resource dictionary, then resource objects in first-reference order, then contents (preferably split into streams and interleaved with needed resources).

---

## 6. The primary hint stream (required) and how its dictionary works

### 6.1 Primary hint stream is required

You must write a **primary hint stream** (a stream object). There may also be an optional overflow hint stream.

### 6.2 Hint stream dictionary must include per-table offsets (decoded bytes)

In addition to normal stream keys, the **primary hint stream dictionary** contains integer entries giving the **byte offset to the start of each hint table**:

* Offsets are relative to the beginning of the **decoded stream data** (after filters).
* If an overflow hint stream exists, treat it as **concatenated** to the decoded primary hint stream when interpreting these offsets.
* The overflow hint stream dictionary **should not** contain these offset keys.
* The **page offset hint table is required** and **must be the first table** and **must start at offset 0**.

### 6.3 Standard table keys (minimum required set)

The primary hint stream dictionary uses keys to indicate which hint tables exist; the shared object hint table **`S` is required**.

Practical minimum for a working linearized file:

* Page Offset Hint Table (required, implicit at offset 0)
* Shared Object Hint Table (`/S`, required)

If your document has outlines, thumbnails, etc., additional keys may be present (e.g., `/O` for outline, `/T` for thumbnails).

---

## 7. Critical offset rule: hint tables pretend the primary hint stream does not exist

**All file offsets stored inside hint tables** must be interpreted as if the **primary hint stream were not present** in the file. Concretely:

* When **decoding** a hint-table offset `pos`:

  * If `pos > hint_stream_offset` (the `offset1` from `/H`), then the real file offset is `pos + hint_stream_length` (the `length1` from `/H`).
  * Otherwise, `pos` is already the real offset.

* When **encoding** hint-table offsets (writer):

  * Compute object offsets in the final file,
  * then for any object that lies after the hint stream, **store (real_offset − hint_stream_length)** into hint tables.

This rule exists because the hint tables themselves determine the hint stream’s content/length, so they must not depend on knowing the hint stream length upfront.

---

## 8. Hint tables are bitstreams (packing rules you must implement)

Hint tables are stored as a **bitstream**, not aligned to byte boundaries. You must implement:

* a bit-level writer/reader that can append/read unsigned integers of arbitrary bit width,
* continuing across byte boundaries.

**Bit order (practical rule):** treat the decoded hint stream data as a sequence of bytes; interpret each byte in **MSB→LSB** bit order and pack fields sequentially. This matches how tooling (qpdf) suggests inspecting hint streams by printing each byte as 8 bits in order.

---

## 9. Page Offset Hint Table (required) — exact binary layout

### 9.1 Purpose

Provides per-page location/size information and, for every page except the first, enumerates referenced shared objects and where in the content stream they first appear (for progressive scheduling).

### 9.2 Overall layout

* Starts with a **header section** (Table F.3).
* Followed by **per-page entries** (Table F.4).
* **Per-page items are NOT stored contiguously per page**. They are stored in a specific sequence across pages.

### 9.3 Ordering of per-page items in the bitstream (MUST)

After the header, write items in this exact order:

1. Item 1 for all pages (page order starting with first page)
2. Item 2 for all pages
3. Item 3 for all pages
4. Item 4 for all shared objects in page 2, then page 3, …
5. Item 5 for all shared objects in page 2, then page 3, …
6. Item 6 for all pages
7. Item 7 for all pages

(“page 2” here means the second page in page order; the first page has special constraints below.)

### 9.4 Header fields (Table F.3) — write in this exact order

Each field is an **unsigned integer** with the given bit width:

1. **least_objects_per_page** — 32 bits
2. **first_page_obj_location** — 32 bits (location of first page’s page object)
3. **bits_for_objects_delta** — 16 bits (bits needed for max(objects_per_page−least_objects_per_page))
4. **least_page_length** — 32 bits (bytes from start of page object to last byte of last object used by that page)
5. **bits_for_page_length_delta** — 16 bits
6. **least_content_stream_start_offset** — 32 bits (relative to beginning of page; start of content stream *object*, not data)
7. **bits_for_content_stream_start_delta** — 16 bits
8. **least_content_stream_length** — 32 bits
9. **bits_for_content_stream_length_delta** — 16 bits
10. **bits_for_greatest_shared_objects_per_page** — 16 bits
11. **bits_for_greatest_shared_object_id** — 16 bits
12. **bits_for_shared_object_position_numerator** — 16 bits
13. **shared_object_position_denominator** — 16 bits

Note: “bits_for_*” fields are stored as 16-bit integers even though the range is 0..32.

### 9.5 Per-page entry fields (Table F.4)

For each page, the per-page entry conceptually contains 7 items; the bit width for items 1,2,4,5,6,7 is determined by header “bits_for_*” values.

**Item 1 (objects_per_page delta)**

* Bit width = `bits_for_objects_delta`.
* Stored value = `objects_per_page − least_objects_per_page`.
* **Object number rules (MUST follow):**

  * The first object of the first page has object number = `/O` from linearization dictionary.
  * The first object of the second page has object number **1**.
  * For later pages, first object number is computed by accumulating object counts of previous pages.

**Item 2 (page length delta)**

* Bit width = `bits_for_page_length_delta`.
* Stored value = `page_length − least_page_length`.
* Page 1 (first page) location is derived from `/O` and the first-page xref entry; subsequent pages’ locations are cumulative sums of prior page lengths, **skipping over the primary hint stream** wherever it is located.

**Item 3 (shared objects referenced from page)**

* Bit width = `bits_for_greatest_shared_objects_per_page`.
* For the **first page, this MUST be 0**.

**Item 4 (shared object identifiers)**

* Repeated `Item 3` times for the page.
* Bit width = `bits_for_greatest_shared_object_id`.
* Each value is an **index** into the shared object hint table (0-based). Not directly an object number.

**Item 5 (shared object position numerators)**

* Repeated `Item 3` times, parallel to item 4 ordering.
* Bit width = `bits_for_shared_object_position_numerator`.
* Interpreted with denominator = `shared_object_position_denominator` (header item 13).

  * If denom = d: numerator 0..d−1 means first reference occurs in that fraction of the page’s content stream.
  * Numerator = d means needed before images/XObjects and other trailing nonshared objects.
  * Numerator ≥ d+1 means needed after those objects.

**Item 6 (content stream start offset delta)**

* Bit width = `bits_for_content_stream_start_delta`.
* Stored value = `content_stream_start_offset − least_content_stream_start_offset`.
* Offset is relative to beginning of the page, and points to the **stream object**, not stream data.

**Item 7 (content stream length delta)**

* Bit width = `bits_for_content_stream_length_delta`.
* Stored value = `content_stream_length − least_content_stream_length`.
* Length includes object overhead before/after stream data.

---

## 10. Shared Object Hint Table (`/S`, required) — exact binary layout

### 10.1 Purpose

Lets the reader locate shared resources efficiently; shared objects may be located either:

* with first-page objects (part 6), or
* in the shared objects section (part 8).

A single shared object hint entry may describe a **group of adjacent objects** if only the first object is referenced from outside the group and the rest are only internally referenced; object numbers in the group must be adjacent.

### 10.2 Overall layout and ordering

* Header section (Table F.5)
* Followed by **shared object group entries** (Table F.6), split into two sequences:

  1. entries for objects located with the first page
  2. then entries for objects located in the shared objects section

**Group-entry items are not contiguous per entry**. Order for each sequence is: item 1 for all groups, then item 2 for all groups, then item 3…, then item 4…

### 10.3 Header fields (Table F.5) — write in this exact order

All unsigned:

1. **shared_section_first_objnum** — 32 bits
   Object number of the first object in the shared objects section (part 8).

2. **shared_section_first_location** — 32 bits
   File location of that first object (subject to “hint stream absent” offset rule).

3. **num_entries_first_page** — 32 bits
   Number of shared-object entries for the first page **including nonshared spans** (which are never used but are still present).

4. **num_entries_total** — 32 bits
   Number of shared-object entries for the shared objects section **including** the first-page entries (i.e., total entries).

5. **bits_for_greatest_objects_in_group** — 16 bits
   Bits needed to represent greatest number of objects in a group.

6. **least_group_length** — 32 bits
   Least group length in bytes.

7. **bits_for_group_length_delta** — 16 bits
   Bits to represent (max_group_length − least_group_length).

### 10.4 Shared object group entry fields (Table F.6)

Each group entry has up to 4 items:

**Item 1: group length delta**

* Bit width = `bits_for_group_length_delta`
* Stored value = `group_length − least_group_length`
* Group locations are computed by:

  * Starting from first-page location from page offset hint header (Table F.3 item 4 reference), accumulating prior group lengths for groups in first-page sequence, and
  * then using header’s `shared_section_first_location` for the first group in the shared section sequence.

**Item 2: signature-present flag**

* 1 bit.
* 1 = signature present; 0 = absent.

**Item 3: optional signature**

* 128 bits (16 bytes) **only if** item 2 is 1.
* Value is an **MD5 hash** identifying the resource for potential client-side caching/substitution.

**Item 4: object count minus 1**

* Bit width = `bits_for_greatest_objects_in_group`
* Stored value = `(num_objects_in_group − 1)`
* Object numbers are computed by accumulation:

  * first object of first page = `/O`
  * accumulate group sizes through first-page entries
  * then first object in shared objects section = `shared_section_first_objnum` (header item 1)

Special case: If document has only one page, its objects are still treated “as if shared” and the shared object hint table reflects this.

---

## 11. Generic hint tables (for outline, threads, named destinations, etc.)

Some hint tables referenced by keys like `/O` (outline), `/A` (article threads), `/E` (named destinations), `/I` (info dictionary) are described as “generic hint tables”.

### 11.1 Generic hint table entry (Table F.9)

Each entry is fixed-size 4×32-bit = 128 bits:

1. first object number in group
2. location of first object in group
3. number of objects in group
4. total byte length of the object group

A generic hint table is simply a sequence of these fixed-size entries; the number of entries is determined from the table length (derived from the next table’s offset or end of hint stream).

### 11.2 Extended generic hint table entry (Table F.10)

Starts with the same 4 items as generic, then:
5) number of shared object references (32 bits)
6) bits needed for numerically greatest shared object identifier used (16 bits)
7…) list of shared object identifiers (bit width as above), each an index into the shared object hint table

---

## 12. Object streams / compressed objects caveat (reader + writer)

If the PDF uses **object streams** (compressed objects), hint-table “positions” for compressed objects are **byte ranges**, not precise offsets; a reader should locate objects via cross-reference streams as if hint tables did not exist.

For a writer: ensure that any hint “location” you provide for compressed content covers the relevant object stream(s), and do not assume consumers will use it as an exact seek point.

---

## 13. Writer implementation algorithm (practical, works in production)

This is a concrete approach that matches how mature implementations do it (e.g., qpdf):

### Step 0 — Precondition

Start from a fully constructed, valid PDF object graph (after any optimization such as removing unreferenced objects, normalizing page trees, etc.).

### Step 1 — Determine “first page”

* Default: leftmost leaf in page tree (page 0).
* If catalog `/OpenAction` opens to another page, treat that page as first and set `/P`.

### Step 2 — Compute dependency closures and classify objects

For each page:

* Compute the transitive closure of objects reachable from the page object, excluding:

  * page tree nodes and
  * other page objects.
* Build a global reference count / reachability map to decide which objects are:

  * **page-local (nonshared)** (reachable only from one page and not otherwise needed), vs
  * **shared** (reachable from multiple pages or required across pages).

Also decide whether the outline hierarchy must be in first-page section (when `/PageMode UseOutlines`).

### Step 3 — Plan physical file ordering into sections (“parts”)

At minimum you need:

* header
* linearization dictionary (part 2)
* first-page xref + trailer (part 3)
* document catalog + page tree nodes needed to reach first page (part 4)
* primary hint stream (part 5)
* first-page section objects (part 6)
* remaining pages’ nonshared objects (later section)
* shared objects section
* main xref + trailer at end

(Exact partitioning beyond the first-page section is your design choice as long as hint tables and xrefs are consistent.)

### Step 4 — Assign object numbers consistent with hint table rules

You must assign object numbers so the page offset hint table’s stated assumptions can be satisfied, notably:

* first page’s first object number = `/O`
* second page’s first object number = 1
* later pages computed by accumulation of per-page object counts

This generally requires **renumbering** objects (do not attempt linearization by only reordering bytes; numbering and xrefs must be coherent).

### Step 5 — Implement two-pass writing (strongly recommended)

Because hint tables must act as if the hint stream is absent, and because the hint stream’s own length depends on the hint tables:

1. **Pass A (measure pass):**

   * Write the whole PDF to a sink (discard or temp file) with placeholders for:

     * `/L`, `/H` lengths, `/E`, `/T`,
     * xref offsets,
     * and hint tables themselves (you can write empty or reserved-length streams).
   * Record all object offsets and lengths.
   * Compute the final hint tables from those offsets using the “hint stream absent” rule.

2. **Pass B (final pass):**

   * Write the final PDF with correct values and correct hint stream content.
   * Any variable-length constructs that would shift offsets must be padded so offsets match what you measured/expect.
   * Adjust offsets that appear after the hint stream start by the hint stream length (now known).

This is exactly the kind of strategy documented by qpdf for linearization (including padding to keep offsets stable).

### Step 6 — Write first-page xref + trailer and main xref + trailer

* Ensure `startxref` points to the **first-page xref**.
* Ensure first-page trailer has `/Prev` pointing to main xref.
* Ensure main trailer does not have `/Prev`.

### Step 7 — Final validation checks (minimum)

* linearization dictionary is first object and within first 1024 bytes
* `/L` matches actual byte length
* `/H` offsets/lengths match actual hint stream object extents
* page offset hint table starts at decoded hint stream offset 0
* shared object hint table offset `/S` points to correct decoded offset
* first page has 0 shared object references in page offset hint table

---

## 14. Reader-side algorithm (if you are implementing a streaming viewer)

1. Fetch first ~1024 bytes; parse first indirect object as linearization dictionary. If missing or invalid, fall back to ordinary PDF behavior.
2. Use `/H` to range-fetch the primary hint stream object; decode it; locate tables using offsets in hint stream dictionary (decoded offsets; page offset table at 0).
3. Decode page offset and shared object hint tables bit-by-bit; when you obtain a file offset from a hint table, apply the “hint stream absent” correction (add hint length if offset is after hint stream).
4. Fetch required byte ranges for first page and progressively for subsequent pages.

---

## 15. Practical recommendation (engineering reality)

Implementing full linearization correctly is non-trivial (bitstreams, renumbering, two xref regions with unusual linkage, offset correction rules). If you are implementing this for production, treat **qpdf’s implementation and its `--check-linearization/--show-linearization` tooling** as a test oracle and behavioral reference.

---

If you tell me whether your target is **(A) a linearized-PDF writer**, **(B) a streaming reader**, or **(C) both**, I can provide a tighter, role-specific checklist and reference pseudocode (bitstream pack/unpack + hint computation) without adding any new external dependencies.
