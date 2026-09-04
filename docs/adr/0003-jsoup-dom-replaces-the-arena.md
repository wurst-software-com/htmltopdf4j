# jsoup supplies the DOM; the arena is not ported

The reference engine implements `html5ever`'s `TreeSink` to build a flat arena,
explicitly to avoid `Rc`/`RefCell` reference-counting costs. That rationale is
void on the JVM — a tracing collector makes an ordinary object graph free — so
porting the arena would import a Rust-ism with no remaining justification. We
parse with jsoup and walk its `Document` once into the Box tree.

## Consequences

Supersedes the reasoning of the reference engine's ADR 0002 and ADR 0009 for
this codebase; the pipeline stays DOM-based, only the representation changes.
jsoup's HTML5 conformance differs from `html5ever` in malformed-markup corners,
which Parity against the Expectations will surface if it ever matters.
