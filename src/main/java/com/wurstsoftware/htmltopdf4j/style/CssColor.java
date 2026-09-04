package com.wurstsoftware.htmltopdf4j.style;

import com.wurstsoftware.htmltopdf4j.paint.Color;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parses every CSS colour syntax the engine accepts: hex, {@code rgb()},
 * {@code rgba()}, {@code hsl()}, {@code hsla()}, the named colours, and
 * {@code transparent}.
 *
 * <p>Alpha is resolved here rather than carried into the Display list. A fully
 * transparent colour becomes {@link Optional#empty()}, which the box tree turns
 * into no Paint command at all; a partly transparent one is composited against
 * white, since the engine paints on an opaque page and PDF transparency groups
 * are not modelled.
 */
public final class CssColor {

    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("black", 0x000000),
            Map.entry("white", 0xFFFFFF),
            Map.entry("red", 0xFF0000),
            Map.entry("green", 0x008000),
            Map.entry("blue", 0x0000FF),
            Map.entry("yellow", 0xFFFF00),
            Map.entry("silver", 0xC0C0C0),
            Map.entry("gray", 0x808080),
            Map.entry("grey", 0x808080),
            Map.entry("maroon", 0x800000),
            Map.entry("olive", 0x808000),
            Map.entry("lime", 0x00FF00),
            Map.entry("aqua", 0x00FFFF),
            Map.entry("cyan", 0x00FFFF),
            Map.entry("teal", 0x008080),
            Map.entry("navy", 0x000080),
            Map.entry("fuchsia", 0xFF00FF),
            Map.entry("magenta", 0xFF00FF),
            Map.entry("purple", 0x800080),
            Map.entry("orange", 0xFFA500),
            Map.entry("pink", 0xFFC0CB),
            Map.entry("brown", 0xA52A2A),
            Map.entry("gold", 0xFFD700),
            Map.entry("lightgray", 0xD3D3D3),
            Map.entry("lightgrey", 0xD3D3D3),
            Map.entry("darkgray", 0xA9A9A9),
            Map.entry("darkgrey", 0xA9A9A9),
            Map.entry("whitesmoke", 0xF5F5F5),
            Map.entry("lightblue", 0xADD8E6),
            Map.entry("lightgreen", 0x90EE90));

    private CssColor() {}

    /**
     * @return the colour, or empty for {@code transparent} and for anything that
     *     is not a colour at all — an unparseable value must leave the inherited
     *     or initial colour in place rather than blacking the element out
     */
    public static Optional<Color> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.equals("transparent") || text.equals("none")) {
            return Optional.empty();
        }
        if (text.startsWith("#")) {
            return hex(text.substring(1));
        }
        if (text.startsWith("rgb")) {
            return rgbFunction(arguments(text));
        }
        if (text.startsWith("hsl")) {
            return hslFunction(arguments(text));
        }
        Integer named = NAMED.get(text);
        return named == null ? Optional.empty() : Optional.of(fromPacked(named));
    }

    private static Optional<Color> hex(String digits) {
        try {
            return switch (digits.length()) {
                    // #rgb and #rgba: each digit is doubled, so f becomes ff.
                case 3, 4 -> {
                    int r = Integer.parseInt(digits.substring(0, 1).repeat(2), 16);
                    int g = Integer.parseInt(digits.substring(1, 2).repeat(2), 16);
                    int b = Integer.parseInt(digits.substring(2, 3).repeat(2), 16);
                    float alpha = digits.length() == 4
                            ? Integer.parseInt(digits.substring(3, 4).repeat(2), 16) / 255f
                            : 1f;
                    yield composite(r, g, b, alpha);
                }
                case 6, 8 -> {
                    int r = Integer.parseInt(digits.substring(0, 2), 16);
                    int g = Integer.parseInt(digits.substring(2, 4), 16);
                    int b = Integer.parseInt(digits.substring(4, 6), 16);
                    float alpha =
                            digits.length() == 8 ? Integer.parseInt(digits.substring(6, 8), 16) / 255f : 1f;
                    yield composite(r, g, b, alpha);
                }
                default -> Optional.empty();
            };
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Color> rgbFunction(String[] parts) {
        if (parts.length < 3) {
            return Optional.empty();
        }
        try {
            int r = channel(parts[0]);
            int g = channel(parts[1]);
            int b = channel(parts[2]);
            return composite(r, g, b, parts.length > 3 ? alpha(parts[3]) : 1f);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Color> hslFunction(String[] parts) {
        if (parts.length < 3) {
            return Optional.empty();
        }
        try {
            float hue = Float.parseFloat(strip(parts[0], "deg"));
            float saturation = clamp01(Float.parseFloat(strip(parts[1], "%")) / 100f);
            float lightness = clamp01(Float.parseFloat(strip(parts[2], "%")) / 100f);
            Color rgb = fromHsl(hue, saturation, lightness);
            float alpha = parts.length > 3 ? alpha(parts[3]) : 1f;
            return composite(
                    Math.round(rgb.r() * 255), Math.round(rgb.g() * 255), Math.round(rgb.b() * 255), alpha);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Color fromHsl(float hueDegrees, float saturation, float lightness) {
        float hue = ((hueDegrees % 360f) + 360f) % 360f / 360f;
        if (saturation == 0f) {
            return new Color(lightness, lightness, lightness);
        }
        float q = lightness < 0.5f
                ? lightness * (1f + saturation)
                : lightness + saturation - lightness * saturation;
        float p = 2f * lightness - q;
        return new Color(hueToChannel(p, q, hue + 1f / 3f), hueToChannel(p, q, hue), hueToChannel(p, q, hue - 1f / 3f));
    }

    private static float hueToChannel(float p, float q, float t) {
        float hue = t < 0f ? t + 1f : t > 1f ? t - 1f : t;
        if (hue < 1f / 6f) {
            return p + (q - p) * 6f * hue;
        }
        if (hue < 1f / 2f) {
            return q;
        }
        if (hue < 2f / 3f) {
            return p + (q - p) * (2f / 3f - hue) * 6f;
        }
        return p;
    }

    /**
     * Flattens alpha against white. The engine paints onto an opaque sheet and
     * models no transparency groups, so this is the closest an opaque colour can
     * come; fully transparent drops the Paint command entirely.
     */
    private static Optional<Color> composite(int r, int g, int b, float alpha) {
        float a = clamp01(alpha);
        if (a <= 0f) {
            return Optional.empty();
        }
        if (a >= 1f) {
            return Optional.of(Color.fromRgb255(clampChannel(r), clampChannel(g), clampChannel(b)));
        }
        return Optional.of(new Color(
                blendOnWhite(r, a), blendOnWhite(g, a), blendOnWhite(b, a)));
    }

    private static float blendOnWhite(int channel, float alpha) {
        return clamp01(clampChannel(channel) / 255f * alpha + (1f - alpha));
    }

    private static int channel(String part) {
        String text = part.trim();
        if (text.endsWith("%")) {
            return Math.round(Float.parseFloat(text.substring(0, text.length() - 1)) * 255f / 100f);
        }
        return Math.round(Float.parseFloat(text));
    }

    private static float alpha(String part) {
        String text = part.trim();
        if (text.endsWith("%")) {
            return Float.parseFloat(text.substring(0, text.length() - 1)) / 100f;
        }
        return Float.parseFloat(text);
    }

    /** The arguments of a colour function, accepting both comma and CSS Color 4 space separators. */
    private static String[] arguments(String text) {
        int open = text.indexOf('(');
        int close = text.lastIndexOf(')');
        if (open < 0 || close < open) {
            return new String[0];
        }
        return text.substring(open + 1, close).replace('/', ',').split("[,\\s]+");
    }

    private static String strip(String value, String suffix) {
        String text = value.trim();
        return text.endsWith(suffix) ? text.substring(0, text.length() - suffix.length()).trim() : text;
    }

    private static Color fromPacked(int packed) {
        return Color.fromRgb255((packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF);
    }

    private static int clampChannel(int value) {
        return Math.clamp(value, 0, 255);
    }

    private static float clamp01(float value) {
        return Math.clamp(value, 0f, 1f);
    }
}
