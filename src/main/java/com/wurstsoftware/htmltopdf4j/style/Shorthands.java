package com.wurstsoftware.htmltopdf4j.style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Expands shorthand properties into the longhands the rest of the engine reads.
 *
 * <p>Expanding during the Cascade rather than when a value is read is what makes
 * precedence come out right: {@code border: 1px solid red} followed by a
 * higher-priority {@code border-color: blue} must keep the width and the style,
 * which only works if both have already become longhands competing per property.
 */
public final class Shorthands {

    private static final List<String> SIDES = List.of("top", "right", "bottom", "left");

    private Shorthands() {}

    /** Expands one declaration, returning the longhands it stands for. */
    public static Map<String, String> expand(String property, String value) {
        Map<String, String> longhands = new LinkedHashMap<>();
        switch (property) {
            case "margin", "padding" -> box(longhands, property, "", value);
            case "border-width" -> box(longhands, "border", "-width", value);
            case "border-color" -> box(longhands, "border", "-color", value);
            case "border-style" -> box(longhands, "border", "-style", value);
            case "border" -> border(longhands, SIDES, value);
            case "border-top", "border-right", "border-bottom", "border-left" ->
                    border(longhands, List.of(property.substring("border-".length())), value);
            case "border-radius" -> longhands.put("border-radius", firstToken(value));
            case "background" -> background(longhands, value);
            case "font" -> font(longhands, value);
            case "flex" -> flex(longhands, value);
            case "gap" -> gap(longhands, value);
            case "list-style" -> listStyle(longhands, value);
            case "text-decoration" -> longhands.put("text-decoration", value);
            case "grid-column" -> track(longhands, "grid-column", value);
            case "grid-row" -> track(longhands, "grid-row", value);
            case "grid-gap" -> gap(longhands, value);
            default -> longhands.put(property, value);
        }
        return longhands;
    }

    /**
     * The CSS one-to-four value pattern: one value for all sides, two for
     * vertical then horizontal, three for top, horizontal, bottom, four for each
     * side clockwise from the top.
     */
    private static void box(Map<String, String> out, String prefix, String suffix, String value) {
        List<String> parts = tokens(value);
        if (parts.isEmpty() || parts.size() > 4) {
            return;
        }
        String top = parts.get(0);
        String right = parts.size() > 1 ? parts.get(1) : top;
        String bottom = parts.size() > 2 ? parts.get(2) : top;
        String left = parts.size() > 3 ? parts.get(3) : right;

        out.put(prefix + "-top" + suffix, top);
        out.put(prefix + "-right" + suffix, right);
        out.put(prefix + "-bottom" + suffix, bottom);
        out.put(prefix + "-left" + suffix, left);
    }

