package com.wurstsoftware.htmltopdf4j.style;

import com.wurstsoftware.htmltopdf4j.paint.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A CSS {@code linear-gradient()}.
 *
 * <p>The angle follows the CSS convention, not the mathematical one: zero
 * degrees points to the top of the box and the angle increases clockwise, so
 * {@code to right} is 90 degrees.
 *
 * @param stops at least two colour stops, each with its position along the
 *     gradient line in {@code [0, 1]}, in ascending order
 */
public record LinearGradient(float angleDegrees, List<Stop> stops) {

    /** One colour stop: a colour, and where along the gradient line it sits. */
    public record Stop(Color color, float position) {}

    public LinearGradient {
        stops = List.copyOf(stops);
        if (stops.size() < 2) {
            throw new IllegalArgumentException("a gradient needs at least two stops");
        }
    }

    /** The colour at a fraction of the way along the gradient line. */
    public Color colorAt(float fraction) {
        float t = Math.clamp(fraction, 0f, 1f);
        for (int i = 1; i < stops.size(); i++) {
            Stop before = stops.get(i - 1);
            Stop after = stops.get(i);
            if (t <= after.position()) {
                float span = after.position() - before.position();
                float local = span <= 0f ? 0f : (t - before.position()) / span;
                return mix(before.color(), after.color(), local);
            }
        }
        return stops.get(stops.size() - 1).color();
    }

    private static Color mix(Color from, Color to, float t) {
        return new Color(
                from.r() + (to.r() - from.r()) * t,
                from.g() + (to.g() - from.g()) * t,
                from.b() + (to.b() - from.b()) * t);
    }

    /**
     * Parses a {@code linear-gradient(...)}, or empty when the value is not one
     * or names something this engine does not implement.
     */
    public static Optional<LinearGradient> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        int open = trimmed.toLowerCase(Locale.ROOT).indexOf("linear-gradient(");
        if (open < 0 || !trimmed.endsWith(")")) {
            return Optional.empty();
        }
        String arguments = trimmed.substring(open + "linear-gradient(".length(), trimmed.length() - 1);
        List<String> parts = splitTopLevel(arguments);
        if (parts.isEmpty()) {
            return Optional.empty();
        }

        float angle = 180f;
        int first = 0;
        Optional<Float> declared = angleOf(parts.get(0));
        if (declared.isPresent()) {
            angle = declared.get();
            first = 1;
        }

        List<Stop> stops = new ArrayList<>();
        List<String> pending = parts.subList(first, parts.size());
        for (int i = 0; i < pending.size(); i++) {
            Optional<Stop> stop = stopOf(pending.get(i), i, pending.size());
            if (stop.isEmpty()) {
                return Optional.empty();
            }
            stops.add(stop.get());
        }
        return stops.size() >= 2 ? Optional.of(new LinearGradient(angle, ascending(stops))) : Optional.empty();
    }

    /** The declared direction, as a CSS angle, or empty when the part is a colour stop. */
    private static Optional<Float> angleOf(String part) {
        String value = part.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith("deg")) {
            try {
                return Optional.of(Float.parseFloat(value.substring(0, value.length() - 3).trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        if (!value.startsWith("to ")) {
            return Optional.empty();
        }
        return Optional.of(switch (value.substring(3).trim().replaceAll("\\s+", " ")) {
            case "top" -> 0f;
            case "right" -> 90f;
            case "bottom" -> 180f;
            case "left" -> 270f;
            case "top right", "right top" -> 45f;
            case "bottom right", "right bottom" -> 135f;
            case "bottom left", "left bottom" -> 225f;
            case "top left", "left top" -> 315f;
            default -> 180f;
        });
    }

    /** A stop with no declared position is spread evenly between its neighbours. */
    private static Optional<Stop> stopOf(String part, int index, int count) {
        String trimmed = part.trim();
        int space = lastTopLevelSpace(trimmed);
        String colorText = space < 0 ? trimmed : trimmed.substring(0, space);
        String positionText = space < 0 ? null : trimmed.substring(space + 1).trim();

        Optional<Color> color = CssColor.parse(colorText.trim());
        if (color.isEmpty()) {
            return Optional.empty();
        }
        float position = count <= 1 ? 0f : (float) index / (count - 1);
        if (positionText != null && positionText.endsWith("%")) {
            try {
                position = Float.parseFloat(positionText.substring(0, positionText.length() - 1)) / 100f;
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.of(new Stop(color.get(), Math.clamp(position, 0f, 1f)));
    }

    /** Stops never move backwards, however the author wrote them. */
    private static List<Stop> ascending(List<Stop> stops) {
        List<Stop> fixed = new ArrayList<>(stops.size());
        float highest = 0f;
        for (Stop stop : stops) {
            highest = Math.max(highest, stop.position());
            fixed.add(new Stop(stop.color(), highest));
        }
        return fixed;
    }

    private static List<String> splitTopLevel(String value) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts.stream().map(String::trim).filter(part -> !part.isEmpty()).toList();
    }

    /** The space separating a colour from its position, ignoring spaces inside {@code rgb(...)}. */
    private static int lastTopLevelSpace(String value) {
        int depth = 0;
        int last = -1;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (Character.isWhitespace(c) && depth == 0) {
                last = i;
            }
        }
        return last;
    }
}
