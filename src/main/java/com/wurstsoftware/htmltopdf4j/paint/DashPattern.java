package com.wurstsoftware.htmltopdf4j.paint;

/**
 * An on/off stroke dash pattern in points, written as the PDF {@code [on off] 0 d}
 * operator. {@code null} where a {@link PaintCommand.SetDash} means "back to solid".
 */
public record DashPattern(float on, float off) {}
