package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * Reflection bridge into the todo plugin's dialog (standard "todo" plugin or
 * this user's own Todo_patrik fork - both use the class name
 * {@code org.openstreetmap.josm.plugins.todo.TodoDialog}). Tries the modern
 * public {@code addItemsFromPrimitives} method first, falling back to
 * simulating clicks on the dialog's private "add"/"select" actions for older
 * versions that predate it.
 */
final class TodoBridge {

    private TodoBridge() {}

    static boolean addWaysToTodo(OsmDataLayer layer, Collection<Way> ways) {
        MapFrame map = MainApplication.getMap();
        if (map == null) {
            return false;
        }
        ToggleDialog todoDialog = findTodoDialog(map);
        if (todoDialog == null) {
            return false;
        }
        try {
            Method addItems = todoDialog.getClass().getMethod("addItemsFromPrimitives", Collection.class);
            addItems.invoke(todoDialog, ways);
            zoomToFirstItem(todoDialog);
            return true;
        } catch (NoSuchMethodException e) {
            try {
                Field actAdd = todoDialog.getClass().getDeclaredField("actAdd");
                actAdd.setAccessible(true);
                Object addAction = actAdd.get(todoDialog);
                Method addPerformed = addAction.getClass().getDeclaredMethod("actionPerformed", ActionEvent.class);
                addPerformed.setAccessible(true);
                addPerformed.invoke(addAction, new ActionEvent(todoDialog, 1001, null));

                Field actSelect = todoDialog.getClass().getDeclaredField("actSelect");
                actSelect.setAccessible(true);
                Object selectAction = actSelect.get(todoDialog);
                Method selectPerformed = selectAction.getClass().getDeclaredMethod("actionPerformed", ActionEvent.class);
                selectPerformed.setAccessible(true);
                selectPerformed.invoke(selectAction, new ActionEvent(todoDialog, 1001, null));
                return true;
            } catch (Exception fallbackFailed) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void zoomToFirstItem(ToggleDialog todoDialog) throws Exception {
        Field modelField = todoDialog.getClass().getDeclaredField("model");
        modelField.setAccessible(true);
        Object model = modelField.get(todoDialog);
        Method getTodoList = model.getClass().getMethod("getTodoList");
        List<?> todoList = (List<?>) getTodoList.invoke(model);
        if (!todoList.isEmpty()) {
            Object firstItem = todoList.get(0);
            Method selectAndZoom = todoDialog.getClass().getDeclaredMethod("selectAndZoom", Collection.class);
            selectAndZoom.setAccessible(true);
            selectAndZoom.invoke(todoDialog, Collections.singletonList(firstItem));
        }
    }

    private static ToggleDialog findTodoDialog(MapFrame map) {
        try {
            Field allDialogs = MapFrame.class.getDeclaredField("allDialogs");
            allDialogs.setAccessible(true);
            for (ToggleDialog dialog : (List<ToggleDialog>) allDialogs.get(map)) {
                if ("org.openstreetmap.josm.plugins.todo.TodoDialog".equals(dialog.getClass().getName())) {
                    return dialog;
                }
            }
        } catch (Exception e) {
            // todo plugin not installed, or its internals changed - caller treats null as "not found"
        }
        return null;
    }
}
