# Carry the fonts the Fixtures need

`features/font-family` and `features/font-face` asserted that Georgia, Arial and
Courier New appear in the PDF's font objects. They passed here because the MS
core fonts were installed into `~/.local/share/fonts` during the port, and would
have failed on any machine without them. The parity ledger was therefore empty
by installation, not by construction, which is a weaker promise than the one
[ADR 0004](0004-behavioural-parity-as-the-contract.md) makes.

Four DejaVu faces — Serif regular and bold, Sans Mono regular and oblique — now
live in `src/test/resources/fixtures/features/fonts/`, and both Fixtures declare
their families with `@font-face` and a `url()` that names them. They are
unmodified and redistributed under the Bitstream Vera licence, which the
directory carries; they are test resources and are not in the published jar.

## Alternatives

**Naming metric-compatible substitutes instead** (Liberation, DejaVu, by family
name) would only move the dependence: the substitutes are as absent from a bare
container as Georgia is. **Letting the two Fixtures skip** would put a hole in
Parity where a Fixture used to be, and a skipped Fixture reads as a green light.

## Consequences

Nothing in the corpus reads a font from outside the repository, so the ledger is
now empty by construction. The check is a run with the fonts hidden:

```
mvn test -DargLine="-Djava.awt.headless=true -Duser.home=/tmp/nofonts"
```

which needs the `argLine` in `pom.xml` overridden, since a configured one wins
over the property.

Resolving a family against a font the *host* has installed — `font-family:
Georgia` with no `@font-face` — is no longer covered by a Fixture. It is covered
by the unit tests around `FontLibrary` and `FaceRegistry`, which skip when the
host has nothing to find, and by the fallback chain every Fixture exercises
anyway. The `local()` form of an `@font-face` `src` moved the same way: it names
a face installed on the host by definition, so it cannot be asserted by a
Fixture that must not depend on one.

The corpus grew by about 1.3 MB. That is the price of the promise, and it is
paid once.
