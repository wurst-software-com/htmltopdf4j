package com.wurstsoftware.htmltopdf4j.style;

import java.util.Locale;

/**
 * One CSS declaration.
 *
 * @param property the property name, lower-cased
 * @param value the value with its {@code !important} flag already stripped
 */
public record Declaration(String property, String value, boolean important) {

    public Declaration {
        property = property.trim().toLowerCase(Locale.ROOT);
        value = value.trim();
    }
}
