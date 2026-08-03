package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Opens {@link QuickTmsDialog} to preview a TMS imagery layer for this session only.
 */
final class QuickTmsAction extends JosmAction {

    QuickTmsAction() {
        super(I18n.tr("Quick TMS..."), "betterworkspace/quick-tms",
                I18n.tr("Preview a TMS imagery layer for this session only, without saving it to your imagery list"),
                Shortcut.registerShortcut("betterworkspace:quicktms",
                        I18n.tr("Quick TMS..."), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                true, "betterworkspace:quicktms", false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new QuickTmsDialog().showAndCreate();
    }
}
