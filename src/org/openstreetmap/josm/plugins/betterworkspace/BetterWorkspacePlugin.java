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
import org.openstreetmap.josm.plugins.betterworkspace.validation.HamletVillageTaggingMismatch;
import org.openstreetmap.josm.plugins.betterworkspace.validation.HighwayClassificationMismatch;
import org.openstreetmap.josm.plugins.betterworkspace.validation.OverlappingLanduseAreas;
import org.openstreetmap.josm.plugins.betterworkspace.validation.ResidentialMultiplePlaceNodes;
import org.openstreetmap.josm.plugins.betterworkspace.validation.ResidentialWithoutHighway;
import org.openstreetmap.josm.plugins.panelorder.ArrangePanelsDialog;
import org.openstreetmap.josm.plugins.panelorder.PanelReorderer;
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

        OsmValidator.addTest(ResidentialMultiplePlaceNodes.class);
        OsmValidator.addTest(HamletVillageTaggingMismatch.class);
        OsmValidator.addTest(HighwayClassificationMismatch.class);
        OsmValidator.addTest(ResidentialWithoutHighway.class);
        OsmValidator.addTest(OverlappingLanduseAreas.class);

        ArrangePanelsAction arrangePanels = new ArrangePanelsAction();

        JMenu bwMenu = new JMenu(I18n.tr("BetterWorkspace"));
        bwMenu.setIcon(new ImageProvider("betterworkspace/betterworkspace").get());
        bwMenu.add(new LoadTmTaskGridAction());
        bwMenu.add(new SetTmApiTokenAction());
        bwMenu.add(new ToggleActiveLayerAction());
        bwMenu.add(new MultiValidationPrepAction());
        bwMenu.add(new ManageValidationRulesAction());
        bwMenu.addSeparator();
        bwMenu.add(new QuickTmsAction());
        bwMenu.add(new LoadEsriImageryDatesAction());
        bwMenu.add(new SecondaryMapViewAction());
        bwMenu.addSeparator();
        arrangePanelsItem = bwMenu.add(arrangePanels);

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
