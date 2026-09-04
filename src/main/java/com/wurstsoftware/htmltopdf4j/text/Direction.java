package com.wurstsoftware.htmltopdf4j.text;

/** The base direction a run of text is shaped and laid out in. */
public enum Direction {
    LTR,
    RTL;

    public boolean isRtl() {
        return this == RTL;
    }
}
