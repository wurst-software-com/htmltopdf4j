package com.wurstsoftware.htmltopdf4j.style;

import com.wurstsoftware.htmltopdf4j.paint.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One computed value per property for one element: the result of the Cascade.
 *
 * <p>Anything that can be resolved without knowing the containing block is
 * resolved here and stored as a number — font size above all, because every
 * {@code em} in the subtree depends on it. Anything that cannot, chiefly
 * percentages, stays a {@link Length} and is resolved during Layout.
 *
 * <p>Properties are read through named accessors rather than a map, so a typo in
 * a property name is a compile error somewhere in the engine instead of a
 * silently missing style.
 */
public final class ComputedStyle {

    /** The initial font size, and what a percentage or {@code em} font size scales from at the root. */
    public static final float INITIAL_FONT_SIZE = 12f;

    private final Map<String, String> declared;
    private final ComputedStyle parent;

    private final float fontSize;
    private final float rootFontSize;
    private final Display display;
    private final Color color;
    private final boolean rtl;

    ComputedStyle(Map<String, String> declared, ComputedStyle parent, float rootFontSize) {
        this.declared = Map.copyOf(declared);
        this.parent = parent;
        this.rootFontSize = rootFontSize;
        this.fontSize = computeFontSize(parent);
        this.display = Display.parse(value("display"), Display.INLINE);
        this.color = CssColor.parse(value("color"))
                .orElseGet(() -> parent != null ? parent.color() : Color.BLACK);
        this.rtl = computeDirection(parent);
    }

    /**
     * A style built straight from declarations, with no parent to inherit from.
     * Used for the things the Cascade does not reach — the {@code @page} rule
     * above all, which styles a Page rather than an element.
     */
    public static ComputedStyle of(Map<String, String> declared) {
        return new ComputedStyle(declared, null, INITIAL_FONT_SIZE);
    }

    /** The style an element with no declarations at all would compute to. */
    public static ComputedStyle initial() {
        return new ComputedStyle(Map.of(), null, INITIAL_FONT_SIZE);
    }

    private float computeFontSize(ComputedStyle parent) {
        float inherited = parent != null ? parent.fontSize : INITIAL_FONT_SIZE;
        String value = value("font-size");
        if (value == null) {
            return inherited;
        }
        Float keyword = absoluteFontSizeKeyword(value.trim().toLowerCase(Locale.ROOT), inherited);
        if (keyword != null) {
            return keyword;
        }
        // An em or a percentage font size is relative to the parent's, not to
        // this element's own — which is why font size is resolved before
        // anything else that might use it.
        return Length.parse(value)
                .map(length -> length.resolve(inherited, rootFontSize, inherited))
                .filter(size -> size > 0f)
                .orElse(inherited);
    }

    private static Float absoluteFontSizeKeyword(String value, float inherited) {
        return switch (value) {
            case "xx-small" -> INITIAL_FONT_SIZE * 0.6f;
            case "x-small" -> INITIAL_FONT_SIZE * 0.75f;
            case "small" -> INITIAL_FONT_SIZE * 0.89f;
            case "medium" -> INITIAL_FONT_SIZE;
            case "large" -> INITIAL_FONT_SIZE * 1.2f;
            case "x-large" -> INITIAL_FONT_SIZE * 1.5f;
            case "xx-large" -> INITIAL_FONT_SIZE * 2f;
            case "smaller" -> inherited / 1.2f;
            case "larger" -> inherited * 1.2f;
            default -> null;
        };
    }

    private boolean computeDirection(ComputedStyle parent) {
        String value = value("direction");
        if (value != null) {
            return value.trim().equalsIgnoreCase("rtl");
        }
        return parent != null && parent.rtl;
    }

    // --- Inherited and computed values -------------------------------------

    public float fontSize() {
        return fontSize;
    }

    public float rootFontSize() {
        return rootFontSize;
    }

    public Display display() {
        return display;
    }

    public Color color() {
        return color;
    }

