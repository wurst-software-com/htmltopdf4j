package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.PageSize;
import com.wurstsoftware.htmltopdf4j.RenderOptions;
import com.wurstsoftware.htmltopdf4j.box.BlockBox;
import com.wurstsoftware.htmltopdf4j.box.BoxChild;
import com.wurstsoftware.htmltopdf4j.box.BoxTree;
import com.wurstsoftware.htmltopdf4j.box.ImageBox;
import com.wurstsoftware.htmltopdf4j.box.InlineBox;
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

    private final Floats floats = new Floats();

    /**
     * Boxes taken out of flow by {@code position: absolute} or {@code fixed},
     * to be laid out after the in-flow content.
     *
     * <p>They are deferred rather than laid out where they are found because
     * they paint above everything in flow, and because a {@code fixed} box has
     * to be repeated on every Page — which needs the Page count.
     */
    private final List<Positioned> positioned = new ArrayList<>();

    /** One out-of-flow box, with where it was found and how it stacks. */
    private record Positioned(
            BlockBox block, boolean fixed, int page, float top, float left, float width, int zIndex) {}

    private int pageIndex;
    private float y;

    private Layout(RenderOptions options, Stylesheet stylesheet) {
        this.options = options;
        this.pageSize = options.pageSize();
        this.faces = new FaceRegistry(options.defaultFace());
        this.images = new ImageLoader(options.baseDirectory().orElse(null));
        this.breaker = new LineBreaker(faces::indexFor, faces::chain, this::measureAtomic);
        declareFontFaces(stylesheet, options.baseDirectory().orElse(null));

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

    /**
     * Registers the Faces an {@code @font-face} rule declares, so a Document can
     * ship its own font instead of hoping the machine has one.
     *
     * <p>Only local sources are read, for the same reason images are: a library
     * that quietly fetched fonts over the network while rendering would be a
     * surprising thing to embed in a server. A {@code src} this engine cannot
     * read leaves the family unresolved, and the next family in the Cascade's
     * list takes over.
     */
    private void declareFontFaces(Stylesheet stylesheet, java.nio.file.Path baseDirectory) {
        for (FontFaceRules rule : FontFaceRules.of(stylesheet)) {
            byte[] program = FontFaceSource.read(rule.source(), baseDirectory);
            if (program != null) {
                faces.declare(rule.family(), rule.bold(), rule.italic(), program);
            }
        }
    }

    /** Flows a Document's Box tree onto Pages. */
    public static LayoutResult layout(BoxTree tree, Stylesheet stylesheet, RenderOptions options) {
        Layout layout = new Layout(options, stylesheet);
        layout.layoutChildren(tree.children(), layout.contentLeft, layout.contentWidth);
        layout.layoutPositioned();
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
        floats.retain(pageIndex);
    }

    /** Starts a new Page when {@code height} will not fit in what is left of this one. */
    private void ensureSpace(float height) {
        if (y > contentTop && y + height > contentBottom + 0.01f) {
            newPage();
        }
    }

    /**
     * Starts a new Page when a box would be divided by the Page boundary and
     * would fit whole on a Page of its own.
     *
     * <p>The second half of that condition is what keeps {@code avoid} a hint: a
     * box taller than the content area has to break somewhere, and moving it to
     * a fresh Page first would only waste one.
     */
    void ensureWhole(float height) {
        if (y > contentTop
                && y + height > contentBottom + 0.01f
                && height <= contentBottom - contentTop) {
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

        String clear = style.raw("clear", "none").trim().toLowerCase(java.util.Locale.ROOT);
        if (!clear.equals("none")) {
            y = floats.clearance(pageIndex, y,
                    clear.equals("left") || clear.equals("both"),
                    clear.equals("right") || clear.equals("both"));
        }

        String position = style.raw("position", "static").trim().toLowerCase(java.util.Locale.ROOT);
        if (position.equals("absolute") || position.equals("fixed")) {
            positioned.add(new Positioned(
                    block, position.equals("fixed"), pageIndex, y, left, width, zIndexOf(style)));
            return previousBottomMargin;
        }

        String floated = style.raw("float", "none").trim().toLowerCase(java.util.Locale.ROOT);
        if (floated.equals("left") || floated.equals("right")) {
            layoutFloat(block, left, width, floated.equals("left"));
            return 0f;
        }

        float outerWidth = forcedWidth != null
                ? Math.max(1f, forcedWidth)
                : usedWidth(style, width, margin, padding, border);
        // `relative` shifts the box and everything in it without changing where
        // the flow below it goes, so the cursor is restored at the end.
        float relativeX = position.equals("relative") ? offset(style, "left", "right", width) : 0f;
        float relativeY = position.equals("relative")
                ? offset(style, "top", "bottom", contentBottom - contentTop)
                : 0f;
        float flowY = y;
        y += relativeY;
        float boxLeft = left + relativeX + margin.left() + indent(style, width, outerWidth, margin);
        float contentX = boxLeft + border.left() + padding.left();
        float innerWidth = Math.max(1f, outerWidth - border.horizontal() - padding.horizontal());

        if (style.keyword("page-break-before", "always") || style.keyword("break-before", "page")) {
            if (y > contentTop) {
                newPage();
            }
        }

        // The box's own margin is already in `y`, and its bottom margin would
        // collapse against the break, so the measured height is the border box.
        if (BreakInside.avoids(style)) {
            ensureWhole(intrinsicHeight(block, innerWidth));
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

        // `height` on a block that overflows it behaves as a minimum here, so
        // `min-height` is the same rule under a different name and the taller of
        // the two wins.
        float minimumHeight = Math.max(
                style.length("height")
                        .map(height -> style.resolve(height, contentBottom - contentTop))
                        .orElse(0f),
                style.length("min-height")
                        .map(height -> style.resolve(height, contentBottom - contentTop))
                        .orElse(0f));
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

        y -= relativeY;
        if (relativeY != 0f) {
            y = Math.max(y, flowY);
        }
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
     * Lays out the boxes taken out of flow, in stacking order, after everything
     * in flow — so a positioned box paints above the content it overlaps.
     */
    private void layoutPositioned() {
        List<Positioned> pending = new ArrayList<>(positioned);
        positioned.clear();
        pending.sort(java.util.Comparator.comparingInt(Positioned::zIndex));
        for (Positioned box : pending) {
            for (int page = 0; page < pages.size(); page++) {
                if (!box.fixed() && page != box.page()) {
                    continue;
                }
                // A fixed box repeats on every Page; an absolute one appears once.
                pageIndex = page;
                place(box);
            }
        }
        // Boxes positioned inside positioned boxes are laid out in the same way.
        if (!positioned.isEmpty()) {
            layoutPositioned();
        }
    }

    private void place(Positioned box) {
        ComputedStyle style = box.block().style();
        Edges margin = Edges.margin(style, box.width());
        Edges padding = Edges.padding(style, box.width());
        Edges border = Edges.borderWidths(style);

        float containingHeight = contentBottom - contentTop;
        float outerWidth = Math.clamp(
                style.length("width")
                        .map(declared -> borderBoxWidth(
                                style, style.resolve(declared, box.width()), padding, border))
                        .orElseGet(() -> Math.min(box.width(), intrinsicWidth(box.block(), box.width()))),
                1f,
                Math.max(1f, box.width()));
        float usedWidth = outerWidth;

        float boxLeft = style.length("left")
                .map(left -> contentLeft + style.resolve(left, contentWidth))
                .orElseGet(() -> style.length("right")
                        .map(right -> contentLeft + contentWidth - usedWidth - style.resolve(right, contentWidth))
                        .orElse(box.left()));
        float top = style.length("top")
                .map(value -> contentTop + style.resolve(value, containingHeight))
                .orElseGet(() -> style.length("bottom")
                        .map(value -> contentBottom - style.resolve(value, containingHeight)
                                - intrinsicHeight(box.block(), usedWidth))
                        .orElse(box.top()));

        int startMark = page().mark();
        y = top + border.top() + padding.top();
        layoutChildren(box.block().children(),
                boxLeft + border.left() + padding.left(),
                Math.max(1f, outerWidth - border.horizontal() - padding.horizontal()));
        y += padding.bottom() + border.bottom();
        paintBoxDecoration(box.block(), pageIndex, top, startMark, boxLeft, outerWidth, border, padding);
    }

    /** A box offset, taking the start side when it has one and the end side otherwise. */
    private static float offset(ComputedStyle style, String start, String end, float basis) {
        return style.length(start)
                .map(length -> style.resolve(length, basis))
                .orElseGet(() -> -style.length(end).map(length -> style.resolve(length, basis)).orElse(0f));
    }

    /** {@code z-index: auto} stacks as zero, which is what CSS says. */
    private static int zIndexOf(ComputedStyle style) {
        try {
            return Integer.parseInt(style.raw("z-index", "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Places a floated block at one edge of its containing block and records the
     * band it occupies, so the lines beside it are narrowed and a {@code clear}
     * drops below it.
     *
     * <p>A float with no declared width is shrink-to-fit: as wide as its content
     * wants, capped by the space available. The block flow cursor does not move,
     * which is what taking a box out of flow means.
     */
    private void layoutFloat(BlockBox block, float left, float width, boolean toLeft) {
        ComputedStyle style = block.style();
        Edges margin = Edges.margin(style, width);
        Edges padding = Edges.padding(style, width);
        Edges border = Edges.borderWidths(style);

        float available = width - margin.horizontal();
        float outerWidth = style.length("width")
                .map(declared -> borderBoxWidth(style, style.resolve(declared, width), padding, border))
                .orElseGet(() -> Math.min(available, intrinsicWidth(block, available)));
        outerWidth = Math.clamp(outerWidth, 1f, Math.max(1f, available));

        // A float is placed whole, so it is measured before anything is laid
        // out: that is what keeps the Page it started on and the Page it ends
        // on the same one, and the band and the cursor below honest.
        float boxHeight = intrinsicHeight(
                block, Math.max(1f, outerWidth - border.horizontal() - padding.horizontal()));
        ensureWhole(boxHeight);

        float top = y;
        float boxLeft = toLeft
                ? floats.leftEdge(pageIndex, top, 1f, left) + margin.left()
                : floats.rightEdge(pageIndex, top, 1f, left + width) - outerWidth - margin.right();

        int startPage = pageIndex;
        int startMark = page().mark();
        anchor(block);
        y += border.top() + padding.top();
        layoutChildren(block.children(),
                boxLeft + border.left() + padding.left(),
                Math.max(1f, outerWidth - border.horizontal() - padding.horizontal()));
        y += padding.bottom() + border.bottom();

        int endPage = pageIndex;
        float bottom = Math.max(y, top + boxHeight);
        // The band is the box's whole extent, but the box is painted only
        // inside the content area: a Page it merely overflows into is not a
        // Page it was laid out on, and has no room reserved for it.
        y = Math.min(bottom, contentBottom);
        paintBoxDecoration(block, startPage, top, startMark, boxLeft, outerWidth, border, padding);
        addBands(startPage, endPage, top, bottom + margin.bottom(),
                toLeft ? boxLeft + outerWidth + margin.right() : boxLeft - margin.left(), toLeft);
        // The flow the float was taken out of carries on where it was, on the
        // Page it was on, however many Pages the float itself reached.
        pageIndex = startPage;
        y = top;
    }

    /**
     * Reserves the float's exclusion on every Page its box crosses.
     *
     * <p>A float too tall for a Page of its own cannot be moved out of the way,
     * so it is divided; each of the Pages it reaches then has to narrow the
     * lines beside it, not only the Page it started on.
     */
    private void addBands(int startPage, int endPage, float top, float bottom, float edge, boolean toLeft) {
        for (int page = startPage; page < endPage; page++) {
            floats.add(page, page == startPage ? top : contentTop, contentBottom, edge, toLeft);
        }
        int page = endPage;
        float bandTop = endPage == startPage ? top : contentTop;
        float bandBottom = bottom;
        while (bandBottom > contentBottom) {
            floats.add(page, bandTop, contentBottom, edge, toLeft);
            bandBottom = contentTop + (bandBottom - contentBottom);
            bandTop = contentTop;
            page++;
        }
        floats.add(page, bandTop, bandBottom, edge, toLeft);
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
    static void paintBorders(
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

        // Lines are broken one at a time because the width available to each
        // depends on the floats beside it, and that changes down the block.
        List<InlineRun> remaining = line.runs();
        float lineIndent = indent;
        while (!remaining.isEmpty()) {
            float lineLeft = floats.leftEdge(pageIndex, y, style.fontSize(), left);
            float lineRight = floats.rightEdge(pageIndex, y, style.fontSize(), left + width);
            float lineWidth = Math.max(1f, lineRight - lineLeft);

            List<LineBreaker.VisualLine> broken = breaker.breakLines(remaining, lineWidth, lineIndent);
            if (broken.isEmpty()) {
                break;
            }
            LineBreaker.VisualLine visual = broken.get(0);
            ensureSpace(visual.height());
            float offset = LineBreaker.alignmentOffset(align, visual.width(), lineWidth);
            float baseline = y + visual.leading() / 2f + visual.ascent();
            // Justification widens the gaps between the pieces of a line rather
            // than moving the line, and the last line of a block keeps the ragged
            // edge it fell with — a stretched final line is the classic tell of a
            // renderer that justified one line too many.
            List<LineBreaker.Fragment> fragments = visual.fragments();
            List<Integer> gaps = align == TextAlign.JUSTIFY && broken.size() > 1
                    ? wordGaps(fragments)
                    : List.of();
            // The trailing space of the last word on the line is not drawn, so a
            // justified line that counted it would stop a space short of the
            // margin instead of squaring off against it.
            float drawn = visual.width() - breaker.trailingSpace(fragments.get(fragments.size() - 1));
            float slack = gaps.isEmpty() ? 0f : Math.max(0f, lineWidth - drawn);

            int widened = 0;
            float[] origins = new float[fragments.size()];
            for (int i = 0; i < fragments.size(); i++) {
                if (gaps.contains(i)) {
                    widened++;
                }
                float spread = gaps.isEmpty() ? 0f : slack * widened / gaps.size();
                origins[i] = lineLeft + offset + spread;
            }

            List<InlineRun> next = broken.size() == 1 ? List.of() : rest(broken);
            paintInlineBoxes(fragments, origins, baseline, lineWidth, next);
            for (int i = 0; i < fragments.size(); i++) {
                emitFragment(fragments.get(i), origins[i], baseline);
            }
            y += visual.height();
            lineIndent = 0f;
            remaining = next;
        }
    }

    /**
     * Paints the background and borders of the inline boxes this line is inside,
     * one rectangle per box per line, behind the text that is about to be drawn.
     *
     * <p>Outer boxes are painted before the boxes nested in them, so a chip
     * inside a highlighted sentence sits on top of the highlight.
     */
    private void paintInlineBoxes(
            List<LineBreaker.Fragment> fragments,
            float[] origins,
            float baseline,
            float containingWidth,
            List<InlineRun> next) {

        java.util.Set<Integer> continuing = new java.util.HashSet<>();
        for (InlineRun run : next) {
            run.inlines().forEach(box -> continuing.add(box.id()));
        }
        for (int depth = 0; depth < maxInlineDepth(fragments); depth++) {
            int start = -1;
            for (int i = 0; i <= fragments.size(); i++) {
                InlineBox box = i < fragments.size() ? inlineAt(fragments.get(i), depth) : null;
                InlineBox open = start < 0 ? null : inlineAt(fragments.get(start), depth);
                if (start >= 0 && !open.sameBoxAs(box)) {
                    paintInlineBox(open, fragments, origins, start, i - 1, baseline, containingWidth,
                            !continuing.contains(open.id()));
                    start = -1;
                }
                if (box != null && start < 0) {
                    start = i;
                }
            }
        }
    }

    private static int maxInlineDepth(List<LineBreaker.Fragment> fragments) {
        int depth = 0;
        for (LineBreaker.Fragment fragment : fragments) {
            depth = Math.max(depth, fragment.run().inlines().size());
        }
        return depth;
    }

    private static InlineBox inlineAt(LineBreaker.Fragment fragment, int depth) {
        List<InlineBox> inlines = fragment.run().inlines();
        return depth < inlines.size() ? inlines.get(depth) : null;
    }

    /** One inline box's rectangle on one line, from its first fragment to its last. */
    private void paintInlineBox(
            InlineBox box,
            List<LineBreaker.Fragment> fragments,
            float[] origins,
            int first,
            int last,
            float baseline,
            float containingWidth,
            boolean closesHere) {

        ComputedStyle style = box.style();
        Edges padding = Edges.padding(style, containingWidth);
        Edges border = Edges.borderWidths(style);
        float left = origins[first] + fragments.get(first).x()
                - (box.opensHere() ? padding.left() + border.left() : 0f);
        float right = origins[last] + fragments.get(last).x() + fragments.get(last).width()
                + (closesHere ? padding.right() + border.right() : 0f);

        // The box is as tall as the text it wraps, which is the same extent a
        // link area covers, grown by the box's own padding and border.
        float size = fragments.get(first).run().style().fontSize();
        float bottom = pdfY(baseline) - size * 0.25f - padding.bottom() - border.bottom();
        float height = size * 1.2f + padding.vertical() + border.vertical();
        Rect rect = new Rect(left, bottom, Math.max(0f, right - left), height);

        // A rounded corner belongs to a whole box: a box cut by a line break is
        // drawn square, because the corners the break made are not corners.
        float radius = box.opensHere() && closesHere
                ? style.length("border-top-left-radius")
                        .map(length -> style.resolve(length, rect.width()))
                        .orElse(0f)
                : 0f;
        Edges sides = new Edges(
                border.top(),
                closesHere ? border.right() : 0f,
                border.bottom(),
                box.opensHere() ? border.left() : 0f);

        List<PaintCommand> decoration = new ArrayList<>();
        style.backgroundColor().ifPresent(color -> {
            decoration.add(new PaintCommand.SetFillColor(color));
            decoration.add(radius > 0f
                    ? new PaintCommand.FillRoundedRect(
                            new RoundedRect(rect.x(), rect.y(), rect.width(), rect.height(), radius))
                    : new PaintCommand.FillRect(rect));
        });
        if (sides.horizontal() + sides.vertical() > 0f) {
            paintBorders(decoration, style, rect, sides, radius);
        }
        decoration.forEach(page()::add);
    }

    /**
     * The indices of the fragments that begin a word, so justification widens
     * the gaps between words and nothing else.
     *
     * <p>A fragment boundary is not always a word boundary: {@code un<b>break</b>able}
     * is three fragments of one word, and stretching between them would draw
     * "un break able".
     */
    private static List<Integer> wordGaps(List<LineBreaker.Fragment> fragments) {
        List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < fragments.size(); i++) {
            if (fragments.get(i - 1) instanceof LineBreaker.TextFragment text
                    && text.text().endsWith(" ")) {
                gaps.add(i);
            }
        }
        return gaps;
    }

    /**
     * The content of every line after the first, as runs to be broken again at
     * the next line's own width.
     */
    private static List<InlineRun> rest(List<LineBreaker.VisualLine> broken) {
        List<InlineRun> remaining = new ArrayList<>();
        // An inline box already open when a line ends is cut by the break, not
        // started by it, so it loses its left edge on the lines that follow.
        java.util.Set<Integer> already = idsOf(broken.get(0));
        for (int i = 1; i < broken.size(); i++) {
            for (LineBreaker.Fragment fragment : broken.get(i).fragments()) {
                InlineRun run = fragment.run();
                List<InlineBox> inlines = run.inlines().stream()
                        .map(box -> already.contains(box.id()) ? box.continued() : box)
                        .toList();
                remaining.add(switch (fragment) {
                    case LineBreaker.TextFragment text ->
                            InlineRun.text(text.text(), run.style(), run.link(), inlines);
                    case LineBreaker.AtomicFragment atomic -> atomic.run().image() != null
                            ? InlineRun.image(run.image(), run.style(), run.link(), inlines)
                            : InlineRun.inlineBlock(run.inlineBlock(), run.style(), run.link(), inlines);
                });
            }
            already.addAll(idsOf(broken.get(i)));
        }
        return remaining;
    }

    /** The inline boxes a visual line touches. */
    private static java.util.Set<Integer> idsOf(LineBreaker.VisualLine line) {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (LineBreaker.Fragment fragment : line.fragments()) {
            fragment.run().inlines().forEach(box -> ids.add(box.id()));
        }
        return ids;
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
                fragment.text(), x, pdfY(baseline), size, fragment.face(),
                faces.syntheticBold(fragment.face()),
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
        String floated = box.style().raw("float", "none").trim().toLowerCase(java.util.Locale.ROOT);
        if (floated.equals("left") || floated.equals("right")) {
            // A floated image is placed at its edge and the lines below narrow
            // around it; the flow cursor does not move.
            boolean toLeft = floated.equals("left");
            Edges margin = Edges.margin(box.style(), width);
            float imageLeft = toLeft
                    ? floats.leftEdge(pageIndex, y, 1f, left) + margin.left()
                    : floats.rightEdge(pageIndex, y, 1f, left + width) - size[0] - margin.right();
            paintImage(box, imageLeft, y, size[0], size[1]);
            floats.add(pageIndex, y, y + size[1] + margin.bottom(),
                    toLeft ? imageLeft + size[0] + margin.right() : imageLeft - margin.left(), toLeft);
            return;
        }
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
        Edges padding = Edges.padding(style, width);
        Edges border = Edges.borderWidths(style);
        float edges = padding.vertical() + border.vertical();
        // A declared height is a minimum, the same way it is in the block flow:
        // content that overruns it makes the box taller rather than spilling.
        float declared = Math.max(lengthOf(style, "height"), lengthOf(style, "min-height"));
        if (declared > 0f && !style.keyword("box-sizing", "border-box")) {
            declared += edges;
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
                case TableBox table -> TableLayout.measure(this, table, width);
            };
        }
        return Math.max(declared, height + edges);
    }

    /** A vertical length in points, or zero when it is not declared. */
    private float lengthOf(ComputedStyle style, String property) {
        return style.length(property)
                .map(length -> style.resolve(length, contentBottom - contentTop))
                .orElse(0f);
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
