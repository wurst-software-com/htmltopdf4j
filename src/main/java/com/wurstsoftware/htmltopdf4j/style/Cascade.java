package com.wurstsoftware.htmltopdf4j.style;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Path;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Selector;

/**
 * The resolution of every CSS declaration that could apply to an element down to
 * one computed value per property.
 *
 * <p>Selector matching is jsoup's: it already implements type, class, id,
 * universal, attribute, descendant, child, sibling and the structural
 * pseudo-classes against the very DOM being rendered, and reimplementing that
 * would be a second matcher to keep correct. What is not jsoup's, and is done
 * here, is the cascade itself — origin, {@code !important}, specificity and
 * source order — and inheritance.
 *
 * <p>Rules are matched once each, against the whole Document, rather than every
 * rule being tested against every element. A stylesheet has far fewer rules than
 * a Document has elements.
 */
public final class Cascade {

    /**
     * Cascade origins in ascending precedence. {@code !important} inverts the
     * order of the origins, which is why author-important sits above
     * author-normal but below UA-important: it exists so a UA can guarantee
     * things an author cannot override.
     */
    private enum Level {
        UA_NORMAL,
        AUTHOR_NORMAL,
        AUTHOR_IMPORTANT,
        UA_IMPORTANT
    }

    private static final Stylesheet USER_AGENT = loadUserAgentStylesheet();

    /** The two pseudo-elements this engine generates content for. */
    public enum Pseudo {
        BEFORE("::before"),
        AFTER("::after");

        private final String suffix;
        private final String legacySuffix;

        Pseudo(String suffix) {
            this.suffix = suffix;
            this.legacySuffix = suffix.substring(1);
        }

