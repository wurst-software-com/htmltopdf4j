package com.wurstsoftware.htmltopdf4j.pdf;

import static com.wurstsoftware.htmltopdf4j.pdf.PdfSyntax.coord;
import static com.wurstsoftware.htmltopdf4j.pdf.PdfSyntax.number;

import com.wurstsoftware.htmltopdf4j.layout.Page;
import com.wurstsoftware.htmltopdf4j.paint.Color;
import com.wurstsoftware.htmltopdf4j.paint.DashPattern;
import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.paint.Rect;
import com.wurstsoftware.htmltopdf4j.paint.RoundedRect;
import com.wurstsoftware.htmltopdf4j.render.RenderContext;
import com.wurstsoftware.htmltopdf4j.text.Direction;
import com.wurstsoftware.htmltopdf4j.text.EmbeddedFace;
import com.wurstsoftware.htmltopdf4j.text.Face;
import com.wurstsoftware.htmltopdf4j.text.FaceChain;
import com.wurstsoftware.htmltopdf4j.text.Glyph;
import com.wurstsoftware.htmltopdf4j.text.ShapingFeatures;
import com.wurstsoftware.htmltopdf4j.text.Standard14Face;
import com.wurstsoftware.htmltopdf4j.text.WinAnsiEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Turns one Page's Display list into a PDF content stream.
 *
 * <p>The switch over {@link PaintCommand} is exhaustive by construction: the
 * interface is sealed, so a command the writer has not been taught to draw is a
 * compile error rather than a primitive that silently disappears from the
 * output.
 */
final class ContentStream {

    /**
     * How thick a faux-bold outline is, as a fraction of the font size. Only
     * reached when a family offers no real bold Face.
     */
    private static final float FAUX_BOLD_STROKE = 0.03f;

    private final RenderContext context;
    private final FontPlans plans;
    private final StringBuilder out = new StringBuilder(4096);

    /**
     * The current fill colour, so faux-bold text can stroke its outline in the
     * same colour it fills with.
     */
    private Color fill = Color.BLACK;

    private ContentStream(RenderContext context, FontPlans plans) {
        this.context = context;
        this.plans = plans;
    }

