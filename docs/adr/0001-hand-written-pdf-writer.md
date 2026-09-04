# A hand-written PDF writer, not Apache PDFBox

The engine positions *shaped glyphs* — glyph ids with HarfBuzz-computed
advances — while PDFBox's text API draws from Strings and re-derives glyphs
itself, so every ligature, kern and reordered RTL run would have to be smuggled
past it through subclassed embedder internals or raw content-stream operators.
We write the PDF ourselves instead: object table, `FlateDecode` streams
(`java.util.zip.Deflater`), Identity-H Type0 CID fonts, `/Link` annotations and
a retain-glyph-id subsetter, using **FontBox** only to parse sfnt tables.

## Considered Options

- **Apache PDFBox 3** — deletes roughly 1.5k lines of writer and subsetter, but
  fights the glyph-level text path on every shaped run.
- **Hybrid** — PDFBox for pages, annotations and images; our own text and font
  code. Rejected: the large dependency remains while earning only the easy half.

## Consequences

Keeping glyph ids unrenumbered during subsetting is what lets the cmap, `/W`
widths, `/ToUnicode` CMap and `/CIDToGIDMap /Identity` stay valid against the
subset. A future move to PDFBox would have to reproduce that property.
