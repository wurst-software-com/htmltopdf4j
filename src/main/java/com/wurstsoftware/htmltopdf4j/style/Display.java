package com.wurstsoftware.htmltopdf4j.style;

import java.util.Locale;

/** The box an element generates, or that it generates none. */
public enum Display {
    BLOCK,
    INLINE,
    INLINE_BLOCK,
    LIST_ITEM,
    FLEX,
    INLINE_FLEX,
    GRID,
    INLINE_GRID,
    TABLE,
    TABLE_ROW,
    TABLE_CELL,
    TABLE_ROW_GROUP,
    TABLE_HEADER_GROUP,
    TABLE_FOOTER_GROUP,
    TABLE_CAPTION,
    /** Generates no box at all, so the element neither renders nor occupies space. */
    NONE;

    public boolean isBlockLevel() {
        return switch (this) {
            case BLOCK, LIST_ITEM, FLEX, GRID, TABLE, TABLE_CAPTION -> true;
            default -> false;
        };
    }

    public boolean isInlineLevel() {
        return switch (this) {
            case INLINE, INLINE_BLOCK, INLINE_FLEX, INLINE_GRID -> true;
            default -> false;
        };
    }

    /** Whether the element is laid out as a block internally, however it sits in its parent. */
    public boolean establishesBlockContainer() {
        return this != INLINE && this != NONE;
    }

    public static Display parse(String value, Display fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "block", "flow-root" -> BLOCK;
            case "inline" -> INLINE;
            case "inline-block" -> INLINE_BLOCK;
            case "list-item" -> LIST_ITEM;
            case "flex" -> FLEX;
            case "inline-flex" -> INLINE_FLEX;
            case "grid" -> GRID;
            case "inline-grid" -> INLINE_GRID;
            case "table" -> TABLE;
            case "table-row" -> TABLE_ROW;
            case "table-cell" -> TABLE_CELL;
            case "table-row-group" -> TABLE_ROW_GROUP;
            case "table-header-group" -> TABLE_HEADER_GROUP;
            case "table-footer-group" -> TABLE_FOOTER_GROUP;
            case "table-caption" -> TABLE_CAPTION;
            case "none" -> NONE;
            default -> fallback;
        };
    }
}
