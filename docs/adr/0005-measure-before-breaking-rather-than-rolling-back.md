# Measure before breaking, rather than laying out and rolling back

An Unbreakable box has to be moved to the next Page when it would be divided,
and there are two ways to know that it would be: measure it before laying it out,
or lay it out, notice the Page changed, and move what was emitted. We measure
first, with `Layout.intrinsicHeight`, because the Display list has no way to
remove or move Paint commands once they are emitted — and giving it one would
mean rollback for link areas and anchors too.

## Consequences

The decision is only as good as the measurement, so `intrinsicHeight` had to
learn how tall a table is; it previously answered zero, which also silently
broke tables nested in grid items, table cells and inline-blocks. It still knows
nothing about floats, so an Unbreakable box whose height is driven by a float
measures short and breaks anyway. That fails open — you get the old behaviour,
not a corrupted Page — and it waits on the float pagination bug (#19), which
looked as though it wanted exactly the rollback machinery this decision declines
to build.

It did not. #19 was fixed by measuring the float and calling `ensureWhole`
before laying it out, the same shape as this decision: once a float is placed
whole, the Page it starts on and the Page it ends on are the same Page, and
both symptoms — a band filed against the wrong Page, a cursor restored to a
stale y — stop being reachable rather than being handled. This decision stands,
and the Display list still has no way to move a Paint command it has emitted.
