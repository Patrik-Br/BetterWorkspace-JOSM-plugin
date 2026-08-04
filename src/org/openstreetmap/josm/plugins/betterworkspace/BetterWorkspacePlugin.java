package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.DialogsPanel;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.panelorder.ArrangePanelsDialog;
import org.openstreetmap.josm.plugins.panelorder.PanelReorderer;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Entry point. Builds the "More tools -&gt; BetterWorkspace" menu and wires up
 * the plugin's map-view-rotation state, which several inner actions below
 * share via {@link #applyRotation(double)}/{@link #currentTheta()}.
 */
public class BetterWorkspacePlugin extends Plugin {

    private static final double ROTATE_STEP_DEG = 15.0;

    private final JMenuItem arrangePanelsItem;

    public BetterWorkspacePlugin(PluginInformation info) {
        super(info);

        RotateAction rotateCw = new RotateAction("betterworkspace:rotate-cw",
                I18n.tr("Rotate view clockwise"), "betterworkspace/rotate-cw", -ROTATE_STEP_DEG);
        RotateAction rotateCcw = new RotateAction("betterworkspace:rotate-ccw",
                I18n.tr("Rotate view counter-clockwise"), "betterworkspace/rotate-ccw", ROTATE_STEP_DEG);
        ResetAction reset = new ResetAction();
        ArrangePanelsAction arrangePanels = new ArrangePanelsAction();

        JMenu bwMenu = new JMenu(I18n.tr("BetterWorkspace"));
        bwMenu.setIcon(new ImageProvider("betterworkspace/betterworkspace").get());
        arrangePanelsItem = bwMenu.add(arrangePanels);
        bwMenu.addSeparator();
        bwMenu.add(new LoadTmTaskGridAction());
        bwMenu.add(new SetTmApiTokenAction());
        bwMenu.add(new ToggleActiveLayerAction());
        bwMenu.add(new MultiValidationPrepAction());
        bwMenu.addSeparator();
        bwMenu.add(new QuickTmsAction());
        bwMenu.add(new LoadEsriImageryDatesAction());
        bwMenu.add(new SecondaryMapViewAction());
        bwMenu.add(rotateCw);
        bwMenu.add(rotateCcw);
        bwMenu.add(reset);

        // Deferred: JOSM core creates "More tools" empty and hidden (MainMenu.initialize()
        // calls moreToolsMenu.setVisible(false)) - it only becomes visible in practice
        // because plugins like utilsplugin2/buildings_tools populate it. Attaching here via
        // invokeLater (instead of directly, now, in the constructor) means our submenu is
        // added only after every plugin's own (synchronous) constructor-time menu setup has
        // already run, so BetterWorkspace reliably lands at the bottom of the list, and we
        // explicitly show the menu ourselves so it still works with neither of those plugins
        // installed.
        SwingUtilities.invokeLater(() -> {
            JMenu moreTools = MainApplication.getMenu().moreToolsMenu;
            moreTools.add(bwMenu);
            moreTools.setVisible(true);
        });
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (arrangePanelsItem != null) {
            arrangePanelsItem.setEnabled(newFrame != null);
        }
        if (newFrame != null) {
            applySavedOrderWhenReady(newFrame, 20);
            AuthorSelectHook.installWhenReady(newFrame, 20);
        } else {
            SecondaryMapViewAction.closeIfOpen();
        }
    }

    private void applySavedOrderWhenReady(MapFrame mapFrame, int retriesLeft) {
        Timer timer = new Timer(250, null);
        timer.addActionListener(e -> {
            timer.stop();
            if (MainApplication.getMap() != mapFrame) {
                return;
            }
            DialogsPanel dialogsPanel = PanelReorderer.findDialogsPanel((Container) mapFrame);
            if (dialogsPanel != null && dialogsPanel.initialized) {
                PanelReorderer.applySavedOrder(mapFrame);
                Logging.debug("BetterWorkspace: saved panel order applied");
            } else if (retriesLeft > 0) {
                applySavedOrderWhenReady(mapFrame, retriesLeft - 1);
            } else {
                Logging.warn("BetterWorkspace: dialogs panel never became ready, giving up");
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    static void applyRotation(double theta) {
        MapFrame mapFrame = MainApplication.getMap();
        if (mapFrame == null || mapFrame.mapView == null) {
            return;
        }
        MapView mapView = mapFrame.mapView;
        Projection currentProjection = ProjectionRegistry.getProjection();
        Projection baseProjection = currentProjection instanceof RotatingProjection
                ? ((RotatingProjection) currentProjection).getUnderlyingProjection()
                : currentProjection;

        EastNorth center = mapView.getCenter();
        double scale = mapView.getScale();
        LatLon centerLatLon = currentProjection.eastNorth2latlon(center);

        Projection newProjection;
        if (theta == 0.0) {
            newProjection = baseProjection;
        } else {
            EastNorth pivot = baseProjection.latlon2eastNorth(centerLatLon);
            newProjection = new RotatingProjection(baseProjection, theta, pivot);
        }

        ProjectionRegistry.setProjection(newProjection);
        try {
            mapView.zoomTo(newProjection.latlon2eastNorth(centerLatLon), scale);
        } catch (RuntimeException ex) {
            Logging.warn(ex);
        }
        mapView.repaint();
    }

    static double currentTheta() {
        Projection projection = ProjectionRegistry.getProjection();
        return projection instanceof RotatingProjection ? ((RotatingProjection) projection).getTheta() : 0.0;
    }

    private static final class RotateAction extends JosmAction {
        private final double deltaDeg;

        RotateAction(String toolbarId, String text, String iconName, double deltaDeg) {
            super(text, iconName, text,
                    Shortcut.registerShortcut(toolbarId, text, KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    true, toolbarId, false);
            this.deltaDeg = deltaDeg;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            applyRotation(currentTheta() + Math.toRadians(deltaDeg));
        }
    }

    private static final class ResetAction extends JosmAction {
        ResetAction() {
            super(I18n.tr("Reset view rotation"), "betterworkspace/reset-north", I18n.tr("Reset view rotation"),
                    Shortcut.registerShortcut("betterworkspace:reset", I18n.tr("Reset view rotation"),
                            KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    true, "betterworkspace:reset", false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            applyRotation(0.0);
        }
    }

    private static final class ArrangePanelsAction extends JosmAction {
        ArrangePanelsAction() {
            super(I18n.tr("Arrange side panels..."), "betterworkspace/arrange-panels",
                    I18n.tr("Change the top-to-bottom order of the panels docked on the right side"),
                    Shortcut.registerShortcut("betterworkspace:arrangepanels",
                            I18n.tr("Arrange side panels..."), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    true, "betterworkspace:arrangepanels", false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            MapFrame mapFrame = MainApplication.getMap();
            if (mapFrame == null) {
                return;
            }
            new ArrangePanelsDialog(mapFrame).showAndApply(mapFrame);
        }
    }
}
