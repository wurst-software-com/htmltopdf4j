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
wants exactly the rollback machinery this decision declines to build. If #19 is
fixed that way, this decision is worth revisiting.
