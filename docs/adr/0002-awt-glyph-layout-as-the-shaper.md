# `java.awt.Font.layoutGlyphVector` is the text shaper

Java has no pure-Java HarfBuzz, but the JDK's own layout engine has *been*
HarfBuzz since JDK 9, so `Font.layoutGlyphVector` gives us the same shaping the
Rust engine gets from `rustybuzz`: glyph ids, positions, and the glyph-to-char
mapping the `/ToUnicode` CMap needs. Faces must therefore always be created with
`Font.createFont` from the exact bytes we embed — logical fonts return glyph
codes that are not the embedded font's glyph ids.

## Considered Options

- **FontBox GSUB** — script-limited substitution, not a general shaper.
- **No shaping** (cmap + `hmtx`, optional `kern`) — loses ligatures entirely and
  renders Arabic in isolated forms.
- **Foreign Function & Memory binding to system HarfBuzz** — still preview in
  Java 21; would force `--enable-preview` on every consumer.

## Consequences

`java.desktop` becomes a hard module requirement (also needed for `ImageIO`).
The identity between AWT glyph codes and embedded glyph ids is an assumption the
whole font strategy rests on, and is verified by a spike before layout work
begins.
