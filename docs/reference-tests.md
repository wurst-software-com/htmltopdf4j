# The reference engine's unit tests, and what became of them

The Rust `htmltopdf` crate carries **219 in-module tests**. Issue #18 asks that
they be ported where they assert behaviour the render seam cannot reach, and
dropped where they assert internal structure this design does not reproduce.
This is that accounting: every one is in exactly one row below.

The Java suite is **606 tests**, of which 48 are the Fixtures — 41 ported from
the reference corpus plus seven this port authored itself, each of which says in
its Expectation why it had no reference Expectation to port. It is larger than
the reference's because a hand translation needs its own tests at the seams the
translation introduced — `Woff`, `FontEnvironment`, `FaceRegistry`, `Laid` — not
because the reference was under-tested.

| Reference module | Tests | Ported | Dropped |
| --- | ---: | ---: | ---: |
| `html.rs` | 81 | 80 | 1 |
| `layout.rs` | 52 | 46 | 6 |
| `font.rs` | 22 | 21 | 1 |
| `image.rs` | 19 | 12 | 7 |
| `script.rs` | 18 | 0 | 18 |
| `dom.rs` | 12 | 0 | 12 |
| `pdf.rs` | 8 | 8 | 0 |
| `lib.rs` | 6 | 3 | 3 |
| `subset.rs` | 1 | 1 | 0 |
| **Total** | **219** | **171** | **48** |

## Ported

Ported does not mean transliterated. Where the reference asserted on its own
data structures and this port has a different one, the assertion was rewritten
against the Java equivalent; where several reference tests cover one behaviour,
they may land in one Java test or several.

- **`html.rs` (80)** — parsing, the Cascade, selector matching, inheritance,
  specificity, `@media`, `@page`, `@font-face`, custom properties, `calc()`,
  shorthand expansion, generated content, and Box tree generation. These live in
  `CascadeTest`, `ComputedStyleTest`, `ShorthandsTest`, `CssColorTest`,
  `LengthTest`, `ContentValueTest`, `LinearGradientTest` and
  `BoxTreeBuilderTest`.
- **`layout.rs` (46)** — flow, wrapping, pagination, margins, floats,
  positioning and z-index, flex, grid, tables, lists, alignment, decoration,
  backgrounds and gradients, `@page` margin boxes, bidi. These live in
  `LayoutTest`, `LineBreakerTest` and `FloatsTest`, driven through the `Laid`
  helper, which takes HTML as far as the Pages.
- **`font.rs` (21)** — face discovery and variant selection, shaping, cluster
  reconstruction, kerning in measurement, the Fallback chain, WinAnsi encoding,
  WOFF1 unwrapping, and `@font-face` shadowing an installed family. These live
  in `FontEnvironmentTest`, `FaceChainTest`, `FaceRegistryTest`, `WoffTest`,
  `GlyphIdentityTest` and `FontFaceSourceTest`.
- **`image.rs` (12)** — PNG and JPEG decoding, the soft mask, `data:` URIs,
  sizing from CSS and HTML attributes, inline versus block images, and the
  refusal to fetch `http(s)`. These live in `ImageLoaderTest` and `LayoutTest`.
- **`pdf.rs` (8)** and **`subset.rs` (1)** — the writer's escaping, colour and
  clip operators, `/ToUnicode`, UTF-16BE hex, Type0 embedding, annotations and
  outline, and the subsetter. These live in `PdfDocumentWriterTest` and
  `TrueTypeSubsetterTest`.
- **`lib.rs` (3)** — `renders_pdf_header`, `rejects_empty_documents` and
  `cjk_renders_via_fallback_chain`, in `EngineTest` and the `edge-cases/unicode`
  Fixture.

## Dropped, and why

### The scripting stage — 21 tests

All 18 of `script.rs`, plus `lib.rs`'s `noop_scripting_matches_static_render`,
`scripting_mutates_rendered_document` and `script_built_content_renders`.

Issue #1 puts the scripting stage out of scope. Nothing in this port evaluates
script, so there is nothing for these to assert. That includes the node and wall
budgets (`create_element_respects_node_budget`, `inner_html_respects_node_budget`,
`inner_html_cannot_overshoot_a_partially_used_node_budget`,
`rejects_scripts_larger_than_the_heap_source_budget`,
`stops_before_evaluation_when_the_wall_budget_is_exhausted`,
`loop_iteration_limit_stops_runaway_scripts`, `default_limits_disable_io`),
which exist to bound an interpreter this port does not have.

### The arena DOM — 12 tests

All of `dom.rs`. ADR 0003 replaces the reference's arena with jsoup, so
`root_is_document`, `parent_links_are_consistent`,
`append_child_refuses_cycles_and_bad_parents`, `matches_rcdom_reference_tree` and
the rest assert the internals of a data structure this port does not contain.
The parsing *behaviour* they also covered — malformed nesting, entity decoding,
comments and doctype, attributes and classes — is jsoup's own contract, tested by
jsoup, and reached here through the Fixtures.

### A fetcher this port does not have — 3 tests

`image.rs`'s `parses_url_host_and_port`, `blocks_private_and_loopback_ips` and
`resolves_only_public_addresses_for_pinned_fetches`. Remote fetching is out of
scope, so there is no URL parser and no address classifier to test. The part
that *is* in scope — that an `http(s)` source is refused rather than fetched —
is ported, from `remote_urls_are_fail_closed_by_default`,
`detects_remote_url_schemes` and `font_face_remote_url_is_fail_closed`.

### Substituted library internals — 5 tests

- `image.rs` `base64_round_trips_known_vectors` and
  `base64_encode_decode_round_trip` — `java.util.Base64` is the JDK's, not ours.
- `image.rs` `png_up_filter_is_reconstructed` and
  `rejects_pngs_that_expand_past_the_decoded_byte_cap` — ImageIO decodes PNG, so
  the Up filter and the decoded-byte cap are the Rust decoder's internals. What
  the decoded image must *look* like is ported.
- `font.rs` `lock_recover_survives_a_poisoned_mutex` — there is no `Mutex` to
  poison; the file cache is a `ConcurrentHashMap`.

### Behaviour this port does not implement — 4 tests

These are the only dropped tests that name real rendering behaviour, and each
one is a **None** row in the [Coverage matrix](coverage-matrix.md):

- `layout.rs` `shrinks_to_fit_a_table_wider_than_the_page`,
  `scales_font_only_when_min_content_overflows_the_page`,
  `keeps_font_size_when_content_fits_the_page`,
  `does_not_shrink_full_span_unbordered_caption_cells` — the reference shrinks a
  document's font size when its minimum content width overflows the page. This
  port does not: an overlong word is broken within itself and an overwide table
  is scaled by column, but the font size is never changed behind the author's
  back.

### Unimplemented feature corners — 3 tests

- `layout.rs` `align_items_stretch_fills_a_definite_container_height` —
  `align-items` is honoured on a grid container but not on a flex one, where
  items are stretched to the line unconditionally.
- `layout.rs` `grid_line_placement_and_minmax_size_tracks` — the line-placement
  half is ported; `minmax()` is not implemented, so the test could not be ported
  whole.
- `html.rs` `parses_background_image_url_with_size_position_repeat` — the `url()`
  half is ported. `background-size`, `background-position` and
  `background-repeat` cascade to a computed value that nothing reads: a
  background image is drawn once, filling the box. Asserting they parse would
  test the Cascade and imply a painting behaviour the engine does not have.
