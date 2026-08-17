package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.DefaultListSelectionModel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;

/**
 * Ports one behavior from this user's own Todo_patrik fork into whichever "todo" plugin the
 * user actually has installed (standard "todo" plugin or the fork - both use the class name
 * {@code org.openstreetmap.josm.plugins.todo.TodoDialog}, matching {@link TodoBridge}): an item
 * marked done stays in the list, at its original position, grayed out, instead of disappearing.
 * Opt-out via the {@value #PREF_KEEP_DONE} preference (on by default) - a plain boolean, visible
 * and editable in JOSM's own Preferences -&gt; Advanced Preferences, no dedicated settings UI
 * needed.
 *
 * <p>There's no way to override the todo plugin's own removal logic in place - reflection can
 * call methods, not rewrite them - so this instead watches the list model's own change events
 * and reacts: whenever an item disappears, it's put back if it's still in the model's internal
 * "done" set (removed only because it was marked done) and the preference above is enabled. An
 * item is left removed - not resurrected - otherwise, which is what makes "Clear the todo list"
 * keep working: a bulk Clear empties the done-set too, so that check never matches; and an item
 * whose layer just got removed is never restored either.
 *
 * <p>Restoration goes through the model's own (package-private, in current versions)
 * {@code addItems(Collection)} rather than splicing the list and firing a change event
 * directly: reflecting into another plugin's own fields/methods is unrestricted (both run as
 * plain classpath code), but reflecting into a JDK platform class like
 * {@code AbstractListModel} to call its protected event-firing method is not - the module
 * system blocks {@code setAccessible} on it unless the JVM was launched with
 * {@code --add-opens java.desktop/javax.swing}, which JOSM's normal launch isn't. Delegating to
 * the model's own {@code addItems} sidesteps that entirely, since firing the event then happens
 * inside the todo plugin's own code, not through our reflection. addItems() always appends at
 * the end though, so afterwards the restored item is spliced back to its original index with a
 * plain List.add(int, Object) (no event needed for a pure reorder - the JList is just told to
 * repaint) rather than left wherever addItems() put it.
 *
 * <p>{@code addItems} has taken different shapes across versions of the todo plugin - newer
 * ones (where {@code TodoListItem} is a record) want already-built {@code TodoListItem}s,
 * older ones want the raw primitives. {@link RestoreListener#restore} tries the item-based call
 * first and falls back to primitives if that throws a type mismatch, rather than hard-coding
 * one shape.
 */
final class TodoBehaviorSync {

    static final String PREF_KEEP_DONE = "betterworkspace.todo.keepdone";

    /** Model instances already hooked, so re-entering mapFrameInitialized() doesn't double-install. */
    private static final Set<Object> installedModels = Collections.newSetFromMap(new WeakHashMap<>());

    private TodoBehaviorSync() { }

