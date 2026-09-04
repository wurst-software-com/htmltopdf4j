# htmltopdf4j

A pure-Java HTML-to-PDF rendering engine. HTML and CSS go in, PDF bytes come
out, with no browser subprocess, no native library and no temporary files.

It is a hand-written translation of the Rust [`htmltopdf`](https://github.com/SanzarRehman/html2pdf)
engine by Sanzar Rahman into idiomatic Java 21, used under the MIT licence, and
it is a *library*: there is no CLI, no HTTP server and no scripting
stage. Rendering is deliberately offline — a Document that names an `http(s)`
image, font or stylesheet makes no network request. A `<link rel=stylesheet>`
or an `@import` naming a file under `RenderOptions.baseDirectory` *is* read
from disk; anything above that directory, and everything when no base directory
was given, is skipped rather than fetched.

## Using it

```xml
<dependency>
  <groupId>com.wurstsoftware</groupId>
  <artifactId>htmltopdf4j</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
Engine engine = new Engine();

byte[] pdf = engine.renderHtml("<h1>Invoice</h1><p>Thank you.</p>");
```

Everything a render can be told is on `RenderOptions`, which is immutable and
built through a builder:

```java
RenderOptions options = RenderOptions.builder()
        .paper(Paper.LETTER)
        .margins(72f, 54f, 72f, 54f)          // points, clockwise from the top
        .baseDirectory(Path.of("/srv/assets")) // where relative url(...) resolves
        .defaultFace(FaceSource.HELVETICA)
        .build();

byte[] pdf = engine.renderHtml(html, options);
```

`renderHtml` throws `EmptyDocumentException` when the Document would put nothing
on a page, and `RenderException` when it cannot be rendered at all. Both are
unchecked: a template that does not render is a programming error, not a
condition every caller should have to handle.

## Thread safety

- **`Engine` is safe to share.** It holds no per-render state, so one instance
  can serve an application for its lifetime and be called from any number of
  threads at once. Everything a render needs — its Faces, its images, its links
  — lives in objects created inside `renderHtml`.
- **`RenderOptions` is immutable.** A render never mutates the options it is
  given, so one options value can drive many concurrent renders. `toBuilder()`
  returns a fresh builder when you want a variant.
- **`RenderOptions.Builder` is not.** Build it on one thread, then share the
  built value.
- **The installed-font scan is process-wide and happens once**, behind a
  double-checked lock. A machine's fonts do not change while a render runs.

The same HTML and options render to the same bytes every time, on any thread.

## What it renders

See [docs/coverage-matrix.md](docs/coverage-matrix.md) for the per-element and
per-property detail: what renders fully, what renders partially, and what is not
implemented. In outline it covers block and inline flow with pagination, the
Cascade with custom properties and `calc()`, borders, backgrounds and linear
gradients, tables with spans and repeating headers, floats and positioning,
flexbox and grid, raster images, links and outlines, `@page` margin boxes and
generated content, real font embedding with subsetting, shaping through the
JDK's own layout engine, and bidirectional text.

## Dependencies

Four at runtime, all pure Java:

| Dependency | Why |
| --- | --- |
| [jsoup](https://jsoup.org) | HTML parsing and the DOM the Cascade matches against |
| [ph-css](https://github.com/phax/ph-css) | CSS tokenising and parsing (the Cascade itself is hand-written) |
| [FontBox](https://pdfbox.apache.org) | reading font `name`, `head`, `hmtx` and `cmap` tables |
| `java.desktop` (JDK) | `Font.layoutGlyphVector` is the shaper; `ImageIO` decodes rasters |

`java.desktop` is a hard requirement, not an optional one — see
[ADR 0002](docs/adr/0002-awt-glyph-layout-as-the-shaper.md). The PDF writer,
the TrueType subsetter and the WOFF1 reader are hand-written against
`java.util.zip`; nothing else is needed to produce output.

The module is named `com.wurstsoftware.htmltopdf4j` and exports only its root
package. The Cascade, Box tree, Layout, the Display list and the writer are
internal.

Test-only: JUnit 5, PDFBox (an independent reader, so a structurally broken file
fails loudly rather than passing a string match), and Jackson for the
Expectation files.

## Building

```
mvn test        # 574 tests, including 48 Fixtures against their Expectations
```

Java 21 is required to build and to run.

All 48 Fixtures pass — 41 ported from the reference corpus, plus seven this
port authored itself, each of which says in its Expectation why it had no
reference Expectation to port.

`src/test/resources/parity-known-failures.txt` — the ledger that reports a
listed Fixture as skipped rather than failed — is empty.
The mechanism stays because it can only shrink: a listed Fixture that *passes*
fails the build.

It is empty by construction: no Fixture reads a font from outside the
repository. The two that assert on face names declare their families with
`@font-face` against four DejaVu faces the corpus carries, under the Bitstream
Vera licence in `src/test/resources/fixtures/features/fonts/`. See
[ADR 0006](docs/adr/0006-carry-the-fonts-the-fixtures-need.md), which also
records what that costs: resolving a family against a font the *host* has
installed is covered by unit tests that skip, not by a Fixture.

## Design notes

- [CONTEXT.md](CONTEXT.md) — the domain vocabulary this codebase speaks.
- [docs/adr/](docs/adr/) — the decisions that shaped the port.
- [docs/coverage-matrix.md](docs/coverage-matrix.md) — what renders, and how well.
- [docs/reference-tests.md](docs/reference-tests.md) — every one of the Rust
  engine's 219 unit tests, and whether it was ported or dropped.

## Licence

htmltopdf4j is MIT-licensed — see [LICENSE](LICENSE).

This library is a Java port of the `htmltopdf` rendering engine originally
written in Rust by Sanzar Rahman (<https://github.com/SanzarRehman/html2pdf>),
used under the MIT licence. A port is a derivative work, so the upstream
copyright and permission notice travels with this one: it is reproduced, with
what it covers and where the grant comes from, in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md), which also lists the licences
of the declared dependencies.
