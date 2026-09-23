package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import org.openstreetmap.josm.gui.MainApplication;

/**
 * Small indeterminate "please wait" dialog shared by the plugin's network-loading actions
 * ({@link LoadTmTaskGridAction}, {@link LoadEsriImageryDatesAction}, {@link BatchDownloadAction}).
 * Callers are responsible for calling {@code setVisible(true)}/{@code dispose()} themselves
 * around their background work.
 *
 * <p>Owned by JOSM's main frame (not a bare {@code null} owner) specifically so it minimizes and
 * restores together with it, the same as JOSM's own progress dialogs (e.g. Download along) - a
 * dialog with no real owner has no such relationship on Windows, so it's left stranded behind the
 * main window after a minimize/restore cycle instead of coming back with it.
 */
final class ProgressDialog {

    private ProgressDialog() {
    }

    static JDialog build(String message) {
        JDialog dlg = new JDialog(MainApplication.getMainFrame(), "BetterWorkspace – Please wait...", false);
        dlg.setSize(380, 110);
        dlg.setLocationRelativeTo(null);
        dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        panel.add(new JLabel(message), BorderLayout.CENTER);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        panel.add(bar, BorderLayout.SOUTH);
        dlg.add(panel);
        return dlg;
    }

    /** Updates the message of a dialog returned by {@link #build} - for callers that track multi-step progress. */
    static void setMessage(JDialog dlg, String message) {
        ((JLabel) ((JPanel) dlg.getContentPane().getComponent(0)).getComponent(0)).setText(message);
    }
}
