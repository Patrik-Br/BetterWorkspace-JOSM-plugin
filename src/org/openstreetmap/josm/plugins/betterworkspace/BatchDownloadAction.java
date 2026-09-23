package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Downloads OSM data around every selected feature (or every feature in the active layer if none
 * are selected) into a fresh layer - the same idea as the separate josm-batch-downloader plugin
 * (https://gitlab.com/Jamalek/josm-batch-downloader), most commonly used after loading a HOT TM
 * task grid: select some task squares, then batch-download real data for just those areas. No
 * dialog - it runs immediately with no buffer and always into a new layer.
 *
 * The one deliberate difference from that plugin: each per-feature download runs with
 * {@link NullProgressMonitor} instead of a {@code null} monitor. A {@code null} monitor makes JOSM
 * fall back to its own PleaseWaitProgressMonitor popup for every single download, repeatedly
 * grabbing focus/bringing the main window forward - with one download per feature that means
 * constant focus-stealing for the whole run. NullProgressMonitor has no UI at all, so only this
 * action's own status dialog is shown and you can keep using JOSM (or anything else) while it runs.
 */
final class BatchDownloadAction extends JosmAction {

    private static final int MANY_FEATURES_WARNING_THRESHOLD = 100;

    BatchDownloadAction() {
        super(I18n.tr("Batch download v2"), "betterworkspace/batch-download",
                I18n.tr("Downloads OSM data into a new layer around every selected feature, or every feature "
                        + "in the active layer if none are selected"),
                Shortcut.registerShortcut("betterworkspace:batchdownload",
                        I18n.tr("Batch download v2 - BetterWorkspace"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                true, "betterworkspace:batchdownload", false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) {
            JOptionPane.showMessageDialog(null, I18n.tr("No active data layer with features to download around."),
                    "BetterWorkspace", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<OsmPrimitive> features = collectFeatures(ds, true);
        if (features.isEmpty()) features = collectFeatures(ds, false);
        if (features.isEmpty()) {
            JOptionPane.showMessageDialog(null, I18n.tr("The active layer has no features to download around."),
                    "BetterWorkspace", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (features.size() > MANY_FEATURES_WARNING_THRESHOLD) {
            // Same rough heuristic as the original josm-batch-downloader plugin (one download per
            // ~100 features takes about a minute) - not remotely precise (real time depends on
            // feature size, spacing, and connection speed), just enough to set expectations.
            int estimatedMinutes = features.size() / 100;
            int choice = JOptionPane.showConfirmDialog(null,
                    "You're about to start " + features.size() + " separate downloads (~" + estimatedMinutes
                    + " minute" + (estimatedMinutes == 1 ? "" : "s") + ", very roughly - depends heavily on "
                    + "feature size, spacing and your connection). Continue?",
                    "BetterWorkspace – Many features", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        startDownload(features);
    }

    // =====================================================================
    //  Feature collection (mirrors josm-batch-downloader: selected features, or all if none
    //  selected; a node already part of a selected/candidate way or relation is skipped, since
    //  downloading around it individually would just be redundant)
    // =====================================================================

    private static List<OsmPrimitive> collectFeatures(DataSet ds, boolean selectedOnly) {
        Collection<OsmPrimitive> candidates = ds.getPrimitives(p -> !selectedOnly || p.isSelected());
        Set<OsmPrimitive> candidateSet = new HashSet<>(candidates);
        List<OsmPrimitive> result = new ArrayList<>();
        for (OsmPrimitive p : candidates) {
            if (p instanceof Node) {
                boolean partOfOther = false;
                for (OsmPrimitive referrer : p.getReferrers()) {
                    if (candidateSet.contains(referrer)) { partOfOther = true; break; }
                }
                if (partOfOther) continue;
            }
            result.add(p);
        }
        return result;
    }

    // =====================================================================
    //  Download: one feature at a time, silently, into a fresh layer (see class javadoc)
    // =====================================================================

    private void startDownload(List<OsmPrimitive> features) {
        OsmDataLayer layer = new OsmDataLayer(new DataSet(), I18n.tr("Batch downloaded"), null);
        MainApplication.getLayerManager().addLayer(layer);
        MainApplication.getLayerManager().setActiveLayer(layer);

        BatchState state = new BatchState();
        JDialog prog = ProgressDialog.build("Downloading feature 1 of " + features.size() + "...", state::cancel);
        prog.setVisible(true);
        downloadNext(prog, features, 0, state);
    }

    private void downloadNext(JDialog prog, List<OsmPrimitive> features, int index, BatchState state) {
        if (state.cancelled) {
            prog.dispose();
            JOptionPane.showMessageDialog(null,
                    "Download cancelled after " + index + " of " + features.size() + " feature(s).\n\n"
                    + "The data downloaded so far is kept in the new layer.",
                    "BetterWorkspace", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (index >= features.size()) {
            prog.dispose();
            return;
        }
        ProgressDialog.setMessage(prog, "Downloading feature " + (index + 1) + " of " + features.size() + "...");
        Bounds bounds = boundsOf(features.get(index).getBBox());
        DownloadOsmTask task = new DownloadOsmTask();
        state.currentDownload = task;
        Future<?> future = task.download(new DownloadParams(), bounds, NullProgressMonitor.INSTANCE);
        MainApplication.worker.submit(() -> {
            try { future.get(); } catch (Exception ignored) {}
            SwingUtilities.invokeLater(() -> {
                state.currentDownload = null;
                downloadNext(prog, features, index + 1, state);
            });
        });
    }

    private static Bounds boundsOf(BBox bbox) {
        return new Bounds(bbox.getMinLat(), bbox.getMinLon(), bbox.getMaxLat(), bbox.getMaxLon());
    }

    /** Shared state of one batch run, so the progress dialog's Cancel button can stop whichever download is in flight. */
    private static final class BatchState {
        volatile boolean cancelled;
        DownloadOsmTask currentDownload;

        void cancel() {
            if (cancelled) return;
            cancelled = true;
            DownloadOsmTask download = currentDownload;
            if (download != null) download.cancel();
        }
    }
}
