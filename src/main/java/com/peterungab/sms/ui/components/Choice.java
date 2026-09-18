package com.peterungab.sms.ui.components;

/**
 * An item for combo boxes: a display label plus an optional value, so "All programs" can be
 * represented as a choice whose value is {@code null}.
 */
public record Choice<T>(String label, T value) {

    public static <T> Choice<T> all(String label) {
        return new Choice<>(label, null);
    }

    public static <T> Choice<T> of(T value) {
        return new Choice<>(String.valueOf(value), value);
    }

    @Override
    public String toString() {
        return label;
    }
}
