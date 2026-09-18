package com.peterungab.sms.ui;

import javax.swing.Icon;
import java.util.function.IntFunction;

/** Top-level navigation targets, in sidebar order. */
public enum Page {
    DASHBOARD("Dashboard", Icons::dashboard),
    STUDENTS("Students", Icons::students),
    COURSES("Courses", Icons::courses),
    ENROLLMENTS("Enrollments & Grades", Icons::enrollments);

    private final String title;
    private final IntFunction<Icon> icon;

    Page(String title, IntFunction<Icon> icon) {
        this.title = title;
        this.icon = icon;
    }

    public String title() {
        return title;
    }

    public Icon icon(int size) {
        return icon.apply(size);
    }

    /** Ctrl+1 .. Ctrl+4. */
    public String shortcut() {
        return "ctrl " + (ordinal() + 1);
    }
}
