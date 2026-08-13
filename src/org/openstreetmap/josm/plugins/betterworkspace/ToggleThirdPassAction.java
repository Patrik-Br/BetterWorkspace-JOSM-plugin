package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.plugins.betterworkspace.validation.BwValidationConfig;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Checkbox menu action that enables or disables BetterWorkspace third-pass
 * validation rules. When disabled, third-pass tests are still registered and
 * visible in Validator Preferences but skip all checks — keeping regular
 * validation fast.
 */
final class ToggleThirdPassAction extends JosmAction {

    ToggleThirdPassAction() {
        super(I18n.tr("Enable third-pass validation rules"), null,
                I18n.tr("Toggle slow third-pass validation rules on/off"),
                Shortcut.registerShortcut("betterworkspace:thirdpass",
                        I18n.tr("Toggle third-pass validation rules"),
                        KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                false);
        putValue(Action.SELECTED_KEY, BwValidationConfig.isThirdPassEnabled());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        boolean newState = !BwValidationConfig.isThirdPassEnabled();
        BwValidationConfig.setThirdPassEnabled(newState);
        putValue(Action.SELECTED_KEY, newState);
    }
}