        /** The element part of a selector that targets this pseudo-element, or {@code null}. */
        String baseOf(String selector) {
            String trimmed = selector.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.endsWith(suffix)) {
                return trimmed.substring(0, trimmed.length() - suffix.length());
            }
            // `:before` is the CSS2 spelling, and documents in the wild still use it.
            if (lower.endsWith(legacySuffix)) {
                return trimmed.substring(0, trimmed.length() - legacySuffix.length());
            }
            return null;
        }
    }

    private final Map<Element, ComputedStyle> styles = new IdentityHashMap<>();
    private final Map<Pseudo, Map<Element, ComputedStyle>> pseudoStyles = new java.util.EnumMap<>(Pseudo.class);
    private final Stylesheet authorStyles;

    private Cascade(Stylesheet authorStyles) {
        this.authorStyles = authorStyles;
    }

    /**
     * Cascades {@code stylesheet} over {@code document} and computes a style for
     * every element.
     */
    public static Cascade apply(Document document, Stylesheet stylesheet) {
        Cascade cascade = new Cascade(stylesheet);
        Map<Element, Map<String, Winner>> declared = new IdentityHashMap<>();

        collect(document, USER_AGENT, Level.UA_NORMAL, Level.UA_IMPORTANT, declared);
        collect(document, stylesheet, Level.AUTHOR_NORMAL, Level.AUTHOR_IMPORTANT, declared);
        collectInlineStyles(document, declared);

        Map<Pseudo, Map<Element, Map<String, Winner>>> pseudoDeclared = new java.util.EnumMap<>(Pseudo.class);
        for (Pseudo pseudo : Pseudo.values()) {
            Map<Element, Map<String, Winner>> forPseudo = new IdentityHashMap<>();
            collectPseudo(document, USER_AGENT, Level.UA_NORMAL, Level.UA_IMPORTANT, pseudo, forPseudo);
            collectPseudo(document, stylesheet, Level.AUTHOR_NORMAL, Level.AUTHOR_IMPORTANT, pseudo, forPseudo);
            pseudoDeclared.put(pseudo, forPseudo);
        }

        cascade.compute(document, declared);
        cascade.computePseudo(pseudoDeclared);
        return cascade;
    }

    /**
     * The style of an element's {@code ::before} or {@code ::after}, or empty
     * when no rule generates one.
     */
    public java.util.Optional<ComputedStyle> pseudoStyleOf(Element element, Pseudo pseudo) {
        return java.util.Optional.ofNullable(pseudoStyles.get(pseudo)).map(map -> map.get(element));
    }

    /** The computed style of an element; the initial style for one never cascaded. */
    public ComputedStyle styleOf(Element element) {
        return styles.getOrDefault(element, ComputedStyle.initial());
    }

    public Stylesheet stylesheet() {
        return authorStyles;
    }

    /** The declaration currently winning one property on one element. */
    private record Winner(Level level, Specificity specificity, int order, String value) {

        boolean losesTo(Level level, Specificity specificity, int order) {
            int byLevel = this.level.compareTo(level);
            if (byLevel != 0) {
                return byLevel < 0;
            }
            int bySpecificity = this.specificity.compareTo(specificity);
            // Equal specificity: the later rule wins, which is what source order means.
            return bySpecificity != 0 ? bySpecificity < 0 : this.order <= order;
        }
    }

    private static void collect(
            Document document,
            Stylesheet stylesheet,
            Level normal,
            Level important,
            Map<Element, Map<String, Winner>> declared) {

        for (StyleRule rule : stylesheet.rules()) {
            List<Element> matches = match(document, rule.selector());
            if (matches.isEmpty()) {
                continue;
            }
            for (Declaration declaration : rule.declarations()) {
                Level level = declaration.important() ? important : normal;
                for (Map.Entry<String, String> longhand :
                        Shorthands.expand(declaration.property(), declaration.value()).entrySet()) {
                    for (Element element : matches) {
                        offer(declared, element, longhand.getKey(), longhand.getValue(),
                                level, rule.specificity(), rule.order());
                    }
                }
            }
        }
    }

    /**
     * Collects the rules that target a pseudo-element. jsoup cannot match
     * {@code ::before}, so the pseudo is stripped off and the element part is
     * matched instead — which is exactly what the pseudo means.
     */
    private static void collectPseudo(
            Document document,
            Stylesheet stylesheet,
            Level normal,
            Level important,
            Pseudo pseudo,
            Map<Element, Map<String, Winner>> declared) {

        for (StyleRule rule : stylesheet.rules()) {
            String base = pseudo.baseOf(rule.selector());
            if (base == null) {
                continue;
            }
            List<Element> matches = match(document, base.isEmpty() ? "*" : base);
            for (Declaration declaration : rule.declarations()) {
                Level level = declaration.important() ? important : normal;
                for (Map.Entry<String, String> longhand :
                        Shorthands.expand(declaration.property(), declaration.value()).entrySet()) {
                    for (Element element : matches) {
                        offer(declared, element, longhand.getKey(), longhand.getValue(),
                                level, rule.specificity(), rule.order());
                    }
                }
            }
        }
    }

    private static void collectInlineStyles(Document document, Map<Element, Map<String, Winner>> declared) {
        for (Element element : document.getAllElements()) {
            String style = element.attr("style");
            if (style.isBlank()) {
                continue;
            }
            for (Declaration declaration : Stylesheet.parseInline(style)) {
                Level level = declaration.important() ? Level.AUTHOR_IMPORTANT : Level.AUTHOR_NORMAL;
                for (Map.Entry<String, String> longhand :
                        Shorthands.expand(declaration.property(), declaration.value()).entrySet()) {
                    offer(declared, element, longhand.getKey(), longhand.getValue(),
                            level, Specificity.INLINE, Integer.MAX_VALUE);
                }
            }
        }
    }

    private static void offer(
            Map<Element, Map<String, Winner>> declared,
            Element element,
            String property,
            String value,
            Level level,
            Specificity specificity,
            int order) {

        Map<String, Winner> forElement = declared.computeIfAbsent(element, key -> new HashMap<>());
        Winner current = forElement.get(property);
        if (current == null || current.losesTo(level, specificity, order)) {
            forElement.put(property, new Winner(level, specificity, order, value));
        }
    }

    /**
     * A selector jsoup cannot parse matches nothing rather than failing the
     * render — the same thing a browser does with a selector it does not know.
     */
    private static List<Element> match(Document document, String selector) {
        try {
            return document.select(selector);
        } catch (Selector.SelectorParseException | IllegalArgumentException e) {
            return List.of();
        }
    }

    /**
     * Walks the tree top-down so each element's style can inherit from a parent
     * that is already computed — which font size, and therefore every
     * {@code em} beneath it, depends on.
     */
    private void compute(Document document, Map<Element, Map<String, Winner>> declared) {
        Element root = document.root();
        float rootFontSize = rootFontSize(document, declared);
        computeSubtree(root, null, declared, rootFontSize);
    }

    private void computeSubtree(
            Element element,
            ComputedStyle parent,
            Map<Element, Map<String, Winner>> declared,
            float rootFontSize) {

        ComputedStyle style = new ComputedStyle(valuesOf(element, declared), parent, rootFontSize);
        styles.put(element, style);
        for (Element child : element.children()) {
            computeSubtree(child, style, declared, rootFontSize);
        }
    }

    /**
     * Computes each pseudo-element's style with its originating element as the
     * parent, so a {@code ::before} inherits the colour and font of the thing it
     * is attached to.
     */
    private void computePseudo(Map<Pseudo, Map<Element, Map<String, Winner>>> declared) {
        declared.forEach((pseudo, elements) -> {
            Map<Element, ComputedStyle> computed = new IdentityHashMap<>();
            elements.forEach((element, winners) -> {
                ComputedStyle parent = styles.get(element);
                if (parent != null) {
                    computed.put(element, new ComputedStyle(
                            valuesOf(element, elements), parent, parent.rootFontSize()));
                }
            });
            pseudoStyles.put(pseudo, computed);
        });
    }

    /**
     * The {@code rem} basis: the {@code <html>} element's font size. It is
     * computed on its own first, because every other element's {@code rem}
     * lengths resolve against it.
     */
    private static float rootFontSize(Document document, Map<Element, Map<String, Winner>> declared) {
        Element html = document.selectFirst("html");
        if (html == null) {
            return ComputedStyle.INITIAL_FONT_SIZE;
        }
        return new ComputedStyle(valuesOf(html, declared), null, ComputedStyle.INITIAL_FONT_SIZE).fontSize();
    }

    private static Map<String, String> valuesOf(Element element, Map<Element, Map<String, Winner>> declared) {
        Map<String, Winner> winners = declared.get(element);
        if (winners == null) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>(winners.size());
        winners.forEach((property, winner) -> values.put(property, winner.value()));
        return values;
    }

    private static Stylesheet loadUserAgentStylesheet() {
        try (InputStream stream = Cascade.class.getResourceAsStream("ua.css")) {
            if (stream == null) {
                throw new IllegalStateException("the user-agent stylesheet is missing from the jar");
            }
            return Stylesheet.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the user-agent stylesheet", e);
        }
    }

    /** Every {@code <style>} block in the Document, in source order. */
    public static Stylesheet authorStylesheet(Document document) {
        return authorStylesheet(document, null);
    }

    /**
     * @param baseDirectory the directory a {@code <link rel=stylesheet>} is
     *     resolved against, or {@code null} to read no sheet from disk at all.
     *     Nothing outside it, and nothing remote, is read either way.
     */
    public static Stylesheet authorStylesheet(Document document, Path baseDirectory) {
        StringBuilder css = new StringBuilder();
        // Linked sheets and blocks are collected in one pass, in document order,
        // because that order is what decides between two rules of equal
        // specificity.
        for (Element element : document.select("style, link")) {
            String media = element.attr("media");
            if (!media.isBlank() && !appliesToPrint(media)) {
                continue;
            }
            if (element.normalName().equals("style")) {
                css.append(ExternalStyles.inline(element.data(), baseDirectory)).append('\n');
            } else if (isStylesheetLink(element)) {
                css.append(ExternalStyles.load(element.attr("href"), baseDirectory)).append('\n');
            }
        }
        // Numbering starts past the user-agent rules so an author rule of equal
        // specificity always sorts later than a UA one.
        return css.isEmpty() ? Stylesheet.EMPTY : Stylesheet.parse(css.toString(), 1_000_000);
    }

    private static boolean isStylesheetLink(Element link) {
        return java.util.Arrays.stream(link.attr("rel").trim().split("\\s+"))
                .anyMatch(token -> token.equalsIgnoreCase("stylesheet"));
    }

    static boolean appliesToPrint(String media) {
        String value = media.toLowerCase(java.util.Locale.ROOT);
        return value.contains("print") || value.contains("all");
    }

    /** Every element in the Document, in document order. */
    public List<Element> elements() {
        return new ArrayList<>(styles.keySet());
    }
}
