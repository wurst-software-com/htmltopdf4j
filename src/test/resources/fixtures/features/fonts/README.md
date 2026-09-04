# Fonts carried by the Fixture corpus

These four faces are here so that no Fixture depends on a font installed on the
machine running the build. Two Fixtures name them through `@font-face`:
`features/font-family` and `features/font-face`.

They are DejaVu, unmodified, redistributed under the Bitstream Vera licence in
`LICENSE.txt`. They are test resources: nothing in `src/main` reads them and
they are not in the published jar.

See [ADR 0006](../../../../../../docs/adr/0006-carry-the-fonts-the-fixtures-need.md).
