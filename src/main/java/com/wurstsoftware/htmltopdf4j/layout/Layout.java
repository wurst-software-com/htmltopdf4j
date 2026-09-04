package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.PageSize;
import com.wurstsoftware.htmltopdf4j.RenderOptions;
import com.wurstsoftware.htmltopdf4j.box.BlockBox;
import com.wurstsoftware.htmltopdf4j.box.BoxChild;
import com.wurstsoftware.htmltopdf4j.box.BoxTree;
import com.wurstsoftware.htmltopdf4j.box.ImageBox;
import com.wurstsoftware.htmltopdf4j.box.InlineRun;
import com.wurstsoftware.htmltopdf4j.box.LineBox;
import com.wurstsoftware.htmltopdf4j.box.TableBox;
import com.wurstsoftware.htmltopdf4j.image.DecodedImage;
import com.wurstsoftware.htmltopdf4j.image.ImageLoader;
import com.wurstsoftware.htmltopdf4j.paint.Color;
import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.paint.Rect;
import com.wurstsoftware.htmltopdf4j.paint.RoundedRect;
import com.wurstsoftware.htmltopdf4j.render.FaceRegistry;
import com.wurstsoftware.htmltopdf4j.render.RenderContext;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.style.CssColor;
import com.wurstsoftware.htmltopdf4j.style.Display;
import com.wurstsoftware.htmltopdf4j.style.Length;
import com.wurstsoftware.htmltopdf4j.style.LinearGradient;
import com.wurstsoftware.htmltopdf4j.style.Shorthands;
import com.wurstsoftware.htmltopdf4j.style.Stylesheet;
import com.wurstsoftware.htmltopdf4j.style.TextAlign;
import com.wurstsoftware.htmltopdf4j.text.FaceChain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Flowing the Box tree onto Pages.
 *
 * <p>Layout is a single downward pass. Blocks stack, each establishing a
 * containing block for its children; inline content is wrapped by
 * {@link LineBreaker} and emitted line by line; when a line will not fit in what
 * is left of the Page, a new Page begins. Nothing is laid out twice, which is
 * why a block's background is inserted at a remembered position in the Page's
 * command list once its height is finally known.
 *
 * <p>Coordinates here run downwards from the top of the Page, because that is
 * how a document reads. They are flipped into PDF's upward y at the moment a
 * Paint command is emitted, and nowhere else.
 */
public final class Layout {

    /** How far a block indents its list marker to the left of its content. */
    private static final float MARKER_GAP = 5f;

    private final RenderOptions options;
    private final PageSize pageSize;
    private final FaceRegistry faces;
    private final ImageLoader images;
    private final LineBreaker breaker;

    private final List<Page> pages = new ArrayList<>();
    private final List<String> links = new ArrayList<>();
    private final Map<String, Integer> linkIndex = new HashMap<>();

    private final Edges pageMargins;
    private final Map<String, List<com.wurstsoftware.htmltopdf4j.style.Declaration>> marginBoxes;
    private final float contentLeft;
    private final float contentWidth;
    private final float contentTop;
    private final float contentBottom;

    private int pageIndex;
    private float y;

    private Layout(RenderOptions options, Stylesheet stylesheet) {
        this.options = options;
        this.pageSize = options.pageSize();
        this.faces = new FaceRegistry(options.defaultFace());
        this.images = new ImageLoader(options.baseDirectory().orElse(null));
        this.breaker = new LineBreaker(faces::indexFor, faces::chain, this::measureAtomic);

        Edges margins = pageMargins(stylesheet, options);
        this.pageMargins = margins;
        this.marginBoxes = stylesheet.pageRules().stream()
                .map(Stylesheet.PageRule::marginBoxes)
                .reduce(new java.util.LinkedHashMap<>(), (all, boxes) -> {
                    all.putAll(boxes);
                    return all;
                });
        this.contentLeft = margins.left();
        this.contentWidth = Math.max(1f, pageSize.width() - margins.horizontal());
        this.contentTop = margins.top();
        this.contentBottom = Math.max(contentTop + 1f, pageSize.height() - margins.bottom());
        this.y = contentTop;
        pages.add(new Page());
    }

