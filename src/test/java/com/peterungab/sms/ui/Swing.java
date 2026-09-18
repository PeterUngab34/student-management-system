package com.peterungab.sms.ui;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.table.JTableHeader;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Helpers for driving Swing components in-process from a test: run code on the EDT, find
 * components in a tree, and trigger the same listeners a user would through the component's own
 * API (button models, input/action maps, synthetic mouse events dispatched to the component).
 * Nothing here touches the OS input queue or the focus owner.
 */
final class Swing {

    private Swing() {
    }

    interface EdtAction {
        void run() throws Exception;
    }

    /** Runs {@code action} on the EDT and rethrows anything it throws (assertions included). */
    static void onEdt(EdtAction action) {
        onEdt(() -> {
            action.run();
            return null;
        });
    }

    static <T> T onEdt(Callable<T> action) {
        List<T> result = new ArrayList<>(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.add(action.call());
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return result.get(0);
    }

    /** Polls {@code condition} on the EDT until it holds, or fails after a few seconds. */
    static void waitUntil(String what, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (true) {
            if (onEdt(condition::getAsBoolean)) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for: " + what);
            }
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for: " + what);
            }
        }
    }

    // ---- finding components ----------------------------------------------------------------

    static <T extends Component> List<T> findAll(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    private static <T extends Component> void collect(Container root, Class<T> type, List<T> into) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                into.add(type.cast(child));
            }
            if (child instanceof Container sub) {
                collect(sub, type, into);
            }
        }
    }

    static <T extends Component> T first(Container root, Class<T> type) {
        List<T> all = findAll(root, type);
        if (all.isEmpty()) {
            fail("No " + type.getSimpleName() + " under " + root.getClass().getSimpleName());
        }
        return all.get(0);
    }

    /** A button whose text is {@code text}, or starts with it (for labels ending in an ellipsis). */
    static AbstractButton button(Container root, String text) {
        for (AbstractButton b : findAll(root, AbstractButton.class)) {
            String t = b.getText();
            if (t != null && (t.equals(text) || t.startsWith(text))) {
                return b;
            }
        }
        return fail("No button '" + text + "' under " + root.getClass().getSimpleName());
    }

    static JTable table(Container root) {
        return first(root, JTable.class);
    }

    static JTextField textField(Container root) {
        return first(root, JTextField.class);
    }

    @SuppressWarnings("unchecked")
    static <T> JComboBox<T> combo(Container root, int index) {
        List<JComboBox> combos = findAll(root, JComboBox.class);
        if (combos.size() <= index) {
            fail("Only " + combos.size() + " combo boxes under " + root.getClass().getSimpleName());
        }
        return (JComboBox<T>) combos.get(index);
    }

    /** All label texts in a container joined with " | " (handy for status bars). */
    static String labelTexts(Container root) {
        StringBuilder sb = new StringBuilder();
        for (JLabel label : findAll(root, JLabel.class)) {
            if (label.getText() != null && !label.getText().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(label.getText());
            }
        }
        return sb.toString();
    }

    static JMenuItem menuItem(JMenuBar bar, String menuText, String itemText) {
        for (int m = 0; m < bar.getMenuCount(); m++) {
            JMenu menu = bar.getMenu(m);
            if (menu == null || !menu.getText().equals(menuText)) {
                continue;
            }
            for (int i = 0; i < menu.getItemCount(); i++) {
                JMenuItem item = menu.getItem(i);
                if (item != null && item.getText() != null && item.getText().startsWith(itemText)) {
                    return item;
                }
            }
        }
        return fail("No menu item '" + menuText + " > " + itemText + "'");
    }

    /** Selects the first combo item whose value (or label) matches. */
    static <T> void select(JComboBox<T> combo, java.util.function.Predicate<T> matches) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (matches.test(combo.getItemAt(i))) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        fail("No matching item in combo box with " + combo.getItemCount() + " items");
    }

    static String cell(JTable table, int viewRow, int column) {
        return String.valueOf(table.getValueAt(viewRow, column));
    }

    // ---- triggering behaviour ----------------------------------------------------------------

    /**
     * Fires the action bound to {@code keyStroke}, resolving it the way Swing does when the key is
     * pressed while {@code component} has focus: the component's own WHEN_FOCUSED map, then the
     * WHEN_ANCESTOR_OF_FOCUSED_COMPONENT maps up the parent chain, then every
     * WHEN_IN_FOCUSED_WINDOW binding in the window (which is where menu accelerators such as
     * Ctrl+N live).
     */
    static void pressKey(JComponent component, String keyStroke) {
        KeyStroke ks = KeyStroke.getKeyStroke(keyStroke);
        if (fire(component, JComponent.WHEN_FOCUSED, ks, keyStroke)) {
            return;
        }
        for (Container c = component; c != null && !(c instanceof java.awt.Window); c = c.getParent()) {
            if (c instanceof JComponent jc && fire(jc, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, ks, keyStroke)) {
                return;
            }
        }
        Container window = SwingUtilities.getWindowAncestor(component);
        if (window != null) {
            for (JComponent jc : windowBindingTargets(window)) {
                if (fire(jc, JComponent.WHEN_IN_FOCUSED_WINDOW, ks, keyStroke)) {
                    return;
                }
            }
        }
        fail("No action bound to '" + keyStroke + "' reachable from " + component.getClass().getSimpleName());
    }

    private static boolean fire(JComponent component, int condition, KeyStroke ks, String description) {
        InputMap map = component.getInputMap(condition);
        Object key = map == null ? null : map.get(ks);
        if (key == null) {
            return false;
        }
        Action action = component.getActionMap().get(key);
        if (action == null || !action.isEnabled()) {
            return false;
        }
        action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, description));
        return true;
    }

    /** Every JComponent in the window, including menu items (whose popups are not in the tree). */
    private static List<JComponent> windowBindingTargets(Container window) {
        List<JComponent> result = new ArrayList<>();
        collectBindingTargets(window, result);
        return result;
    }

    private static void collectBindingTargets(Container root, List<JComponent> into) {
        for (Component child : root.getComponents()) {
            if (child instanceof JComponent jc) {
                into.add(jc);
            }
            if (child instanceof JMenu menu) {
                collectBindingTargets(menu.getPopupMenu(), into);
            } else if (child instanceof Container sub) {
                collectBindingTargets(sub, into);
            }
        }
    }

    /** Dispatches a left double-click at the centre of a cell to the table's own listeners. */
    static void doubleClickRow(JTable table, int viewRow) {
        Rectangle r = table.getCellRect(viewRow, 0, true);
        click(table, r.x + r.width / 2, r.y + r.height / 2, 2);
    }

    /** Dispatches a left click on a column header, which is how a user sorts a column. */
    static void clickHeader(JTable table, int viewColumn) {
        JTableHeader header = table.getTableHeader();
        Rectangle r = header.getHeaderRect(viewColumn);
        click(header, r.x + r.width / 2, r.y + r.height / 2, 1);
    }

    private static void click(Component target, int x, int y, int clicks) {
        long now = System.currentTimeMillis();
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, now, 0, x, y, clicks, false, MouseEvent.BUTTON1));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, now, 0, x, y, clicks, false, MouseEvent.BUTTON1));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, now, 0, x, y, clicks, false, MouseEvent.BUTTON1));
    }
}
