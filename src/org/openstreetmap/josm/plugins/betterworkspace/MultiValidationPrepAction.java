package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Prepares the layer below the active one for a second/third mapping pass by
 * selecting all its ways and adding them to the todo plugin's list (via
 * {@link TodoBridge}), so a reviewer can page through task borders one by one.
 */
final class MultiValidationPrepAction extends JosmAction {

    MultiValidationPrepAction() {
        super(I18n.tr("Multi-validation prep (add task borders to todo)"), null,
                I18n.tr("Switch to layer below, select all ways, add to todo list, switch back."),
                Shortcut.registerShortcut("betterworkspace:multivalidationprep",
                        I18n.tr("Multi-validation prep"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MainLayerManager layerManager = MainApplication.getLayerManager();
        List<Layer> layers = layerManager.getLayers();
        Layer activeLayer = layerManager.getActiveLayer();
        if (activeLayer == null) {
            warn("No active layer.");
            return;
        }
        int belowIndex = layers.indexOf(activeLayer) + 1;
        if (belowIndex >= layers.size()) {
            warn("No layer below the active layer.");
            return;
        }
        Layer below = layers.get(belowIndex);
        if (!(below instanceof OsmDataLayer)) {
            warn("Layer below is not an OSM data layer.");
            return;
        }
        OsmDataLayer belowDataLayer = (OsmDataLayer) below;
        layerManager.setActiveLayer(belowDataLayer);
        Collection<Way> ways = belowDataLayer.getDataSet().getWays();
        belowDataLayer.getDataSet().setSelected(ways);
        boolean added = TodoBridge.addWaysToTodo(belowDataLayer, ways);
        if (!added) {
            warn("Todo plugin not found.\nPlease install todo_patrik or the standard todo plugin.");
        }
        layerManager.setActiveLayer(activeLayer);
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(MainApplication.getMainFrame(), message, "BetterWorkspace",
                JOptionPane.WARNING_MESSAGE);
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(MainApplication.getLayerManager().getActiveLayer() != null);
    }
}