    /** Flows a Document's Box tree onto Pages. */
    public static LayoutResult layout(BoxTree tree, Stylesheet stylesheet, RenderOptions options) {
        Layout layout = new Layout(options, stylesheet);
        layout.layoutChildren(tree.children(), layout.contentLeft, layout.contentWidth);
        return layout.result();
    }

    private LayoutResult result() {
        List<Page> emitted = new ArrayList<>(pages);
        // A trailing empty Page is an artefact of a break that turned out to be
        // the end of the Document; a Document that is genuinely empty keeps one.
        while (emitted.size() > 1 && emitted.get(emitted.size() - 1).isEmpty()) {
            emitted.remove(emitted.size() - 1);
        }
        // Margin boxes are painted last because counter(pages) is the total Page
        // count, and nothing knows that until pagination has finished.
        MarginBoxes.paint(emitted, marginBoxes, pageSize, pageMargins, faces);
        return new LayoutResult(
                emitted, new RenderContext(pageSize, faces.chains(), links, images.images()));
    }

    /**
     * The Page margins: the {@code @page} rule's if the Document declares one,
     * otherwise the caller's.
     */
    private static Edges pageMargins(Stylesheet stylesheet, RenderOptions options) {
        Edges caller = new Edges(
                options.marginTop(), options.marginRight(), options.marginBottom(), options.marginLeft());
        for (Stylesheet.PageRule rule : stylesheet.pageRules()) {
            Map<String, String> declared = new HashMap<>();
            rule.declarations().forEach(declaration ->
                    Shorthands.expand(declaration.property(), declaration.value())
                            .forEach(declared::put));
            ComputedStyle style = ComputedStyle.of(declared);
            caller = new Edges(
                    margin(style, "margin-top", caller.top()),
                    margin(style, "margin-right", caller.right()),
                    margin(style, "margin-bottom", caller.bottom()),
                    margin(style, "margin-left", caller.left()));
        }
        return caller;
    }

    private static float margin(ComputedStyle style, String property, float fallback) {
        return style.length(property).map(length -> style.resolve(length, 0f)).orElse(fallback);
    }

    // --- Page management ----------------------------------------------------

    private Page page() {
        return pages.get(pageIndex);
    }

    private void newPage() {
        pageIndex++;
        if (pageIndex == pages.size()) {
            pages.add(new Page());
        }
        y = contentTop;
    }

    /** Starts a new Page when {@code height} will not fit in what is left of this one. */
    private void ensureSpace(float height) {
        if (y > contentTop && y + height > contentBottom + 0.01f) {
            newPage();
        }
    }

    /** Flips a downward layout coordinate into PDF's upward page space. */
    private float pdfY(float layoutY) {
        return pageSize.height() - layoutY;
    }

    // --- Block flow ---------------------------------------------------------

    private void layoutChildren(List<BoxChild> children, float left, float width) {
        float previousBottomMargin = 0f;
        for (BoxChild child : children) {
            switch (child) {
                case BlockBox block -> previousBottomMargin = layoutBlock(block, left, width, previousBottomMargin);
                case LineBox line -> {
                    y += previousBottomMargin;
                    previousBottomMargin = 0f;
                    layoutLine(line, left, width, TextAlign.LEFT, null);
                }
                case ImageBox image -> {
                    y += previousBottomMargin;
                    previousBottomMargin = 0f;
                    layoutBlockImage(image, left, width);
                }
                case TableBox table -> {
                    y += previousBottomMargin;
                    previousBottomMargin = 0f;
                    TableLayout.layout(this, table, left, width);
                }
            }
        }
        y += previousBottomMargin;
    }

    /**
     * Lays one block out and returns the bottom margin it leaves behind, so its
     * next sibling can collapse against it rather than adding to it — which is
     * what keeps consecutive paragraphs one margin apart instead of two.
     */
    private float layoutBlock(BlockBox block, float left, float width, float previousBottomMargin) {
        return layoutBlock(block, left, width, previousBottomMargin, null);
    }