    /** {@code border: <width> || <style> || <color>}, in any order, applied to the given sides. */
    private static void border(Map<String, String> out, List<String> sides, String value) {
        String width = null;
        String style = null;
        String color = null;

        for (String token : tokens(value)) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (isBorderStyle(lower)) {
                style = lower;
            } else if (Length.parse(token).isPresent() || isBorderWidthKeyword(lower)) {
                width = token;
            } else if (CssColor.parse(token).isPresent() || lower.equals("transparent")) {
                color = token;
            }
        }
        for (String side : sides) {
            if (width != null) {
                out.put("border-" + side + "-width", width);
            }
            if (style != null) {
                out.put("border-" + side + "-style", style);
            }
            if (color != null) {
                out.put("border-" + side + "-color", color);
            }
            // `border: none` and `border: 0` both mean no border, and a bare
            // style with no width still needs the UA's medium width to apply.
            if (style != null && width == null && !style.equals("none") && !style.equals("hidden")) {
                out.putIfAbsent("border-" + side + "-width", "3px");
            }
        }
    }

    private static boolean isBorderStyle(String token) {
        return switch (token) {
            case "none", "hidden", "solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset" ->
                    true;
            default -> false;
        };
    }

    private static boolean isBorderWidthKeyword(String token) {
        return token.equals("thin") || token.equals("medium") || token.equals("thick");
    }

    /**
     * {@code background} carries a colour, an image, and positioning. Only the
     * colour and the image are separated out; the rest travels as the original
     * value for the background painter to read.
     */
    private static void background(Map<String, String> out, String value) {
        String remaining = value;
        int url = indexOfFunction(remaining, "url(");
        int gradient = indexOfFunction(remaining, "linear-gradient(");
        if (url >= 0 || gradient >= 0) {
            out.put("background-image", value);
            return;
        }
        if (CssColor.parse(value.trim()).isPresent() || value.trim().equalsIgnoreCase("transparent")) {
            out.put("background-color", value.trim());
            return;
        }
        // Several values with no image: the last colour-looking token wins,
        // which is how the shorthand is used in practice.
        for (String token : tokens(value)) {
            if (CssColor.parse(token).isPresent()) {
                out.put("background-color", token);
            }
        }
    }

    /** {@code font: [style] [weight] size[/line-height] family}. */
    private static void font(Map<String, String> out, String value) {
        int slash = value.indexOf('/');
        List<String> parts = tokens(slash >= 0 ? value.substring(0, slash) : value);
        if (parts.isEmpty()) {
            return;
        }

        int sizeAt = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (Length.parse(parts.get(i)).isPresent() || isFontSizeKeyword(parts.get(i))) {
                sizeAt = i;
            }
        }
        if (sizeAt < 0) {
            return; // no size means it is not the font shorthand we can expand
        }

        for (int i = 0; i < sizeAt; i++) {
            String token = parts.get(i).toLowerCase(Locale.ROOT);
            if (token.equals("italic") || token.equals("oblique")) {
                out.put("font-style", token);
            } else if (token.equals("bold") || token.equals("bolder") || token.matches("[1-9]00")) {
                out.put("font-weight", token);
            } else if (token.equals("small-caps")) {
                out.put("font-variant", token);
            }
        }
        out.put("font-size", parts.get(sizeAt));

        if (slash >= 0) {
            List<String> after = tokens(value.substring(slash + 1));
            if (!after.isEmpty()) {
                out.put("line-height", after.get(0));
                if (after.size() > 1) {
                    out.put("font-family", String.join(" ", after.subList(1, after.size())));
                }
                return;
            }
        }
        if (sizeAt + 1 < parts.size()) {
            out.put("font-family", String.join(" ", parts.subList(sizeAt + 1, parts.size())));
        }
    }

    private static boolean isFontSizeKeyword(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "xx-small", "x-small", "small", "medium", "large", "x-large", "xx-large", "smaller", "larger" ->
                    true;
            default -> false;
        };
    }

    /** {@code flex: <grow> <shrink> <basis>}, with the CSS defaults for what is omitted. */
    private static void flex(Map<String, String> out, String value) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        switch (text) {
            case "none" -> {
                out.put("flex-grow", "0");
                out.put("flex-shrink", "0");
                out.put("flex-basis", "auto");
                return;
            }
            case "auto" -> {
                out.put("flex-grow", "1");
                out.put("flex-shrink", "1");
                out.put("flex-basis", "auto");
                return;
            }
            case "initial" -> {
                out.put("flex-grow", "0");
                out.put("flex-shrink", "1");
                out.put("flex-basis", "auto");
                return;
            }
            default -> {
                // Fall through to the numeric forms.
            }
        }

        List<String> parts = tokens(value);
        if (parts.isEmpty()) {
            return;
        }
        // A single number is the grow factor, and the basis becomes 0 rather
        // than auto — the one place the shorthand is not just the longhands.
        out.put("flex-grow", parts.get(0));
        out.put("flex-shrink", parts.size() > 1 && Length.number(parts.get(1)).isPresent() ? parts.get(1) : "1");
        String basis = parts.stream()
                .skip(1)
                .filter(part -> Length.parse(part).isPresent() || part.equalsIgnoreCase("auto"))
                .findFirst()
                .orElse("0");
        out.put("flex-basis", basis);
    }

    private static void gap(Map<String, String> out, String value) {
        List<String> parts = tokens(value);
        if (parts.isEmpty()) {
            return;
        }
        out.put("row-gap", parts.get(0));
        out.put("column-gap", parts.size() > 1 ? parts.get(1) : parts.get(0));
    }

    private static void listStyle(Map<String, String> out, String value) {
        for (String token : tokens(value)) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (lower.equals("inside") || lower.equals("outside")) {
                out.put("list-style-position", lower);
            } else if (lower.startsWith("url(")) {
                out.put("list-style-image", token);
            } else {
                out.put("list-style-type", lower);
            }
        }
    }

    /** {@code grid-column: <start> / <end>} and its row counterpart. */
    private static void track(Map<String, String> out, String property, String value) {
        int slash = value.indexOf('/');
        if (slash < 0) {
            out.put(property + "-start", value.trim());
            return;
        }
        out.put(property + "-start", value.substring(0, slash).trim());
        out.put(property + "-end", value.substring(slash + 1).trim());
    }

    private static String firstToken(String value) {
        List<String> parts = tokens(value);
        return parts.isEmpty() ? value.trim() : parts.get(0);
    }

    /** Splits on whitespace, keeping bracketed function arguments together. */
    static List<String> tokens(String value) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth = Math.max(0, depth - 1);
            }
            if (Character.isWhitespace(ch) && depth == 0) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static int indexOfFunction(String value, String name) {
        return value.toLowerCase(Locale.ROOT).indexOf(name);
    }
}