    static byte[] of(Page page, RenderContext context, FontPlans plans) {
        ContentStream stream = new ContentStream(context, plans);
        for (PaintCommand command : page.commands()) {
            stream.draw(command);
        }
        // The stream holds only PDF syntax and WinAnsi-escaped text, so every
        // char in it is a byte.
        return stream.out.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private void draw(PaintCommand command) {
        switch (command) {
            case PaintCommand.SetFillColor(Color color) -> {
                fill = color;
                out.append(rgb(color)).append(" rg\n");
            }
            case PaintCommand.SetStrokeColor(Color color) -> out.append(rgb(color)).append(" RG\n");
            case PaintCommand.SetLineWidth(float width) -> out.append(number(width, 3)).append(" w\n");
            case PaintCommand.SetDash(DashPattern pattern) -> {
                if (pattern == null) {
                    out.append("[] 0 d\n");
                } else {
                    out.append('[').append(coord(pattern.on())).append(' ')
                            .append(coord(pattern.off())).append("] 0 d\n");
                }
            }
            case PaintCommand.StrokeRect(Rect rect) -> out.append(rect(rect)).append(" re S\n");
            case PaintCommand.FillRect(Rect rect) -> out.append(rect(rect)).append(" re f\n");
            case PaintCommand.StrokeLine(float x1, float y1, float x2, float y2) ->
                    out.append(coord(x1)).append(' ').append(coord(y1)).append(" m ")
                            .append(coord(x2)).append(' ').append(coord(y2)).append(" l S\n");
            case PaintCommand.StrokeRoundedRect(RoundedRect rect) -> {
                roundedRectPath(rect);
                out.append("S\n");
            }
            case PaintCommand.FillRoundedRect(RoundedRect rect) -> {
                roundedRectPath(rect);
                out.append("f\n");
            }
            case PaintCommand.PushClipRect(Rect rect) ->
                    out.append("q\n").append(rect(rect)).append(" re W n\n");
            case PaintCommand.PopClip() -> out.append("Q\n");
            case PaintCommand.Image image -> drawImage(image);
            case PaintCommand.Text text -> drawText(text);
        }
    }

    private void drawImage(PaintCommand.Image image) {
        // Image space is the unit square, so the CTM both scales the image to its
        // box and moves it there.
        out.append("q\n")
                .append(coord(image.width())).append(" 0 0 ").append(coord(image.height()))
                .append(' ').append(coord(image.x())).append(' ').append(coord(image.y()))
                .append(" cm\n")
                .append("/Im").append(image.imageIndex()).append(" Do\n")
                .append("Q\n");
    }

    private void drawText(PaintCommand.Text text) {
        out.append("BT\n");
        if (text.letterSpacing() != 0f) {
            // Tc pads every glyph shown, two-byte CIDs included. It is graphics
            // state rather than text-object state, so it outlives ET and has to
            // be reset explicitly.
            out.append(number(text.letterSpacing(), 3)).append(" Tc\n");
        }
        if (text.bold()) {
            // Faux-bold: fill and stroke the glyphs in the same colour, because
            // this family gave us no real bold Face to embed.
            out.append(rgb(fill)).append(" RG\n")
                    .append(number(text.fontSize() * FAUX_BOLD_STROKE, 3)).append(" w 2 Tr\n");
        }

        FaceChain chain = context.face(text.face());
        List<FaceChain.Segment> segments = chain.segment(text.text());
        out.append(coord(text.x())).append(' ').append(coord(text.y())).append(" Td\n");

        if (segments == null) {
            selectFont(chain.primary(), text.fontSize());
            showSegment(chain.primary(), text.text());
        } else {
            // The text position flows across font switches, so consecutive
            // segments continue along the same baseline.
            Face current = null;
            for (FaceChain.Segment segment : segments) {
                Face face = chain.at(segment.chainIndex());
                if (face != current) {
                    selectFont(face, text.fontSize());
                    current = face;
                }
                showSegment(face, segment.text());
            }
        }

        if (text.bold()) {
            out.append("0 Tr\n");
        }
        if (text.letterSpacing() != 0f) {
            out.append("0 Tc\n");
        }
        out.append("ET\n");
    }

    private void selectFont(Face face, float size) {
        out.append('/').append(plans.resourceName(face)).append(' ').append(coord(size)).append(" Tf\n");
    }

    /**
     * Shows one run of text in one Face: glyph ids for an embedded Face, a
     * WinAnsi literal for the standard-14 one.
     */
    private void showSegment(Face face, String segment) {
        if (face instanceof EmbeddedFace embedded) {
            showGlyphs(embedded, segment);
        } else {
            out.append('(').append(WinAnsiEncoder.escapeLiteral(segment)).append(") Tj\n");
        }
    }

    /**
     * Writes a shaped run as {@code TJ}. The viewer advances each glyph by the
     * width in {@code /W}, so kerning is emitted as the difference between the
     * natural and the shaped advance, in thousandths of an em.
     */
    private void showGlyphs(EmbeddedFace face, String segment) {
        out.append("[<");
        for (Glyph glyph : face.shape(segment, 1000f, Direction.LTR, ShapingFeatures.SHAPED).glyphs()) {
            out.append(String.format(java.util.Locale.ROOT, "%04X", glyph.glyphId()));
            int natural = Math.round(face.advanceWidth(glyph.glyphId()) * 1000f / face.unitsPerEm());
            int adjustment = natural - Math.round(glyph.advance());
            if (adjustment != 0) {
                out.append("> ").append(adjustment).append(" <");
            }
        }
        out.append(">] TJ\n");
    }

    /**
     * Four edges joined by quarter-circle Béziers, using the standard circle
     * approximation constant. {@code (x, y)} is the lower-left corner; the caller
     * appends the painting operator.
     */
    private void roundedRectPath(RoundedRect rect) {
        float x = rect.x();
        float y = rect.y();
        float w = rect.width();
        float h = rect.height();
        float r = Math.max(0f, Math.min(rect.radius(), Math.min(w / 2f, h / 2f)));
        float k = 0.55228475f * r;

        moveTo(x + r, y);
        lineTo(x + w - r, y);
        curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r);
        lineTo(x + w, y + h - r);
        curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h);
        lineTo(x + r, y + h);
        curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r);
        lineTo(x, y + r);
        curveTo(x, y + r - k, x + r - k, y, x + r, y);
        out.append("h\n");
    }

    private void moveTo(float x, float y) {
        out.append(coord(x)).append(' ').append(coord(y)).append(" m\n");
    }

    private void lineTo(float x, float y) {
        out.append(coord(x)).append(' ').append(coord(y)).append(" l\n");
    }

    private void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        out.append(coord(x1)).append(' ').append(coord(y1)).append(' ')
                .append(coord(x2)).append(' ').append(coord(y2)).append(' ')
                .append(coord(x3)).append(' ').append(coord(y3)).append(" c\n");
    }

    private static String rgb(Color color) {
        return number(color.r(), 4) + " " + number(color.g(), 4) + " " + number(color.b(), 4);
    }

    private static String rect(Rect rect) {
        return coord(rect.x()) + " " + coord(rect.y()) + " " + coord(rect.width()) + " " + coord(rect.height());
    }
}