    /**
     * @param forcedWidth the border-box width to use instead of the one the
     *     block's own style would give it, or {@code null}. A flex or grid item's
     *     width is decided by its container, not by itself.
     */
    private float layoutBlock(
            BlockBox block, float left, float width, float previousBottomMargin, Float forcedWidth) {

        ComputedStyle style = block.style();
        if (style.display() == Display.NONE) {
            return previousBottomMargin;
        }

        Edges margin = Edges.margin(style, width);
        Edges padding = Edges.padding(style, width);
        Edges border = Edges.borderWidths(style);

        y += Math.max(previousBottomMargin, margin.top());

        float outerWidth = forcedWidth != null
                ? Math.max(1f, forcedWidth)
                : usedWidth(style, width, margin, padding, border);
        float boxLeft = left + margin.left() + indent(style, width, outerWidth, margin);
        float contentX = boxLeft + border.left() + padding.left();
        float innerWidth = Math.max(1f, outerWidth - border.horizontal() - padding.horizontal());

        if (style.keyword("page-break-before", "always") || style.keyword("break-before", "page")) {
            if (y > contentTop) {
                newPage();
            }
        }

        int startPage = pageIndex;
        float startY = y;
        int startMark = page().mark();

        anchor(block);
        if (style.display() == Display.FLEX || style.display() == Display.INLINE_FLEX
                || style.display() == Display.GRID || style.display() == Display.INLINE_GRID) {
            y += border.top() + padding.top();
            if (style.display() == Display.FLEX || style.display() == Display.INLINE_FLEX) {
                FlexLayout.layout(this, block, contentX, innerWidth);
            } else {
                GridLayout.layout(this, block, contentX, innerWidth);
            }
            y += padding.bottom() + border.bottom();
            paintBoxDecoration(block, startPage, startY, startMark, boxLeft, outerWidth, border, padding);
            return margin.bottom();
        }
        float clipHeight = clippedHeight(style, border, padding);
        if (clipHeight > 0f) {
            page().add(new PaintCommand.PushClipRect(
                    new Rect(boxLeft, pdfY(startY + clipHeight), outerWidth, clipHeight)));
        }
        y += border.top() + padding.top();
        if (block.marker() != null) {
            paintMarker(block, contentX);
        }
        layoutChildren(block.children(), contentX, innerWidth);
        y += padding.bottom() + border.bottom();

        float minimumHeight = style.length("height")
                .map(height -> style.resolve(height, contentBottom - contentTop))
                .orElse(0f);
        if (pageIndex == startPage && y - startY < minimumHeight) {
            y = startY + minimumHeight;
        }
        if (clipHeight > 0f) {
            page().add(new PaintCommand.PopClip());
            if (pageIndex == startPage) {
                // Content taller than the clip is drawn and then clipped away,
                // but it must not push the following blocks down.
                y = startY + clipHeight;
            }
        }

        paintBoxDecoration(block, startPage, startY, startMark, boxLeft, outerWidth, border, padding);

        if (style.keyword("page-break-after", "always") || style.keyword("break-after", "page")) {
            newPage();
            return 0f;
        }
        return margin.bottom();
    }

    /**
     * The border-box height a block clips its content to, or zero when it does
     * not clip. {@code overflow: hidden} without a definite height clips
     * nothing, because there is nothing for the content to overflow.
     */
    private float clippedHeight(ComputedStyle style, Edges border, Edges padding) {
        String overflow = style.raw("overflow", "visible").trim().toLowerCase(java.util.Locale.ROOT);
        if (!overflow.equals("hidden") && !overflow.equals("clip")) {
            return 0f;
        }
        float page = contentBottom - contentTop;
        float declared = style.length("height")
                .or(() -> style.length("max-height"))
                .map(height -> style.resolve(height, page))
                .orElse(0f);
        if (declared <= 0f) {
            return 0f;
        }
        return style.keyword("box-sizing", "border-box")
                ? declared
                : declared + border.vertical() + padding.vertical();
    }

