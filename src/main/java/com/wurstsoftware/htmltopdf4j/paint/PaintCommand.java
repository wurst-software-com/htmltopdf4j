package com.wurstsoftware.htmltopdf4j.paint;

/**
 * One drawing primitive in a Display list, expressed in points with the origin
 * at the bottom left of the Page.
 *
 * <p>This is the seam that keeps Layout from knowing anything about PDF. It is
 * sealed so the writer's switch over it is exhaustive at compile time, which is
 * what the reference engine got from a Rust {@code match} — adding a command
 * without teaching the writer to draw it is a compile error, not a silently
 * skipped primitive.
 *
 * <p>Internal by design: ADR 0001 justifies the seam existing, not its being
 * part of the published surface.
 */
public sealed interface PaintCommand {

    record SetFillColor(Color color) implements PaintCommand {}

    record SetStrokeColor(Color color) implements PaintCommand {}

    /** Stroke line width in points, for subsequent strokes. */
    record SetLineWidth(float width) implements PaintCommand {}

    /**
     * Stroke dash pattern; a {@code null} pattern returns to solid. Every dashed
     * stroke is paired with a reset so later strokes stay solid.
     */
    record SetDash(DashPattern pattern) implements PaintCommand {}

    /**
     * A run of text drawn with its baseline starting at {@code (x, y)}.
     *
     * @param face index into the render context's resolved Faces; 0 is the default Face
     * @param bold draw with faux-bold (fill plus a thin stroke) because the
     *     family provided no real bold Face
     * @param letterSpacing CSS {@code letter-spacing} in points, emitted as the
     *     PDF {@code Tc} text state and reset afterwards
     */
    record Text(String text, float x, float y, float fontSize, int face, boolean bold, float letterSpacing)
            implements PaintCommand {}

    record StrokeRect(Rect rect) implements PaintCommand {}

    record FillRect(Rect rect) implements PaintCommand {}

    record StrokeLine(float x1, float y1, float x2, float y2) implements PaintCommand {}

    record StrokeRoundedRect(RoundedRect rect) implements PaintCommand {}

    record FillRoundedRect(RoundedRect rect) implements PaintCommand {}

    record PushClipRect(Rect rect) implements PaintCommand {}

    record PopClip() implements PaintCommand {}

    /**
     * Draw the image at {@code imageIndex} in the Document's image table into the
     * box whose lower-left corner is {@code (x, y)}.
     */
    record Image(int imageIndex, float x, float y, float width, float height) implements PaintCommand {}
}
