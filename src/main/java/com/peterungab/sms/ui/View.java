package com.peterungab.sms.ui;

/** A page shown in the main window's content area. */
public interface View {

    /** Reloads the data shown by the page. Called every time the page becomes visible. */
    void refresh();

    /** Opens the "add new" dialog of this page (Ctrl+N). */
    default void createNew() {
    }

    /** Moves focus to the search box (Ctrl+F). */
    default void focusSearch() {
    }

    /** Exports the visible table to CSV (Ctrl+E). */
    default void exportCsv() {
    }

    default boolean supportsExport() {
        return false;
    }
}
