package com.peterungab.sms.ui;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.util.Objects;
import java.util.Optional;

/**
 * The one place where the UI blocks and waits for the user: modal dialogs, confirmations,
 * message boxes and file choosers.
 * <p>
 * Views call the static methods. Behind them sits a {@link Handler}: in the app it is
 * {@link #SWING}, which shows the real Swing dialogs; the UI tests install a scripted handler
 * that fills in the same dialogs in-process, so every click path can be exercised without a
 * visible window, keyboard focus or OS-level input.
 */
public final class Prompts {

    /** Everything that needs an answer from the user. */
    public interface Handler {

        /** Shows a modal dialog and returns once it has been closed. */
        void showModal(JDialog dialog);

        /** Asks a yes/no question; {@code confirmText} is the label of the affirmative button. */
        boolean confirm(Component parent, String title, String message, String confirmText);

        /** Shows a message box; {@code messageType} is a {@link JOptionPane} constant. */
        void showMessage(Component parent, String title, String message, int messageType);

        /** Asks for a file to save to. Empty when the user cancels. */
        Optional<File> chooseSaveFile(Component parent, String title, File suggested, FileNameExtensionFilter filter);
    }

    /** The real Swing dialogs. */
    public static final Handler SWING = new Handler() {
        @Override
        public void showModal(JDialog dialog) {
            dialog.setVisible(true);
        }

        @Override
        public boolean confirm(Component parent, String title, String message, String confirmText) {
            Object[] options = {confirmText, "Cancel"};
            int choice = JOptionPane.showOptionDialog(parent, message, title, JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            return choice == 0;
        }

        @Override
        public void showMessage(Component parent, String title, String message, int messageType) {
            JOptionPane.showMessageDialog(parent, message, title, messageType);
        }

        @Override
        public Optional<File> chooseSaveFile(Component parent, String title, File suggested,
                                             FileNameExtensionFilter filter) {
            JFileChooser chooser = new JFileChooser(suggested.getParentFile());
            chooser.setDialogTitle(title);
            if (filter != null) {
                chooser.setFileFilter(filter);
            }
            chooser.setSelectedFile(suggested);
            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return Optional.empty();
            }
            return Optional.ofNullable(chooser.getSelectedFile());
        }
    };

    private static volatile Handler handler = SWING;

    private Prompts() {
    }

    /** Replaces the handler (used by tests). Pair with {@link #reset()}. */
    public static void install(Handler newHandler) {
        handler = Objects.requireNonNull(newHandler, "handler");
    }

    public static void reset() {
        handler = SWING;
    }

    public static void showModal(JDialog dialog) {
        handler.showModal(dialog);
    }

    public static boolean confirm(Component parent, String title, String message, String confirmText) {
        return handler.confirm(parent, title, message, confirmText);
    }

    public static void showMessage(Component parent, String title, String message, int messageType) {
        handler.showMessage(parent, title, message, messageType);
    }

    public static Optional<File> chooseSaveFile(Component parent, String title, File suggested,
                                                FileNameExtensionFilter filter) {
        return handler.chooseSaveFile(parent, title, suggested, filter);
    }
}
