package com.wurstsoftware.htmltopdf4j.paint;

/**
 * A rectangle with a uniform corner radius, for {@code border-radius}. The
 * radius is clamped to half the shorter side when the path is built, so a
 * producer may pass a radius larger than the box without producing a
 * self-intersecting path.
 */
public record RoundedRect(float x, float y, float width, float height, float radius) {}
