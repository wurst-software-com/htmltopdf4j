# Coverage matrix

What this engine renders, per HTML element and CSS feature: **Full**, **Partial**
or **None**. "Partial" always says what is missing.

This is the honest state of the port, not a roadmap. It is written against the
code, and the Parity harness — 44 Fixtures, one JUnit case each — is what keeps
it from drifting. Where a row says Full, there is a Fixture or a unit test that
would fail if it stopped being true.

Scope is set by [issue #1](https://github.com/wurst-software-com/htmltopdf4j/issues/1):
no CLI, no HTTP server, no scripting stage, no remote `http(s)` fetching, and no
byte-identical output. Anything outside that is None here and stays None.

## HTML elements

| Element | Support | Notes |
| --- | --- | --- |
| `html`, `body`, `div`, `section`, `article`, `aside`, `header`, `footer`, `main`, `nav` | Full | Block containers |
| `p`, `h1`–`h6`, `blockquote`, `figure`, `figcaption`, `address`, `center` | Full | With the user-agent margins |
| `span`, `b`, `strong`, `i`, `em`, `cite`, `var`, `dfn`, `small`, `big`, `code`, `kbd`, `samp`, `tt`, `mark` | Full | Inline runs |
| `u`, `ins`, `s`, `strike`, `del` | Full | Underline and line-through |
| `sub`, `sup` | Partial | Sized down, but not raised or lowered — `vertical-align` moves table cells only |
| `br` | Full | Forces a line break |
| `hr` | Full | Drawn as a rule |
| `pre` | Full | `white-space: pre` and a monospace Face |
| `ul`, `ol`, `li` | Full | `disc`, `circle`, `square`, `decimal`, `lower-alpha`, `upper-alpha`, `lower-roman`, `upper-roman` |
| `dl`, `dt`, `dd` | Full | |
| `table`, `thead`, `tbody`, `tfoot`, `tr`, `th`, `td` | Full | Including `colspan` and `rowspan` |
| `caption` | Full | |
| `col`, `colgroup` | None | Parsed and ignored; column widths come from the cells |
| `a` | Full | External links and `#fragment` links both become PDF link annotations |
| `img` | Full | See *Images* below |
| `style` | Full | Every `<style>` in the Document contributes |
| `head`, `link`, `meta`, `script`, `title`, `base`, `template`, `noscript` | None | Generate no box, by design. `<link rel=stylesheet>` is not fetched |
| `input`, `button`, `select`, `textarea` | Partial | Laid out as empty inline-blocks; no widget is drawn |
| `svg`, `canvas`, `video`, `audio`, `iframe`, `object` | None | |

## Selectors

| Feature | Support | Notes |
| --- | --- | --- |
| Type, class, id, universal | Full | |
| Descendant, child, sibling, attribute, `:not()`, `:nth-child()` | Full | Matched by jsoup, so its selector syntax is what is supported |
| `::before`, `::after` (and the legacy one-colon form) | Full | The pseudo is stripped and the element part matched |
| `:hover`, `:focus`, `:active` and the other interactive pseudo-classes | None | Meaningless on paper |
| `::first-line`, `::first-letter`, `::marker` | None | A selector the matcher cannot handle matches nothing rather than failing the render |
| Specificity, source order, `!important`, origins | Full | UA normal < author normal < author important < UA important |
| Inline `style` attribute | Full | Beaten only by an important author rule |

## At-rules

| Feature | Support | Notes |
| --- | --- | --- |
| `@media print`, `@media all`, bare feature queries | Full | Evaluated once per render: this is a printer |
| `@media screen` | Full | Correctly does *not* apply |
| `@page` size, orientation and margins | Full | |
| `@page` margin boxes (`@top-center` and friends) | Full | Painted after pagination, because `counter(pages)` needs the final Page count |
| `@font-face` with `local()`, `url()` and `data:` | Full | `local()` matches a face's full or PostScript name, not only its family. An alternative hinted `format(woff2)`, `format(svg)` or `format(embedded-opentype)` is skipped without being read. `http(s)` sources are refused, not fetched |
| `@font-face` `font-weight` and `font-style` descriptors | Full | A second rule for one family supplies its bold or italic variant rather than replacing it |
| `@font-face` source formats | Partial | Bare SFNT (`.ttf`, `.otf`) and WOFF1, which is unwrapped to SFNT. WOFF2 needs Brotli, which the JDK does not carry, so it is refused and the `src` chain moves on |
| `@import` | None | Would be a network or file fetch mid-parse |
| `@supports`, `@keyframes`, `@layer`, `@container` | None | Ignored; the rules around them survive |

## Values

| Feature | Support | Notes |
| --- | --- | --- |
| Lengths `px`, `pt`, `em`, `rem`, `ex`, `in`, `cm`, `mm`, `pc`, `%` | Full | |
| `ch`, `vw`, `vh`, `vmin`, `vmax` | None | |
| `calc()` | Partial | Additions and subtractions between one relative term and absolute ones, e.g. `calc(100% - 2rem)`. Two different relative units, nested `calc()`, `min()` and `max()` yield nothing, so the declaration is dropped rather than mis-sized |
| Custom properties and `var()`, with fallbacks | Full | Always inherit; substitution is bounded at depth 16, and a cycle makes the declaration invalid at computed-value time |
| `inherit`, `initial`, `unset` | Full | Honoured for every property at once |
| `revert`, `revert-layer` | None | |
| Colours: `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`, `rgb()`, `rgba()`, `hsl()`, `hsla()`, named, `transparent` | Full | Alpha is parsed; the PDF is painted opaque |
| `currentColor` | None | |
| `attr()` and `counter()` in `content` | Partial | `counter(page)` and `counter(pages)`; other counters make the value generate nothing rather than half a label |

## Box model and flow

| Feature | Support | Notes |
| --- | --- | --- |
| `display: block`, `inline`, `inline-block`, `list-item`, `none`, `flow-root` | Full | |
| `margin`, `padding`, `border` and their longhands | Full | Including the one-to-four value pattern |
| `border-style` | Partial | `none`, `solid`, `dashed` and `dotted` are drawn as declared; `double`, `groove`, `ridge`, `inset` and `outset` are drawn solid |
| `border-radius` | Full | Including the four-corner form; the elliptical `/` form uses its horizontal half |
| `box-sizing` | Full | `content-box` and `border-box` |
| `width`, `height`, `min-width`, `max-width`, `min-height`, `max-height` | Full | `min-height` holds a short block open, the same rule `height` already followed |
| Percentage widths and heights | Full | |
| Margin collapsing | Partial | Adjacent siblings collapse; parent/first-child collapsing does not |
| `overflow: hidden` | Partial | Emitted as a PDF clip, but only for a box with a definite height — without one there is nothing for the content to overflow |
| `visibility: hidden` | Full | |
| `opacity`, `box-shadow`, `text-shadow`, `transform`, `filter` | None | |

## Text

| Feature | Support | Notes |
| --- | --- | --- |
| `font-family` with a fallback list and the generic families | Full | Resolved against the installed fonts; a family that is not installed falls through to the next |
| `font-size` (lengths, percentages and the absolute keywords), `font-weight`, `font-style` | Full | |
| `font` shorthand | Full | Including the `size/line-height` form |
| Faux bold | Full | Fill plus a thin stroke, when the family has no real bold Face |
| Faux italic | None | An italic-less family is drawn upright |
| `line-height` (`normal`, unitless, lengths, percentages) | Full | A unitless value inherits as a multiplier |
| `text-align: left`, `right`, `center`, `justify` | Full | Justification widens the gaps between words — not the gaps inside a word split across styles — and squares the line off against the right margin; the last line of a block keeps the ragged edge it fell with |
| `text-decoration: underline`, `line-through`, `none` | Full | |
| `text-decoration: overline` | None | |
| `text-transform`, `text-indent`, `letter-spacing`, `word-spacing` | Full | |
| `white-space: normal`, `pre`, `nowrap`, `pre-wrap`, `break-spaces` | Full | |
| `word-break`, overlong-word breaking | Full | A word wider than the line is broken within itself rather than overflowing |
| Line breaking | Full | Greedy, matching Chromium, which the Expectations were recorded against |
| Ligatures and kerning | Full | Via `java.awt.Font.layoutGlyphVector` |
| Fallback chain for missing glyphs | Full | One level deep; a character covered nowhere draws the primary's `.notdef` |
| `direction: rtl` and bidi reordering | Full | Via `java.text.Bidi` |
| Vertical writing modes | None | |
| Hyphenation | None | |

## Layout modes

| Feature | Support | Notes |
| --- | --- | --- |
| Normal flow | Full | |
| Inline box decoration: `background`, `border`, `border-radius`, `padding` on a `<span>` | Full | Painted once per line the box occupies. The left border and left padding are on the first line, the right on the last, and top and bottom on all of them; a box cut by a line break is drawn square, because the corners the break made are not corners. Horizontal padding is space on the line; vertical padding grows the painted box without changing the line box, which is what CSS says |
| `float: left`, `right`, `none`; `clear` | Full | Floats become bands the line breaker consults line by line. A declared `height` or `min-height` sizes the painted box and the band alike, and is a minimum: content that overruns it makes the float taller |
| `position: static`, `relative`, `absolute`, `fixed` | Full | Absolute offsets resolve against the page area, which is the initial containing block in paged media. `fixed` repeats on every Page |
| `position: sticky` | None | |
| `z-index` | Full | Positioned boxes are painted in a post-pass sorted by z-index |
| Flexbox: `flex-direction`, `flex-wrap`, `flex`, `flex-grow/shrink/basis`, `justify-content`, `order`, `gap` | Full | Inline children of a flex container are blockified |
| Flexbox: `align-items`, `align-content`, `align-self` | None | Items are stretched to the line whatever the value says |
| Grid: `grid-template-columns` (lengths, `fr`, `repeat()`), `grid-template-areas`, `grid-column`, `grid-row`, `grid-area`, `gap` | Full | Definite tracks first, then `fr` shares the remainder; row-major placement |
| Grid: `grid-template-rows` (lengths, `fr`, `auto`) | Full | An `fr` row shares the container's declared `height`; with no declared height it is content-sized, because a fraction needs something to be a fraction of |
| Grid: `align-items`, `align-self` | Partial | `center`/`middle` and `end`/`flex-end`/`self-end` place the item in its row; `stretch`, the initial value, leaves it content-sized rather than growing it to the track |
| Grid: `grid-auto-flow`, `grid-auto-rows`, `minmax()` | None | Rows not named by a track are sized to their content |
| Tables: automatic column widths, `colspan`, `rowspan`, header and footer groups | Full | A rowspan occupancy grid keeps the rows below a spanning cell aligned |
| Tables: per-side cell borders | Full | A cell declaring only `border-bottom` gets one line under it, through the same per-side stroking blocks use |
| Tables: `vertical-align` on a cell | Partial | `middle` and `bottom` place the content in the row; `top` is honoured and `baseline`, the initial value, is treated as `top` rather than aligning the cells' first baselines |
| Tables: `border-collapse`, `table-layout: fixed` | None | Borders are always separate |
| Multi-column (`column-count`, `column-width`) | None | |

## Paged media

| Feature | Support | Notes |
| --- | --- | --- |
| Pagination | Full | A single downward pass; a block that does not fit continues on the next Page |
| `page-break-before/after: always`, `break-before/after` | Full | |
| `page-break-inside: avoid`, `break-inside: avoid`/`avoid-page` | Full | A box that would be divided by the Page boundary moves whole to the next Page. It is a hint: a box taller than the content area breaks as if the property were absent, rather than wasting a Page. Honoured on blocks, tables, and grid rows and flex lines — where the whole row or line moves, since moving one item would tear it |
| `break-inside: avoid-column`, `avoid-region` | n/a | Accepted and ignored: this engine has neither columns nor regions, so there is nothing to avoid |
| `page-break-inside` on a `<tr>` | Full | A table row is never divided by a Page boundary in any case |
| `page-break-inside` on `<thead>`/`<tbody>` | None | A row group is not kept together; it is usually the whole table body, where `avoid` would degrade to `auto` anyway |
| A float at a Page boundary | Full | A float is placed whole: it is measured before it is laid out, so one that does not fit in what is left of the Page starts at the top of the next one, and the band it excludes belongs to the Page it is painted on |
| A float taller than the content area | Partial | It cannot be moved out of the way, so it is divided: it excludes a band on every Page it crosses, and the flow it was taken out of carries on from the Page it started on. Only the part inside the content area of the Pages it was laid out on is painted — a Page it merely overflows into reserves the room but draws nothing |
| A block image at a Page boundary | Full | Never divided — an image that does not fit moves whole to the next Page, whatever `break-inside` says |
| `orphans`, `widows` | Partial | Cascaded and inherited, but not yet enforced by the breaker |
| Running headers and footers | Full | Through `@page` margin boxes |
| `counter(page)`, `counter(pages)` | Full | |

## Backgrounds and images

| Feature | Support | Notes |
| --- | --- | --- |
| `background-color` | Full | |
| `background-image: linear-gradient()` | Full | Painted as bands, with the band count derived from the box size |
| `background-image: url()` | Full | |
| `radial-gradient()`, `conic-gradient()`, repeating gradients | None | |
| `background-repeat`, `background-position`, `background-size` | None | An image background is drawn once, filling the box |
| PNG, JPEG, GIF, BMP | Full | Anything `ImageIO` reads. JPEG bytes are carried through undecoded |
| PNG transparency | Full | Split into samples and a PDF soft mask; an all-opaque alpha channel costs nothing |
| `data:` image URIs | Full | |
| Remote (`http(s)`) images | None | Refused by design — a library that made network requests while rendering would be a surprise to embed in a server |
| SVG | None | |

## PDF output

| Feature | Support | Notes |
| --- | --- | --- |
| Font embedding and subsetting | Full | Subsetting retains glyph ids rather than renumbering them |
| Link annotations, internal and external | Full | |
| Document outline from `h1`–`h6` | Full | |
| Deflate compression | Full | |
| Encryption, tagged PDF, PDF/A, forms, attachments | None | |

## Environment dependence

The known-failures ledger (`src/test/resources/parity-known-failures.txt`) is
**empty**: all 44 Fixtures meet their Expectations. The mechanism stays, because
it can only shrink — a listed Fixture that starts passing fails the build — but
it currently lists nothing.

Two Fixtures do depend on the machine's fonts. `features/font-family` and
`features/font-face` assert that Georgia, Arial and Courier New appear in the
PDF's font objects, and this engine will not invent a `BaseFont` name for a Face
it does not have, so they need those families installed. The rest of the suite
needs no particular font: where a test needs a real font program it takes
whatever is installed, and skips when there is nothing at all.
