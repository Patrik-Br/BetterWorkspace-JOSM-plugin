package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.net.URI;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingConstants;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Lets the user paste their HOT Tasking Manager personal API token, needed by
 * {@link LoadTmTaskGridAction} to load task grids for private/draft projects.
 */
final class SetTmApiTokenAction extends JosmAction {

    private static final String TM_SETTINGS_URL = "https://tasks.hotosm.org/settings";

    SetTmApiTokenAction() {
        super(I18n.tr("Set HOT TM API Token..."), "betterworkspace/tm-set-token",
                I18n.tr("Save your HOT Tasking Manager personal API token, used to load private/draft task grids"),
                Shortcut.registerShortcut("betterworkspace:tmsettoken",
                        I18n.tr("Set HOT TM API Token..."), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                true, "betterworkspace:tmsettoken", false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JPanel panel = new JPanel(new BorderLayout(6, 10));

        JLabel instructions = new JLabel("<html>Get your token from the HOT Tasking Manager website:<br>"
                + "<b>" + TM_SETTINGS_URL + "</b> &rarr; enable <b>Expert mode</b> &rarr; copy the <b>API Key</b>.<br>"
                + "Pasting the copied \"Token xxx\" text or just the token itself both work.<br>"
                + "The token expires roughly 7 days after your last TM login.</html>");
        panel.add(instructions, BorderLayout.NORTH);

        JPasswordField field = new JPasswordField(TmApiToken.get(), 40);
        panel.add(field, BorderLayout.CENTER);

        JLabel openLink = new JLabel("<html><a href=\"\">Open HOT TM settings in browser</a></html>");
        openLink.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        openLink.setHorizontalAlignment(SwingConstants.LEFT);
        openLink.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                try {
                    Desktop.getDesktop().browse(new URI(TM_SETTINGS_URL));
                } catch (Exception ex) {
                    Logging.warn(ex);
                }
            }
        });
        panel.add(openLink, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(null, panel, "BetterWorkspace – HOT TM API Token",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            TmApiToken.set(new String(field.getPassword()));
        }
    }
}
