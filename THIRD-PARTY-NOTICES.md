# Third-party notices

## The engine this library is a port of

`htmltopdf4j` is a hand-written Java translation of the Rust engine
**`htmltopdf`** by **Sanzar Rahman**.

| | |
| --- | --- |
| Upstream repository | <https://github.com/SanzarRehman/html2pdf> |
| Author | Sanzar Rahman (`sanzar.rahman@bracits.com`), sole author of all 83 commits |
| Commit this port was translated from | `c744c5fcd37026879bf699c272804abeea27cc22` (10 August 2026) |
| Licence | MIT, declared in the workspace `Cargo.toml` (`license = "MIT"`) and in the README's *License* section |

A translation of a program into another language is a derivative work, so the
MIT grant above covers this repository and travels with it.

### A note on the notice below

The upstream repository **carries no `LICENSE` file**: the MIT grant is declared
by the SPDX identifier in `Cargo.toml` and restated in the README, and GitHub
reports no licence file for the repository. There is therefore no upstream
copyright-and-permission notice to reproduce verbatim.

What follows is the standard MIT licence text that the upstream SPDX identifier
denotes, with the copyright holder taken from the repository's git history and
the year from its commit dates (every commit is from 2026). It is reproduced
here so that the permission notice accompanies this derivative work, as the MIT
licence requires.

```
MIT License

Copyright (c) 2026 Sanzar Rahman

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### What in this repository is derived from it

- `src/main/java/**` — the whole rendering pipeline, translated stage by stage.
- `src/test/resources/fixtures/**` — the Fixtures and their Expectations, which
  encode the reference engine's observed output.
- `docs/reference-tests.md` — an accounting of the reference engine's own tests.
- `docs/coverage-matrix.md` and `CONTEXT.md` — written for this port, but they
  describe behaviour derived from the reference.

## Dependencies

These are declared dependencies, resolved by the build. This library is not
shaded and redistributes none of their code; each remains under its own licence.

| Dependency | Licence |
| --- | --- |
| jsoup 1.18.3 | MIT |
| ph-css 7.0.1 (and ph-commons) | Apache-2.0 |
| Apache FontBox 3.0.3 | Apache-2.0 |
| Apache PDFBox 3.0.3 *(test scope only)* | Apache-2.0 |
| JUnit Jupiter 5.11.3 *(test scope only)* | EPL-2.0 |
| `java.desktop` (JDK) | GPL-2.0-with-classpath-exception, as part of the JDK |

## Fonts

No font file is in the published jar. The engine reads fonts installed on the
rendering host and embeds subsets of them into the PDFs it produces; whether a
given font may be embedded is governed by that font's own licence, which is the
caller's responsibility.

The *test corpus* does carry four faces, in
`src/test/resources/fixtures/features/fonts/`, so that no Fixture depends on a
font installed on the machine running the build:

| Font | Licence |
| --- | --- |
| DejaVu Serif, DejaVu Serif Bold, DejaVu Sans Mono, DejaVu Sans Mono Oblique — unmodified | Bitstream Vera, in `LICENSE.txt` beside them |

Copyright (c) 2003 by Bitstream, Inc. All Rights Reserved. Bitstream Vera is a
trademark of Bitstream, Inc. DejaVu changes are in the public domain.
