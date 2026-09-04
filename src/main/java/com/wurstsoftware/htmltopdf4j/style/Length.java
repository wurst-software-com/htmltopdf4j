package com.wurstsoftware.htmltopdf4j.style;

import java.util.Locale;
import java.util.Optional;

/**
 * A CSS length, kept unresolved until the things it depends on are known.
 *
 * <p>A percentage cannot be turned into points before its containing block
 * exists, and {@code em} cannot before the element's font size does, so lengths
 * travel through the Cascade as values and are resolved during Layout.
 */
public record Length(float value, Unit unit) {

    /** CSS reckons 96 pixels to the inch; PDF reckons 72 points to it. */
    private static final float POINTS_PER_PIXEL = 72f / 96f;

    public static final Length ZERO = new Length(0f, Unit.POINT);

    public enum Unit {
        POINT(1f),
        PIXEL(POINTS_PER_PIXEL),
        INCH(72f),
        CENTIMETRE(72f / 2.54f),
        MILLIMETRE(72f / 25.4f),
        PICA(12f),
        /** Relative to the element's own font size. */
        EM(Float.NaN),
        /** Relative to the root element's font size. */
        REM(Float.NaN),
        /** Relative to the font's x-height, approximated as half the em. */
        EX(Float.NaN),
        /** Relative to a basis the caller supplies — usually the containing block's width. */
        PERCENT(Float.NaN);

        private final float points;

        Unit(float points) {
            this.points = points;
        }

        boolean isAbsolute() {
            return !Float.isNaN(points);
        }
    }

    /** Whether this length needs a containing block before it means anything. */
    public boolean isRelativeToContainer() {
        return unit == Unit.PERCENT;
    }

    /**
     * Resolves to points.
     *
     * @param emSize the element's font size in points
     * @param rootEmSize the root element's font size in points
     * @param percentBasis what a percentage is a percentage of, in points
     */
    public float resolve(float emSize, float rootEmSize, float percentBasis) {
        return switch (unit) {
            case EM -> value * emSize;
            case REM -> value * rootEmSize;
            // No face is consulted for ex: the ratio varies by a few percent
            // between faces and half an em is the conventional stand-in.
            case EX -> value * emSize * 0.5f;
            case PERCENT -> value / 100f * percentBasis;
            default -> value * unit.points;
        };
    }

    /** Resolves a length that is known not to be relative. */
    public float resolveAbsolute() {
        if (!unit.isAbsolute()) {
            throw new IllegalStateException(this + " needs a font size or a containing block to resolve");
        }
        return value * unit.points;
    }

    /**
     * Parses a CSS length. A bare {@code 0} needs no unit; anything else without
     * one is not a length, so it is rejected rather than guessed at.
     */
    public static Optional<Length> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.startsWith("calc(")) {
            return Calc.evaluate(value);
        }

        for (var candidate : SUFFIXES) {
            if (value.endsWith(candidate.suffix())) {
                return number(value.substring(0, value.length() - candidate.suffix().length()))
                        .map(number -> new Length(number, candidate.unit()));
            }
        }
        // A unitless zero is a valid length; any other unitless number is not.
        return number(value).filter(number -> number == 0f).map(number -> ZERO);
    }

    private record Suffix(String suffix, Unit unit) {}

    /** Longest first, so {@code rem} is not mistaken for {@code em}. */
    private static final Suffix[] SUFFIXES = {
        new Suffix("rem", Unit.REM),
        new Suffix("px", Unit.PIXEL),
        new Suffix("pt", Unit.POINT),
        new Suffix("em", Unit.EM),
        new Suffix("ex", Unit.EX),
        new Suffix("in", Unit.INCH),
        new Suffix("cm", Unit.CENTIMETRE),
        new Suffix("mm", Unit.MILLIMETRE),
        new Suffix("pc", Unit.PICA),
        new Suffix("%", Unit.PERCENT)
    };

    static Optional<Float> number(String text) {
        try {
            return Optional.of(Float.parseFloat(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return value + unit.name().toLowerCase(Locale.ROOT);
    }
}
