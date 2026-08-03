/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.openstreetmap.josm.actions.JosmAction
 *  org.openstreetmap.josm.data.coor.EastNorth
 *  org.openstreetmap.josm.data.coor.LatLon
 *  org.openstreetmap.josm.data.projection.Projection
 *  org.openstreetmap.josm.data.projection.ProjectionRegistry
 *  org.openstreetmap.josm.gui.MainApplication
 *  org.openstreetmap.josm.gui.MapFrame
 *  org.openstreetmap.josm.gui.MapView
 *  org.openstreetmap.josm.gui.dialogs.DialogsPanel
 *  org.openstreetmap.josm.plugins.Plugin
 *  org.openstreetmap.josm.plugins.PluginInformation
 *  org.openstreetmap.josm.tools.I18n
 *  org.openstreetmap.josm.tools.Logging
 *  org.openstreetmap.josm.tools.Shortcut
 */
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
import org.openstreetmap.josm.plugins.betterworkspace.RotatingProjection;
import org.openstreetmap.josm.plugins.panelorder.ArrangePanelsDialog;
import org.openstreetmap.josm.plugins.panelorder.PanelReorderer;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

public class BetterWorkspacePlugin
extends Plugin {
    private static final double STEP_DEG = 15.0;
    private final JMenuItem arrangePanelsItem;

    public BetterWorkspacePlugin(PluginInformation pluginInformation) {
        super(pluginInformation);
        RotateAction rotateCw = new RotateAction("betterworkspace:rotate-cw", I18n.tr((String)"Rotate view clockwise", (Object[])new Object[0]), "betterworkspace/rotate-cw", -15.0);
        RotateAction rotateCcw = new RotateAction("betterworkspace:rotate-ccw", I18n.tr((String)"Rotate view counter-clockwise", (Object[])new Object[0]), "betterworkspace/rotate-ccw", 15.0);
        ResetAction reset = new ResetAction();
        ArrangePanelsAction arrangePanels = new ArrangePanelsAction();

        JMenu bwMenu = new JMenu(I18n.tr((String)"BetterWorkspace", (Object[])new Object[0]));
        bwMenu.setIcon(new ImageProvider("betterworkspace/betterworkspace").get());
        this.arrangePanelsItem = bwMenu.add(arrangePanels);
        bwMenu.addSeparator();
        bwMenu.add(new LoadTmTaskGridAction());
        bwMenu.add(new SetTmApiTokenAction());
        bwMenu.add(new ToggleActiveLayerAction());
        bwMenu.add(new MultiValidationPrepAction());
        bwMenu.addSeparator();
        bwMenu.add(new QuickTmsAction());
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

    public void mapFrameInitialized(MapFrame mapFrame, MapFrame mapFrame2) {
        if (this.arrangePanelsItem != null) {
            this.arrangePanelsItem.setEnabled(mapFrame2 != null);
        }
        if (mapFrame2 != null) {
            this.applySavedOrderWhenReady(mapFrame2, 20);
            AuthorSelectHook.installWhenReady(mapFrame2, 20);
        } else {
            SecondaryMapViewAction.closeIfOpen();
        }
    }

    private void applySavedOrderWhenReady(MapFrame mapFrame, int n) {
        Timer timer = new Timer(250, null);
        timer.addActionListener(actionEvent -> {
            timer.stop();
            if (MainApplication.getMap() != mapFrame) {
                return;
            }
            DialogsPanel dialogsPanel = PanelReorderer.findDialogsPanel((Container)mapFrame);
            if (dialogsPanel != null && dialogsPanel.initialized) {
                PanelReorderer.applySavedOrder(mapFrame);
                Logging.debug((String)"BetterWorkspace: saved panel order applied");
            } else if (n > 0) {
                this.applySavedOrderWhenReady(mapFrame, n - 1);
            } else {
                Logging.warn((String)"BetterWorkspace: dialogs panel never became ready, giving up");
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    static void applyRotation(double d) {
        Projection projection;
        MapFrame mapFrame = MainApplication.getMap();
        if (mapFrame == null || mapFrame.mapView == null) {
            return;
        }
        MapView mapView = mapFrame.mapView;
        Projection projection2 = ProjectionRegistry.getProjection();
        Projection projection3 = projection2 instanceof RotatingProjection ? ((RotatingProjection)projection2).getUnderlyingProjection() : projection2;
        EastNorth eastNorth = mapView.getCenter();
        double d2 = mapView.getScale();
        LatLon latLon = projection2.eastNorth2latlon(eastNorth);
        if (d == 0.0) {
            projection = projection3;
        } else {
            EastNorth eastNorth2 = projection3.latlon2eastNorth(latLon);
            projection = new RotatingProjection(projection3, d, eastNorth2);
        }
        ProjectionRegistry.setProjection((Projection)projection);
        try {
            mapView.zoomTo(projection.latlon2eastNorth(latLon), d2);
        }
        catch (RuntimeException runtimeException) {
            Logging.warn((Throwable)runtimeException);
        }
        mapView.repaint();
    }

    static double currentTheta() {
        Projection projection = ProjectionRegistry.getProjection();
        return projection instanceof RotatingProjection ? ((RotatingProjection)projection).getTheta() : 0.0;
    }

    private static final class RotateAction
    extends JosmAction {
        private final double deltaDeg;

        RotateAction(String string, String string2, String string3, double d) {
            super(string2, string3, string2, Shortcut.registerShortcut((String)string, (String)string2, (int)65535, (int)5000), true, string, false);
            this.deltaDeg = d;
        }

        public void actionPerformed(ActionEvent actionEvent) {
            BetterWorkspacePlugin.applyRotation(BetterWorkspacePlugin.currentTheta() + Math.toRadians(this.deltaDeg));
        }
    }

    private static final class ResetAction
    extends JosmAction {
        ResetAction() {
            super(I18n.tr((String)"Reset view rotation", (Object[])new Object[0]), "betterworkspace/reset-north", I18n.tr((String)"Reset view rotation", (Object[])new Object[0]), Shortcut.registerShortcut((String)"betterworkspace:reset", (String)I18n.tr((String)"Reset view rotation", (Object[])new Object[0]), (int)65535, (int)5000), true, "betterworkspace:reset", false);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            BetterWorkspacePlugin.applyRotation(0.0);
        }
    }

    private static final class ArrangePanelsAction
    extends JosmAction {
        ArrangePanelsAction() {
            super(I18n.tr((String)"Arrange side panels...", (Object[])new Object[0]), "betterworkspace/arrange-panels",
                    I18n.tr((String)"Change the top-to-bottom order of the panels docked on the right side", (Object[])new Object[0]),
                    Shortcut.registerShortcut("betterworkspace:arrangepanels",
                            I18n.tr((String)"Arrange side panels...", (Object[])new Object[0]), (int)KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    true, "betterworkspace:arrangepanels", false);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            MapFrame mapFrame = MainApplication.getMap();
            if (mapFrame == null) {
                return;
            }
            new ArrangePanelsDialog(mapFrame).showAndApply(mapFrame);
        }
    }
}

