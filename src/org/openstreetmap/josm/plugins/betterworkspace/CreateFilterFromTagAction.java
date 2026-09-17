package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;

import org.openstreetmap.josm.data.osm.Filter;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.search.SearchSetting;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.FilterDialog;
import org.openstreetmap.josm.gui.dialogs.properties.PropertiesDialog;
import org.openstreetmap.josm.gui.dialogs.properties.TagEditHelper;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

/**
 * Adds a "Create filter from Key/Value" entry to the Tags/Memberships panel's right-click menu,
 * next to JOSM's own "Search Key/Value" - builds the identical key/value search expression (via
 * the same {@code PropertiesDialog.createSearchSetting} JOSM's own action uses) but adds it as a
 * new row in the Filters panel instead of running a search.
 *
 * <p>{@code PropertiesDialog.getPropertyPopupMenuHandler()} is a genuine public JOSM extension
 * point - other plugins already add entries to this exact menu the same way - so no reflection
 * is needed to add the menu entry itself. Reflection is only needed for two things JOSM keeps
 * private: reading which tag row is currently selected (the table and its edit helper), and
 * calling {@code createSearchSetting} itself, which is package-private.
 */
final class CreateFilterFromTagAction extends AbstractAction {

    private final PropertiesDialog propertiesDialog;

    private CreateFilterFromTagAction(PropertiesDialog propertiesDialog) {
        super(I18n.tr("Create filter from Key/Value"));
        this.propertiesDialog = propertiesDialog;
        putValue(SHORT_DESCRIPTION,
                I18n.tr("Add a new Filters-panel entry matching the key and value of the selected tag"));
        // Same icon as the Filters panel itself (images/dialogs/filter.svg in JOSM core).
        new ImageProvider("dialogs/filter").getResource().attachImageIcon(this, true);
    }

    static void install(MapFrame mapFrame) {
        if (mapFrame == null || mapFrame.propertiesDialog == null) {
            return;
        }
        PropertiesDialog propertiesDialog = mapFrame.propertiesDialog;
        JMenuItem item = propertiesDialog.getPropertyPopupMenuHandler()
                .addAction(new CreateFilterFromTagAction(propertiesDialog));
        repositionAfterSearchKeyValueType(propertiesDialog, item);
    }

    /**
     * addAction() always appends at the menu's current end, which at install time lands right
     * after JOSM's own built-in items - Search Key/Value/Type included. Moves it up to sit
     * directly under that one instead, grouping it with the other "build an expression from this
     * tag" actions rather than down among the Taginfo/wiki links.
     *
     * <p>PopupMenuHandler doesn't expose the underlying JPopupMenu, so this reflects into
     * PropertiesDialog's own private {@code tagMenu} field instead - left wherever addAction put
     * it (still fully functional, just lower in the menu) if that ever stops matching a future
     * JOSM version.
     */
    private static void repositionAfterSearchKeyValueType(PropertiesDialog propertiesDialog, JMenuItem item) {
        try {
            Field tagMenuField = PropertiesDialog.class.getDeclaredField("tagMenu");
            tagMenuField.setAccessible(true);
            JPopupMenu tagMenu = (JPopupMenu) tagMenuField.get(propertiesDialog);
            String targetText = I18n.tr("Search Key/Value/Type");
            for (int i = 0; i < tagMenu.getComponentCount(); i++) {
                Component c = tagMenu.getComponent(i);
                if (c instanceof JMenuItem && targetText.equals(((JMenuItem) c).getText())) {
                    tagMenu.remove(item);
                    tagMenu.add(item, i + 1);
                    return;
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ex) {
            Logging.warn("BetterWorkspace: could not reposition the create-filter menu item: " + ex);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Field tagTableField = PropertiesDialog.class.getDeclaredField("tagTable");
            tagTableField.setAccessible(true);
            JTable tagTable = (JTable) tagTableField.get(propertiesDialog);
            if (tagTable.getSelectedRowCount() != 1) {
                return; // matches JOSM's own Search Key/Value, which requires exactly one row too
            }

            Field editHelperField = PropertiesDialog.class.getDeclaredField("editHelper");
            editHelperField.setAccessible(true);
            TagEditHelper editHelper = (TagEditHelper) editHelperField.get(propertiesDialog);
            String key = editHelper.getDataKey(tagTable.getSelectedRow());

            Collection<? extends IPrimitive> selection = OsmDataManager.getInstance().getInProgressISelection();
            if (selection.isEmpty()) {
                return;
            }

            Method createSearchSetting = PropertiesDialog.class.getDeclaredMethod(
                    "createSearchSetting", String.class, Collection.class, boolean.class);
            createSearchSetting.setAccessible(true);
            SearchSetting setting = (SearchSetting) createSearchSetting.invoke(null, key, selection, false);

            Filter filter = new Filter(setting);
            filter.enable = true;

            MapFrame mapFrame = MainApplication.getMap();
            FilterDialog filterDialog = mapFrame == null ? null : mapFrame.getToggleDialog(FilterDialog.class);
            if (filterDialog == null) {
                return;
            }
            filterDialog.getFilterModel().addFilter(filter);
            if (!filterDialog.isDialogShowing()) {
                filterDialog.unfurlDialog();
            }
        } catch (ReflectiveOperationException | ClassCastException ex) {
            Logging.warn("BetterWorkspace: could not create a filter from the selected tag: " + ex);
        }
    }
}
