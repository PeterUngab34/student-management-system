package com.peterungab.sms.ui.dialogs;

import com.formdev.flatlaf.FlatClientProperties;
import com.peterungab.sms.dao.DataAccessException;
import com.peterungab.sms.service.BusinessRuleException;
import com.peterungab.sms.service.ValidationException;
import com.peterungab.sms.ui.Ui;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for modal add/edit dialogs: heading, two-column form with an inline error label
 * under every field, and Cancel / Save buttons. Subclasses implement {@link #save()}, which calls
 * the service layer; {@link ValidationException}s are mapped back onto the fields.
 *
 * @param <T> type of the saved object
 */
public abstract class FormDialog<T> extends JDialog {

    protected final JPanel form = new JPanel(new MigLayout("insets 0, fillx, wrap 2, gap 18 0",
            "[grow,fill,sg col][grow,fill,sg col]", ""));
    private final Map<String, JLabel> errorLabels = new LinkedHashMap<>();
    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private final JLabel generalError = Ui.errorLabel();
    private final JButton saveButton;
    private int minWidth;
    private T result;

    protected FormDialog(Window owner, String title, String heading, String subheading, String saveText) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        form.setOpaque(false);

        JLabel headingLabel = new JLabel(heading);
        headingLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +6");

        saveButton = Ui.primaryButton(saveText, null);
        JButton cancel = Ui.button("Cancel", null);
        saveButton.addActionListener(e -> onSave());
        cancel.addActionListener(e -> dispose());

        JPanel content = new JPanel(new MigLayout("fill, insets 24 26 20 26", "[grow,fill]", "[]2[]20[grow,fill]8[]14[]"));
        content.add(headingLabel, "wrap");
        content.add(Ui.muted(subheading), "wrap");
        content.add(form, "wrap");
        content.add(generalError, "wrap");
        JPanel buttons = new JPanel(new MigLayout("insets 0, fillx", "push[]10[]", "[]"));
        buttons.setOpaque(false);
        buttons.add(cancel, "wmin 96");
        buttons.add(saveButton, "wmin 110");
        content.add(buttons, "growx");
        setContentPane(content);
        getRootPane().setDefaultButton(saveButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Adds a labelled field. {@code key} is the field name used in {@link ValidationException#errors()}. */
    protected void addField(String label, JComponent field, String key, boolean fullWidth) {
        JPanel cell = new JPanel(new MigLayout("insets 0, fillx, gap 0", "[grow,fill]", "[]6[]3[]"));
        cell.setOpaque(false);
        JLabel caption = new JLabel(label);
        caption.putClientProperty(FlatClientProperties.STYLE, "font: bold -1");
        caption.setLabelFor(field);
        JLabel error = Ui.errorLabel();
        errorLabels.put(key, error);
        fields.put(key, field);
        cell.add(caption, "wrap");
        cell.add(field, "h 34!, wrap");
        cell.add(error);
        form.add(cell, fullWidth ? "span 2, growx, gapbottom 6" : "growx, gapbottom 6");
    }

    /** Performs the save and returns the saved object; may throw service exceptions. */
    protected abstract T save();

    /** Hook to validate purely UI-level input (e.g. date parsing) before {@link #save()}. */
    protected void validateInput(Map<String, String> errors) {
    }

    private void onSave() {
        clearErrors();
        Map<String, String> uiErrors = new LinkedHashMap<>();
        validateInput(uiErrors);
        if (!uiErrors.isEmpty()) {
            showErrors(uiErrors);
            return;
        }
        try {
            result = save();
            dispose();
        } catch (ValidationException e) {
            showErrors(e.errors());
        } catch (BusinessRuleException e) {
            generalError.setText(e.getMessage());
        } catch (DataAccessException e) {
            Ui.showError(this, "Database error", e.getMessage());
        }
    }

    private void clearErrors() {
        errorLabels.values().forEach(l -> l.setText(" "));
        generalError.setText(" ");
    }

    private void showErrors(Map<String, String> errors) {
        errors.forEach((field, message) -> {
            JLabel label = errorLabels.get(field);
            if (label != null) {
                label.setText(message);
            } else {
                generalError.setText(message);
            }
        });
        // grow to fit longer messages, but never shrink below the initial width
        Dimension pref = getPreferredSize();
        setSize(Math.max(minWidth, pref.width), Math.max(getHeight(), pref.height));
    }

    /** The saved object, or empty if the dialog was cancelled. */
    public Optional<T> result() {
        return Optional.ofNullable(result);
    }

    // ---- programmatic access (used by the UI tests) -----------------------------------------

    /** The input component registered under {@code key} in {@link #addField}. */
    public JComponent field(String key) {
        JComponent field = fields.get(key);
        if (field == null) {
            throw new IllegalArgumentException("No field '" + key + "'; known fields: " + fields.keySet());
        }
        return field;
    }

    /** The validation message currently shown under a field, or an empty string. */
    public String errorMessage(String key) {
        JLabel label = errorLabels.get(key);
        return label == null ? "" : label.getText().trim();
    }

    /** The message shown above the buttons for errors that belong to no single field, or "". */
    public String generalErrorMessage() {
        return generalError.getText().trim();
    }

    /** Same as pressing the Save button (or Enter). */
    public void submit() {
        saveButton.doClick();
    }

    protected void finishLayout(int width) {
        pack();
        minWidth = Math.max(width, getWidth());
        setSize(minWidth, getHeight());
        setResizable(false);
        setLocationRelativeTo(getOwner());
    }
}
