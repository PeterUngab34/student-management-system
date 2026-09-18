package com.peterungab.sms.ui;

import javax.swing.JDialog;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@link Prompts.Handler} for tests: instead of showing modal dialogs it hands each one to a
 * script that fills it in and submits it, answers confirmations with a preset choice, records
 * every message box and returns a preset file from the "file chooser".
 */
final class RecordingPrompts implements Prompts.Handler {

    final List<JDialog> dialogs = new ArrayList<>();
    final List<String> confirmations = new ArrayList<>();
    final List<String> messages = new ArrayList<>();
    final List<File> suggestedFiles = new ArrayList<>();

    private Consumer<JDialog> script = d -> fail("Unexpected dialog: " + d.getTitle());
    private boolean confirmAnswer = true;
    private File fileToSave;

    /** What to do with the next modal dialogs (fill fields, call {@code submit()}, ...). */
    RecordingPrompts onDialog(Consumer<JDialog> script) {
        this.script = script;
        return this;
    }

    RecordingPrompts answerConfirmations(boolean yes) {
        this.confirmAnswer = yes;
        return this;
    }

    RecordingPrompts saveTo(File file) {
        this.fileToSave = file;
        return this;
    }

    @Override
    public void showModal(JDialog dialog) {
        dialogs.add(dialog);
        script.accept(dialog);
    }

    @Override
    public boolean confirm(Component parent, String title, String message, String confirmText) {
        confirmations.add(title + ": " + message);
        return confirmAnswer;
    }

    @Override
    public void showMessage(Component parent, String title, String message, int messageType) {
        messages.add(title + ": " + message);
    }

    @Override
    public Optional<File> chooseSaveFile(Component parent, String title, File suggested, FileNameExtensionFilter filter) {
        suggestedFiles.add(suggested);
        return Optional.ofNullable(fileToSave);
    }

    <T extends JDialog> T lastDialog(Class<T> type) {
        for (int i = dialogs.size() - 1; i >= 0; i--) {
            if (type.isInstance(dialogs.get(i))) {
                return type.cast(dialogs.get(i));
            }
        }
        return fail("No " + type.getSimpleName() + " was shown; dialogs: " + dialogs);
    }
}
