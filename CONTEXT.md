# htmltopdf4j

A Java 21 HTML-to-PDF rendering engine: HTML and CSS in, PDF bytes out, with no
browser subprocess. It is a hand-written translation of the Rust `htmltopdf`
engine, and the vocabulary below is the language that translation speaks.

## Language

### The pipeline

**Document**:
A parsed HTML input together with the styles that apply to it. The unit a single
render consumes.
_Avoid_: Page (a Document spans many), File, Input

**Cascade**:
The resolution of every CSS declaration that could apply to an element down to
one computed value per property, by specificity and source order.
_Avoid_: Styling, Resolution

**Box tree**:
The tree of things that will be drawn, generated from the Document after the
Cascade. Elements that generate no box (`display: none`) are absent from it.
_Avoid_: Render tree, Element tree, DOM (the DOM is upstream of it)

**Flow**:
The subset of the Box tree laid out as ordinary block and inline content —
headings, paragraphs, lists — as distinct from table content.
_Avoid_: Body, Content, Stream

**Layout**:
The assignment of a position and size, in points, to every box, including line
breaking and the division of content across Pages.
_Avoid_: Positioning, Measurement (measurement is one input to it)

**Page**:
One sheet of output at a fixed Paper size and orientation, holding the Paint
commands that fall on it. Produced by Layout, consumed by the writer.
_Avoid_: Sheet, Canvas

**Content area**:
The band of a Page that Flow content may occupy: the sheet less its margins.
What "fits on a Page" is measured against.
_Avoid_: Page box, Body area, Viewport (there is no viewport in a paged medium)

**Unbreakable box**:
A box that Layout must place on one Page or not at all, rather than dividing it
at the Page boundary. A request, not a guarantee: a box taller than the Content
area is divided anyway.
_Avoid_: Atomic box (an atomic inline is a different thing — an image or an
inline-block on a line), Keep-together, Widow

**Display list**:
The complete, backend-neutral sequence of Paint commands for a Page. The seam
that keeps Layout from knowing anything about PDF.
_Avoid_: Draw list, Command buffer, Instructions

**Paint command**:
One drawing primitive in a Display list — text, a rectangle, a line, a clip, an
image, or a graphics-state change. Expressed in points, origin bottom-left.
_Avoid_: Operation, Op, Primitive

### Text

**Face**:
A single font program: one family at one weight and one style, backed by the
bytes that will be embedded.
_Avoid_: Font file, Typeface

**Shaped run**:
A string converted by the shaper into positioned Glyphs, with kerning,
ligatures and joining applied. The unit both measurement and text output use.
_Avoid_: Text run, Segment, Shaped text

**Glyph**:
One drawable shape in a Face, identified by its glyph id, carrying an advance
and the characters it covers.
_Avoid_: Character, Symbol

**Fallback chain**:
The ordered Faces consulted for characters the primary Face cannot draw. One
level deep: a fallback Face has no chain of its own.
_Avoid_: Font stack, Substitution list

**Subsetting**:
Rebuilding a Face so it carries outlines only for the Glyphs a Document
actually uses, keeping glyph ids unchanged.
_Avoid_: Stripping, Pruning, Trimming

### Verification

**Parity**:
Agreement between this engine's output and the reference Rust engine's, judged
by the Expectations rather than by byte equality.
_Avoid_: Compatibility, Equivalence, Match

**Fixture**:
An HTML input held in the test corpus to exercise a named area of the Coverage
matrix.
_Avoid_: Test case, Sample, Example

**Expectation**:
The recorded assertions a Fixture's rendered output must satisfy. Ported from
the reference engine and authoritative for Parity.
_Avoid_: Snapshot, Golden file, Baseline

**Coverage matrix**:
The support table stating, per HTML element and CSS feature, whether the engine
renders it fully, partially, or not at all.
_Avoid_: Feature list, Support table, Roadmap
