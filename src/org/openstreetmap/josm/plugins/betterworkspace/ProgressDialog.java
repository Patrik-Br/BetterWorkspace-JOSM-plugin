package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.BorderLayout;
import java.awt.Frame;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/**
 * Small indeterminate "please wait" dialog shared by the plugin's network-loading actions
 * ({@link LoadTmTaskGridAction}, {@link LoadEsriImageryDatesAction}). Callers are responsible for
 * calling {@code setVisible(true)}/{@code dispose()} themselves around their background work.
 */
final class ProgressDialog {

    private ProgressDialog() {
    }

    static JDialog build(String message) {
        JDialog dlg = new JDialog((Frame) null, "BetterWorkspace – Please wait...", false);
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
}