    /**
     * The used width of a block: its declared width when it has one, otherwise
     * the containing width less its own horizontal edges.
     */
    private static float usedWidth(
            ComputedStyle style, float containingWidth, Edges margin, Edges padding, Edges border) {

        float available = containingWidth - margin.horizontal();
        float declared = style.length("width")
                .map(width -> style.resolve(width, containingWidth))
                .orElse(Float.NaN);
        float used = Float.isNaN(declared) ? available : borderBoxWidth(style, declared, padding, border);

        used = clamp(used, style, "min-width", containingWidth, true);
        used = clamp(used, style, "max-width", containingWidth, false);
        return Math.max(1f, Math.min(used, available));
    }

    /** Turns a declared content width into the border-box width Layout works in. */
    private static float borderBoxWidth(ComputedStyle style, float declared, Edges padding, Edges border) {
        return style.keyword("box-sizing", "border-box")
                ? declared
                : declared + padding.horizontal() + border.horizontal();
    }

    private static float clamp(float used, ComputedStyle style, String property, float basis, boolean minimum) {
        Optional<Float> limit = style.length(property).map(length -> style.resolve(length, basis));
        if (limit.isEmpty()) {
            return used;
        }
        return minimum ? Math.max(used, limit.get()) : Math.min(used, limit.get());
    }

    /** {@code margin: auto} on both sides centres a block that is narrower than its container. */
    private static float indent(ComputedStyle style, float containingWidth, float outerWidth, Edges margin) {
        boolean centred = style.isAuto("margin-left") && style.isAuto("margin-right");
        return centred ? Math.max(0f, (containingWidth - margin.horizontal() - outerWidth) / 2f) : 0f;
    }

    // --- Painting a block's own decoration ----------------------------------

    /**
     * Paints a block's background and borders behind content that has already
     * been emitted, on every Page the block reached. A block split across a
     * break gets one painted segment per Page, which is what a browser does.
     */
    private void paintBoxDecoration(
            BlockBox block,
            int startPage,
            float startY,
            int startMark,
            float boxLeft,
            float outerWidth,
            Edges border,
            Edges padding) {

        Optional<Color> background = block.style().backgroundColor();
        Optional<LinearGradient> gradient =
                LinearGradient.parse(block.style().raw("background-image"));
        Optional<Integer> backgroundImage = backgroundImageOf(block.style());
        boolean hasBorder = border.vertical() + border.horizontal() > 0f;
        if (background.isEmpty() && gradient.isEmpty() && backgroundImage.isEmpty() && !hasBorder) {
            return;
        }
        float radius = block.style().length("border-top-left-radius")
                .map(length -> block.style().resolve(length, outerWidth))
                .orElse(0f);

        for (int index = startPage; index <= pageIndex; index++) {
            float top = index == startPage ? startY : contentTop;
            float bottom = index == pageIndex ? y : contentBottom;
            float height = bottom - top;
            if (height <= 0f) {
                continue;
            }
            Rect rect = new Rect(boxLeft, pdfY(bottom), outerWidth, height);
            List<PaintCommand> decoration = new ArrayList<>();
            background.ifPresent(color -> {
                decoration.add(new PaintCommand.SetFillColor(color));
                decoration.add(radius > 0f
                        ? new PaintCommand.FillRoundedRect(
                                new RoundedRect(rect.x(), rect.y(), rect.width(), rect.height(), radius))
                        : new PaintCommand.FillRect(rect));
            });
            gradient.ifPresent(value -> paintGradient(decoration, value, rect));
            backgroundImage.ifPresent(imageIndex -> decoration.add(
                    new PaintCommand.Image(imageIndex, rect.x(), rect.y(), rect.width(), rect.height())));
            if (hasBorder) {
                paintBorders(decoration, block.style(), rect, border, radius);
            }
            pages.get(index).insert(index == startPage ? startMark : 0, decoration);
        }
    }

    /** How wide, in points, one band of a gradient is allowed to be. */
    private static final float GRADIENT_BAND_WIDTH = 2f;

    private static final int MIN_GRADIENT_BANDS = 8;
    private static final int MAX_GRADIENT_BANDS = 256;

