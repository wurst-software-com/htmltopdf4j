package com.wurstsoftware.htmltopdf4j.style;

import com.helger.css.ECSSVersion;
import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSFontFaceRule;
import com.helger.css.decl.CSSMediaQuery;
import com.helger.css.decl.CSSMediaRule;
import com.helger.css.decl.CSSPageRule;
import com.helger.css.decl.CSSSelector;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.reader.CSSReader;
import com.helger.css.reader.CSSReaderSettings;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The rules of a Document's {@code <style>} blocks, flattened into the order the
 * Cascade needs them in.
 *
 * <p>Media queries are evaluated here rather than during matching, because the
 * answer is fixed for a whole render: this is a printer. {@code print} and
 * {@code all} apply, {@code screen} does not, and a query with no media type —
 * a bare feature query such as {@code (min-width: 40em)} — applies, since the
 * page has a width and no better answer exists without evaluating features.
 */
public final class Stylesheet {

    private final List<StyleRule> rules;
    private final List<PageRule> pageRules;
    private final List<FontFaceRule> fontFaceRules;

    /**
     * An {@code @page} rule: its own declarations, for margins and orientation,
     * and its margin boxes, which carry the running text in the page furniture.
     *
     * @param marginBoxes keyed by the margin box name, such as {@code @top-center}
     */
    public record PageRule(
            String selector,
            List<Declaration> declarations,
            java.util.Map<String, List<Declaration>> marginBoxes) {}

    /** An {@code @font-face} rule's declarations, which shadow system Face lookup. */
    public record FontFaceRule(List<Declaration> declarations) {}

    private Stylesheet(List<StyleRule> rules, List<PageRule> pageRules, List<FontFaceRule> fontFaceRules) {
        this.rules = List.copyOf(rules);
        this.pageRules = List.copyOf(pageRules);
        this.fontFaceRules = List.copyOf(fontFaceRules);
    }

    public static final Stylesheet EMPTY = new Stylesheet(List.of(), List.of(), List.of());

    public List<StyleRule> rules() {
        return rules;
    }

    public List<PageRule> pageRules() {
        return pageRules;
    }

    public List<FontFaceRule> fontFaceRules() {
        return fontFaceRules;
    }

    /**
     * Parses CSS text.
     *
     * <p>Parsing is browser-compliant: a rule that will not parse is dropped and
     * the ones around it are kept, exactly as a browser recovers, and a
     * stylesheet that will not parse at all yields no rules rather than failing
     * the render.
     */
    public static Stylesheet parse(String css, int firstOrder) {
        CascadingStyleSheet parsed = CSSReader.readFromStringStream(
                css,
                new CSSReaderSettings()
                        .setCSSVersion(ECSSVersion.CSS30)
                        .setFallbackCharset(StandardCharsets.UTF_8)
                        .setBrowserCompliantMode(true));
        if (parsed == null) {
            return EMPTY;
        }

        List<StyleRule> rules = new ArrayList<>();
        List<PageRule> pageRules = new ArrayList<>();
        List<FontFaceRule> fontFaceRules = new ArrayList<>();
        int[] order = {firstOrder};

        for (ICSSTopLevelRule rule : parsed.getAllRules()) {
            collect(rule, rules, pageRules, fontFaceRules, order);
        }
        return new Stylesheet(rules, pageRules, fontFaceRules);
    }

    private static void collect(
            ICSSTopLevelRule rule,
            List<StyleRule> rules,
            List<PageRule> pageRules,
            List<FontFaceRule> fontFaceRules,
            int[] order) {

        switch (rule) {
            case CSSStyleRule styleRule -> {
                List<Declaration> declarations = declarations(styleRule.getAllDeclarations());
                if (declarations.isEmpty()) {
                    return;
                }
                for (CSSSelector selector : styleRule.getAllSelectors()) {
                    String text = selector.getAsCSSString().trim();
                    if (!text.isEmpty()) {
                        rules.add(new StyleRule(text, Specificity.of(text), order[0]++, declarations));
                    }
                }
            }
            case CSSMediaRule mediaRule -> {
                if (!appliesToPrint(mediaRule)) {
                    return;
                }
                for (ICSSTopLevelRule nested : mediaRule.getAllRules()) {
                    collect(nested, rules, pageRules, fontFaceRules, order);
                }
            }
            case CSSPageRule pageRule -> pageRules.add(pageRule(pageRule));
            case CSSFontFaceRule fontFace ->
                    fontFaceRules.add(new FontFaceRule(declarations(fontFace.getAllDeclarations())));
            default -> {
                // @import, @keyframes, @supports and friends: nothing this engine
                // renders depends on them, and ignoring one is better than
                // refusing the whole stylesheet.
            }
        }
    }

    /**
     * Splits an {@code @page} rule into its own declarations and its margin
     * boxes. ph-css models both as members of the rule, so they are told apart by
     * type rather than by position.
     */
    private static PageRule pageRule(CSSPageRule rule) {
        List<Declaration> declarations = new ArrayList<>();
        java.util.Map<String, List<Declaration>> marginBoxes = new java.util.LinkedHashMap<>();

        for (com.helger.css.decl.ICSSPageRuleMember member : rule.getAllMembers()) {
            switch (member) {
                case CSSDeclaration declaration -> declarations.addAll(declarations(List.of(declaration)));
                case com.helger.css.decl.CSSPageMarginBlock block ->
                        marginBoxes.put(
                                block.getPageMarginSymbol().toLowerCase(Locale.ROOT),
                                declarations(block.getAllDeclarations()));
                default -> {
                    // No other member kind carries anything the page furniture needs.
                }
            }
        }
        return new PageRule(rule.getAllSelectors().stream().findFirst().orElse(""), declarations, marginBoxes);
    }

    /** Whether any of a media rule's queries selects print. */
    private static boolean appliesToPrint(CSSMediaRule rule) {
        if (rule.getAllMediaQueries().isEmpty()) {
            return true;
        }
        for (CSSMediaQuery query : rule.getAllMediaQueries()) {
            String medium = query.getMedium();
            boolean matches = medium == null
                    || medium.isEmpty()
                    || medium.equalsIgnoreCase("print")
                    || medium.equalsIgnoreCase("all");
            if (query.isNot()) {
                matches = !matches;
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    static List<Declaration> declarations(Iterable<CSSDeclaration> source) {
        List<Declaration> declarations = new ArrayList<>();
        for (CSSDeclaration declaration : source) {
            String property = declaration.getProperty();
            if (property == null || property.isBlank()) {
                continue;
            }
            String value = declaration.getExpression().getAsCSSString();
            if (value == null) {
                continue;
            }
            declarations.add(new Declaration(
                    property.toLowerCase(Locale.ROOT), value.trim(), declaration.isImportant()));
        }
        return declarations;
    }

    /** Parses the declarations of an inline {@code style} attribute. */
    public static List<Declaration> parseInline(String style) {
        if (style == null || style.isBlank()) {
            return List.of();
        }
        // ph-css has no entry point for a bare declaration list, so wrap it in a
        // rule whose selector is irrelevant and take the declarations back out.
        Stylesheet wrapped = parse("*{" + style + "}", 0);
        return wrapped.rules().isEmpty() ? List.of() : wrapped.rules().get(0).declarations();
    }
}
