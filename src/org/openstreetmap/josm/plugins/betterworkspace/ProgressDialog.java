package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.I18n;

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
        return build(message, null);
    }

    /**
     * Same dialog, plus a Cancel button (also wired to the window's close button and Esc) when
     * {@code onCancel} is non-null - callers with no cancellable work keep using {@link #build}.
     */
    static JDialog build(String message, Runnable onCancel) {
        JDialog dlg = new JDialog(MainApplication.getMainFrame(), "BetterWorkspace – Please wait...", false);
        dlg.setSize(380, onCancel == null ? 110 : 140);
        dlg.setLocationRelativeTo(null);
        dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dlg.setLayout(new BorderLayout());
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, onCancel == null ? 16 : 8, 20));
        panel.add(new JLabel(message), BorderLayout.CENTER);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        panel.add(bar, BorderLayout.SOUTH);
        dlg.add(panel, BorderLayout.CENTER);
        if (onCancel != null) {
            JButton btnCancel = new JButton(I18n.tr("Cancel"));
            btnCancel.addActionListener(e -> onCancel.run());
            // The window's close button cancels too, instead of hiding the dialog while the
            // background work keeps running with nothing left visible to track or stop it.
            dlg.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    onCancel.run();
                }
            });
            dlg.getRootPane().registerKeyboardAction(e -> onCancel.run(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            JPanel btns = new JPanel();
            btns.add(btnCancel);
            dlg.add(btns, BorderLayout.SOUTH);
        }
        return dlg;
    }

    /** Updates the message of a dialog returned by {@link #build} - for callers that track multi-step progress. */
    static void setMessage(JDialog dlg, String message) {
        ((JLabel) ((JPanel) dlg.getContentPane().getComponent(0)).getComponent(0)).setText(message);
    }
}