    /**
     * Paints a gradient as a run of solid bands across the box.
     *
     * <p>PDF has axial shadings, which would be exact, but they need a pattern
     * resource per gradient and a colour space switch in the content stream.
     * Bands need neither, and a band every couple of points is below what a
     * reader resolves at any sane zoom.
     */
    static void paintGradient(List<PaintCommand> out, LinearGradient gradient, Rect rect) {
        double radians = Math.toRadians(gradient.angleDegrees());
        // A CSS angle is clockwise from "to top", so the gradient line's
        // direction in PDF's y-up space is (sin, cos).
        boolean horizontal = Math.abs(Math.sin(radians)) >= Math.abs(Math.cos(radians));
        boolean reversed = horizontal ? Math.sin(radians) < 0 : Math.cos(radians) > 0;

        float span = horizontal ? rect.width() : rect.height();
        // One band every couple of points, so the seams fall below what a reader
        // resolves. A short box gets few bands and a wide one gets many, rather
        // than one count having to be right for both.
        int bands = Math.clamp(
                Math.round(span / GRADIENT_BAND_WIDTH), MIN_GRADIENT_BANDS, MAX_GRADIENT_BANDS);
        float step = span / bands;
        for (int i = 0; i < bands; i++) {
            float fraction = (i + 0.5f) / bands;
            out.add(new PaintCommand.SetFillColor(
                    gradient.colorAt(reversed ? 1f - fraction : fraction)));
            // Bands overlap by a hair so rounding never leaves a white seam.
            out.add(new PaintCommand.FillRect(horizontal
                    ? new Rect(rect.x() + i * step, rect.y(), step + 0.5f, rect.height())
                    : new Rect(rect.x(), rect.y() + i * step, rect.width(), step + 0.5f)));
        }
    }

    /** The {@code background-image: url(...)} of a block, decoded, if it has one. */
    private Optional<Integer> backgroundImageOf(ComputedStyle style) {
        String value = style.raw("background-image");
        if (value == null) {
            return Optional.empty();
        }
        int open = value.toLowerCase(java.util.Locale.ROOT).indexOf("url(");
        if (open < 0) {
            return Optional.empty();
        }
        int close = value.indexOf(')', open);
        if (close < 0) {
            return Optional.empty();
        }
        String source = value.substring(open + 4, close).trim().replaceAll("^['\"]|['\"]$", "");
        return images.resolve(source);
    }

    /**
     * Strokes each border side down the middle of its own width, which is where
     * PDF centres a stroke, so a declared width covers the space it consumed.
     */
    private static void paintBorders(
            List<PaintCommand> out, ComputedStyle style, Rect rect, Edges border, float radius) {

        if (radius > 0f && uniform(border) && uniformStyle(style)) {
            out.add(new PaintCommand.SetStrokeColor(borderColor(style, "top")));
            out.add(new PaintCommand.SetLineWidth(border.top()));
            out.add(new PaintCommand.StrokeRoundedRect(new RoundedRect(
                    rect.x() + border.top() / 2f,
                    rect.y() + border.top() / 2f,
                    rect.width() - border.top(),
                    rect.height() - border.top(),
                    radius)));
            return;
        }
        side(out, style, "top", border.top(), rect.x(), rect.top() - border.top() / 2f,
                rect.right(), rect.top() - border.top() / 2f);
        side(out, style, "bottom", border.bottom(), rect.x(), rect.y() + border.bottom() / 2f,
                rect.right(), rect.y() + border.bottom() / 2f);
        side(out, style, "left", border.left(), rect.x() + border.left() / 2f, rect.y(),
                rect.x() + border.left() / 2f, rect.top());
        side(out, style, "right", border.right(), rect.right() - border.right() / 2f, rect.y(),
                rect.right() - border.right() / 2f, rect.top());
    }

    private static void side(
            List<PaintCommand> out,
            ComputedStyle style,
            String name,
            float width,
            float x1,
            float y1,
            float x2,
            float y2) {

        if (width <= 0f) {
            return;
        }
        BorderStyle borderStyle = BorderStyle.of(style, name);
        out.add(new PaintCommand.SetStrokeColor(borderColor(style, name)));
        out.add(new PaintCommand.SetLineWidth(width));
        out.add(new PaintCommand.SetDash(borderStyle.dash(width)));
        out.add(new PaintCommand.StrokeLine(x1, y1, x2, y2));
        out.add(new PaintCommand.SetDash(null));
    }

