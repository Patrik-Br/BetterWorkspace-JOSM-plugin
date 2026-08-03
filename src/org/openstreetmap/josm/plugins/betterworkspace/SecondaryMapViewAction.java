package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Opens/closes a second, view-only map window (see {@link SecondaryMapViewFrame})
 * that always tracks the main map view's position and zoom, with its own
 * independent set of which layers are shown.
 */
final class SecondaryMapViewAction extends JosmAction {

    private static SecondaryMapViewFrame frame;

    SecondaryMapViewAction() {
        super(I18n.tr("Secondary Map View"), "betterworkspace/secondary-map-view",
                I18n.tr("Open or close a second, view-only map window tracking the main view's position and zoom"),
                Shortcut.registerShortcut("betterworkspace:secondarymapview",
                        I18n.tr("Secondary Map View"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                true, "betterworkspace:secondarymapview", false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (frame != null) {
            frame.dispose();
            return;
        }
        frame = new SecondaryMapViewFrame();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent evt) {
                frame = null;
            }
        });
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(MainApplication.getMap() != null);
    }

    /** Called by {@link BetterWorkspacePlugin} when the map frame goes away - the secondary view can't survive that. */
    static void closeIfOpen() {
        if (frame != null) {
            frame.dispose();
        }
    }
}
