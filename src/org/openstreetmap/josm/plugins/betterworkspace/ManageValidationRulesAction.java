package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Shortcut;

/** Opens {@link ValidationRulesDialog} to individually enable/disable BetterWorkspace validation rules. */
final class ManageValidationRulesAction extends JosmAction {

    ManageValidationRulesAction() {
        super(I18n.tr("Manage BetterWorkspace validation rules..."), "dialogs/validator",
                I18n.tr("Enable or disable BetterWorkspace's validation rules individually"),
                Shortcut.registerShortcut("betterworkspace:managevalidationrules",
                        I18n.tr("Manage validation rules... - BetterWorkspace"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new ValidationRulesDialog().showDialog();
    }
}
