package org.openstreetmap.josm.plugins.betterworkspace;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import jakarta.json.Json;
import jakarta.json.JsonReader;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.preferences.sources.SourceEntry;
import org.openstreetmap.josm.data.preferences.sources.SourceType;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.io.GeoJSONReader;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Loads Esri World Imagery's "Citations" footprint layer for the current map
 * view as a data layer, so each footprint's real acquisition date can be read
 * from its tags (as {@code SRC_DATE}/{@code date}, plus resolution/accuracy/
 * source). JOSM's own "Show tile info" reports today's date for this imagery,
 * not the actual capture date - this queries the same underlying metadata
 * service that https://martinedoesgis.github.io/esri-imagery-date-finder/
 * uses, instead.
 *
 * <p>The query is capped to the current view and refused outright above
 * {@link #MAX_DIAGONAL_METERS} across: Esri's own server already caps each
 * response at 100 footprints (its {@code maxRecordCount}), so a large query
 * wouldn't just be heavier than necessary, it would silently come back
 * incomplete. If it's still truncated at that size, the user is told rather
 * than shown a misleadingly partial grid.
 */
final class LoadEsriImageryDatesAction extends JosmAction {

    private static final String QUERY_URL =
            "https://services.arcgisonline.com/arcgis/rest/services/World_Imagery/MapServer/4/query";
    private static final String OUT_FIELDS = "SRC_DATE,SRC_RES,SRC_ACC,SRC_DESC,NICE_NAME,NICE_DESC";
    private static final double MAX_DIAGONAL_METERS = 50_000;

    /**
     * Selector matches only on {@code SRC_RES}, a field name unique to Esri's Citations layer, so
     * this never touches real OSM data even though JOSM's map paint styles apply dataset-wide
     * rather than per-layer. Text color is a user-adjustable {@code setting()}, editable from the
     * gear icon next to this style in Preferences -&gt; Map Paint Styles.
     */
    private static final String MAPCSS_STYLE =
            "setting::colordisplay {\n"
            + "  type: color;\n"
            + "  label: tr(\"Color used for displaying ESRI text\");\n"
            + "  default: #000000;\n"
            + "}\n"
            + "\n"
            + "way[SRC_RES], relation[SRC_RES] {\n"
            + "  text: date;\n"
            + "  font-size: 18;\n"
            + "  text-position: center;\n"
            + "  text-color: setting(\"colordisplay\");\n"
            + "  text-halo-radius: 1;\n"
            + "  text-halo-color: #000000;\n"
            + "  color: #ffaa00;\n"
            + "  width: 2;\n"
            + "}\n";
    private static final String MAPCSS_STYLE_NAME = "BetterWorkspace: Esri Imagery Dates";
    /** Bump whenever {@link #MAPCSS_STYLE} changes, so already-registered installs pick up the update. */
    private static final int MAPCSS_STYLE_VERSION = 2;
    private static final String MAPCSS_STYLE_VERSION_PREF = "betterworkspace.esri.mapcss.styleversion";

    LoadEsriImageryDatesAction() {
        super(I18n.tr("Load Esri Imagery Date Grid"), "betterworkspace/esri-imagery-dates",
                I18n.tr("Load Esri World Imagery's acquisition-date footprints for the current view as a data layer"),
                Shortcut.registerShortcut("betterworkspace:esriimagerydates",
                        I18n.tr("Load Esri Imagery Date Grid"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                true, "betterworkspace:esriimagerydates", false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MapFrame mapFrame = MainApplication.getMap();
        if (mapFrame == null || mapFrame.mapView == null) {
            return;
        }
        Bounds view = mapFrame.mapView.getRealBounds();
        double diagonalMeters = view.getMin().greatCircleDistance(view.getMax());
        if (diagonalMeters > MAX_DIAGONAL_METERS) {
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                    I18n.tr("Zoom in further before loading the imagery date grid: the current view is "
                            + "{0} km across (max {1} km). Esri only returns the first 100 footprints per "
                            + "query, so a larger area would come back silently incomplete.",
                            String.format(Locale.ROOT, "%.0f", diagonalMeters / 1000),
                            String.format(Locale.ROOT, "%.0f", MAX_DIAGONAL_METERS / 1000)),
                    "BetterWorkspace", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final JDialog progress = progressDialog(I18n.tr("Loading Esri imagery date grid..."));
        SwingWorker<DataSet, Void> worker = new SwingWorker<DataSet, Void>() {
            private String errorMessage;
            private boolean truncated;

            @Override
            protected DataSet doInBackground() {
                try {
                    byte[] body = fetchBody(view);
                    truncated = wasTruncated(body);
                    return GeoJSONReader.parseDataSet(new ByteArrayInputStream(body), NullProgressMonitor.INSTANCE);
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
                    JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                            I18n.tr("Failed to load the Esri imagery date grid:\n{0}", errorMessage),
                            "BetterWorkspace", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                addFriendlyDateTags(dataSet);
                ensureDateLabelStyleActive();
                MainApplication.getLayerManager().addLayer(
                        new OsmDataLayer(dataSet, I18n.tr("Esri Imagery Date Grid"), null));
                if (truncated) {
                    JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                            I18n.tr("Esri returned only the first 100 footprints for this area - "
                                    + "zoom in and reload to see the rest."),
                            "BetterWorkspace", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        progress.setVisible(true);
        worker.execute();
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(MainApplication.getMap() != null);
    }

    /** Adds a human-readable {@code date=YYYY-MM-DD} tag alongside Esri's raw {@code SRC_DATE} (YYYYMMDD). */
    private static void addFriendlyDateTags(DataSet dataSet) {
        for (var primitive : dataSet.getPrimitives(p -> true)) {
            String rawDate = primitive.get("SRC_DATE");
            if (rawDate != null && rawDate.matches("\\d{8}")) {
                primitive.put("date", rawDate.substring(0, 4) + "-" + rawDate.substring(4, 6) + "-" + rawDate.substring(6, 8));
            }
        }
    }

    /**
     * Registers {@link #MAPCSS_STYLE} as an active map paint style, so the {@code date} tag is
     * drawn as a label directly on every footprint (JOSM has no per-layer styling, only this
     * dataset-wide mechanism - hence the tight {@code [SRC_RES]} selector above). Writes the style
     * to a file under JOSM's user data directory and registers it via {@link MapPaintStyles}.
     *
     * <p>Registration is by name, which on its own would make this a permanent no-op after the
     * first run - a later plugin update that changes {@link #MAPCSS_STYLE} would never reach users
     * who already have the old file. {@link #MAPCSS_STYLE_VERSION} guards against that: it's stored
     * in JOSM's preferences once written, and a mismatch means the on-disk file is stale, so it's
     * rewritten and the existing (already-registered, so still using the same URL/settings) source
     * is reloaded in place rather than re-added as a duplicate entry.
     */
    private static void ensureDateLabelStyleActive() {
        int installedVersion = Config.getPref().getInt(MAPCSS_STYLE_VERSION_PREF, 0);
        var existing = MapPaintStyles.getStyles().getStyleSources().stream()
                .filter(s -> MAPCSS_STYLE_NAME.equals(s.name))
                .findFirst();
        if (existing.isPresent() && installedVersion >= MAPCSS_STYLE_VERSION) {
            return;
        }
        try {
            File dir = Config.getDirs().getUserDataDirectory(true);
            File styleFile = new File(dir, "betterworkspace-esri-imagery-dates.mapcss");
            Files.writeString(styleFile.toPath(), MAPCSS_STYLE, StandardCharsets.UTF_8);
            if (existing.isPresent()) {
                existing.get().loadStyleSource();
                MapPaintStyles.fireMapPaintStylesUpdated();
            } else {
                MapPaintStyles.addStyle(new SourceEntry(SourceType.MAP_PAINT_STYLE,
                        styleFile.toURI().toString(), MAPCSS_STYLE_NAME, MAPCSS_STYLE_NAME, true));
            }
            Config.getPref().putInt(MAPCSS_STYLE_VERSION_PREF, MAPCSS_STYLE_VERSION);
        } catch (IOException | RuntimeException ex) {
            Logging.warn("BetterWorkspace: could not register Esri imagery date label style: " + ex);
        }
    }

    private static byte[] fetchBody(Bounds view) throws IOException {
        String bbox = String.format(Locale.ROOT, "%f,%f,%f,%f",
                view.getMinLon(), view.getMinLat(), view.getMaxLon(), view.getMaxLat());
        StringBuilder url = new StringBuilder(QUERY_URL).append('?');
        // Excludes Esri's undated global base mosaic (e.g. "TerraColor NextGen"), which would
        // otherwise come back as one huge featureless polygon covering the whole query area.
        appendParam(url, "where", "SRC_DATE IS NOT NULL");
        appendParam(url, "outFields", OUT_FIELDS);
        appendParam(url, "geometry", bbox);
        appendParam(url, "geometryType", "esriGeometryEnvelope");
        appendParam(url, "inSR", "4326");
        appendParam(url, "spatialRel", "esriSpatialRelIntersects");
        appendParam(url, "returnGeometry", "true");
        appendParam(url, "f", "geojson");

        HttpURLConnection conn = (HttpURLConnection) new URL(url.substring(0, url.length() - 1)).openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "BetterWorkspace-JOSMPlugin/1.0.1");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("Esri World Imagery API returned HTTP " + code);
        }
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return buffer.toByteArray();
        }
    }

    private static void appendParam(StringBuilder url, String key, String value) {
        url.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&');
    }

    /** True if Esri's {@code maxRecordCount} (100 features) cut this response short. */
    private static boolean wasTruncated(byte[] body) {
        try (JsonReader jsonReader = Json.createReader(new ByteArrayInputStream(body))) {
            return jsonReader.readObject().getBoolean("exceededTransferLimit", false);
        } catch (RuntimeException ex) {
            return false;
        }
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
}
