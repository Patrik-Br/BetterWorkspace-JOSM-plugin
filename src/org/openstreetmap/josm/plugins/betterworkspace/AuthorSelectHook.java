package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.Timer;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.UserListDialog;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;

/**
 * Adds a "Select objects" entry to the right-click menu of JOSM's built-in
 * Authors panel (UserListDialog), which normally only offers "Copy". JOSM
 * core exposes no public extension point for this dialog, so this reaches
 * its private popupMenu/userTable/model fields via reflection and delegates
 * to the model's own selectPrimitivesOwnedBy(int...) - the same method the
 * dialog's built-in "Select" button already uses - rather than reimplementing
 * selection logic ourselves.
 */
final class AuthorSelectHook {

    private static final String MENU_TEXT_KEY = "Select objects - BetterWorkspace";

    private AuthorSelectHook() { }

    static void installWhenReady(MapFrame mapFrame, int retriesLeft) {
        UserListDialog dialog = mapFrame.getToggleDialog(UserListDialog.class);
        if (dialog != null) {
            install(dialog);
            return;
        }
        if (retriesLeft <= 0) {
            Logging.warn("BetterWorkspace: Authors panel never became available, giving up on 'Select objects' hook");
            return;
        }
        Timer timer = new Timer(250, null);
        timer.addActionListener(e -> {
            timer.stop();
            if (MainApplication.getMap() != mapFrame) {
                return;
            }
            installWhenReady(mapFrame, retriesLeft - 1);
        });
        timer.setRepeats(false);
        timer.start();
    }

    private static void install(UserListDialog dialog) {
        try {
            Field tableField = UserListDialog.class.getDeclaredField("userTable");
            tableField.setAccessible(true);
            JTable table = (JTable) tableField.get(dialog);

            Field popupField = UserListDialog.class.getDeclaredField("popupMenu");
            popupField.setAccessible(true);
            JPopupMenu popup = (JPopupMenu) popupField.get(dialog);

            Field modelField = UserListDialog.class.getDeclaredField("model");
            modelField.setAccessible(true);
            Object model = modelField.get(dialog);

            if (table == null || popup == null || model == null || alreadyInstalled(popup)) {
                return;
            }

            Method selectPrimitivesOwnedBy = model.getClass().getDeclaredMethod("selectPrimitivesOwnedBy", int[].class);
            selectPrimitivesOwnedBy.setAccessible(true);

            JMenuItem item = new JMenuItem(I18n.tr(MENU_TEXT_KEY));
            item.addActionListener(e -> {
                int[] rows = table.getSelectedRows();
                if (rows.length == 0) {
                    return;
                }
                try {
                    selectPrimitivesOwnedBy.invoke(model, (Object) rows);
                } catch (ReflectiveOperationException ex) {
                    Logging.warn("BetterWorkspace: 'Select objects' failed: " + ex);
                }
            });
            popup.add(item);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            Logging.warn("BetterWorkspace: could not hook Authors panel context menu (JOSM internals may have changed): " + ex);
        }
    }

    private static boolean alreadyInstalled(JPopupMenu popup) {
        for (Component c : popup.getComponents()) {
            if (c instanceof JMenuItem && I18n.tr(MENU_TEXT_KEY).equals(((JMenuItem) c).getText())) {
                return true;
            }
        }
        return false;
    }
}
