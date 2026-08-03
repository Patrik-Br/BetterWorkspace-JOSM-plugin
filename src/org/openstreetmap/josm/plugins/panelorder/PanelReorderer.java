/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.openstreetmap.josm.gui.MapFrame
 *  org.openstreetmap.josm.gui.dialogs.DialogsPanel
 *  org.openstreetmap.josm.gui.dialogs.DialogsPanel$Action
 *  org.openstreetmap.josm.gui.dialogs.ToggleDialog
 *  org.openstreetmap.josm.spi.preferences.Config
 *  org.openstreetmap.josm.tools.Logging
 */
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
            DialogsPanel dialogsPanel;
            if (component instanceof DialogsPanel) {
                return (DialogsPanel)component;
            }
            if (!(component instanceof Container) || (dialogsPanel = PanelReorderer.findDialogsPanel((Container)component)) == null) continue;
            return dialogsPanel;
        }
        return null;
    }

    private static List<ToggleDialog> liveDialogList(DialogsPanel dialogsPanel) {
        try {
            Field field = DialogsPanel.class.getDeclaredField("allDialogs");
            field.setAccessible(true);
            return (List)field.get(dialogsPanel);
        }
        catch (ClassCastException | ReflectiveOperationException | SecurityException exception) {
            Logging.error((String)("PanelOrder: cannot access DialogsPanel.allDialogs - " + String.valueOf(exception)));
            Logging.trace((Throwable)exception);
            return null;
        }
    }

    public static List<ToggleDialog> getCurrentOrder(MapFrame mapFrame) {
        DialogsPanel dialogsPanel = PanelReorderer.findDialogsPanel((Container)mapFrame);
        if (dialogsPanel == null || !dialogsPanel.initialized) {
            return new ArrayList<ToggleDialog>();
        }
        List<ToggleDialog> list = PanelReorderer.liveDialogList(dialogsPanel);
        return list == null ? new ArrayList<ToggleDialog>() : new ArrayList<ToggleDialog>(list);
    }

    public static boolean applyOrder(MapFrame mapFrame, List<ToggleDialog> list) {
        DialogsPanel dialogsPanel = PanelReorderer.findDialogsPanel((Container)mapFrame);
        if (dialogsPanel == null || !dialogsPanel.initialized) {
            Logging.warn((String)"PanelOrder: DialogsPanel not ready, cannot apply order");
            return false;
        }
        List<ToggleDialog> list2 = PanelReorderer.liveDialogList(dialogsPanel);
        if (list2 == null) {
            return false;
        }
        PanelReorderer.rememberStartupOrder(list2);
        LinkedHashSet<ToggleDialog> linkedHashSet = new LinkedHashSet<ToggleDialog>();
        for (ToggleDialog toggleDialog : list) {
            if (!list2.contains(toggleDialog)) continue;
            linkedHashSet.add(toggleDialog);
        }
        linkedHashSet.addAll(list2);
        if (linkedHashSet.size() != list2.size()) {
            Logging.warn((String)"PanelOrder: dialog set mismatch, aborting reorder");
            return false;
        }
        list2.clear();
        list2.addAll(linkedHashSet);
        try {
            dialogsPanel.reconstruct(DialogsPanel.Action.RESTORE_SAVED, null);
        }
        catch (RuntimeException runtimeException) {
            Logging.error((String)("PanelOrder: relayout failed - " + String.valueOf(runtimeException)));
            Logging.trace((Throwable)runtimeException);
            return false;
        }
        return true;
    }

    public static void saveOrder(List<ToggleDialog> list) {
        Config.getPref().putList(PREF_KEY, list.stream().map(toggleDialog -> toggleDialog.getClass().getName()).collect(Collectors.toList()));
    }

    public static boolean applySavedOrder(MapFrame mapFrame) {
        List list = Config.getPref().getList(PREF_KEY);
        List<ToggleDialog> list2 = PanelReorderer.getCurrentOrder(mapFrame);
        PanelReorderer.rememberStartupOrder(list2);
        if (list == null || list.isEmpty() || list2.isEmpty()) {
            return false;
        }
        ArrayList<ToggleDialog> arrayList = new ArrayList<ToggleDialog>(list2);
        arrayList.sort(Comparator.comparingInt(toggleDialog -> {
            int n = list.indexOf(toggleDialog.getClass().getName());
            return n >= 0 ? n : list.size() + list2.indexOf(toggleDialog);
        }));
        if (arrayList.equals(list2)) {
            return true;
        }
        return PanelReorderer.applyOrder(mapFrame, arrayList);
    }

    private static synchronized void rememberStartupOrder(List<ToggleDialog> list) {
        if (startupOrder == null && list != null && !list.isEmpty()) {
            startupOrder = list.stream().map(toggleDialog -> toggleDialog.getClass().getName()).collect(Collectors.toList());
        }
    }

    public static synchronized List<String> getStartupOrder() {
        return startupOrder == null ? null : new ArrayList<String>(startupOrder);
    }
}

