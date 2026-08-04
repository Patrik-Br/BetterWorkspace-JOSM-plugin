package org.openstreetmap.josm.plugins.panelorder;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.DialogsPanel;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

/**
 * Reorders the docked {@link ToggleDialog} side panels within JOSM's
 * {@link DialogsPanel}. JOSM core exposes no public API for changing that
 * order, so this reaches the panel's private {@code allDialogs} field via
 * reflection, reorders it in place, and asks the panel to relayout itself.
 * The resulting order is persisted to JOSM's preferences and reapplied on
 * every startup by {@link org.openstreetmap.josm.plugins.betterworkspace.BetterWorkspacePlugin}.
 */
public final class PanelReorderer {

    public static final String PREF_KEY = "panelorder.order";

    private static List<String> startupOrder;

    private PanelReorderer() {
    }

    public static DialogsPanel findDialogsPanel(Container container) {
        if (container == null) {
            return null;
        }
        for (Component component : container.getComponents()) {
            if (component instanceof DialogsPanel) {
                return (DialogsPanel) component;
            }
            if (component instanceof Container) {
                DialogsPanel found = findDialogsPanel((Container) component);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<ToggleDialog> liveDialogList(DialogsPanel dialogsPanel) {
        try {
            Field field = DialogsPanel.class.getDeclaredField("allDialogs");
            field.setAccessible(true);
            return (List<ToggleDialog>) field.get(dialogsPanel);
        } catch (ClassCastException | ReflectiveOperationException | SecurityException ex) {
            Logging.error("PanelOrder: cannot access DialogsPanel.allDialogs - " + ex);
            Logging.trace(ex);
            return null;
        }
    }

    public static List<ToggleDialog> getCurrentOrder(MapFrame mapFrame) {
        DialogsPanel dialogsPanel = findDialogsPanel(mapFrame);
        if (dialogsPanel == null || !dialogsPanel.initialized) {
            return new ArrayList<>();
        }
        List<ToggleDialog> live = liveDialogList(dialogsPanel);
        return live == null ? new ArrayList<>() : new ArrayList<>(live);
    }

    public static boolean applyOrder(MapFrame mapFrame, List<ToggleDialog> newOrder) {
        DialogsPanel dialogsPanel = findDialogsPanel(mapFrame);
        if (dialogsPanel == null || !dialogsPanel.initialized) {
            Logging.warn("PanelOrder: DialogsPanel not ready, cannot apply order");
            return false;
        }
        List<ToggleDialog> live = liveDialogList(dialogsPanel);
        if (live == null) {
            return false;
        }
        rememberStartupOrder(live);

        // Build the reordered set from newOrder, then append any dialogs that
        // weren't in it (defensive - keeps every currently-docked dialog present
        // even if newOrder is somehow incomplete) rather than silently dropping them.
        LinkedHashSet<ToggleDialog> reordered = new LinkedHashSet<>();
        for (ToggleDialog dialog : newOrder) {
            if (live.contains(dialog)) {
                reordered.add(dialog);
            }
        }
        reordered.addAll(live);
        if (reordered.size() != live.size()) {
            Logging.warn("PanelOrder: dialog set mismatch, aborting reorder");
            return false;
        }

        live.clear();
        live.addAll(reordered);
        try {
            dialogsPanel.reconstruct(DialogsPanel.Action.RESTORE_SAVED, null);
        } catch (RuntimeException ex) {
            Logging.error("PanelOrder: relayout failed - " + ex);
            Logging.trace(ex);
            return false;
        }
        return true;
    }

    public static void saveOrder(List<ToggleDialog> order) {
        Config.getPref().putList(PREF_KEY,
                order.stream().map(d -> d.getClass().getName()).collect(Collectors.toList()));
    }

    public static boolean applySavedOrder(MapFrame mapFrame) {
        List<String> savedOrder = Config.getPref().getList(PREF_KEY);
        List<ToggleDialog> current = getCurrentOrder(mapFrame);
        rememberStartupOrder(current);
        if (savedOrder == null || savedOrder.isEmpty() || current.isEmpty()) {
            return false;
        }
        List<ToggleDialog> sorted = new ArrayList<>(current);
        sorted.sort(Comparator.comparingInt(d -> {
            int index = savedOrder.indexOf(d.getClass().getName());
            return index >= 0 ? index : savedOrder.size() + current.indexOf(d);
        }));
        if (sorted.equals(current)) {
            return true;
        }
        return applyOrder(mapFrame, sorted);
    }

    private static synchronized void rememberStartupOrder(List<ToggleDialog> order) {
        if (startupOrder == null && order != null && !order.isEmpty()) {
            startupOrder = order.stream().map(d -> d.getClass().getName()).collect(Collectors.toList());
        }
    }

    public static synchronized List<String> getStartupOrder() {
        return startupOrder == null ? null : new ArrayList<>(startupOrder);
    }
}
