package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Ported from 3rdPassJOSMPlugin ("ThirdPassMM"): toggles the visibility of
 * whichever layer is currently active, without needing to find it in the
 * Layers panel.
 */
final class ToggleActiveLayerAction extends JosmAction {

    ToggleActiveLayerAction() {
        super(I18n.tr("Toggle active layer visibility"), null,
                I18n.tr("Toggle the visibility of the currently active layer."),
                Shortcut.registerShortcut("betterworkspace:togglelayer",
                        I18n.tr("Toggle active layer visibility"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Layer layer = MainApplication.getLayerManager().getActiveLayer();
        if (layer != null) {
            layer.setVisible(!layer.isVisible());
        }
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(MainApplication.getLayerManager().getActiveLayer() != null);
    }
}