    private static Color borderColor(ComputedStyle style, String side) {
        return CssColor.parse(style.raw("border-" + side + "-color")).orElse(style.color());
    }

    private static boolean uniform(Edges border) {
        return border.top() == border.right() && border.right() == border.bottom()
                && border.bottom() == border.left();
    }

    private static boolean uniformStyle(ComputedStyle style) {
        BorderStyle top = BorderStyle.of(style, "top");
        return top == BorderStyle.of(style, "right")
                && top == BorderStyle.of(style, "bottom")
                && top == BorderStyle.of(style, "left");
    }

    // --- Inline content -----------------------------------------------------

    void layoutLine(LineBox line, float left, float width, TextAlign inheritedAlign, String marker) {
        if (line.runs().isEmpty()) {
            return;
        }
        ComputedStyle style = line.runs().get(0).style();
        TextAlign align = style.textAlign() != null ? style.textAlign() : inheritedAlign;
        float indent = style.length("text-indent").map(length -> style.resolve(length, width)).orElse(0f);

        for (LineBreaker.VisualLine visual : breaker.breakLines(line.runs(), width, indent)) {
            ensureSpace(visual.height());
            float offset = LineBreaker.alignmentOffset(align, visual.width(), width);
            float baseline = y + visual.leading() / 2f + visual.ascent();
            for (LineBreaker.Fragment fragment : visual.fragments()) {
                emitFragment(fragment, left + offset, baseline);
            }
            y += visual.height();
        }
    }

    private void emitFragment(LineBreaker.Fragment fragment, float originX, float baseline) {
        float x = originX + fragment.x();
        switch (fragment) {
            case LineBreaker.TextFragment text -> emitText(text, x, baseline);
            case LineBreaker.AtomicFragment atomic -> emitAtomic(atomic, x, baseline);
        }
    }

    private void emitText(LineBreaker.TextFragment fragment, float x, float baseline) {
        if (fragment.text().isBlank()) {
            return;
        }
        ComputedStyle style = fragment.run().style();
        float size = fragment.size();
        page().add(new PaintCommand.SetFillColor(style.color()));
        page().add(new PaintCommand.Text(
                fragment.text(), x, pdfY(baseline), size, fragment.face(), style.bold(),
                style.length("letter-spacing").map(length -> style.resolve(length, 0f)).orElse(0f)));

        if (style.underline()) {
            decoration(x, fragment.width(), pdfY(baseline) - size * 0.1f, size * 0.06f, style.color());
        }
        if (style.lineThrough()) {
            decoration(x, fragment.width(), pdfY(baseline) + size * 0.25f, size * 0.06f, style.color());
        }
        linkArea(fragment.run(), x, pdfY(baseline) - size * 0.25f, fragment.width(), size * 1.2f);
    }

    private void decoration(float x, float width, float lineY, float thickness, Color color) {
        page().add(new PaintCommand.SetStrokeColor(color));
        page().add(new PaintCommand.SetLineWidth(Math.max(0.4f, thickness)));
        page().add(new PaintCommand.StrokeLine(x, lineY, x + width, lineY));
    }

    private void emitAtomic(LineBreaker.AtomicFragment fragment, float x, float baseline) {
        InlineRun run = fragment.run();
        float top = baseline - fragment.baseline();
        if (run.image() != null) {
            paintImage(run.image(), x, top, fragment.width(), fragment.height());
        } else if (run.inlineBlock() != null) {
            // An inline-block's content is laid out where the line put it, so its
            // own block flow resumes from this Page position.
            float savedY = y;
            y = top;
            layoutChildren(run.inlineBlock().children(), x, fragment.width());
            y = savedY;
        }
        linkArea(run, x, pdfY(baseline), fragment.width(), fragment.height());
    }

    private void linkArea(InlineRun run, float x, float pdfBottom, float width, float height) {
        if (run.link() == null || run.link().isBlank() || width <= 0f) {
            return;
        }
        page().addLink(new LinkArea(new Rect(x, pdfBottom, width, height), linkOf(run.link())));
    }

