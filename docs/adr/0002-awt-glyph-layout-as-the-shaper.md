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

## Spike outcome

The identity assumption is **verified**. On JDK 21, for faces created with
`Font.createFont` from the bytes we embed, `layoutGlyphVector` returns glyph
codes equal to the face's own glyph ids, and `Font.getNumGlyphs` agrees with the
`maxp` glyph count. Checked against FontBox's cmap across faces at 1000 and 2048
units per em. `GlyphIdentityTest` keeps the assumption under test, because it is
a property of the JDK rather than of our code — the fallback ladder in issue #1
stays relevant if a future JDK breaks it.

Two things the decision above got wrong, found by the same spike:

**Shaping is opt-in.** `layoutGlyphVector` applies neither kerning nor ligatures
unless the `Font` carries `TextAttribute.KERNING_ON` and `LIGATURES_ON`. Without
them the JDK returns a plain cmap mapping, which would have looked like working
shaping while silently losing every ligature and kern. `Face` derives a shaped
variant carrying both, and keeps the unshaped variant because the subsetter and
the metrics tests need glyph ids a cmap can be checked against — a ligature
glyph has none.

**Ligature clusters need reconstructing.** AWT reports one character index per
glyph, the first of its cluster, so a ligature glyph looks like it covers a
single character. The `/ToUnicode` CMap needs the whole cluster, which `Face`
recovers by ending each cluster where the next claimed character index begins.

Shaping is also linear in font size, so runs are shaped once at a canonical em
and scaled, and the per-Face Shaped run cache is keyed without size.

Variable fonts are excluded from the identity test: AWT exposes an interpolated
instance whose metrics need not match the file's default instance.