    /** Whether this element's inline content reads right to left. */
    public boolean rtl() {
        return rtl;
    }

    public Optional<Color> backgroundColor() {
        return CssColor.parse(value("background-color"));
    }

    public TextAlign textAlign() {
        String value = value("text-align");
        TextAlign inherited = parent != null ? parent.textAlign() : (rtl ? TextAlign.RIGHT : TextAlign.LEFT);
        return TextAlign.parse(value, rtl, inherited);
    }

    /** The families to try, in order, before falling back to the default Face. */
    public List<String> fontFamily() {
        String value = value("font-family");
        if (value == null) {
            return parent != null ? parent.fontFamily() : List.of();
        }
        List<String> families = new ArrayList<>();
        for (String family : value.split(",")) {
            String name = family.trim().replaceAll("^['\"]|['\"]$", "").trim();
            if (!name.isEmpty()) {
                families.add(name);
            }
        }
        return List.copyOf(families);
    }

    public boolean bold() {
        String value = value("font-weight");
        if (value == null) {
            return parent != null && parent.bold();
        }
        String weight = value.trim().toLowerCase(Locale.ROOT);
        if (weight.equals("bold") || weight.equals("bolder")) {
            return true;
        }
        if (weight.equals("normal") || weight.equals("lighter")) {
            return false;
        }
        return Length.number(weight).map(number -> number >= 600f).orElse(false);
    }

    public boolean italic() {
        String value = value("font-style");
        if (value == null) {
            return parent != null && parent.italic();
        }
        String style = value.trim().toLowerCase(Locale.ROOT);
        return style.equals("italic") || style.equals("oblique");
    }

    public boolean underline() {
        return hasDecoration("underline");
    }

    public boolean lineThrough() {
        return hasDecoration("line-through");
    }

    private boolean hasDecoration(String keyword) {
        String value = value("text-decoration");
        if (value == null) {
            value = value("text-decoration-line");
        }
        if (value == null) {
            // Decoration is not an inherited property, but it does propagate to
            // descendants of the box that declared it, which amounts to the same
            // thing for the runs this engine paints.
            return parent != null && parent.hasDecoration(keyword);
        }
        String decoration = value.toLowerCase(Locale.ROOT);
        return decoration.contains(keyword) && !decoration.contains("none");
    }

    /** {@code line-height}, empty for {@code normal}. */
    public Optional<LineHeight> lineHeight() {
        String value = value("line-height");
        if (value == null) {
            return parent != null ? parent.lineHeight() : Optional.empty();
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        if (text.equals("normal")) {
            return Optional.empty();
        }
        // A unitless line-height is a multiplier, and it is the multiplier that
        // inherits, not the length it computes to on this element.
        Optional<Float> multiplier = Length.number(text);
        if (multiplier.isPresent()) {
            return Optional.of(new LineHeight.Multiplier(multiplier.get()));
        }
        return Length.parse(text).map(LineHeight.Absolute::new);
    }

    /** {@code line-height} as either a multiple of the font size or a length. */
    public sealed interface LineHeight {
        record Multiplier(float times) implements LineHeight {}

        record Absolute(Length length) implements LineHeight {}
    }

    // --- Generic property access -------------------------------------------

    /**
     * The properties that inherit, beyond the ones with named accessors above.
     *
     * <p>Inheritance is a property of the property, not of the element, so it
     * has to be listed somewhere. The box properties — margins, padding,
     * borders, width, position — are deliberately absent: they do not inherit,
     * and a box that silently took its parent's margin would be a hard bug to
     * see.
     */
    private static final java.util.Set<String> INHERITED = java.util.Set.of(
            "direction",
            "letter-spacing",
            "list-style-position",
            "list-style-type",
            "quotes",
            "text-indent",
            "text-transform",
            "text-align",
            "visibility",
            "white-space",
            "word-break",
            "word-spacing",
            "orphans",
            "widows");

    /**
     * The computed value of a property: what was declared, or what was inherited
     * when the property inherits and nothing was declared.
     *
     * <p>The explicit {@code inherit} and {@code initial} keywords are honoured
     * here rather than in each accessor, so they work for every property at once.
     */
    public String value(String property) {
        return substituteVariables(declaredOrInherited(property), 0);
    }

    /**
     * Replaces every {@code var(--name, fallback)} with the custom property it
     * names, or with its fallback when that property is not set.
     *
     * <p>Custom properties resolve against <em>this</em> element's chain, which
     * is what lets a subtree redefine {@code --brand} and have every descendant
     * that reads it change with it. Substitution is bounded, because
     * {@code --a: var(--b)} and {@code --b: var(--a)} is legal CSS to write.
     */
    private String substituteVariables(String value, int depth) {
        if (value == null || depth > MAX_VARIABLE_DEPTH || !value.contains("var(")) {
            return value;
        }
        StringBuilder resolved = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            int start = value.indexOf("var(", i);
            if (start < 0) {
                resolved.append(value, i, value.length());
                break;
            }
            resolved.append(value, i, start);
            int end = closingParenthesis(value, start + 4);
            if (end < 0) {
                resolved.append(value, start, value.length());
                break;
            }
            String reference = substituteVariables(resolve(value.substring(start + 4, end)), depth + 1);
            if (reference == null) {
                // A var() that resolves to nothing makes the whole declaration
                // invalid at computed-value time, as CSS requires.
                return null;
            }
            resolved.append(reference);
            i = end + 1;
        }
        return resolved.toString();
    }