    private int linkOf(String target) {
        return linkIndex.computeIfAbsent(target, key -> {
            links.add(key);
            return links.size();
        });
    }

    private void anchor(BlockBox block) {
        if (block.anchor() != null) {
            page().addAnchor(AnchorMark.named(block.anchor(), pdfY(y)));
        }
        int level = headingLevel(block.tag());
        if (level > 0) {
            page().addAnchor(new AnchorMark(
                    block.anchor() != null ? block.anchor() : block.tag() + "-" + pageIndex + "-" + (int) y,
                    level,
                    headingText(block),
                    pdfY(y)));
        }
    }

    private static int headingLevel(String tag) {
        return tag.length() == 2 && tag.charAt(0) == 'h' && tag.charAt(1) >= '1' && tag.charAt(1) <= '6'
                ? tag.charAt(1) - '0'
                : 0;
    }

    private static String headingText(BlockBox block) {
        StringBuilder text = new StringBuilder();
        collectText(block.children(), text);
        return text.toString().trim();
    }

    private static void collectText(List<BoxChild> children, StringBuilder text) {
        for (BoxChild child : children) {
            switch (child) {
                case BlockBox block -> collectText(block.children(), text);
                case LineBox line -> line.runs().forEach(run -> {
                    if (run.isText()) {
                        text.append(run.text());
                    }
                });
                case ImageBox image -> text.append(image.alternativeText());
                case TableBox table -> { }
            }
        }
    }

    private void paintMarker(BlockBox block, float contentX) {
        ComputedStyle style = block.style();
        int face = faces.indexFor(style);
        float size = style.fontSize();
        float width = faces.chain(face).measure(block.marker(), size);
        float baseline = y + faces.chain(face).primary().lineAscentFraction() * size;
        page().add(new PaintCommand.SetFillColor(style.color()));
        page().add(new PaintCommand.Text(
                block.marker(), contentX - width - MARKER_GAP, pdfY(baseline), size, face, false, 0f));
    }

    // --- Images -------------------------------------------------------------

    private void layoutBlockImage(ImageBox box, float left, float width) {
        Optional<Integer> index = images.resolve(box.source());
        if (index.isEmpty()) {
            return;
        }
        float[] size = imageSize(box, index.get(), width);
        ensureSpace(size[1]);
        paintImage(box, left, y, size[0], size[1]);
        y += size[1];
    }

    private void paintImage(ImageBox box, float x, float top, float width, float height) {
        images.resolve(box.source()).ifPresent(index ->
                page().add(new PaintCommand.Image(index, x, pdfY(top + height), width, height)));
    }

    /**
     * The used size of an image: CSS wins over the presentational attributes,
     * which win over the intrinsic size, and a dimension given alone keeps the
     * aspect ratio.
     */
    private float[] imageSize(ImageBox box, int index, float availableWidth) {
        DecodedImage decoded = images.image(index);
        ComputedStyle style = box.style();
        float intrinsicWidth = decoded.width() * Length.POINTS_PER_PIXEL;
        float intrinsicHeight = decoded.height() * Length.POINTS_PER_PIXEL;

        Float width = style.length("width").map(length -> style.resolve(length, availableWidth))
                .orElseGet(() -> box.attributeWidth() == null
                        ? null
                        : box.attributeWidth() * Length.POINTS_PER_PIXEL);
        Float height = style.length("height").map(length -> style.resolve(length, 0f))
                .orElseGet(() -> box.attributeHeight() == null
                        ? null
                        : box.attributeHeight() * Length.POINTS_PER_PIXEL);

        if (width == null && height == null) {
            width = intrinsicWidth;
            height = intrinsicHeight;
        } else if (width == null) {
            width = height * (intrinsicWidth / Math.max(1f, intrinsicHeight));
        } else if (height == null) {
            height = width * (intrinsicHeight / Math.max(1f, intrinsicWidth));
        }
        float maximum = style.length("max-width").map(length -> style.resolve(length, availableWidth))
                .orElse(availableWidth);
        if (width > maximum) {
            height = height * (maximum / width);
            width = maximum;
        }
        return new float[] {width, height};
    }

