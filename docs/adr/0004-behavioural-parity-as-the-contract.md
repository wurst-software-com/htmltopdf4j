# Behavioural parity against the ported corpus, not byte parity

"Done" for the translation is defined by the reference engine's Fixtures and
Expectations, ported here and run under JUnit 5 — not by producing byte-identical
PDFs. Byte parity would forbid every library substitution the port depends on
while making no visible difference to output.

## Consequences

The Expectations are authoritative: a divergence is a port bug until someone
records otherwise. The reference repository is a hard fork at today's tree and is
not tracked upstream, so its Coverage matrix is copied here and maintained
independently. Chrome visual-diff scripts stay behind in the reference repo and
may be re-pointed at this build later.