    /** Looks up one {@code var()} argument list: the custom property, else its fallback. */
    private String resolve(String arguments) {
        int comma = arguments.indexOf(',');
        String name = (comma < 0 ? arguments : arguments.substring(0, comma)).trim();
        String fallback = comma < 0 ? null : arguments.substring(comma + 1).trim();
        String custom = declaredOrInherited(name);
        return custom != null ? custom : fallback;
    }

    private static int closingParenthesis(String value, int from) {
        int depth = 1;
        for (int i = from; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static final int MAX_VARIABLE_DEPTH = 16;

    private String declaredOrInherited(String property) {
        String declaredValue = declared.get(property);
        if (declaredValue != null) {
            String keyword = declaredValue.trim().toLowerCase(Locale.ROOT);
            if (keyword.equals("initial")) {
                return null;
            }
            if (!keyword.equals("inherit") && !keyword.equals("unset")) {
                return declaredValue;
            }
            // `unset` means `inherit` for an inherited property and `initial`
            // for every other one.
            if (keyword.equals("unset") && !inherits(property)) {
                return null;
            }
            return parent != null ? parent.declaredOrInherited(property) : null;
        }
        if (inherits(property) && parent != null) {
            return parent.declaredOrInherited(property);
        }
        return null;
    }

    /** Custom properties always inherit, which is what makes a themed subtree work. */
    private static boolean inherits(String property) {
        return property.startsWith("--") || INHERITED.contains(property);
    }

    public Optional<Length> length(String property) {
        return Length.parse(value(property));
    }

    /** A length property that may also be the keyword {@code auto}. */
    public boolean isAuto(String property) {
        return keyword(property, "auto");
    }

    public boolean has(String property) {
        return value(property) != null;
    }

    /** The computed value of a property, for the places a keyword is read directly. */
    public String raw(String property) {
        return value(property);
    }

    public String raw(String property, String fallback) {
        String value = value(property);
        return value != null ? value : fallback;
    }

    public boolean keyword(String property, String expected) {
        String value = value(property);
        return value != null && value.trim().equalsIgnoreCase(expected);
    }

    /** Resolves a length property against this element's font size. */
    public float resolve(Length length, float percentBasis) {
        return length.resolve(fontSize, rootFontSize, percentBasis);
    }

    public ComputedStyle parent() {
        return parent;
    }

    Map<String, String> declared() {
        return declared;
    }
}
