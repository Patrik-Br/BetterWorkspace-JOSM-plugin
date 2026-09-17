package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.DialogsPanel;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.betterworkspace.validation.BuildingOverlappingResidentialArea;
import org.openstreetmap.josm.plugins.betterworkspace.validation.HamletVillageTaggingMismatch;
import org.openstreetmap.josm.plugins.betterworkspace.validation.HighwayClassificationMismatch;
import org.openstreetmap.josm.plugins.betterworkspace.validation.OverlappingLanduseAreas;
import org.openstreetmap.josm.plugins.betterworkspace.validation.ResidentialMultiplePlaceNodes;
import org.openstreetmap.josm.plugins.betterworkspace.validation.ResidentialWithoutHighway;
import org.openstreetmap.josm.plugins.panelorder.ArrangePanelsDialog;
import org.openstreetmap.josm.plugins.panelorder.PanelReorderer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Entry point. Builds the "More tools -&gt; BetterWorkspace" menu.
 */
public class BetterWorkspacePlugin extends Plugin {

    private final JMenuItem arrangePanelsItem;

    public BetterWorkspacePlugin(PluginInformation info) {
        super(info);

        safely("residential-multiple-place-nodes validator", () -> OsmValidator.addTest(ResidentialMultiplePlaceNodes.class));
        safely("hamlet/village tagging mismatch validator", () -> OsmValidator.addTest(HamletVillageTaggingMismatch.class));
        safely("highway classification mismatch validator", () -> OsmValidator.addTest(HighwayClassificationMismatch.class));
        safely("residential without highway validator", () -> OsmValidator.addTest(ResidentialWithoutHighway.class));
        safely("overlapping landuse areas validator", () -> OsmValidator.addTest(OverlappingLanduseAreas.class));
        safely("building overlaps residential landuse validator", () -> OsmValidator.addTest(BuildingOverlappingResidentialArea.class));

        // Primes the preference so it shows up in Preferences -> Advanced Preferences right
        // away, rather than only appearing the first time TodoBehaviorSync actually reads it.
        safely("todo-keep-done preference priming", () -> Config.getPref().getBoolean(TodoBehaviorSync.PREF_KEEP_DONE, true));

        JMenu bwMenu = new JMenu(I18n.tr("BetterWorkspace"));
        bwMenu.setIcon(new ImageProvider("betterworkspace/betterworkspace").get());
        safely("Load task grid menu item", () -> bwMenu.add(new LoadTmTaskGridAction()));
        safely("Set TM API token menu item", () -> bwMenu.add(new SetTmApiTokenAction()));
        safely("Toggle active layer menu item", () -> bwMenu.add(new ToggleActiveLayerAction()));
        safely("Multi validation prep menu item", () -> bwMenu.add(new MultiValidationPrepAction()));
        safely("Manage validation rules menu item", () -> bwMenu.add(new ManageValidationRulesAction()));
        bwMenu.addSeparator();
        safely("Quick TMS menu item", () -> bwMenu.add(new QuickTmsAction()));
        safely("Load Esri imagery dates menu item", () -> bwMenu.add(new LoadEsriImageryDatesAction()));
        safely("Secondary map view menu item", () -> bwMenu.add(new SecondaryMapViewAction()));
        bwMenu.addSeparator();

        JMenuItem arrangePanelsMenuItem = null;
        try {
            arrangePanelsMenuItem = bwMenu.add(new ArrangePanelsAction());
        } catch (RuntimeException | LinkageError e) {
            Logging.warn("BetterWorkspace: Arrange side panels menu item failed to initialize");
            Logging.warn(e);
        }
        arrangePanelsItem = arrangePanelsMenuItem;

        // Deferred: JOSM core creates "More tools" empty and hidden (MainMenu.initialize()
        // calls moreToolsMenu.setVisible(false)) - it only becomes visible in practice
        // because plugins like utilsplugin2/buildings_tools populate it. Attaching here via
        // invokeLater (instead of directly, now, in the constructor) means our submenu is
        // added only after every plugin's own (synchronous) constructor-time menu setup has
        // already run, so BetterWorkspace reliably lands at the bottom of the list, and we
        // explicitly show the menu ourselves so it still works with neither of those plugins
        // installed.
        SwingUtilities.invokeLater(() -> safely("attaching BetterWorkspace menu to More tools", () -> {
            JMenu moreTools = MainApplication.getMenu().moreToolsMenu;
            moreTools.add(bwMenu);
            moreTools.setVisible(true);
        }));
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (arrangePanelsItem != null) {
            arrangePanelsItem.setEnabled(newFrame != null);
        }
        if (newFrame != null) {
            safely("saved panel order", () -> applySavedOrderWhenReady(newFrame, 20));
            safely("author-select hook", () -> AuthorSelectHook.installWhenReady(newFrame, 20));
            // up to 30s - this user's JOSM loads 70+ plugins
            safely("todo behavior sync", () -> TodoBehaviorSync.installWhenReady(newFrame, 120));
            safely("create-filter-from-tag menu hook", () -> CreateFilterFromTagAction.install(newFrame));
        } else {
            safely("secondary map view close", SecondaryMapViewAction::closeIfOpen);
        }
    }

    // Isolates one startup step so a bug or a JOSM-core API change in it is logged and skipped
    // instead of aborting the rest of BetterWorkspace's setup.
    private static void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            Logging.warn("BetterWorkspace: " + what + " failed to initialize");
            Logging.warn(e);
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