    static void installWhenReady(MapFrame mapFrame, int retriesLeft) {
        ToggleDialog todoDialog = TodoBridge.findTodoDialog(mapFrame);
        if (todoDialog != null) {
            install(todoDialog);
            return;
        }
        if (retriesLeft <= 0) {
            return; // todo plugin not installed - nothing to hook
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

    @SuppressWarnings("unchecked")
    private static void install(ToggleDialog todoDialog) {
        Object model = null;
        JList<Object> list = null;
        try {
            Field modelField = todoDialog.getClass().getDeclaredField("model");
            modelField.setAccessible(true);
            model = modelField.get(todoDialog);

            Field listField = todoDialog.getClass().getDeclaredField("lstPrimitives");
            listField.setAccessible(true);
            list = (JList<Object>) listField.get(todoDialog);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            Logging.log(Logging.LEVEL_WARN, "BetterWorkspace: could not find the todo plugin's "
                    + "list/model (its internals may have changed)", ex);
            return;
        }
        if (model == null || !installedModels.add(model)) {
            return;
        }
        installSyncHook(todoDialog, model, list);
        installGrayDoneRenderer(model, list);
    }

    private static void installSyncHook(ToggleDialog todoDialog, Object model, JList<Object> list) {
        try {
            Field todoListField = model.getClass().getDeclaredField("todoList");
            todoListField.setAccessible(true);
            Field doneListField = model.getClass().getDeclaredField("doneList");
            doneListField.setAccessible(true);
            // addItems is package-private in current todo plugin versions - getDeclaredMethod()
            // (not getMethod(), which only finds public members) plus setAccessible() is required.
            Method addItems = model.getClass().getDeclaredMethod("addItems", Collection.class);
            addItems.setAccessible(true);
            Method addListDataListener = model.getClass().getMethod("addListDataListener", ListDataListener.class);
            Method removeListDataListener = model.getClass().getMethod("removeListDataListener", ListDataListener.class);
            Method getListeners = model.getClass().getMethod("getListeners", Class.class);

            // Both optional - a missing selection model or selectAndZoom just means marking an
            // item done won't auto-advance to the next one; the core restore/reorder still works.
            DefaultListSelectionModel selectionModel = null;
            Method selectAndZoom = null;
            try {
                Field selectionModelField = model.getClass().getDeclaredField("selectionModel");
                selectionModelField.setAccessible(true);
                selectionModel = (DefaultListSelectionModel) selectionModelField.get(model);
                selectAndZoom = todoDialog.getClass().getDeclaredMethod("selectAndZoom", Collection.class);
                selectAndZoom.setAccessible(true);
            } catch (ReflectiveOperationException | ClassCastException ex) {
                Logging.log(Logging.LEVEL_WARN, "BetterWorkspace: could not hook auto-advance-to-next-item "
                        + "for the todo list (its internals may have changed)", ex);
            }

            addListDataListener.invoke(model, new RestoreListener(
                    todoDialog, model, list, todoListField, doneListField, addItems,
                    removeListDataListener, addListDataListener, getListeners, selectionModel, selectAndZoom));
            Logging.info("BetterWorkspace: hooked the todo plugin's list ({0})", model.getClass().getName());
        } catch (ReflectiveOperationException | ClassCastException ex) {
            Logging.log(Logging.LEVEL_WARN, "BetterWorkspace: could not hook the todo plugin's list "
                    + "(its internals may have changed)", ex);
        }
    }

    /**
     * The standard todo plugin's own renderer doesn't gray out done items (this user's
     * Todo_patrik fork does) - wraps the list's existing cell renderer to add that, independent
     * of whether the sync hook above installed successfully.
     */
    private static void installGrayDoneRenderer(Object model, JList<Object> list) {
        try {
            Method isDone = null;
            for (Method m : model.getClass().getDeclaredMethods()) {
                if ("isDone".equals(m.getName()) && m.getParameterCount() == 1) {
                    isDone = m;
                    break;
                }
            }
            if (list == null || isDone == null) {
                return;
            }
            Method isDoneMethod = isDone;
            isDoneMethod.setAccessible(true);

            ListCellRenderer<Object> original = list.getCellRenderer();
            list.setCellRenderer((jlist, value, index, isSelected, cellHasFocus) -> {
                Component c = original.getListCellRendererComponent(jlist, value, index, isSelected, cellHasFocus);
                try {
                    if (!isSelected && value != null && Config.getPref().getBoolean(PREF_KEEP_DONE, true)
                            && Boolean.TRUE.equals(isDoneMethod.invoke(model, value))) {
                        c.setForeground(Color.GRAY);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // leave the row styled as the original renderer left it
                }
                return c;
            });
        } catch (RuntimeException ex) {
            Logging.log(Logging.LEVEL_WARN, "BetterWorkspace: could not install the todo list's "
                    + "done-item graying (its internals may have changed)", ex);
        }
    }

    private static final class RestoreListener implements ListDataListener {
        private final ToggleDialog todoDialog;
        private final Object model;
        private final JList<Object> list;
        private final Field todoListField;
        private final Field doneListField;
        private final Method addItems;
        private final Method removeListDataListener;
        private final Method addListDataListener;
        private final Method getListeners;
        private final DefaultListSelectionModel selectionModel;
        private final Method selectAndZoom;
        /** Ordered snapshot (not just a Set) so a restored item can go back at its original index. */
        private List<Object> knownItemsOrdered;
        private boolean restoring;

        RestoreListener(ToggleDialog todoDialog, Object model, JList<Object> list, Field todoListField,
                Field doneListField, Method addItems, Method removeListDataListener, Method addListDataListener,
                Method getListeners, DefaultListSelectionModel selectionModel, Method selectAndZoom) {
            this.todoDialog = todoDialog;
            this.model = model;
            this.list = list;
            this.todoListField = todoListField;
            this.doneListField = doneListField;
            this.addItems = addItems;
            this.removeListDataListener = removeListDataListener;
            this.addListDataListener = addListDataListener;
            this.getListeners = getListeners;
            this.selectionModel = selectionModel;
            this.selectAndZoom = selectAndZoom;
            snapshot();
        }

        @Override
        public void intervalAdded(ListDataEvent e) {
            sync();
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
            sync();
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
            sync();
        }

        @SuppressWarnings("unchecked")
        private void sync() {
            if (restoring) {
                return; // this event is a byproduct of our own restoration call below, not a new change
            }
            try {
                List<Object> currentTodo = (List<Object>) todoListField.get(model);
                Collection<Object> currentDone = (Collection<Object>) doneListField.get(model);

                Set<Object> currentSet = new HashSet<>(currentTodo);
                List<Object> vanished = new ArrayList<>();
                for (Object item : knownItemsOrdered) {
                    if (!currentSet.contains(item)) {
                        vanished.add(item);
                    }
                }

                List<Object> restoreAsDone = new ArrayList<>();
                for (Object item : vanished) {
                    if (!layerStillOpen(item)) {
                        continue; // layer was removed - don't resurrect into a defunct layer
                    }
                    if (currentDone.contains(item) && Config.getPref().getBoolean(PREF_KEEP_DONE, true)) {
                        restoreAsDone.add(item); // marked done, then dropped - restore unless opted out
                    }
                }

                if (!restoreAsDone.isEmpty()) {
                    restoring = true;
                    try {
                        // Some versions' addItems() strips its argument from doneList as a side
                        // effect (or simply won't re-add something already there) - lift the
                        // marker first so restoreAsDone items go back in, then restore it after.
                        currentDone.removeAll(restoreAsDone);
                        // addItems() (the real plugin's own code) unconditionally strips doneList
                        // itself before adding to todoList and firing its own event - so for one
                        // instant, mid-call, a just-restored item genuinely has no "done" marker.
                        // The stock TitleUpdater listener reacts to that same event and displays
                        // that transient (wrong) state. Since we can't rewrite addItems() itself,
                        // temporarily unhook TitleUpdater so it simply never observes it, rather
                        // than racing to overwrite whatever it displays.
                        Object titleUpdater = suppressTitleUpdater();
                        try {
                            restore(restoreAsDone); // appends everything at the end of todoList
                        } finally {
                            unsuppressTitleUpdater(titleUpdater);
                        }
                        currentDone.addAll(restoreAsDone);
                        reinsertAtOriginalPositions(currentTodo, restoreAsDone, knownItemsOrdered);
                        advanceToNextAfter(restoreAsDone, knownItemsOrdered);
                        // The stock title ("done/total") computes total as todoList.size() +
                        // doneList.size(), assuming those never overlap - true for the stock
                        // plugin (marking done removes from todoList) but no longer true here,
                        // since a done item now deliberately stays in todoList too. Recompute
                        // and override with the correct math - both synchronously now and
                        // once more via invokeLater as a final defensive correction, in case
                        // TitleUpdater (now back in place) reacts to some later event we
                        // haven't accounted for.
                        fixTitleNow();
                        SwingUtilities.invokeLater(this::fixTitleNow);
                    } finally {
                        restoring = false;
                    }
                }

                knownItemsOrdered = new ArrayList<>((List<Object>) todoListField.get(model));
            } catch (ReflectiveOperationException | ClassCastException ex) {
                Logging.warn("BetterWorkspace: todo list sync failed: " + ex);
            }
        }

        /** Returns the model's TitleUpdater listener (unregistered), or null if none was found/removed. */
        private Object suppressTitleUpdater() {
            try {
                Object[] listeners = (Object[]) getListeners.invoke(model, ListDataListener.class);
                for (Object candidate : listeners) {
                    if ("TitleUpdater".equals(candidate.getClass().getSimpleName())) {
                        removeListDataListener.invoke(model, candidate);
                        return candidate;
                    }
                }
            } catch (ReflectiveOperationException | ClassCastException ex) {
                Logging.warn("BetterWorkspace: could not suppress the todo list's title updater: " + ex);
            }
            return null;
        }

        private void unsuppressTitleUpdater(Object titleUpdater) {
            if (titleUpdater == null) {
                return;
            }
            try {
                addListDataListener.invoke(model, titleUpdater);
            } catch (ReflectiveOperationException ex) {
                Logging.warn("BetterWorkspace: could not restore the todo list's title updater: " + ex);
            }
        }

        @SuppressWarnings("unchecked")
        private void fixTitleNow() {
            if (todoDialog == null) {
                return;
            }
            try {
                int total = ((List<Object>) todoListField.get(model)).size();
                int done = ((Collection<Object>) doneListField.get(model)).size();
                int percent = total == 0 ? 0 : (int) Math.round(100.0 * done / total);
                String summary = total == 0
                        ? I18n.tr("Todo list")
                        : I18n.tr("Todo list {0}/{1} ({2}%)", done, total, percent);
                todoDialog.setTitle(summary);
            } catch (IllegalAccessException | ClassCastException ex) {
                Logging.warn("BetterWorkspace: could not fix the todo list title: " + ex);
            }
        }

        /**
         * addItems() always appends at the end - moves each just-restored item back to (roughly)
         * where it used to sit, using the pre-removal ordering as a reference. Pure data
         * mutation (List.remove/add) on the live todoList (the caller already holds the same
         * reference, no need to re-fetch it via reflection here) - no protected AbstractListModel
         * method involved, so a plain repaint (rather than a formal ListDataEvent) is enough to
         * make the JList reflect it.
         */
        private void reinsertAtOriginalPositions(List<Object> liveTodo, List<Object> restored, List<Object> previousOrder) {
            for (Object item : restored) {
                int originalIndex = previousOrder.indexOf(item);
                if (originalIndex < 0) {
                    continue;
                }
                if (!liveTodo.remove(item)) {
                    continue;
                }
                liveTodo.add(Math.min(originalIndex, liveTodo.size()), item);
            }
            if (list != null) {
                list.repaint();
            }
        }

        /**
         * The stock plugin advances selection to "the next item" by relying on the list shrinking
         * when an item is marked done - the next item slides into the same index. Since restoring
         * keeps the list at full size (nothing actually shrinks), that trick lands on the wrong
         * row instead - typically back on the same item, or the one before it - so this does it
         * explicitly: select and zoom to whatever now sits right after the last just-marked item's
         * original position. Deferred via invokeLater because markItems() and the toolbar action
         * that called it both still run their own (now-stale) selection/zoom logic against this
         * same synchronous call stack - only after all of that finishes should this take over.
         */
        @SuppressWarnings("unchecked")
        private void advanceToNextAfter(List<Object> justMarkedDone, List<Object> previousOrder) {
            if (selectionModel == null || selectAndZoom == null) {
                return;
            }
            int lastOriginalIndex = -1;
            for (Object item : justMarkedDone) {
                lastOriginalIndex = Math.max(lastOriginalIndex, previousOrder.indexOf(item));
            }
            if (lastOriginalIndex < 0) {
                return;
            }
            int nextIndex = lastOriginalIndex + 1;
            SwingUtilities.invokeLater(() -> {
                try {
                    List<Object> liveTodo = (List<Object>) todoListField.get(model);
                    if (nextIndex < 0 || nextIndex >= liveTodo.size()) {
                        return; // the marked item(s) were at the end of the list - nothing to advance to
                    }
                    Object nextItem = liveTodo.get(nextIndex);
                    selectionModel.setSelectionInterval(nextIndex, nextIndex);
                    if (list != null) {
                        list.ensureIndexIsVisible(nextIndex);
                    }
                    selectAndZoom.invoke(null, Collections.singletonList(nextItem));
                } catch (ReflectiveOperationException | ClassCastException | IndexOutOfBoundsException ex) {
                    Logging.warn("BetterWorkspace: could not advance to the next todo item: " + ex);
                }
            });
        }

        /** Tries addItems(Collection&lt;TodoListItem&gt;) first, falls back to raw primitives on a type mismatch. */
        private void restore(List<Object> items) throws ReflectiveOperationException {
            try {
                addItems.invoke(model, items);
            } catch (InvocationTargetException ex) {
                if (!(ex.getCause() instanceof ClassCastException)) {
                    throw ex;
                }
                List<Object> primitives = new ArrayList<>(items.size());
                for (Object item : items) primitives.add(primitiveOf(item));
                addItems.invoke(model, primitives);
            }
        }

        @SuppressWarnings("unchecked")
        private void snapshot() {
            try {
                knownItemsOrdered = new ArrayList<>((List<Object>) todoListField.get(model));
            } catch (IllegalAccessException | ClassCastException ex) {
                knownItemsOrdered = new ArrayList<>();
            }
        }

        private static boolean layerStillOpen(Object todoListItem) {
            Object layer = accessorOrField(todoListItem, "layer");
            return layer != null && MainApplication.getLayerManager().getLayers().contains(layer);
        }

        private static Object primitiveOf(Object todoListItem) {
            return accessorOrField(todoListItem, "primitive");
        }

        /**
         * TodoListItem is a Java record in current todo plugin versions (private fields, public
         * no-arg accessor methods, e.g. {@code primitive()}) but a plain class with public
         * fields in older versions (including this user's own Todo_patrik fork) - try the
         * accessor method first, fall back to the field.
         *
         * <p>setAccessible(true) is required either way: TodoListItem itself is a package-private
         * class, and a public method declared on a non-public class still isn't reflectively
         * invocable from another package without it, even though the method modifier is public.
         */
        private static Object accessorOrField(Object target, String name) {
            try {
                Method accessor = target.getClass().getMethod(name);
                accessor.setAccessible(true);
                return accessor.invoke(target);
            } catch (ReflectiveOperationException ex) {
                try {
                    Field field = target.getClass().getField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ex2) {
                    return null;
                }
            }
        }
    }
}
