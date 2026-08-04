package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.GeoJSONReader;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Loads a HOT Tasking Manager project's task grid as a data layer, the same
 * content as JOSM's File -&gt; Open Location with the TM "as_file=true&amp;
 * format=geojson" task-grid URL, except this also attaches the user's TM API
 * token (see {@link TmApiToken}/{@link SetTmApiTokenAction}) as an
 * Authorization header, so it also works for private and draft projects the
 * user has access to - something Open Location can't do since it has no way
 * to add custom headers to the request.
 */
final class LoadTmTaskGridAction extends JosmAction {

    private static final String TM_API = "https://tasking-manager-production-api.hotosm.org/api/v2";
    private static final String PREF_LAST_PROJECT_ID = "betterworkspace.tm.lastprojectid";

    LoadTmTaskGridAction() {
        super(I18n.tr("Load HOT TM Task Grid..."), "betterworkspace/tm-load-taskgrid",
                I18n.tr("Load a HOT Tasking Manager project's task grid as a data layer, including private/draft projects"),
                Shortcut.registerShortcut("betterworkspace:tmloadtaskgrid",
                        I18n.tr("Load HOT TM Task Grid..."), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                true, "betterworkspace:tmloadtaskgrid", false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String lastId = Config.getPref().get(PREF_LAST_PROJECT_ID, "");
        String input = (String) JOptionPane.showInputDialog(null,
                I18n.tr("HOT Tasking Manager project ID:"),
                I18n.tr("BetterWorkspace – Load Task Grid"), JOptionPane.QUESTION_MESSAGE,
                null, null, lastId);
        if (input == null) {
            return;
        }
        int projectId;
        try {
            projectId = Integer.parseInt(input.trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, I18n.tr("Not a valid project ID: {0}", input),
                    "BetterWorkspace", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Config.getPref().put(PREF_LAST_PROJECT_ID, String.valueOf(projectId));

        final int id = projectId;
        final JDialog progress = progressDialog(I18n.tr("Loading task grid for project #{0}...", String.valueOf(id)));
        SwingWorker<DataSet, Void> worker = new SwingWorker<DataSet, Void>() {
            private String errorMessage;

            @Override
            protected DataSet doInBackground() {
                try {
                    return fetchTaskGrid(id);
                } catch (Exception ex) {
                    Logging.warn(ex);
                    errorMessage = ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                progress.dispose();
                DataSet dataSet;
                try {
                    dataSet = get();
                } catch (Exception ex) {
                    Logging.warn(ex);
                    dataSet = null;
                }
                if (dataSet == null) {
                    JOptionPane.showMessageDialog(null,
                            I18n.tr("Failed to load task grid for project #{0}:\n{1}", String.valueOf(id), errorMessage),
                            "BetterWorkspace", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                MainApplication.getLayerManager().addLayer(
                        new OsmDataLayer(dataSet, I18n.tr("TM Task Grid #{0}", String.valueOf(id)), null));
            }
        };
        progress.setVisible(true);
        worker.execute();
    }

    private static JDialog progressDialog(String message) {
        JDialog dlg = new JDialog((java.awt.Frame) null, "BetterWorkspace – Please wait...", false);
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

    private static DataSet fetchTaskGrid(int projectId) throws IOException {
        String urlStr = TM_API + "/projects/" + projectId + "/tasks/?as_file=true&format=geojson";
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "BetterWorkspace-JOSMPlugin/1.0.1");
        String auth = TmApiToken.authorizationHeader();
        if (auth != null) {
            conn.setRequestProperty("Authorization", auth);
        }
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        int code = conn.getResponseCode();
        if (code == 403) {
            throw new IOException(auth == null
                    ? "Access denied (HTTP 403). This is likely a private or draft project – "
                      + "set your HOT TM API token via More tools → BetterWorkspace → Set HOT TM API Token..."
                    : "Access denied (HTTP 403). Your HOT TM API token may be missing, wrong, expired, "
                      + "or you may not have access to this project.");
        }
        if (code == 404) {
            throw new IOException("Project #" + projectId + " not found.");
        }
        if (code != 200) {
            throw new IOException("TM API returned HTTP " + code);
        }

        try (InputStream in = conn.getInputStream()) {
            return GeoJSONReader.parseDataSet(in, NullProgressMonitor.INSTANCE);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Could not parse task grid GeoJSON: " + ex.getMessage(), ex);
        }
    }
}