    private LineBreaker.Size measureAtomic(InlineRun run, float availableWidth) {
        if (run.image() != null) {
            Optional<Integer> index = images.resolve(run.image().source());
            if (index.isEmpty()) {
                return new LineBreaker.Size(0f, 0f, 0f);
            }
            float[] size = imageSize(run.image(), index.get(), Math.max(1f, availableWidth));
            return new LineBreaker.Size(size[0], size[1], size[1]);
        }
        if (run.inlineBlock() != null) {
            float width = intrinsicWidth(run.inlineBlock(), availableWidth);
            float height = intrinsicHeight(run.inlineBlock(), width);
            return new LineBreaker.Size(width, height, height);
        }
        return new LineBreaker.Size(0f, 0f, 0f);
    }

    /** The width an inline-block wants: its declared width, or its content's, capped by what is left. */
    private float intrinsicWidth(BlockBox block, float availableWidth) {
        ComputedStyle style = block.style();
        float cap = Math.max(1f, Math.min(availableWidth, contentWidth));
        Optional<Float> declared = style.length("width").map(length -> style.resolve(length, contentWidth));
        if (declared.isPresent()) {
            return Math.min(declared.get(), cap);
        }
        float widest = 0f;
        for (BoxChild child : block.children()) {
            widest = Math.max(widest, switch (child) {
                case LineBox line -> breaker.unwrappedWidth(line.runs());
                case BlockBox nested -> intrinsicWidth(nested, cap);
                case ImageBox image -> images.resolve(image.source())
                        .map(index -> imageSize(image, index, cap)[0])
                        .orElse(0f);
                case TableBox table -> cap;
            });
        }
        Edges padding = Edges.padding(style, contentWidth);
        Edges border = Edges.borderWidths(style);
        return Math.min(cap, widest + padding.horizontal() + border.horizontal());
    }

    /** How tall an inline-block's content comes out at a known width. */
    private float intrinsicHeight(BlockBox block, float width) {
        ComputedStyle style = block.style();
        Optional<Float> declared = style.length("height").map(length -> style.resolve(length, 0f));
        Edges padding = Edges.padding(style, width);
        Edges border = Edges.borderWidths(style);
        float edges = padding.vertical() + border.vertical();
        if (declared.isPresent()) {
            return declared.get() + (style.keyword("box-sizing", "border-box") ? 0f : edges);
        }
        float height = 0f;
        for (BoxChild child : block.children()) {
            height += switch (child) {
                case LineBox line -> breaker.breakLines(line.runs(), Math.max(1f, width), 0f).stream()
                        .map(LineBreaker.VisualLine::height)
                        .reduce(0f, Float::sum);
                case BlockBox nested -> intrinsicHeight(nested, width)
                        + Edges.margin(nested.style(), width).vertical();
                case ImageBox image -> images.resolve(image.source())
                        .map(index -> imageSize(image, index, width)[1])
                        .orElse(0f);
                case TableBox table -> 0f;
            };
        }
        return height + edges;
    }

    // --- Access for the table layout ----------------------------------------

    float y() {
        return y;
    }

    void setY(float value) {
        y = value;
    }

    float contentTop() {
        return contentTop;
    }

    float contentBottom() {
        return contentBottom;
    }

    Page currentPage() {
        return page();
    }

    void breakPage() {
        newPage();
    }

    LineBreaker breaker() {
        return breaker;
    }

    FaceRegistry faces() {
        return faces;
    }

    float toPdfY(float layoutY) {
        return pdfY(layoutY);
    }

    void flowChildren(List<BoxChild> children, float left, float width) {
        layoutChildren(children, left, width);
    }

    /** Lays one block out at a width its container decided, as a flex or grid item does. */
    void flowItem(BlockBox item, float left, float containingWidth, Float itemWidth) {
        layoutBlock(item, left, containingWidth, 0f, itemWidth);
    }

    float measureChildren(BlockBox block, float width) {
        return intrinsicHeight(block, width);
    }

    float measureIntrinsicWidth(BlockBox block, float availableWidth) {
        return intrinsicWidth(block, availableWidth);
    }
}
