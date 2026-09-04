package com.wurstsoftware.htmltopdf4j.style;

import java.util.Optional;

/**
 * Evaluates the {@code calc()} expressions that appear in real stylesheets.
 *
 * <p>Only the shape that actually occurs is handled: a chain of additions and
 * subtractions between one relative term and absolute ones, such as
 * {@code calc(100% - 2rem)}. Multiplication and division by a plain number are
 * folded into whichever term they attach to. A relative term is kept relative —
 * the result is a {@link Length} whose unit is that of the single relative
 * operand, with the absolute terms converted into it at resolution time.
 *
 * <p>Anything more involved — two different relative units in one expression,
 * nested {@code calc()}, {@code min()} or {@code max()} — is not a length this
 * can produce, and yields empty so the declaration is dropped rather than
 * silently mis-sized.
 */
final class Calc {

    private Calc() {}

    static Optional<Length> evaluate(String expression) {
        String body = expression.substring("calc(".length());
        if (!body.endsWith(")")) {
            return Optional.empty();
        }
        body = body.substring(0, body.length() - 1).trim();
        if (body.contains("(")) {
            return Optional.empty(); // nested functions are out of scope
        }

        // Split on + and - that stand alone; CSS requires them to be surrounded
        // by whitespace precisely so they cannot be confused with a sign.
        String[] terms = body.split("(?<=\\s)(?=[+-]\\s)");
        float absolutePoints = 0f;
        Float relativeValue = null;
        Length.Unit relativeUnit = null;

        for (String rawTerm : terms) {
            String term = rawTerm.trim();
            if (term.isEmpty()) {
                continue;
            }
            float sign = 1f;
            if (term.startsWith("+") || term.startsWith("-")) {
                sign = term.startsWith("-") ? -1f : 1f;
                term = term.substring(1).trim();
            }

            Scaled scaled = scale(term);
            if (scaled == null) {
                return Optional.empty();
            }
            Optional<Length> parsed = Length.parse(scaled.term());
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            Length length = parsed.get();
            float magnitude = sign * scaled.factor() * length.value();

            if (length.unit().isAbsolute()) {
                absolutePoints += new Length(magnitude, length.unit()).resolveAbsolute();
            } else if (relativeUnit == null) {
                relativeUnit = length.unit();
                relativeValue = magnitude;
            } else if (relativeUnit == length.unit()) {
                relativeValue += magnitude;
            } else {
                return Optional.empty(); // two different relative units
            }
        }

        if (relativeUnit == null) {
            return Optional.of(new Length(absolutePoints, Length.Unit.POINT));
        }
        // A mixed expression cannot be one Length. Percentages dominate in
        // practice, so keep the relative part and drop nothing silently: a
        // non-zero absolute remainder means we cannot represent the value.
        return absolutePoints == 0f
                ? Optional.of(new Length(relativeValue, relativeUnit))
                : Optional.empty();
    }

    private record Scaled(String term, float factor) {}

    /** Folds a trailing {@code * n} or {@code / n} into a multiplier on the term. */
    private static Scaled scale(String term) {
        int star = term.indexOf('*');
        int slash = term.indexOf('/');
        if (star < 0 && slash < 0) {
            return new Scaled(term, 1f);
        }
        boolean divide = star < 0 || (slash >= 0 && slash < star);
        int at = divide ? slash : star;
        Optional<Float> factor = Length.number(term.substring(at + 1));
        if (factor.isEmpty() || (divide && factor.get() == 0f)) {
            return null;
        }
        return new Scaled(term.substring(0, at).trim(), divide ? 1f / factor.get() : factor.get());
    }
}
