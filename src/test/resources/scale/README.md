# The scale document

`reg-2-9-1.html` is 1.8 MB of PhpSpreadsheet output: 1,391 rows, 22,166 cells,
16 declared columns, landscape `@page`, collapsed borders and a per-cell style
class for almost every cell. It is carried over from the reference engine, where
it was the original benchmark — the document that first showed a browser-free
engine could render a real spreadsheet in tens of megabytes.

It is **not** a Fixture. Every feature it uses is already Full in the
[coverage matrix](../../../../docs/coverage-matrix.md) and covered by a Fixture
that isolates it, so it asserts nothing new about behaviour. What it does is
scale: the largest table in the Parity corpus is `edge-cases/long-table.html` at
60 rows, three orders of magnitude smaller, and nothing else in the suite would
notice a quadratic column-measurement pass, a per-cell cascade lookup that stopped
being cached, or a Display list that keeps every Page's Paint commands live at
once. Those are the failure modes a hand translation from Rust to the JVM is most
likely to introduce, and they are invisible on a 60-row table.

`ScaleTest` is tagged `scale` and excluded from `mvn test`, because a 1.8 MB
render does not belong in the loop a developer runs on every change. Run it with:

```
mvn test -Pscale
```

The heap cap in that profile (`-Xmx512m`) is part of the assertion: the engine
should hold one document, not the document and a copy of everything it has
painted so far.

## What it renders today

126 Pages, 1.9 MB, about five seconds. Every row survives to the last Page and
cost per row does not grow with the number of rows, which is what the test
asserts.

It renders **portrait**, and it should be landscape. The document asks through a
*named* Page — `@page page0 { … size: landscape }` selected by `page: page0` on
the table — and `@page` here contributes margins and margin boxes only: the
`size` declaration is parsed and never read. Sixteen columns declaring 1,879pt of
width are then crushed into a 595pt sheet, and cell text wraps mid-word. That is
[#34](https://github.com/wurst-software-com/htmltopdf4j/issues/34), which the
document found; this test asserts around it rather than pretending otherwise.

## Provenance

The document is a student advising list from a university registrar, exported by
PhpSpreadsheet, and it was anonymised before it reached the reference repository:
names are letter-scrambled, every email is `<student-id>@abc.com`, student ids
run sequentially from 1000055403, and the mobile-number column holds one constant
value repeated on all 1,391 rows. It is kept byte-for-byte as the reference
carries it, under the same MIT licence as the rest of the port — see
[THIRD-PARTY-NOTICES.md](../../../../THIRD-PARTY-NOTICES.md).
