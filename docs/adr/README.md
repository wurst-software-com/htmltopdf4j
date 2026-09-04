# Decisions

Each file here records one decision this port made, in the order it was made.
They are decisions *for this codebase*, not translations of the reference
engine's — a Java 21 port faces a different set of constraints, and where the
reference reasoned about `Rc`, arenas and `Send`, most of that reasoning simply
does not arrive.

- [0001](0001-hand-written-pdf-writer.md) — a hand-written PDF writer, not PDFBox
- [0002](0002-awt-glyph-layout-as-the-shaper.md) — `Font.layoutGlyphVector` is the shaper
- [0003](0003-jsoup-dom-replaces-the-arena.md) — jsoup supplies the DOM
- [0004](0004-behavioural-parity-as-the-contract.md) — behavioural parity, not byte parity
- [0005](0005-measure-before-breaking-rather-than-rolling-back.md) — measure before breaking
- [0006](0006-carry-the-fonts-the-fixtures-need.md) — carry the fonts the Fixtures need

## What became of the reference engine's decisions

The Rust `htmltopdf` repository carries nine ADRs of its own. Because this port
is a hard fork at that tree ([ADR 0004](0004-behavioural-parity-as-the-contract.md))
they are not copied here — their reasoning is about Rust, and a copy would rot
against a repository nobody here updates. This is the accounting instead: every
one of the nine is in exactly one row.

| Reference ADR | Here |
| --- | --- |
| 0001 Display-list rendering architecture | **Adopted.** The stage boundary the whole engine hangs off — layout emits a Display list of Paint commands, and the PDF writer consumes only that. `paint/` and `pdf/` never see a Box tree. |
| 0002 DOM-based pipeline and foundation dependencies | **Superseded** by [ADR 0003](0003-jsoup-dom-replaces-the-arena.md). The pipeline stays DOM-based; only the representation changes, because the arena existed to dodge reference counting and a tracing collector makes that free. |
| 0003 Font embedding (`/TrueType` + `/WinAnsiEncoding`) | **Dead before the fork** — the reference superseded it with its own 0005. What survives is the `FontDescriptor` metrics and the compressed `/FontFile2` program, both of which this port's writer reproduces. |
| 0004 Nested flow box tree | **Adopted.** `box/` builds the same nested model, and Box tree is a term in [CONTEXT.md](../../CONTEXT.md). |
| 0005 CID/Unicode font embedding (Type0/Identity-H) | **Adopted**, and it is most of what [ADR 0001](0001-hand-written-pdf-writer.md) exists to defend: PDFBox's String-oriented text API is what makes writing Identity-H by hand cheaper than bending it. |
| 0006 Bounded pre-layout JavaScript | **Out of scope**, by [issue #1](https://github.com/wurst-software-com/htmltopdf4j/issues/1). No script stage, no engine dependency, and the reference's 18 `script.rs` tests are dropped rather than ported (see [reference-tests.md](../reference-tests.md)). Data-driven documents belong in a template engine in front of this one. |
| 0007 Raster images via PDF image XObjects | **Adopted with one substitution.** JPEG still embeds verbatim through `DCTDecode`; the reference's hand-written PNG decoder is replaced by ImageIO, which the JDK already ships, with the alpha channel becoming an `/SMask` exactly as before. Remote fetching is *absent* rather than disabled — rendering a hostile Document cannot reach the network. |
| 0008 Live-DOM `innerHTML` spike | **Out of scope**, following 0006. |
| 0009 Live-DOM tree mutation, and no mid-script layout reads | **Out of scope**, following 0006; its arena reasoning is separately superseded by [ADR 0003](0003-jsoup-dom-replaces-the-arena.md). |

The reference's other documents are deliberately not carried. `IMPLEMENTATION.md`
and `PLAN.md` are its own build narrative, which the tracker replaces here;
`docs/COVERAGE.md` is replaced by [coverage-matrix.md](../coverage-matrix.md),
written against this code and held in place by the Parity harness; and the Chrome
visual-diff scripts stay in the reference repo, as [ADR 0004](0004-behavioural-parity-as-the-contract.md)
says.
