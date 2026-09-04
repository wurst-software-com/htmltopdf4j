# Coverage matrix

What this engine renders, per HTML element and CSS feature: **Full**, **Partial**
or **None**. "Partial" always says what is missing.

This is the honest state of the port, not a roadmap. It is written against the
code, and the Parity harness — 41 Fixtures, one JUnit case each — is what keeps
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
| `sub`, `sup` | Partial | Sized down, but not raised or lowered — no `vertical-align` |
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
| `@font-face` with `local()`, `url()` and `data:` | Full | `http(s)` sources are refused, not fetched |
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
| `width`, `height`, `min-width`, `max-width`, `max-height` | Full | |
| `min-height` | None | |
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
| `text-align: left`, `right`, `center` | Full | |
| `text-align: justify` | None | Falls back to left |
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
| `float: left`, `right`, `none`; `clear` | Full | Floats become bands the line breaker consults line by line |
| `position: static`, `relative`, `absolute`, `fixed` | Full | Absolute offsets resolve against the page area, which is the initial containing block in paged media. `fixed` repeats on every Page |
| `position: sticky` | None | |
| `z-index` | Full | Positioned boxes are painted in a post-pass sorted by z-index |
| Flexbox: `flex-direction`, `flex-wrap`, `flex`, `flex-grow/shrink/basis`, `justify-content`, `order`, `gap` | Full | Inline children of a flex container are blockified |
| Flexbox: `align-items`, `align-content`, `align-self` | None | Items are stretched to the line |
| Grid: `grid-template-columns` (lengths, `fr`, `repeat()`), `grid-template-areas`, `grid-column`, `grid-row`, `grid-area`, `gap` | Full | Definite tracks first, then `fr` shares the remainder; row-major placement |
| Grid: `grid-template-rows`, `grid-auto-flow`, `grid-auto-rows`, `minmax()` | None | Rows are sized to their content |
| Tables: automatic column widths, `colspan`, `rowspan`, header and footer groups | Full | A rowspan occupancy grid keeps the rows below a spanning cell aligned |
| Tables: `border-collapse`, `table-layout: fixed` | None | Borders are always separate |
| Multi-column (`column-count`, `column-width`) | None | |

## Paged media

| Feature | Support | Notes |
| --- | --- | --- |
| Pagination | Full | A single downward pass; a block that does not fit continues on the next Page |
| `page-break-before/after: always`, `break-before/after` | Full | |
| `page-break-inside: avoid` | None | |
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

## Known environment-dependent gaps

Two Fixtures are in the known-failures ledger
(`src/test/resources/parity-known-failures.txt`) because they name fonts that are
not installed on the build machine — `features/font-family` and
`features/font-face`. They are skipped rather than failed, and the ledger can
only shrink: a listed Fixture that starts passing fails the build.
